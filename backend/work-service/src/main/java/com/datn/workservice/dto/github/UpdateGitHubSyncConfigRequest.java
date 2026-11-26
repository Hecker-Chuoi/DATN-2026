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

    /** Từ khoá hoàn thành, cách nhau dấu phẩy. Chuỗi rỗng = quay về bộ mặc định; null = không đổi. */
    private String prCloseKeywords;

    /** Nhánh đích: chỉ PR mở/merge vào nhánh này mới tự chuyển trạng thái Task. */
    private String defaultBranch;
}
