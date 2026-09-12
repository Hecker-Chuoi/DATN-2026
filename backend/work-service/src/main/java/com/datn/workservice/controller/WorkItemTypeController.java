package com.datn.workservice.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.workservice.dto.request.CreateWorkItemTypeRequest;
import com.datn.workservice.dto.request.UpdateWorkItemTypeRequest;
import com.datn.workservice.dto.response.WorkItemTypeResponse;
import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.security.RequireProjectPermission;
import com.datn.workservice.security.SecurityUtils;
import com.datn.workservice.service.WorkItemTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/work/projects/{projectId}/types")
@RequiredArgsConstructor
public class WorkItemTypeController {

    /**
     * ──────────────────────────── DANH SÁCH API ────────────────────────────
     * GET    /api/v1/work/projects/{projectId}/types           : Lấy danh sách loại công việc của dự án
     * POST   /api/v1/work/projects/{projectId}/types           : Tạo loại công việc mới cho dự án
     * PUT    /api/v1/work/projects/{projectId}/types/{typeId}  : Cập nhật loại công việc
     * DELETE /api/v1/work/projects/{projectId}/types/{typeId}  : Xóa loại công việc
     * ────────────────────────────────────────────────────────────────────────
     */

    private final WorkItemTypeService workItemTypeService;

    @GetMapping
    @RequireProjectPermission(PermissionKey.PROJECT_VIEW)
    public ResponseEntity<ApiResponse<List<WorkItemTypeResponse>>> getProjectWorkItemTypes(
            @PathVariable("projectId") UUID projectId
    ) {
        List<WorkItemTypeResponse> responses = workItemTypeService.getProjectWorkItemTypes(projectId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @PostMapping
    @RequireProjectPermission(PermissionKey.PROJECT_ADMIN)
    public ResponseEntity<ApiResponse<WorkItemTypeResponse>> createWorkItemType(
            @PathVariable("projectId") UUID projectId,
            @Valid @RequestBody CreateWorkItemTypeRequest request
    ) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        WorkItemTypeResponse response = workItemTypeService.createWorkItemType(projectId, request, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Đã tạo loại công việc thành công"));
    }

    @PutMapping("/{typeId}")
    @RequireProjectPermission(PermissionKey.PROJECT_ADMIN)
    public ResponseEntity<ApiResponse<WorkItemTypeResponse>> updateWorkItemType(
            @PathVariable("projectId") UUID projectId,
            @PathVariable("typeId") UUID typeId,
            @Valid @RequestBody UpdateWorkItemTypeRequest request
    ) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        WorkItemTypeResponse response = workItemTypeService.updateWorkItemType(projectId, typeId, request, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(response, "Đã cập nhật loại công việc thành công"));
    }

    @DeleteMapping("/{typeId}")
    @RequireProjectPermission(PermissionKey.PROJECT_ADMIN)
    public ResponseEntity<ApiResponse<Void>> deleteWorkItemType(
            @PathVariable("projectId") UUID projectId,
            @PathVariable("typeId") UUID typeId
    ) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        workItemTypeService.deleteWorkItemType(projectId, typeId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã xóa loại công việc thành công"));
    }
}
