package com.datn.workservice.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.workservice.dto.request.CompleteSprintRequest;
import com.datn.workservice.dto.request.CreateSprintRequest;
import com.datn.workservice.dto.request.SprintItemsRequest;
import com.datn.workservice.dto.request.StartSprintRequest;
import com.datn.workservice.dto.request.UpdateSprintRequest;
import com.datn.workservice.dto.response.BoardCardResponse;
import com.datn.workservice.dto.response.SprintCompletionResponse;
import com.datn.workservice.dto.response.SprintResponse;
import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.enums.SprintState;
import com.datn.workservice.security.ProjectIdSource;
import com.datn.workservice.security.RequireProjectPermission;
import com.datn.workservice.security.SecurityUtils;
import com.datn.workservice.service.SprintService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/work")
@RequiredArgsConstructor
public class SprintController {

    private final SprintService sprintService;

    @GetMapping("/projects/{projectId}/sprints")
    @RequireProjectPermission(PermissionKey.PROJECT_VIEW)
    public ResponseEntity<ApiResponse<List<SprintResponse>>> listByProject(
            @PathVariable UUID projectId,
            @RequestParam(required = false) List<String> state
    ) {
        Set<SprintState> states = parseStates(state);
        return ResponseEntity.ok(ApiResponse.success(sprintService.listByProject(projectId, states)));
    }

    @PostMapping("/projects/{projectId}/sprints")
    @RequireProjectPermission(PermissionKey.SPRINT_MANAGE)
    public ResponseEntity<ApiResponse<SprintResponse>> create(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateSprintRequest request
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        SprintResponse response = sprintService.create(projectId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Tạo Sprint thành công"));
    }

    @GetMapping("/sprints/{sprintId}")
    @RequireProjectPermission(value = PermissionKey.PROJECT_VIEW, source = ProjectIdSource.SPRINT, param = "sprintId")
    public ResponseEntity<ApiResponse<SprintResponse>> getById(@PathVariable UUID sprintId) {
        return ResponseEntity.ok(ApiResponse.success(sprintService.getById(sprintId)));
    }

    @PutMapping("/sprints/{sprintId}")
    @RequireProjectPermission(value = PermissionKey.SPRINT_MANAGE, source = ProjectIdSource.SPRINT, param = "sprintId")
    public ResponseEntity<ApiResponse<SprintResponse>> update(
            @PathVariable UUID sprintId,
            @Valid @RequestBody UpdateSprintRequest request
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                sprintService.update(sprintId, request, userId), "Cập nhật Sprint thành công"));
    }

    @DeleteMapping("/sprints/{sprintId}")
    @RequireProjectPermission(value = PermissionKey.SPRINT_MANAGE, source = ProjectIdSource.SPRINT, param = "sprintId")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID sprintId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        sprintService.delete(sprintId, userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã xoá Sprint"));
    }

    @PostMapping("/sprints/{sprintId}/start")
    @RequireProjectPermission(value = PermissionKey.SPRINT_MANAGE, source = ProjectIdSource.SPRINT, param = "sprintId")
    public ResponseEntity<ApiResponse<SprintResponse>> start(
            @PathVariable UUID sprintId,
            @RequestBody(required = false) StartSprintRequest request
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                sprintService.startSprint(sprintId, request, userId), "Sprint đã bắt đầu"));
    }

    /**
     * Frontend goi /close, so do tuan tu trong tai lieu goi /complete - nhan ca hai,
     * theo dung quy uoc dual-path da dung o WorkItemCommentController.
     */
    @PostMapping({"/sprints/{sprintId}/close", "/sprints/{sprintId}/complete"})
    @RequireProjectPermission(value = PermissionKey.SPRINT_MANAGE, source = ProjectIdSource.SPRINT, param = "sprintId")
    public ResponseEntity<ApiResponse<SprintCompletionResponse>> complete(
            @PathVariable UUID sprintId,
            @RequestBody(required = false) CompleteSprintRequest request
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                sprintService.completeSprint(sprintId, request, userId), "Đã đóng Sprint"));
    }

    @PostMapping("/sprints/{sprintId}/items")
    @RequireProjectPermission(value = PermissionKey.SPRINT_MANAGE, source = ProjectIdSource.SPRINT, param = "sprintId")
    public ResponseEntity<ApiResponse<List<BoardCardResponse>>> addItems(
            @PathVariable UUID sprintId,
            @Valid @RequestBody SprintItemsRequest request
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                sprintService.addItemsToSprint(sprintId, request.getItemIds(), userId),
                "Đã thêm công việc vào Sprint"));
    }

    /**
     * Ho tro ca DELETE kem body lan POST .../items/remove: mot so gateway loai bo
     * body cua request DELETE.
     */
    @DeleteMapping("/sprints/{sprintId}/items")
    @RequireProjectPermission(value = PermissionKey.SPRINT_MANAGE, source = ProjectIdSource.SPRINT, param = "sprintId")
    public ResponseEntity<ApiResponse<List<BoardCardResponse>>> removeItems(
            @PathVariable UUID sprintId,
            @Valid @RequestBody SprintItemsRequest request
    ) {
        return removeItemsInternal(sprintId, request);
    }

    @PostMapping("/sprints/{sprintId}/items/remove")
    @RequireProjectPermission(value = PermissionKey.SPRINT_MANAGE, source = ProjectIdSource.SPRINT, param = "sprintId")
    public ResponseEntity<ApiResponse<List<BoardCardResponse>>> removeItemsViaPost(
            @PathVariable UUID sprintId,
            @Valid @RequestBody SprintItemsRequest request
    ) {
        return removeItemsInternal(sprintId, request);
    }

    private ResponseEntity<ApiResponse<List<BoardCardResponse>>> removeItemsInternal(
            UUID sprintId, SprintItemsRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                sprintService.removeItemsFromSprint(sprintId, request.getItemIds(), userId),
                "Đã đưa công việc về Backlog"));
    }

    private Set<SprintState> parseStates(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        Set<SprintState> states = raw.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> {
                    try {
                        return SprintState.valueOf(s.trim().toUpperCase());
                    } catch (IllegalArgumentException ex) {
                        throw new IllegalArgumentException("Trạng thái Sprint không hợp lệ: " + s);
                    }
                })
                .collect(Collectors.toSet());
        return states.isEmpty() ? null : EnumSet.copyOf(states);
    }
}
