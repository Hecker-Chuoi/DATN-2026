package com.datn.workservice.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.common.dto.PageResponse;
import com.datn.workservice.dto.request.CreateSubtaskRequest;
import com.datn.workservice.dto.request.CreateWorkItemRequest;
import com.datn.workservice.dto.request.UpdateWorkItemRequest;
import com.datn.workservice.dto.request.WorkItemFilterRequest;
import com.datn.workservice.dto.response.WorkItemDetailResponse;
import com.datn.workservice.dto.response.WorkItemResponse;
import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.security.RequireProjectPermission;
import com.datn.workservice.security.RequireTaskPermission;
import com.datn.workservice.security.SecurityUtils;
import com.datn.workservice.service.WorkItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/work")
@RequiredArgsConstructor
public class WorkItemController {

    private final WorkItemService workItemService;

    @PostMapping("/projects/{projectId}/items")
    @RequireProjectPermission(PermissionKey.ISSUE_CREATE)
    public ResponseEntity<ApiResponse<WorkItemDetailResponse>> createWorkItem(
            @PathVariable("projectId") UUID projectId,
            @Valid @RequestBody CreateWorkItemRequest request
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        request.setProjectId(projectId);
        WorkItemDetailResponse response = workItemService.createWorkItem(projectId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/projects/{projectId}/items")
    @RequireProjectPermission(PermissionKey.PROJECT_VIEW)
    public ResponseEntity<ApiResponse<PageResponse<WorkItemResponse>>> listWorkItems(
            @PathVariable("projectId") UUID projectId,
            @ModelAttribute WorkItemFilterRequest filter,
            @PageableDefault(size = 50, sort = "rank", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        if (filter == null) {
            filter = new WorkItemFilterRequest();
        }
        filter.setProjectId(projectId);
        PageResponse<WorkItemResponse> response = workItemService.listWorkItems(projectId, filter, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/items/{id}")
    @RequireTaskPermission(PermissionKey.PROJECT_VIEW)
    public ResponseEntity<ApiResponse<WorkItemDetailResponse>> getWorkItemDetail(@PathVariable("id") UUID id) {
        WorkItemDetailResponse response = workItemService.getWorkItemDetail(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Tra cứu theo mã (VD: DATN-12). Quyền PROJECT_VIEW được kiểm tra trong service sau khi resolve project.
     */
    @GetMapping("/items/code/{code}")
    public ResponseEntity<ApiResponse<WorkItemDetailResponse>> getWorkItemByCode(@PathVariable("code") String code) {
        WorkItemDetailResponse response = workItemService.getWorkItemByCode(code);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/items/{id}/subtasks")
    @RequireTaskPermission(PermissionKey.PROJECT_VIEW)
    public ResponseEntity<ApiResponse<List<WorkItemResponse>>> listSubtasks(@PathVariable("id") UUID id) {
        List<WorkItemResponse> subtasks = workItemService.listSubtasks(id);
        return ResponseEntity.ok(ApiResponse.success(subtasks));
    }

    @PostMapping("/items/{id}/subtasks")
    @RequireTaskPermission(PermissionKey.ISSUE_CREATE)
    public ResponseEntity<ApiResponse<WorkItemDetailResponse>> createSubtask(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CreateSubtaskRequest request
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        WorkItemDetailResponse response = workItemService.createSubtask(id, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Sub-task đã được tạo thành công"));
    }

    @PutMapping("/items/{id}")
    @RequireTaskPermission(PermissionKey.ISSUE_EDIT)
    public ResponseEntity<ApiResponse<WorkItemDetailResponse>> updateWorkItem(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateWorkItemRequest request
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        WorkItemDetailResponse response = workItemService.updateWorkItem(id, request, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/items/{id}/rank")
    @RequireTaskPermission(PermissionKey.ISSUE_EDIT)
    public ResponseEntity<ApiResponse<Void>> updateRank(
            @PathVariable("id") UUID id,
            @RequestBody Map<String, String> body
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        String newRank = body.get("rank");
        workItemService.updateRank(id, newRank, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/items/{id}")
    @RequireTaskPermission(PermissionKey.ISSUE_DELETE)
    public ResponseEntity<ApiResponse<Void>> deleteWorkItem(@PathVariable("id") UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId();
        workItemService.deleteWorkItem(id, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
