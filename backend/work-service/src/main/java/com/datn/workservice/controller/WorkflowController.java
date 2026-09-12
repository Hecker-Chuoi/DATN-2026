package com.datn.workservice.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.workservice.dto.request.*;
import com.datn.workservice.dto.response.*;
import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.security.RequireProjectPermission;
import com.datn.workservice.service.WorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/work")
@RequiredArgsConstructor
public class WorkflowController {

    /**
     * ──────────────────────────── DANH SÁCH API ────────────────────────────
     * GET    /api/v1/work/projects/{projectId}/workflow                            : Lấy chi tiết workflow đang hoạt động của dự án
     * GET    /api/v1/work/projects/{projectId}/workflows                           : Lấy tất cả workflow của dự án
     * GET    /api/v1/work/projects/{projectId}/workflow/scheme                     : Lấy sơ đồ workflow & mapping loại công việc
     * PUT    /api/v1/work/projects/{projectId}/workflow/scheme                     : Cập nhật sơ đồ workflow của dự án
     * GET    /api/v1/work/projects/{projectId}/statuses                            : Lấy tất cả trạng thái trong dự án
     * POST   /api/v1/work/statuses                                                 : Tạo trạng thái mới
     * PUT    /api/v1/work/statuses/{statusId}                                      : Cập nhật trạng thái
     * DELETE /api/v1/work/statuses/{statusId}                                      : Xóa trạng thái (nếu không đang được sử dụng)
     * GET    /api/v1/work/workflows/{workflowId}                                   : Lấy chi tiết workflow theo ID
     * POST   /api/v1/work/workflows                                                : Tạo quy trình mới
     * PUT    /api/v1/work/workflows/{workflowId}                                   : Cập nhật thông tin quy trình
     * POST   /api/v1/work/workflows/{workflowId}/transitions                       : Thêm bước chuyển (transition) vào workflow
     * DELETE /api/v1/work/workflows/{workflowId}/transitions/{transitionId}        : Xóa bước chuyển khỏi workflow
     * ────────────────────────────────────────────────────────────────────────
     */

    private final WorkflowService workflowService;

    /**
     * Lấy chi tiết workflow đang hoạt động (bao gồm đầy đủ các bước chuyển và trạng thái) của dự án.
     */
    @GetMapping("/projects/{projectId}/workflow")
    @RequireProjectPermission(PermissionKey.PROJECT_VIEW)
    public ResponseEntity<ApiResponse<WorkflowDetailResponse>> getProjectWorkflowDetail(
            @PathVariable("projectId") UUID projectId
    ) {
        WorkflowDetailResponse detail = workflowService.getProjectWorkflowDetail(projectId);
        return ResponseEntity.ok(ApiResponse.success(detail));
    }

    /**
     * Lấy danh sách tất cả workflow đã cấu hình cho dự án.
     */
    @GetMapping("/projects/{projectId}/workflows")
    @RequireProjectPermission(PermissionKey.PROJECT_VIEW)
    public ResponseEntity<ApiResponse<List<WorkflowResponse>>> getProjectWorkflows(
            @PathVariable("projectId") UUID projectId
    ) {
        List<WorkflowResponse> workflows = workflowService.getProjectWorkflows(projectId);
        return ResponseEntity.ok(ApiResponse.success(workflows));
    }

    /**
     * Lấy sơ đồ workflow đang hoạt động và ánh xạ loại công việc (issue type) của dự án.
     */
    @GetMapping("/projects/{projectId}/workflow/scheme")
    @RequireProjectPermission(PermissionKey.PROJECT_VIEW)
    public ResponseEntity<ApiResponse<WorkflowSchemeResponse>> getProjectWorkflowScheme(
            @PathVariable("projectId") UUID projectId
    ) {
        WorkflowSchemeResponse scheme = workflowService.getProjectWorkflowScheme(projectId);
        return ResponseEntity.ok(ApiResponse.success(scheme));
    }

    /**
     * Cập nhật ánh xạ sơ đồ workflow cho dự án.
     */
    @PutMapping("/projects/{projectId}/workflow/scheme")
    @RequireProjectPermission(PermissionKey.PROJECT_ADMIN)
    public ResponseEntity<ApiResponse<WorkflowSchemeResponse>> updateProjectWorkflowScheme(
            @PathVariable("projectId") UUID projectId,
            @Valid @RequestBody UpdateWorkflowSchemeRequest request
    ) {
        WorkflowSchemeResponse updated = workflowService.updateProjectWorkflowScheme(projectId, request);
        return ResponseEntity.ok(ApiResponse.success(updated, "Cập nhật sơ đồ quy trình dự án thành công"));
    }

    /**
     * Lấy danh sách tất cả trạng thái khả dụng trong dự án.
     */
    @GetMapping("/projects/{projectId}/statuses")
    @RequireProjectPermission(PermissionKey.PROJECT_VIEW)
    public ResponseEntity<ApiResponse<List<StatusResponse>>> getProjectStatuses(
            @PathVariable("projectId") UUID projectId
    ) {
        List<StatusResponse> statuses = workflowService.getProjectStatuses(projectId);
        return ResponseEntity.ok(ApiResponse.success(statuses));
    }

