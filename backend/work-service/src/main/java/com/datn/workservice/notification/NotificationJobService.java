package com.datn.workservice.notification;

import com.datn.workservice.entity.Project;
import com.datn.workservice.entity.Sprint;
import com.datn.workservice.entity.WorkItem;
import com.datn.workservice.enums.SprintState;
import com.datn.workservice.repository.ProjectRepository;
import com.datn.workservice.service.SprintSequence;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.datn.common.notification.NotificationType.*;
import static com.datn.common.notification.RecipientReason.*;

/**
 * Các job thông báo theo lịch (08:00 giờ Việt Nam, xem {@link NotificationScheduler}):
 * <ul>
 *   <li>Nhắc hạn: F1 sắp đến hạn, F2 đến hạn hôm nay, F3 quá hạn (ngày 1, 3, 7), F5 hôm nay bắt đầu, F6 lâu không cập nhật.</li>
 *   <li>Chu kỳ: E3 quá hạn (ngày 1, 3, 7), E4 kết thúc ngày mai, E6 đến ngày bắt đầu chu kỳ tiếp theo (BR-S15).</li>
 *   <li>C4: yêu cầu xác nhận hoàn thành chờ quá 48 giờ.</li>
 *   <li>Tổng hợp cho Quản lý: F4 hằng ngày, F7 dự án sắp kết thúc, F8 dự án quá hạn.</li>
 * </ul>
 * Mỗi sự kiện có {@code dedupKey} riêng nên chạy lại bao nhiêu lần cũng không sinh trùng (R6).
 * Bỏ qua dự án mẫu, dự án / công việc đã xoá, việc đã xong và việc đang chờ xác nhận hoàn thành (R7, R11).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationJobService {

    static final Set<Long> REPEAT_DAYS = Set.of(1L, 3L, 7L);
    static final int STALE_DAYS = 5;
    static final int PENDING_APPROVAL_HOURS = 48;
    static final int PROJECT_ENDING_DAYS = 7;

    private final NamedParameterJdbcTemplate jdbc;
    private final ProjectRepository projectRepository;
    private final NotificationEventPublisher publisher;

    // ───────────────────────────── F1, F2, F3, F5, F6 ─────────────────────────────

    @Transactional
    public int runDueDateReminders(LocalDate today, UUID onlyProjectId) {
        String sql = """
                SELECT w.id, w.project_id, w.code, w.summary, w.assignee_id, w.due_date, w.start_date, w.priority,
                       w.updated_at, s.category, s.name AS status_name
                FROM work_items w
                JOIN statuses s ON s.id = w.status_id
                JOIN projects p ON p.id = w.project_id
                WHERE w.deleted_at IS NULL AND p.deleted_at IS NULL AND p.is_template = FALSE
                  AND w.assignee_id IS NOT NULL AND s.category <> 'DONE'
                  AND NOT EXISTS (SELECT 1 FROM work_item_completion_requests r
                                  WHERE r.work_item_id = w.id AND r.status = 'PENDING')
                  AND ( w.due_date BETWEEN :minDue AND :maxDue
                        OR w.start_date = :today
                        OR (s.category = 'IN_PROGRESS' AND w.updated_at < :staleBefore) )
                """ + onlyProject("p.id", onlyProjectId);
        Map<String, Object> params = withProject(Map.of(
                "minDue", today.minusDays(7), "maxDue", today.plusDays(3), "today", today,
                "staleBefore", today.minusDays(STALE_DAYS).atStartOfDay()), onlyProjectId);
        List<ItemRow> rows = jdbc.query(sql, params, NotificationJobService::itemRow);
        Map<UUID, Project> projects = loadProjects(rows.stream().map(r -> r.projectId).collect(Collectors.toSet()));

        int sent = 0;
        for (ItemRow r : rows) {
            Project p = projects.get(r.projectId);
            if (p == null) continue;
            WorkItem item = r.toItem();

            if (r.dueDate != null) {
                long daysLeft = ChronoUnit.DAYS.between(today, r.dueDate);
                boolean high = "HIGH".equals(r.priority) || "HIGHEST".equals(r.priority);
                if (daysLeft == 1 || (daysLeft == 3 && high)) {
                    sent += send(publisher.event(TASK_DUE_SOON, p).item(item).to(r.assigneeId, ASSIGNEE)
                            .data("dueDate", r.dueDate).data("daysLeft", daysLeft)
                            .dedupKey("TASK_DUE_SOON:" + r.id + ":" + r.dueDate + ":" + daysLeft));
                } else if (daysLeft == 0) {
                    sent += send(publisher.event(TASK_DUE_TODAY, p).item(item).to(r.assigneeId, ASSIGNEE)
                            .data("dueDate", r.dueDate)
                            .dedupKey("TASK_DUE_TODAY:" + r.id + ":" + r.dueDate));
                } else if (daysLeft < 0 && REPEAT_DAYS.contains(-daysLeft)) {
                    sent += send(publisher.event(TASK_OVERDUE, p).item(item).to(r.assigneeId, ASSIGNEE)
                            .data("dueDate", r.dueDate).data("daysOverdue", -daysLeft)
                            .dedupKey("TASK_OVERDUE:" + r.id + ":" + r.dueDate + ":" + (-daysLeft)));
                }
            }
            if (today.equals(r.startDate) && "TODO".equals(r.category)) {
                sent += send(publisher.event(TASK_START_TODAY, p).item(item).to(r.assigneeId, ASSIGNEE)
                        .data("startDate", r.startDate)
                        .dedupKey("TASK_START_TODAY:" + r.id + ":" + r.startDate));
            }
            if ("IN_PROGRESS".equals(r.category) && r.updatedAt != null) {
                long idle = ChronoUnit.DAYS.between(r.updatedAt.toLocalDate(), today);
                if (idle >= STALE_DAYS) {
                    // Khoá theo lần cập nhật cuối: chỉ nhắc một lần cho mỗi đợt "đứng yên"
                    sent += send(publisher.event(TASK_STALE, p).item(item).to(r.assigneeId, ASSIGNEE)
                            .data("days", idle).data("status", r.statusName)
                            .dedupKey("TASK_STALE:" + r.id + ":" + r.updatedAt.toLocalDate()));
                }
            }
        }
        return sent;
    }

    // ───────────────────────────── E3, E4, E6 ─────────────────────────────

    @Transactional
    public int runSprintDeadlines(LocalDate today, UUID onlyProjectId) {
        List<Sprint> sprints = jdbc.query("""
                SELECT s.id, s.project_id, s.name, s.state, s.start_date, s.end_date, s.created_at
                FROM sprints s JOIN projects p ON p.id = s.project_id
                WHERE p.deleted_at IS NULL AND p.is_template = FALSE AND s.state IN ('ACTIVE', 'FUTURE')
                """ + onlyProject("p.id", onlyProjectId), withProject(Map.of(), onlyProjectId), (rs, i) -> Sprint.builder()
                .id(rs.getObject("id", UUID.class))
                .projectId(rs.getObject("project_id", UUID.class))
                .name(rs.getString("name"))
                .state(SprintState.valueOf(rs.getString("state")))
                .startDate(ldt(rs, "start_date"))
                .endDate(ldt(rs, "end_date"))
                .createdAt(ldt(rs, "created_at"))
                .build());
        Map<UUID, List<Sprint>> byProject = sprints.stream().collect(Collectors.groupingBy(Sprint::getProjectId));
        Map<UUID, Project> projects = loadProjects(byProject.keySet());

        int sent = 0;
        for (Map.Entry<UUID, List<Sprint>> e : byProject.entrySet()) {
            Project p = projects.get(e.getKey());
            if (p == null) continue;
            for (Sprint s : e.getValue()) {
                if (s.getState() != SprintState.ACTIVE || s.getEndDate() == null) continue;
                long daysOverdue = ChronoUnit.DAYS.between(s.getEndDate().toLocalDate(), today);
                if (REPEAT_DAYS.contains(daysOverdue)) {
                    sent += send(publisher.event(SPRINT_OVERDUE, p).sprint(s)
                            .toAll(publisher.recipients().managers(p), PROJECT_MANAGER)
                            .data("daysOverdue", daysOverdue)
                            .dedupKey("SPRINT_OVERDUE:" + s.getId() + ":" + s.getEndDate().toLocalDate() + ":" + daysOverdue));
                } else if (daysOverdue == -1) {
                    Map<UUID, Integer> openCounts = openCountsInSprint(s.getId());
                    sent += send(publisher.event(SPRINT_ENDING_SOON, p).sprint(s)
                            .toAll(publisher.recipients().managers(p), PROJECT_MANAGER)
                            .toAll(openCounts.keySet(), ASSIGNEE)
                            .data("endDate", s.getEndDate().toLocalDate())
                            .data("openCounts", openCounts)
                            .dedupKey("SPRINT_ENDING_SOON:" + s.getId() + ":" + s.getEndDate().toLocalDate()));
                }
            }
            UUID startable = SprintSequence.startableSprintId(e.getValue());
            if (startable != null) {
                Sprint next = e.getValue().stream().filter(s -> s.getId().equals(startable)).findFirst().orElse(null);
                if (next != null && next.getStartDate() != null && !next.getStartDate().toLocalDate().isAfter(today)) {
                    sent += send(publisher.event(NEXT_SPRINT_DUE, p).sprint(next)
                            .toAll(publisher.recipients().managers(p), PROJECT_MANAGER)
                            .data("plannedStart", next.getStartDate().toLocalDate())
                            .dedupKey("NEXT_SPRINT_DUE:" + next.getId() + ":" + next.getStartDate().toLocalDate()));
                }
            }
        }
        return sent;
    }

    private Map<UUID, Integer> openCountsInSprint(UUID sprintId) {
        Map<UUID, Integer> counts = new LinkedHashMap<>();
        jdbc.query("""
                SELECT w.assignee_id, COUNT(*) AS n FROM work_items w JOIN statuses s ON s.id = w.status_id
                WHERE w.sprint_id = :s AND w.deleted_at IS NULL AND w.assignee_id IS NOT NULL AND s.category <> 'DONE'
                GROUP BY w.assignee_id
                """, Map.of("s", sprintId), rs -> {
            counts.put(rs.getObject("assignee_id", UUID.class), rs.getInt("n"));
        });
        return counts;
    }

    // ───────────────────────────── C4 ─────────────────────────────

    @Transactional
    public int runCompletionReminders(LocalDateTime now, UUID onlyProjectId) {
        Map<UUID, List<String>> codesByProject = new LinkedHashMap<>();
        jdbc.query("""
                SELECT r.project_id, w.code FROM work_item_completion_requests r
                JOIN work_items w ON w.id = r.work_item_id
                JOIN projects p ON p.id = r.project_id
                WHERE r.status = 'PENDING' AND r.requested_at < :before
                  AND w.deleted_at IS NULL AND p.deleted_at IS NULL AND p.is_template = FALSE
                """ + onlyProject("p.id", onlyProjectId) + " ORDER BY r.requested_at",
                withProject(Map.of("before", now.minusHours(PENDING_APPROVAL_HOURS)), onlyProjectId), rs -> {
            codesByProject.computeIfAbsent(rs.getObject("project_id", UUID.class), k -> new ArrayList<>())
                    .add(rs.getString("code"));
        });
        Map<UUID, Project> projects = loadProjects(codesByProject.keySet());
        int sent = 0;
        for (Map.Entry<UUID, List<String>> e : codesByProject.entrySet()) {
            Project p = projects.get(e.getKey());
            if (p == null) continue;
            sent += send(publisher.event(COMPLETION_PENDING_REMINDER, p).projectEntity()
                    .toAll(publisher.recipients().managers(p), PROJECT_MANAGER)
                    .data("count", e.getValue().size())
                    .data("codes", e.getValue())
                    .dedupKey("COMPLETION_PENDING_REMINDER:" + p.getId() + ":" + now.toLocalDate()));
        }
        return sent;
    }

    // ───────────────────────────── F4, F7, F8 ─────────────────────────────

    @Transactional
    public int runProjectDigest(LocalDate today, UUID onlyProjectId) {
        List<DigestRow> rows = jdbc.query("""
                SELECT p.id, p.end_date,
                  (SELECT COUNT(*) FROM work_items w JOIN statuses s ON s.id = w.status_id
                    WHERE w.project_id = p.id AND w.deleted_at IS NULL AND s.category <> 'DONE' AND w.due_date < :today) AS overdue,
                  (SELECT COUNT(*) FROM work_items w JOIN statuses s ON s.id = w.status_id
                    WHERE w.project_id = p.id AND w.deleted_at IS NULL AND s.category <> 'DONE' AND w.due_date = :today) AS due_today,
                  (SELECT COUNT(*) FROM work_item_completion_requests r
                    WHERE r.project_id = p.id AND r.status = 'PENDING') AS pending,
                  (SELECT COUNT(*) FROM work_items w JOIN sprints sp ON sp.id = w.sprint_id JOIN statuses s ON s.id = w.status_id
                    WHERE w.project_id = p.id AND w.deleted_at IS NULL AND sp.state = 'ACTIVE'
                      AND w.assignee_id IS NULL AND s.category <> 'DONE') AS unassigned,
                  (SELECT COUNT(*) FROM work_items w JOIN statuses s ON s.id = w.status_id
                    WHERE w.project_id = p.id AND w.deleted_at IS NULL AND s.category <> 'DONE') AS open_count
                FROM projects p
                WHERE p.deleted_at IS NULL AND p.is_template = FALSE
                """ + onlyProject("p.id", onlyProjectId), withProject(Map.of("today", today), onlyProjectId), (rs, i) -> new DigestRow(
                rs.getObject("id", UUID.class), rs.getObject("end_date", LocalDate.class),
                rs.getLong("overdue"), rs.getLong("due_today"), rs.getLong("pending"),
                rs.getLong("unassigned"), rs.getLong("open_count")));
        Map<UUID, Project> projects = loadProjects(rows.stream().map(DigestRow::projectId).collect(Collectors.toSet()));

        int sent = 0;
        for (DigestRow r : rows) {
            Project p = projects.get(r.projectId());
            if (p == null) continue;
            if (r.overdue() + r.dueToday() + r.pending() + r.unassigned() > 0) {
                sent += send(publisher.event(PROJECT_DAILY_DIGEST, p).projectEntity()
                        .toAll(publisher.recipients().managers(p), PROJECT_MANAGER)
                        .data("overdue", r.overdue()).data("dueToday", r.dueToday())
                        .data("pendingApprovals", r.pending()).data("unassigned", r.unassigned())
                        .dedupKey("PROJECT_DAILY_DIGEST:" + p.getId() + ":" + today));
            }
            if (r.endDate() != null && r.openCount() > 0) {
                long daysLeft = ChronoUnit.DAYS.between(today, r.endDate());
                if (daysLeft == PROJECT_ENDING_DAYS) {
                    sent += send(publisher.event(PROJECT_ENDING_SOON, p).projectEntity()
                            .toAll(publisher.recipients().managers(p), PROJECT_MANAGER)
                            .data("endDate", r.endDate()).data("daysLeft", daysLeft).data("openCount", r.openCount())
                            .dedupKey("PROJECT_ENDING_SOON:" + p.getId() + ":" + r.endDate()));
                } else if (daysLeft < 0 && REPEAT_DAYS.contains(-daysLeft)) {
                    sent += send(publisher.event(PROJECT_OVERDUE, p).projectEntity()
                            .toAll(publisher.recipients().managers(p), PROJECT_MANAGER)
                            .data("daysOverdue", -daysLeft).data("openCount", r.openCount())
                            .dedupKey("PROJECT_OVERDUE:" + p.getId() + ":" + r.endDate() + ":" + (-daysLeft)));
                }
            }
        }
        return sent;
    }

    // ───────────────────────────── tiện ích ─────────────────────────────

    /** Chạy cả 4 job, trả về số sự kiện đã ghi theo từng job. */
    public Map<String, Integer> runAll(LocalDate today, LocalDateTime now, UUID onlyProjectId) {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("due-reminders", runDueDateReminders(today, onlyProjectId));
        result.put("sprint-deadlines", runSprintDeadlines(today, onlyProjectId));
        result.put("completion-reminders", runCompletionReminders(now, onlyProjectId));
        result.put("project-digest", runProjectDigest(today, onlyProjectId));
        return result;
    }

    /** Giới hạn job trong một dự án (endpoint admin, chạy thử); null = mọi dự án. */
    private static String onlyProject(String column, UUID onlyProjectId) {
        return onlyProjectId == null ? "" : " AND " + column + " = :onlyProjectId";
    }

    private static Map<String, Object> withProject(Map<String, Object> params, UUID onlyProjectId) {
        if (onlyProjectId == null) return params;
        Map<String, Object> all = new HashMap<>(params);
        all.put("onlyProjectId", onlyProjectId);
        return all;
    }

    private Map<UUID, Project> loadProjects(Set<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<UUID, Project> map = new HashMap<>();
        projectRepository.findAllById(ids).forEach(p -> map.put(p.getId(), p));
        return map;
    }

    /**
     * Đọc cột thời gian thành LocalDateTime theo múi giờ JVM (giống JPA). Dùng getTimestamp vì driver PostgreSQL
     * không cho đọc TIMESTAMPTZ thẳng thành LocalDateTime, và DB trộn cả timestamp lẫn timestamptz.
     */
    static LocalDateTime ldt(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toLocalDateTime();
    }

    private static int send(NotificationEventPublisher.Draft draft) {
        return draft.publish() ? 1 : 0;
    }

    private static ItemRow itemRow(ResultSet rs, int i) throws SQLException {
        return new ItemRow(
                rs.getObject("id", UUID.class), rs.getObject("project_id", UUID.class),
                rs.getString("code"), rs.getString("summary"), rs.getObject("assignee_id", UUID.class),
                rs.getObject("due_date", LocalDate.class), rs.getObject("start_date", LocalDate.class),
                rs.getString("priority"), ldt(rs, "updated_at"),
                rs.getString("category"), rs.getString("status_name"));
    }

    record ItemRow(UUID id, UUID projectId, String code, String summary, UUID assigneeId, LocalDate dueDate,
                   LocalDate startDate, String priority, LocalDateTime updatedAt, String category, String statusName) {
        WorkItem toItem() {
            return WorkItem.builder().id(id).projectId(projectId).code(code).summary(summary).assigneeId(assigneeId).build();
        }
    }

    record DigestRow(UUID projectId, LocalDate endDate, long overdue, long dueToday, long pending,
                     long unassigned, long openCount) {}
}
