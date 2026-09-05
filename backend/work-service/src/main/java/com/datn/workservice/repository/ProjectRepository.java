package com.datn.workservice.repository;

import com.datn.workservice.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID>, JpaSpecificationExecutor<Project> {
    Optional<Project> findByKey(String key);
    Optional<Project> findByKeyIgnoreCase(String key);
    boolean existsByKey(String key);
    boolean existsByKeyIgnoreCase(String key);
    List<Project> findByWorkspaceId(UUID workspaceId);
    Page<Project> findByWorkspaceId(UUID workspaceId, Pageable pageable);
    List<Project> findByTeamId(UUID teamId);
    Optional<Project> findByWorkspaceIdAndKey(UUID workspaceId, String key);
}