    /**
     * Tạo trạng thái (status) mới trong hệ thống hoặc dự án.
     */
    @PostMapping("/statuses")
    public ResponseEntity<ApiResponse<StatusResponse>> createStatus(
            @Valid @RequestBody CreateStatusRequest request
    ) {
        StatusResponse created = workflowService.createStatus(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Tạo trạng thái mới thành công"));
    }

    /**
     * Tạo trạng thái (status) mới dành riêng cho dự án.
     */
    @PostMapping("/projects/{projectId}/statuses")
    @RequireProjectPermission(PermissionKey.PROJECT_ADMIN)
    public ResponseEntity<ApiResponse<StatusResponse>> createProjectStatus(
            @PathVariable("projectId") UUID projectId,
            @Valid @RequestBody CreateStatusRequest request
    ) {
        request.setProjectId(projectId);
        StatusResponse created = workflowService.createStatus(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Tạo trạng thái mới cho dự án thành công"));
    }

    /**
     * Cập nhật thông tin trạng thái đã tồn tại trong hệ thống.
     */
    @PutMapping("/statuses/{statusId}")
    public ResponseEntity<ApiResponse<StatusResponse>> updateStatus(
            @PathVariable("statusId") UUID statusId,
            @Valid @RequestBody UpdateStatusRequest request
    ) {
        StatusResponse updated = workflowService.updateStatus(statusId, request);
        return ResponseEntity.ok(ApiResponse.success(updated, "Cập nhật trạng thái thành công"));
    }

    /**
     * Xóa trạng thái khỏi hệ thống (nếu không đang được sử dụng).
     */
    @DeleteMapping("/statuses/{statusId}")
    public ResponseEntity<ApiResponse<Void>> deleteStatus(
            @PathVariable("statusId") UUID statusId
    ) {
        workflowService.deleteStatus(statusId);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa trạng thái thành công"));
    }

    /**
     * Lấy chi tiết workflow theo ID.
     */
    @GetMapping("/workflows/{workflowId}")
    public ResponseEntity<ApiResponse<WorkflowDetailResponse>> getWorkflowById(
            @PathVariable("workflowId") UUID workflowId
    ) {
        WorkflowDetailResponse detail = workflowService.getWorkflowById(workflowId);
        return ResponseEntity.ok(ApiResponse.success(detail));
    }

    /**
     * Tạo quy trình (workflow) mới.
     */
    @PostMapping("/workflows")
    public ResponseEntity<ApiResponse<WorkflowResponse>> createWorkflow(
            @Valid @RequestBody CreateWorkflowRequest request
    ) {
        WorkflowResponse created = workflowService.createWorkflow(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Tạo quy trình thành công"));
    }

    /**
     * Cập nhật thông tin quy trình (workflow).
     */
    @PutMapping("/workflows/{workflowId}")
    public ResponseEntity<ApiResponse<WorkflowResponse>> updateWorkflow(
            @PathVariable("workflowId") UUID workflowId,
            @Valid @RequestBody UpdateWorkflowRequest request
    ) {
        WorkflowResponse updated = workflowService.updateWorkflow(workflowId, request);
        return ResponseEntity.ok(ApiResponse.success(updated, "Cập nhật quy trình thành công"));
    }

    /**
     * Thêm bước chuyển (transition) mới vào workflow.
     */
    @PostMapping("/workflows/{workflowId}/transitions")
    public ResponseEntity<ApiResponse<WorkflowTransitionResponse>> createTransition(
            @PathVariable("workflowId") UUID workflowId,
            @Valid @RequestBody CreateWorkflowTransitionRequest request
    ) {
        request.setWorkflowId(workflowId);
        WorkflowTransitionResponse created = workflowService.createTransition(workflowId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Tạo bước chuyển quy trình thành công"));
    }

    /**
     * Cập nhật bước chuyển (transition) trong workflow.
     */
    @PutMapping("/workflows/{workflowId}/transitions/{transitionId}")
    public ResponseEntity<ApiResponse<WorkflowTransitionResponse>> updateTransition(
            @PathVariable("workflowId") UUID workflowId,
            @PathVariable("transitionId") UUID transitionId,
            @Valid @RequestBody UpdateWorkflowTransitionRequest request
    ) {
        WorkflowTransitionResponse updated = workflowService.updateTransition(workflowId, transitionId, request);
        return ResponseEntity.ok(ApiResponse.success(updated, "Cập nhật bước chuyển quy trình thành công"));
    }

    /**
     * Xóa bước chuyển (transition) khỏi workflow.
     */
    @DeleteMapping("/workflows/{workflowId}/transitions/{transitionId}")
    public ResponseEntity<ApiResponse<Void>> deleteTransition(
            @PathVariable("workflowId") UUID workflowId,
            @PathVariable("transitionId") UUID transitionId
    ) {
        workflowService.deleteTransition(workflowId, transitionId);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa bước chuyển thành công"));
    }
}
