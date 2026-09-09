package com.datn.workservice.service;

import com.datn.workservice.dto.request.AssignProjectRoleRequest;
import com.datn.workservice.dto.response.MyProjectPermissionsResponse;
import com.datn.workservice.dto.response.ProjectRoleResponse;
import com.datn.workservice.dto.response.RoleAssignmentResponse;
import com.datn.workservice.enums.PermissionKey;

import java.util.List;
import java.util.UUID;

public interface ProjectMemberService {

    /**
     * Checks if a user has a specific granular permission within a project.
     * Deny-by-default: users without any role assignment in the project have NO permission
     * (except Project Lead / creator who are implicit administrators).
     */
    boolean hasPermission(UUID projectId, UUID userId, PermissionKey permissionKey);

    /**
     * Retrieves role and permission keys granted to the current user in a project.
     * Throws AccessDeniedException when the user is not a member (and not Global ADMIN).
     */
    MyProjectPermissionsResponse getMyPermissions(UUID projectId, UUID currentUserId, boolean isGlobalAdmin);

    /**
     * Retrieves all members and their assigned roles in a project (read-only).
     */
    List<RoleAssignmentResponse> getProjectMembers(UUID projectId);

    /**
     * Assigns a role to a user in a project (adds member).
     */
    RoleAssignmentResponse addMemberToProject(UUID projectId, AssignProjectRoleRequest request, UUID currentUserId);

    /**
     * Updates an existing member's role in a project.
     */
    RoleAssignmentResponse updateMemberRole(UUID projectId, UUID userId, UUID newRoleId, UUID currentUserId);

    /**
     * Removes a member from a project.
     */
    void removeMemberFromProject(UUID projectId, UUID userId, UUID currentUserId);

    /**
     * Retrieves all available roles in a project.
     */
    List<ProjectRoleResponse> getProjectRoles(UUID projectId);

    /**
     * Synchronizes members of the project's team into role_assignments
     * (LEADER -> ADMINISTRATOR, MEMBER -> MEMBER, VIEWER -> VIEWER). Existing assignments are kept.
     */
    void syncTeamMembersToProject(UUID projectId);
}
