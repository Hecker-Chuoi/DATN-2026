package com.datn.workservice.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.common.dto.PageResponse;
import com.datn.workservice.dto.request.CloneProjectRequest;
import com.datn.workservice.dto.request.CreateProjectRequest;
import com.datn.workservice.dto.request.ProjectFilter;
import com.datn.workservice.dto.request.UpdateProjectRequest;
import com.datn.workservice.dto.response.ProjectResponse;
import com.datn.workservice.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.parameters.P;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/work/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(
            @Valid @RequestBody CreateProjectRequest request,
            Principal principal) {
        UUID currentUserId = extractUserId(principal);
        ProjectResponse project = projectService.createProject(request, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(project, "Tạo dự án thành công"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProjectResponse>>> getAllProjects(
            ProjectFilter filter,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<ProjectResponse> projects = projectService.getAllProjects(filter, pageable);
        return ResponseEntity.ok(ApiResponse.success(projects, "Lấy danh sách dự án thành công"));
    }

    @GetMapping("/my-projects")
    public ResponseEntity<ApiResponse<PageResponse<ProjectResponse>>> getMyProjects(
            ProjectFilter filter,
            @PageableDefault(page = 0, size = 9, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Principal principal) {
        UUID currentUserId = extractUserId(principal);
        PageResponse<ProjectResponse> projects = projectService.getMyProjects(currentUserId, filter, pageable);
        return ResponseEntity.ok(ApiResponse.success(projects, "Lấy danh sách dự án của tôi thành công"));
    }

    @GetMapping("/templates")
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> getTemplateProjects() {
        List<ProjectResponse> templates = projectService.getTemplateProjects();
        return ResponseEntity.ok(ApiResponse.success(templates, "Lấy danh sách dự án mẫu thành công"));
    }

    @GetMapping("/{projectId}")
    @PreAuthorize("@projectSecurity.hasProjectPermission(#projectId, 'PROJECT_VIEW')")
    public ResponseEntity<ApiResponse<ProjectResponse>> getProjectById(@PathVariable("projectId") @P("projectId") UUID projectId) {
        ProjectResponse project = projectService.getProjectById(projectId);
        return ResponseEntity.ok(ApiResponse.success(project));
    }

    @PutMapping("/{projectId}")
    @PreAuthorize("@projectSecurity.hasProjectPermission(#projectId, 'PROJECT_EDIT')")
    public ResponseEntity<ApiResponse<ProjectResponse>> updateProject(
            @PathVariable("projectId") @P("projectId") UUID projectId,
            @Valid @RequestBody UpdateProjectRequest request) {
        ProjectResponse project = projectService.updateProject(projectId, request);
        return ResponseEntity.ok(ApiResponse.success(project, "Cập nhật dự án thành công"));
    }

    @DeleteMapping("/{projectId}")
    @PreAuthorize("@projectSecurity.hasProjectPermission(#projectId, 'PROJECT_DELETE')")
    public ResponseEntity<ApiResponse<Void>> deleteProject(@PathVariable("projectId") @P("projectId") UUID projectId) {
        projectService.deleteProject(projectId);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa dự án thành công"));
    }

    @PostMapping("/{projectId}/clone")
    public ResponseEntity<ApiResponse<ProjectResponse>> cloneProject(
            @PathVariable("projectId") UUID projectId,
            @Valid @RequestBody CloneProjectRequest request,
            Principal principal) {
        // Đảm bảo sourceProjectId khớp với path variable
        request.setSourceProjectId(projectId);
        UUID currentUserId = extractUserId(principal);
        ProjectResponse cloned = projectService.cloneProject(request, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(cloned, "Nhân bản dự án từ mẫu thành công"));
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
