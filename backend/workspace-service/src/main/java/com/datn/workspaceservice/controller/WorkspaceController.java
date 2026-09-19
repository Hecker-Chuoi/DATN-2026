package com.datn.workspaceservice.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.workspaceservice.dto.request.CreateWorkspaceRequest;
import com.datn.workspaceservice.dto.request.UpdateWorkspaceRequest;
import com.datn.workspaceservice.dto.response.WorkspaceResponse;
import com.datn.workspaceservice.enums.WorkspaceRole;
import com.datn.workspaceservice.security.CurrentUserResolver;
import com.datn.workspaceservice.security.RequireWorkspaceRole;
import com.datn.workspaceservice.service.WorkspaceMemberService;
import com.datn.workspaceservice.service.WorkspaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * API workspace. Qua gateway, đường dẫn ngoài là /workspace/api/v1/workspaces/**
 * (StripPrefix=1).
 * <p>
 * Theo style của ProjectMemberController: DTO có kiểu kèm Valid, 201 CREATED khi
 * tạo. KHÔNG theo TeamController (nhồi ~540 dòng logic vào controller và dùng
 * Map nên Valid không bao giờ chạy).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final WorkspaceMemberService memberService;
    private final CurrentUserResolver currentUserResolver;

    /** Bất kỳ user đã xác thực. Người tạo trở thành OWNER. */
    @PostMapping
    public ResponseEntity<ApiResponse<WorkspaceResponse>> create(
            @Valid @RequestBody CreateWorkspaceRequest request) {
        UUID currentUserId = currentUserResolver.currentUserId();
        WorkspaceResponse created = workspaceService.createWorkspace(request, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Đã tạo workspace"));
    }

    /**
     * Chỉ những workspace mà người gọi có hàng membership.
     * Cố tình KHÔNG có endpoint liệt kê toàn cục.
     */
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<WorkspaceResponse>>> listMine() {
        UUID currentUserId = currentUserResolver.currentUserId();
        return ResponseEntity.ok(ApiResponse.success(workspaceService.getMyWorkspaces(currentUserId)));
    }

    /** Cần là thành viên, vai trò nào cũng được. Người ngoài nhận 404. */
    @GetMapping("/{workspaceId}")
    public ResponseEntity<ApiResponse<WorkspaceResponse>> getById(@PathVariable UUID workspaceId) {
        UUID currentUserId = currentUserResolver.currentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                workspaceService.getWorkspaceById(workspaceId, currentUserId)));
    }

    @PutMapping("/{workspaceId}")
    @RequireWorkspaceRole(WorkspaceRole.ADMIN)
    public ResponseEntity<ApiResponse<WorkspaceResponse>> update(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody UpdateWorkspaceRequest request) {
        UUID currentUserId = currentUserResolver.currentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                workspaceService.updateWorkspace(workspaceId, request, currentUserId),
                "Đã cập nhật workspace"));
    }

    /** Soft delete. Chỉ OWNER. */
    @DeleteMapping("/{workspaceId}")
    @RequireWorkspaceRole(WorkspaceRole.OWNER)
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID workspaceId) {
        UUID currentUserId = currentUserResolver.currentUserId();
        workspaceService.deleteWorkspace(workspaceId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(null, "Workspace đã được chuyển vào thùng rác"));
    }

    /**
     * Chỉ OWNER. Guard vẫn hoạt động trên workspace đã soft-delete vì nó tra vai
     * trò qua bảng workspace_members, không phụ thuộc cột deleted_at.
     */
    @PostMapping("/{workspaceId}/restore")
    @RequireWorkspaceRole(WorkspaceRole.OWNER)
    public ResponseEntity<ApiResponse<WorkspaceResponse>> restore(@PathVariable UUID workspaceId) {
        UUID currentUserId = currentUserResolver.currentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                workspaceService.restoreWorkspace(workspaceId, currentUserId),
                "Đã phục hồi workspace"));
    }

    /** Mọi thành viên đều rời được, TRỪ khi đang là OWNER duy nhất. */
    @PostMapping("/{workspaceId}/leave")
    public ResponseEntity<ApiResponse<Void>> leave(@PathVariable UUID workspaceId) {
        UUID currentUserId = currentUserResolver.currentUserId();
        memberService.leaveWorkspace(workspaceId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã rời workspace"));
    }
}
