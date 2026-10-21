package com.datn.workservice.repository;

import com.datn.workservice.entity.ProjectGitHubRepo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectGitHubRepoRepository extends JpaRepository<ProjectGitHubRepo, UUID> {

    Optional<ProjectGitHubRepo> findByProjectId(UUID projectId);

    Optional<ProjectGitHubRepo> findByRepoFullName(String repoFullName);

    boolean existsByProjectId(UUID projectId);

    void deleteByProjectId(UUID projectId);
}
