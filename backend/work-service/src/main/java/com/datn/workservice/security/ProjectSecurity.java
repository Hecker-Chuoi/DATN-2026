package com.datn.workservice.security;

import com.datn.workservice.entity.Project;
import com.datn.workservice.entity.Task;
import com.datn.workservice.entity.TeamMember;
import com.datn.workservice.entity.Team;
import com.datn.workservice.repository.ProjectRepository;
import com.datn.workservice.repository.RolePermissionRepository;
import com.datn.workservice.repository.TaskRepository;
import com.datn.workservice.repository.TeamMemberRepository;
import com.datn.workservice.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component("projectSecurity")
@RequiredArgsConstructor
public class ProjectSecurity {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final RolePermissionRepository rolePermissionRepository;

    /**
     * Kiểm tra người dùng hiện tại có quyền thực hiện hành động trên Project cụ thể hay không.
     */
    public boolean hasProjectPermission(UUID projectId, String permissionCode) {
        if (projectId == null || permissionCode == null) return false;

        UUID currentUserId = extractUserId();
        if (currentUserId == null) {
            log.warn("ProjectSecurity: Không thể xác định currentUserId từ SecurityContext");
            return false;
        }

        // 1. Tầng 1: Global Admin Bypass (Kiểm tra role ADMIN từ JWT realm_access)
        if (hasGlobalRole("ADMIN")) {
            log.info("ProjectSecurity: User {} là Global ADMIN -> Bypass check", currentUserId);
            return true;
        }

        // 2. Lấy thông tin dự án từ DB
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            log.warn("ProjectSecurity: Không tìm thấy Project có ID {}", projectId);
            return false;
        }

        // 3. Tầng 2 - ABAC Check: Người tạo dự án (CreatedBy) có toàn quyền trên dự án đó
        if (currentUserId.equals(project.getCreatedBy())) {
            return true;
        }

        // 4. Tầng 2 - ReBAC Check: Kiểm tra quan hệ thành viên trong dự án/team
        if (project.getTeamId() == null) {
            return false;
        }

        TeamMember member = teamMemberRepository.findByIdTeamIdAndIdUserId(project.getTeamId(), currentUserId).orElse(null);
        if (member == null) {
            log.debug("ProjectSecurity: User {} không phải thành viên team {} thuộc project {}", currentUserId, project.getTeamId(), projectId);
            return false;
        }

        // 5. Tầng 2 - Local RBAC Check: Tra cứu vai trò dự án (role_in_team) với permissionCode trong DB
        boolean hasPerm = rolePermissionRepository.existsByRoleNameIgnoreCaseAndPermissionCodeIgnoreCase(member.getRoleInTeam(), permissionCode);
        if (!hasPerm) {
            log.debug("ProjectSecurity: User {} với vai trò {} không có quyền {} trên project {}",
                    currentUserId, member.getRoleInTeam(), permissionCode, projectId);
        }
        return hasPerm;
    }

    /**
     * Kiểm tra quyền tác động lên Task thông qua Project chứa Task đó.
     */
    public boolean hasTaskPermission(UUID taskId, String permissionCode) {
        if (taskId == null || permissionCode == null) return false;

        Task task = taskRepository.findById(taskId).orElse(null);
        if (task == null || task.getProjectId() == null) {
            log.warn("ProjectSecurity: Không tìm thấy Task hoặc Task không thuộc Project nào. Task ID: {}", taskId);
            return false;
        }

        return hasProjectPermission(task.getProjectId(), permissionCode);
    }

    /**
     * Kiểm tra quyền tác động lên Team (Mời/Xóa thành viên, đổi role...).
     */
    public boolean hasTeamPermission(UUID teamId, String permissionCode) {
        if (teamId == null || permissionCode == null) return false;

        UUID currentUserId = extractUserId();
        if (currentUserId == null) return false;

        // 1. Global Admin Bypass
        if (hasGlobalRole("ADMIN")) return true;

        // 2. ABAC Check: Team Owner có quyền thực hiện
        Team team = teamRepository.findById(teamId).orElse(null);
        if (team == null) return false;
        if (currentUserId.equals(team.getOwnerId())) return true;

        // 3. ReBAC & Local RBAC Check
        TeamMember member = teamMemberRepository.findByIdTeamIdAndIdUserId(teamId, currentUserId).orElse(null);
        if (member == null) return false;

        return rolePermissionRepository.existsByRoleNameIgnoreCaseAndPermissionCodeIgnoreCase(member.getRoleInTeam(), permissionCode);
    }

    /**
     * Lấy User ID (UUID) từ claim "sub" trong JWT Authentication Token.
     */
    private UUID extractUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtToken) {
            String sub = jwtToken.getToken().getSubject();
            if (sub != null && !sub.isBlank()) {
                try {
                    return UUID.fromString(sub);
                } catch (IllegalArgumentException e) {
                    log.error("ProjectSecurity: Sub claim không phải là UUID hợp lệ: {}", sub);
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Kiểm tra role toàn cục từ JWT (realm_access.roles) hoặc từ GrantedAuthorities.
     */
    @SuppressWarnings("unchecked")
    private boolean hasGlobalRole(String roleName) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return false;

        // Check GrantedAuthorities
        boolean inAuthorities = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equalsIgnoreCase(roleName) || a.getAuthority().equalsIgnoreCase("ROLE_" + roleName));
        if (inAuthorities) return true;

        // Check trực tiếp claim realm_access.roles trong JWT Payload
        if (authentication instanceof JwtAuthenticationToken jwtToken) {
            Map<String, Object> realmAccess = jwtToken.getToken().getClaimAsMap("realm_access");
            if (realmAccess != null && realmAccess.get("roles") instanceof List<?> roles) {
                return roles.stream().anyMatch(r -> roleName.equalsIgnoreCase(String.valueOf(r)));
            }
        }

        return false;
    }
}
