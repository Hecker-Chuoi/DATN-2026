package com.datn.workservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "project_github_repos", uniqueConstraints = {
        @UniqueConstraint(name = "uq_project_repo", columnNames = {"project_id", "repo_full_name"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectGitHubRepo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "repo_owner", nullable = false, length = 255)
    private String repoOwner;

    @Column(name = "repo_name", nullable = false, length = 255)
    private String repoName;

    @Column(name = "repo_full_name", nullable = false, length = 512)
    private String repoFullName;

    @Column(name = "repo_url", nullable = false, length = 512)
    private String repoUrl;

    @Column(name = "default_branch", length = 100)
    @Builder.Default
    private String defaultBranch = "main";

    @Column(name = "webhook_id")
    private Long webhookId;

    @Column(name = "webhook_secret", length = 255)
    private String webhookSecret;

    @Column(name = "sync_issues_enabled")
    @Builder.Default
    private Boolean syncIssuesEnabled = true;

    @Column(name = "auto_move_in_review_on_pr")
    @Builder.Default
    private Boolean autoMoveInReviewOnPr = true;

    @Column(name = "auto_close_task_on_pr_merge")
    @Builder.Default
    private Boolean autoCloseTaskOnPrMerge = true;

    @Column(name = "auto_close_github_issue_on_done")
    @Builder.Default
    private Boolean autoCloseGithubIssueOnDone = true;

    /** Chiều GitHub -> DATN: Issue đóng/mở lại thì chuyển Task qua workflow (vẫn qua cổng "Tạm hoàn thành"). */
    @Column(name = "auto_close_task_on_issue_close")
    @Builder.Default
    private Boolean autoCloseTaskOnIssueClose = true;

    /** Keycloak sub của người đã kết nối repo; token GitHub của họ dùng cho các tác vụ nền. */
    @Column(name = "connected_by_user_id")
    private UUID connectedByUserId;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
