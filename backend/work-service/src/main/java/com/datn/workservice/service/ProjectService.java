package com.datn.workservice.service;

import com.datn.common.dto.PageResponse;
import com.datn.workservice.dto.request.CloneProjectRequest;
import com.datn.workservice.dto.request.CreateProjectRequest;
import com.datn.workservice.dto.request.ProjectFilter;
import com.datn.workservice.dto.request.UpdateProjectRequest;
import com.datn.workservice.dto.response.ProjectResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ProjectService {

    ProjectResponse createProject(CreateProjectRequest request, UUID currentUserId);

    PageResponse<ProjectResponse> getAllProjects(ProjectFilter filter, Pageable pageable);

    List<ProjectResponse> getProjectsByTeam(UUID teamId);

    List<ProjectResponse> getTemplateProjects();

    ProjectResponse getProjectById(UUID projectId);

    PageResponse<ProjectResponse> getMyProjects(UUID userId, ProjectFilter filter, Pageable pageable);

    ProjectResponse updateProject(UUID projectId, UpdateProjectRequest request);

    void deleteProject(UUID projectId);

    ProjectResponse cloneProject(CloneProjectRequest request, UUID currentUserId);
}

