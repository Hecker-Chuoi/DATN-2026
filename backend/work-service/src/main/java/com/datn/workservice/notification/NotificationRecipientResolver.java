package com.datn.workservice.notification;

import com.datn.workservice.entity.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Chọn người nhận theo vai trò trong dự án. Dùng truy vấn cục bộ (không gọi workspace-service)
 * để không phát sinh lời gọi HTTP trong transaction nghiệp vụ.
 *
 * <ul>
 *   <li>Thành viên: có role_assignments trong dự án, cộng Trưởng dự án và người tạo dự án.</li>
 *   <li>Quản lý (QL): Trưởng dự án, người tạo dự án, vai trò ADMINISTRATOR hoặc vai trò có quyền PROJECT_ADMIN
 *       (cùng tập với WorkItemCompletionGate.canConfirmCompletion, trừ Global ADMIN không phải thành viên).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class NotificationRecipientResolver {

    private final NamedParameterJdbcTemplate jdbc;

    public Set<UUID> members(Project project) {
        Set<UUID> result = new LinkedHashSet<>(jdbc.queryForList(
                "SELECT DISTINCT user_id FROM role_assignments WHERE project_id = :p",
                Map.of("p", project.getId()), UUID.class));
        addIfPresent(result, project.getLeadUserId());
        addIfPresent(result, project.getCreatedBy());
        return result;
    }

    public Set<UUID> managers(Project project) {
        Set<UUID> result = new LinkedHashSet<>();
        addIfPresent(result, project.getLeadUserId());
        addIfPresent(result, project.getCreatedBy());
        result.addAll(jdbc.queryForList("""
                SELECT DISTINCT ra.user_id
                FROM role_assignments ra
                JOIN project_roles r ON r.id = ra.role_id
                WHERE ra.project_id = :p
                  AND (UPPER(r.name) = 'ADMINISTRATOR'
                       OR EXISTS (SELECT 1 FROM permission_scheme_entries e
                                  WHERE e.project_id = :p AND e.role_id = ra.role_id
                                    AND e.permission_key = 'PROJECT_ADMIN'))
                """, Map.of("p", project.getId()), UUID.class));
        return result;
    }

    public boolean isMember(Project project, UUID userId) {
        if (userId == null) return false;
        if (userId.equals(project.getLeadUserId()) || userId.equals(project.getCreatedBy())) return true;
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM role_assignments WHERE project_id = :p AND user_id = :u",
                Map.of("p", project.getId(), "u", userId), Integer.class);
        return n != null && n > 0;
    }

    /** Lọc danh sách, chỉ giữ thành viên (một truy vấn cho cả danh sách). */
    public Set<UUID> onlyMembers(Project project, Set<UUID> userIds) {
        if (userIds.isEmpty()) return userIds;
        Set<UUID> all = members(project);
        Set<UUID> kept = new LinkedHashSet<>();
        for (UUID u : userIds) if (all.contains(u)) kept.add(u);
        return kept;
    }

    private static void addIfPresent(Set<UUID> set, UUID id) {
        if (id != null) set.add(id);
    }

    static List<UUID> nonNull(List<UUID> ids) {
        return ids.stream().filter(Objects::nonNull).toList();
    }
}
