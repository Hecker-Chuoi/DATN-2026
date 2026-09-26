package com.datn.workservice.service.impl;

import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workservice.dto.model.WorkflowConditionConfig;
import com.datn.workservice.dto.model.WorkflowPostFunctionConfig;
import com.datn.workservice.dto.model.WorkflowValidatorConfig;
import com.datn.workservice.dto.request.ExecuteTransitionRequest;
import com.datn.workservice.dto.response.AvailableTransitionResponse;
import com.datn.workservice.entity.*;
import com.datn.workservice.enums.LinkType;
import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.enums.StatusCategory;
import com.datn.workservice.enums.WorkItemResolution;
import com.datn.workservice.repository.*;
import com.datn.workservice.service.ProjectMemberService;
import com.datn.workservice.service.WorkflowEngineService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowEngineServiceImpl implements WorkflowEngineService {

    private final WorkItemRepository workItemRepository;
    private final WorkItemLinkRepository workItemLinkRepository;
    private final WorkflowRepository workflowRepository;
    private final WorkflowTransitionRepository workflowTransitionRepository;
    private final WorkflowSchemeRepository workflowSchemeRepository;
    private final WorkflowSchemeMappingRepository workflowSchemeMappingRepository;
    private final StatusRepository statusRepository;
    private final WorkItemChangelogRepository workItemChangelogRepository;
    private final WorkItemCommentRepository workItemCommentRepository;
    private final ProjectMemberService projectMemberService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public Workflow resolveWorkflow(UUID projectId, UUID workItemTypeId) {
        // 1. Try resolving via Project's Workflow Scheme & Mapping
        List<WorkflowScheme> schemes = workflowSchemeRepository.findByProjectId(projectId);
        if (schemes != null && !schemes.isEmpty()) {
            WorkflowScheme scheme = schemes.get(0);
            Optional<WorkflowSchemeMapping> mapping =
                    workflowSchemeMappingRepository.findBySchemeIdAndWorkItemTypeId(scheme.getId(), workItemTypeId);
            if (mapping.isPresent()) {
                return workflowRepository.findById(mapping.get().getWorkflowId())
                        .orElseThrow(() -> new ResourceNotFoundException("Workflow mapping exists but Workflow not found: " + mapping.get().getWorkflowId()));
            }
        }

        // 2. Fallback to Project Default Workflow
        Optional<Workflow> projectDefault = workflowRepository.findByProjectIdAndIsDefaultTrue(projectId);
        if (projectDefault.isPresent()) {
            return projectDefault.get();
        }

        // 3. Fallback to any Project Workflow
        List<Workflow> projectWorkflows = workflowRepository.findByProjectId(projectId);
        if (!projectWorkflows.isEmpty()) {
            return projectWorkflows.get(0);
        }

        // 4. Fallback to Global System Default Workflow
        return workflowRepository.findFirstByIsDefaultTrue()
                .orElseThrow(() -> new ResourceNotFoundException("No workflow found for project " + projectId + " and workItemType " + workItemTypeId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AvailableTransitionResponse> getAvailableTransitions(UUID workItemId, UUID currentUserId) {
        WorkItem workItem = workItemRepository.findById(workItemId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkItem not found with id: " + workItemId));

        Workflow workflow = resolveWorkflow(workItem.getProjectId(), workItem.getTypeId());
        List<WorkflowTransition> transitions = workflowTransitionRepository.findByWorkflowId(workflow.getId());

        List<AvailableTransitionResponse> result = new ArrayList<>();
        for (WorkflowTransition t : transitions) {
            // Check if transition origin matches current status
            if (t.getFromStatusId() != null && !t.getFromStatusId().equals(workItem.getStatusId())) {
                continue;
            }

            // Evaluate Conditions
            if (!evaluateConditions(t.getConditionsConfig(), workItem, currentUserId)) {
                continue;
            }

            // Target Status info
            Status toStatus = statusRepository.findById(t.getToStatusId()).orElse(null);
            if (toStatus == null) {
                continue;
            }

            // Check if transition requires screen (e.g. going to DONE or having required fields)
            List<String> requiredFields = extractRequiredFields(t.getValidatorsConfig());
            boolean hasScreen = toStatus.getCategory() == StatusCategory.DONE || !requiredFields.isEmpty();

            result.add(AvailableTransitionResponse.builder()
                    .transitionId(t.getId())
                    .name(t.getName())
                    .toStatusId(toStatus.getId())
                    .toStatusName(toStatus.getName())
                    .toStatusCategory(toStatus.getCategory())
                    .toStatusColorHex(toStatus.getColorHex())
                    .hasScreen(hasScreen)
                    .requiredFields(requiredFields)
                    .build());
        }

        return result;
    }

    @Override
    @Transactional
    public WorkItem executeTransition(UUID workItemId, UUID transitionId, ExecuteTransitionRequest request, UUID currentUserId) {
        WorkItem workItem = workItemRepository.findById(workItemId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkItem not found with id: " + workItemId));

        Workflow workflow = resolveWorkflow(workItem.getProjectId(), workItem.getTypeId());
        WorkflowTransition transition = workflowTransitionRepository.findById(transitionId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkflowTransition not found with id: " + transitionId));

        // 1. Verify Transition belongs to the active workflow
        if (!transition.getWorkflowId().equals(workflow.getId())) {
            throw new BadRequestException("Bước chuyển (Transition) không thuộc quy trình của công việc này");
        }

        // 2. Verify Origin Status matches current status
        if (transition.getFromStatusId() != null && !transition.getFromStatusId().equals(workItem.getStatusId())) {
            throw new BadRequestException("Trạng thái hiện tại của công việc không phù hợp với bước chuyển này");
        }

        // 3. Evaluate Conditions
        if (!evaluateConditions(transition.getConditionsConfig(), workItem, currentUserId)) {
            throw new AccessDeniedException("Bạn không đủ điều kiện (Condition) để thực hiện bước chuyển này");
        }

        // 4. Run Validators
        Status toStatus = statusRepository.findById(transition.getToStatusId())
                .orElseThrow(() -> new ResourceNotFoundException("Target Status not found: " + transition.getToStatusId()));

        // 4a. Blocked Transition Guard (BLOCKS link restriction)
        if (toStatus.getCategory() == StatusCategory.IN_PROGRESS || toStatus.getCategory() == StatusCategory.DONE) {
            List<WorkItemLink> incomingBlocks = workItemLinkRepository.findByTargetItemIdAndLinkType(workItem.getId(), LinkType.BLOCKS);
            if (!incomingBlocks.isEmpty()) {
                List<String> activeBlockers = new ArrayList<>();
                for (WorkItemLink link : incomingBlocks) {
                    workItemRepository.findById(link.getSourceItemId()).ifPresent(blocker -> {
                        if (blocker.getDeletedAt() == null) {
                            Status blockerStatus = statusRepository.findById(blocker.getStatusId()).orElse(null);
                            if (blockerStatus == null || blockerStatus.getCategory() != StatusCategory.DONE) {
                                activeBlockers.add(blocker.getCode() + " - " + blocker.getSummary());
                            }
                        }
                    });
                }
                if (!activeBlockers.isEmpty()) {
                    throw new BadRequestException("Không thể chuyển sang trạng thái '" + toStatus.getName() + "' do công việc đang bị chặn bởi: " + String.join("; ", activeBlockers));
                }
            }
        }

        // 4b. Subtask & Descendant Blocking Guard & Cascading Resolve (when moving to DONE)
        if (toStatus.getCategory() == StatusCategory.DONE) {
            List<WorkItem> descendants = findAllDescendants(workItem.getId());
            if (!descendants.isEmpty()) {
                List<WorkItem> incompleteDescendants = descendants.stream()
                        .filter(desc -> {
                            Status descStatus = statusRepository.findById(desc.getStatusId()).orElse(null);
                            return descStatus == null || descStatus.getCategory() != StatusCategory.DONE;
                        })
                        .toList();
                if (!incompleteDescendants.isEmpty()) {
                    if (request != null && Boolean.TRUE.equals(request.getResolveAllSubtasks())) {
                        List<Status> projectDoneStatuses = statusRepository.findByProjectId(workItem.getProjectId()).stream()
                                .filter(s -> s.getCategory() == StatusCategory.DONE)
                                .toList();
                        Status defaultDone = !projectDoneStatuses.isEmpty() ? projectDoneStatuses.get(0) : toStatus;
                        for (WorkItem desc : incompleteDescendants) {
                            Status oldDescStatus = statusRepository.findById(desc.getStatusId()).orElse(null);
                            desc.setStatusId(defaultDone.getId());
                            desc.setResolution(WorkItemResolution.DONE);
                            desc.setResolvedAt(LocalDateTime.now());
                            workItemRepository.save(desc);
                            if (currentUserId != null) {
                                WorkItemChangelog descChangelog = WorkItemChangelog.builder()
                                        .workItemId(desc.getId())
                                        .fieldName("STATUS")
                                        .oldValue(oldDescStatus != null ? oldDescStatus.getName() : null)
                                        .newValue(defaultDone.getName())
                                        .changedBy(currentUserId)
                                        .changedAt(LocalDateTime.now())
                                        .build();
                                workItemChangelogRepository.save(descChangelog);
                            }
                        }
                        log.info("Auto-resolved {} descendant work items for parent WorkItem '{}'", incompleteDescendants.size(), workItem.getCode());
                    } else {
                        throw new BadRequestException("Không thể hoàn thành công việc này vì còn " + incompleteDescendants.size() + " nhiệm vụ con (Sub-task) chưa hoàn thành");
                    }
                }
            }
        }

        executeValidators(transition.getValidatorsConfig(), workItem, request, toStatus);

        // 5. Update Status & Resolution
        Status oldStatus = statusRepository.findById(workItem.getStatusId()).orElse(null);
        workItem.setStatusId(toStatus.getId());

        // Handle Resolution
        if (request != null && request.getResolution() != null && !request.getResolution().isBlank()) {
            try {
                workItem.setResolution(WorkItemResolution.valueOf(request.getResolution().trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                workItem.setResolution(WorkItemResolution.DONE);
            }
        } else if (toStatus.getCategory() == StatusCategory.DONE && workItem.getResolution() == null) {
            workItem.setResolution(WorkItemResolution.DONE);
        }

        // Handle optional fields
        if (request != null && request.getFields() != null) {
            applyFieldsUpdate(workItem, request.getFields());
        }

        // 6. Run Post-Functions
        executePostFunctions(transition.getPostFunctionsConfig(), workItem, oldStatus, toStatus, currentUserId, request);

        // 7. Save & Return
        WorkItem saved = workItemRepository.save(workItem);
        log.info("WorkItem '{}' transitioned from '{}' to '{}' via transition '{}' by user '{}'",
                saved.getCode(),
                oldStatus != null ? oldStatus.getName() : "NULL",
                toStatus.getName(),
                transition.getName(),
                currentUserId);

        // 8. Cascade Ancestor Status Transitions (Auto-reopen on active child & Auto-start on IN_PROGRESS)
        cascadeAncestorStatusTransitions(workItem, toStatus, currentUserId);

        return saved;
    }

    // ─── Pipeline Helpers ────────────────────────────────────────────────────────

    private boolean evaluateConditions(Object conditionsConfig, WorkItem workItem, UUID currentUserId) {
        if (conditionsConfig == null) {
            return true;
        }

        List<WorkflowConditionConfig> conditions = parseConfigList(conditionsConfig, new TypeReference<List<WorkflowConditionConfig>>() {});
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }

        for (WorkflowConditionConfig cond : conditions) {
            if (cond == null || cond.getType() == null) continue;
            String type = cond.getType().trim().toUpperCase();

            switch (type) {
                case "ASSIGNEE_ONLY" -> {
                    if (currentUserId == null || !currentUserId.equals(workItem.getAssigneeId())) {
                        return false;
                    }
                }
                case "ONLY_CREATOR", "REPORTER_ONLY" -> {
                    if (currentUserId == null || !currentUserId.equals(workItem.getReporterId())) {
                        return false;
                    }
                }
                case "PERMISSION_REQUIRED" -> {
                    String permKey = cond.getParams() != null && cond.getParams().get("permissionKey") != null
                            ? cond.getParams().get("permissionKey").toString()
                            : "ISSUE_TRANSITION";
                    try {
                        PermissionKey key = PermissionKey.valueOf(permKey);
                        if (!projectMemberService.hasPermission(workItem.getProjectId(), currentUserId, key)) {
                            return false;
                        }
                    } catch (Exception e) {
                        return false;
                    }
                }
                default -> log.debug("Unrecognized condition type: {}", type);
            }
        }
        return true;
    }

    private void executeValidators(Object validatorsConfig, WorkItem workItem, ExecuteTransitionRequest request, Status toStatus) {
        List<WorkflowValidatorConfig> validators = parseConfigList(validatorsConfig, new TypeReference<List<WorkflowValidatorConfig>>() {});

        // Built-in Validator: If transition is going to DONE category and resolution is required
        if (toStatus.getCategory() == StatusCategory.DONE) {
            boolean hasResolution = (request != null && request.getResolution() != null && !request.getResolution().isBlank())
                    || workItem.getResolution() != null;
            // If resolution not explicitly provided, we will auto-assign WorkItemResolution.DONE in state update
        }

        if (validators == null || validators.isEmpty()) {
            return;
        }

        for (WorkflowValidatorConfig val : validators) {
            if (val == null || val.getType() == null) continue;
            String type = val.getType().trim().toUpperCase();

            switch (type) {
                case "ALL_SUBTASKS_DONE" -> {
                    if (request != null && Boolean.TRUE.equals(request.getResolveAllSubtasks())) {
                        break;
                    }
                    List<WorkItem> descendants = findAllDescendants(workItem.getId());
                    if (!descendants.isEmpty()) {
                        long incomplete = descendants.stream()
                                .map(desc -> statusRepository.findById(desc.getStatusId()).orElse(null))
                                .filter(stStatus -> stStatus == null || stStatus.getCategory() != StatusCategory.DONE)
                                .count();
                        if (incomplete > 0) {
                            throw new BadRequestException("Không thể hoàn thành Story/Task này vì còn " + incomplete + " nhiệm vụ con (Sub-task) chưa hoàn thành");
                        }
                    }
                }
                case "FIELD_REQUIRED" -> {
                    if (val.getParams() != null && val.getParams().get("fields") instanceof List<?> requiredFields) {
                        for (Object field : requiredFields) {
                            String fieldName = field.toString();
                            if ("resolution".equalsIgnoreCase(fieldName)) {
                                if ((request == null || request.getResolution() == null || request.getResolution().isBlank())
                                        && workItem.getResolution() == null) {
                                    throw new BadRequestException("Vui lòng cung cấp lý do/kết quả giải quyết (Resolution)");
                                }
                            } else if ("comment".equalsIgnoreCase(fieldName)) {
                                if (request == null || request.getComment() == null || request.getComment().isBlank()) {
                                    throw new BadRequestException("Bắt buộc phải nhập bình luận (Comment) khi thực hiện bước chuyển này");
                                }
                            }
                        }
                    }
                }
                case "ESTIMATE_REQUIRED" -> {
                    if (workItem.getStoryPoints() == null && (workItem.getOriginalEstimateSeconds() == null || workItem.getOriginalEstimateSeconds() == 0)) {
                        throw new BadRequestException("Công việc này bắt buộc phải có ước lượng (Story Points hoặc Estimate) trước khi chuyển trạng thái");
                    }
                }
                default -> log.debug("Unrecognized validator type: {}", type);
            }
        }
    }

    private void executePostFunctions(Object postFunctionsConfig, WorkItem workItem, Status oldStatus, Status toStatus, UUID currentUserId, ExecuteTransitionRequest request) {
        // Built-in 1: Set Resolved At when moving to DONE
        if (toStatus.getCategory() == StatusCategory.DONE) {
            if (workItem.getResolvedAt() == null) {
                workItem.setResolvedAt(LocalDateTime.now());
            }
        }

        // Built-in 2: Clear Resolution & Resolved At when moving out of DONE
        if (oldStatus != null && oldStatus.getCategory() == StatusCategory.DONE && toStatus.getCategory() != StatusCategory.DONE) {
            workItem.setResolution(null);
            workItem.setResolvedAt(null);
        }

        // Built-in 3: Emit Changelog
        if (currentUserId != null) {
            WorkItemChangelog changelog = WorkItemChangelog.builder()
                    .workItemId(workItem.getId())
                    .fieldName("STATUS")
                    .oldValue(oldStatus != null ? oldStatus.getName() : null)
                    .newValue(toStatus.getName())
                    .changedBy(currentUserId)
                    .changedAt(LocalDateTime.now())
                    .build();
            workItemChangelogRepository.save(changelog);
        }

        // Built-in 4: Save comment if provided
        if (request != null && request.getComment() != null && !request.getComment().isBlank() && currentUserId != null) {
            WorkItemComment comment = WorkItemComment.builder()
                    .workItemId(workItem.getId())
                    .authorId(currentUserId)
                    .body(request.getComment().trim())
                    .build();
            workItemCommentRepository.save(comment);
        }

        // Custom configured post-functions
        List<WorkflowPostFunctionConfig> postFunctions = parseConfigList(postFunctionsConfig, new TypeReference<List<WorkflowPostFunctionConfig>>() {});
        if (postFunctions != null) {
            for (WorkflowPostFunctionConfig pf : postFunctions) {
                if (pf == null || pf.getType() == null) continue;
                String type = pf.getType().trim().toUpperCase();
                switch (type) {
                    case "CLEAR_RESOLUTION" -> {
                        workItem.setResolution(null);
                        workItem.setResolvedAt(null);
                    }
                    case "SET_RESOLVED_TIME" -> workItem.setResolvedAt(LocalDateTime.now());
                    default -> log.debug("Post-function executed: {}", type);
                }
            }
        }
    }

    private List<String> extractRequiredFields(Object validatorsConfig) {
        List<String> required = new ArrayList<>();
        List<WorkflowValidatorConfig> validators = parseConfigList(validatorsConfig, new TypeReference<List<WorkflowValidatorConfig>>() {});
        if (validators != null) {
            for (WorkflowValidatorConfig v : validators) {
                if (v != null && "FIELD_REQUIRED".equalsIgnoreCase(v.getType()) && v.getParams() != null) {
                    Object fieldsObj = v.getParams().get("fields");
                    if (fieldsObj instanceof List<?> list) {
                        list.forEach(item -> required.add(item.toString()));
                    }
                }
            }
        }
        return required;
    }

    private void applyFieldsUpdate(WorkItem workItem, Map<String, Object> fields) {
        if (fields.containsKey("assigneeId")) {
            Object val = fields.get("assigneeId");
            workItem.setAssigneeId(val != null ? UUID.fromString(val.toString()) : null);
        }
        if (fields.containsKey("fixVersionId")) {
            Object val = fields.get("fixVersionId");
            workItem.setFixVersionId(val != null ? UUID.fromString(val.toString()) : null);
        }
    }

    private <T> T parseConfigList(Object configObj, TypeReference<T> typeRef) {
        if (configObj == null) return null;
        try {
            return objectMapper.convertValue(configObj, typeRef);
        } catch (Exception e) {
            log.warn("Failed to parse JSONB workflow config: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Cascades status changes recursively up the hierarchy (Subtask -> Story/Task -> Epic):
     * 1. Cascade Reopen (Reverse Roll-up): If a child work item is not DONE (e.g. IN_PROGRESS or TODO)
     *    and an ancestor (parent Story/Task or grandparent Epic) is currently in DONE,
     *    that ancestor CANNOT remain DONE (an issue cannot be completed if its children are incomplete).
     *    The ancestor is automatically reopened (moved to IN_PROGRESS), its resolution and resolvedAt are cleared,
     *    and audit changelog is recorded.
     * 2. Auto-start Ancestors: If child moves to IN_PROGRESS and an ancestor is in TODO,
     *    the ancestor is automatically transitioned to IN_PROGRESS.
     */
    private void cascadeAncestorStatusTransitions(WorkItem workItem, Status toStatus, UUID currentUserId) {
        if (workItem.getParentId() == null) {
            return;
        }

        UUID currentParentId = workItem.getParentId();
        WorkItem triggerItem = workItem;
        Set<UUID> visited = new HashSet<>();

        while (currentParentId != null && visited.add(currentParentId)) {
            WorkItem parent = workItemRepository.findById(currentParentId).orElse(null);
            if (parent == null || parent.getDeletedAt() != null) {
                break;
            }

            Status parentStatus = statusRepository.findById(parent.getStatusId()).orElse(null);
            if (parentStatus != null) {
                // Rule 1: Descendant is not DONE (e.g. reopened to IN_PROGRESS or TODO), but ancestor is DONE
                if (toStatus.getCategory() != StatusCategory.DONE && parentStatus.getCategory() == StatusCategory.DONE) {
                    Status targetReopenStatus = resolveInProgressOrActiveStatus(parent);
                    if (targetReopenStatus != null) {
                        String oldStatusName = parentStatus.getName();
                        WorkItemResolution oldResolution = parent.getResolution();

                        parent.setStatusId(targetReopenStatus.getId());
                        parent.setResolution(null);
                        parent.setResolvedAt(null);
                        workItemRepository.save(parent);

                        if (currentUserId != null) {
                            WorkItemChangelog statusChangelog = WorkItemChangelog.builder()
                                    .workItemId(parent.getId())
                                    .fieldName("STATUS")
                                    .oldValue(oldStatusName)
                                    .newValue(targetReopenStatus.getName())
                                    .changedBy(currentUserId)
                                    .changedAt(LocalDateTime.now())
                                    .build();
                            workItemChangelogRepository.save(statusChangelog);

                            if (oldResolution != null) {
                                WorkItemChangelog resChangelog = WorkItemChangelog.builder()
                                        .workItemId(parent.getId())
                                        .fieldName("RESOLUTION")
                                        .oldValue(oldResolution.name())
                                        .newValue(null)
                                        .changedBy(currentUserId)
                                        .changedAt(LocalDateTime.now())
                                        .build();
                                workItemChangelogRepository.save(resChangelog);
                            }
                        }
                        log.info("Cascading reopen: Ancestor WorkItem '{}' automatically reopened from '{}' to '{}' because descendant '{}' is in non-DONE status '{}'",
                                parent.getCode(), oldStatusName, targetReopenStatus.getName(), triggerItem.getCode(), toStatus.getName());
                    }
                }
                // Rule 2: Descendant moved to IN_PROGRESS, but ancestor is still in TODO
                else if (toStatus.getCategory() == StatusCategory.IN_PROGRESS && parentStatus.getCategory() == StatusCategory.TODO) {
                    Status targetStartStatus = resolveInProgressOrActiveStatus(parent);
                    if (targetStartStatus != null) {
                        String oldStatusName = parentStatus.getName();
                        parent.setStatusId(targetStartStatus.getId());
                        workItemRepository.save(parent);

                        if (currentUserId != null) {
                            WorkItemChangelog statusChangelog = WorkItemChangelog.builder()
                                    .workItemId(parent.getId())
                                    .fieldName("STATUS")
                                    .oldValue(oldStatusName)
                                    .newValue(targetStartStatus.getName())
                                    .changedBy(currentUserId)
                                    .changedAt(LocalDateTime.now())
                                    .build();
                            workItemChangelogRepository.save(statusChangelog);
                        }
                        log.info("Auto-transitioned ancestor WorkItem '{}' from '{}' to '{}' because descendant '{}' moved to IN_PROGRESS",
                                parent.getCode(), oldStatusName, targetStartStatus.getName(), triggerItem.getCode());
                    }
                }
            }

            // Ascend to next level in hierarchy (e.g. Subtask -> Story -> Epic)
            triggerItem = parent;
            currentParentId = parent.getParentId();
        }
    }

    private Status resolveInProgressOrActiveStatus(WorkItem item) {
        // 1. Try to find an IN_PROGRESS status from item's workflow
        try {
            Workflow workflow = resolveWorkflow(item.getProjectId(), item.getTypeId());
            List<WorkflowTransition> transitions = workflowTransitionRepository.findByWorkflowId(workflow.getId());
            for (WorkflowTransition t : transitions) {
                Status s = statusRepository.findById(t.getToStatusId()).orElse(null);
                if (s != null && s.getCategory() == StatusCategory.IN_PROGRESS) {
                    return s;
                }
            }
        } catch (Exception ignored) {
            // Fallback below
        }

        // 2. Global fallback to any IN_PROGRESS status
        List<Status> inProgressList = statusRepository.findByCategory(StatusCategory.IN_PROGRESS);
        if (!inProgressList.isEmpty()) {
            return inProgressList.get(0);
        }

        // 3. Fallback to TODO if no IN_PROGRESS exists
        List<Status> todoList = statusRepository.findByCategory(StatusCategory.TODO);
        return !todoList.isEmpty() ? todoList.get(0) : null;
    }

    private List<WorkItem> findAllDescendants(UUID rootId) {
        List<WorkItem> descendants = new ArrayList<>();
        collectDescendants(rootId, descendants);
        return descendants;
    }

    private void collectDescendants(UUID parentId, List<WorkItem> acc) {
        List<WorkItem> children = workItemRepository.findByParentId(parentId);
        if (children != null && !children.isEmpty()) {
            for (WorkItem child : children) {
                if (child.getDeletedAt() == null) {
                    acc.add(child);
                    collectDescendants(child.getId(), acc);
                }
            }
        }
    }
}
