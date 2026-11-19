package com.datn.workservice.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.workservice.dto.github.WorkItemDevelopmentResponse;
import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.security.RequireTaskPermission;
import com.datn.workservice.service.GitHubIntegrationService;
import com.datn.workservice.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Khung "Phát triển" của Task (kiểu Jira Development panel):
 * GET  /api/v1/work/items/{itemId}/github/development : nhánh gợi ý / đã tạo + link mở PR
 * POST /api/v1/work/items/{itemId}/github/branch      : tạo nhánh trên GitHub từ nhánh đích của dự án
 * POST /api/v1/work/items/{itemId}/github/issue       : tạo GitHub Issue cho Task và gắn liên kết
 */
@RestController
@RequestMapping("/api/v1/work")
@RequiredArgsConstructor
public class WorkItemGitHubController {

    private final GitHubIntegrationService gitHubIntegrationService;

    @GetMapping({"/items/{itemId}/github/development", "/work-items/{itemId}/github/development"})
    @RequireTaskPermission(PermissionKey.PROJECT_VIEW)
    public ResponseEntity<ApiResponse<WorkItemDevelopmentResponse>> getDevelopment(@PathVariable("itemId") UUID workItemId) {
        return ResponseEntity.ok(ApiResponse.success(gitHubIntegrationService.getDevelopmentInfo(workItemId)));
    }

    @PostMapping({"/items/{itemId}/github/branch", "/work-items/{itemId}/github/branch"})
    @RequireTaskPermission(PermissionKey.ISSUE_EDIT)
    public ResponseEntity<ApiResponse<WorkItemDevelopmentResponse>> createBranch(@PathVariable("itemId") UUID workItemId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                gitHubIntegrationService.createBranch(workItemId, currentUserId), "Đã tạo nhánh trên GitHub"));
    }

    @PostMapping({"/items/{itemId}/github/issue", "/work-items/{itemId}/github/issue"})
    @RequireTaskPermission(PermissionKey.ISSUE_EDIT)
    public ResponseEntity<ApiResponse<WorkItemDevelopmentResponse>> createIssue(@PathVariable("itemId") UUID workItemId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                gitHubIntegrationService.createIssue(workItemId, currentUserId), "Đã tạo Issue trên GitHub"));
    }
}
