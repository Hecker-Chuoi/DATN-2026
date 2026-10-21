package com.datn.workservice.client;

import com.datn.common.exception.BadRequestException;
import com.datn.workservice.dto.github.GitHubOAuthTokenResponse;
import com.datn.workservice.dto.github.GitHubRepoDto;
import com.datn.workservice.dto.github.GitHubUserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubApiClient {

    private final RestTemplate restTemplate;

    @Value("${github.client-id:}")
    private String clientId;

    @Value("${github.client-secret:}")
    private String clientSecret;

    @Value("${github.redirect-uri:}")
    private String redirectUri;

    @Value("${github.api-url:https://api.github.com}")
    private String apiUrl;

    /**
     * Tạo đường dẫn chuyển hướng OAuth sang GitHub để người dùng cấp quyền
     */
    public String buildAuthorizeUrl(String state) {
        String scope = "repo,read:user,admin:repo_hook";
        return String.format(
                "https://github.com/login/oauth/authorize?client_id=%s&redirect_uri=%s&scope=%s&state=%s",
                clientId, redirectUri, scope, state != null ? state : ""
        );
    }

    /**
     * Đổi Authorization Code lấy Access Token từ GitHub
     */
    public GitHubOAuthTokenResponse exchangeCodeForToken(String code) {
        String tokenUrl = "https://github.com/login/oauth/access_token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("code", code);
        body.add("redirect_uri", redirectUri);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<GitHubOAuthTokenResponse> response = restTemplate.exchange(
                    tokenUrl, HttpMethod.POST, request, GitHubOAuthTokenResponse.class
            );
            GitHubOAuthTokenResponse tokenResponse = response.getBody();
            if (tokenResponse != null && tokenResponse.getError() != null) {
                log.error("GitHub OAuth exchange error: {} - {}", tokenResponse.getError(), tokenResponse.getErrorDescription());
                throw new BadRequestException("Lỗi xác thực GitHub: " + tokenResponse.getErrorDescription());
            }
            return tokenResponse;
        } catch (Exception e) {
            log.error("Failed to exchange code for GitHub access token: {}", e.getMessage(), e);
            throw new BadRequestException("Không thể xác thực với GitHub: " + e.getMessage());
        }
    }

    /**
     * Lấy thông tin tài khoản GitHub của người dùng
     */
    public GitHubUserResponse getUserProfile(String accessToken) {
        String url = apiUrl + "/user";

        HttpHeaders headers = createAuthHeaders(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<GitHubUserResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, GitHubUserResponse.class
            );
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to get GitHub user profile: {}", e.getMessage(), e);
            throw new BadRequestException("Không thể lấy thông tin tài khoản GitHub: " + e.getMessage());
        }
    }

    /**
     * Lấy danh sách Repository của người dùng (bao gồm cá nhân & tổ chức họ có quyền)
     */
    public List<GitHubRepoDto> getUserRepositories(String accessToken) {
        String url = apiUrl + "/user/repos?per_page=100&sort=updated&affiliation=owner,collaborator,organization_member";

        HttpHeaders headers = createAuthHeaders(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<List<GitHubRepoDto>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    new ParameterizedTypeReference<List<GitHubRepoDto>>() {}
            );
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to get GitHub repositories: {}", e.getMessage(), e);
            throw new BadRequestException("Không thể lấy danh sách kho mã nguồn từ GitHub: " + e.getMessage());
        }
    }

    /**
     * Đăng ký Webhook tự động lên GitHub Repository để nhận commit, PR, issues
     */
    public Long createWebhook(String accessToken, String owner, String repo, String webhookPayloadUrl, String secret) {
        String url = String.format("%s/repos/%s/%s/hooks", apiUrl, owner, repo);

        HttpHeaders headers = createAuthHeaders(accessToken);

        Map<String, Object> config = new HashMap<>();
        config.put("url", webhookPayloadUrl);
        config.put("content_type", "json");
        config.put("secret", secret);
        config.put("insecure_ssl", "0");

        Map<String, Object> body = new HashMap<>();
        body.put("name", "web");
        body.put("active", true);
        body.put("events", Arrays.asList("push", "pull_request", "issues", "issue_comment"));
        body.put("config", config);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);
            if (response.getBody() != null && response.getBody().get("id") != null) {
                return Long.valueOf(response.getBody().get("id").toString());
            }
        } catch (Exception e) {
            log.warn("Could not auto-register GitHub webhook on {}/{}: {}", owner, repo, e.getMessage());
            // Không throw để không chặn luồng kết nối repo nếu user không có quyền admin webhook
        }
        return null;
    }

    /**
     * Xóa Webhook khi ngắt kết nối
     */
    public void deleteWebhook(String accessToken, String owner, String repo, Long webhookId) {
        if (webhookId == null) return;
        String url = String.format("%s/repos/%s/%s/hooks/%d", apiUrl, owner, repo, webhookId);

        HttpHeaders headers = createAuthHeaders(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            restTemplate.exchange(url, HttpMethod.DELETE, request, Void.class);
            log.info("Deleted GitHub webhook {} on {}/{}", webhookId, owner, repo);
        } catch (Exception e) {
            log.warn("Failed to delete GitHub webhook {} on {}/{}: {}", webhookId, owner, repo, e.getMessage());
        }
    }

    private HttpHeaders createAuthHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(Collections.singletonList(MediaType.parseMediaType("application/vnd.github.v3+json")));
        return headers;
    }
}
