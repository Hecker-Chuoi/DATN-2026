package com.datn.workservice.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.workservice.dto.request.CloneProjectRequest;
import com.datn.workservice.dto.request.CreateProjectFromTemplateRequest;
import com.datn.workservice.dto.request.SaveAsTemplateRequest;
import com.datn.workservice.dto.response.TemplateInstantiationResponse;
import com.datn.workservice.dto.response.TemplateIntegrityReport;
import com.datn.workservice.dto.response.TemplatePreviewResponse;
import com.datn.workservice.dto.response.TemplateSummaryResponse;
import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.enums.TemplateCategory;
import com.datn.workservice.security.RequireProjectPermission;
import com.datn.workservice.security.SecurityUtils;
import com.datn.workservice.security.WorkspaceAccessChecker;
import com.datn.workservice.service.ProjectTemplateService;
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
public class ProjectTemplateController {

    /**
     * ──────────────────────────── DANH SÁCH API ────────────────────────────
     * GET    /api/v1/work/templates                              : Thư viện mẫu (lọc workspace/category/search)
     * GET    /api/v1/work/templates/{templateId}/preview         : Xem trước toàn bộ bản thiết kế của mẫu
     * POST   /api/v1/work/templates/{templateId}/projects        : Tạo dự án mới từ mẫu
     * POST   /api/v1/work/projects/{projectId}/save-as-template  : Lưu dự án thành mẫu mới
     * POST   /api/v1/work/projects/{projectId}/clone             : Nhân bản dự án
     * GET    /api/v1/work/projects/{projectId}/template-check    : Kiểm tra dự án có đủ điều kiện làm mẫu
     * ────────────────────────────────────────────────────────────────────────
     */

    private final ProjectTemplateService projectTemplateService;
    private final WorkspaceAccessChecker workspaceAccessChecker;

    @GetMapping("/templates")
    public ResponseEntity<ApiResponse<List<TemplateSummaryResponse>>> listTemplates(
            @RequestParam(required = false) UUID workspaceId,
            @RequestParam(required = false) TemplateCategory category,
            @RequestParam(required = false) String search
    ) {
        List<TemplateSummaryResponse> response =
                projectTemplateService.listTemplates(resolveWorkspaceScope(workspaceId), category, search);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** Quyền xem mẫu (thành viên workspace chứa mẫu) được kiểm tra trong service. */
    @GetMapping("/templates/{templateId}/preview")
    public ResponseEntity<ApiResponse<TemplatePreviewResponse>> previewTemplate(
            @PathVariable("templateId") UUID templateId
    ) {
        TemplatePreviewResponse response =
                projectTemplateService.getPreview(templateId, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** Quyền dùng mẫu + quyền tạo dự án (trưởng nhóm / Global ADMIN) được kiểm tra trong service. */
    @PostMapping("/templates/{templateId}/projects")
    public ResponseEntity<ApiResponse<TemplateInstantiationResponse>> createFromTemplate(
            @PathVariable("templateId") UUID templateId,
            @Valid @RequestBody CreateProjectFromTemplateRequest request
    ) {
        TemplateInstantiationResponse response =
                projectTemplateService.createFromTemplate(templateId, request, SecurityUtils.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Dự án đã được tạo từ mẫu thành công"));
    }

    @PostMapping("/projects/{projectId}/save-as-template")
    @RequireProjectPermission(PermissionKey.PROJECT_ADMIN)
    public ResponseEntity<ApiResponse<TemplateInstantiationResponse>> saveAsTemplate(
            @PathVariable("projectId") UUID projectId,
            @Valid @RequestBody SaveAsTemplateRequest request
    ) {
        TemplateInstantiationResponse response =
                projectTemplateService.saveAsTemplate(projectId, request, SecurityUtils.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Đã lưu dự án thành mẫu"));
    }

    @PostMapping("/projects/{projectId}/clone")
    @RequireProjectPermission(PermissionKey.PROJECT_ADMIN)
    public ResponseEntity<ApiResponse<TemplateInstantiationResponse>> cloneProject(
            @PathVariable("projectId") UUID projectId,
            @Valid @RequestBody CloneProjectRequest request
    ) {
        TemplateInstantiationResponse response =
                projectTemplateService.duplicateProject(projectId, request, SecurityUtils.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Nhân bản dự án thành công"));
    }

    @GetMapping("/projects/{projectId}/template-check")
    @RequireProjectPermission(PermissionKey.PROJECT_VIEW)
    public ResponseEntity<ApiResponse<TemplateIntegrityReport>> checkTemplateIntegrity(
            @PathVariable("projectId") UUID projectId
    ) {
        return ResponseEntity.ok(ApiResponse.success(projectTemplateService.checkIntegrity(projectId)));
    }

    /**
     * Có workspaceId: xác minh thành viên rồi giới hạn đúng workspace đó.
     * Luôn đính kèm DEFAULT_WORKSPACE_ID để mọi workspace đều dùng được mẫu hệ thống.
     * Bỏ trống: Global ADMIN thấy mọi mẫu (null), người khác thấy mẫu trong các workspace của mình.
     */
    private List<UUID> resolveWorkspaceScope(UUID workspaceId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        if (workspaceId != null) {
            if (!SecurityUtils.isGlobalAdmin()) {
                workspaceAccessChecker.requireMember(workspaceId, currentUserId);
            }
            if (!com.datn.workservice.service.template.TemplateDataSeeder.DEFAULT_WORKSPACE_ID.equals(workspaceId)) {
                return List.of(workspaceId, com.datn.workservice.service.template.TemplateDataSeeder.DEFAULT_WORKSPACE_ID);
            }
            return List.of(workspaceId);
        }
        return SecurityUtils.isGlobalAdmin() ? null : workspaceAccessChecker.getWorkspaceIds(currentUserId);
    }
}
