package com.datn.workservice.service.impl;

import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workservice.client.GitHubApiClient;
import com.datn.workservice.dto.github.*;
import com.datn.workservice.dto.request.ExecuteTransitionRequest;
import com.datn.workservice.dto.response.AvailableTransitionResponse;
import com.datn.workservice.entity.*;
import com.datn.workservice.enums.GitHubLinkType;
import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.enums.StatusCategory;
import com.datn.workservice.enums.WorkItemPriority;
import com.datn.workservice.enums.WorkItemResolution;
import com.datn.workservice.repository.*;
import com.datn.workservice.security.GitHubOAuthStateService;
import com.datn.workservice.security.GitHubWebhookSignatureValidator;
import com.datn.workservice.service.AtomicKeySequenceGenerator;
import com.datn.workservice.service.GitHubIntegrationService;
import com.datn.workservice.service.LexoRankService;
import com.datn.workservice.service.ProjectMemberService;
import com.datn.workservice.service.WorkflowEngineService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubIntegrationServiceImpl implements GitHubIntegrationService {

    private final GitHubApiClient gitHubApiClient;
    private final UserGitHubTokenRepository userGitHubTokenRepository;
    private final ProjectGitHubRepoRepository projectGitHubRepoRepository;
    private final ProjectRepository projectRepository;
    private final WorkItemRepository workItemRepository;
    private final WorkItemGitHubLinkRepository workItemGitHubLinkRepository;
    private final WorkItemTypeRepository workItemTypeRepository;
    private final StatusRepository statusRepository;
    private final AtomicKeySequenceGenerator atomicKeySequenceGenerator;
    private final LexoRankService lexoRankService;
    private final GitHubWebhookSignatureValidator gitHubWebhookSignatureValidator;
    private final GitHubOAuthStateService gitHubOAuthStateService;
    private final ProjectMemberService projectMemberService;
    private final WorkflowEngineService workflowEngineService;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * URL công khai ĐẦY ĐỦ mà GitHub gọi tới, ví dụ qua gateway + ngrok:
     * {@code https://xxxx.ngrok-free.app/work/api/v1/work/integrations/github/webhook}.
     * Để trống thì không tự đăng ký webhook (chỉ đồng bộ tay).
     */
    @Value("${github.webhook-url:}")
    private String webhookUrl;

    /** Đích chuyển trạng thái Task theo sự kiện GitHub. */
    private enum MoveTarget { DONE, REOPEN, IN_REVIEW }

    /**
     * Kết quả bước 1 của webhook: cần chuyển trạng thái Task qua workflow sau khi lưu liên kết.
     * {@code actorGithubId} là người thao tác trên GitHub (người đóng Issue, người merge PR…).
     */
    private record FollowUp(UUID workItemId, UUID projectId, MoveTarget target, String ref, Long actorGithubId) {
    }

    /** Tên trạng thái được coi là "đang review" (dự án có thể tự đặt tên tiếng Anh hoặc tiếng Việt). */
    private static final List<String> REVIEW_STATUS_KEYWORDS = List.of("review", "duyệt", "kiểm tra", "kiểm thử");

    @Override
    public String getOAuthAuthorizeUrl(UUID projectId, UUID currentUserId) {
        return gitHubApiClient.buildAuthorizeUrl(gitHubOAuthStateService.issue(projectId, currentUserId));
    }

    @Override
    @Transactional
    public ProjectGitHubStatusResponse handleOAuthCallback(UUID projectId, String code, String state, UUID currentUserId) {
        if (code == null || code.isBlank()) {
            throw new BadRequestException("Mã xác thực Authorization Code từ GitHub không hợp lệ");
        }
        // Chống OAuth CSRF: state phải do server phát cho đúng người dùng + dự án này
        gitHubOAuthStateService.verify(state, projectId, currentUserId);

        // 1. Đổi code lấy Access Token
        GitHubOAuthTokenResponse tokenResponse = gitHubApiClient.exchangeCodeForToken(code);
        String accessToken = tokenResponse.getAccessToken();

        // 2. Lấy thông tin user GitHub
        GitHubUserResponse userProfile = gitHubApiClient.getUserProfile(accessToken);

        // 3. Lưu hoặc cập nhật Token của người dùng
        UserGitHubToken userToken = userGitHubTokenRepository.findByUserId(currentUserId)
                .orElse(UserGitHubToken.builder().userId(currentUserId).build());

        userToken.setGithubUserId(userProfile.getId());
        userToken.setGithubUsername(userProfile.getLogin());
        userToken.setGithubAvatarUrl(userProfile.getAvatarUrl());
        userToken.setAccessTokenEncrypted(accessToken); // GitHubTokenConverter mã hoá AES-GCM khi lưu
        userToken.setRefreshTokenEncrypted(tokenResponse.getRefreshToken());
        userToken.setExpiresAt(tokenResponse.getExpiresIn() != null
                ? LocalDateTime.now().plusSeconds(tokenResponse.getExpiresIn()) : null);
        userToken.setRefreshTokenExpiresAt(tokenResponse.getRefreshTokenExpiresIn() != null
                ? LocalDateTime.now().plusSeconds(tokenResponse.getRefreshTokenExpiresIn()) : null);
        userGitHubTokenRepository.save(userToken);

        log.info("Linked GitHub account @{} for user {}", userProfile.getLogin(), currentUserId);

        return getProjectGitHubStatus(projectId, currentUserId);
    }

    @Override
    @Transactional
    public List<GitHubRepoDto> getAvailableRepositories(UUID currentUserId) {
        UserGitHubToken userToken = userGitHubTokenRepository.findByUserId(currentUserId)
                .orElseThrow(() -> new BadRequestException("Bạn chưa kết nối tài khoản GitHub"));

        return gitHubApiClient.getUserRepositories(getValidAccessToken(userToken));
    }

    @Override
    @Transactional
    public ProjectGitHubStatusResponse connectRepository(UUID projectId, ConnectRepoRequest request, UUID currentUserId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dự án với ID: " + projectId));

        UserGitHubToken userToken = userGitHubTokenRepository.findByUserId(currentUserId)
                .orElseThrow(() -> new BadRequestException("Vui lòng kết nối tài khoản GitHub trước khi liên kết kho"));

        String[] parts = request.getRepoFullName() != null ? request.getRepoFullName().trim().split("/") : new String[0];
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new BadRequestException("Tên repository không hợp lệ. Định dạng chuẩn phải là: owner/repo");
        }
        String accessToken = getValidAccessToken(userToken);

        // Kiểm tra người dùng thực sự truy cập được repo, lấy tên/URL chuẩn từ GitHub thay vì tin client
        GitHubRepoDto ghRepo = gitHubApiClient.getRepository(accessToken, parts[0].trim(), parts[1].trim());
        String fullName = ghRepo.getFullName() != null ? ghRepo.getFullName() : parts[0].trim() + "/" + parts[1].trim();
        String owner = fullName.substring(0, fullName.indexOf('/'));
        String repoName = fullName.substring(fullName.indexOf('/') + 1);

        ProjectGitHubRepo projectRepo = projectGitHubRepoRepository.findByProjectId(projectId)
                .orElse(ProjectGitHubRepo.builder().projectId(projectId).build());

        // Đổi repo / kết nối lại: gỡ webhook cũ để không còn webhook "mồ côi" bắn về
        if (projectRepo.getWebhookId() != null) {
            deleteWebhookQuietly(projectRepo, currentUserId);
        }

        // Secret ngẫu nhiên riêng cho từng repo (không dùng secret chung)
        String webhookSecret = newWebhookSecret();
        Long webhookId = null;
        if (webhookUrl != null && !webhookUrl.isBlank()) {
            webhookId = gitHubApiClient.createWebhook(accessToken, owner, repoName, webhookUrl, webhookSecret);
        }

        projectRepo.setRepoOwner(owner);
        projectRepo.setRepoName(repoName);
        projectRepo.setRepoFullName(fullName);
        projectRepo.setRepoUrl(ghRepo.getHtmlUrl() != null ? ghRepo.getHtmlUrl() : request.getRepoUrl());
        projectRepo.setDefaultBranch(ghRepo.getDefaultBranch() != null ? ghRepo.getDefaultBranch()
                : (request.getDefaultBranch() != null ? request.getDefaultBranch() : "main"));
        projectRepo.setWebhookId(webhookId);
        projectRepo.setWebhookSecret(webhookSecret);
        projectRepo.setConnectedByUserId(currentUserId);
        if (request.getSyncIssues() != null) {
            projectRepo.setSyncIssuesEnabled(request.getSyncIssues());
        }

        projectGitHubRepoRepository.save(projectRepo);
        log.info("Connected repo {} to project {} (webhook={})", fullName, project.getName(), webhookId);

        return getProjectGitHubStatus(projectId, currentUserId);
    }

    @Override
    @Transactional
    public void disconnectRepository(UUID projectId, UUID currentUserId) {
        Optional<ProjectGitHubRepo> repoOpt = projectGitHubRepoRepository.findByProjectId(projectId);
        if (repoOpt.isEmpty()) {
            return;
        }

        ProjectGitHubRepo repo = repoOpt.get();
        deleteWebhookQuietly(repo, currentUserId);

        projectGitHubRepoRepository.delete(repo);
        log.info("Disconnected repo {} from project {}", repo.getRepoFullName(), projectId);
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectGitHubStatusResponse getProjectGitHubStatus(UUID projectId, UUID currentUserId) {
        Optional<UserGitHubToken> userTokenOpt = userGitHubTokenRepository.findByUserId(currentUserId);
        Optional<ProjectGitHubRepo> projectRepoOpt = projectGitHubRepoRepository.findByProjectId(projectId);

        ProjectGitHubStatusResponse.ProjectGitHubStatusResponseBuilder builder = ProjectGitHubStatusResponse.builder();

        if (userTokenOpt.isPresent()) {
            UserGitHubToken token = userTokenOpt.get();
            builder.userConnected(true)
                    .githubUsername(token.getGithubUsername())
                    .githubAvatarUrl(token.getGithubAvatarUrl());
        } else {
            builder.userConnected(false);
        }

        if (projectRepoOpt.isPresent()) {
            ProjectGitHubRepo repo = projectRepoOpt.get();
            builder.projectLinked(true)
                    .repoLinkId(repo.getId())
                    .repoFullName(repo.getRepoFullName())
                    .repoUrl(repo.getRepoUrl())
                    .defaultBranch(repo.getDefaultBranch())
                    .webhookActive(repo.getWebhookId() != null && repo.getWebhookSecret() != null)
                    .syncIssuesEnabled(repo.getSyncIssuesEnabled())
                    .autoMoveInReviewOnPr(repo.getAutoMoveInReviewOnPr())
                    .autoCloseTaskOnPrMerge(repo.getAutoCloseTaskOnPrMerge())
                    .autoCloseGithubIssueOnDone(repo.getAutoCloseGithubIssueOnDone())
                    .autoCloseTaskOnIssueClose(repo.getAutoCloseTaskOnIssueClose())
                    .lastSyncedAt(repo.getLastSyncedAt());
        } else {
            builder.projectLinked(false);
        }

        return builder.build();
    }

    @Override
    @Transactional
    public ProjectGitHubStatusResponse updateSyncConfig(UUID projectId, UpdateGitHubSyncConfigRequest request, UUID currentUserId) {
        ProjectGitHubRepo repo = projectGitHubRepoRepository.findByProjectId(projectId)
                .orElseThrow(() -> new BadRequestException("Dự án chưa liên kết với GitHub Repository nào"));

        if (request.getSyncIssuesEnabled() != null) {
            repo.setSyncIssuesEnabled(request.getSyncIssuesEnabled());
        }
        if (request.getAutoMoveInReviewOnPr() != null) {
            repo.setAutoMoveInReviewOnPr(request.getAutoMoveInReviewOnPr());
        }
        if (request.getAutoCloseTaskOnPrMerge() != null) {
            repo.setAutoCloseTaskOnPrMerge(request.getAutoCloseTaskOnPrMerge());
        }
        if (request.getAutoCloseGithubIssueOnDone() != null) {
            repo.setAutoCloseGithubIssueOnDone(request.getAutoCloseGithubIssueOnDone());
        }
        if (request.getAutoCloseTaskOnIssueClose() != null) {
            repo.setAutoCloseTaskOnIssueClose(request.getAutoCloseTaskOnIssueClose());
        }

        projectGitHubRepoRepository.save(repo);
        return getProjectGitHubStatus(projectId, currentUserId);
    }

    @Override
    @Transactional
    public GitHubSyncResultResponse syncIssues(UUID projectId, UUID currentUserId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dự án với ID: " + projectId));

        ProjectGitHubRepo repo = projectGitHubRepoRepository.findByProjectId(projectId)
                .orElseThrow(() -> new BadRequestException("Dự án chưa liên kết với GitHub Repository nào"));

        // Chỉ dùng token của người trong dự án: người đang thao tác -> người đã kết nối repo -> Lead
        UserGitHubToken userToken = findFirstToken(currentUserId, repo.getConnectedByUserId(), project.getLeadUserId())
                .orElseThrow(() -> new BadRequestException(
                        "Bạn cần kết nối tài khoản GitHub (có quyền truy cập repository) để đồng bộ Issues"));

        List<GitHubIssueDto> issues = gitHubApiClient.getRepositoryIssues(
                getValidAccessToken(userToken),
                repo.getRepoOwner(),
                repo.getRepoName(),
                "all"
        );

        int totalFound = issues.size();
        int importedCount = 0;
        int updatedCount = 0;

        UUID defaultTypeId = resolveDefaultWorkItemTypeId(projectId);
        UUID initialStatusId = resolveInitialStatus(projectId);
        UUID doneStatusId = resolveDoneStatus(projectId);

        for (GitHubIssueDto issue : issues) {
            Optional<WorkItemGitHubLink> existingLink = workItemGitHubLinkRepository
                    .findByProjectIdAndLinkTypeAndGithubNumber(projectId, GitHubLinkType.ISSUE, issue.getNumber());

            String issueState = (issue.getState() != null) ? issue.getState().toUpperCase() : "OPEN";
            boolean isClosed = "CLOSED".equals(issueState);

            if (existingLink.isPresent()) {
                WorkItemGitHubLink link = existingLink.get();
                link.setTitle(issue.getTitle());
                link.setStatus(issueState);
                if (issue.getHtmlUrl() != null) {
                    link.setUrl(issue.getHtmlUrl());
                }
                workItemGitHubLinkRepository.save(link);
                updatedCount++;
            } else {
                // Endpoint yêu cầu PROJECT_ADMIN nên người nhập là Admin dự án: Issue đã đóng được tạo thẳng ở
                // nhóm DONE là hợp lệ với quy tắc "Tạm hoàn thành" (task mới, chưa có yêu cầu chờ duyệt).
                boolean importAsDone = isClosed && doneStatusId != null;
                WorkItem savedItem = createWorkItemFromIssue(project, issue,
                        importAsDone ? doneStatusId : initialStatusId, currentUserId, importAsDone);
                workItemGitHubLinkRepository.save(buildIssueLink(projectId, savedItem.getId(), issue, issueState));
                importedCount++;
            }
        }

        repo.setLastSyncedAt(LocalDateTime.now());
        projectGitHubRepoRepository.save(repo);

        log.info("Synced GitHub issues for project '{}': total={}, imported={}, updated={}",
                project.getName(), totalFound, importedCount, updatedCount);

        return GitHubSyncResultResponse.builder()
                .totalFound(totalFound)
                .importedCount(importedCount)
                .updatedCount(updatedCount)
                .lastSyncedAt(repo.getLastSyncedAt())
                .build();
    }

    /**
     * Không đặt @Transactional ở đây: bước lưu liên kết chạy trong transaction riêng (TransactionTemplate),
     * sau đó việc chuyển trạng thái đi qua WorkflowEngine trong transaction của chính nó. Nhờ vậy lỗi ở
     * workflow (thiếu bước chuyển, validator chặn…) chỉ được ghi log, không làm rollback liên kết đã lưu.
     */
    @Override
    public void handleWebhook(String event, String signature, String rawPayload) {
        log.info("Handling GitHub webhook event: {}", event);

        JsonNode root;
        try {
            root = objectMapper.readTree(rawPayload);
        } catch (Exception e) {
            log.error("Failed to parse GitHub webhook payload JSON: {}", e.getMessage());
            throw new BadRequestException("Payload JSON của Webhook không hợp lệ");
        }

        // ping không thay đổi dữ liệu nên không cần xác thực
        if ("ping".equalsIgnoreCase(event)) {
            log.info("GitHub webhook ping received: hook_id={}, zen={}",
                    root.path("hook_id").asText(), root.path("zen").asText());
            return;
        }

        String repoFullName = root.path("repository").path("full_name").asText();
        if (repoFullName == null || repoFullName.isBlank()) {
            log.warn("Webhook payload does not contain repository.full_name, ignoring");
            return;
        }

        List<ProjectGitHubRepo> candidates = projectGitHubRepoRepository.findAllByRepoFullNameIgnoreCase(repoFullName);
        if (candidates.isEmpty()) {
            log.info("No project linked to GitHub repository '{}', ignoring webhook", repoFullName);
            return;
        }

        // Mỗi dự án có webhook + secret riêng: chỉ xử lý cho (các) dự án có secret khớp chữ ký
        List<ProjectGitHubRepo> verified = candidates.stream()
                .filter(r -> r.getWebhookSecret() != null && !r.getWebhookSecret().isBlank())
                .filter(r -> gitHubWebhookSignatureValidator.isValidSignature(rawPayload, signature, r.getWebhookSecret()))
                .toList();
        if (verified.isEmpty()) {
            log.warn("Invalid HMAC signature for GitHub webhook on repository '{}'", repoFullName);
            throw new BadRequestException("Chữ ký Webhook GitHub không hợp lệ");
        }

        for (ProjectGitHubRepo repo : verified) {
            if ("issues".equalsIgnoreCase(event)) {
                handleIssueEvent(repo, root);
            } else if ("pull_request".equalsIgnoreCase(event)) {
                handlePullRequestEvent(repo, root);
            } else {
                log.info("Unhandled GitHub webhook event type: {}", event);
                return;
            }
        }
    }

    private void handleIssueEvent(ProjectGitHubRepo repo, JsonNode root) {
        if (!Boolean.TRUE.equals(repo.getSyncIssuesEnabled())) {
            log.info("Issue synchronization is disabled for repository '{}', skipping event", repo.getRepoFullName());
            return;
        }

        String action = root.path("action").asText("");
        JsonNode issueNode = root.path("issue");
        if (issueNode.isMissingNode() || issueNode.isNull()) {
            return;
        }

        GitHubIssueDto issue;
        try {
            issue = objectMapper.treeToValue(issueNode, GitHubIssueDto.class);
        } catch (Exception e) {
            log.error("Failed to map issue node to GitHubIssueDto: {}", e.getMessage());
            return;
        }
        if (issue.isPullRequest() || issue.getNumber() == null) {
            return;
        }

        FollowUp followUp;
        try {
            followUp = transactionTemplate.execute(status -> applyIssueEvent(repo, action, issue, root));
        } catch (DataIntegrityViolationException e) {
            // Chỉ mục duy nhất (project_id, github_number): webhook gửi lại hoặc chạy song song với đồng bộ tay
            log.info("GitHub issue #{} đã được liên kết trong dự án {}, bỏ qua bản trùng", issue.getNumber(), repo.getProjectId());
            return;
        }

        if (followUp != null) {
            moveWorkItemByGitHub(followUp);
        }
    }

    /**
     * Pull Request nhắc mã Task (tiêu đề, tên nhánh hoặc mô tả, ví dụ {@code feat/DATN-12-auth}):
     * gắn liên kết PR vào Task; mở PR (không phải draft) thì chuyển Task sang trạng thái review;
     * merge PR thì hoàn thành Task (qua workflow + cổng "Tạm hoàn thành", với tư cách người merge).
     */
    private void handlePullRequestEvent(ProjectGitHubRepo repo, JsonNode root) {
        String action = root.path("action").asText("");
        JsonNode pr = root.path("pull_request");
        if (pr.isMissingNode() || pr.isNull() || !pr.path("number").canConvertToInt()) {
            return;
        }
        Long senderId = longOrNull(root.path("sender").path("id"));
        Long mergerId = longOrNull(pr.path("merged_by").path("id"));
        boolean merged = pr.path("merged").asBoolean(false);
        boolean draft = pr.path("draft").asBoolean(false);

        List<FollowUp> followUps = transactionTemplate.execute(status -> {
            Project project = projectRepository.findById(repo.getProjectId()).orElse(null);
            if (project == null) {
                return List.<FollowUp>of();
            }
            String text = String.join("\n",
                    pr.path("title").asText(""),
                    pr.path("head").path("ref").asText(""),
                    pr.path("body").asText(""));
            List<WorkItem> items = findMentionedWorkItems(project, text);
            if (items.isEmpty()) {
                return List.<FollowUp>of();
            }

            String linkStatus = "closed".equals(action) ? (merged ? "MERGED" : "CLOSED") : "OPEN";
            int number = pr.path("number").asInt();
            String ref = "PR #" + number;
            List<FollowUp> result = new ArrayList<>();
            for (WorkItem item : items) {
                upsertPullRequestLink(project.getId(), item.getId(), pr, number, linkStatus);

                switch (action) {
                    case "opened", "reopened", "ready_for_review" -> {
                        if (!draft && Boolean.TRUE.equals(repo.getAutoMoveInReviewOnPr())) {
                            result.add(new FollowUp(item.getId(), project.getId(), MoveTarget.IN_REVIEW, ref, senderId));
                        }
                    }
                    case "closed" -> {
                        if (merged && Boolean.TRUE.equals(repo.getAutoCloseTaskOnPrMerge())) {
                            result.add(new FollowUp(item.getId(), project.getId(), MoveTarget.DONE, ref,
                                    mergerId != null ? mergerId : senderId));
                        }
                    }
                    default -> {
                        // edited / synchronize…: chỉ cập nhật liên kết
                    }
                }
            }
            log.info("GitHub {} ({}) liên kết với {} công việc trong dự án '{}'", ref, action, items.size(), project.getName());
            return result;
        });

        if (followUps != null) {
            followUps.forEach(this::moveWorkItemByGitHub);
        }
    }

    private void upsertPullRequestLink(UUID projectId, UUID workItemId, JsonNode pr, int number, String status) {
        WorkItemGitHubLink link = workItemGitHubLinkRepository
                .findByWorkItemIdAndLinkTypeAndGithubNumber(workItemId, GitHubLinkType.PULL_REQUEST, number)
                .orElseGet(() -> WorkItemGitHubLink.builder()
                        .workItemId(workItemId)
                        .projectId(projectId)
                        .linkType(GitHubLinkType.PULL_REQUEST)
                        .githubNumber(number)
                        .build());
        link.setGithubId(pr.path("id").asText(null));
        link.setTitle(pr.path("title").asText(null));
        link.setUrl(pr.path("html_url").asText(""));
        link.setStatus(status);
        link.setAuthorName(pr.path("user").path("login").asText(null));
        link.setAuthorAvatarUrl(pr.path("user").path("avatar_url").asText(null));
        workItemGitHubLinkRepository.save(link);
    }

    /** Tìm các mã Task dạng {@code KEY-123} của dự án xuất hiện trong văn bản (không phân biệt hoa/thường). */
    private List<WorkItem> findMentionedWorkItems(Project project, String text) {
        if (project.getKey() == null || project.getKey().isBlank() || text == null || text.isBlank()) {
            return List.of();
        }
        String key = project.getKey().trim().toUpperCase(Locale.ROOT);
        Pattern pattern = Pattern.compile("(?<![A-Za-z0-9])" + Pattern.quote(key) + "-(\\d+)(?![0-9])",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        Set<String> codes = new LinkedHashSet<>();
        while (matcher.find() && codes.size() < 20) {
            codes.add(key + "-" + matcher.group(1));
        }
        List<WorkItem> items = new ArrayList<>();
        for (String code : codes) {
            workItemRepository.findByProjectIdAndCode(project.getId(), code)
                    .filter(item -> item.getDeletedAt() == null)
                    .ifPresent(items::add);
        }
        return items;
    }

    private static Long longOrNull(JsonNode node) {
        return node != null && !node.isMissingNode() && !node.isNull() && node.canConvertToLong() ? node.asLong() : null;
    }

    /** Chạy trong transaction: cập nhật liên kết / tạo Task. Trả về việc cần chuyển trạng thái (nếu có). */
    private FollowUp applyIssueEvent(ProjectGitHubRepo repo, String action, GitHubIssueDto issue, JsonNode root) {
        UUID projectId = repo.getProjectId();
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            return null;
        }

        Optional<WorkItemGitHubLink> linkOpt = workItemGitHubLinkRepository
                .findByProjectIdAndLinkTypeAndGithubNumber(projectId, GitHubLinkType.ISSUE, issue.getNumber());

        switch (action.toLowerCase()) {
            case "opened" -> {
                if (linkOpt.isEmpty()) {
                    WorkItem saved = createWorkItemFromIssue(project, issue, resolveInitialStatus(projectId), null, false);
                    workItemGitHubLinkRepository.saveAndFlush(buildIssueLink(projectId, saved.getId(), issue, "OPEN"));
                    log.info("Auto-created WorkItem '{}' from GitHub issue #{} in project '{}'",
                            saved.getCode(), issue.getNumber(), project.getName());
                }
                return null;
            }
            case "closed", "reopened" -> {
                if (linkOpt.isEmpty()) {
                    return null;
                }
                boolean closed = "closed".equalsIgnoreCase(action);
                WorkItemGitHubLink link = linkOpt.get();
                link.setStatus(closed ? "CLOSED" : "OPEN");
                workItemGitHubLinkRepository.save(link);
                // Cờ riêng cho chiều GitHub -> DATN (autoCloseGithubIssueOnDone là chiều ngược lại)
                if (!Boolean.TRUE.equals(repo.getAutoCloseTaskOnIssueClose())) {
                    return null;
                }
                return new FollowUp(link.getWorkItemId(), projectId, closed ? MoveTarget.DONE : MoveTarget.REOPEN,
                        "issue #" + issue.getNumber(), longOrNull(root.path("sender").path("id")));
            }
            case "edited" -> {
                if (linkOpt.isEmpty()) {
                    return null;
                }
                // Chỉ ghi đè trường thực sự đổi trên GitHub, không xoá nội dung đã sửa phía DATN
                JsonNode changes = root.path("changes");
                boolean titleChanged = changes.has("title");
                boolean bodyChanged = changes.has("body");
                WorkItemGitHubLink link = linkOpt.get();
                if (titleChanged) {
                    link.setTitle(issue.getTitle());
                    workItemGitHubLinkRepository.save(link);
                }
                if (titleChanged || bodyChanged) {
                    workItemRepository.findById(link.getWorkItemId()).ifPresent(item -> {
                        if (titleChanged) {
                            item.setSummary(issue.getTitle());
                        }
                        if (bodyChanged) {
                            item.setDescription(issue.getBody() != null ? issue.getBody() : "");
                        }
                        workItemRepository.save(item);
                    });
                }
                return null;
            }
            case "deleted" -> {
                linkOpt.ifPresent(workItemGitHubLinkRepository::delete);
                return null;
            }
            default -> {
                log.debug("Unhandled issue action: {}", action);
                return null;
            }
        }
    }

    /**
     * Chuyển Task theo sự kiện GitHub (Issue đóng / mở lại, PR mở / merge) bằng WorkflowEngine với tư cách người đã
     * thao tác trên GitHub (tra theo github user id ↔ tài khoản GitHub đã liên kết). Nhờ đi qua engine, mọi quy tắc vẫn
     * áp dụng: người không phải Admin dự án chỉ tạo yêu cầu "Tạm hoàn thành", có lịch sử, thông báo, sổ ghi công.
     * Không xác định được người thao tác (chưa liên kết DATN / không có quyền) thì chỉ cập nhật liên kết.
     */
    private void moveWorkItemByGitHub(FollowUp followUp) {
        UUID actorId = resolveActor(followUp.actorGithubId(), followUp.projectId());
        if (actorId == null) {
            log.info("GitHub {}: người thao tác (github id={}) chưa liên kết tài khoản DATN hoặc không có quyền "
                    + "chuyển trạng thái trong dự án, chỉ cập nhật liên kết", followUp.ref(), followUp.actorGithubId());
            return;
        }
        try {
            WorkItem item = workItemRepository.findById(followUp.workItemId()).orElse(null);
            if (item == null || item.getDeletedAt() != null) {
                return;
            }
            Status current = statusRepository.findById(item.getStatusId()).orElse(null);
            StatusCategory currentCategory = current != null ? current.getCategory() : null;

            List<AvailableTransitionResponse> transitions =
                    workflowEngineService.getAvailableTransitions(item.getId(), actorId);
            Optional<AvailableTransitionResponse> chosen = switch (followUp.target()) {
                case DONE -> currentCategory == StatusCategory.DONE ? Optional.empty()
                        : transitions.stream().filter(t -> t.getToStatusCategory() == StatusCategory.DONE).findFirst();
                case REOPEN -> currentCategory != StatusCategory.DONE ? Optional.empty()
                        : transitions.stream().filter(t -> t.getToStatusCategory() == StatusCategory.TODO).findFirst()
                        .or(() -> transitions.stream().filter(t -> t.getToStatusCategory() != StatusCategory.DONE).findFirst());
                case IN_REVIEW -> chooseReviewTransition(current, transitions);
            };
            if (chosen.isEmpty()) {
                log.info("WorkItem '{}': không có bước chuyển phù hợp ({}) theo GitHub {}",
                        item.getCode(), followUp.target(), followUp.ref());
                return;
            }

            WorkItem result = workflowEngineService.executeTransition(item.getId(), chosen.get().getTransitionId(),
                    ExecuteTransitionRequest.builder()
                            .transitionId(chosen.get().getTransitionId())
                            .resolution(followUp.target() == MoveTarget.DONE ? WorkItemResolution.DONE.name() : null)
                            .comment("Tự động theo GitHub " + followUp.ref())
                            .build(),
                    actorId);
            log.info("WorkItem '{}' → {} theo GitHub {} (người thao tác {})",
                    result.getCode(), chosen.get().getToStatusName(), followUp.ref(), actorId);
        } catch (RuntimeException e) {
            log.warn("Không thể chuyển trạng thái WorkItem {} theo GitHub {}: {}",
                    followUp.workItemId(), followUp.ref(), e.getMessage());
        }
    }

    /**
     * PR được mở: ưu tiên bước chuyển tới trạng thái có tên kiểu "Review"/"Chờ duyệt"; quy trình không có trạng thái
     * review thì Task đang ở To Do được chuyển sang nhóm In Progress. Không kéo Task đã Done hoặc đang review lùi lại.
     */
    private Optional<AvailableTransitionResponse> chooseReviewTransition(Status current,
                                                                         List<AvailableTransitionResponse> transitions) {
        if (current == null || current.getCategory() == StatusCategory.DONE || isReviewStatusName(current.getName())) {
            return Optional.empty();
        }
        Optional<AvailableTransitionResponse> review = transitions.stream()
                .filter(t -> t.getToStatusCategory() != StatusCategory.DONE && isReviewStatusName(t.getToStatusName()))
                .findFirst();
        if (review.isPresent() || current.getCategory() != StatusCategory.TODO) {
            return review;
        }
        return transitions.stream().filter(t -> t.getToStatusCategory() == StatusCategory.IN_PROGRESS).findFirst();
    }

    private static boolean isReviewStatusName(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return REVIEW_STATUS_KEYWORDS.stream().anyMatch(lower::contains);
    }

    private UUID resolveActor(Long senderGithubId, UUID projectId) {
        if (senderGithubId == null) {
            return null;
        }
        return userGitHubTokenRepository.findFirstByGithubUserId(senderGithubId)
                .map(UserGitHubToken::getUserId)
                .filter(userId -> projectMemberService.hasPermission(projectId, userId, PermissionKey.ISSUE_TRANSITION))
                .orElse(null);
    }

    /**
     * Gọi từ listener sau khi transaction đổi trạng thái đã COMMIT (bất đồng bộ), nên chạy transaction riêng.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncWorkItemStatusToGitHub(UUID workItemId, StatusCategory oldCategory, StatusCategory newCategory,
                                           UUID changedByUserId) {
        boolean enteringDone = newCategory == StatusCategory.DONE && oldCategory != StatusCategory.DONE;
        boolean leavingDone = oldCategory == StatusCategory.DONE && newCategory != StatusCategory.DONE;
        if (!enteringDone && !leavingDone) {
            return;
        }

        WorkItem item = workItemRepository.findById(workItemId).orElse(null);
        if (item == null) return;

        ProjectGitHubRepo repo = projectGitHubRepoRepository.findByProjectId(item.getProjectId()).orElse(null);
        if (repo == null || !Boolean.TRUE.equals(repo.getAutoCloseGithubIssueOnDone())) {
            return;
        }

        List<WorkItemGitHubLink> issueLinks = workItemGitHubLinkRepository
                .findByWorkItemIdOrderByCreatedAtDesc(workItemId)
                .stream()
                .filter(l -> l.getLinkType() == GitHubLinkType.ISSUE && l.getGithubNumber() != null)
                .filter(l -> enteringDone != "CLOSED".equalsIgnoreCase(l.getStatus()))
                .toList();
        if (issueLinks.isEmpty()) return;

        Project project = projectRepository.findById(item.getProjectId()).orElse(null);
        if (project == null) return;

        // Người kết nối repo (chắc chắn có quyền ghi) -> Lead -> người vừa đổi trạng thái. KHÔNG lấy token người lạ.
        UserGitHubToken token = findFirstToken(repo.getConnectedByUserId(), project.getLeadUserId(), changedByUserId)
                .orElse(null);
        if (token == null) {
            log.warn("Cannot sync status to GitHub: no project-related GitHub token for repo {}", repo.getRepoFullName());
            return;
        }

        String validToken = getValidAccessToken(token);
        String targetState = enteringDone ? "closed" : "open";

        for (WorkItemGitHubLink link : issueLinks) {
            boolean ok = gitHubApiClient.updateIssueState(
                    validToken, repo.getRepoOwner(), repo.getRepoName(), link.getGithubNumber(), targetState);
            if (ok) {
                link.setStatus(enteringDone ? "CLOSED" : "OPEN");
                workItemGitHubLinkRepository.save(link);
                log.info("{} GitHub issue #{} on {} because WorkItem '{}' {}",
                        enteringDone ? "Closed" : "Reopened", link.getGithubNumber(), repo.getRepoFullName(),
                        item.getCode(), enteringDone ? "is DONE" : "moved out of DONE");
            }
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private WorkItem createWorkItemFromIssue(Project project, GitHubIssueDto issue, UUID statusId, UUID reporterId,
                                             boolean resolved) {
        WorkItem workItem = WorkItem.builder()
                .projectId(project.getId())
                .typeId(resolveDefaultWorkItemTypeId(project.getId()))
                .statusId(statusId)
                .code(atomicKeySequenceGenerator.generateNextCode(project.getKey()))
                .summary(issue.getTitle())
                .description(issue.getBody() != null ? issue.getBody() : "")
                .priority(WorkItemPriority.MEDIUM)
                .reporterId(reporterId != null ? reporterId : project.getLeadUserId())
                .rank(calculateInitialRankForNewItem(project.getId()))
                .originalEstimateSeconds(0L)
                .remainingEstimateSeconds(0L)
                .timeSpentSeconds(0L)
                .version(0)
                .build();
        if (resolved) {
            workItem.setResolution(WorkItemResolution.DONE);
            workItem.setResolvedAt(LocalDateTime.now());
        }
        return workItemRepository.save(workItem);
    }

    private WorkItemGitHubLink buildIssueLink(UUID projectId, UUID workItemId, GitHubIssueDto issue, String state) {
        return WorkItemGitHubLink.builder()
                .workItemId(workItemId)
                .projectId(projectId)
                .linkType(GitHubLinkType.ISSUE)
                .githubId(issue.getId() != null ? String.valueOf(issue.getId()) : null)
                .githubNumber(issue.getNumber())
                .title(issue.getTitle())
                .url(issue.getHtmlUrl() != null ? issue.getHtmlUrl() : "")
                .status(state)
                .authorName(issue.getUser() != null ? issue.getUser().getLogin() : null)
                .authorAvatarUrl(issue.getUser() != null ? issue.getUser().getAvatarUrl() : null)
                .build();
    }

    /** Token GitHub đầu tiên tìm thấy theo thứ tự người dùng truyền vào (bỏ qua null / trùng). */
    private Optional<UserGitHubToken> findFirstToken(UUID... userIds) {
        Set<UUID> seen = new LinkedHashSet<>();
        for (UUID userId : userIds) {
            if (userId == null || !seen.add(userId)) {
                continue;
            }
            Optional<UserGitHubToken> token = userGitHubTokenRepository.findByUserId(userId);
            if (token.isPresent()) {
                return token;
            }
        }
        return Optional.empty();
    }

    private void deleteWebhookQuietly(ProjectGitHubRepo repo, UUID currentUserId) {
        if (repo.getWebhookId() == null) {
            return;
        }
        // Webhook do người kết nối tạo: ưu tiên token của họ
        Optional<UserGitHubToken> token = findFirstToken(repo.getConnectedByUserId(), currentUserId);
        if (token.isEmpty()) {
            log.warn("Không có token để gỡ webhook {} trên {}", repo.getWebhookId(), repo.getRepoFullName());
            return;
        }
        try {
            gitHubApiClient.deleteWebhook(getValidAccessToken(token.get()),
                    repo.getRepoOwner(), repo.getRepoName(), repo.getWebhookId());
        } catch (Exception e) {
            log.warn("Could not delete webhook on GitHub: {}", e.getMessage());
        }
    }

    private String newWebhookSecret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private UUID resolveDefaultWorkItemTypeId(UUID projectId) {
        List<WorkItemType> types = workItemTypeRepository.findByProjectIdOrProjectIdIsNull(projectId);
        return types.stream()
                .filter(t -> "TASK".equalsIgnoreCase(t.getCode()) || "STORY".equalsIgnoreCase(t.getCode()))
                .findFirst()
                .map(WorkItemType::getId)
                .orElseGet(() -> types.stream()
                        .filter(t -> Integer.valueOf(0).equals(t.getHierarchyLevel()))
                        .findFirst()
                        .map(WorkItemType::getId)
                        .orElseGet(() -> types.isEmpty() ? null : types.get(0).getId())
                );
    }

    private UUID resolveInitialStatus(UUID projectId) {
        List<Status> statuses = statusRepository.findByProjectId(projectId);
        return statuses.stream()
                .filter(s -> s.getCategory() == StatusCategory.TODO)
                .findFirst()
                .map(Status::getId)
                .orElseGet(() -> statuses.isEmpty() ? null : statuses.get(0).getId());
    }

    private UUID resolveDoneStatus(UUID projectId) {
        List<Status> statuses = statusRepository.findByProjectId(projectId);
        return statuses.stream()
                .filter(s -> s.getCategory() == StatusCategory.DONE)
                .findFirst()
                .map(Status::getId)
                .orElse(null);
    }

    @Override
    @Transactional
    public void unlinkUserAccount(UUID currentUserId) {
        userGitHubTokenRepository.findByUserId(currentUserId)
                .ifPresent(token -> {
                    userGitHubTokenRepository.delete(token);
                    log.info("Unlinked GitHub account @{} for user {}", token.getGithubUsername(), currentUserId);
                });
    }

    @Transactional
    public String getValidAccessToken(UserGitHubToken userToken) {
        if (userToken == null) {
            throw new BadRequestException("Chưa kết nối tài khoản GitHub");
        }
        if (userToken.getAccessTokenEncrypted() == null) {
            // Không giải mã được (khoá mã hoá đã đổi): buộc kết nối lại
            throw new BadRequestException("Không đọc được token GitHub đã lưu, vui lòng kết nối lại tài khoản GitHub");
        }

        // Tự động làm mới token nếu có refresh_token và sắp hết hạn trong vòng 5 phút
        if (userToken.getRefreshTokenEncrypted() != null && userToken.getExpiresAt() != null
                && userToken.getExpiresAt().isBefore(LocalDateTime.now().plusMinutes(5))) {
            log.info("GitHub token for user {} expires at {}, auto-refreshing...",
                    userToken.getUserId(), userToken.getExpiresAt());
            GitHubOAuthTokenResponse refreshed;
            try {
                refreshed = gitHubApiClient.refreshAccessToken(userToken.getRefreshTokenEncrypted());
            } catch (Exception e) {
                log.error("Failed to auto-refresh GitHub token for user {}: {}", userToken.getUserId(), e.getMessage());
                // Báo rõ ngay tại đây thay vì trả token đã hết hạn để lỗi 401 nổ ra ở chỗ khác
                throw new BadRequestException("Phiên đăng nhập GitHub đã hết hạn, vui lòng kết nối lại tài khoản GitHub");
            }
            userToken.setAccessTokenEncrypted(refreshed.getAccessToken());
            if (refreshed.getRefreshToken() != null) {
                userToken.setRefreshTokenEncrypted(refreshed.getRefreshToken());
            }
            if (refreshed.getExpiresIn() != null) {
                userToken.setExpiresAt(LocalDateTime.now().plusSeconds(refreshed.getExpiresIn()));
            }
            if (refreshed.getRefreshTokenExpiresIn() != null) {
                userToken.setRefreshTokenExpiresAt(LocalDateTime.now().plusSeconds(refreshed.getRefreshTokenExpiresIn()));
            }
            userGitHubTokenRepository.save(userToken);
            log.info("Successfully auto-refreshed GitHub token for user {}, new expires_at={}",
                    userToken.getUserId(), userToken.getExpiresAt());
        }

        return userToken.getAccessTokenEncrypted();
    }

    private String calculateInitialRankForNewItem(UUID projectId) {
        List<WorkItem> items = workItemRepository.findActiveByProjectIdOrderByRankAsc(projectId);
        if (items.isEmpty()) {
            return lexoRankService.calculateInitialRank();
        }
        String lastRank = items.get(items.size() - 1).getRank();
        return lexoRankService.calculateAfter(lastRank);
    }
}
