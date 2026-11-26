package com.datn.workservice.dto.github;

import lombok.*;

import java.util.List;

/**
 * Bảng "Phát triển" trong chi tiết Task, dành cho người quản lý: tiến độ PR / Issue và quy ước gửi cho dev.
 * Tên nhánh và từ khoá được dựng ở server để frontend không lặp lại quy tắc đặt tên.
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

    /** Nhánh đích của dự án: PR merge vào đây mới làm Task đổi trạng thái. */
    private String baseBranch;

    /** Tên nhánh gợi ý cho dev: {@code feat/KEY-n-slug}. */
    private String branchName;

    /** Cách ghi trong tiêu đề PR để merge là Task hoàn thành, VD {@code FINAL FRONE-10}. */
    private String closeExample;

    /** Người được giao của Task (null = chưa giao). */
    private java.util.UUID assigneeId;

    private String assigneeName;

    /** Tài khoản GitHub của người được giao; null = người đó chưa liên kết GitHub. */
    private String assigneeGithubLogin;

    /** Issue / PR đã gắn với Task (mới nhất trước). */
    private List<WorkItemGitHubLinkDto> links;

    /** Task đã có GitHub Issue liên kết chưa (có thì ẩn nút "Tạo Issue"). */
    private boolean issueLinked;
}
