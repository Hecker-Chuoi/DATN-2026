package com.datn.workservice.integration.github;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** Repo GitHub liên kết với một dự án phần mềm (1 repo / 1 dự án). */
@Entity
@Table(name = "github_repo_links")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GithubRepoLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false, unique = true)
    private UUID projectId;

    @Column(name = "repo_id", nullable = false, unique = true)
    private Long repoId;

    @Column(name = "repo_owner", nullable = false, length = 100)
    private String repoOwner;

    @Column(name = "repo_name", nullable = false, length = 100)
    private String repoName;

    @Column(name = "html_url", nullable = false)
    private String htmlUrl;

    @Column(name = "default_branch", nullable = false)
    @Builder.Default
    private String defaultBranch = "main";

    @Column(name = "webhook_id")
    private Long webhookId;

    /** Keycloak sub của người liên kết; dùng làm actor khi auto-transition. */
    @Column(name = "linked_by", nullable = false)
    private UUID linkedBy;

    @Column(name = "auto_transition_on_pr_open", nullable = false)
    @Builder.Default
    private boolean autoTransitionOnPrOpen = true;

    @Column(name = "auto_transition_on_pr_merge", nullable = false)
    @Builder.Default
    private boolean autoTransitionOnPrMerge = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    public String fullName() {
        return repoOwner + "/" + repoName;
    }
}
