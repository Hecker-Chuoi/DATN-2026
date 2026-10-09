package com.datn.workservice.integration.github;

import com.datn.workservice.integration.github.GithubDtos.ConnectionStatus;
import com.datn.workservice.integration.github.GithubDtos.LinkRepoRequest;
import com.datn.workservice.integration.github.GithubDtos.RepoOption;

import java.util.List;
import java.util.UUID;

public interface GithubService {

    // ─── OAuth / tài khoản cá nhân ──────────────────────────────────────────
    String buildAuthorizeUrl(UUID userId, String redirectUri);

    ConnectionStatus completeOAuth(UUID userId, String code, String state, String redirectUri);

    ConnectionStatus myConnection(UUID userId);

    void disconnect(UUID userId);

    // ─── Repo của dự án ──────────────────────────────────────────────────────
    List<RepoOption> listMyRepos(UUID userId);

    GithubRepoLink getRepoLink(UUID projectId);

    GithubRepoLink linkRepo(UUID projectId, LinkRepoRequest request, UUID userId);

    void unlinkRepo(UUID projectId, UUID userId);

    // ─── Webhook (public, xác thực bằng HMAC) ────────────────────────────────
    void handleWebhook(String event, String signature, byte[] body);
}
