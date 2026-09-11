package com.datn.workservice.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.workservice.dto.request.ExecuteTransitionRequest;
import com.datn.workservice.dto.response.AvailableTransitionResponse;
import com.datn.workservice.entity.WorkItem;
import com.datn.workservice.security.SecurityUtils;
import com.datn.workservice.service.WorkflowEngineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/work/work-items/{id}/transitions")
@RequiredArgsConstructor
public class WorkItemTransitionController {

    /**
     * ──────────────────────────── DANH SÁCH API ────────────────────────────
     * GET  /api/v1/work/work-items/{id}/transitions                : Lấy các bước chuyển khả dụng từ trạng thái hiện tại
     * POST /api/v1/work/work-items/{id}/transitions/{transitionId} : Thực hiện chuyển trạng thái theo transitionId trên path
     * POST /api/v1/work/work-items/{id}/transitions                : Thực hiện chuyển trạng thái (transitionId trong body)
     * ────────────────────────────────────────────────────────────────────────
     */

    private final WorkflowEngineService workflowEngineService;

    /**
     * Retrieves all available transitions from the current status of the work item
     * evaluated for the authenticated user.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<AvailableTransitionResponse>>> getAvailableTransitions(
            @PathVariable("id") UUID workItemId
    ) {
        UUID currentUserId = SecurityUtils.getCurrentUserIdOptional().orElse(null);
        List<AvailableTransitionResponse> transitions = workflowEngineService.getAvailableTransitions(workItemId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(transitions));
    }

    /**
     * Executes a specific workflow transition by transition ID in the path.
     */
    @PostMapping("/{transitionId}")
    public ResponseEntity<ApiResponse<WorkItem>> executeTransitionById(
            @PathVariable("id") UUID workItemId,
            @PathVariable("transitionId") UUID transitionId,
            @RequestBody(required = false) ExecuteTransitionRequest request
    ) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        if (request == null) {
            request = ExecuteTransitionRequest.builder().transitionId(transitionId).build();
        } else if (request.getTransitionId() == null) {
            request.setTransitionId(transitionId);
        }
        WorkItem updated = workflowEngineService.executeTransition(workItemId, transitionId, request, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(updated, "Chuyển trạng thái công việc thành công"));
    }

    /**
     * Executes a workflow transition with transition ID specified in the request body.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<WorkItem>> executeTransition(
            @PathVariable("id") UUID workItemId,
            @Valid @RequestBody ExecuteTransitionRequest request
    ) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        WorkItem updated = workflowEngineService.executeTransition(workItemId, request.getTransitionId(), request, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(updated, "Chuyển trạng thái công việc thành công"));
    }
}
