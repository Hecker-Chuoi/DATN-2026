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
import com.datn.workservice.service.ProjectAccessService;
import com.datn.workservice.service.ProjectMemberService;
import com.datn.workservice.service.ProjectService;
import com.datn.workservice.specification.ProjectSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
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
    private final ProjectAccessService projectAccessService;
    private final ProjectMemberService projectMemberService;
    private final com.datn.workservice.security.WorkspaceAccessChecker workspaceAccessChecker;

    @Override
    @Transactional
    public ProjectResponse createProject(CreateProjectRequest request, UUID currentUserId) {
        String key = request.getKey().trim().toUpperCase();

        if (projectRepository.existsByKeyIgnoreCase(key)) {
            throw new BadRequestException("Mã dự án '" + key + "' đã tồn tại trong hệ thống. Vui lòng chọn mã khác.");
        }

        UUID leadUserId = request.getLeadUserId() != null ? request.getLeadUserId() : currentUserId;

        // Chỉ thành viên Workspace (MEMBER trở lên) hoặc Global ADMIN mới được tạo dự án
        assertCanCreateProject(request.getWorkspaceId(), currentUserId, leadUserId);

        Project project = Project.builder()
                .workspaceId(request.getWorkspaceId())
                .name(request.getName().trim())
                .key(key)
                .description(request.getDescription())
                .iconUrl(request.getIconUrl())
                .leadUserId(leadUserId)
                .defaultAssigneeType(request.getDefaultAssigneeType() != null ? request.getDefaultAssigneeType() : DefaultAssigneeType.PROJECT_LEADER)
                .projectType(request.getProjectType() != null ? request.getProjectType() : ProjectType.SOFTWARE_SCRUM)
                .isTemplate(false)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .createdBy(currentUserId)
                .build();

        Project savedProject = projectRepository.save(project);

        // Auto-seed project dependencies
        initProjectEcosystem(savedProject, leadUserId, currentUserId);

        log.info("Project created successfully: ID={}, Key={}, WorkspaceId={}",
                savedProject.getId(), savedProject.getKey(), savedProject.getWorkspaceId());

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
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dự án với mã: " + key));
        return mapToProjectDetailResponse(project);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> getAllProjects(
            List<UUID> workspaceIds,
            String search,
            Boolean isTemplate,
            Pageable pageable,
            UUID currentUserId,
            boolean includeAll
    ) {
        Specification<Project> spec = ProjectSpecification.withFilters(workspaceIds, search, isTemplate, false);

        Map<UUID, String> myRoles = currentUserId != null ? resolveMyRoles(currentUserId) : Collections.emptyMap();
        if (!includeAll) {
            spec = spec.and(ProjectSpecification.accessibleBy(currentUserId, myRoles.keySet()));
        }

        Page<Project> page = projectRepository.findAll(spec, pageable);
        List<ProjectResponse> content = page.getContent().stream()
                .map(this::mapToProjectResponse)
                .peek(r -> r.setMyRole(myRoles.get(r.getId())))
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
    public PageResponse<ProjectResponse> getTemplates(
            List<UUID> workspaceIds,
            Pageable pageable,
            UUID currentUserId,
            boolean includeAll
    ) {
        return getAllProjects(workspaceIds, null, true, pageable, currentUserId, includeAll);
    }

    @Override
    @Transactional
    public ProjectResponse updateProject(UUID projectId, UpdateProjectRequest request, UUID currentUserId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dự án với ID: " + projectId));

        if (request.getName() != null && !request.getName().isBlank()) {
            project.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            project.setDescription(request.getDescription());
        }
        if (request.getIconUrl() != null) {
            project.setIconUrl(request.getIconUrl());
        }
        if (request.getDefaultAssigneeType() != null) {
            project.setDefaultAssigneeType(request.getDefaultAssigneeType());
        }
        if (request.getStartDate() != null) {
            project.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            project.setEndDate(request.getEndDate());
        }

        if (request.getLeadUserId() != null && !request.getLeadUserId().equals(project.getLeadUserId())) {
            UUID newLead = request.getLeadUserId();
            boolean isProjectMember = roleAssignmentRepository.findByProjectIdAndUserId(projectId, newLead)
                    .stream().findAny().isPresent();
            if (!isProjectMember) {
                workspaceAccessChecker.requireMember(project.getWorkspaceId(), newLead);
            }
            project.setLeadUserId(newLead);
        }

        Project updatedProject = projectRepository.save(project);
        log.info("Project updated: ID={}, Key={}", updatedProject.getId(), updatedProject.getKey());

        return mapToProjectResponse(updatedProject);
    }

    @Override
    public void assertCanCreateProject(UUID workspaceId, UUID currentUserId, UUID leadUserId) {
        if (projectAccessService.isGlobalAdmin()) {
            return;
        }
        if (workspaceId != null) {
            workspaceAccessChecker.requireMember(workspaceId, currentUserId);
            if (leadUserId != null && !leadUserId.equals(currentUserId)) {
                workspaceAccessChecker.requireMember(workspaceId, leadUserId);
            }
        }
    }

    private boolean isWorkspaceAdminOrOwner(UUID workspaceId, UUID userId) {
        if (workspaceId == null || userId == null) return false;
        try {
            workspaceAccessChecker.requireAtLeast(workspaceId, userId, "ADMIN");
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public ProjectResponse toProjectResponse(Project project) {
        return mapToProjectResponse(project);
    }

    @Override
    @Transactional
    public void deleteProject(UUID projectId, UUID currentUserId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dự án: " + projectId));
        project.setDeletedAt(LocalDateTime.now());
        projectRepository.save(project);
        log.info("Project soft-deleted: id={}, by={}", projectId, currentUserId);
    }

    @Override
    @Transactional
    public ProjectResponse restoreProject(UUID projectId, UUID currentUserId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dự án: " + projectId));
        project.setDeletedAt(null);
        Project saved = projectRepository.save(project);
        log.info("Project restored: id={}, by={}", projectId, currentUserId);
        return mapToProjectResponse(saved);
    }

    // ─── PRIVATE ECOSYSTEM SEEDING ──────────────────────────────────────────

    private void initProjectEcosystem(Project project, UUID leadUserId, UUID currentUserId) {
        // 1. PostgreSQL Sequence
        atomicKeySequenceGenerator.createSequenceForProject(project.getKey());

        // 2. Default WorkItemTypes
        WorkItemType epic = WorkItemType.builder()
                .projectId(project.getId())
                .name("Epic")
                .code("EPIC")
                .description("A large body of work that can be broken down into smaller tasks")
                .iconName("bookmark")
                .colorHex("#904EE2")
                .hierarchyLevel(1)
                .isSubtask(false)
                .isSystemDefault(true)
                .build();

        WorkItemType task = WorkItemType.builder()
                .projectId(project.getId())
                .name("Task")
                .code("TASK")
                .description("A small piece of work that needs to be done")
                .iconName("check-square")
                .colorHex("#4BADE8")
                .hierarchyLevel(0)
                .isSubtask(false)
                .isSystemDefault(true)
                .build();

        WorkItemType subtask = WorkItemType.builder()
                .projectId(project.getId())
                .name("Sub-task")
                .code("SUBTASK")
                .description("A sub-component of a parent task")
                .iconName("corner-down-right")
                .colorHex("#4BADE8")
                .hierarchyLevel(-1)
                .isSubtask(true)
                .isSystemDefault(true)
                .build();

        workItemTypeRepository.saveAll(List.of(epic, task, subtask));

        // 3. Default Statuses
        Status todo = createDefaultProjectStatus(project.getId(), "TO DO", StatusCategory.TODO, "#4BADE8", "Work not yet started");
        Status inProgress = createDefaultProjectStatus(project.getId(), "IN PROGRESS", StatusCategory.IN_PROGRESS, "#F5A623", "Work currently active");
        Status done = createDefaultProjectStatus(project.getId(), "DONE", StatusCategory.DONE, "#27AE60", "Work completed and verified");

        // 4. Default Workflow & Transitions
        Workflow workflow = workflowRepository.save(Workflow.builder()
                .projectId(project.getId())
                .name(project.getName() + " Workflow")
                .description("Default Agile Workflow for " + project.getName())
                .isDefault(true)
                .build());

        workflowTransitionRepository.saveAll(List.of(
                WorkflowTransition.builder().workflowId(workflow.getId()).name("Start Work").fromStatusId(todo.getId()).toStatusId(inProgress.getId()).build(),
                WorkflowTransition.builder().workflowId(workflow.getId()).name("Complete Work").fromStatusId(inProgress.getId()).toStatusId(done.getId()).build(),
                WorkflowTransition.builder().workflowId(workflow.getId()).name("Reopen").fromStatusId(done.getId()).toStatusId(todo.getId()).build()
        ));

        // 5. Workflow Scheme
        WorkflowScheme scheme = workflowSchemeRepository.save(WorkflowScheme.builder()
                .projectId(project.getId())
                .name(project.getName() + " Workflow Scheme")
                .build());

        workflowSchemeMappingRepository.saveAll(List.of(
                WorkflowSchemeMapping.builder().schemeId(scheme.getId()).workItemTypeId(epic.getId()).workflowId(workflow.getId()).build(),
                WorkflowSchemeMapping.builder().schemeId(scheme.getId()).workItemTypeId(task.getId()).workflowId(workflow.getId()).build(),
                WorkflowSchemeMapping.builder().schemeId(scheme.getId()).workItemTypeId(subtask.getId()).workflowId(workflow.getId()).build()
        ));

        // 6. Default Kanban / Scrum Board
        BoardType boardType = (project.getProjectType() == ProjectType.SOFTWARE_KANBAN || project.getProjectType() == ProjectType.BUSINESS) ? BoardType.KANBAN : BoardType.SCRUM;
        Board board = boardRepository.save(Board.builder()
                .projectId(project.getId())
                .name(project.getName() + " Board")
                .type(boardType)
                .build());

        BoardColumn colTodo = boardColumnRepository.save(BoardColumn.builder().boardId(board.getId()).name("TO DO").columnOrder(0).build());
        BoardColumn colInProgress = boardColumnRepository.save(BoardColumn.builder().boardId(board.getId()).name("IN PROGRESS").columnOrder(1).build());
        BoardColumn colDone = boardColumnRepository.save(BoardColumn.builder().boardId(board.getId()).name("DONE").columnOrder(2).build());

        boardColumnStatusRepository.saveAll(List.of(
                BoardColumnStatus.builder().columnId(colTodo.getId()).statusId(todo.getId()).build(),
                BoardColumnStatus.builder().columnId(colInProgress.getId()).statusId(inProgress.getId()).build(),
                BoardColumnStatus.builder().columnId(colDone.getId()).statusId(done.getId()).build()
        ));

        // 7. Standard Jira-like Project Roles
        ProjectRole adminRole = projectRoleRepository.save(ProjectRole.builder()
                .projectId(project.getId())
                .name("ADMINISTRATOR")
                .description("Project administrator with full control")
                .build());

        ProjectRole memberRole = projectRoleRepository.save(ProjectRole.builder()
                .projectId(project.getId())
                .name("MEMBER")
                .description("Project member with work item management abilities")
                .build());

        ProjectRole viewerRole = projectRoleRepository.save(ProjectRole.builder()
                .projectId(project.getId())
                .name("VIEWER")
                .description("Read-only access to the project")
                .build());

        // Assign standard permissions to ADMINISTRATOR
        for (PermissionKey key : PermissionKey.values()) {
            permissionSchemeEntryRepository.save(PermissionSchemeEntry.builder()
                    .projectId(project.getId())
                    .roleId(adminRole.getId())
                    .permissionKey(key.name())
                    .build());
        }

        // Assign standard permissions to MEMBER
        List<PermissionKey> memberPerms = List.of(
                PermissionKey.PROJECT_VIEW,
                PermissionKey.ISSUE_CREATE,
                PermissionKey.ISSUE_EDIT,
                PermissionKey.ISSUE_ASSIGN,
                PermissionKey.ISSUE_TRANSITION,
                PermissionKey.COMMENT_CREATE,
                PermissionKey.COMMENT_EDIT_OWN
        );
        for (PermissionKey key : memberPerms) {
            permissionSchemeEntryRepository.save(PermissionSchemeEntry.builder()
                    .projectId(project.getId())
                    .roleId(memberRole.getId())
                    .permissionKey(key.name())
                    .build());
        }

        // Assign standard permissions to VIEWER
        List<PermissionKey> viewerPerms = List.of(
                PermissionKey.PROJECT_VIEW
        );
        for (PermissionKey key : viewerPerms) {
            permissionSchemeEntryRepository.save(PermissionSchemeEntry.builder()
                    .projectId(project.getId())
                    .roleId(viewerRole.getId())
                    .permissionKey(key.name())
                    .build());
        }

        // Assign Lead / Creator as ADMINISTRATOR
        Set<UUID> adminUserIds = new HashSet<>();
        if (leadUserId != null) adminUserIds.add(leadUserId);
        if (currentUserId != null && (currentUserId.equals(leadUserId) || !isWorkspaceAdminOrOwner(project.getWorkspaceId(), currentUserId))) {
            adminUserIds.add(currentUserId);
        }

        for (UUID userId : adminUserIds) {
            roleAssignmentRepository.save(RoleAssignment.builder()
                    .projectId(project.getId())
                    .userId(userId)
                    .roleId(adminRole.getId())
                    .build());
        }

        log.info("Project ecosystem seeded successfully for project: ID={}", project.getId());
    }

    private Status createDefaultProjectStatus(UUID projectId, String name, StatusCategory category, String colorHex, String description) {
        return statusRepository.findByProjectIdAndNameIgnoreCase(projectId, name).orElseGet(() ->
                statusRepository.save(Status.builder()
                        .projectId(projectId)
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
                .name(p.getName())
                .key(p.getKey())
                .description(p.getDescription())
                .iconUrl(p.getIconUrl())
                .leadUserId(p.getLeadUserId())
                .defaultAssigneeType(p.getDefaultAssigneeType())
                .projectType(p.getProjectType())
                .isTemplate(p.getIsTemplate())
                .templateCategory(p.getTemplateCategory())
                .startDate(p.getStartDate())
                .endDate(p.getEndDate())
                .createdBy(p.getCreatedBy())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .deletedAt(p.getDeletedAt())
                .build();
    }

    private ProjectDetailResponse mapToProjectDetailResponse(Project p) {
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
                .templateCategory(p.getTemplateCategory())
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

    private Map<UUID, String> resolveMyRoles(UUID userId) {
        if (userId == null) return Collections.emptyMap();
        List<RoleAssignment> assignments = roleAssignmentRepository.findByUserId(userId);
        if (assignments.isEmpty()) return Collections.emptyMap();

        Set<UUID> roleIds = assignments.stream().map(RoleAssignment::getRoleId).collect(Collectors.toSet());
        Map<UUID, String> roleNameById = projectRoleRepository.findAllById(roleIds).stream()
                .collect(Collectors.toMap(ProjectRole::getId, ProjectRole::getName));

        Map<UUID, String> result = new HashMap<>();
        for (RoleAssignment ra : assignments) {
            String roleName = roleNameById.get(ra.getRoleId());
            if (roleName != null) {
                result.put(ra.getProjectId(), roleName);
            }
        }
        return result;
    }
}
