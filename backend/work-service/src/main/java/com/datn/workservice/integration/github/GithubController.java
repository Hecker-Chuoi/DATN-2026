package com.datn.workservice.integration.github;

import com.datn.common.dto.ApiResponse;
import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.integration.github.GithubDtos.ConnectionStatus;
import com.datn.workservice.integration.github.GithubDtos.LinkRepoRequest;
import com.datn.workservice.integration.github.GithubDtos.OAuthCallbackRequest;
import com.datn.workservice.integration.github.GithubDtos.RepoOption;
import com.datn.workservice.security.RequireProjectPermission;
import com.datn.workservice.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Tích hợp GitHub: kết nối tài khoản cá nhân + liên kết repo với dự án phần mềm. */
@RestController
@RequestMapping("/api/v1/work")
@RequiredArgsConstructor
public class GithubController {

    private final GithubService githubService;

    // ─── Tài khoản cá nhân ───────────────────────────────────────────────────

    @GetMapping("/github/oauth/authorize-url")
    public ResponseEntity<ApiResponse<Map<String, String>>> authorizeUrl(@RequestParam String redirectUri) {
        String url = githubService.buildAuthorizeUrl(SecurityUtils.getCurrentUserId(), redirectUri);
        return ResponseEntity.ok(ApiResponse.success(Map.of("url", url)));
    }

    @PostMapping("/github/oauth/callback")
    public ResponseEntity<ApiResponse<ConnectionStatus>> callback(@Valid @RequestBody OAuthCallbackRequest req) {
        ConnectionStatus status = githubService.completeOAuth(
                SecurityUtils.getCurrentUserId(), req.code(), req.state(), req.redirectUri());
        return ResponseEntity.ok(ApiResponse.success(status, "Đã kết nối GitHub: " + status.login()));
    }

    @GetMapping("/github/me")
    public ResponseEntity<ApiResponse<ConnectionStatus>> me() {
        return ResponseEntity.ok(ApiResponse.success(githubService.myConnection(SecurityUtils.getCurrentUserId())));
    }

    @DeleteMapping("/github/me")
    public ResponseEntity<ApiResponse<Void>> disconnect() {
        githubService.disconnect(SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(null, "Đã ngắt kết nối GitHub"));
    }

    @GetMapping("/github/repos")
    public ResponseEntity<ApiResponse<List<RepoOption>>> myRepos() {
        return ResponseEntity.ok(ApiResponse.success(githubService.listMyRepos(SecurityUtils.getCurrentUserId())));
    }

    // ─── Repo của dự án ──────────────────────────────────────────────────────

    @GetMapping("/projects/{projectId}/github/repo")
    @RequireProjectPermission(PermissionKey.PROJECT_VIEW)
    public ResponseEntity<ApiResponse<GithubRepoLink>> repoLink(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success(githubService.getRepoLink(projectId)));
    }

    @PutMapping("/projects/{projectId}/github/repo")
    @RequireProjectPermission(PermissionKey.PROJECT_ADMIN)
    public ResponseEntity<ApiResponse<GithubRepoLink>> linkRepo(@PathVariable UUID projectId,
                                                                @Valid @RequestBody LinkRepoRequest req) {
        GithubRepoLink link = githubService.linkRepo(projectId, req, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(link, "Đã liên kết repo " + link.fullName()));
    }

    @DeleteMapping("/projects/{projectId}/github/repo")
    @RequireProjectPermission(PermissionKey.PROJECT_ADMIN)
    public ResponseEntity<ApiResponse<Void>> unlinkRepo(@PathVariable UUID projectId) {
        githubService.unlinkRepo(projectId, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(null, "Đã gỡ liên kết repo GitHub"));
    }
}
