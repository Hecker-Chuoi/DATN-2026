package com.datn.workservice.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.workservice.dto.request.CreateWorkItemLinkRequest;
import com.datn.workservice.dto.response.WorkItemLinkResponse;
import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.security.RequireTaskPermission;
import com.datn.workservice.security.SecurityUtils;
import com.datn.workservice.service.WorkItemLinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/work")
@RequiredArgsConstructor
public class WorkItemLinkController {

    /**
     * ──────────────────────────── DANH SÁCH API ────────────────────────────
     * GET    /api/v1/work/items/{itemId}/links | /work-items/{itemId}/links              : Lấy danh sách liên kết của công việc
     * POST   /api/v1/work/items/{itemId}/links | /work-items/{itemId}/links              : Tạo liên kết giữa hai công việc
     * DELETE /api/v1/work/items/{itemId}/links/{linkId} | /work-items/{itemId}/links/{linkId} : Xóa liên kết của công việc
     * ────────────────────────────────────────────────────────────────────────
     */

    private final WorkItemLinkService linkService;

    @GetMapping({"/items/{itemId}/links", "/work-items/{itemId}/links"})
    @RequireTaskPermission(PermissionKey.PROJECT_VIEW)
    public ResponseEntity<ApiResponse<List<WorkItemLinkResponse>>> getLinks(
            @PathVariable("itemId") UUID workItemId
    ) {
        List<WorkItemLinkResponse> links = linkService.getLinks(workItemId);
        return ResponseEntity.ok(ApiResponse.success(links));
    }

    @PostMapping({"/items/{itemId}/links", "/work-items/{itemId}/links"})
    @RequireTaskPermission(PermissionKey.ISSUE_EDIT)
    public ResponseEntity<ApiResponse<WorkItemLinkResponse>> createLink(
            @PathVariable("itemId") UUID workItemId,
            @Valid @RequestBody CreateWorkItemLinkRequest request
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        WorkItemLinkResponse response = linkService.createLink(workItemId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Đã thiết lập liên kết thành công"));
    }

    @DeleteMapping({"/items/{itemId}/links/{linkId}", "/work-items/{itemId}/links/{linkId}"})
    @RequireTaskPermission(PermissionKey.ISSUE_EDIT)
    public ResponseEntity<ApiResponse<Void>> deleteLink(
            @PathVariable("itemId") UUID workItemId,
            @PathVariable("linkId") UUID linkId
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        linkService.deleteLink(workItemId, linkId, userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã xóa liên kết thành công"));
    }
}
