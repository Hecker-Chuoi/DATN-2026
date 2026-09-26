package com.datn.workservice.service.impl;

import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workservice.client.IdentityClient;
import com.datn.workservice.client.dto.UserResponse;
import com.datn.workservice.dto.request.AssignProjectRoleRequest;
import com.datn.workservice.dto.response.MyProjectPermissionsResponse;
import com.datn.workservice.dto.response.ProjectRoleResponse;
import com.datn.workservice.dto.response.RoleAssignmentResponse;
import com.datn.workservice.entity.Project;
import com.datn.workservice.entity.ProjectRole;
import com.datn.workservice.entity.RoleAssignment;
import com.datn.workservice.entity.TeamMember;
import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.enums.TeamRole;
import com.datn.workservice.repository.PermissionSchemeEntryRepository;
import com.datn.workservice.repository.ProjectRepository;
import com.datn.workservice.repository.ProjectRoleRepository;
import com.datn.workservice.repository.RoleAssignmentRepository;
import com.datn.workservice.repository.TeamMemberRepository;
import com.datn.workservice.service.ProjectMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectMemberServiceImpl implements ProjectMemberService {

    public static final String ROLE_ADMINISTRATOR = "ADMINISTRATOR";
    public static final String ROLE_MEMBER = "MEMBER";
    public static final String ROLE_VIEWER = "VIEWER";

    private final ProjectRepository projectRepository;
    private final ProjectRoleRepository projectRoleRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final PermissionSchemeEntryRepository permissionSchemeEntryRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final IdentityClient identityClient;

    // ─── Permission evaluation ───────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public boolean hasPermission(UUID projectId, UUID userId, PermissionKey permissionKey) {
        if (projectId == null || userId == null || permissionKey == null) {
            return false;
        }

        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null || project.getDeletedAt() != null) {
            return false;
        }

        // 1. Project Lead & Creator have super-admin privileges over the project
        if (userId.equals(project.getLeadUserId()) || userId.equals(project.getCreatedBy())) {
            return true;
        }

        // 2. Membership is mandatory: no role assignment -> no permission at all (deny by default)
        List<RoleAssignment> assignments = roleAssignmentRepository.findByProjectIdAndUserId(projectId, userId);
        if (assignments.isEmpty()) {
            return false;
        }

        // 3. Any member may browse the project
        if (permissionKey == PermissionKey.PROJECT_VIEW) {
            return true;
        }

        // 4. ADMINISTRATOR role or explicit permission scheme entry
        for (RoleAssignment assignment : assignments) {
            ProjectRole role = projectRoleRepository.findById(assignment.getRoleId()).orElse(null);
            if (role == null) continue;
            if (ROLE_ADMINISTRATOR.equalsIgnoreCase(role.getName())) {
                return true;
            }
            if (permissionSchemeEntryRepository.existsByProjectIdAndRoleIdAndPermissionKey(
                    projectId, role.getId(), permissionKey.name())) {
                return true;
            }
        }

        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public MyProjectPermissionsResponse getMyPermissions(UUID projectId, UUID currentUserId, boolean isGlobalAdmin) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dự án: " + projectId));

        boolean isLead = currentUserId.equals(project.getLeadUserId()) || currentUserId.equals(project.getCreatedBy());

        if (isGlobalAdmin || isLead) {
            List<String> allPerms = Arrays.stream(PermissionKey.values())
                    .map(Enum::name)
                    .collect(Collectors.toList());
            return MyProjectPermissionsResponse.builder()
                    .projectId(projectId)
                    .roleName(ROLE_ADMINISTRATOR)
                    .isLead(isLead)
                    .isGlobalAdmin(isGlobalAdmin)
                    .permissions(allPerms)
                    .build();
        }

        List<RoleAssignment> assignments = roleAssignmentRepository.findByProjectIdAndUserId(projectId, currentUserId);
        if (assignments.isEmpty()) {
            return MyProjectPermissionsResponse.builder()
                    .projectId(projectId)
                    .roleName(null)
                    .isLead(false)
                    .isGlobalAdmin(false)
                    .permissions(Collections.emptyList())
                    .build();
        }

        Set<String> permissions = new LinkedHashSet<>();
        permissions.add(PermissionKey.PROJECT_VIEW.name());
        String primaryRoleName = ROLE_MEMBER;

        for (RoleAssignment assignment : assignments) {
            ProjectRole role = projectRoleRepository.findById(assignment.getRoleId()).orElse(null);
            if (role == null) continue;
            primaryRoleName = role.getName();
            if (ROLE_ADMINISTRATOR.equalsIgnoreCase(role.getName())) {
                Arrays.stream(PermissionKey.values()).forEach(pk -> permissions.add(pk.name()));
                break;
            }
            permissionSchemeEntryRepository.findByProjectIdAndRoleId(projectId, role.getId())
                    .forEach(entry -> permissions.add(entry.getPermissionKey()));
        }

        return MyProjectPermissionsResponse.builder()
                .projectId(projectId)
                .roleName(primaryRoleName)
                .isLead(false)
                .isGlobalAdmin(false)
                .permissions(new ArrayList<>(permissions))
                .build();
    }

    // ─── Members ─────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<RoleAssignmentResponse> getProjectMembers(UUID projectId) {
        List<RoleAssignment> assignments = roleAssignmentRepository.findByProjectId(projectId);
        if (assignments.isEmpty()) {
            return Collections.emptyList();
        }

        // Batch fetch user details from identity-service
        List<UUID> userIds = assignments.stream().map(RoleAssignment::getUserId).distinct().toList();
        Map<UUID, UserResponse> userMap = new HashMap<>();

        try {
            var res = identityClient.getUsersByIds(userIds);
            if (res != null && res.getData() != null) {
                res.getData().forEach(u -> {
                    if (u.getId() != null) userMap.put(u.getId(), u);
                    if (u.getKeycloakId() != null) {
                        try {
                            userMap.put(UUID.fromString(u.getKeycloakId()), u);
                        } catch (Exception ignored) {
                        }
                    }
                });
            }
        } catch (Exception e) {
            log.debug("Batch fetching user info failed: {}", e.getMessage());
        }

        Map<UUID, String> roleMap = projectRoleRepository.findByProjectId(projectId).stream()
                .collect(Collectors.toMap(ProjectRole::getId, ProjectRole::getName, (a, b) -> a));

        return assignments.stream().map(a -> {
            UserResponse user = userMap.get(a.getUserId());
            String roleName = roleMap.getOrDefault(a.getRoleId(), ROLE_MEMBER);

            return RoleAssignmentResponse.builder()
                    .id(a.getId())
                    .projectId(a.getProjectId())
                    .userId(a.getUserId())
                    .roleId(a.getRoleId())
                    .roleName(roleName)
                    .assignedAt(a.getAssignedAt())
                    .username(user != null ? user.getUsername() : null)
                    .email(user != null ? user.getEmail() : null)
                    .fullName(user != null ? user.getFullName() : null)
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void syncTeamMembersToProject(UUID projectId) {
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null || project.getTeamId() == null) return;

        List<TeamMember> teamMembers = teamMemberRepository.findByTeamId(project.getTeamId());
        if (teamMembers.isEmpty()) return;

        List<ProjectRole> projectRoles = projectRoleRepository.findByProjectId(project.getId());
        ProjectRole adminRole = findRole(projectRoles, ROLE_ADMINISTRATOR);
        ProjectRole memberRole = findRole(projectRoles, ROLE_MEMBER);
        ProjectRole viewerRole = findRole(projectRoles, ROLE_VIEWER);
        if (adminRole == null || memberRole == null || viewerRole == null) return;

        for (TeamMember tm : teamMembers) {
            if (tm.getUserId() == null) continue;
            List<RoleAssignment> existing = roleAssignmentRepository.findByProjectIdAndUserId(project.getId(), tm.getUserId());
            if (!existing.isEmpty()) continue;

            ProjectRole targetRole = memberRole;
            if (tm.getRole() == TeamRole.LEADER) {
                targetRole = adminRole;
            } else if (tm.getRole() == TeamRole.VIEWER) {
                targetRole = viewerRole;
            }

            roleAssignmentRepository.save(RoleAssignment.builder()
                    .projectId(project.getId())
                    .userId(tm.getUserId())
                    .roleId(targetRole.getId())
                    .build());
        }
    }

    @Override
    @Transactional
    public RoleAssignmentResponse addMemberToProject(UUID projectId, AssignProjectRoleRequest request, UUID currentUserId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dự án: " + projectId));

        ProjectRole role = projectRoleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vai trò: " + request.getRoleId()));
        assertRoleBelongsToProject(role, projectId);

        UUID targetUserId = canonicalUserId(request.getUserId());

        if (roleAssignmentRepository.existsByProjectIdAndUserIdAndRoleId(projectId, targetUserId, request.getRoleId())) {
            throw new BadRequestException("Người dùng đã có vai trò này trong dự án");
        }
        if (!roleAssignmentRepository.findByProjectIdAndUserId(projectId, targetUserId).isEmpty()) {
            throw new BadRequestException("Người dùng đã là thành viên của dự án, hãy dùng chức năng đổi vai trò");
        }

        RoleAssignment assignment = RoleAssignment.builder()
                .projectId(projectId)
                .userId(targetUserId)
                .roleId(request.getRoleId())
                .build();

        RoleAssignment saved = roleAssignmentRepository.save(assignment);
        log.info("Member added to project: user={}, role={}, project={} by {}", targetUserId, role.getName(), projectId, currentUserId);

        UserResponse user = fetchUser(targetUserId);

        return RoleAssignmentResponse.builder()
                .id(saved.getId())
                .projectId(saved.getProjectId())
                .userId(saved.getUserId())
                .roleId(saved.getRoleId())
                .roleName(role.getName())
                .assignedAt(saved.getAssignedAt())
                .username(user != null ? user.getUsername() : null)
                .email(user != null ? user.getEmail() : null)
                .fullName(user != null ? user.getFullName() : null)
                .build();
    }

    @Override
    @Transactional
    public RoleAssignmentResponse updateMemberRole(UUID projectId, UUID userId, UUID newRoleId, UUID currentUserId) {
        ProjectRole role = projectRoleRepository.findById(newRoleId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vai trò mới: " + newRoleId));
        assertRoleBelongsToProject(role, projectId);

        UUID targetUserId = canonicalUserId(userId);
        List<RoleAssignment> assignments = roleAssignmentRepository.findByProjectIdAndUserId(projectId, targetUserId);
        if (assignments.isEmpty()) {
            throw new ResourceNotFoundException("Thành viên không thuộc dự án này");
        }

        RoleAssignment assignment = assignments.get(0);
        boolean wasAdmin = isAdministratorRole(assignment.getRoleId());
        boolean willBeAdmin = ROLE_ADMINISTRATOR.equalsIgnoreCase(role.getName());
        if (wasAdmin && !willBeAdmin) {
            assertNotLastAdministrator(projectId, targetUserId);
        }

        assignment.setRoleId(newRoleId);
        RoleAssignment saved = roleAssignmentRepository.save(assignment);

        log.info("Member role updated: user={}, newRole={}, project={} by {}", targetUserId, role.getName(), projectId, currentUserId);

        return RoleAssignmentResponse.builder()
                .id(saved.getId())
                .projectId(saved.getProjectId())
                .userId(saved.getUserId())
                .roleId(saved.getRoleId())
                .roleName(role.getName())
                .assignedAt(saved.getAssignedAt())
                .build();
    }

    @Override
    @Transactional
    public void removeMemberFromProject(UUID projectId, UUID userId, UUID currentUserId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dự án: " + projectId));

        UUID targetUserId = canonicalUserId(userId);

        if (targetUserId.equals(project.getLeadUserId()) || userId.equals(project.getLeadUserId())) {
            throw new BadRequestException("Không thể xóa Project Lead khỏi dự án");
        }

        List<RoleAssignment> assignments = roleAssignmentRepository.findByProjectIdAndUserId(projectId, targetUserId);
        boolean isAdmin = assignments.stream().anyMatch(a -> isAdministratorRole(a.getRoleId()));
        if (isAdmin) {
            assertNotLastAdministrator(projectId, targetUserId);
        }

        roleAssignmentRepository.deleteByProjectIdAndUserId(projectId, targetUserId);
        log.info("Member removed from project: user={}, project={} by {}", targetUserId, projectId, currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectRoleResponse> getProjectRoles(UUID projectId) {
        return projectRoleRepository.findByProjectId(projectId).stream()
                .map(r -> ProjectRoleResponse.builder()
                        .id(r.getId())
                        .projectId(r.getProjectId())
                        .name(r.getName())
                        .description(r.getDescription())
                        .createdAt(r.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private boolean isImplicitAdmin(Project project, UUID userId) {
        return userId.equals(project.getLeadUserId()) || userId.equals(project.getCreatedBy());
    }

    private static List<String> allPermissionKeys() {
        return Arrays.stream(PermissionKey.values()).map(Enum::name).collect(Collectors.toList());
    }

    private int rolePriority(String roleName) {
        if (ROLE_ADMINISTRATOR.equalsIgnoreCase(roleName)) return 0;
        if (ROLE_MEMBER.equalsIgnoreCase(roleName)) return 1;
        if (ROLE_VIEWER.equalsIgnoreCase(roleName)) return 2;
        return 3;
    }

    private static ProjectRole findRole(List<ProjectRole> roles, String name) {
        return roles.stream().filter(r -> name.equalsIgnoreCase(r.getName())).findFirst().orElse(null);
    }

    private void assertRoleBelongsToProject(ProjectRole role, UUID projectId) {
        if (role.getProjectId() != null && !role.getProjectId().equals(projectId)) {
            throw new BadRequestException("Vai trò không thuộc dự án này");
        }
    }

    private boolean isAdministratorRole(UUID roleId) {
        if (roleId == null) return false;
        return projectRoleRepository.findById(roleId)
                .map(r -> ROLE_ADMINISTRATOR.equalsIgnoreCase(r.getName()))
                .orElse(false);
    }

    /**
     * Ensures at least one other ADMINISTRATOR (or the Project Lead) remains after removing/demoting {@code userId}.
     */
    private void assertNotLastAdministrator(UUID projectId, UUID userId) {
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project != null && project.getLeadUserId() != null && !project.getLeadUserId().equals(userId)) {
            return; // Project Lead is always an administrator
        }
        List<ProjectRole> roles = projectRoleRepository.findByProjectId(projectId);
        ProjectRole adminRole = findRole(roles, ROLE_ADMINISTRATOR);
        if (adminRole == null) return;
        long otherAdmins = roleAssignmentRepository.findByProjectIdAndRoleId(projectId, adminRole.getId()).stream()
                .map(RoleAssignment::getUserId)
                .filter(id -> !id.equals(userId))
                .distinct()
                .count();
        if (otherAdmins == 0) {
            throw new BadRequestException("Dự án phải còn ít nhất một Administrator");
        }
    }

    /**
     * Normalizes any user identifier (identity DB id or Keycloak id) to the Keycloak id used by work-service.
     */
    private UUID canonicalUserId(UUID anyId) {
        if (anyId == null) return null;
        UserResponse user = fetchUser(anyId);
        if (user != null && user.getKeycloakId() != null) {
            try {
                return UUID.fromString(user.getKeycloakId());
            } catch (IllegalArgumentException ignored) {
            }
        }
        return anyId;
    }

    private UserResponse fetchUser(UUID userId) {
        if (userId == null) return null;
        try {
            var res = identityClient.getUserById(userId);
            if (res != null && res.getData() != null) {
                return res.getData();
            }
        } catch (Exception e) {
            log.debug("Could not fetch user {} from identity-service: {}", userId, e.getMessage());
        }
        return null;
    }
}
