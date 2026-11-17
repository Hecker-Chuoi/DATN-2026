package com.datn.workservice.dto.github;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateGitHubSyncConfigRequest {

    private Boolean syncIssuesEnabled;

    private Boolean autoMoveInReviewOnPr;

    private Boolean autoCloseTaskOnPrMerge;

    private Boolean autoCloseGithubIssueOnDone;

    private Boolean autoCloseTaskOnIssueClose;
}
