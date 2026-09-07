package com.datn.workservice.service;

import com.datn.common.dto.ApiResponse;
import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workservice.client.IdentityClient;
import com.datn.workservice.client.dto.UserResponse;
import com.datn.workservice.dto.request.AssignProjectRoleRequest;
import com.datn.workservice.dto.response.ProjectRoleResponse;
import com.datn.workservice.dto.response.RoleAssignmentResponse;
import com.datn.workservice.entity.PermissionSchemeEntry;
import com.datn.workservice.entity.Project;
import com.datn.workservice.entity.ProjectRole;
import com.datn.workservice.entity.RoleAssignment;
import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.repository.PermissionSchemeEntryRepository;
import com.datn.workservice.repository.ProjectRepository;
import com.datn.workservice.repository.ProjectRoleRepository;
import com.datn.workservice.repository.RoleAssignmentRepository;
import com.datn.workservice.repository.TeamMemberRepository;
import com.datn.workservice.service.impl.ProjectMemberServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectMemberServiceTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectRoleRepository projectRoleRepository;
    @Mock
    private RoleAssignmentRepository roleAssignmentRepository;
    @Mock
    private PermissionSchemeEntryRepository permissionSchemeEntryRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private IdentityClient identityClient;

    @InjectMocks
    private ProjectMemberServiceImpl projectMemberService;

    private UUID projectId;
    private UUID leadUserId;
    private UUID creatorUserId;
    private UUID memberUserId;
    private UUID roleId;

    private Project project;
    private ProjectRole memberRole;
    private ProjectRole adminRole;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        leadUserId = UUID.randomUUID();
        creatorUserId = UUID.randomUUID();
        memberUserId = UUID.randomUUID();
        roleId = UUID.randomUUID();

        project = Project.builder()
                .id(projectId)
                .name("Fintech Core")
                .key("FIN")
                .leadUserId(leadUserId)
                .createdBy(creatorUserId)
                .build();

        memberRole = ProjectRole.builder()
                .id(roleId)
                .projectId(projectId)
                .name("MEMBER")
                .description("Team member")
                .build();

        adminRole = ProjectRole.builder()
                .id(UUID.randomUUID())
                .projectId(projectId)
                .name("ADMINISTRATOR")
                .description("Project Administrator")
                .build();
    }

    @Test
    @DisplayName("addMemberToProject: Adds member successfully with user details")
    void testAddMemberToProject_Success() {
        AssignProjectRoleRequest request = AssignProjectRoleRequest.builder()
                .userId(memberUserId)
                .roleId(roleId)
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectRoleRepository.findById(roleId)).thenReturn(Optional.of(memberRole));
        when(roleAssignmentRepository.existsByProjectIdAndUserIdAndRoleId(projectId, memberUserId, roleId))
                .thenReturn(false);

        RoleAssignment savedAssignment = RoleAssignment.builder()
                .id(UUID.randomUUID())
                .projectId(projectId)
                .userId(memberUserId)
                .roleId(roleId)
                .assignedAt(LocalDateTime.now())
                .build();

        when(roleAssignmentRepository.save(any(RoleAssignment.class))).thenReturn(savedAssignment);

        UserResponse userResponse = UserResponse.builder()
                .id(memberUserId)
                .username("john_doe")
                .email("john@example.com")
                .firstName("John")
                .lastName("Doe")
                .build();
        when(identityClient.getUserById(memberUserId)).thenReturn(ApiResponse.success(userResponse));

        RoleAssignmentResponse response = projectMemberService.addMemberToProject(projectId, request, leadUserId);

        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo(memberUserId);
        assertThat(response.getRoleName()).isEqualTo("MEMBER");
        assertThat(response.getUsername()).isEqualTo("john_doe");
        assertThat(response.getEmail()).isEqualTo("john@example.com");
        verify(roleAssignmentRepository).save(any(RoleAssignment.class));
    }

    @Test
    @DisplayName("addMemberToProject: Throws BadRequestException when user already has role")
    void testAddMemberToProject_DuplicateRole_ThrowsBadRequest() {
        AssignProjectRoleRequest request = AssignProjectRoleRequest.builder()
                .userId(memberUserId)
                .roleId(roleId)
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectRoleRepository.findById(roleId)).thenReturn(Optional.of(memberRole));
        when(roleAssignmentRepository.existsByProjectIdAndUserIdAndRoleId(projectId, memberUserId, roleId))
                .thenReturn(true);

        assertThatThrownBy(() -> projectMemberService.addMemberToProject(projectId, request, leadUserId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Người dùng đã có vai trò này");

        verify(roleAssignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("addMemberToProject: Throws ResourceNotFoundException when role does not exist")
    void testAddMemberToProject_RoleNotFound_ThrowsResourceNotFound() {
        AssignProjectRoleRequest request = AssignProjectRoleRequest.builder()
                .userId(memberUserId)
                .roleId(roleId)
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectRoleRepository.findById(roleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectMemberService.addMemberToProject(projectId, request, leadUserId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy vai trò");
    }

    @Test
    @DisplayName("updateMemberRole: Successfully changes member's role")
    void testUpdateMemberRole_Success() {
        UUID newRoleId = adminRole.getId();
        when(projectRoleRepository.findById(newRoleId)).thenReturn(Optional.of(adminRole));

        RoleAssignment existing = RoleAssignment.builder()
                .id(UUID.randomUUID())
                .projectId(projectId)
                .userId(memberUserId)
                .roleId(roleId)
                .assignedAt(LocalDateTime.now())
                .build();

        when(roleAssignmentRepository.findByProjectIdAndUserId(projectId, memberUserId))
                .thenReturn(List.of(existing));

        when(roleAssignmentRepository.save(existing)).thenReturn(existing);

        RoleAssignmentResponse response = projectMemberService.updateMemberRole(projectId, memberUserId, newRoleId, leadUserId);

        assertThat(response).isNotNull();
        assertThat(response.getRoleId()).isEqualTo(newRoleId);
        assertThat(response.getRoleName()).isEqualTo("ADMINISTRATOR");
        verify(roleAssignmentRepository).save(existing);
    }

    @Test
    @DisplayName("updateMemberRole: Throws ResourceNotFoundException when user is not member of project")
    void testUpdateMemberRole_MemberNotFound() {
        UUID newRoleId = adminRole.getId();
        when(projectRoleRepository.findById(newRoleId)).thenReturn(Optional.of(adminRole));
        when(roleAssignmentRepository.findByProjectIdAndUserId(projectId, memberUserId)).thenReturn(List.of());

        assertThatThrownBy(() -> projectMemberService.updateMemberRole(projectId, memberUserId, newRoleId, leadUserId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Thành viên không thuộc dự án");
    }

    @Test
    @DisplayName("removeMemberFromProject: Successfully removes non-lead member")
    void testRemoveMemberFromProject_Success() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        projectMemberService.removeMemberFromProject(projectId, memberUserId, leadUserId);

        verify(roleAssignmentRepository).deleteByProjectIdAndUserId(projectId, memberUserId);
    }

    @Test
    @DisplayName("removeMemberFromProject: Throws BadRequestException when trying to delete Project Lead")
    void testRemoveMemberFromProject_ProjectLead_ThrowsBadRequest() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> projectMemberService.removeMemberFromProject(projectId, leadUserId, leadUserId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Không thể xóa Project Lead");

        verify(roleAssignmentRepository, never()).deleteByProjectIdAndUserId(any(), any());
    }

    @Test
    @DisplayName("getMyPermissions: Project Lead gets all permissions")
    void testGetMyPermissions_AsLead_ReturnsAll() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        List<String> permissions = projectMemberService.getMyPermissions(projectId, leadUserId);

        assertThat(permissions).hasSize(PermissionKey.values().length);
        assertThat(permissions).contains(
                PermissionKey.PROJECT_ADMIN.name(),
                PermissionKey.ISSUE_CREATE.name(),
                PermissionKey.SPRINT_MANAGE.name(),
                PermissionKey.RELEASE_MANAGE.name()
        );
    }

    @Test
    @DisplayName("getMyPermissions: Member gets assigned scheme permissions")
    void testGetMyPermissions_AsMember_ReturnsAssignedPermissions() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        when(roleAssignmentRepository.findByProjectIdAndUserId(projectId, memberUserId)).thenReturn(List.of(
                RoleAssignment.builder().projectId(projectId).userId(memberUserId).roleId(roleId).build()
        ));
        when(projectRoleRepository.findById(roleId)).thenReturn(Optional.of(memberRole));

        PermissionSchemeEntry entry1 = PermissionSchemeEntry.builder()
                .projectId(projectId)
                .roleId(roleId)
                .permissionKey(PermissionKey.ISSUE_CREATE.name())
                .build();
        PermissionSchemeEntry entry2 = PermissionSchemeEntry.builder()
                .projectId(projectId)
                .roleId(roleId)
                .permissionKey(PermissionKey.ISSUE_TRANSITION.name())
                .build();

        when(permissionSchemeEntryRepository.findByProjectIdAndRoleId(projectId, roleId))
                .thenReturn(List.of(entry1, entry2));

        List<String> permissions = projectMemberService.getMyPermissions(projectId, memberUserId);

        assertThat(permissions).contains(
                PermissionKey.PROJECT_VIEW.name(),
                PermissionKey.ISSUE_CREATE.name(),
                PermissionKey.ISSUE_TRANSITION.name()
        );
        assertThat(permissions).doesNotContain(PermissionKey.PROJECT_ADMIN.name());
    }

    @Test
    @DisplayName("getProjectRoles: Returns all roles of project")
    void testGetProjectRoles_ReturnsAllRoles() {
        when(projectRoleRepository.findByProjectId(projectId)).thenReturn(List.of(adminRole, memberRole));

        List<ProjectRoleResponse> roles = projectMemberService.getProjectRoles(projectId);

        assertThat(roles).hasSize(2);
        assertThat(roles.stream().map(ProjectRoleResponse::getName)).contains("ADMINISTRATOR", "MEMBER");
    }

    @Test
    @DisplayName("getProjectMembers: Batch fetches and maps users with roles")
    void testGetProjectMembers_ReturnsMappedMembers() {
        RoleAssignment assignment = RoleAssignment.builder()
                .id(UUID.randomUUID())
                .projectId(projectId)
                .userId(memberUserId)
                .roleId(roleId)
                .assignedAt(LocalDateTime.now())
                .build();

        when(roleAssignmentRepository.findByProjectId(projectId)).thenReturn(List.of(assignment));
        when(projectRoleRepository.findByProjectId(projectId)).thenReturn(List.of(memberRole));

        UserResponse user = UserResponse.builder()
                .id(memberUserId)
                .username("john_doe")
                .email("john@example.com")
                .firstName("John")
                .lastName("Doe")
                .build();
        when(identityClient.getUsersByIds(List.of(memberUserId))).thenReturn(ApiResponse.success(List.of(user)));

        List<RoleAssignmentResponse> result = projectMemberService.getProjectMembers(projectId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUsername()).isEqualTo("john_doe");
        assertThat(result.get(0).getRoleName()).isEqualTo("MEMBER");
    }
}
