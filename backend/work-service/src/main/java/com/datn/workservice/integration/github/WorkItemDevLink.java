package com.datn.workservice.integration.github;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Branch / commit / PR trên GitHub gắn với một work item (ghi từ webhook, đợt sau). */
@Entity
@Table(name = "work_item_dev_links", uniqueConstraints = {
        @UniqueConstraint(name = "uq_dev_link", columnNames = {"work_item_id", "kind", "external_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkItemDevLink {

    public enum Kind { BRANCH, COMMIT, PULL_REQUEST }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "work_item_id", nullable = false)
    private UUID workItemId;

    @Column(name = "repo_link_id", nullable = false)
    private UUID repoLinkId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    private String title;

    @Column(nullable = false)
    private String url;

    @Column(length = 20)
    private String state;

    @Column(name = "author_login", length = 100)
    private String authorLogin;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
