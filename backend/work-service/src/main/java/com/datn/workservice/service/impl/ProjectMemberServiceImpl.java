package com.datn.workservice.service.impl;

import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workservice.client.IdentityClient;
import com.datn.workservice.client.dto.UserResponse;
import com.datn.workservice.dto.request.AssignProjectRoleRequest;
import com.datn.workservice.dto.response.ProjectRoleResponse;
import com.datn.workservice.dto.response.RoleAssignmentResponse;
import com.datn.workservice.entity.Project;
import com.datn.workservice.entity.ProjectRole;
import com.datn.workservice.entity.RoleAssignment;
import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.repository.PermissionSchemeEntryRepository;
import com.datn.workservice.repository.ProjectRepository;
import com.datn.workservice.repository.ProjectRoleRepository;
import com.datn.workservice.repository.RoleAssignmentRepository;
import com.datn.workservice.service.ProjectMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectMemberServiceImpl implements ProjectMemberService {

    private final ProjectRepository projectRepository;
    private final ProjectRoleRepository projectRoleRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final PermissionSchemeEntryRepository permissionSchemeEntryRepository;
    private final IdentityClient identityClient;

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

        // 0. System Admin (ROLE_ADMIN) has all project permissions
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities() != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equalsIgnoreCase(a.getAuthority()) || "ADMIN".equalsIgnoreCase(a.getAuthority()))) {
            return true;
        }

        // 1. Project Lead & Creator have super-admin privileges over the project
        if (userId.equals(project.getLeadUserId()) || userId.equals(project.getCreatedBy())) {
            return true;
        }

        // 2. Fetch user's assigned roles in this project
        List<RoleAssignment> assignments = roleAssignmentRepository.findByProjectIdAndUserId(projectId, userId);
        if (assignments.isEmpty()) {
            // Allow general view for any authenticated user having access to the project
            return permissionKey == PermissionKey.PROJECT_VIEW;
        }

        // 3. Check if any assigned role is ADMINISTRATOR or has explicit permission entry
        for (RoleAssignment assignment : assignments) {
            ProjectRole role = projectRoleRepository.findById(assignment.getRoleId()).orElse(null);
            if (role != null) {
                if ("ADMINISTRATOR".equalsIgnoreCase(role.getName())) {
                    return true;
                }
                boolean hasEntry = permissionSchemeEntryRepository.existsByProjectIdAndRoleIdAndPermissionKey(
                        projectId, role.getId(), permissionKey.name()
                );
                if (hasEntry) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getMyPermissions(UUID projectId, UUID currentUserId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dự án: " + projectId));

        // If user has system role ROLE_ADMIN, grant all permissions
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities() != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equalsIgnoreCase(a.getAuthority()) || "ADMIN".equalsIgnoreCase(a.getAuthority()))) {
            return Arrays.stream(PermissionKey.values())
                    .map(Enum::name)
                    .collect(Collectors.toList());
        }

        // If user is Lead or Creator, grant all permissions
        if (currentUserId.equals(project.getLeadUserId()) || currentUserId.equals(project.getCreatedBy())) {
            return Arrays.stream(PermissionKey.values())
                    .map(Enum::name)
                    .collect(Collectors.toList());
        }

        List<RoleAssignment> assignments = roleAssignmentRepository.findByProjectIdAndUserId(projectId, currentUserId);
        if (assignments.isEmpty()) {
            return List.of(PermissionKey.PROJECT_VIEW.name());
        }

        Set<String> permissions = new HashSet<>();
        permissions.add(PermissionKey.PROJECT_VIEW.name());
        for (RoleAssignment assignment : assignments) {
            ProjectRole role = projectRoleRepository.findById(assignment.getRoleId()).orElse(null);
            if (role != null) {
                if ("ADMINISTRATOR".equalsIgnoreCase(role.getName())) {
                    return Arrays.stream(PermissionKey.values())
                            .map(Enum::name)
                            .collect(Collectors.toList());
                }
                permissionSchemeEntryRepository.findByProjectIdAndRoleId(projectId, role.getId())
                        .forEach(entry -> permissions.add(entry.getPermissionKey()));
            }
        }

        return new ArrayList<>(permissions);
    }

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

        // Fetch roles
        Map<UUID, String> roleMap = projectRoleRepository.findByProjectId(projectId).stream()
                .collect(Collectors.toMap(ProjectRole::getId, ProjectRole::getName, (a, b) -> a));

        return assignments.stream().map(a -> {
            UserResponse user = userMap.get(a.getUserId());
            String roleName = roleMap.getOrDefault(a.getRoleId(), "MEMBER");

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
    public RoleAssignmentResponse addMemberToProject(UUID projectId, AssignProjectRoleRequest request, UUID currentUserId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dự án: " + projectId));

        ProjectRole role = projectRoleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vai trò: " + request.getRoleId()));

        if (roleAssignmentRepository.existsByProjectIdAndUserIdAndRoleId(projectId, request.getUserId(), request.getRoleId())) {
            throw new BadRequestException("Người dùng đã có vai trò này trong dự án");
        }

        RoleAssignment assignment = RoleAssignment.builder()
                .projectId(projectId)
                .userId(request.getUserId())
                .roleId(request.getRoleId())
                .build();

        RoleAssignment saved = roleAssignmentRepository.save(assignment);
        log.info("Member added to project: user={}, role={}, project={}", request.getUserId(), role.getName(), projectId);

        // Fetch user info
        String username = null;
        String email = null;
        String fullName = null;
        try {
            var userRes = identityClient.getUserById(request.getUserId());
            if (userRes != null && userRes.getData() != null) {
                username = userRes.getData().getUsername();
                email = userRes.getData().getEmail();
                fullName = userRes.getData().getFullName();
            }
        } catch (Exception ignored) {
        }

        return RoleAssignmentResponse.builder()
                .id(saved.getId())
                .projectId(saved.getProjectId())
                .userId(saved.getUserId())
                .roleId(saved.getRoleId())
                .roleName(role.getName())
                .assignedAt(saved.getAssignedAt())
                .username(username)
                .email(email)
                .fullName(fullName)
                .build();
    }

    @Override
    @Transactional
    public RoleAssignmentResponse updateMemberRole(UUID projectId, UUID userId, UUID newRoleId, UUID currentUserId) {
        ProjectRole role = projectRoleRepository.findById(newRoleId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vai trò mới: " + newRoleId));

        List<RoleAssignment> assignments = roleAssignmentRepository.findByProjectIdAndUserId(projectId, userId);
        if (assignments.isEmpty()) {
            throw new ResourceNotFoundException("Thành viên không thuộc dự án này");
        }

        RoleAssignment assignment = assignments.get(0);
        assignment.setRoleId(newRoleId);
        RoleAssignment saved = roleAssignmentRepository.save(assignment);

        log.info("Member role updated: user={}, newRole={}, project={}", userId, role.getName(), projectId);

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

        if (userId.equals(project.getLeadUserId())) {
            throw new BadRequestException("Không thể xóa Project Lead khỏi dự án");
        }

        roleAssignmentRepository.deleteByProjectIdAndUserId(projectId, userId);
        log.info("Member removed from project: user={}, project={}", userId, projectId);
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
}
