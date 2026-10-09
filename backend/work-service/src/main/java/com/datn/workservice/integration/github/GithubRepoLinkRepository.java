package com.datn.workservice.integration.github;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GithubRepoLinkRepository extends JpaRepository<GithubRepoLink, UUID> {
    Optional<GithubRepoLink> findByProjectId(UUID projectId);
    Optional<GithubRepoLink> findByRepoId(Long repoId);
}
