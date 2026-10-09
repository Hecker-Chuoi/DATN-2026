package com.datn.workservice.integration.github;

import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workservice.entity.Project;
import com.datn.workservice.enums.ProjectType;
import com.datn.workservice.integration.github.GithubDtos.ConnectionStatus;
import com.datn.workservice.integration.github.GithubDtos.LinkRepoRequest;
import com.datn.workservice.integration.github.GithubDtos.RepoOption;
import com.datn.workservice.repository.ProjectRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GithubServiceImpl implements GithubService {

    private static final String OAUTH_STATE_PREFIX = "gh:oauth:";
    private static final String SCOPE = "repo read:user";

    private final GithubProperties props;
    private final GithubApi api;
    private final TokenCrypto crypto;
    private final GithubConnectionRepository connectionRepository;
    private final GithubRepoLinkRepository repoLinkRepository;
    private final ProjectRepository projectRepository;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    // ─── OAuth ───────────────────────────────────────────────────────────────

    @Override
    public String buildAuthorizeUrl(UUID userId, String redirectUri) {
        if (props.getClientId() == null || props.getClientId().isBlank()) {
            throw new BadRequestException("Chưa cấu hình GITHUB_CLIENT_ID cho work-service");
        }
        String state = UUID.randomUUID().toString();
        redis.opsForValue().set(OAUTH_STATE_PREFIX + state, userId.toString(), Duration.ofMinutes(10));
        return UriComponentsBuilder.fromUriString("https://github.com/login/oauth/authorize")
                .queryParam("client_id", props.getClientId())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", SCOPE)
                .queryParam("state", state)
                .build().encode().toUriString();
    }

    @Override
    @Transactional
    public ConnectionStatus completeOAuth(UUID userId, String code, String state, String redirectUri) {
        String owner = redis.opsForValue().getAndDelete(OAUTH_STATE_PREFIX + state);
        if (owner == null || !owner.equals(userId.toString())) {
            throw new BadRequestException("Phiên kết nối GitHub không hợp lệ hoặc đã hết hạn, hãy thử lại");
        }
        JsonNode tokenRes = api.exchangeCode(code, redirectUri);
        String token = tokenRes.path("access_token").asText();
        JsonNode user = api.get(token, "/user");

        GithubConnection conn = connectionRepository.findByUserId(userId)
                .orElseGet(() -> GithubConnection.builder().userId(userId).build());
        conn.setGithubUserId(user.path("id").asLong());
        conn.setGithubLogin(user.path("login").asText());
        conn.setAvatarUrl(user.path("avatar_url").asText(null));
        conn.setAccessTokenEnc(crypto.encrypt(token));
        conn.setScope(tokenRes.path("scope").asText(null));
        conn.setInvalid(false);
        connectionRepository.save(conn);
        log.info("GitHub connected: user={} login={}", userId, conn.getGithubLogin());
        return ConnectionStatus.of(conn);
    }

    @Override
    @Transactional(readOnly = true)
    public ConnectionStatus myConnection(UUID userId) {
        return connectionRepository.findByUserId(userId).map(ConnectionStatus::of).orElse(ConnectionStatus.none());
    }

    @Override
    @Transactional
    public void disconnect(UUID userId) {
        // ponytail: không revoke token phía GitHub; người dùng tự gỡ ở Settings > Applications nếu muốn.
        connectionRepository.deleteByUserId(userId);
    }

    // ─── Repo ────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<RepoOption> listMyRepos(UUID userId) {
        GithubConnection conn = requireConnection(userId);
        // ponytail: chỉ trang 1 (100 repo mới cập nhật nhất); thêm phân trang khi cần.
        JsonNode repos = callAs(conn, () -> api.get(token(conn),
                "/user/repos?per_page=100&sort=updated&affiliation=owner,collaborator,organization_member"));
        List<RepoOption> out = new ArrayList<>();
        for (JsonNode r : repos) {
            out.add(new RepoOption(r.path("id").asLong(), r.path("full_name").asText(),
                    r.path("owner").path("login").asText(), r.path("name").asText(),
                    r.path("private").asBoolean(), r.path("default_branch").asText("main")));
        }
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public GithubRepoLink getRepoLink(UUID projectId) {
        return repoLinkRepository.findByProjectId(projectId).orElse(null);
    }

    @Override
    @Transactional
    public GithubRepoLink linkRepo(UUID projectId, LinkRepoRequest request, UUID userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dự án: " + projectId));
        if (project.getProjectType() == ProjectType.BUSINESS) {
            throw new BadRequestException("Chỉ dự án phần mềm (Scrum/Kanban) mới liên kết được GitHub");
        }
        boolean onOpen = request.autoOnPrOpen() == null || request.autoOnPrOpen();
        boolean onMerge = request.autoOnPrMerge() == null || request.autoOnPrMerge();

        GithubRepoLink existing = repoLinkRepository.findByProjectId(projectId).orElse(null);
        if (existing != null && existing.getRepoOwner().equalsIgnoreCase(request.owner())
                && existing.getRepoName().equalsIgnoreCase(request.name())) {
            existing.setAutoTransitionOnPrOpen(onOpen);
            existing.setAutoTransitionOnPrMerge(onMerge);
            return repoLinkRepository.save(existing);
        }

        GithubConnection conn = requireConnection(userId);
        if (existing != null) {
            deleteHookQuietly(existing, conn);
            repoLinkRepository.delete(existing);
            repoLinkRepository.flush();
        }

        String repoPath = "/repos/" + request.owner() + "/" + request.name();
        JsonNode repo = callAs(conn, () -> api.get(token(conn), repoPath));
        JsonNode hook = callAs(conn, () -> api.post(token(conn), repoPath + "/hooks", Map.of(
                "name", "web",
                "active", true,
                "events", List.of("push", "pull_request", "create", "delete"),
                "config", Map.of(
                        "url", props.webhookUrl(),
                        "content_type", "json",
                        "secret", props.getWebhookSecret(),
                        "insecure_ssl", "0"))));

        GithubRepoLink link = GithubRepoLink.builder()
                .projectId(projectId)
                .repoId(repo.path("id").asLong())
                .repoOwner(repo.path("owner").path("login").asText(request.owner()))
                .repoName(repo.path("name").asText(request.name()))
                .htmlUrl(repo.path("html_url").asText())
                .defaultBranch(repo.path("default_branch").asText("main"))
                .webhookId(hook.path("id").isNumber() ? hook.path("id").asLong() : null)
                .linkedBy(userId)
                .autoTransitionOnPrOpen(onOpen)
                .autoTransitionOnPrMerge(onMerge)
                .build();
        try {
            GithubRepoLink saved = repoLinkRepository.saveAndFlush(link);
            log.info("GitHub repo linked: project={} repo={} hook={}", projectId, saved.fullName(), saved.getWebhookId());
            return saved;
        } catch (DataIntegrityViolationException e) {
            deleteHookQuietly(link, conn);
            throw new BadRequestException("Repo này đã được liên kết với dự án khác");
        }
    }

    @Override
    @Transactional
    public void unlinkRepo(UUID projectId, UUID userId) {
        GithubRepoLink link = repoLinkRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Dự án chưa liên kết repo GitHub"));
        GithubConnection conn = connectionRepository.findByUserId(userId)
                .or(() -> connectionRepository.findByUserId(link.getLinkedBy()))
                .orElse(null);
        if (conn != null) {
            deleteHookQuietly(link, conn);
        }
        repoLinkRepository.delete(link);
        log.info("GitHub repo unlinked: project={} repo={}", projectId, link.fullName());
    }

    // ─── Webhook ─────────────────────────────────────────────────────────────

    @Override
    public void handleWebhook(String event, String signature, byte[] body) {
        verifySignature(signature, body);
        JsonNode json;
        try {
            json = objectMapper.readTree(body);
        } catch (IOException e) {
            throw new BadRequestException("Payload webhook không phải JSON");
        }
        String repoFullName = json.path("repository").path("full_name").asText("?");
        log.info("GitHub webhook '{}' từ repo {} (action={})", event, repoFullName, json.path("action").asText("-"));
        // ponytail: đợt sau xử lý push / pull_request / create / delete -> work_item_dev_links + auto transition.
    }

    private void verifySignature(String signature, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(props.getWebhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
            if (signature == null || !MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
                throw new AccessDeniedException("Chữ ký webhook GitHub không hợp lệ");
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private GithubConnection requireConnection(UUID userId) {
        GithubConnection conn = connectionRepository.findByUserId(userId)
                .orElseThrow(() -> new BadRequestException("Hãy kết nối tài khoản GitHub ở Trang cá nhân trước"));
        if (conn.isInvalid()) {
            throw new BadRequestException("Token GitHub không còn hiệu lực, hãy kết nối lại ở Trang cá nhân");
        }
        return conn;
    }

    private String token(GithubConnection conn) {
        return crypto.decrypt(conn.getAccessTokenEnc());
    }

    /** Gọi GitHub bằng token của connection; 401 thì đánh dấu invalid rồi báo lỗi dễ hiểu. */
    private <T> T callAs(GithubConnection conn, java.util.function.Supplier<T> call) {
        try {
            return call.get();
        } catch (GithubApi.Unauthorized e) {
            conn.setInvalid(true);
            connectionRepository.save(conn);
            throw new BadRequestException("Token GitHub không còn hiệu lực, hãy kết nối lại ở Trang cá nhân");
        }
    }

    private void deleteHookQuietly(GithubRepoLink link, GithubConnection conn) {
        if (link.getWebhookId() == null) return;
        try {
            api.delete(token(conn), "/repos/" + link.fullName() + "/hooks/" + link.getWebhookId());
        } catch (RuntimeException e) {
            log.warn("Không xóa được webhook {} của {}: {}", link.getWebhookId(), link.fullName(), e.getMessage());
        }
    }
}
