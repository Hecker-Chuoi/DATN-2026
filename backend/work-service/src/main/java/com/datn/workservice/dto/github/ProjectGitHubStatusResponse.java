package com.datn.workservice.dto.github;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectGitHubStatusResponse {

    private boolean userConnected;

    private String githubUsername;

    private String githubAvatarUrl;

    private boolean projectLinked;

    private UUID repoLinkId;

    private String repoFullName;

    private String repoUrl;

    private String defaultBranch;

    private Boolean webhookActive;

    private Boolean syncIssuesEnabled;

    private Boolean autoMoveInReviewOnPr;

    private Boolean autoCloseTaskOnPrMerge;

    private Boolean autoCloseGithubIssueOnDone;

    private LocalDateTime lastSyncedAt;
}
