package com.datn.workservice.client;

import com.datn.common.exception.BadRequestException;
import com.datn.workservice.dto.github.GitHubIssueDto;
import com.datn.workservice.dto.github.GitHubOAuthTokenResponse;
import com.datn.workservice.dto.github.GitHubBranchDto;
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

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubApiClient {

    private static final int MAX_ISSUE_PAGES = 10;

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
                enc(clientId), enc(redirectUri), enc(scope), enc(state != null ? state : "")
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
     * Làm mới Access Token từ GitHub bằng Refresh Token
     */
    public GitHubOAuthTokenResponse refreshAccessToken(String refreshToken) {
        String tokenUrl = "https://github.com/login/oauth/access_token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set(HttpHeaders.USER_AGENT, "DATN-WorkService-2026");

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("grant_type", "refresh_token");
        body.add("refresh_token", refreshToken);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<GitHubOAuthTokenResponse> response = restTemplate.exchange(
                    tokenUrl, HttpMethod.POST, request, GitHubOAuthTokenResponse.class
            );
            GitHubOAuthTokenResponse tokenResponse = response.getBody();
            if (tokenResponse != null && tokenResponse.getError() != null) {
                log.error("GitHub OAuth refresh error: {} - {}", tokenResponse.getError(), tokenResponse.getErrorDescription());
                throw new BadRequestException("Không thể làm mới token GitHub: " + tokenResponse.getErrorDescription());
            }
            return tokenResponse;
        } catch (Exception e) {
            log.error("Failed to refresh GitHub access token: {}", e.getMessage(), e);
            throw new BadRequestException("Không thể làm mới token GitHub: " + e.getMessage());
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
        } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized e) {
            log.error("GitHub API 401 Unauthorized for user profile: {}", e.getMessage());
            throw new BadRequestException("Token GitHub đã hết hạn hoặc bị thu hồi (Bad credentials). Vui lòng kết nối lại tài khoản GitHub.");
        } catch (Exception e) {
            if (e.getMessage() != null && (e.getMessage().contains("401") || e.getMessage().contains("Bad credentials"))) {
                throw new BadRequestException("Token GitHub đã hết hạn hoặc bị thu hồi (Bad credentials). Vui lòng kết nối lại tài khoản GitHub.");
            }
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
        } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized e) {
            log.error("GitHub API 401 Unauthorized for user repos: {}", e.getMessage());
            throw new BadRequestException("Token GitHub đã hết hạn hoặc bị thu hồi (Bad credentials). Vui lòng kết nối lại tài khoản GitHub.");
        } catch (Exception e) {
            if (e.getMessage() != null && (e.getMessage().contains("401") || e.getMessage().contains("Bad credentials"))) {
                throw new BadRequestException("Token GitHub đã hết hạn hoặc bị thu hồi (Bad credentials). Vui lòng kết nối lại tài khoản GitHub.");
            }
            log.error("Failed to get GitHub repositories: {}", e.getMessage(), e);
            throw new BadRequestException("Không thể lấy danh sách kho mã nguồn từ GitHub: " + e.getMessage());
        }
    }

    /**
     * Lấy thông tin một Repository bằng token của người dùng. Dùng để kiểm tra người dùng thực sự truy cập được
     * repo và lấy tên chuẩn (full_name, html_url, default_branch) thay vì tin dữ liệu client gửi lên.
     */
    public GitHubRepoDto getRepository(String accessToken, String owner, String repo) {
        String url = String.format("%s/repos/%s/%s", apiUrl, owner, repo);
        HttpEntity<Void> request = new HttpEntity<>(createAuthHeaders(accessToken));
        try {
            ResponseEntity<GitHubRepoDto> response = restTemplate.exchange(url, HttpMethod.GET, request, GitHubRepoDto.class);
            if (response.getBody() == null) {
                throw new BadRequestException("Không đọc được thông tin repository " + owner + "/" + repo);
            }
            return response.getBody();
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            throw new BadRequestException("Không tìm thấy repository " + owner + "/" + repo + " hoặc tài khoản GitHub của bạn không có quyền truy cập");
        } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized e) {
            throw new BadRequestException("Token GitHub đã hết hạn hoặc bị thu hồi (Bad credentials). Vui lòng kết nối lại tài khoản GitHub.");
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get GitHub repository {}/{}: {}", owner, repo, e.getMessage(), e);
            throw new BadRequestException("Không thể đọc thông tin repository từ GitHub: " + e.getMessage());
        }
    }

    private static final int MAX_BRANCH_PAGES = 3;

    /**
     * Lấy danh sách nhánh của Repository (tối đa {@value #MAX_BRANCH_PAGES} trang × 100 nhánh).
     */
    public List<GitHubBranchDto> listBranches(String accessToken, String owner, String repo) {
        HttpEntity<Void> request = new HttpEntity<>(createAuthHeaders(accessToken));
        List<GitHubBranchDto> all = new java.util.ArrayList<>();
        try {
            for (int page = 1; page <= MAX_BRANCH_PAGES; page++) {
                String url = String.format("%s/repos/%s/%s/branches?per_page=100&page=%d", apiUrl, enc(owner), enc(repo), page);
                ResponseEntity<List<GitHubBranchDto>> response = restTemplate.exchange(
                        url, HttpMethod.GET, request, new ParameterizedTypeReference<List<GitHubBranchDto>>() {});
                List<GitHubBranchDto> body = response.getBody();
                if (body == null || body.isEmpty()) {
                    break;
                }
                all.addAll(body);
                if (body.size() < 100) {
                    break;
                }
            }
            return all;
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            throw new BadRequestException("Không tìm thấy repository " + owner + "/" + repo + " hoặc tài khoản GitHub của bạn không có quyền truy cập");
        } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized e) {
            throw new BadRequestException("Token GitHub đã hết hạn hoặc bị thu hồi (Bad credentials). Vui lòng kết nối lại tài khoản GitHub.");
        } catch (Exception e) {
            log.error("Failed to list branches of {}/{}: {}", owner, repo, e.getMessage(), e);
            throw new BadRequestException("Không thể lấy danh sách nhánh từ GitHub: " + e.getMessage());
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

    /**
     * Lấy danh sách Issues từ GitHub Repository (lọc bỏ các Pull Requests)
     */
    public List<GitHubIssueDto> getRepositoryIssues(String accessToken, String owner, String repo, String state) {
        String queryState = (state != null && !state.isBlank()) ? state : "all";

        HttpHeaders headers = createAuthHeaders(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            List<GitHubIssueDto> all = new ArrayList<>();
            // Phân trang (tối đa MAX_ISSUE_PAGES x 100 issue) thay vì chỉ lấy 100 issue đầu
            for (int page = 1; page <= MAX_ISSUE_PAGES; page++) {
                String url = String.format("%s/repos/%s/%s/issues?state=%s&per_page=100&sort=updated&page=%d",
                        apiUrl, owner, repo, queryState, page);
                ResponseEntity<List<GitHubIssueDto>> response = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        request,
                        new ParameterizedTypeReference<List<GitHubIssueDto>>() {}
                );
                List<GitHubIssueDto> list = response.getBody();
                if (list == null || list.isEmpty()) {
                    break;
                }
                all.addAll(list);
                if (list.size() < 100) {
                    break;
                }
            }
            return all.stream()
                    .filter(issue -> !issue.isPullRequest())
                    .toList();
        } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized e) {
            log.error("GitHub API 401 Unauthorized for {}/{}: {}", owner, repo, e.getMessage());
            throw new BadRequestException("Token GitHub đã hết hạn hoặc bị thu hồi (Bad credentials). Vui lòng kết nối lại tài khoản GitHub.");
        } catch (Exception e) {
            if (e.getMessage() != null && (e.getMessage().contains("401") || e.getMessage().contains("Bad credentials"))) {
                throw new BadRequestException("Token GitHub đã hết hạn hoặc bị thu hồi (Bad credentials). Vui lòng kết nối lại tài khoản GitHub.");
            }
            log.error("Failed to get issues for {}/{}: {}", owner, repo, e.getMessage(), e);
            throw new BadRequestException("Không thể lấy danh sách Issues từ GitHub: " + e.getMessage());
        }
    }

    /**
     * Cập nhật trạng thái của GitHub Issue (ví dụ: open hoặc closed)
     */
    public boolean updateIssueState(String accessToken, String owner, String repo, int issueNumber, String state) {
        String url = String.format("%s/repos/%s/%s/issues/%d", apiUrl, owner, repo, issueNumber);

        HttpHeaders headers = createAuthHeaders(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of("state", state);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        try {
            restTemplate.exchange(url, HttpMethod.PATCH, request, Void.class);
            log.info("Updated GitHub issue #{}: state={} on {}/{}", issueNumber, state, owner, repo);
            return true;
        } catch (Exception e) {
            log.warn("Failed to update GitHub issue #{} state={} on {}/{}: {}", issueNumber, state, owner, repo, e.getMessage());
            // Không throw: lỗi GitHub (rate-limit, thiếu quyền) không được làm hỏng nghiệp vụ phía DATN
            return false;
        }
    }

    /**
     * Tạo nhánh {@code branch} tách từ đầu nhánh {@code baseBranch}.
     *
     * @return true nếu vừa tạo, false nếu nhánh đã tồn tại trên GitHub (coi như thành công)
     */
    @SuppressWarnings("rawtypes")
    public boolean createBranch(String accessToken, String owner, String repo, String branch, String baseBranch) {
        String gitApi = String.format("%s/repos/%s/%s/git", apiUrl, enc(owner), enc(repo));
        HttpHeaders headers = createAuthHeaders(accessToken);

        String sha;
        try {
            // URI dựng sẵn để RestTemplate không mã hoá lại dấu "/" trong tên nhánh (VD: release/1.0)
            ResponseEntity<Map> ref = restTemplate.exchange(URI.create(gitApi + "/ref/heads/" + encPath(baseBranch)),
                    HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            Object object = ref.getBody() != null ? ref.getBody().get("object") : null;
            sha = object instanceof Map<?, ?> obj && obj.get("sha") != null ? obj.get("sha").toString() : null;
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            throw new BadRequestException("Không tìm thấy nhánh đích \"" + baseBranch + "\" trên " + owner + "/" + repo
                    + " (hoặc tài khoản GitHub không truy cập được repo)");
        } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized e) {
            throw new BadRequestException("Token GitHub đã hết hạn hoặc bị thu hồi. Vui lòng kết nối lại tài khoản GitHub.");
        } catch (Exception e) {
            log.error("Failed to read branch {} of {}/{}: {}", baseBranch, owner, repo, e.getMessage(), e);
            throw new BadRequestException("Không đọc được nhánh đích từ GitHub: " + e.getMessage());
        }
        if (sha == null || sha.isBlank()) {
            throw new BadRequestException("GitHub không trả về commit của nhánh đích \"" + baseBranch + "\"");
        }

        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of("ref", "refs/heads/" + branch, "sha", sha);
        try {
            restTemplate.exchange(URI.create(gitApi + "/refs"), HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
            log.info("Created branch {} from {} on {}/{}", branch, baseBranch, owner, repo);
            return true;
        } catch (org.springframework.web.client.HttpClientErrorException.UnprocessableEntity e) {
            if (e.getResponseBodyAsString().toLowerCase(Locale.ROOT).contains("already exists")) {
                return false;
            }
            throw new BadRequestException("GitHub từ chối tên nhánh \"" + branch + "\"");
        } catch (org.springframework.web.client.HttpClientErrorException.Forbidden
                 | org.springframework.web.client.HttpClientErrorException.NotFound e) {
            throw new BadRequestException("Tài khoản GitHub dùng để tạo nhánh không có quyền ghi vào " + owner + "/" + repo);
        } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized e) {
            throw new BadRequestException("Token GitHub đã hết hạn hoặc bị thu hồi. Vui lòng kết nối lại tài khoản GitHub.");
        } catch (Exception e) {
            log.error("Failed to create branch {} on {}/{}: {}", branch, owner, repo, e.getMessage(), e);
            throw new BadRequestException("Không tạo được nhánh trên GitHub: " + e.getMessage());
        }
    }

    /** Tạo Issue mới trên repo, trả về Issue GitHub vừa tạo (số, URL, tác giả). */
    public GitHubIssueDto createIssue(String accessToken, String owner, String repo, String title, String body) {
        HttpHeaders headers = createAuthHeaders(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> payload = new HashMap<>();
        payload.put("title", title);
        payload.put("body", body);
        try {
            ResponseEntity<GitHubIssueDto> response = restTemplate.exchange(
                    URI.create(String.format("%s/repos/%s/%s/issues", apiUrl, enc(owner), enc(repo))),
                    HttpMethod.POST, new HttpEntity<>(payload, headers), GitHubIssueDto.class);
            if (response.getBody() == null || response.getBody().getNumber() == null) {
                throw new BadRequestException("GitHub không trả về Issue vừa tạo");
            }
            log.info("Created GitHub issue #{} on {}/{}", response.getBody().getNumber(), owner, repo);
            return response.getBody();
        } catch (BadRequestException e) {
            throw e;
        } catch (org.springframework.web.client.HttpClientErrorException.Gone e) {
            throw new BadRequestException("Repository " + owner + "/" + repo + " đang tắt tính năng Issues trên GitHub");
        } catch (org.springframework.web.client.HttpClientErrorException.Forbidden
                 | org.springframework.web.client.HttpClientErrorException.NotFound e) {
            throw new BadRequestException("Tài khoản GitHub dùng để tạo Issue không có quyền ghi vào " + owner + "/" + repo);
        } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized e) {
            throw new BadRequestException("Token GitHub đã hết hạn hoặc bị thu hồi. Vui lòng kết nối lại tài khoản GitHub.");
        } catch (Exception e) {
            log.error("Failed to create issue on {}/{}: {}", owner, repo, e.getMessage(), e);
            throw new BadRequestException("Không tạo được Issue trên GitHub: " + e.getMessage());
        }
    }

    /** Mã hoá từng đoạn của tên nhánh, giữ nguyên dấu "/" phân cách. */
    private static String encPath(String branch) {
        List<String> parts = new ArrayList<>();
        for (String part : (branch != null ? branch : "").split("/")) {
            parts.add(enc(part).replace("+", "%20"));
        }
        return String.join("/", parts);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value != null ? value : "", StandardCharsets.UTF_8);
    }

    private HttpHeaders createAuthHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(Collections.singletonList(MediaType.parseMediaType("application/vnd.github.v3+json")));
        headers.set(HttpHeaders.USER_AGENT, "DATN-WorkService-2026");
        return headers;
    }
}
