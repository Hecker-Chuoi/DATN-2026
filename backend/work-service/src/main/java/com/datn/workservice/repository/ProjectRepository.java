package com.datn.workservice.repository;

import com.datn.workservice.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {
    // List<Project> findByWorkspaceId(UUID workspaceId);
    // List<Project> findByWorkspaceIdAndIsTemplate(UUID workspaceId, Boolean isTemplate);
    List<Project> findByTeamId(UUID teamId);
    List<Project> findByIsTemplateTrue();
    Optional<Project> findByKey(String key);
}
