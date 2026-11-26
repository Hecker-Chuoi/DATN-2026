package com.datn.workservice.notification;

import com.datn.workservice.entity.Project;
import com.datn.workservice.entity.Sprint;
import com.datn.workservice.entity.Status;
import com.datn.workservice.entity.WorkItem;
import com.datn.workservice.entity.WorkItemComment;
import com.datn.workservice.entity.WorkItemCompletionRequest;
import com.datn.workservice.entity.WorkItemLink;
import com.datn.workservice.enums.LinkType;
import com.datn.workservice.enums.StatusCategory;
import com.datn.workservice.enums.WorkItemPriority;
import com.datn.workservice.repository.ProjectRepository;
import com.datn.workservice.repository.SprintRepository;
import com.datn.workservice.repository.StatusRepository;
import com.datn.workservice.repository.WorkItemLinkRepository;
import com.datn.workservice.repository.WorkItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.datn.common.notification.NotificationType.*;
import static com.datn.common.notification.RecipientReason.*;

/**
 * Quyết định gửi thông báo gì, cho ai, khi công việc thay đổi: nhóm A (công việc), B (phụ thuộc),
 * C (xác nhận hoàn thành), D (bình luận) trong docs/services/notification/1_DANH_MUC_LOAI_THONG_BAO.md.
 *
 * Mọi phương thức "an toàn": lỗi được NotificationEventPublisher bắt và ghi log, không làm hỏng nghiệp vụ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkItemNotifier {

    private final NotificationEventPublisher publisher;
    private final ProjectRepository projectRepository;
    private final WorkItemRepository workItemRepository;
    private final WorkItemLinkRepository workItemLinkRepository;
    private final StatusRepository statusRepository;
    private final SprintRepository sprintRepository;

    // ─────────────────────── A. Công việc ───────────────────────

    /** A1 cho người mới, A2 cho người cũ. */
    public void assigneeChanged(WorkItem item, UUID oldAssignee, UUID newAssignee, UUID actorId) {
        if (Objects.equals(oldAssignee, newAssignee)) return;
        Project project = project(item);
        if (newAssignee != null) {
            publisher.event(TASK_ASSIGNED, project).item(item).actor(actorId)
                    .to(newAssignee, NEW_ASSIGNEE)
                    .data("priority", item.getPriority())
                    .data("dueDate", item.getDueDate())
                    .publish();
        }
        if (oldAssignee != null) {
            publisher.event(TASK_UNASSIGNED, project).item(item).actor(actorId)
                    .to(oldAssignee, PREVIOUS_ASSIGNEE)
                    .data("newAssigneeId", newAssignee)
                    .publish();
        }
    }

    /** Công việc / công việc con mới có sẵn người thực hiện. */
    public void created(WorkItem item, UUID actorId) {
        assigneeChanged(item, null, item.getAssigneeId(), actorId);
    }

    /**
     * Sau updateWorkItem: đổi người thực hiện (A1/A2), nâng ưu tiên (A4), đổi hạn chót (A5), đổi chu kỳ (A10).
     * Đã đổi người thực hiện thì chỉ gửi A1/A2 (A1 đã kèm ưu tiên và hạn chót).
     */
    public void updated(WorkItem before, WorkItem after, UUID actorId) {
        if (!Objects.equals(before.getAssigneeId(), after.getAssigneeId())) {
            assigneeChanged(after, before.getAssigneeId(), after.getAssigneeId(), actorId);
            return;
        }
        UUID assignee = after.getAssigneeId();
        if (assignee == null) return;
        Project project = project(after);

        if (priorityRaised(before.getPriority(), after.getPriority())) {
            publisher.event(TASK_PRIORITY_RAISED, project).item(after).actor(actorId)
                    .to(assignee, ASSIGNEE).data("priority", after.getPriority()).publish();
        }
        if (!Objects.equals(before.getDueDate(), after.getDueDate())) {
            publisher.event(TASK_DUE_DATE_CHANGED, project).item(after).actor(actorId)
                    .to(assignee, ASSIGNEE)
                    .data("oldDueDate", before.getDueDate())
                    .data("newDueDate", after.getDueDate())
                    .publish();
        }
        if (!Objects.equals(before.getSprintId(), after.getSprintId())) {
            Sprint sprint = after.getSprintId() == null ? null : sprintRepository.findById(after.getSprintId()).orElse(null);
            publisher.event(TASK_SPRINT_CHANGED, project).item(after).actor(actorId)
                    .to(assignee, ASSIGNEE)
                    .data("sprintName", sprint != null ? sprint.getName() : null)
                    .publish();
        }
    }

    static boolean priorityRaised(WorkItemPriority before, WorkItemPriority after) {
        if (after == null) return false;
        boolean high = after == WorkItemPriority.HIGH || after == WorkItemPriority.HIGHEST;
        return high && (before == null || after.ordinal() > before.ordinal());
    }

    /**
     * A3 cho người tạo + người thực hiện; khi vào nhóm DONE thì kiểm tra B2 cho các việc nó đang chặn.
     *
     * @param excluded người không cần A3 vì đã nhận thông báo riêng (người gửi yêu cầu Tạm hoàn thành vừa được đóng)
     */
    public void statusChanged(WorkItem item, Status from, Status to, UUID actorId, Collection<UUID> excluded) {
        if (to == null || (from != null && Objects.equals(from.getId(), to.getId()))) return;
        Project project = project(item);
        NotificationEventPublisher.Draft draft = publisher.event(TASK_STATUS_CHANGED, project).item(item).actor(actorId)
                .to(item.getReporterId(), REPORTER)
                .to(item.getAssigneeId(), ASSIGNEE)
                .data("fromStatus", from != null ? from.getName() : null)
                .data("toStatus", to.getName())
                .data("toCategory", to.getCategory());
        if (excluded != null) excluded.forEach(draft.recipientIds()::remove);
        draft.publish();

        boolean enteredDone = to.getCategory() == StatusCategory.DONE
                && (from == null || from.getCategory() != StatusCategory.DONE);
        if (enteredDone) {
            blockerCleared(item, "DONE", actorId);
        }
    }

    /** A6, và B2 cho các việc mà công việc bị xoá đang chặn. */
    public void deleted(WorkItem item, UUID actorId) {
        publisher.event(TASK_DELETED, project(item)).item(item).actor(actorId)
                .to(item.getAssigneeId(), ASSIGNEE)
                .to(item.getReporterId(), REPORTER)
                .publish();
        blockerCleared(item, "DELETED", actorId);
    }

    /** A7: công việc cha tự mở lại vì công việc con chưa xong. */
    public void parentReopened(WorkItem parent, WorkItem child, Status newParentStatus, Status childStatus, UUID actorId) {
        publisher.event(PARENT_AUTO_REOPENED, project(parent)).item(parent).actor(actorId)
                .to(parent.getAssigneeId(), ASSIGNEE)
                .to(parent.getReporterId(), REPORTER)
                .data("childCode", child.getCode())
                .data("toStatus", childStatus != null ? childStatus.getName() : null)
                .data("parentStatus", newParentStatus != null ? newParentStatus.getName() : null)
                .publish();
    }

    /** A8: mỗi người thực hiện nhận một thông báo liệt kê công việc con của mình (R5). */
    public void subtasksAutoResolved(WorkItem parent, List<WorkItem> subtasks, UUID actorId) {
        Map<UUID, List<String>> byAssignee = new LinkedHashMap<>();
        for (WorkItem s : subtasks) {
            if (s.getAssigneeId() != null) byAssignee.computeIfAbsent(s.getAssigneeId(), k -> new ArrayList<>()).add(s.getCode());
        }
        Project project = project(parent);
        byAssignee.forEach((assignee, codes) -> publisher.event(SUBTASKS_AUTO_RESOLVED, project).item(parent).actor(actorId)
                .to(assignee, ASSIGNEE).data("childCodes", codes).publish());
    }

    /** A9: công việc nguồn tự hoàn thành vì trùng lặp. */
    public void markedDuplicate(WorkItem source, WorkItem target, UUID actorId) {
        publisher.event(TASK_MARKED_DUPLICATE, project(source)).item(source).actor(actorId)
                .to(source.getAssigneeId(), ASSIGNEE)
                .to(source.getReporterId(), REPORTER)
                .data("targetCode", target.getCode())
                .publish();
    }

    // ─────────────────────── B. Phụ thuộc ───────────────────────

    /** B1: {@code blocker} chặn {@code target}. Không báo nếu target đã xong. */
    public void blocked(WorkItem blocker, WorkItem target, UUID actorId) {
        if (target.getAssigneeId() == null || isDone(target)) return;
        publisher.event(TASK_BLOCKED, project(target)).item(target).actor(actorId)
                .to(target.getAssigneeId(), ASSIGNEE)
                .data("blockerCode", blocker.getCode())
                .publish();
    }

    /** B2 khi một liên kết BLOCKS bị gỡ. */
    public void linkRemoved(WorkItemLink link, WorkItem source, WorkItem target, UUID actorId) {
        if (link.getLinkType() != LinkType.BLOCKS || source == null || target == null) return;
        notifyIfUnblocked(target, source, "LINK_REMOVED", actorId);
    }

    /** B2 cho mọi việc mà {@code blocker} đang chặn (blocker vừa xong hoặc bị xoá). */
    public void blockerCleared(WorkItem blocker, String reason, UUID actorId) {
        List<WorkItemLink> outgoing = workItemLinkRepository.findBySourceItemIdAndLinkType(blocker.getId(), LinkType.BLOCKS);
        for (WorkItemLink link : outgoing) {
            workItemRepository.findById(link.getTargetItemId())
                    .ifPresent(target -> notifyIfUnblocked(target, blocker, reason, actorId));
        }
    }

    private void notifyIfUnblocked(WorkItem target, WorkItem clearedBlocker, String reason, UUID actorId) {
        if (target.getDeletedAt() != null || target.getAssigneeId() == null || isDone(target)) return;
        boolean stillBlocked = workItemLinkRepository.findByTargetItemIdAndLinkType(target.getId(), LinkType.BLOCKS).stream()
                .filter(l -> !l.getSourceItemId().equals(clearedBlocker.getId()))
                .map(l -> workItemRepository.findById(l.getSourceItemId()).orElse(null))
                .anyMatch(b -> b != null && b.getDeletedAt() == null && !isDone(b));
        if (stillBlocked) return;
        publisher.event(TASK_UNBLOCKED, project(target)).item(target).actor(actorId)
                .to(target.getAssigneeId(), ASSIGNEE)
                .data("blockerCode", clearedBlocker.getCode())
                .data("reason", reason)
                .publish();
    }

    // ─────────────────────── C. Xác nhận hoàn thành ───────────────────────

    /** C1: gửi mọi Quản lý dự án. */
    public void completionRequested(WorkItem item, WorkItemCompletionRequest request, Status targetStatus) {
        Project project = project(item);
        if (project == null) return;
        publisher.event(COMPLETION_REQUESTED, project).item(item).actor(request.getRequestedBy())
                .toAll(publisher.recipients().managers(project), PROJECT_MANAGER)
                .data("note", request.getRequestNote())
                .data("targetStatus", targetStatus != null ? targetStatus.getName() : null)
                .data("requestId", request.getId())
                .publish();
    }

    /** C2: người gửi yêu cầu (+ người thực hiện nếu khác). */
    public void completionApproved(WorkItem item, WorkItemCompletionRequest request, UUID actorId) {
        publisher.event(COMPLETION_APPROVED, project(item)).item(item).actor(actorId)
                .to(request.getRequestedBy(), REQUESTER)
                .to(item.getAssigneeId(), ASSIGNEE)
                .publish();
    }

    /** C3. */
    public void completionRejected(WorkItem item, WorkItemCompletionRequest request, String reason, UUID actorId) {
        publisher.event(COMPLETION_REJECTED, project(item)).item(item).actor(actorId)
                .to(request.getRequestedBy(), REQUESTER)
                .data("reason", reason)
                .publish();
    }

    /**
     * C5 cho người gửi yêu cầu. Nếu chính người gửi rút lại (người nhận = người thao tác) thì không ai cần
     * thông báo, chỉ phát sự kiện nội bộ COMPLETION_WITHDRAWN để đóng C1 của các Quản lý (R9).
     *
     * @param reasonCode AUTO_STATUS_CHANGED (trạng thái đổi trước khi duyệt) hoặc CANCELLED_BY_ADMIN
     */
    public void completionCancelled(WorkItem item, WorkItemCompletionRequest request, String reasonCode, String toStatus, UUID actorId) {
        if (Objects.equals(actorId, request.getRequestedBy())) {
            publisher.event(COMPLETION_WITHDRAWN, project(item)).item(item).actor(actorId).publish();
            return;
        }
        publisher.event(COMPLETION_CANCELLED, project(item)).item(item).actor(actorId)
                .to(request.getRequestedBy(), REQUESTER)
                .data("reason", reasonCode)
                .data("toStatus", toStatus)
                .publish();
    }

    // ─────────────────────── D. Bình luận ───────────────────────

    /**
     * D1 cho người được nhắc tên, D3 cho tác giả bình luận gốc, D2 cho người thực hiện + người tạo.
     * Mỗi người chỉ nhận MỘT trong ba (R3: nhắc tên > trả lời > bình luận mới).
     */
    public void commentAdded(WorkItem item, WorkItemComment comment, UUID parentAuthorId, UUID actorId) {
        Project project = project(item);
        String snippet = MentionParser.plainText(comment.getBody());
        Set<UUID> notified = new LinkedHashSet<>();

        Set<UUID> mentioned = MentionParser.mentionedUserIds(comment.getBody());
        mentioned.remove(actorId);
        if (!mentioned.isEmpty()) {
            NotificationEventPublisher.Draft d = publisher.event(COMMENT_MENTION, project).item(item).actor(actorId)
                    .toAll(mentioned, MENTIONED)
                    .data("commentId", comment.getId()).data("snippet", snippet);
            d.publish();
            notified.addAll(d.recipientIds());
        }

        if (parentAuthorId != null && !notified.contains(parentAuthorId) && !parentAuthorId.equals(actorId)) {
            NotificationEventPublisher.Draft d = publisher.event(COMMENT_REPLIED, project).item(item).actor(actorId)
                    .to(parentAuthorId, COMMENT_AUTHOR)
                    .data("commentId", comment.getId()).data("snippet", snippet);
            d.publish();
            notified.addAll(d.recipientIds());
        }

        NotificationEventPublisher.Draft d = publisher.event(COMMENT_ADDED, project).item(item).actor(actorId)
                .to(item.getAssigneeId(), ASSIGNEE)
                .to(item.getReporterId(), REPORTER)
                .data("commentId", comment.getId()).data("snippet", snippet);
        notified.forEach(d.recipientIds()::remove);
        d.publish();
    }

    /** Sửa bình luận: chỉ báo D1 cho người MỚI được nhắc. */
    public void commentEdited(WorkItem item, WorkItemComment comment, String oldBody, UUID actorId) {
        Set<UUID> added = new LinkedHashSet<>(MentionParser.mentionedUserIds(comment.getBody()));
        added.removeAll(MentionParser.mentionedUserIds(oldBody));
        added.remove(actorId);
        if (added.isEmpty()) return;
        publisher.event(COMMENT_MENTION, project(item)).item(item).actor(actorId)
                .toAll(added, MENTIONED)
                .data("commentId", comment.getId())
                .data("snippet", MentionParser.plainText(comment.getBody()))
                .publish();
    }

    // ─────────────────────── tiện ích ───────────────────────

    private Project project(WorkItem item) {
        return item == null || item.getProjectId() == null ? null : projectRepository.findById(item.getProjectId()).orElse(null);
    }

    private boolean isDone(WorkItem item) {
        if (item.getStatusId() == null) return false;
        return statusRepository.findById(item.getStatusId())
                .map(s -> s.getCategory() == StatusCategory.DONE)
                .orElse(false);
    }

    /** Người gửi yêu cầu vừa được đóng: đã nhận C2/C5 nên không cần thêm A3. */
    public static Set<UUID> requesterOf(WorkItemCompletionRequest request) {
        Set<UUID> s = new LinkedHashSet<>();
        if (request != null && request.getRequestedBy() != null) s.add(request.getRequestedBy());
        return s;
    }
}
