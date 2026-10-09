package com.datn.workspaceservice.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.common.dto.PageResponse;
import com.datn.workspaceservice.dto.request.AddWorkspaceMemberRequest;
import com.datn.workspaceservice.dto.request.UpdateMemberRoleRequest;
import com.datn.workspaceservice.dto.response.WorkspaceInvitationResponse;
import com.datn.workspaceservice.dto.response.WorkspaceMemberResponse;
import com.datn.workspaceservice.enums.WorkspaceRole;
import com.datn.workspaceservice.security.CurrentUserResolver;
import com.datn.workspaceservice.security.RequireWorkspaceRole;
import com.datn.workspaceservice.service.WorkspaceMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/members")
@RequiredArgsConstructor
public class WorkspaceMemberController {

    private final WorkspaceMemberService memberService;
    private final CurrentUserResolver currentUserResolver;

    /** Cần là thành viên, vai trò nào cũng được. Người ngoài nhận 404. */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<WorkspaceMemberResponse>>> list(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) WorkspaceRole role,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID currentUserId = currentUserResolver.currentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                memberService.getMembers(workspaceId, currentUserId, search, role, pageable)));
    }

    /** Frontend dùng để gate UI theo vai trò. */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<WorkspaceMemberResponse>> getMine(@PathVariable UUID workspaceId) {
        UUID currentUserId = currentUserResolver.currentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                memberService.getMyMembership(workspaceId, currentUserId)));
    }

    /** Không thêm thẳng: tạo lời mời, người được mời chấp nhận mới thành thành viên. */
    @PostMapping
    @RequireWorkspaceRole(WorkspaceRole.ADMIN)
    public ResponseEntity<ApiResponse<WorkspaceInvitationResponse>> add(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody AddWorkspaceMemberRequest request) {
        UUID currentUserId = currentUserResolver.currentUserId();
        WorkspaceInvitationResponse invitation = memberService.invite(workspaceId, request, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(invitation, "Đã gửi lời mời. Người được mời cần chấp nhận để trở thành thành viên."));
    }

    /** Lời mời đang chờ của workspace (phía người quản trị). Đặt dưới /members để aspect resolve được workspaceId. */
    @GetMapping("/invitations")
    @RequireWorkspaceRole(WorkspaceRole.ADMIN)
    public ResponseEntity<ApiResponse<List<WorkspaceInvitationResponse>>> invitations(@PathVariable UUID workspaceId) {
        return ResponseEntity.ok(ApiResponse.success(memberService.listInvitations(workspaceId)));
    }

    @PutMapping("/{userId}/role")
    @RequireWorkspaceRole(WorkspaceRole.ADMIN)
    public ResponseEntity<ApiResponse<WorkspaceMemberResponse>> updateRole(
            @PathVariable UUID workspaceId,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateMemberRoleRequest request) {
        UUID currentUserId = currentUserResolver.currentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                memberService.updateMemberRole(workspaceId, userId, request.getRole(), currentUserId),
                "Đã cập nhật vai trò thành viên"));
    }

    @DeleteMapping("/{userId}")
    @RequireWorkspaceRole(WorkspaceRole.ADMIN)
    public ResponseEntity<ApiResponse<Void>> remove(
            @PathVariable UUID workspaceId,
            @PathVariable UUID userId) {
        UUID currentUserId = currentUserResolver.currentUserId();
        memberService.removeMember(workspaceId, userId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã xóa thành viên khỏi workspace"));
    }
}
