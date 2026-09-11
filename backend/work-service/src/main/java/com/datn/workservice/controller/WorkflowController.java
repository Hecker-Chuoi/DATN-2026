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
     * GET    /api/v1/work/workflows/{workflowId}                                   : Lấy chi tiết workflow theo ID
     * POST   /api/v1/work/workflows/{workflowId}/transitions                       : Thêm bước chuyển (transition) vào workflow
     * DELETE /api/v1/work/workflows/{workflowId}/transitions/{transitionId}        : Xóa bước chuyển khỏi workflow
     * ────────────────────────────────────────────────────────────────────────
     */

    private final WorkflowService workflowService;

    /**
     * Retrieves the primary active workflow detail with full transitions and statuses for a project.
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
     * Retrieves all workflows configured for a project.
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
     * Retrieves the active workflow scheme and issue type mappings for a project.
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
     * Updates workflow scheme mappings for a project.
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
     * Retrieves all statuses available in the project.
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
     * Creates a new status in the system.
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
     * Retrieves details of a specific workflow by ID.
     */
    @GetMapping("/workflows/{workflowId}")
    public ResponseEntity<ApiResponse<WorkflowDetailResponse>> getWorkflowById(
            @PathVariable("workflowId") UUID workflowId
    ) {
        WorkflowDetailResponse detail = workflowService.getWorkflowById(workflowId);
        return ResponseEntity.ok(ApiResponse.success(detail));
    }

    /**
     * Adds a new transition to a workflow.
     */
    @PostMapping("/workflows/{workflowId}/transitions")
    public ResponseEntity<ApiResponse<WorkflowTransitionResponse>> createTransition(
            @PathVariable("workflowId") UUID workflowId,
            @Valid @RequestBody CreateWorkflowTransitionRequest request
    ) {
        WorkflowTransitionResponse created = workflowService.createTransition(workflowId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Tạo bước chuyển quy trình thành công"));
    }

    /**
     * Deletes a transition from a workflow.
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
