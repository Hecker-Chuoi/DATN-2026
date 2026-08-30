package com.datn.workservice.service;

import com.datn.common.dto.PageResponse;
import com.datn.workservice.dto.request.CreateProjectRequest;
import com.datn.workservice.dto.request.UpdateProjectRequest;
import com.datn.workservice.dto.response.ProjectDetailResponse;
import com.datn.workservice.dto.response.ProjectResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ProjectService {

    /**
     * Creates a new project with auto-seeding of ecosystem (Sequence, Types, Workflow, Board, Roles).
     */
    ProjectResponse createProject(CreateProjectRequest request, UUID currentUserId);

    /**
     * Retrieves full project details by ID with lead user info, types, and roles.
     */
    ProjectDetailResponse getProjectById(UUID projectId);

    /**
     * Retrieves full project details by unique Project Key.
     */
    ProjectDetailResponse getProjectByKey(String key);

    /**
     * Lists and filters projects within a workspace or team with pagination and search.
     */
    PageResponse<ProjectResponse> getAllProjects(
            UUID workspaceId,
            UUID teamId,
            String search,
            Boolean isTemplate,
            Pageable pageable
    );

    /**
     * Lists template projects.
     */
    PageResponse<ProjectResponse> getTemplates(UUID workspaceId, Pageable pageable);

    /**
     * Updates an existing project.
     */
    ProjectResponse updateProject(UUID projectId, UpdateProjectRequest request, UUID currentUserId);

    /**
     * Soft-deletes a project by setting deletedAt.
     */
    void deleteProject(UUID projectId, UUID currentUserId);

    /**
     * Restores a soft-deleted project.
     */
    ProjectResponse restoreProject(UUID projectId, UUID currentUserId);
}
