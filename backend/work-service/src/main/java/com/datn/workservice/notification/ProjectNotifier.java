package com.datn.workservice.notification;

import com.datn.workservice.entity.Invitation;
import com.datn.workservice.entity.Project;
import com.datn.workservice.entity.Sprint;
import com.datn.workservice.entity.WorkItem;
import com.datn.workservice.enums.SprintState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.datn.common.notification.NotificationType.*;
import static com.datn.common.notification.RecipientReason.*;

/**
 * Thông báo nhóm E (chu kỳ / giai đoạn), G (thành viên & quyền), H (dự án), I (đóng góp).
 * Xem docs/services/notification/1_DANH_MUC_LOAI_THONG_BAO.md.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectNotifier {

    private final NotificationEventPublisher publisher;
    private final NamedParameterJdbcTemplate jdbc;

    // ─────────────────────── E. Chu kỳ / Giai đoạn ───────────────────────

    /** E1: mọi thành viên; mỗi người biết mình có bao nhiêu việc trong chu kỳ. */
    public void sprintStarted(Project project, Sprint sprint, List<WorkItem> items, UUID actorId) {
        if (project == null) return;
        publisher.event(SPRINT_STARTED, project).sprint(sprint).actor(actorId)
                .toAll(publisher.recipients().members(project), MEMBER)
                .data("startDate", toDate(sprint.getStartDate()))
                .data("endDate", toDate(sprint.getEndDate()))
                .data("itemCounts", countByAssignee(items))
                .publish();
    }

    /** E2: mọi thành viên; người còn việc dở biết việc của mình được chuyển đi đâu. */
    public void sprintCompleted(Project project, Sprint sprint, List<WorkItem> completed, List<WorkItem> incomplete,
                                String targetName, UUID actorId) {
        if (project == null) return;
        publisher.event(SPRINT_COMPLETED, project).sprint(sprint).actor(actorId)
                .toAll(publisher.recipients().members(project), MEMBER)
                .data("completedCount", completed.size())
                .data("totalCount", completed.size() + incomplete.size())
                .data("targetName", targetName)
                .data("incompleteCounts", countByAssignee(incomplete))
                .publish();
    }

    /** E5 (thêm) / E7 (bớt): chỉ khi chu kỳ đang chạy; mỗi người thực hiện một thông báo. */
    public void sprintScopeChanged(Project project, Sprint sprint, List<WorkItem> items, boolean added, UUID actorId) {
        if (project == null) return;
        if (sprint.getState() != SprintState.ACTIVE || items.isEmpty()) return;
        codesByAssignee(items).forEach((assignee, codes) ->
                publisher.event(added ? SPRINT_SCOPE_ADDED : SPRINT_SCOPE_REMOVED, project).sprint(sprint).actor(actorId)
                        .to(assignee, ASSIGNEE)
                        .data("codes", codes)
                        .publish());
    }

    /** E8: chu kỳ đang chạy đổi ngày kết thúc; báo người có việc trong chu kỳ. */
    public void sprintDatesChanged(Project project, Sprint sprint, LocalDateTime oldEnd, List<WorkItem> items, UUID actorId) {
        if (project == null) return;
        if (sprint.getState() != SprintState.ACTIVE || Objects.equals(toDate(oldEnd), toDate(sprint.getEndDate()))) return;
        publisher.event(SPRINT_DATES_CHANGED, project).sprint(sprint).actor(actorId)
                .toAll(assignees(items), ASSIGNEE)
                .data("oldEndDate", toDate(oldEnd))
                .data("newEndDate", toDate(sprint.getEndDate()))
                .publish();
    }

    /** E9: công việc trong chu kỳ bị xoá trở về Kho việc. */
    public void sprintDeleted(Project project, Sprint sprint, List<WorkItem> items, UUID actorId) {
        if (project == null) return;
        if (items.isEmpty()) return;
        publisher.event(SPRINT_DELETED, project).sprint(sprint).actor(actorId)
                .toAll(assignees(items), ASSIGNEE)
                .data("itemCounts", countByAssignee(items))
                .publish();
    }

    // ─────────────────────── G. Thành viên & quyền ───────────────────────

    /** G1: người được mời chưa là thành viên nên cho phép người ngoài dự án. */
    public void invitationCreated(Project project, Invitation invitation, String roleName, UUID actorId) {
        if (project == null) return;
        publisher.event(PROJECT_INVITATION_RECEIVED, project)
                .entity("INVITATION", invitation.getId(), project.getKey(), project.getName())
                .actor(actorId)
                .to(invitation.getInviteeUserId(), INVITEE)
                .data("roleName", roleName)
                .allowNonMembers()
                .publish();
    }

    /** G4: người mời + Trưởng dự án; đồng thời đóng G1 của người được mời (R9). */
    public void invitationAnswered(Project project, Invitation invitation, boolean accepted, String roleName, UUID actorId) {
        if (project == null) return;
        publisher.event(PROJECT_INVITATION_ANSWERED, project)
                .entity("INVITATION", invitation.getId(), project.getKey(), project.getName())
                .actor(actorId)
                .to(invitation.getInvitedBy(), INVITER)
                .to(project.getLeadUserId(), PROJECT_MANAGER)
                .data("accepted", accepted)
                .data("roleName", roleName)
                .publish();
    }

    /** Lời mời bị huỷ: chỉ đóng G1 (sự kiện nội bộ). */
    public void invitationCancelled(Project project, Invitation invitation, UUID actorId) {
        if (project == null) return;
        publisher.event(PROJECT_INVITATION_CANCELLED, project)
                .entity("INVITATION", invitation.getId(), project.getKey(), project.getName())
                .actor(actorId)
                .publish();
    }

    /** G5. */
    public void memberAdded(Project project, UUID userId, String roleName, UUID actorId) {
        if (project == null) return;
        publisher.event(PROJECT_MEMBER_ADDED, project).projectEntity().actor(actorId)
                .to(userId, TARGET_USER)
                .data("roleName", roleName)
                .publish();
    }

    /** G6: hạ quyền thì là Cảnh báo. */
    public void roleChanged(Project project, UUID userId, String oldRole, String newRole, UUID actorId) {
        if (project == null) return;
        if (Objects.equals(normalizeRole(oldRole), normalizeRole(newRole))) return;
        publisher.event(PROJECT_ROLE_CHANGED, project).projectEntity().actor(actorId)
                .to(userId, TARGET_USER)
                .data("oldRole", oldRole)
                .data("newRole", newRole)
                .data("downgraded", roleRank(newRole) < roleRank(oldRole))
                .publish();
    }

    /** G2 cho người bị xoá (đã mất quyền nên cho phép người ngoài); G3 cho Quản lý nếu người đó còn việc dở. */
    public void memberRemoved(Project project, UUID userId, UUID actorId) {
        if (project == null) return;
        publisher.event(PROJECT_MEMBER_REMOVED, project).projectEntity().actor(actorId)
                .to(userId, TARGET_USER)
                .allowNonMembers()
                .publish();

        List<String> openCodes = openItemCodes(project.getId(), userId);
        if (!openCodes.isEmpty()) {
            publisher.event(MEMBER_REMOVED_OPEN_ITEMS, project).projectEntity().actor(actorId)
                    .toAll(publisher.recipients().managers(project), PROJECT_MANAGER)
                    .data("memberId", userId)
                    .data("codes", openCodes)
                    .publish();
        }
    }

    /** G7: Trưởng dự án mới và cũ. */
    public void leadChanged(Project project, UUID oldLead, UUID newLead, UUID actorId) {
        if (project == null) return;
        if (Objects.equals(oldLead, newLead)) return;
        publisher.event(PROJECT_LEAD_CHANGED, project).projectEntity().actor(actorId)
                .to(newLead, TARGET_USER)
                .to(oldLead, PREVIOUS_ASSIGNEE)
                .data("newLeadId", newLead)
                .data("oldLeadId", oldLead)
                .allowNonMembers()
                .publish();
    }

    // ─────────────────────── H. Dự án ───────────────────────

    /** H1: dự án vừa bị xoá mềm nên phải cho phép phát. */
    public void projectArchived(Project project, UUID actorId) {
        if (project == null) return;
        publisher.event(PROJECT_ARCHIVED, project).projectEntity().actor(actorId)
                .toAll(publisher.recipients().members(project), MEMBER)
                .allowDeletedProject()
                .publish();
    }

    /** H2. */
    public void projectRestored(Project project, UUID actorId) {
        if (project == null) return;
        publisher.event(PROJECT_RESTORED, project).projectEntity().actor(actorId)
                .toAll(publisher.recipients().members(project), MEMBER)
                .publish();
    }

    /** H3: ưu tiên báo đổi ngày kết thúc. */
    public void scheduleChanged(Project project, LocalDate oldStart, LocalDate oldEnd, UUID actorId) {
        if (project == null) return;
        boolean endChanged = !Objects.equals(oldEnd, project.getEndDate());
        boolean startChanged = !Objects.equals(oldStart, project.getStartDate());
        if (!endChanged && !startChanged) return;
        publisher.event(PROJECT_SCHEDULE_CHANGED, project).projectEntity().actor(actorId)
                .toAll(publisher.recipients().members(project), MEMBER)
                .data("endChanged", endChanged)
                .data("newEndDate", project.getEndDate())
                .data("newStartDate", project.getStartDate())
                .publish();
    }

    // ─────────────────────── I. Đóng góp ───────────────────────

    /** I1. */
    public void contributionAdjusted(Project project, UUID userId, BigDecimal points, String reason, UUID actorId) {
        if (project == null) return;
        publisher.event(CONTRIBUTION_ADJUSTED, project).projectEntity().actor(actorId)
                .to(userId, TARGET_USER)
                .data("points", points)
                .data("reason", reason)
                .publish();
    }

    /** I2: mỗi thành viên thấy số điểm được ghi nhận của mình. */
    public void contributionFrozen(Project project, Sprint sprint, Map<UUID, BigDecimal> pointsByUser) {
        if (project == null) return;
        publisher.event(CONTRIBUTION_FROZEN, project).sprint(sprint)
                .toAll(publisher.recipients().members(project), MEMBER)
                .data("points", pointsByUser)
                .publish();
    }

    // ─────────────────────── tiện ích ───────────────────────

    List<String> openItemCodes(UUID projectId, UUID userId) {
        return jdbc.queryForList("""
                SELECT w.code FROM work_items w
                JOIN statuses s ON s.id = w.status_id
                WHERE w.project_id = :p AND w.assignee_id = :u AND w.deleted_at IS NULL AND s.category <> 'DONE'
                ORDER BY w.created_at
                """, Map.of("p", projectId, "u", userId), String.class);
    }

    static Map<UUID, Integer> countByAssignee(Collection<WorkItem> items) {
        Map<UUID, Integer> counts = new LinkedHashMap<>();
        for (WorkItem w : items) {
            if (w.getAssigneeId() != null) counts.merge(w.getAssigneeId(), 1, Integer::sum);
        }
        return counts;
    }

    static Map<UUID, List<String>> codesByAssignee(Collection<WorkItem> items) {
        Map<UUID, List<String>> map = new LinkedHashMap<>();
        for (WorkItem w : items) {
            if (w.getAssigneeId() != null) map.computeIfAbsent(w.getAssigneeId(), k -> new ArrayList<>()).add(w.getCode());
        }
        return map;
    }

    private static List<UUID> assignees(Collection<WorkItem> items) {
        return items.stream().map(WorkItem::getAssigneeId).filter(Objects::nonNull).distinct().toList();
    }

    private static LocalDate toDate(LocalDateTime t) {
        return t == null ? null : t.toLocalDate();
    }

    private static String normalizeRole(String role) {
        return role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
    }

    /** ADMINISTRATOR > MEMBER (và vai trò tuỳ chỉnh) > VIEWER. */
    static int roleRank(String role) {
        return switch (normalizeRole(role)) {
            case "ADMINISTRATOR" -> 3;
            case "VIEWER" -> 1;
            default -> 2;
        };
    }
}
