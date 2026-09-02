package com.datn.workservice.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.common.dto.PageResponse;
import com.datn.workservice.dto.request.CreateWorkItemRequest;
import com.datn.workservice.dto.request.UpdateWorkItemRequest;
import com.datn.workservice.dto.request.WorkItemFilterRequest;
import com.datn.workservice.dto.response.WorkItemDetailResponse;
import com.datn.workservice.dto.response.WorkItemResponse;
import com.datn.workservice.service.WorkItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
    public ResponseEntity<ApiResponse<WorkItemDetailResponse>> createWorkItem(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateWorkItemRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = extractUserId(jwt);
        request.setProjectId(projectId);
        WorkItemDetailResponse response = workItemService.createWorkItem(projectId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/projects/{projectId}/items")
    public ResponseEntity<ApiResponse<PageResponse<WorkItemResponse>>> listWorkItems(
            @PathVariable UUID projectId,
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
    public ResponseEntity<ApiResponse<WorkItemDetailResponse>> getWorkItemDetail(@PathVariable UUID id) {
        WorkItemDetailResponse response = workItemService.getWorkItemDetail(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/items/code/{code}")
    public ResponseEntity<ApiResponse<WorkItemDetailResponse>> getWorkItemByCode(@PathVariable String code) {
        WorkItemDetailResponse response = workItemService.getWorkItemByCode(code);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/items/{id}/subtasks")
    public ResponseEntity<ApiResponse<List<WorkItemResponse>>> listSubtasks(@PathVariable UUID id) {
        List<WorkItemResponse> subtasks = workItemService.listSubtasks(id);
        return ResponseEntity.ok(ApiResponse.success(subtasks));
    }

    @PutMapping("/items/{id}")
    public ResponseEntity<ApiResponse<WorkItemDetailResponse>> updateWorkItem(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateWorkItemRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = extractUserId(jwt);
        WorkItemDetailResponse response = workItemService.updateWorkItem(id, request, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/items/{id}/rank")
    public ResponseEntity<ApiResponse<Void>> updateRank(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = extractUserId(jwt);
        String newRank = body.get("rank");
        workItemService.updateRank(id, newRank, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/items/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteWorkItem(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = extractUserId(jwt);
        workItemService.deleteWorkItem(id, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private UUID extractUserId(Jwt jwt) {
        if (jwt == null) {
            return UUID.fromString("00000000-0000-0000-0000-000000000001");
        }
        String sub = jwt.getSubject();
        try {
            return UUID.fromString(sub);
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(sub.getBytes());
        }
    }
}
