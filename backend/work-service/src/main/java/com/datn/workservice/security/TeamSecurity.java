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

    public boolean isGlobalAdmin() {
        return SecurityUtils.isGlobalAdmin();
    }

    public boolean isOwner(UUID teamId) {
        if (isGlobalAdmin()) return true;
        UUID userId = SecurityUtils.getCurrentUserIdOptional().orElse(null);
        if (teamId == null || userId == null) return false;
        return teamRepository.findById(teamId)
                .map(team -> userId.equals(team.getOwnerId()))
                .orElse(false);
    }

    public boolean isLeader(UUID teamId) {
        if (isGlobalAdmin()) return true;
        UUID userId = SecurityUtils.getCurrentUserIdOptional().orElse(null);
        if (teamId == null || userId == null) return false;

        Optional<Team> team = teamRepository.findById(teamId);
        if (team.isEmpty()) return false;
        if (userId.equals(team.get().getOwnerId())) return true;

        return teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .map(TeamMember::getRole)
                .map(role -> role == TeamRole.LEADER)
                .orElse(false);
    }

    public boolean isMember(UUID teamId) {
        if (isGlobalAdmin()) return true;
        UUID userId = SecurityUtils.getCurrentUserIdOptional().orElse(null);
        if (teamId == null || userId == null) return false;

        Optional<Team> team = teamRepository.findById(teamId);
        if (team.isEmpty()) return false;
        if (userId.equals(team.get().getOwnerId())) return true;

        return teamMemberRepository.existsByTeamIdAndUserId(teamId, userId);
    }
}
