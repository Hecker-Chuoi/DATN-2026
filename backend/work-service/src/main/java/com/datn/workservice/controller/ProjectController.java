package com.datn.workservice.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.common.dto.PageResponse;
import com.datn.workservice.dto.request.CreateProjectRequest;
import com.datn.workservice.dto.request.UpdateProjectRequest;
import com.datn.workservice.dto.response.ProjectDetailResponse;
import com.datn.workservice.dto.response.ProjectResponse;
import com.datn.workservice.security.SecurityUtils;
import com.datn.workservice.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/work/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(
            @Valid @RequestBody CreateProjectRequest request
    ) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        ProjectResponse response = projectService.createProject(request, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Dự án đã được tạo và khởi tạo hệ sinh thái thành công"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProjectResponse>>> getAllProjects(
            @RequestParam(required = false) UUID workspaceId,
            @RequestParam(required = false) UUID teamId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isTemplate,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<ProjectResponse> response = projectService.getAllProjects(
                workspaceId, teamId, search, isTemplate, pageable
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/templates")
    public ResponseEntity<ApiResponse<PageResponse<ProjectResponse>>> getTemplates(
            @RequestParam(required = false) UUID workspaceId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<ProjectResponse> response = projectService.getTemplates(workspaceId, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ApiResponse<ProjectDetailResponse>> getProjectById(
            @PathVariable UUID projectId
    ) {
        ProjectDetailResponse response = projectService.getProjectById(projectId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/key/{key}")
    public ResponseEntity<ApiResponse<ProjectDetailResponse>> getProjectByKey(
            @PathVariable String key
    ) {
        ProjectDetailResponse response = projectService.getProjectByKey(key);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{projectId}")
    public ResponseEntity<ApiResponse<ProjectResponse>> updateProject(
            @PathVariable UUID projectId,
            @Valid @RequestBody UpdateProjectRequest request
    ) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        ProjectResponse response = projectService.updateProject(projectId, request, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(response, "Cập nhật thông tin dự án thành công"));
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<ApiResponse<Void>> deleteProject(
            @PathVariable UUID projectId
    ) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        projectService.deleteProject(projectId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(null, "Dự án đã được lưu trữ / chuyển vào thùng rác"));
    }

    @PostMapping("/{projectId}/restore")
    public ResponseEntity<ApiResponse<ProjectResponse>> restoreProject(
            @PathVariable UUID projectId
    ) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        ProjectResponse response = projectService.restoreProject(projectId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(response, "Khôi phục dự án thành công"));
    }
}
