package com.datn.workservice.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.workservice.dto.request.RejectCompletionRequest;
import com.datn.workservice.dto.response.CompletionRequestResponse;
import com.datn.workservice.entity.WorkItem;
import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.security.RequireProjectPermission;
import com.datn.workservice.security.RequireTaskPermission;
import com.datn.workservice.security.SecurityUtils;
import com.datn.workservice.service.WorkItemCompletionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * "Tạm hoàn thành" → Admin dự án xác nhận Hoàn thành (BR-W03..BR-W08).
 */
@RestController
@RequestMapping("/api/v1/work")
@RequiredArgsConstructor
public class WorkItemCompletionController {

    /**
     * ──────────────────────────── DANH SÁCH API ────────────────────────────
     * GET    /api/v1/work/projects/{projectId}/completion-requests : Các yêu cầu đang chờ xác nhận của dự án
     * GET    /api/v1/work/items/{id}/completion-requests           : Lịch sử yêu cầu xác nhận của một công việc
     * POST   /api/v1/work/items/{id}/completion/approve            : Admin dự án xác nhận Hoàn thành
     * POST   /api/v1/work/items/{id}/completion/reject             : Admin dự án từ chối (kèm lý do)
     * DELETE /api/v1/work/items/{id}/completion                    : Người gửi / Admin rút lại yêu cầu
     * ────────────────────────────────────────────────────────────────────────
     */

    private final WorkItemCompletionService completionService;

    @GetMapping("/projects/{projectId}/completion-requests")
    @RequireProjectPermission(PermissionKey.PROJECT_VIEW)
    public ResponseEntity<ApiResponse<List<CompletionRequestResponse>>> listPending(@PathVariable("projectId") UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success(completionService.listPending(projectId)));
    }

    @GetMapping("/items/{id}/completion-requests")
    @RequireTaskPermission(PermissionKey.PROJECT_VIEW)
    public ResponseEntity<ApiResponse<List<CompletionRequestResponse>>> history(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResponse.success(completionService.getHistory(id)));
    }

    @PostMapping("/items/{id}/completion/approve")
    @RequireTaskPermission(PermissionKey.PROJECT_ADMIN)
    public ResponseEntity<ApiResponse<WorkItem>> approve(@PathVariable("id") UUID id) {
        WorkItem updated = completionService.approve(id, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(updated, "Đã xác nhận hoàn thành công việc"));
    }

    @PostMapping("/items/{id}/completion/reject")
    @RequireTaskPermission(PermissionKey.PROJECT_ADMIN)
    public ResponseEntity<ApiResponse<CompletionRequestResponse>> reject(
            @PathVariable("id") UUID id,
            @Valid @RequestBody(required = false) RejectCompletionRequest request
    ) {
        String reason = request != null ? request.getReason() : null;
        return ResponseEntity.ok(ApiResponse.success(
                completionService.reject(id, reason, SecurityUtils.getCurrentUserId()),
                "Đã từ chối yêu cầu xác nhận hoàn thành"));
    }

    @DeleteMapping("/items/{id}/completion")
    @RequireTaskPermission(PermissionKey.PROJECT_VIEW)
    public ResponseEntity<ApiResponse<CompletionRequestResponse>> cancel(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                completionService.cancel(id, SecurityUtils.getCurrentUserId()),
                "Đã hủy yêu cầu xác nhận hoàn thành"));
    }
}
