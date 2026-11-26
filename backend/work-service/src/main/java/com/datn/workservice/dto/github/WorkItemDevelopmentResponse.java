package com.datn.workservice.dto.github;

import lombok.*;

import java.util.List;

/**
 * Khung "Phát triển" trong chi tiết Task: nhánh gợi ý / đã tạo và các đường dẫn mở PR trên GitHub.
 * URL được dựng sẵn ở server để frontend không phải lặp lại quy tắc đặt tên nhánh và từ khoá hoàn thành.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkItemDevelopmentResponse {

    /** Dự án đã liên kết repo chưa; false thì các trường còn lại để trống. */
    private boolean linked;

    private String repoFullName;

    private String repoUrl;

    /** Nhánh đích của dự án, nhánh mới được tách từ đây. */
    private String baseBranch;

    /** Nhánh đã gắn với Task, hoặc tên gợi ý {@code feat/KEY-n-slug} nếu chưa tạo. */
    private String branchName;

    private boolean branchCreated;

    private String branchUrl;

    /** Tiêu đề PR chỉ nhắc mã: mở PR thì Task sang Review, merge không hoàn thành. */
    private String prTitle;

    /** Tiêu đề PR có từ khoá hoàn thành: merge vào nhánh đích thì Task hoàn thành. */
    private String closePrTitle;

    private String compareUrl;

    private String closeCompareUrl;

    private String checkoutCommand;

    /** Issue / PR đã gắn với Task (mới nhất trước). */
    private List<WorkItemGitHubLinkDto> links;

    /** Task đã có GitHub Issue liên kết chưa (có thì ẩn nút "Tạo Issue"). */
    private boolean issueLinked;
}
