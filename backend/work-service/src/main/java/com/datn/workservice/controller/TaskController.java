package com.datn.workservice.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.workservice.dto.request.CreateTaskRequest;
import com.datn.workservice.dto.request.UpdateTaskRequest;
import com.datn.workservice.dto.response.TaskResponse;
import com.datn.workservice.enums.TaskStatus;
import com.datn.workservice.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/work/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    public ResponseEntity<ApiResponse<TaskResponse>> createTask(
            @Valid @RequestBody CreateTaskRequest request,
            Principal principal) {
        UUID currentUserId = extractUserId(principal);
        TaskResponse task = taskService.createTask(request, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(task, "Tạo tác vụ thành công"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getTasks(
            @RequestParam("projectId") UUID projectId,
            @RequestParam(value = "milestoneId", required = false) UUID milestoneId,
            @RequestParam(value = "status", required = false) TaskStatus status,
            @RequestParam(value = "assigneeId", required = false) UUID assigneeId,
            @RequestParam(value = "search", required = false) String search) {
        List<TaskResponse> tasks = taskService.getTasks(projectId, milestoneId, status, assigneeId, search);
        return ResponseEntity.ok(ApiResponse.success(tasks, "Lấy danh sách tác vụ thành công"));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<ApiResponse<TaskResponse>> getTaskById(@PathVariable("taskId") UUID taskId) {
        TaskResponse task = taskService.getTaskById(taskId);
        return ResponseEntity.ok(ApiResponse.success(task));
    }

    @GetMapping("/{taskId}/subtasks")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getSubtasks(@PathVariable("taskId") UUID taskId) {
        List<TaskResponse> subtasks = taskService.getSubtasks(taskId);
        return ResponseEntity.ok(ApiResponse.success(subtasks, "Lấy danh sách tác vụ con thành công"));
    }

    @PutMapping("/{taskId}")
    public ResponseEntity<ApiResponse<TaskResponse>> updateTask(
            @PathVariable("taskId") UUID taskId,
            @Valid @RequestBody UpdateTaskRequest request) {
        TaskResponse task = taskService.updateTask(taskId, request);
        return ResponseEntity.ok(ApiResponse.success(task, "Cập nhật tác vụ thành công"));
    }

    @PutMapping("/{taskId}/assignee")
    public ResponseEntity<ApiResponse<TaskResponse>> assignTask(
            @PathVariable("taskId") UUID taskId,
            @RequestBody com.datn.workservice.dto.request.AssignTaskRequest request,
            Principal principal) {
        UUID currentUserId = extractUserId(principal);
        TaskResponse task = taskService.assignTask(taskId, request != null ? request.getAssigneeId() : null, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(task, "Cập nhật người thực hiện thành công"));
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<ApiResponse<Void>> deleteTask(@PathVariable("taskId") UUID taskId) {
        taskService.deleteTask(taskId);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa tác vụ thành công"));
    }

    private UUID extractUserId(Principal principal) {
        if (principal instanceof JwtAuthenticationToken jwtToken) {
            String sub = jwtToken.getToken().getSubject();
            try {
                return UUID.fromString(sub);
            } catch (Exception ignored) {}
        }
        return null;
    }
}
