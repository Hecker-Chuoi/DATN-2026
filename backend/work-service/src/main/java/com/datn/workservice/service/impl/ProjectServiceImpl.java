package com.datn.workservice.service.impl;

import com.datn.common.dto.PageResponse;
import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workservice.client.IdentityClient;
import com.datn.workservice.client.dto.UserResponse;
import com.datn.workservice.dto.request.CreateProjectRequest;
import com.datn.workservice.dto.request.UpdateProjectRequest;
import com.datn.workservice.dto.response.ProjectDetailResponse;
import com.datn.workservice.dto.response.ProjectResponse;
import com.datn.workservice.dto.response.ProjectRoleResponse;
import com.datn.workservice.dto.response.WorkItemTypeResponse;
import com.datn.workservice.entity.*;
import com.datn.workservice.enums.BoardType;
import com.datn.workservice.enums.DefaultAssigneeType;
import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.enums.ProjectType;
import com.datn.workservice.enums.StatusCategory;
import com.datn.workservice.repository.*;
import com.datn.workservice.service.AtomicKeySequenceGenerator;
import com.datn.workservice.service.ProjectService;
import com.datn.workservice.specification.ProjectSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final TeamRepository teamRepository;
    private final WorkItemTypeRepository workItemTypeRepository;
    private final StatusRepository statusRepository;
    private final WorkflowRepository workflowRepository;
    private final WorkflowTransitionRepository workflowTransitionRepository;
    private final WorkflowSchemeRepository workflowSchemeRepository;
    private final WorkflowSchemeMappingRepository workflowSchemeMappingRepository;
    private final BoardRepository boardRepository;
    private final BoardColumnRepository boardColumnRepository;
    private final BoardColumnStatusRepository boardColumnStatusRepository;
    private final ProjectRoleRepository projectRoleRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final PermissionSchemeEntryRepository permissionSchemeEntryRepository;
    private final WorkItemRepository workItemRepository;

    private final AtomicKeySequenceGenerator atomicKeySequenceGenerator;
    private final IdentityClient identityClient;

    @Override
    @Transactional
    public ProjectResponse createProject(CreateProjectRequest request, UUID currentUserId) {
        String key = request.getKey().trim().toUpperCase();
        if (projectRepository.existsByKeyIgnoreCase(key)) {
            throw new BadRequestException("Mã dự án '" + key + "' đã tồn tại trong hệ thống");
        }

        UUID leadUserId = request.getLeadUserId() != null ? request.getLeadUserId() : currentUserId;

        Project project = Project.builder()
                .workspaceId(request.getWorkspaceId())
                .teamId(request.getTeamId())
                .name(request.getName().trim())
                .key(key)
                .description(request.getDescription())
                .iconUrl(request.getIconUrl())
                .leadUserId(leadUserId)
                .defaultAssigneeType(request.getDefaultAssigneeType() != null ? request.getDefaultAssigneeType() : DefaultAssigneeType.PROJECT_LEADER)
                .projectType(request.getProjectType() != null ? request.getProjectType() : ProjectType.SOFTWARE_SCRUM)
                .isTemplate(Boolean.TRUE.equals(request.getIsTemplate()))
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .createdBy(currentUserId)
                .build();

        Project savedProject = projectRepository.save(project);
        log.info("Project created successfully: ID={}, Key={}", savedProject.getId(), savedProject.getKey());

        // Auto-seed project ecosystem
        seedProjectEcosystem(savedProject, currentUserId, leadUserId);

        return mapToProjectResponse(savedProject);
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectDetailResponse getProjectById(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dự án với ID: " + projectId));
        return mapToProjectDetailResponse(project);
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectDetailResponse getProjectByKey(String key) {
        Project project = projectRepository.findByKeyIgnoreCase(key.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dự án với mã Key: " + key));
        return mapToProjectDetailResponse(project);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> getAllProjects(
            UUID workspaceId,
            UUID teamId,
            String search,
            Boolean isTemplate,
            Pageable pageable
    ) {
        Specification<Project> spec = ProjectSpecification.withFilters(workspaceId, teamId, search, isTemplate, false);
        Page<Project> page = projectRepository.findAll(spec, pageable);

        List<ProjectResponse> content = page.getContent().stream()
                .map(this::mapToProjectResponse)
                .collect(Collectors.toList());

        return PageResponse.of(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast(),
                page.isFirst()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> getTemplates(UUID workspaceId, Pageable pageable) {
        return getAllProjects(workspaceId, null, null, true, pageable);
    }

    @Override
    @Transactional
    public ProjectResponse updateProject(UUID projectId, UpdateProjectRequest request, UUID currentUserId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dự án với ID: " + projectId));

        if (project.getDeletedAt() != null) {
            throw new BadRequestException("Không thể cập nhật dự án đã bị xóa");
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            project.setName(request.getName().trim());
        }
        if (request.getTeamId() != null) {
            project.setTeamId(request.getTeamId());
        }
        if (request.getDescription() != null) {
            project.setDescription(request.getDescription());
        }
        if (request.getIconUrl() != null) {
            project.setIconUrl(request.getIconUrl());
        }
        if (request.getLeadUserId() != null) {
            project.setLeadUserId(request.getLeadUserId());
        }
        if (request.getDefaultAssigneeType() != null) {
            project.setDefaultAssigneeType(request.getDefaultAssigneeType());
        }
        if (request.getProjectType() != null) {
            project.setProjectType(request.getProjectType());
        }
        if (request.getIsTemplate() != null) {
            project.setIsTemplate(request.getIsTemplate());
        }
        if (request.getStartDate() != null) {
            project.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            project.setEndDate(request.getEndDate());
        }

        Project updatedProject = projectRepository.save(project);
        log.info("Project updated: ID={}, Name={}", updatedProject.getId(), updatedProject.getName());
        return mapToProjectResponse(updatedProject);
    }

    @Override
    @Transactional
    public void deleteProject(UUID projectId, UUID currentUserId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dự án với ID: " + projectId));

        if (project.getDeletedAt() != null) {
            return;
        }

        project.setDeletedAt(LocalDateTime.now());
        projectRepository.save(project);
        log.info("Project soft-deleted: ID={}, Key={}", project.getId(), project.getKey());
    }

    @Override
    @Transactional
    public ProjectResponse restoreProject(UUID projectId, UUID currentUserId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dự án với ID: " + projectId));

        project.setDeletedAt(null);
        Project saved = projectRepository.save(project);
        log.info("Project restored: ID={}, Key={}", saved.getId(), saved.getKey());
        return mapToProjectResponse(saved);
    }

    // ─── AUTO-SEEDING ECOSYSTEM ───────────────────────────────────────────────

    private void seedProjectEcosystem(Project project, UUID currentUserId, UUID leadUserId) {
        // 1. Sequence in PostgreSQL
        try {
            atomicKeySequenceGenerator.createSequenceForProject(project.getKey());
        } catch (Exception e) {
            log.warn("Sequence initialization notice for project '{}': {}", project.getKey(), e.getMessage());
        }

        // 2. Default 5 Work Item Types
        List<WorkItemType> defaultTypes = List.of(
                WorkItemType.builder()
                        .projectId(project.getId())
                        .name("Epic")
                        .code("EPIC")
                        .description("High-level initiative or large body of work")
                        .iconName("Zap")
                        .colorHex("#9333EA")
                        .hierarchyLevel(1)
                        .isSubtask(false)
                        .isSystemDefault(true)
                        .build(),
                WorkItemType.builder()
                        .projectId(project.getId())
                        .name("Story")
                        .code("STORY")
                        .description("User story capturing requirements from a user perspective")
                        .iconName("Bookmark")
                        .colorHex("#10B981")
                        .hierarchyLevel(0)
                        .isSubtask(false)
                        .isSystemDefault(true)
                        .build(),
                WorkItemType.builder()
                        .projectId(project.getId())
                        .name("Task")
                        .code("TASK")
                        .description("Standard task or action item")
                        .iconName("CheckSquare")
                        .colorHex("#3B82F6")
                        .hierarchyLevel(0)
                        .isSubtask(false)
                        .isSystemDefault(true)
                        .build(),
                WorkItemType.builder()
                        .projectId(project.getId())
                        .name("Bug")
                        .code("BUG")
                        .description("Problem or defect that impairs product functionality")
                        .iconName("Bug")
                        .colorHex("#EF4444")
                        .hierarchyLevel(0)
                        .isSubtask(false)
                        .isSystemDefault(true)
                        .build(),
                WorkItemType.builder()
                        .projectId(project.getId())
                        .name("Subtask")
                        .code("SUBTASK")
                        .description("Sub-element of a parent task or story")
                        .iconName("GitCommit")
                        .colorHex("#06B6D4")
                        .hierarchyLevel(-1)
                        .isSubtask(true)
                        .isSystemDefault(true)
                        .build()
        );
        List<WorkItemType> savedTypes = workItemTypeRepository.saveAll(defaultTypes);

        // 3. Default Statuses
        Status todoStatus = getOrCreateStatus("To Do", StatusCategory.TODO, "#64748B", "Initial backlog state");
        Status inProgressStatus = getOrCreateStatus("In Progress", StatusCategory.IN_PROGRESS, "#3B82F6", "Work actively ongoing");
        Status doneStatus = getOrCreateStatus("Done", StatusCategory.DONE, "#10B981", "Task completed");

        // 4. Default Workflow & Transitions
        Workflow workflow = Workflow.builder()
                .projectId(project.getId())
                .name(project.getName() + " Default Workflow")
                .description("Standard 3-state agile workflow for " + project.getName())
                .isDefault(true)
                .build();
        workflow = workflowRepository.save(workflow);

        List<WorkflowTransition> transitions = List.of(
                WorkflowTransition.builder().workflowId(workflow.getId()).fromStatusId(null).toStatusId(todoStatus.getId()).name("Create").build(),
                WorkflowTransition.builder().workflowId(workflow.getId()).fromStatusId(todoStatus.getId()).toStatusId(inProgressStatus.getId()).name("Start Work").build(),
                WorkflowTransition.builder().workflowId(workflow.getId()).fromStatusId(inProgressStatus.getId()).toStatusId(doneStatus.getId()).name("Complete").build(),
                WorkflowTransition.builder().workflowId(workflow.getId()).fromStatusId(doneStatus.getId()).toStatusId(inProgressStatus.getId()).name("Reopen").build(),
                WorkflowTransition.builder().workflowId(workflow.getId()).fromStatusId(inProgressStatus.getId()).toStatusId(todoStatus.getId()).name("Stop Progress").build(),
                WorkflowTransition.builder().workflowId(workflow.getId()).fromStatusId(todoStatus.getId()).toStatusId(doneStatus.getId()).name("Quick Done").build()
        );
        workflowTransitionRepository.saveAll(transitions);

        // 5. Workflow Scheme & Mappings
        WorkflowScheme scheme = WorkflowScheme.builder()
                .projectId(project.getId())
                .name(project.getName() + " Workflow Scheme")
                .description("Default scheme binding all types to standard workflow")
                .build();
        scheme = workflowSchemeRepository.save(scheme);

        UUID schemeId = scheme.getId();
        UUID workflowId = workflow.getId();
        List<WorkflowSchemeMapping> mappings = savedTypes.stream()
                .map(type -> WorkflowSchemeMapping.builder()
                        .schemeId(schemeId)
                        .workItemTypeId(type.getId())
                        .workflowId(workflowId)
                        .build())
                .collect(Collectors.toList());
        workflowSchemeMappingRepository.saveAll(mappings);

        // 6. Default Board & Board Columns
        BoardType boardType = project.getProjectType() == ProjectType.SOFTWARE_KANBAN ? BoardType.KANBAN : BoardType.SCRUM;
        Board board = Board.builder()
                .projectId(project.getId())
                .name(project.getName() + " Board")
                .type(boardType)
                .build();
        board = boardRepository.save(board);

        BoardColumn colTodo = boardColumnRepository.save(BoardColumn.builder().boardId(board.getId()).name("TO DO").columnOrder(0).build());
        BoardColumn colInProgress = boardColumnRepository.save(BoardColumn.builder().boardId(board.getId()).name("IN PROGRESS").columnOrder(1).build());
        BoardColumn colDone = boardColumnRepository.save(BoardColumn.builder().boardId(board.getId()).name("DONE").columnOrder(2).build());

        List<BoardColumnStatus> columnStatuses = List.of(
                BoardColumnStatus.builder().columnId(colTodo.getId()).statusId(todoStatus.getId()).build(),
                BoardColumnStatus.builder().columnId(colInProgress.getId()).statusId(inProgressStatus.getId()).build(),
                BoardColumnStatus.builder().columnId(colDone.getId()).statusId(doneStatus.getId()).build()
        );
        boardColumnStatusRepository.saveAll(columnStatuses);

        // 7. Default Project Roles & Permissions
        ProjectRole adminRole = projectRoleRepository.save(ProjectRole.builder()
                .projectId(project.getId())
                .name("ADMINISTRATOR")
                .description("Full administrative access to project settings, members, workflows and items")
                .build());

        ProjectRole memberRole = projectRoleRepository.save(ProjectRole.builder()
                .projectId(project.getId())
                .name("MEMBER")
                .description("Standard member who can view, create, edit, and transition work items")
                .build());

        ProjectRole viewerRole = projectRoleRepository.save(ProjectRole.builder()
                .projectId(project.getId())
                .name("VIEWER")
                .description("Read-only access to project work items and boards")
                .build());

        // Seed permission entries for ADMINISTRATOR (All 10 permissions)
        List<PermissionSchemeEntry> adminEntries = Arrays.stream(PermissionKey.values())
                .map(pk -> PermissionSchemeEntry.builder()
                        .projectId(project.getId())
                        .roleId(adminRole.getId())
                        .permissionKey(pk.name())
                        .build())
                .toList();

        // Seed permission entries for MEMBER
        List<PermissionSchemeEntry> memberEntries = List.of(
                PermissionKey.PROJECT_VIEW,
                PermissionKey.ISSUE_CREATE,
                PermissionKey.ISSUE_EDIT,
                PermissionKey.ISSUE_TRANSITION,
                PermissionKey.ISSUE_ASSIGN,
                PermissionKey.COMMENT_CREATE
        ).stream()
                .map(pk -> PermissionSchemeEntry.builder()
                        .projectId(project.getId())
                        .roleId(memberRole.getId())
                        .permissionKey(pk.name())
                        .build())
                .toList();

        // Seed permission entries for VIEWER
        List<PermissionSchemeEntry> viewerEntries = List.of(
                PermissionSchemeEntry.builder()
                        .projectId(project.getId())
                        .roleId(viewerRole.getId())
                        .permissionKey(PermissionKey.PROJECT_VIEW.name())
                        .build()
        );

        List<PermissionSchemeEntry> allEntries = new ArrayList<>();
        allEntries.addAll(adminEntries);
        allEntries.addAll(memberEntries);
        allEntries.addAll(viewerEntries);
        permissionSchemeEntryRepository.saveAll(allEntries);

        // Assign Lead / Creator as ADMINISTRATOR
        Set<UUID> adminUserIds = new HashSet<>();
        if (leadUserId != null) adminUserIds.add(leadUserId);
        if (currentUserId != null) adminUserIds.add(currentUserId);

        for (UUID userId : adminUserIds) {
            roleAssignmentRepository.save(RoleAssignment.builder()
                    .projectId(project.getId())
                    .userId(userId)
                    .roleId(adminRole.getId())
                    .build());
        }

        log.info("Project ecosystem seeded successfully for project: ID={}", project.getId());
    }

    private Status getOrCreateStatus(String name, StatusCategory category, String colorHex, String description) {
        return statusRepository.findByName(name).orElseGet(() ->
                statusRepository.save(Status.builder()
                        .name(name)
                        .category(category)
                        .colorHex(colorHex)
                        .description(description)
                        .build())
        );
    }

    // ─── MAPPERS ─────────────────────────────────────────────────────────────

    private ProjectResponse mapToProjectResponse(Project p) {
        return ProjectResponse.builder()
                .id(p.getId())
                .workspaceId(p.getWorkspaceId())
                .teamId(p.getTeamId())
                .name(p.getName())
                .key(p.getKey())
                .description(p.getDescription())
                .iconUrl(p.getIconUrl())
                .leadUserId(p.getLeadUserId())
                .defaultAssigneeType(p.getDefaultAssigneeType())
                .projectType(p.getProjectType())
                .isTemplate(p.getIsTemplate())
                .startDate(p.getStartDate())
                .endDate(p.getEndDate())
                .createdBy(p.getCreatedBy())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .deletedAt(p.getDeletedAt())
                .build();
    }

    private ProjectDetailResponse mapToProjectDetailResponse(Project p) {
        String teamName = null;
        if (p.getTeamId() != null) {
            teamName = teamRepository.findById(p.getTeamId())
                    .map(Team::getName)
                    .orElse(null);
        }

        String leadUserName = null;
        String leadUserEmail = null;
        String leadUserAvatar = null;

        if (p.getLeadUserId() != null) {
            try {
                var userApiRes = identityClient.getUserById(p.getLeadUserId());
                if (userApiRes != null && userApiRes.getData() != null) {
                    UserResponse user = userApiRes.getData();
                    leadUserName = user.getFullName();
                    leadUserEmail = user.getEmail();
                }
            } catch (Exception e) {
                log.debug("Could not fetch user details for leadUserId {}: {}", p.getLeadUserId(), e.getMessage());
            }
        }

        long workItemCount = workItemRepository.countByProjectId(p.getId());
        long memberCount = roleAssignmentRepository.findByProjectId(p.getId()).stream()
                .map(RoleAssignment::getUserId)
                .distinct()
                .count();

        List<WorkItemTypeResponse> workItemTypes = workItemTypeRepository.findByProjectId(p.getId()).stream()
                .map(t -> WorkItemTypeResponse.builder()
                        .id(t.getId())
                        .projectId(t.getProjectId())
                        .name(t.getName())
                        .code(t.getCode())
                        .description(t.getDescription())
                        .iconName(t.getIconName())
                        .colorHex(t.getColorHex())
                        .hierarchyLevel(t.getHierarchyLevel())
                        .isSubtask(t.getIsSubtask())
                        .isSystemDefault(t.getIsSystemDefault())
                        .createdAt(t.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        List<ProjectRoleResponse> roles = projectRoleRepository.findByProjectId(p.getId()).stream()
                .map(r -> ProjectRoleResponse.builder()
                        .id(r.getId())
                        .projectId(r.getProjectId())
                        .name(r.getName())
                        .description(r.getDescription())
                        .createdAt(r.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return ProjectDetailResponse.builder()
                .id(p.getId())
                .workspaceId(p.getWorkspaceId())
                .teamId(p.getTeamId())
                .teamName(teamName)
                .name(p.getName())
                .key(p.getKey())
                .description(p.getDescription())
                .iconUrl(p.getIconUrl())
                .leadUserId(p.getLeadUserId())
                .leadUserName(leadUserName)
                .leadUserEmail(leadUserEmail)
                .leadUserAvatarUrl(leadUserAvatar)
                .defaultAssigneeType(p.getDefaultAssigneeType())
                .projectType(p.getProjectType())
                .isTemplate(p.getIsTemplate())
                .startDate(p.getStartDate())
                .endDate(p.getEndDate())
                .createdBy(p.getCreatedBy())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .deletedAt(p.getDeletedAt())
                .workItemCount(workItemCount)
                .memberCount(memberCount)
                .workItemTypes(workItemTypes)
                .roles(roles)
                .build();
    }
}
