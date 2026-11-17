package com.datn.workservice.notification;

import com.datn.workservice.entity.Invitation;
import com.datn.workservice.entity.Sprint;
import com.datn.workservice.entity.WorkItem;
import com.datn.workservice.enums.SprintState;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.datn.workservice.notification.NotificationTestSupport.data;
import static com.datn.workservice.notification.NotificationTestSupport.recipientIds;
import static com.datn.workservice.notification.NotificationTestSupport.recipients;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectNotifierTest {

    private final NotificationTestSupport t = new NotificationTestSupport();
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final ProjectNotifier notifier = new ProjectNotifier(t.publisher, jdbc);

    private final UUID an = UUID.randomUUID();
    private final UUID binh = UUID.randomUUID();
    private final UUID chi = UUID.randomUUID();

    private Sprint sprint(SprintState state) {
        return Sprint.builder().id(UUID.randomUUID()).projectId(t.project.getId()).name("Giai đoạn 1").state(state)
                .startDate(LocalDateTime.of(2026, 11, 14, 8, 0)).endDate(LocalDateTime.of(2026, 11, 28, 17, 0)).build();
    }

    private WorkItem item(String code, UUID assignee) {
        return WorkItem.builder().id(UUID.randomUUID()).projectId(t.project.getId()).code(code).assigneeId(assignee).build();
    }

    @Test
    void sprintStarted_allMembers_withPersonalCounts() {
        when(t.resolver.members(t.project)).thenReturn(new LinkedHashSet<>(List.of(an, binh, chi)));
        notifier.sprintStarted(t.project, sprint(SprintState.ACTIVE),
                List.of(item("MOC-2", binh), item("MOC-5", binh), item("MOC-7", chi), item("MOC-9", null)), an);
        Map<String, Object> e1 = t.only("SPRINT_STARTED");
        assertThat(recipientIds(e1)).containsExactlyInAnyOrder(binh.toString(), chi.toString());
        assertThat(e1).containsEntry("entityType", "SPRINT").containsEntry("entityTitle", "Giai đoạn 1");
        assertThat(data(e1)).containsEntry("startDate", "2026-11-14").containsEntry("endDate", "2026-11-28")
                .containsEntry("itemCounts", Map.of(binh.toString(), 2, chi.toString(), 1))
                .containsEntry("sprintName", "Giai đoạn 1");
    }

    @Test
    void sprintCompleted_countsAndIncompletePerAssignee() {
        when(t.resolver.members(t.project)).thenReturn(new LinkedHashSet<>(List.of(binh, chi)));
        notifier.sprintCompleted(t.project, sprint(SprintState.CLOSED), List.of(item("MOC-2", binh)),
                List.of(item("MOC-5", binh), item("MOC-7", chi)), "Giai đoạn 2", an);
        assertThat(data(t.only("SPRINT_COMPLETED"))).containsEntry("completedCount", 1).containsEntry("totalCount", 3)
                .containsEntry("targetName", "Giai đoạn 2")
                .containsEntry("incompleteCounts", Map.of(binh.toString(), 1, chi.toString(), 1));
    }

    @Test
    void scopeChanges_onlyForActiveSprint_oneEventPerAssignee() {
        notifier.sprintScopeChanged(t.project, sprint(SprintState.FUTURE), List.of(item("MOC-2", binh)), true, an);
        verify(t.outbox, never()).record(anyString(), any(), anyString(), any());

        notifier.sprintScopeChanged(t.project, sprint(SprintState.ACTIVE),
                List.of(item("MOC-2", binh), item("MOC-5", binh), item("MOC-7", chi)), true, an);
        assertThat(t.types()).containsExactly("SPRINT_SCOPE_ADDED", "SPRINT_SCOPE_ADDED");
        assertThat(data(t.recorded().get(0))).containsEntry("codes", List.of("MOC-2", "MOC-5"));
    }

    @Test
    void sprintDates_onlyWhenActiveEndDateChanges() {
        Sprint s = sprint(SprintState.ACTIVE);
        notifier.sprintDatesChanged(t.project, s, LocalDateTime.of(2026, 11, 28, 9, 0), List.of(item("MOC-2", binh)), an);
        verify(t.outbox, never()).record(anyString(), any(), anyString(), any()); // cùng ngày, khác giờ

        notifier.sprintDatesChanged(t.project, s, LocalDateTime.of(2026, 11, 25, 17, 0), List.of(item("MOC-2", binh)), an);
        assertThat(data(t.only("SPRINT_DATES_CHANGED"))).containsEntry("oldEndDate", "2026-11-25").containsEntry("newEndDate", "2026-11-28");
    }

    @Test
    void invitation_lifecycle() {
        Invitation inv = Invitation.builder().id(UUID.randomUUID()).inviteeUserId(chi).invitedBy(an).build();
        when(t.resolver.onlyMembers(eq(t.project), any())).thenReturn(new LinkedHashSet<>(List.of(an))); // Chi chưa là thành viên
        notifier.invitationCreated(t.project, inv, "MEMBER", an);
        Map<String, Object> g1 = t.only("PROJECT_INVITATION_RECEIVED");
        assertThat(recipients(g1)).containsExactly(Map.entry(chi.toString(), "INVITEE"));
        assertThat(g1).containsEntry("entityType", "INVITATION").containsEntry("entityId", inv.getId().toString());

        notifier.invitationCancelled(t.project, inv, an);
        assertThat(t.types()).contains("PROJECT_INVITATION_CANCELLED");
    }

    @Test
    void roleChange_marksDowngrade_andIgnoresNoop() {
        notifier.roleChanged(t.project, binh, "MEMBER", "member", an);
        verify(t.outbox, never()).record(anyString(), any(), anyString(), any());

        notifier.roleChanged(t.project, binh, "ADMINISTRATOR", "VIEWER", an);
        assertThat(data(t.only("PROJECT_ROLE_CHANGED"))).containsEntry("downgraded", true).containsEntry("newRole", "VIEWER");
    }

    @Test
    void memberRemoved_notifiesRemovedUser_andManagersWhenOpenWorkRemains() {
        when(t.resolver.onlyMembers(eq(t.project), any())).thenAnswer(inv -> {
            var s = new LinkedHashSet<UUID>(inv.getArgument(1));
            s.remove(binh); // Bình vừa bị xoá
            return s;
        });
        when(t.resolver.managers(t.project)).thenReturn(new LinkedHashSet<>(List.of(an)));
        when(jdbc.queryForList(anyString(), anyMap(), eq(String.class))).thenReturn(List.of("MOC-5", "MOC-24"));

        notifier.memberRemoved(t.project, binh, chi);

        assertThat(recipientIds(t.only("PROJECT_MEMBER_REMOVED"))).containsExactly(binh.toString());
        Map<String, Object> g3 = t.only("MEMBER_REMOVED_OPEN_ITEMS");
        assertThat(recipientIds(g3)).containsExactly(an.toString());
        assertThat(data(g3)).containsEntry("memberId", binh.toString()).containsEntry("codes", List.of("MOC-5", "MOC-24"));
    }

    @Test
    void leadChange_notifiesNewAndOldLead() {
        notifier.leadChanged(t.project, binh, chi, an);
        assertThat(recipientIds(t.only("PROJECT_LEAD_CHANGED"))).containsExactlyInAnyOrder(binh.toString(), chi.toString());
    }

    @Test
    void archive_isAllowedOnDeletedProject() {
        t.project.setDeletedAt(LocalDateTime.now());
        when(t.resolver.members(t.project)).thenReturn(new LinkedHashSet<>(List.of(an, binh)));
        notifier.projectArchived(t.project, an);
        assertThat(recipientIds(t.only("PROJECT_ARCHIVED"))).containsExactly(binh.toString());
    }

    @Test
    void schedule_onlyWhenDatesChange() {
        when(t.resolver.members(t.project)).thenReturn(new LinkedHashSet<>(List.of(binh)));
        t.project.setEndDate(LocalDate.of(2026, 12, 15));
        notifier.scheduleChanged(t.project, null, LocalDate.of(2026, 12, 15), an);
        verify(t.outbox, never()).record(anyString(), any(), anyString(), any());
        notifier.scheduleChanged(t.project, null, LocalDate.of(2026, 12, 1), an);
        assertThat(data(t.only("PROJECT_SCHEDULE_CHANGED"))).containsEntry("endChanged", true).containsEntry("newEndDate", "2026-12-15");
    }

    @Test
    void contribution_adjustedAndFrozen() {
        notifier.contributionAdjusted(t.project, binh, new BigDecimal("-2.5"), "Trễ hạn", an);
        assertThat(data(t.only("CONTRIBUTION_ADJUSTED"))).containsEntry("points", -2.5).containsEntry("reason", "Trễ hạn");

        when(t.resolver.members(t.project)).thenReturn(new LinkedHashSet<>(List.of(binh)));
        notifier.contributionFrozen(t.project, sprint(SprintState.CLOSED), Map.of(binh, new BigDecimal("13.0")));
        assertThat(data(t.only("CONTRIBUTION_FROZEN"))).containsEntry("points", Map.of(binh.toString(), 13L));
    }

    @Test
    void nullProject_isIgnoredSafely() {
        notifier.sprintStarted(null, sprint(SprintState.ACTIVE), List.of(), an);
        notifier.memberRemoved(null, binh, an);
        verify(t.outbox, never()).record(anyString(), any(), anyString(), any());
    }

    @Test
    void roleRank() {
        assertThat(ProjectNotifier.roleRank("ADMINISTRATOR")).isGreaterThan(ProjectNotifier.roleRank("MEMBER"));
        assertThat(ProjectNotifier.roleRank("Kế toán")).isEqualTo(ProjectNotifier.roleRank("MEMBER"));
        assertThat(ProjectNotifier.roleRank("VIEWER")).isLessThan(ProjectNotifier.roleRank("MEMBER"));
    }
}
