package com.datn.workservice.service.impl;

import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workservice.client.IdentityClient;
import com.datn.workservice.client.dto.UserResponse;
import com.datn.workservice.dto.response.InvitationResponse;
import com.datn.workservice.entity.Invitation;
import com.datn.workservice.entity.Project;
import com.datn.workservice.entity.ProjectRole;
import com.datn.workservice.entity.RoleAssignment;
import com.datn.workservice.entity.Team;
import com.datn.workservice.entity.TeamMember;
import com.datn.workservice.enums.InvitationStatus;
import com.datn.workservice.enums.InvitationType;
import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.enums.TeamRole;
import com.datn.workservice.repository.InvitationRepository;
import com.datn.workservice.repository.ProjectRepository;
import com.datn.workservice.repository.ProjectRoleRepository;
import com.datn.workservice.repository.RoleAssignmentRepository;
import com.datn.workservice.repository.TeamMemberRepository;
import com.datn.workservice.repository.TeamRepository;
import com.datn.workservice.security.TeamSecurity;
import com.datn.workservice.security.WorkspaceAccessChecker;
import com.datn.workservice.service.InvitationMailer;
import com.datn.workservice.service.InvitationService;
import com.datn.workservice.service.ProjectMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvitationServiceImpl implements InvitationService {

    private final InvitationRepository invitationRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final ProjectRepository projectRepository;
    private final ProjectRoleRepository projectRoleRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final ProjectMemberService projectMemberService;
    private final TeamSecurity teamSecurity;
    private final WorkspaceAccessChecker workspaceAccessChecker;
    private final IdentityClient identityClient;
    private final InvitationMailer mailer;

    @Override
    @Transactional
    public InvitationResponse inviteToTeam(UUID teamId, UUID inviteeUserId, TeamRole role, UUID invitedBy) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm: " + teamId));
        UUID invitee = projectMemberService.canonicalUserId(inviteeUserId);

        // Ràng buộc Workspace: Chỉ có thể mời người dùng đang là thành viên của cùng Workspace
        if (team.getWorkspaceId() != null) {
            try {
                workspaceAccessChecker.requireMember(team.getWorkspaceId(), invitee);
            } catch (Exception e) {
                throw new BadRequestException("Chỉ có thể mời người dùng đang là thành viên của Không gian làm việc (Workspace) này vào nhóm");
            }
        }

        if (invitee.equals(team.getOwnerId()) || teamMemberRepository.existsByTeamIdAndUserId(teamId, invitee)) {
            throw new BadRequestException("Người dùng đã là thành viên của nhóm");
        }
        assertNoPending(InvitationType.TEAM, teamId, invitee);
        Invitation saved = invitationRepository.save(Invitation.builder()
                .type(InvitationType.TEAM)
                .targetId(teamId)
                .inviteeUserId(invitee)
                .invitedBy(invitedBy)
                .teamRole(role == null ? TeamRole.MEMBER : role)
                .build());
        log.info("Team invitation created: team={}, invitee={}, by={}", teamId, invitee, invitedBy);
        InvitationResponse res = toResponse(saved);
        mailer.send(res);
        return res;
    }

    @Override
    @Transactional
    public InvitationResponse inviteToProject(UUID projectId, UUID inviteeUserId, UUID roleId, UUID invitedBy) {
        projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dự án: " + projectId));
        ProjectRole role = projectRoleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vai trò: " + roleId));
        if (!projectId.equals(role.getProjectId())) {
            throw new BadRequestException("Vai trò không thuộc dự án này");
        }
        UUID invitee = projectMemberService.canonicalUserId(inviteeUserId);
        if (!roleAssignmentRepository.findByProjectIdAndUserId(projectId, invitee).isEmpty()) {
            throw new BadRequestException("Người dùng đã là thành viên của dự án");
        }
        assertNoPending(InvitationType.PROJECT, projectId, invitee);
        Invitation saved = invitationRepository.save(Invitation.builder()
                .type(InvitationType.PROJECT)
                .targetId(projectId)
                .inviteeUserId(invitee)
                .invitedBy(invitedBy)
                .projectRoleId(roleId)
                .build());
        log.info("Project invitation created: project={}, invitee={}, by={}", projectId, invitee, invitedBy);
        InvitationResponse res = toResponse(saved);
        mailer.send(res);
        return res;
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvitationResponse> listMine(UUID userId) {
        return invitationRepository.findByInviteeUserIdAndStatusOrderByCreatedAtDesc(userId, InvitationStatus.PENDING)
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvitationResponse> listForTarget(InvitationType type, UUID targetId) {
        return invitationRepository.findByTypeAndTargetIdAndStatusOrderByCreatedAtDesc(type, targetId, InvitationStatus.PENDING)
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public InvitationResponse accept(UUID invitationId, UUID userId) {
        Invitation inv = requirePendingForInvitee(invitationId, userId);
        if (inv.getType() == InvitationType.TEAM) {
            TeamRole role = inv.getTeamRole() == null ? TeamRole.MEMBER : inv.getTeamRole();
            if (!teamMemberRepository.existsByTeamIdAndUserId(inv.getTargetId(), userId)) {
                teamMemberRepository.save(TeamMember.builder()
                        .teamId(inv.getTargetId()).userId(userId).role(role).build());
            }
            projectMemberService.syncTeamMemberToProjects(inv.getTargetId(), userId, role);
        } else {
            ProjectRole role = projectRoleRepository.findById(inv.getProjectRoleId())
                    .orElseThrow(() -> new BadRequestException("Vai trò trong lời mời không còn tồn tại"));
            if (roleAssignmentRepository.findByProjectIdAndUserId(inv.getTargetId(), userId).isEmpty()) {
                roleAssignmentRepository.save(RoleAssignment.builder()
                        .projectId(inv.getTargetId()).userId(userId).roleId(role.getId()).build());
            }
        }
        inv.setStatus(InvitationStatus.ACCEPTED);
        inv.setRespondedAt(LocalDateTime.now());
        log.info("Invitation {} accepted by {}", invitationId, userId);
        return toResponse(invitationRepository.save(inv));
    }

    @Override
    @Transactional
    public InvitationResponse decline(UUID invitationId, UUID userId) {
        Invitation inv = requirePendingForInvitee(invitationId, userId);
        inv.setStatus(InvitationStatus.DECLINED);
        inv.setRespondedAt(LocalDateTime.now());
        return toResponse(invitationRepository.save(inv));
    }

    @Override
    @Transactional
    public InvitationResponse cancel(UUID invitationId, UUID userId, boolean globalAdmin) {
        Invitation inv = requirePending(invitationId);
        boolean allowed = globalAdmin
                || userId.equals(inv.getInvitedBy())
                || (inv.getType() == InvitationType.TEAM && teamSecurity.canManageTeam(inv.getTargetId()))
                || (inv.getType() == InvitationType.PROJECT
                    && projectMemberService.hasPermission(inv.getTargetId(), userId, PermissionKey.PROJECT_ADMIN));
        if (!allowed) {
            throw new AccessDeniedException("Bạn không có quyền hủy lời mời này");
        }
        inv.setStatus(InvitationStatus.CANCELLED);
        inv.setRespondedAt(LocalDateTime.now());
        return toResponse(invitationRepository.save(inv));
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private void assertNoPending(InvitationType type, UUID targetId, UUID inviteeUserId) {
        if (invitationRepository.existsByTypeAndTargetIdAndInviteeUserIdAndStatus(type, targetId, inviteeUserId, InvitationStatus.PENDING)) {
            throw new BadRequestException("Đã có lời mời đang chờ người dùng này trả lời");
        }
    }

    private Invitation requirePending(UUID invitationId) {
        Invitation inv = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lời mời: " + invitationId));
        if (inv.getStatus() != InvitationStatus.PENDING) {
            throw new BadRequestException("Lời mời đã được xử lý trước đó");
        }
        return inv;
    }

    private Invitation requirePendingForInvitee(UUID invitationId, UUID userId) {
        Invitation inv = requirePending(invitationId);
        if (!userId.equals(inv.getInviteeUserId())) {
            throw new AccessDeniedException("Lời mời này không dành cho bạn");
        }
        return inv;
    }

    private InvitationResponse toResponse(Invitation inv) {
        String targetName;
        String roleName;
        if (inv.getType() == InvitationType.TEAM) {
            targetName = teamRepository.findById(inv.getTargetId()).map(Team::getName).orElse(null);
            roleName = inv.getTeamRole() != null ? inv.getTeamRole().name() : null;
        } else {
            targetName = projectRepository.findById(inv.getTargetId()).map(Project::getName).orElse(null);
            roleName = inv.getProjectRoleId() == null ? null
                    : projectRoleRepository.findById(inv.getProjectRoleId()).map(ProjectRole::getName).orElse(null);
        }
        UserResponse inviter = fetchUser(inv.getInvitedBy());
        UserResponse invitee = fetchUser(inv.getInviteeUserId());
        return InvitationResponse.builder()
                .id(inv.getId())
                .type(inv.getType())
                .targetId(inv.getTargetId())
                .targetName(targetName)
                .roleName(roleName)
                .status(inv.getStatus())
                .invitedBy(inv.getInvitedBy())
                .inviterName(displayName(inviter))
                .inviteeUserId(inv.getInviteeUserId())
                .inviteeName(displayName(invitee))
                .inviteeEmail(invitee != null ? invitee.getEmail() : null)
                .createdAt(inv.getCreatedAt())
                .respondedAt(inv.getRespondedAt())
                .build();
    }

    private UserResponse fetchUser(UUID userId) {
        if (userId == null) return null;
        try {
            var res = identityClient.getUserById(userId);
            return res != null ? res.getData() : null;
        } catch (Exception e) {
            log.debug("Could not fetch user {}: {}", userId, e.getMessage());
            return null;
        }
    }

    private static String displayName(UserResponse u) {
        if (u == null) return null;
        String first = u.getFirstName() == null ? "" : u.getFirstName();
        String last = u.getLastName() == null ? "" : u.getLastName();
        String full = (first + " " + last).trim();
        if (!full.isEmpty()) return full;
        return u.getUsername() != null ? u.getUsername() : u.getEmail();
    }
}
