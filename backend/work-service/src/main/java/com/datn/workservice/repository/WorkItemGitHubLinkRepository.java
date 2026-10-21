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

    void deleteByWorkItemId(UUID workItemId);
}
