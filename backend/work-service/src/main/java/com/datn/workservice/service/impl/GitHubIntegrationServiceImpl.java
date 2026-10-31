package com.datn.workservice.service.impl;

import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workservice.client.GitHubApiClient;
import com.datn.workservice.dto.github.*;
import com.datn.workservice.entity.*;
import com.datn.workservice.enums.GitHubLinkType;
import com.datn.workservice.enums.StatusCategory;
import com.datn.workservice.enums.WorkItemPriority;
import com.datn.workservice.enums.WorkItemResolution;
import com.datn.workservice.repository.*;
import com.datn.workservice.security.GitHubWebhookSignatureValidator;
import com.datn.workservice.service.AtomicKeySequenceGenerator;
import com.datn.workservice.service.GitHubIntegrationService;
import com.datn.workservice.service.LexoRankService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    private final ObjectMapper objectMapper;

    @Value("${github.webhook-secret:datn-github-webhook-secret-2026}")
    private String webhookSecret;

    @Value("${github.webhook-base-url:}")
    private String webhookBaseUrl;

    @Override
    public String getOAuthAuthorizeUrl(UUID projectId, UUID currentUserId) {
        // State format: projectId:userId để callback nhận diện đúng ngữ cảnh
        String state = projectId + ":" + currentUserId;
        return gitHubApiClient.buildAuthorizeUrl(state);
    }

    @Override
    @Transactional
    public ProjectGitHubStatusResponse handleOAuthCallback(UUID projectId, String code, UUID currentUserId) {
        if (code == null || code.isBlank()) {
            throw new BadRequestException("Mã xác thực Authorization Code từ GitHub không hợp lệ");
        }

        // 1. Đổi code lấy Access Token (có cơ chế idempotent nếu code bị submit trùng lặp)
        GitHubOAuthTokenResponse tokenResponse;
        try {
            tokenResponse = gitHubApiClient.exchangeCodeForToken(code);
        } catch (BadRequestException e) {
            Optional<UserGitHubToken> existingToken = userGitHubTokenRepository.findByUserId(currentUserId);
            if (existingToken.isPresent()) {
                log.warn("OAuth exchange failed, but user {} already has an active token. Returning current status.", currentUserId);
                return getProjectGitHubStatus(projectId, currentUserId);
            }
            throw e;
        }
        String accessToken = tokenResponse.getAccessToken();

        // 2. Lấy thông tin user GitHub
        GitHubUserResponse userProfile = gitHubApiClient.getUserProfile(accessToken);

        // 3. Lưu hoặc cập nhật Token của người dùng
        UserGitHubToken userToken = userGitHubTokenRepository.findByUserId(currentUserId)
                .orElse(UserGitHubToken.builder().userId(currentUserId).build());

        userToken.setGithubUserId(userProfile.getId());
        userToken.setGithubUsername(userProfile.getLogin());
        userToken.setGithubAvatarUrl(userProfile.getAvatarUrl());
        userToken.setAccessTokenEncrypted(accessToken); // Trong thực tế có thể mã hóa qua AES
        if (tokenResponse.getRefreshToken() != null) {
            userToken.setRefreshTokenEncrypted(tokenResponse.getRefreshToken());
        }
        if (tokenResponse.getExpiresIn() != null) {
            userToken.setExpiresAt(LocalDateTime.now().plusSeconds(tokenResponse.getExpiresIn()));
        }
        if (tokenResponse.getRefreshTokenExpiresIn() != null) {
            userToken.setRefreshTokenExpiresAt(LocalDateTime.now().plusSeconds(tokenResponse.getRefreshTokenExpiresIn()));
        }
        userGitHubTokenRepository.save(userToken);

        log.info("Linked GitHub account @{} for user {}", userProfile.getLogin(), currentUserId);

        return getProjectGitHubStatus(projectId, currentUserId);
    }

    @Override
    @Transactional
    public List<GitHubRepoDto> getAvailableRepositories(UUID currentUserId) {
        UserGitHubToken userToken = userGitHubTokenRepository.findByUserId(currentUserId)
                .orElseThrow(() -> new BadRequestException("Bạn chưa kết nối tài khoản GitHub"));

        String validAccessToken = getValidAccessToken(userToken);
        return gitHubApiClient.getUserRepositories(validAccessToken);
    }

    @Override
    @Transactional
    public ProjectGitHubStatusResponse connectRepository(UUID projectId, ConnectRepoRequest request, UUID currentUserId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dự án với ID: " + projectId));

        UserGitHubToken userToken = userGitHubTokenRepository.findByUserId(currentUserId)
                .orElseThrow(() -> new BadRequestException("Vui lòng kết nối tài khoản GitHub trước khi liên kết kho"));

        String fullName = request.getRepoFullName();
        String[] parts = fullName.split("/");
        if (parts.length != 2) {
            throw new BadRequestException("Tên repository không hợp lệ. Định dạng chuẩn phải là: owner/repo");
        }
        String owner = parts[0].trim();
        String repo = parts[1].trim();

        // Đăng ký Webhook tự động nếu có cấu hình public webhook url
        Long webhookId = null;
        if (webhookBaseUrl != null && !webhookBaseUrl.isBlank()) {
            String webhookPayloadUrl = webhookBaseUrl + "/api/v1/work/integrations/github/webhook";
            webhookId = gitHubApiClient.createWebhook(
                    getValidAccessToken(userToken), owner, repo, webhookPayloadUrl, webhookSecret
            );
        }

        ProjectGitHubRepo projectRepo = projectGitHubRepoRepository.findByProjectId(projectId)
                .orElse(ProjectGitHubRepo.builder().projectId(projectId).build());

        projectRepo.setRepoOwner(owner);
        projectRepo.setRepoName(repo);
        projectRepo.setRepoFullName(fullName);
        projectRepo.setRepoUrl(request.getRepoUrl());
        projectRepo.setDefaultBranch(request.getDefaultBranch() != null ? request.getDefaultBranch() : "main");
        projectRepo.setWebhookId(webhookId);
        projectRepo.setWebhookSecret(webhookSecret);
        if (request.getSyncIssues() != null) {
            projectRepo.setSyncIssuesEnabled(request.getSyncIssues());
        }

        projectGitHubRepoRepository.save(projectRepo);
        log.info("Successfully connected repo {} to project {}", fullName, project.getName());

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
        // Cố gắng xóa Webhook trên GitHub
        Optional<UserGitHubToken> userTokenOpt = userGitHubTokenRepository.findByUserId(currentUserId);
        if (userTokenOpt.isPresent() && repo.getWebhookId() != null) {
            try {
                gitHubApiClient.deleteWebhook(
                        getValidAccessToken(userTokenOpt.get()),
                        repo.getRepoOwner(),
                        repo.getRepoName(),
                        repo.getWebhookId()
                );
            } catch (Exception e) {
                log.warn("Could not delete webhook on GitHub: {}", e.getMessage());
            }
        }

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
                    .webhookActive(repo.getWebhookId() != null)
                    .syncIssuesEnabled(repo.getSyncIssuesEnabled())
                    .autoMoveInReviewOnPr(repo.getAutoMoveInReviewOnPr())
                    .autoCloseTaskOnPrMerge(repo.getAutoCloseTaskOnPrMerge())
                    .autoCloseGithubIssueOnDone(repo.getAutoCloseGithubIssueOnDone())
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

        UserGitHubToken userToken = userGitHubTokenRepository.findByUserId(currentUserId)
                .or(() -> userGitHubTokenRepository.findByUserId(project.getLeadUserId()))
                .or(userGitHubTokenRepository::findTopByOrderByCreatedAtDesc)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy Access Token GitHub để thực hiện đồng bộ"));

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
            boolean isClosed = "CLOSED".equalsIgnoreCase(issueState);

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
                String code = atomicKeySequenceGenerator.generateNextCode(project.getKey());
                String rank = calculateInitialRankForNewItem(projectId);
                UUID statusId = (isClosed && doneStatusId != null) ? doneStatusId : initialStatusId;

                WorkItem workItem = WorkItem.builder()
                        .projectId(projectId)
                        .typeId(defaultTypeId)
                        .statusId(statusId)
                        .code(code)
                        .summary(issue.getTitle())
                        .description(issue.getBody() != null ? issue.getBody() : "")
                        .priority(WorkItemPriority.MEDIUM)
                        .reporterId(currentUserId != null ? currentUserId : project.getLeadUserId())
                        .rank(rank)
                        .originalEstimateSeconds(0L)
                        .remainingEstimateSeconds(0L)
                        .timeSpentSeconds(0L)
                        .version(0)
                        .build();

                if (isClosed) {
                    workItem.setResolution(WorkItemResolution.DONE);
                    workItem.setResolvedAt(LocalDateTime.now());
                }

                WorkItem savedItem = workItemRepository.save(workItem);

                WorkItemGitHubLink newLink = WorkItemGitHubLink.builder()
                        .workItemId(savedItem.getId())
                        .linkType(GitHubLinkType.ISSUE)
                        .githubId(issue.getId() != null ? String.valueOf(issue.getId()) : null)
                        .githubNumber(issue.getNumber())
                        .title(issue.getTitle())
                        .url(issue.getHtmlUrl() != null ? issue.getHtmlUrl() : "")
                        .status(issueState)
                        .authorName(issue.getUser() != null ? issue.getUser().getLogin() : null)
                        .authorAvatarUrl(issue.getUser() != null ? issue.getUser().getAvatarUrl() : null)
                        .build();

                workItemGitHubLinkRepository.save(newLink);
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

    @Override
    @Transactional
    public void handleWebhook(String event, String signature, String rawPayload) {
        log.info("Handling GitHub webhook event: {}", event);

        JsonNode root;
        try {
            root = objectMapper.readTree(rawPayload);
        } catch (Exception e) {
            log.error("Failed to parse GitHub webhook payload JSON: {}", e.getMessage());
            throw new BadRequestException("Payload JSON của Webhook không hợp lệ");
        }

        // Xử lý sự kiện ping
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

        Optional<ProjectGitHubRepo> repoOpt = projectGitHubRepoRepository.findByRepoFullName(repoFullName);
        if (repoOpt.isEmpty()) {
            log.info("No project linked to GitHub repository '{}', ignoring webhook", repoFullName);
            return;
        }

        ProjectGitHubRepo repo = repoOpt.get();

        // Xác thực HMAC SHA-256 Signature
        String secret = repo.getWebhookSecret() != null ? repo.getWebhookSecret() : webhookSecret;
        if (!gitHubWebhookSignatureValidator.isValidSignature(rawPayload, signature, secret)) {
            log.warn("Invalid HMAC signature for GitHub webhook on repository '{}'", repoFullName);
            throw new BadRequestException("Chữ ký Webhook GitHub không hợp lệ");
        }

        // Xử lý sự kiện issues
        if ("issues".equalsIgnoreCase(event)) {
            handleIssueEvent(repo, root);
        } else {
            log.info("Unhandled GitHub webhook event type: {}", event);
        }
    }

    private void handleIssueEvent(ProjectGitHubRepo repo, JsonNode root) {
        if (!Boolean.TRUE.equals(repo.getSyncIssuesEnabled())) {
            log.info("Issue synchronization is disabled for repository '{}', skipping event", repo.getRepoFullName());
            return;
        }

        String action = root.path("action").asText();
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

        if (issue.isPullRequest()) {
            // Bỏ qua pull requests trong luồng issues
            return;
        }

        UUID projectId = repo.getProjectId();
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            return;
        }

        Optional<WorkItemGitHubLink> linkOpt = workItemGitHubLinkRepository
                .findByProjectIdAndLinkTypeAndGithubNumber(projectId, GitHubLinkType.ISSUE, issue.getNumber());

        switch (action.toLowerCase()) {
            case "opened" -> {
                if (linkOpt.isEmpty()) {
                    UUID defaultTypeId = resolveDefaultWorkItemTypeId(projectId);
                    UUID initialStatusId = resolveInitialStatus(projectId);
                    String code = atomicKeySequenceGenerator.generateNextCode(project.getKey());
                    String rank = calculateInitialRankForNewItem(projectId);

                    WorkItem workItem = WorkItem.builder()
                            .projectId(projectId)
                            .typeId(defaultTypeId)
                            .statusId(initialStatusId)
                            .code(code)
                            .summary(issue.getTitle())
                            .description(issue.getBody() != null ? issue.getBody() : "")
                            .priority(WorkItemPriority.MEDIUM)
                            .reporterId(project.getLeadUserId())
                            .rank(rank)
                            .originalEstimateSeconds(0L)
                            .remainingEstimateSeconds(0L)
                            .timeSpentSeconds(0L)
                            .version(0)
                            .build();

                    WorkItem saved = workItemRepository.save(workItem);

                    WorkItemGitHubLink link = WorkItemGitHubLink.builder()
                            .workItemId(saved.getId())
                            .linkType(GitHubLinkType.ISSUE)
                            .githubId(issue.getId() != null ? String.valueOf(issue.getId()) : null)
                            .githubNumber(issue.getNumber())
                            .title(issue.getTitle())
                            .url(issue.getHtmlUrl() != null ? issue.getHtmlUrl() : "")
                            .status("OPEN")
                            .authorName(issue.getUser() != null ? issue.getUser().getLogin() : null)
                            .authorAvatarUrl(issue.getUser() != null ? issue.getUser().getAvatarUrl() : null)
                            .build();

                    workItemGitHubLinkRepository.save(link);
                    log.info("Auto-created WorkItem '{}' from GitHub issue #{} in project '{}'",
                            code, issue.getNumber(), project.getName());
                }
            }
            case "closed" -> {
                if (linkOpt.isPresent()) {
                    WorkItemGitHubLink link = linkOpt.get();
                    link.setStatus("CLOSED");
                    workItemGitHubLinkRepository.save(link);

                    if (Boolean.TRUE.equals(repo.getAutoCloseGithubIssueOnDone())) {
                        WorkItem item = workItemRepository.findById(link.getWorkItemId()).orElse(null);
                        if (item != null) {
                            UUID doneStatusId = resolveDoneStatus(projectId);
                            if (doneStatusId != null && !doneStatusId.equals(item.getStatusId())) {
                                item.setStatusId(doneStatusId);
                                item.setResolution(WorkItemResolution.DONE);
                                item.setResolvedAt(LocalDateTime.now());
                                workItemRepository.save(item);
                                log.info("Auto-moved WorkItem '{}' to DONE because GitHub issue #{} was closed",
                                        item.getCode(), issue.getNumber());
                            }
                        }
                    }
                }
            }
            case "reopened" -> {
                if (linkOpt.isPresent()) {
                    WorkItemGitHubLink link = linkOpt.get();
                    link.setStatus("OPEN");
                    workItemGitHubLinkRepository.save(link);

                    WorkItem item = workItemRepository.findById(link.getWorkItemId()).orElse(null);
                    if (item != null) {
                        UUID doneStatusId = resolveDoneStatus(projectId);
                        if (doneStatusId != null && doneStatusId.equals(item.getStatusId())) {
                            UUID initialStatusId = resolveInitialStatus(projectId);
                            if (initialStatusId != null) {
                                item.setStatusId(initialStatusId);
                                item.setResolution(null);
                                item.setResolvedAt(null);
                                workItemRepository.save(item);
                                log.info("Auto-reopened WorkItem '{}' because GitHub issue #{} was reopened",
                                        item.getCode(), issue.getNumber());
                            }
                        }
                    }
                }
            }
            case "edited" -> {
                if (linkOpt.isPresent()) {
                    WorkItemGitHubLink link = linkOpt.get();
                    link.setTitle(issue.getTitle());
                    workItemGitHubLinkRepository.save(link);

                    WorkItem item = workItemRepository.findById(link.getWorkItemId()).orElse(null);
                    if (item != null) {
                        item.setSummary(issue.getTitle());
                        if (issue.getBody() != null) {
                            item.setDescription(issue.getBody());
                        }
                        workItemRepository.save(item);
                    }
                }
            }
            case "deleted" -> linkOpt.ifPresent(workItemGitHubLinkRepository::delete);
            default -> log.debug("Unhandled issue action: {}", action);
        }
    }

    @Override
    @Transactional
    public void syncWorkItemStatusToGitHub(UUID workItemId, StatusCategory oldCategory, StatusCategory newCategory) {
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
                .toList();

        if (issueLinks.isEmpty()) return;

        Project project = projectRepository.findById(item.getProjectId()).orElse(null);
        if (project == null) return;

        UserGitHubToken token = userGitHubTokenRepository.findByUserId(project.getLeadUserId())
                .or(userGitHubTokenRepository::findTopByOrderByCreatedAtDesc)
                .orElse(null);

        if (token == null) {
            log.warn("Cannot sync status to GitHub: no user token available for repo {}", repo.getRepoFullName());
            return;
        }

        String validToken = getValidAccessToken(token);

        if (newCategory == StatusCategory.DONE) {
            for (WorkItemGitHubLink link : issueLinks) {
                if (!"CLOSED".equalsIgnoreCase(link.getStatus())) {
                    gitHubApiClient.updateIssueState(
                            validToken,
                            repo.getRepoOwner(),
                            repo.getRepoName(),
                            link.getGithubNumber(),
                            "closed"
                    );
                    link.setStatus("CLOSED");
                    workItemGitHubLinkRepository.save(link);
                    log.info("Closed GitHub issue #{} on {}/{} because WorkItem '{}' is DONE",
                            link.getGithubNumber(), repo.getRepoOwner(), repo.getRepoName(), item.getCode());
                }
            }
        } else if (oldCategory == StatusCategory.DONE && newCategory != StatusCategory.DONE) {
            for (WorkItemGitHubLink link : issueLinks) {
                if ("CLOSED".equalsIgnoreCase(link.getStatus())) {
                    gitHubApiClient.updateIssueState(
                            validToken,
                            repo.getRepoOwner(),
                            repo.getRepoName(),
                            link.getGithubNumber(),
                            "open"
                    );
                    link.setStatus("OPEN");
                    workItemGitHubLinkRepository.save(link);
                    log.info("Reopened GitHub issue #{} on {}/{} because WorkItem '{}' moved out of DONE",
                            link.getGithubNumber(), repo.getRepoOwner(), repo.getRepoName(), item.getCode());
                }
            }
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

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

        // Tự động làm mới token nếu có refresh_token và sắp hết hạn trong vòng 5 phút
        if (userToken.getRefreshTokenEncrypted() != null && userToken.getExpiresAt() != null) {
            if (userToken.getExpiresAt().isBefore(LocalDateTime.now().plusMinutes(5))) {
                log.info("GitHub token for user {} expires at {}, auto-refreshing...",
                        userToken.getUserId(), userToken.getExpiresAt());
                try {
                    GitHubOAuthTokenResponse refreshed = gitHubApiClient.refreshAccessToken(userToken.getRefreshTokenEncrypted());
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
                } catch (Exception e) {
                    log.error("Failed to auto-refresh GitHub token for user {}: {}", userToken.getUserId(), e.getMessage());
                }
            }
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
