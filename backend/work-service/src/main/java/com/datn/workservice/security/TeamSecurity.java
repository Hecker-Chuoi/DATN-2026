package com.datn.workservice.security;

import com.datn.workservice.entity.Team;
import com.datn.workservice.entity.TeamMember;
import com.datn.workservice.enums.TeamRole;
import com.datn.workservice.repository.TeamMemberRepository;
import com.datn.workservice.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Kiểm tra quan hệ của người dùng hiện tại với một Team, dùng trong @PreAuthorize:
 * <pre>
 *   @PreAuthorize("@teamSecurity.isMember(#teamId)")
 *   @PreAuthorize("@teamSecurity.isLeader(#teamId)")
 *   @PreAuthorize("@teamSecurity.isOwner(#teamId)")
 * </pre>
 * Global ADMIN luôn được phép.
 */
@Component("teamSecurity")
@RequiredArgsConstructor
public class TeamSecurity {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final WorkspaceAccessChecker workspaceAccessChecker;

    public boolean isGlobalAdmin() {
        return SecurityUtils.isGlobalAdmin();
    }

    /** Kiểm tra xem user có phải là Workspace Owner của Workspace chứa team này không */
    public boolean isWorkspaceOwner(Team team, UUID userId) {
        if (team == null || team.getWorkspaceId() == null || userId == null) return false;
        try {
            String wsRole = workspaceAccessChecker.getRole(team.getWorkspaceId(), userId);
            return "OWNER".equalsIgnoreCase(wsRole);
        } catch (Exception e) {
            return false;
        }
    }

    /** Kiểm tra xem user có phải là Workspace Admin hoặc Workspace Owner không */
    public boolean isWorkspaceAdminOrOwner(Team team, UUID userId) {
        if (team == null || team.getWorkspaceId() == null || userId == null) return false;
        try {
            String wsRole = workspaceAccessChecker.getRole(team.getWorkspaceId(), userId);
            return "OWNER".equalsIgnoreCase(wsRole) || "ADMIN".equalsIgnoreCase(wsRole);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Thẩm quyền quản trị vòng đời nhóm (Chỉnh sửa thông tin, Xóa nhóm, Bổ nhiệm Trưởng nhóm):
     * Dành cho Global Admin, Workspace Owner, Workspace Admin, hoặc chính Team Leader.
     */
    public boolean canManageTeam(UUID teamId) {
        if (isGlobalAdmin()) return true;
        UUID userId = SecurityUtils.getCurrentUserIdOptional().orElse(null);
        if (teamId == null || userId == null) return false;

        Optional<Team> team = teamRepository.findById(teamId);
        if (team.isEmpty()) return false;

        if (isWorkspaceAdminOrOwner(team.get(), userId)) return true;
        return isLeader(teamId);
    }

    /** Trưởng nhóm = người tạo/active leader (teams.owner_id) hoặc thành viên có role LEADER, hoặc Workspace Owner. */
    public boolean isLeader(UUID teamId) {
        if (isGlobalAdmin()) return true;
        UUID userId = SecurityUtils.getCurrentUserIdOptional().orElse(null);
        if (teamId == null || userId == null) return false;

        Optional<Team> team = teamRepository.findById(teamId);
        if (team.isEmpty()) return false;

        // 1. Workspace Owner luôn có toàn quyền (Bóng ma Siêu Quản Trị)
        if (isWorkspaceOwner(team.get(), userId)) return true;

        // 2. Active Leader của nhóm (bắt buộc phải đang là thành viên hợp lệ của Workspace)
        if (userId.equals(team.get().getOwnerId())) {
            try {
                if (workspaceAccessChecker.getRole(team.get().getWorkspaceId(), userId) != null) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }

        // 3. Vai trò LEADER trong bảng team_members (phải đang thuộc Workspace)
        return teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .map(TeamMember::getRole)
                .map(role -> role == TeamRole.LEADER)
                .orElse(false)
                && isUserInWorkspace(team.get().getWorkspaceId(), userId);
    }

    public boolean isMember(UUID teamId) {
        if (isGlobalAdmin()) return true;
        UUID userId = SecurityUtils.getCurrentUserIdOptional().orElse(null);
        if (teamId == null || userId == null) return false;

        Optional<Team> team = teamRepository.findById(teamId);
        if (team.isEmpty()) return false;

        // Workspace Owner & Admin luôn có thể truy cập/xem nhóm
        if (isWorkspaceAdminOrOwner(team.get(), userId)) return true;

        // Người dùng ngoài Workspace không bao giờ là thành viên của nhóm
        if (!isUserInWorkspace(team.get().getWorkspaceId(), userId)) {
            return false;
        }

        if (userId.equals(team.get().getOwnerId())) return true;

        return teamMemberRepository.existsByTeamIdAndUserId(teamId, userId);
    }

    private boolean isUserInWorkspace(UUID workspaceId, UUID userId) {
        if (workspaceId == null || userId == null) return false;
        try {
            return workspaceAccessChecker.getRole(workspaceId, userId) != null;
        } catch (Exception e) {
            return false;
        }
    }
}
