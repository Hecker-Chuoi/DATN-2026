package com.datn.workservice.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.workservice.dto.github.*;
import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.security.RequireProjectPermission;
import com.datn.workservice.security.SecurityUtils;
import com.datn.workservice.service.GitHubIntegrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/work/projects/{projectId}/integrations/github")
@RequiredArgsConstructor
public class GitHubIntegrationController {

    private final GitHubIntegrationService gitHubIntegrationService;

    /**
     * Lấy URL chuyển hướng OAuth GitHub
     */
    @GetMapping("/auth-url")
    @RequireProjectPermission(PermissionKey.PROJECT_VIEW)
    public ResponseEntity<ApiResponse<Map<String, String>>> getOAuthUrl(@PathVariable UUID projectId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        String url = gitHubIntegrationService.getOAuthAuthorizeUrl(projectId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("authUrl", url)));
    }

    /**
     * Xử lý OAuth callback sau khi user cấp quyền
     */
    @PostMapping("/oauth-callback")
    @RequireProjectPermission(PermissionKey.PROJECT_VIEW)
    public ResponseEntity<ApiResponse<ProjectGitHubStatusResponse>> handleCallback(
            @PathVariable UUID projectId,
            @RequestParam String code,
            @RequestParam(required = false) String state
    ) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        ProjectGitHubStatusResponse response = gitHubIntegrationService.handleOAuthCallback(projectId, code, state, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(response, "Liên kết tài khoản GitHub thành công"));
    }

    /**
     * Xem trạng thái tích hợp GitHub của dự án
     */
    @GetMapping("/status")
    @RequireProjectPermission(PermissionKey.PROJECT_VIEW)
    public ResponseEntity<ApiResponse<ProjectGitHubStatusResponse>> getStatus(@PathVariable UUID projectId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(gitHubIntegrationService.getProjectGitHubStatus(projectId, currentUserId)));
    }

    /**
     * Lấy danh sách Repositories có thể liên kết
     */
    @GetMapping("/repos")
    @RequireProjectPermission(PermissionKey.PROJECT_ADMIN)
    public ResponseEntity<ApiResponse<List<GitHubRepoDto>>> getRepositories(@PathVariable UUID projectId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(gitHubIntegrationService.getAvailableRepositories(currentUserId)));
    }

    /**
     * Lấy danh sách nhánh của một Repository để chọn nhánh đích
     */
    @GetMapping("/repos/{owner}/{repo}/branches")
    @RequireProjectPermission(PermissionKey.PROJECT_ADMIN)
    public ResponseEntity<ApiResponse<List<GitHubBranchDto>>> getBranches(
            @PathVariable UUID projectId,
            @PathVariable String owner,
            @PathVariable String repo
    ) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                gitHubIntegrationService.listBranches(projectId, owner + "/" + repo, currentUserId)));
    }

    /**
     * Liên kết Repository cụ thể vào dự án
     */
    @PostMapping("/connect")
    @RequireProjectPermission(PermissionKey.PROJECT_ADMIN)
    public ResponseEntity<ApiResponse<ProjectGitHubStatusResponse>> connectRepo(
            @PathVariable UUID projectId,
            @Valid @RequestBody ConnectRepoRequest request
    ) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        ProjectGitHubStatusResponse response = gitHubIntegrationService.connectRepository(projectId, request, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(response, "Liên kết Repository GitHub thành công"));
    }

    /**
     * Ngắt kết nối Repository khỏi dự án
     */
    @DeleteMapping("/disconnect")
    @RequireProjectPermission(PermissionKey.PROJECT_ADMIN)
    public ResponseEntity<ApiResponse<Void>> disconnectRepo(@PathVariable UUID projectId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        gitHubIntegrationService.disconnectRepository(projectId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã ngắt kết nối Repository GitHub"));
    }

    /**
     * Cập nhật quy tắc tự động hóa đồng bộ
     */
    @PutMapping("/config")
    @RequireProjectPermission(PermissionKey.PROJECT_ADMIN)
    public ResponseEntity<ApiResponse<ProjectGitHubStatusResponse>> updateConfig(
            @PathVariable UUID projectId,
            @RequestBody UpdateGitHubSyncConfigRequest request
    ) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        ProjectGitHubStatusResponse response = gitHubIntegrationService.updateSyncConfig(projectId, request, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(response, "Cập nhật cấu hình tích hợp thành công"));
    }

    /**
     * Kéo toàn bộ danh sách Issues từ GitHub Repository về làm Task
     */
    @PostMapping("/sync-issues")
    @RequireProjectPermission(PermissionKey.PROJECT_ADMIN)
    public ResponseEntity<ApiResponse<GitHubSyncResultResponse>> syncIssues(@PathVariable UUID projectId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        GitHubSyncResultResponse response = gitHubIntegrationService.syncIssues(projectId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(response, "Đồng bộ Issues từ GitHub thành công"));
    }

    /**
     * Hủy liên kết tài khoản GitHub của người dùng (xóa token)
     */
    @DeleteMapping("/user-account")
    @RequireProjectPermission(PermissionKey.PROJECT_VIEW)
    public ResponseEntity<ApiResponse<Void>> unlinkUserAccount(@PathVariable UUID projectId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        gitHubIntegrationService.unlinkUserAccount(currentUserId);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã hủy liên kết tài khoản GitHub"));
    }
}
