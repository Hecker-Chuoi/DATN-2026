package com.datn.workservice.service;

import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workservice.client.IdentityClient;
import com.datn.workservice.dto.response.InvitationResponse;
import com.datn.workservice.entity.Invitation;
import com.datn.workservice.entity.Project;
import com.datn.workservice.entity.ProjectRole;
import com.datn.workservice.entity.RoleAssignment;
import com.datn.workservice.enums.InvitationStatus;
import com.datn.workservice.enums.InvitationType;
import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.repository.InvitationRepository;
import com.datn.workservice.repository.ProjectRepository;
import com.datn.workservice.repository.ProjectRoleRepository;
import com.datn.workservice.repository.RoleAssignmentRepository;
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
import java.util.List;
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
    private ProjectRepository projectRepository;
    @Mock
    private ProjectRoleRepository projectRoleRepository;
    @Mock
    private RoleAssignmentRepository roleAssignmentRepository;
    @Mock
    private ProjectMemberService projectMemberService;
    @Mock
    private IdentityClient identityClient;
    @Mock
    private com.datn.workservice.notification.ProjectNotifier projectNotifier;

    @InjectMocks
    private InvitationServiceImpl invitationService;

    private UUID projectId;
    private UUID workspaceId;
    private UUID roleId;
    private UUID inviteeUserId;
    private UUID inviterUserId;
    private Project project;
    private ProjectRole projectRole;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        workspaceId = UUID.randomUUID();
        roleId = UUID.randomUUID();
        inviteeUserId = UUID.randomUUID();
        inviterUserId = UUID.randomUUID();

        project = Project.builder()
                .id(projectId)
                .workspaceId(workspaceId)
                .name("Alpha Project")
                .key("ALPHA")
                .build();

        projectRole = ProjectRole.builder()
                .id(roleId)
                .projectId(projectId)
                .name("Developer")
                .build();
    }

    @Test
    @DisplayName("Ném ResourceNotFoundException khi không tìm thấy dự án")
    void inviteToProject_ThrowsNotFound_WhenProjectNotFound() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invitationService.inviteToProject(projectId, inviteeUserId, roleId, inviterUserId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy dự án");

        verify(invitationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Ném BadRequestException khi vai trò không thuộc về dự án")
    void inviteToProject_ThrowsBadRequest_WhenRoleNotBelongsToProject() {
        ProjectRole foreignRole = ProjectRole.builder()
                .id(roleId)
                .projectId(UUID.randomUUID()) // khác projectId
                .name("Developer")
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectRoleRepository.findById(roleId)).thenReturn(Optional.of(foreignRole));

        assertThatThrownBy(() -> invitationService.inviteToProject(projectId, inviteeUserId, roleId, inviterUserId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Vai trò không thuộc dự án này");

        verify(invitationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Ném BadRequestException khi user đã là thành viên của dự án")
    void inviteToProject_ThrowsBadRequest_WhenUserAlreadyMember() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectRoleRepository.findById(roleId)).thenReturn(Optional.of(projectRole));
        when(projectMemberService.canonicalUserId(inviteeUserId)).thenReturn(inviteeUserId);
        when(roleAssignmentRepository.findByProjectIdAndUserId(projectId, inviteeUserId))
                .thenReturn(List.of(RoleAssignment.builder().id(UUID.randomUUID()).build()));

        assertThatThrownBy(() -> invitationService.inviteToProject(projectId, inviteeUserId, roleId, inviterUserId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Người dùng đã là thành viên của dự án");

        verify(invitationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Mời vào dự án thành công và gửi mail thông báo")
    void inviteToProject_Succeeds() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectRoleRepository.findById(roleId)).thenReturn(Optional.of(projectRole));
        when(projectMemberService.canonicalUserId(inviteeUserId)).thenReturn(inviteeUserId);
        when(roleAssignmentRepository.findByProjectIdAndUserId(projectId, inviteeUserId))
                .thenReturn(Collections.emptyList());
        when(invitationRepository.existsByTypeAndTargetIdAndInviteeUserIdAndStatus(
                eq(InvitationType.PROJECT), eq(projectId), eq(inviteeUserId), eq(InvitationStatus.PENDING)))
                .thenReturn(false);

        UUID invitationId = UUID.randomUUID();
        when(invitationRepository.save(any(Invitation.class))).thenAnswer(invocation -> {
            Invitation inv = invocation.getArgument(0);
            inv.setId(invitationId);
            return inv;
        });

        InvitationResponse response = invitationService.inviteToProject(projectId, inviteeUserId, roleId, inviterUserId);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(invitationId);
        assertThat(response.getInviteeUserId()).isEqualTo(inviteeUserId);
        assertThat(response.getType()).isEqualTo(InvitationType.PROJECT);
        // G1: thông báo + email lời mời đi qua notification-service (outbox), không gửi mail trực tiếp nữa
        verify(projectNotifier, times(1)).invitationCreated(eq(project), any(Invitation.class), eq(projectRole.getName()), eq(inviterUserId));
    }

    @Test
    @DisplayName("Hủy lời mời: Thành công khi người gọi có quyền PROJECT_ADMIN")
    void cancel_SucceedsForProjectAdmin() {
        UUID invitationId = UUID.randomUUID();
        UUID managerUserId = UUID.randomUUID();

        Invitation inv = Invitation.builder()
                .id(invitationId)
                .type(InvitationType.PROJECT)
                .targetId(projectId)
                .inviteeUserId(inviteeUserId)
                .invitedBy(inviterUserId)
                .status(InvitationStatus.PENDING)
                .build();

        when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(inv));
        when(projectMemberService.hasPermission(projectId, managerUserId, PermissionKey.PROJECT_ADMIN)).thenReturn(true);
        when(invitationRepository.save(any(Invitation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InvitationResponse res = invitationService.cancel(invitationId, managerUserId, false);

        assertThat(res.getStatus()).isEqualTo(InvitationStatus.CANCELLED);
        verify(invitationRepository).save(inv);
    }
}
