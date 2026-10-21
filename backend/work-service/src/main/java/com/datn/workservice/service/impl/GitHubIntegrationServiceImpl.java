package com.datn.workservice.service.impl;

import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workservice.client.GitHubApiClient;
import com.datn.workservice.dto.github.*;
import com.datn.workservice.entity.Project;
import com.datn.workservice.entity.ProjectGitHubRepo;
import com.datn.workservice.entity.UserGitHubToken;
import com.datn.workservice.repository.ProjectGitHubRepoRepository;
import com.datn.workservice.repository.ProjectRepository;
import com.datn.workservice.repository.UserGitHubTokenRepository;
import com.datn.workservice.service.GitHubIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        userToken.setAccessTokenEncrypted(accessToken); // Trong thực tế có thể mã hóa qua AES
        userGitHubTokenRepository.save(userToken);

        log.info("Linked GitHub account @{} for user {}", userProfile.getLogin(), currentUserId);

        return getProjectGitHubStatus(projectId, currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GitHubRepoDto> getAvailableRepositories(UUID currentUserId) {
        UserGitHubToken userToken = userGitHubTokenRepository.findByUserId(currentUserId)
                .orElseThrow(() -> new BadRequestException("Bạn chưa kết nối tài khoản GitHub"));

        return gitHubApiClient.getUserRepositories(userToken.getAccessTokenEncrypted());
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
                    userToken.getAccessTokenEncrypted(), owner, repo, webhookPayloadUrl, webhookSecret
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
                        userTokenOpt.get().getAccessTokenEncrypted(),
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
}
