package com.datn.workservice.security;

import com.datn.workservice.entity.Project;
import com.datn.workservice.entity.ProjectRole;
import com.datn.workservice.entity.RoleAssignment;
import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.repository.PermissionSchemeEntryRepository;
import com.datn.workservice.repository.ProjectRepository;
import com.datn.workservice.repository.ProjectRoleRepository;
import com.datn.workservice.repository.RoleAssignmentRepository;
import com.datn.workservice.service.impl.ProjectMemberServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectPermissionGuardTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectRoleRepository projectRoleRepository;
    @Mock
    private RoleAssignmentRepository roleAssignmentRepository;
    @Mock
    private PermissionSchemeEntryRepository permissionSchemeEntryRepository;

    @InjectMocks
    private ProjectMemberServiceImpl projectMemberService;

    private UUID projectId;
    private UUID leadUserId;
    private UUID creatorUserId;
    private UUID memberUserId;
    private UUID outsiderUserId;

    private Project project;
    private ProjectRole adminRole;
    private ProjectRole memberRole;
    private ProjectRole viewerRole;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        leadUserId = UUID.randomUUID();
        creatorUserId = UUID.randomUUID();
        memberUserId = UUID.randomUUID();
        outsiderUserId = UUID.randomUUID();

        project = Project.builder()
                .id(projectId)
                .name("Core Platform")
                .key("CP")
                .leadUserId(leadUserId)
                .createdBy(creatorUserId)
                .build();

        adminRole = ProjectRole.builder().id(UUID.randomUUID()).projectId(projectId).name("ADMINISTRATOR").build();
        memberRole = ProjectRole.builder().id(UUID.randomUUID()).projectId(projectId).name("MEMBER").build();
        viewerRole = ProjectRole.builder().id(UUID.randomUUID()).projectId(projectId).name("VIEWER").build();
    }

    @Test
    @DisplayName("Project Lead & Creator have full permissions unconditionally")
    void testLeadAndCreatorHaveFullPermissions() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        // Lead checks
        assertThat(projectMemberService.hasPermission(projectId, leadUserId, PermissionKey.PROJECT_ADMIN)).isTrue();
        assertThat(projectMemberService.hasPermission(projectId, leadUserId, PermissionKey.ISSUE_DELETE)).isTrue();
        assertThat(projectMemberService.hasPermission(projectId, leadUserId, PermissionKey.SPRINT_MANAGE)).isTrue();

        // Creator checks
        assertThat(projectMemberService.hasPermission(projectId, creatorUserId, PermissionKey.PROJECT_ADMIN)).isTrue();
        assertThat(projectMemberService.hasPermission(projectId, creatorUserId, PermissionKey.RELEASE_MANAGE)).isTrue();
    }

    @Test
    @DisplayName("User with ADMINISTRATOR role has all permissions")
    void testAdministratorRoleHasAllPermissions() {
        UUID adminUserId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(roleAssignmentRepository.findByProjectIdAndUserId(projectId, adminUserId)).thenReturn(List.of(
                RoleAssignment.builder().projectId(projectId).userId(adminUserId).roleId(adminRole.getId()).build()
        ));
        when(projectRoleRepository.findById(adminRole.getId())).thenReturn(Optional.of(adminRole));

        assertThat(projectMemberService.hasPermission(projectId, adminUserId, PermissionKey.PROJECT_ADMIN)).isTrue();
        assertThat(projectMemberService.hasPermission(projectId, adminUserId, PermissionKey.ISSUE_DELETE)).isTrue();
    }

    @Test
    @DisplayName("User with MEMBER role has ISSUE_CREATE but lacks PROJECT_ADMIN")
    void testMemberRolePermissions() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(roleAssignmentRepository.findByProjectIdAndUserId(projectId, memberUserId)).thenReturn(List.of(
                RoleAssignment.builder().projectId(projectId).userId(memberUserId).roleId(memberRole.getId()).build()
        ));
        when(projectRoleRepository.findById(memberRole.getId())).thenReturn(Optional.of(memberRole));

        when(permissionSchemeEntryRepository.existsByProjectIdAndRoleIdAndPermissionKey(
                projectId, memberRole.getId(), PermissionKey.ISSUE_CREATE.name()
        )).thenReturn(true);

        when(permissionSchemeEntryRepository.existsByProjectIdAndRoleIdAndPermissionKey(
                projectId, memberRole.getId(), PermissionKey.PROJECT_ADMIN.name()
        )).thenReturn(false);

        // Member has CREATE_ISSUE
        assertThat(projectMemberService.hasPermission(projectId, memberUserId, PermissionKey.ISSUE_CREATE)).isTrue();
        // Member lacks PROJECT_ADMIN
        assertThat(projectMemberService.hasPermission(projectId, memberUserId, PermissionKey.PROJECT_ADMIN)).isFalse();
    }

    @Test
    @DisplayName("Outsider user not in project has zero permissions (deny by default, including PROJECT_VIEW)")
    void testOutsiderHasNoPermissions() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(roleAssignmentRepository.findByProjectIdAndUserId(projectId, outsiderUserId)).thenReturn(List.of());

        assertThat(projectMemberService.hasPermission(projectId, outsiderUserId, PermissionKey.PROJECT_VIEW)).isFalse();
        assertThat(projectMemberService.hasPermission(projectId, outsiderUserId, PermissionKey.ISSUE_CREATE)).isFalse();
        assertThat(projectMemberService.hasPermission(projectId, outsiderUserId, PermissionKey.PROJECT_ADMIN)).isFalse();
    }

    @Test
    @DisplayName("Any project member (e.g. VIEWER) can browse the project but cannot create issues")
    void testViewerCanBrowseOnly() {
        UUID viewerUserId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(roleAssignmentRepository.findByProjectIdAndUserId(projectId, viewerUserId)).thenReturn(List.of(
                RoleAssignment.builder().projectId(projectId).userId(viewerUserId).roleId(viewerRole.getId()).build()
        ));
        when(projectRoleRepository.findById(viewerRole.getId())).thenReturn(Optional.of(viewerRole));
        when(permissionSchemeEntryRepository.existsByProjectIdAndRoleIdAndPermissionKey(
                projectId, viewerRole.getId(), PermissionKey.ISSUE_CREATE.name()
        )).thenReturn(false);

        assertThat(projectMemberService.hasPermission(projectId, viewerUserId, PermissionKey.PROJECT_VIEW)).isTrue();
        assertThat(projectMemberService.hasPermission(projectId, viewerUserId, PermissionKey.ISSUE_CREATE)).isFalse();
    }

    @Test
    @DisplayName("Soft-deleted project grants no permission to anyone but Global ADMIN (handled by guard)")
    void testDeletedProjectDeniesAll() {
        project.setDeletedAt(java.time.LocalDateTime.now());
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        assertThat(projectMemberService.hasPermission(projectId, leadUserId, PermissionKey.PROJECT_VIEW)).isFalse();
    }
}
