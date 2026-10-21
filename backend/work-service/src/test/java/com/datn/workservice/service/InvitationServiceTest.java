package com.datn.workservice.service;

import com.datn.common.exception.BadRequestException;
import com.datn.workservice.client.IdentityClient;
import com.datn.workservice.dto.response.InvitationResponse;
import com.datn.workservice.entity.Invitation;
import com.datn.workservice.entity.Team;
import com.datn.workservice.enums.InvitationStatus;
import com.datn.workservice.enums.InvitationType;
import com.datn.workservice.enums.TeamRole;
import com.datn.workservice.repository.InvitationRepository;
import com.datn.workservice.repository.ProjectRepository;
import com.datn.workservice.repository.ProjectRoleRepository;
import com.datn.workservice.repository.RoleAssignmentRepository;
import com.datn.workservice.repository.TeamMemberRepository;
import com.datn.workservice.repository.TeamRepository;
import com.datn.workservice.security.TeamSecurity;
import com.datn.workservice.security.WorkspaceAccessChecker;
import com.datn.workservice.service.impl.InvitationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    @Mock
    private InvitationRepository invitationRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectRoleRepository projectRoleRepository;
    @Mock
    private RoleAssignmentRepository roleAssignmentRepository;
    @Mock
    private ProjectMemberService projectMemberService;
    @Mock
    private TeamSecurity teamSecurity;
    @Mock
    private WorkspaceAccessChecker workspaceAccessChecker;
    @Mock
    private IdentityClient identityClient;
    @Mock
    private InvitationMailer mailer;

    @InjectMocks
    private InvitationServiceImpl invitationService;

    private UUID teamId;
    private UUID workspaceId;
    private UUID inviteeUserId;
    private UUID inviterUserId;
    private Team team;

    @BeforeEach
    void setUp() {
        teamId = UUID.randomUUID();
        workspaceId = UUID.randomUUID();
        inviteeUserId = UUID.randomUUID();
        inviterUserId = UUID.randomUUID();

        team = Team.builder()
                .id(teamId)
                .workspaceId(workspaceId)
                .name("Core Squad")
                .ownerId(inviterUserId)
                .build();
    }

    @Test
    @DisplayName("Ném BadRequestException khi mời user KHÔNG thuộc Workspace vào Team")
    void inviteToTeam_FailsWhenUserNotInWorkspace() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(projectMemberService.canonicalUserId(inviteeUserId)).thenReturn(inviteeUserId);
        doThrow(new AccessDeniedException("Forbidden")).when(workspaceAccessChecker).requireMember(workspaceId, inviteeUserId);

        assertThatThrownBy(() -> invitationService.inviteToTeam(teamId, inviteeUserId, TeamRole.MEMBER, inviterUserId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Chỉ có thể mời người dùng đang là thành viên của Không gian làm việc (Workspace) này vào nhóm");

        verify(invitationRepository, never()).save(any());
        verify(mailer, never()).send(any());
    }

    @Test
    @DisplayName("Mời thành công khi user là thành viên cùng Workspace")
    void inviteToTeam_SucceedsWhenUserInWorkspace() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(projectMemberService.canonicalUserId(inviteeUserId)).thenReturn(inviteeUserId);
        doNothing().when(workspaceAccessChecker).requireMember(workspaceId, inviteeUserId);
        when(teamMemberRepository.existsByTeamIdAndUserId(teamId, inviteeUserId)).thenReturn(false);
        when(invitationRepository.existsByTypeAndTargetIdAndInviteeUserIdAndStatus(
                eq(InvitationType.TEAM), eq(teamId), eq(inviteeUserId), eq(InvitationStatus.PENDING)))
                .thenReturn(false);

        UUID invitationId = UUID.randomUUID();
        when(invitationRepository.save(any(Invitation.class))).thenAnswer(invocation -> {
            Invitation inv = invocation.getArgument(0);
            inv.setId(invitationId);
            return inv;
        });

        InvitationResponse response = invitationService.inviteToTeam(teamId, inviteeUserId, TeamRole.MEMBER, inviterUserId);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(invitationId);
        assertThat(response.getInviteeUserId()).isEqualTo(inviteeUserId);
        verify(mailer, times(1)).send(any());
    }

    @Test
    @DisplayName("Hủy lời mời: Thành công khi người gọi là canManageTeam (Workspace Owner / Admin)")
    void cancel_SucceedsForWorkspaceManager() {
        UUID invitationId = UUID.randomUUID();
        UUID managerUserId = UUID.randomUUID();

        Invitation inv = Invitation.builder()
                .id(invitationId)
                .type(InvitationType.TEAM)
                .targetId(teamId)
                .inviteeUserId(inviteeUserId)
                .invitedBy(inviterUserId)
                .status(InvitationStatus.PENDING)
                .build();

        when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(inv));
        when(teamSecurity.canManageTeam(teamId)).thenReturn(true);
        when(invitationRepository.save(any(Invitation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InvitationResponse res = invitationService.cancel(invitationId, managerUserId, false);

        assertThat(res.getStatus()).isEqualTo(InvitationStatus.CANCELLED);
        verify(invitationRepository).save(inv);
    }
}
