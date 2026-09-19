package com.datn.workservice.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.common.dto.PageResponse;
import com.datn.workservice.dto.request.CreateProjectRequest;
import com.datn.workservice.dto.request.UpdateProjectRequest;
import com.datn.workservice.dto.response.ProjectDetailResponse;
import com.datn.workservice.dto.response.ProjectResponse;
import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.security.RequireProjectPermission;
import com.datn.workservice.security.SecurityUtils;
import com.datn.workservice.security.WorkspaceAccessChecker;
import com.datn.workservice.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/work/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final WorkspaceAccessChecker workspaceAccessChecker;

    @PostMapping
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(
            @Valid @RequestBody CreateProjectRequest request
    ) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        // GUEST và người ngoài không được tạo project trong workspace.
        workspaceAccessChecker.requireAtLeast(request.getWorkspaceId(), currentUserId, "MEMBER");
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
                resolveWorkspaceScope(workspaceId), teamId, search, isTemplate, pageable
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/templates")
    public ResponseEntity<ApiResponse<PageResponse<ProjectResponse>>> getTemplates(
            @RequestParam(required = false) UUID workspaceId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<ProjectResponse> response =
                projectService.getTemplates(resolveWorkspaceScope(workspaceId), pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Giải phạm vi workspace cho các endpoint listing.
     * <p>
     * Có workspaceId thì xác minh người gọi là thành viên rồi giới hạn đúng
     * workspace đó. Bỏ trống thì lấy toàn bộ workspace của người gọi. Trước đây
     * workspaceId là optional và KHÔNG được enforce, nên bỏ trống nó là liệt kê
     * mọi project trong toàn hệ thống.
     * <p>
     * Trả về danh sách RỖNG (không phải null) khi người gọi không thuộc workspace
     * nào - để ra trang trống chứ không phải trang không filter.
     */
    private List<UUID> resolveWorkspaceScope(UUID workspaceId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        if (workspaceId != null) {
            workspaceAccessChecker.requireMember(workspaceId, currentUserId);
            return List.of(workspaceId);
        }
        return workspaceAccessChecker.getWorkspaceIds(currentUserId);
    }

    @GetMapping("/{projectId}")
    @RequireProjectPermission(PermissionKey.PROJECT_VIEW)
    public ResponseEntity<ApiResponse<ProjectDetailResponse>> getProjectById(
            @PathVariable("projectId") UUID projectId
    ) {
        ProjectDetailResponse response = projectService.getProjectById(projectId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/key/{key}")
    public ResponseEntity<ApiResponse<ProjectDetailResponse>> getProjectByKey(
            @PathVariable("key") String key
    ) {
        ProjectDetailResponse response = projectService.getProjectByKey(key);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{projectId}")
    @RequireProjectPermission(PermissionKey.PROJECT_ADMIN)
    public ResponseEntity<ApiResponse<ProjectResponse>> updateProject(
            @PathVariable("projectId") UUID projectId,
            @Valid @RequestBody UpdateProjectRequest request
    ) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        ProjectResponse response = projectService.updateProject(projectId, request, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(response, "Cập nhật thông tin dự án thành công"));
    }

    @DeleteMapping("/{projectId}")
    @RequireProjectPermission(PermissionKey.PROJECT_ADMIN)
    public ResponseEntity<ApiResponse<Void>> deleteProject(
            @PathVariable("projectId") UUID projectId
    ) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        projectService.deleteProject(projectId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(null, "Dự án đã được lưu trữ / chuyển vào thùng rác"));
    }

    @PostMapping("/{projectId}/restore")
    @RequireProjectPermission(PermissionKey.PROJECT_ADMIN)
    public ResponseEntity<ApiResponse<ProjectResponse>> restoreProject(
            @PathVariable("projectId") UUID projectId
    ) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        ProjectResponse response = projectService.restoreProject(projectId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(response, "Khôi phục dự án thành công"));
    }
}
