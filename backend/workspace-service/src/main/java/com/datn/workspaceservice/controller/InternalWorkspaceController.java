package com.datn.workspaceservice.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.workspaceservice.dto.request.EnsureWorkspaceMemberRequest;
import com.datn.workspaceservice.dto.response.WorkspaceResponse;
import com.datn.workspaceservice.enums.WorkspaceRole;
import com.datn.workspaceservice.service.WorkspaceMemberService;
import com.datn.workspaceservice.service.WorkspaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * API nội bộ service-to-service, mirror InternalUserController của identity-service.
 * <p>
 * KHÔNG xác thực (SecurityConfig permit /internal/**) và KHÔNG được route qua
 * api-gateway: predicate ở gateway chỉ khớp /workspace/api/** và
 * /workspace/actuator/**, nên nhóm này chỉ tới được từ trong mạng backend.
 * <p>
 * Người tiêu thụ: work-service, identity-service để scope truy vấn và tạo workspace mặc định.
 */
@Slf4j
@RestController
@RequestMapping("/internal/workspaces")
@RequiredArgsConstructor
public class InternalWorkspaceController {

    private final WorkspaceMemberService memberService;
    private final WorkspaceService workspaceService;

    /**
     * Vai trò của user trong workspace.
     *
     * @return data = null khi user không phải thành viên (không phải lỗi - phía
     *         gọi tự quyết định coi đó là từ chối)
     */
    @GetMapping("/{workspaceId}/members/{userId}/role")
    public ResponseEntity<ApiResponse<String>> getMemberRole(@PathVariable UUID workspaceId,
                                                             @PathVariable UUID userId) {
        String role = memberService.findRole(workspaceId, userId)
                .map(WorkspaceRole::name)
                .orElse(null);
        return ResponseEntity.ok(ApiResponse.success(role));
    }

    /** Danh sách workspace id của user - đã loại workspace soft-delete. */
    @GetMapping("/users/{userId}/ids")
    public ResponseEntity<ApiResponse<List<UUID>>> getWorkspaceIds(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(workspaceService.getWorkspaceIdsOfUser(userId)));
    }

    /** Workspace có tồn tại và chưa bị soft-delete hay không. */
    @GetMapping("/{workspaceId}/exists")
    public ResponseEntity<ApiResponse<Boolean>> exists(@PathVariable UUID workspaceId) {
        return ResponseEntity.ok(ApiResponse.success(workspaceService.existsActive(workspaceId)));
    }

    /** Tự động khởi tạo default workspace cho user mới */
    @PostMapping("/users/{userId}/default")
    public ResponseEntity<ApiResponse<WorkspaceResponse>> createDefaultWorkspace(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(workspaceService.createDefaultWorkspace(userId)));
    }

    /**
     * Tự động liên đới thành viên vào workspace (Smart Auto-Cascade).
     * Dùng khi user được thêm hoặc chấp nhận vào một Project thuộc workspace này.
     */
    @PostMapping("/{workspaceId}/members/ensure")
    public ResponseEntity<ApiResponse<Void>> ensureMember(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody EnsureWorkspaceMemberRequest request) {
        WorkspaceRole role = WorkspaceRole.MEMBER;
        if (request.getRole() != null) {
            try {
                role = WorkspaceRole.valueOf(request.getRole().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }
        memberService.ensureMember(workspaceId, request.getUserId(), role, request.getInvitedBy());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
