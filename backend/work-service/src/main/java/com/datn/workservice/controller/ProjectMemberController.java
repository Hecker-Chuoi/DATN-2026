package com.datn.workservice.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.workservice.dto.request.AssignProjectRoleRequest;
import com.datn.workservice.dto.response.ProjectRoleResponse;
import com.datn.workservice.dto.response.RoleAssignmentResponse;
import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.security.RequireProjectPermission;
import com.datn.workservice.security.SecurityUtils;
import com.datn.workservice.service.ProjectMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/work/projects/{projectId}")
@RequiredArgsConstructor
public class ProjectMemberController {

    private final ProjectMemberService projectMemberService;

    /**
     * Retrieves permissions of current user in this project.
     */
    @GetMapping("/permissions/my")
    public ResponseEntity<ApiResponse<List<String>>> getMyPermissions(@PathVariable("projectId") UUID projectId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        List<String> permissions = projectMemberService.getMyPermissions(projectId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(permissions));
    }

    /**
     * Retrieves all members and roles in this project.
     */
    @GetMapping("/members")
    @RequireProjectPermission(PermissionKey.PROJECT_VIEW)
    public ResponseEntity<ApiResponse<List<RoleAssignmentResponse>>> getProjectMembers(
            @PathVariable("projectId") UUID projectId
    ) {
        List<RoleAssignmentResponse> members = projectMemberService.getProjectMembers(projectId);
        return ResponseEntity.ok(ApiResponse.success(members));
    }

    /**
     * Adds a member with role to this project.
     */
    @PostMapping("/members")
    @RequireProjectPermission(PermissionKey.PROJECT_ADMIN)
    public ResponseEntity<ApiResponse<RoleAssignmentResponse>> addMemberToProject(
            @PathVariable("projectId") UUID projectId,
            @Valid @RequestBody AssignProjectRoleRequest request
    ) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        RoleAssignmentResponse response = projectMemberService.addMemberToProject(projectId, request, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Thêm thành viên vào dự án thành công"));
    }

    /**
     * Updates an existing member's role in this project.
     */
    @PutMapping("/members/{userId}/role")
    @RequireProjectPermission(PermissionKey.PROJECT_ADMIN)
    public ResponseEntity<ApiResponse<RoleAssignmentResponse>> updateMemberRole(
            @PathVariable("projectId") UUID projectId,
            @PathVariable("userId") UUID userId,
            @RequestBody Map<String, UUID> body
    ) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        UUID newRoleId = body.get("roleId");
        RoleAssignmentResponse response = projectMemberService.updateMemberRole(projectId, userId, newRoleId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(response, "Cập nhật vai trò thành viên thành công"));
    }

    /**
     * Removes a member from this project.
     */
    @DeleteMapping("/members/{userId}")
    @RequireProjectPermission(PermissionKey.PROJECT_ADMIN)
    public ResponseEntity<ApiResponse<Void>> removeMemberFromProject(
            @PathVariable("projectId") UUID projectId,
            @PathVariable("userId") UUID userId
    ) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        projectMemberService.removeMemberFromProject(projectId, userId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã xóa thành viên khỏi dự án"));
    }

    /**
     * Retrieves all project roles.
     */
    @GetMapping("/roles")
    @RequireProjectPermission(PermissionKey.PROJECT_VIEW)
    public ResponseEntity<ApiResponse<List<ProjectRoleResponse>>> getProjectRoles(
            @PathVariable("projectId") UUID projectId
    ) {
        List<ProjectRoleResponse> roles = projectMemberService.getProjectRoles(projectId);
        return ResponseEntity.ok(ApiResponse.success(roles));
    }
}
