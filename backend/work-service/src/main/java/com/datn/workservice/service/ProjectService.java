package com.datn.workservice.service;

import com.datn.workservice.dto.request.CloneProjectRequest;
import com.datn.workservice.dto.request.CreateProjectRequest;
import com.datn.workservice.dto.request.UpdateProjectRequest;
import com.datn.workservice.dto.response.ProjectResponse;

import java.util.List;
import java.util.UUID;

public interface ProjectService {

    ProjectResponse createProject(CreateProjectRequest request, UUID currentUserId);

    List<ProjectResponse> getAllProjects();

    List<ProjectResponse> getProjectsByTeam(UUID teamId);

    List<ProjectResponse> getTemplateProjects();

    ProjectResponse getProjectById(UUID projectId);

    ProjectResponse updateProject(UUID projectId, UpdateProjectRequest request);

    void deleteProject(UUID projectId);

    ProjectResponse cloneProject(CloneProjectRequest request, UUID currentUserId);
}
