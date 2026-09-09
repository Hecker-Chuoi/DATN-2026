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
    private final TeamMemberRepository teamMemberRepository;

    private final AtomicKeySequenceGenerator atomicKeySequenceGenerator;
    private final IdentityClient identityClient;
    private final ProjectAccessService projectAccessService;
    private final ProjectMemberService projectMemberService;

    @Override
    @Transactional
    public ProjectResponse createProject(CreateProjectRequest request, UUID currentUserId) {
        String key = request.getKey().trim().toUpperCase();
        if (projectRepository.existsByKeyIgnoreCase(key)) {
            throw new BadRequestException("Mã dự án '" + key + "' đã tồn tại trong hệ thống");
        }

        UUID leadUserId = request.getLeadUserId() != null ? request.getLeadUserId() : currentUserId;

        // Chỉ thành viên của team (hoặc Global ADMIN) mới được gắn dự án vào team đó
        validateTeamMembership(request.getTeamId(), currentUserId, leadUserId);

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
        projectAccessService.assertProjectPermission(project.getId(), PermissionKey.PROJECT_VIEW);
        return mapToProjectDetailResponse(project);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> getAllProjects(
            UUID workspaceId,
            UUID teamId,
            String search,
            Boolean isTemplate,
            Pageable pageable,
            UUID currentUserId,
            boolean includeAll
    ) {
        Specification<Project> spec = ProjectSpecification.withFilters(workspaceId, teamId, search, isTemplate, false);

        Map<UUID, String> myRoles = currentUserId != null ? resolveMyRoles(currentUserId) : Collections.emptyMap();
        if (!includeAll) {
            if (currentUserId == null) {
                return PageResponse.of(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0L, 0, true, true);
            }
            spec = spec.and(ProjectSpecification.accessibleBy(currentUserId, myRoles.keySet()));
        }

        Page<Project> page = projectRepository.findAll(spec, pageable);

        List<ProjectResponse> content = page.getContent().stream()
                .map(p -> {
                    ProjectResponse response = mapToProjectResponse(p);
                    response.setMyRole(resolveMyRoleLabel(p, currentUserId, myRoles));
                    return response;
                })
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
    public PageResponse<ProjectResponse> getTemplates(UUID workspaceId, Pageable pageable, UUID currentUserId, boolean includeAll) {
        return getAllProjects(workspaceId, null, null, true, pageable, currentUserId, includeAll);
    }

    @Override
    @Transactional
    public ProjectResponse updateProject(UUID projectId, UpdateProjectRequest request, UUID currentUserId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dự án với ID: " + projectId));

        if (project.getDeletedAt() != null) {
            throw new BadRequestException("Không thể cập nhật dự án đã bị xóa");
        }

        boolean teamChanged = false;
        boolean leadChanged = false;

        if (request.getName() != null && !request.getName().isBlank()) {
            project.setName(request.getName().trim());
        }
        if (request.getTeamId() != null && !request.getTeamId().equals(project.getTeamId())) {
            validateTeamMembership(request.getTeamId(), currentUserId, null);
            project.setTeamId(request.getTeamId());
            teamChanged = true;
        }
        if (request.getDescription() != null) {
            project.setDescription(request.getDescription());
        }
        if (request.getIconUrl() != null) {
            project.setIconUrl(request.getIconUrl());
        }
        if (request.getLeadUserId() != null && !request.getLeadUserId().equals(project.getLeadUserId())) {
            UUID newLead = request.getLeadUserId();
            boolean isProjectMember = !roleAssignmentRepository.findByProjectIdAndUserId(projectId, newLead).isEmpty();
            boolean isTeamMember = project.getTeamId() != null && teamRepository.findById(project.getTeamId())
                    .map(t -> isTeamMember(t, newLead))
                    .orElse(false);
            if (!isProjectMember && !isTeamMember) {
                throw new BadRequestException("Trưởng dự án (Project Lead) mới phải là thành viên của dự án hoặc của nhóm");
            }
            project.setLeadUserId(newLead);
            leadChanged = true;
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

        if (leadChanged) {
            ensureAdministratorAssignment(updatedProject.getId(), updatedProject.getLeadUserId());
        }
        if (teamChanged) {
            projectMemberService.syncTeamMembersToProject(updatedProject.getId());
        }

        return mapToProjectResponse(updatedProject);
    }

    // ─── ACCESS VALIDATION HELPERS ─────────────────────────────────────────────

    /**
     * Chỉ thành viên/owner của team (hoặc Global ADMIN) mới được gắn dự án vào team.
     * Nếu chỉ định Project Lead khác người tạo thì Lead cũng phải thuộc team.
     */
    private void validateTeamMembership(UUID teamId, UUID currentUserId, UUID leadUserId) {
        if (teamId == null) return;

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm: " + teamId));

        if (!projectAccessService.isGlobalAdmin() && !isTeamMember(team, currentUserId)) {
            throw new AccessDeniedException("Bạn không phải thành viên của nhóm này nên không thể gắn dự án vào nhóm");
        }

        if (leadUserId != null && !leadUserId.equals(currentUserId) && !isTeamMember(team, leadUserId)) {
            throw new BadRequestException("Trưởng dự án (Project Lead) phải là thành viên của nhóm");
        }
    }

    private boolean isTeamMember(Team team, UUID userId) {
        if (team == null || userId == null) return false;
        if (userId.equals(team.getOwnerId())) return true;
        return teamMemberRepository.existsByTeamIdAndUserId(team.getId(), userId);
    }

    /**
     * Đảm bảo user có vai trò ADMINISTRATOR trong dự án (dùng khi đổi Project Lead).
     */
    private void ensureAdministratorAssignment(UUID projectId, UUID userId) {
        if (userId == null) return;
        ProjectRole adminRole = projectRoleRepository.findByProjectId(projectId).stream()
                .filter(r -> "ADMINISTRATOR".equalsIgnoreCase(r.getName()))
                .findFirst()
                .orElse(null);
        if (adminRole == null) return;

        List<RoleAssignment> existing = roleAssignmentRepository.findByProjectIdAndUserId(projectId, userId);
        if (existing.isEmpty()) {
            roleAssignmentRepository.save(RoleAssignment.builder()
                    .projectId(projectId)
                    .userId(userId)
                    .roleId(adminRole.getId())
                    .build());
        } else {
            RoleAssignment assignment = existing.get(0);
            if (!adminRole.getId().equals(assignment.getRoleId())) {
                assignment.setRoleId(adminRole.getId());
                roleAssignmentRepository.save(assignment);
            }
        }
    }

    /**
     * Map projectId -> tên vai trò cao nhất của user trong dự án đó.
     */
    private Map<UUID, String> resolveMyRoles(UUID userId) {
        List<RoleAssignment> assignments = roleAssignmentRepository.findByUserId(userId);
        if (assignments == null || assignments.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<UUID> roleIds = assignments.stream().map(RoleAssignment::getRoleId).collect(Collectors.toSet());
        Map<UUID, String> roleNames = projectRoleRepository.findAllById(roleIds).stream()
                .collect(Collectors.toMap(ProjectRole::getId, ProjectRole::getName, (a, b) -> a));

        Map<UUID, String> result = new HashMap<>();
        for (RoleAssignment assignment : assignments) {
            String name = roleNames.getOrDefault(assignment.getRoleId(), "MEMBER");
            result.merge(assignment.getProjectId(), name,
                    (oldName, newName) -> rolePriority(newName) < rolePriority(oldName) ? newName : oldName);
        }
        return result;
    }

    private String resolveMyRoleLabel(Project project, UUID userId, Map<UUID, String> myRoles) {
        if (userId == null) return null;
        if (userId.equals(project.getLeadUserId()) || userId.equals(project.getCreatedBy())) {
            return "ADMINISTRATOR";
        }
        return myRoles.get(project.getId());
    }

    private static int rolePriority(String roleName) {
        if ("ADMINISTRATOR".equalsIgnoreCase(roleName)) return 0;
        if ("MEMBER".equalsIgnoreCase(roleName)) return 1;
        if ("VIEWER".equalsIgnoreCase(roleName)) return 2;
        return 3;
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
                PermissionKey.COMMENT_CREATE,
                PermissionKey.COMMENT_EDIT_OWN,
                PermissionKey.COMMENT_DELETE_OWN,
                PermissionKey.ATTACHMENT_DELETE_OWN
        ).stream()
                .map(pk -> PermissionSchemeEntry.builder()
                        .projectId(project.getId())
                        .roleId(memberRole.getId())
                        .permissionKey(pk.name())
                        .build())
                .toList();

        // Seed permission entries for VIEWER (read-only + trao đổi bằng bình luận, giống Observer trong thiết kế)
        List<PermissionSchemeEntry> viewerEntries = List.of(
                PermissionKey.PROJECT_VIEW,
                PermissionKey.COMMENT_CREATE,
                PermissionKey.COMMENT_EDIT_OWN,
                PermissionKey.COMMENT_DELETE_OWN
        ).stream()
                .map(pk -> PermissionSchemeEntry.builder()
                        .projectId(project.getId())
                        .roleId(viewerRole.getId())
                        .permissionKey(pk.name())
                        .build())
                .toList();

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

        // Auto-assign Team Members if project belongs to a Team
        if (project.getTeamId() != null) {
            List<TeamMember> teamMembers = teamMemberRepository.findByTeamId(project.getTeamId());
            for (TeamMember tm : teamMembers) {
                if (tm.getUserId() != null && !adminUserIds.contains(tm.getUserId())) {
                    UUID targetRoleId = memberRole.getId();
                    if (tm.getRole() == com.datn.workservice.enums.TeamRole.LEADER) {
                        targetRoleId = adminRole.getId();
                    } else if (tm.getRole() == com.datn.workservice.enums.TeamRole.VIEWER) {
                        targetRoleId = viewerRole.getId();
                    }

                    if (!roleAssignmentRepository.existsByProjectIdAndUserIdAndRoleId(project.getId(), tm.getUserId(), targetRoleId)) {
                        roleAssignmentRepository.save(RoleAssignment.builder()
                                .projectId(project.getId())
                                .userId(tm.getUserId())
                                .roleId(targetRoleId)
                                .build());
                    }
                }
            }
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
