package com.datn.workservice.repository;

import com.datn.workservice.entity.WorkItemGitHubLink;
import com.datn.workservice.enums.GitHubLinkType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkItemGitHubLinkRepository extends JpaRepository<WorkItemGitHubLink, UUID> {

    List<WorkItemGitHubLink> findByWorkItemIdOrderByCreatedAtDesc(UUID workItemId);

    Optional<WorkItemGitHubLink> findByWorkItemIdAndLinkTypeAndGithubNumber(UUID workItemId, GitHubLinkType linkType, Integer githubNumber);

    Optional<WorkItemGitHubLink> findByWorkItemIdAndLinkTypeAndGithubId(UUID workItemId, GitHubLinkType linkType, String githubId);

    List<WorkItemGitHubLink> findByLinkTypeAndGithubNumber(GitHubLinkType linkType, Integer githubNumber);

    @org.springframework.data.jpa.repository.Query("SELECT l FROM WorkItemGitHubLink l JOIN WorkItem w ON l.workItemId = w.id WHERE w.projectId = :projectId AND l.linkType = :linkType AND l.githubNumber = :githubNumber")
    Optional<WorkItemGitHubLink> findByProjectIdAndLinkTypeAndGithubNumber(
            @org.springframework.data.repository.query.Param("projectId") UUID projectId,
            @org.springframework.data.repository.query.Param("linkType") GitHubLinkType linkType,
            @org.springframework.data.repository.query.Param("githubNumber") Integer githubNumber
    );

    void deleteByWorkItemId(UUID workItemId);
}
