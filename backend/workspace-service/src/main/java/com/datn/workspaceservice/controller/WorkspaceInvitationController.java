package com.datn.workspaceservice.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.workspaceservice.dto.response.WorkspaceInvitationResponse;
import com.datn.workspaceservice.security.CurrentUserResolver;
import com.datn.workspaceservice.service.WorkspaceMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Lời mời workspace phía người được mời / người mời.
 * Không có {workspaceId} trên path nên không dùng @RequireWorkspaceRole; quyền kiểm tra trong service
 * (accept/decline = invitee, cancel = người mời hoặc ADMIN+ của workspace).
 * Path tách khỏi /api/v1/workspaces/** để "my" không bị bắt bởi GET /api/v1/workspaces/{workspaceId}.
 */
@RestController
@RequestMapping("/api/v1/workspace-invitations")
@RequiredArgsConstructor
public class WorkspaceInvitationController {

    private final WorkspaceMemberService memberService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<WorkspaceInvitationResponse>>> my() {
        return ResponseEntity.ok(ApiResponse.success(
                memberService.listMyInvitations(currentUserResolver.currentUserId())));
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<ApiResponse<WorkspaceInvitationResponse>> accept(@PathVariable UUID id) {
        WorkspaceInvitationResponse res = memberService.acceptInvitation(id, currentUserResolver.currentUserId());
        return ResponseEntity.ok(ApiResponse.success(res, "Bạn đã tham gia workspace " + res.getWorkspaceName()));
    }

    @PostMapping("/{id}/decline")
    public ResponseEntity<ApiResponse<WorkspaceInvitationResponse>> decline(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                memberService.declineInvitation(id, currentUserResolver.currentUserId()), "Đã từ chối lời mời"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<WorkspaceInvitationResponse>> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                memberService.cancelInvitation(id, currentUserResolver.currentUserId()), "Đã hủy lời mời"));
    }
}
