package com.datn.workservice.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.workservice.dto.request.AssignProjectRoleRequest;
import com.datn.workservice.dto.response.MyProjectPermissionsResponse;
import com.datn.workservice.dto.response.ProjectRoleResponse;
import com.datn.workservice.dto.response.InvitationResponse;
import com.datn.workservice.dto.response.RoleAssignmentResponse;
import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.security.RequireProjectPermission;
import com.datn.workservice.security.SecurityUtils;
import com.datn.workservice.service.InvitationService;
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

    /**
     * ──────────────────────────── DANH SÁCH API ────────────────────────────
     * GET    /api/v1/work/projects/{projectId}/permissions/my         : Lấy quyền của người dùng hiện tại trong dự án
     * GET    /api/v1/work/projects/{projectId}/members                : Lấy danh sách thành viên & vai trò trong dự án
     * POST   /api/v1/work/projects/{projectId}/members                : Thêm thành viên vào dự án (Project Admin)
     * PUT    /api/v1/work/projects/{projectId}/members/{userId}/role  : Cập nhật vai trò thành viên (Project Admin)
     * DELETE /api/v1/work/projects/{projectId}/members/{userId}       : Xóa thành viên khỏi dự án (Project Admin)
     * GET    /api/v1/work/projects/{projectId}/roles                  : Lấy danh sách vai trò của dự án
     * ────────────────────────────────────────────────────────────────────────
     */

    private final ProjectMemberService projectMemberService;
    private final InvitationService invitationService;

    /**
     * Retrieves role & permissions of current user in this project.
     * Returns 403 when the user is not a member of the project (frontend uses this as access check).
     */
    @GetMapping("/permissions/my")
    public ResponseEntity<ApiResponse<MyProjectPermissionsResponse>> getMyPermissions(@PathVariable("projectId") UUID projectId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        MyProjectPermissionsResponse permissions = projectMemberService.getMyPermissions(
                projectId, currentUserId, SecurityUtils.isGlobalAdmin());
        return ResponseEntity.ok(ApiResponse.success(permissions));
    }

    /**
     * Lấy danh sách tất cả thành viên và vai trò trong dự án.
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
     * Thêm thành viên mới (kèm vai trò) vào dự án.
     */
    @PostMapping("/members")
    @RequireProjectPermission(PermissionKey.PROJECT_ADMIN)
    public ResponseEntity<ApiResponse<InvitationResponse>> addMemberToProject(
            @PathVariable("projectId") UUID projectId,
            @Valid @RequestBody AssignProjectRoleRequest request
    ) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        // Không thêm trực tiếp: gửi lời mời, người được mời chấp nhận mới có role_assignment (InvitationService.accept)
        InvitationResponse response = invitationService.inviteToProject(projectId, request.getUserId(), request.getRoleId(), currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Đã gửi lời mời tham gia dự án"));
    }

    /**
     * Cập nhật vai trò của thành viên đã tồn tại trong dự án.
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
     * Xóa thành viên khỏi dự án.
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
     * Lấy danh sách tất cả vai trò của dự án.
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
