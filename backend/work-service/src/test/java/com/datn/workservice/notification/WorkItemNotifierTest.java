package com.datn.workservice.notification;

import com.datn.workservice.entity.Sprint;
import com.datn.workservice.entity.Status;
import com.datn.workservice.entity.WorkItem;
import com.datn.workservice.entity.WorkItemComment;
import com.datn.workservice.entity.WorkItemCompletionRequest;
import com.datn.workservice.entity.WorkItemLink;
import com.datn.workservice.enums.LinkType;
import com.datn.workservice.enums.StatusCategory;
import com.datn.workservice.enums.WorkItemPriority;
import com.datn.workservice.repository.SprintRepository;
import com.datn.workservice.repository.StatusRepository;
import com.datn.workservice.repository.WorkItemLinkRepository;
import com.datn.workservice.repository.WorkItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.datn.workservice.notification.NotificationTestSupport.data;
import static com.datn.workservice.notification.NotificationTestSupport.recipientIds;
import static com.datn.workservice.notification.NotificationTestSupport.recipients;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkItemNotifierTest {

    private final NotificationTestSupport t = new NotificationTestSupport();
    private final WorkItemRepository items = mock(WorkItemRepository.class);
    private final WorkItemLinkRepository links = mock(WorkItemLinkRepository.class);
    private final StatusRepository statuses = mock(StatusRepository.class);
    private final SprintRepository sprints = mock(SprintRepository.class);
    private WorkItemNotifier notifier;

    private final UUID an = UUID.randomUUID();     // người thao tác / Quản lý
    private final UUID binh = UUID.randomUUID();   // người thực hiện
    private final UUID chi = UUID.randomUUID();    // người tạo
    private final Status todo = Status.builder().id(UUID.randomUUID()).name("Cần làm").category(StatusCategory.TODO).build();
    private final Status doing = Status.builder().id(UUID.randomUUID()).name("Đang thực hiện").category(StatusCategory.IN_PROGRESS).build();
    private final Status done = Status.builder().id(UUID.randomUUID()).name("Hoàn thành").category(StatusCategory.DONE).build();

    @BeforeEach
    void setUp() {
        notifier = new WorkItemNotifier(t.publisher, t.projectRepository, items, links, statuses, sprints);
        for (Status s : List.of(todo, doing, done)) lenient().when(statuses.findById(s.getId())).thenReturn(Optional.of(s));
        lenient().when(links.findBySourceItemIdAndLinkType(any(), any())).thenReturn(List.of());
        lenient().when(links.findByTargetItemIdAndLinkType(any(), any())).thenReturn(List.of());
    }

    private WorkItem item(String code, Status status, UUID assignee) {
        WorkItem w = WorkItem.builder().id(UUID.randomUUID()).projectId(t.project.getId()).code(code).summary("Việc " + code)
                .statusId(status.getId()).assigneeId(assignee).reporterId(chi).priority(WorkItemPriority.MEDIUM)
                .dueDate(LocalDate.of(2026, 11, 20)).build();
        lenient().when(items.findById(w.getId())).thenReturn(Optional.of(w));
        return w;
    }

    private static WorkItem copy(WorkItem w) {
        return WorkItem.builder().id(w.getId()).projectId(w.getProjectId()).code(w.getCode()).summary(w.getSummary())
                .statusId(w.getStatusId()).assigneeId(w.getAssigneeId()).reporterId(w.getReporterId())
                .priority(w.getPriority()).dueDate(w.getDueDate()).sprintId(w.getSprintId()).build();
    }

    // ─── A ───

    @Test
    void reassign_sendsA1ToNewAndA2ToOld() {
        WorkItem w = item("MOC-2", todo, binh);
        notifier.assigneeChanged(w, chi, binh, an);
        assertThat(t.types()).containsExactly("TASK_ASSIGNED", "TASK_UNASSIGNED");
        assertThat(recipients(t.only("TASK_ASSIGNED"))).containsExactly(Map.entry(binh.toString(), "NEW_ASSIGNEE"));
        assertThat(data(t.only("TASK_ASSIGNED"))).containsEntry("dueDate", "2026-11-20").containsEntry("priority", "MEDIUM");
        assertThat(data(t.only("TASK_UNASSIGNED"))).containsEntry("newAssigneeId", binh.toString());
    }

    @Test
    void selfAssign_sendsNothing() {
        WorkItem w = item("MOC-2", todo, an);
        notifier.created(w, an);
        verify(t.outbox, never()).record(anyString(), any(), anyString(), any());
    }

    @Test
    void update_priorityDueDateSprint_eachProduceOneNotification() {
        WorkItem before = item("MOC-5", todo, binh);
        WorkItem after = copy(before);
        after.setPriority(WorkItemPriority.HIGHEST);
        after.setDueDate(LocalDate.of(2026, 11, 18));
        UUID sprintId = UUID.randomUUID();
        after.setSprintId(sprintId);
        when(sprints.findById(sprintId)).thenReturn(Optional.of(Sprint.builder().id(sprintId).name("Giai đoạn 2").build()));

        notifier.updated(before, after, an);

        assertThat(t.types()).containsExactly("TASK_PRIORITY_RAISED", "TASK_DUE_DATE_CHANGED", "TASK_SPRINT_CHANGED");
        assertThat(data(t.only("TASK_DUE_DATE_CHANGED"))).containsEntry("oldDueDate", "2026-11-20").containsEntry("newDueDate", "2026-11-18");
        assertThat(data(t.only("TASK_SPRINT_CHANGED"))).containsEntry("sprintName", "Giai đoạn 2");
    }

    @Test
    void update_withReassignment_onlySendsAssignmentNotifications() {
        WorkItem before = item("MOC-5", todo, binh);
        WorkItem after = copy(before);
        after.setAssigneeId(chi);
        after.setPriority(WorkItemPriority.HIGHEST);
        notifier.updated(before, after, an);
        assertThat(t.types()).containsExactly("TASK_ASSIGNED", "TASK_UNASSIGNED");
    }

    @Test
    void priorityRaised_onlyWhenBecomingHighOrHighest() {
        assertThat(WorkItemNotifier.priorityRaised(WorkItemPriority.MEDIUM, WorkItemPriority.HIGH)).isTrue();
        assertThat(WorkItemNotifier.priorityRaised(WorkItemPriority.HIGH, WorkItemPriority.HIGHEST)).isTrue();
        assertThat(WorkItemNotifier.priorityRaised(WorkItemPriority.HIGHEST, WorkItemPriority.HIGH)).isFalse();
        assertThat(WorkItemNotifier.priorityRaised(WorkItemPriority.LOW, WorkItemPriority.MEDIUM)).isFalse();
    }

    @Test
    void statusChange_notifiesReporterAndAssignee_exceptActorAndExcluded() {
        WorkItem w = item("MOC-2", doing, binh);
        notifier.statusChanged(w, todo, doing, binh, Set.of());
        Map<String, Object> a3 = t.only("TASK_STATUS_CHANGED");
        assertThat(recipientIds(a3)).containsExactly(chi.toString()); // Bình là người thao tác
        assertThat(data(a3)).containsEntry("toStatus", "Đang thực hiện").containsEntry("toCategory", "IN_PROGRESS")
                .containsEntry("fromStatus", "Cần làm");
    }

    @Test
    void statusChange_excludedRequesterGetsNoA3() {
        WorkItem w = item("MOC-2", done, binh);
        notifier.statusChanged(w, doing, done, an, new LinkedHashSet<>(List.of(binh)));
        assertThat(recipientIds(t.only("TASK_STATUS_CHANGED"))).containsExactly(chi.toString());
    }

    // ─── B ───

    @Test
    void blockerDone_unblocksTargetWithNoOtherOpenBlockers() {
        WorkItem blocker = item("MOC-2", done, binh);
        WorkItem target = item("MOC-7", todo, chi);
        WorkItemLink link = WorkItemLink.builder().id(UUID.randomUUID()).sourceItemId(blocker.getId())
                .targetItemId(target.getId()).linkType(LinkType.BLOCKS).build();
        when(links.findBySourceItemIdAndLinkType(blocker.getId(), LinkType.BLOCKS)).thenReturn(List.of(link));
        when(links.findByTargetItemIdAndLinkType(target.getId(), LinkType.BLOCKS)).thenReturn(List.of(link));

        notifier.statusChanged(blocker, doing, done, an, Set.of());

        Map<String, Object> b2 = t.only("TASK_UNBLOCKED");
        assertThat(b2).containsEntry("entityCode", "MOC-7");
        assertThat(recipientIds(b2)).containsExactly(chi.toString());
        assertThat(data(b2)).containsEntry("blockerCode", "MOC-2").containsEntry("reason", "DONE");
    }

    @Test
    void blockerDone_butAnotherOpenBlockerRemains_noB2() {
        WorkItem blocker = item("MOC-2", done, binh);
        WorkItem other = item("MOC-3", doing, binh);
        WorkItem target = item("MOC-7", todo, chi);
        WorkItemLink l1 = WorkItemLink.builder().sourceItemId(blocker.getId()).targetItemId(target.getId()).linkType(LinkType.BLOCKS).build();
        WorkItemLink l2 = WorkItemLink.builder().sourceItemId(other.getId()).targetItemId(target.getId()).linkType(LinkType.BLOCKS).build();
        when(links.findBySourceItemIdAndLinkType(blocker.getId(), LinkType.BLOCKS)).thenReturn(List.of(l1));
        when(links.findByTargetItemIdAndLinkType(target.getId(), LinkType.BLOCKS)).thenReturn(List.of(l1, l2));

        notifier.statusChanged(blocker, doing, done, an, Set.of());
        assertThat(t.types()).doesNotContain("TASK_UNBLOCKED");
    }

    @Test
    void blocked_notifiesTargetAssigneeUnlessDone() {
        WorkItem blocker = item("MOC-2", doing, binh);
        WorkItem target = item("MOC-7", todo, chi);
        notifier.blocked(blocker, target, an);
        assertThat(data(t.only("TASK_BLOCKED"))).containsEntry("blockerCode", "MOC-2");

        WorkItem finished = item("MOC-8", done, chi);
        notifier.blocked(blocker, finished, an);
        assertThat(t.types()).containsExactly("TASK_BLOCKED");
    }

    // ─── C ───

    @Test
    void completionRequested_goesToAllManagers() {
        UUID admin2 = UUID.randomUUID();
        when(t.resolver.managers(t.project)).thenReturn(new LinkedHashSet<>(List.of(an, admin2)));
        WorkItem w = item("MOC-2", doing, binh);
        WorkItemCompletionRequest req = WorkItemCompletionRequest.builder().id(UUID.randomUUID()).requestedBy(binh)
                .requestNote("Xong rồi").build();
        notifier.completionRequested(w, req, done);
        Map<String, Object> c1 = t.only("COMPLETION_REQUESTED");
        assertThat(recipients(c1)).containsOnlyKeys(an.toString(), admin2.toString());
        assertThat(c1).containsEntry("actorId", binh.toString());
        assertThat(data(c1)).containsEntry("note", "Xong rồi").containsEntry("targetStatus", "Hoàn thành");
    }

    @Test
    void completionCancelled_byRequester_onlyClosesManagersRequest() {
        WorkItem w = item("MOC-2", doing, binh);
        WorkItemCompletionRequest req = WorkItemCompletionRequest.builder().requestedBy(binh).build();
        notifier.completionCancelled(w, req, "CANCELLED_BY_ADMIN", null, binh);
        assertThat(t.types()).containsExactly("COMPLETION_WITHDRAWN");

        notifier.completionCancelled(w, req, "AUTO_STATUS_CHANGED", "Cần làm", an);
        Map<String, Object> c5 = t.only("COMPLETION_CANCELLED");
        assertThat(recipientIds(c5)).containsExactly(binh.toString());
        assertThat(data(c5)).containsEntry("reason", "AUTO_STATUS_CHANGED").containsEntry("toStatus", "Cần làm");
    }

    @Test
    void approved_goesToRequesterAndAssignee() {
        WorkItem w = item("MOC-2", done, chi);
        WorkItemCompletionRequest req = WorkItemCompletionRequest.builder().requestedBy(binh).build();
        notifier.completionApproved(w, req, an);
        assertThat(recipients(t.only("COMPLETION_APPROVED")))
                .containsEntry(binh.toString(), "REQUESTER").containsEntry(chi.toString(), "ASSIGNEE");
    }

    // ─── D ───

    @Test
    void comment_mentionReplyAndAdded_eachPersonOnce() {
        WorkItem w = item("MOC-2", doing, binh); // Bình thực hiện, Chi tạo
        UUID dung = UUID.randomUUID();
        WorkItemComment c = WorkItemComment.builder().id(UUID.randomUUID())
                .body("@[Bình](" + binh + ") xem nhé, @[An](" + an + ")").build();

        notifier.commentAdded(w, c, dung, an); // trả lời bình luận của Dũng, người viết là An

        assertThat(t.types()).containsExactly("COMMENT_MENTION", "COMMENT_REPLIED", "COMMENT_ADDED");
        assertThat(recipientIds(t.only("COMMENT_MENTION"))).containsExactly(binh.toString()); // An tự nhắc mình: bỏ
        assertThat(recipientIds(t.only("COMMENT_REPLIED"))).containsExactly(dung.toString());
        assertThat(recipientIds(t.only("COMMENT_ADDED"))).containsExactly(chi.toString()); // Bình đã nhận D1
        assertThat(data(t.only("COMMENT_MENTION"))).containsEntry("snippet", "@Bình xem nhé, @An")
                .containsEntry("commentId", c.getId().toString());
    }

    @Test
    void editingComment_onlyNotifiesNewlyMentioned() {
        WorkItem w = item("MOC-2", doing, binh);
        WorkItemComment c = WorkItemComment.builder().id(UUID.randomUUID())
                .body("@[Bình](" + binh + ") và @[Chi](" + chi + ")").build();
        notifier.commentEdited(w, c, "@[Bình](" + binh + ")", an);
        assertThat(recipientIds(t.only("COMMENT_MENTION"))).containsExactly(chi.toString());
    }
}
