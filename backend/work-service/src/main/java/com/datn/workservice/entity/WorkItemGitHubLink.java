package com.datn.workservice.entity;

import com.datn.workservice.enums.GitHubLinkType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "work_item_github_links")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkItemGitHubLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "work_item_id", nullable = false)
    private UUID workItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false, length = 50)
    private GitHubLinkType linkType;

    @Column(name = "github_id", length = 255)
    private String githubId;

    @Column(name = "github_number")
    private Integer githubNumber;

    @Column(length = 512)
    private String title;

    @Column(nullable = false, length = 512)
    private String url;

    @Column(length = 50)
    private String status;

    @Column(name = "author_name", length = 255)
    private String authorName;

    @Column(name = "author_avatar_url", length = 512)
    private String authorAvatarUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
