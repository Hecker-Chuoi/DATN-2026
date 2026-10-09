package com.datn.workservice.integration.github;

import jakarta.validation.constraints.NotBlank;

/** DTO nhỏ của tích hợp GitHub, gom một file cho gọn. */
public final class GithubDtos {

    private GithubDtos() {}

    public record ConnectionStatus(boolean connected, String login, String avatarUrl, boolean invalid) {
        static ConnectionStatus none() { return new ConnectionStatus(false, null, null, false); }
        static ConnectionStatus of(GithubConnection c) {
            return new ConnectionStatus(true, c.getGithubLogin(), c.getAvatarUrl(), c.isInvalid());
        }
    }

    public record OAuthCallbackRequest(@NotBlank String code, @NotBlank String state, @NotBlank String redirectUri) {}

    public record RepoOption(long id, String fullName, String owner, String name, boolean isPrivate, String defaultBranch) {}

    public record LinkRepoRequest(@NotBlank String owner, @NotBlank String name,
                                  Boolean autoOnPrOpen, Boolean autoOnPrMerge) {}
}
