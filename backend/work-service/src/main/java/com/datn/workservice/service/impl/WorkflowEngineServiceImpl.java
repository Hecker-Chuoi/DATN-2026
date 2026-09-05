package com.datn.workservice.service.impl;

import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workservice.dto.model.WorkflowConditionConfig;
import com.datn.workservice.dto.model.WorkflowPostFunctionConfig;
import com.datn.workservice.dto.model.WorkflowValidatorConfig;
import com.datn.workservice.dto.request.ExecuteTransitionRequest;
import com.datn.workservice.dto.response.AvailableTransitionResponse;
import com.datn.workservice.entity.*;
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
                    List<WorkItem> subtasks = workItemRepository.findByParentId(workItem.getId());
                    if (subtasks != null && !subtasks.isEmpty()) {
                        long incomplete = subtasks.stream()
                                .filter(st -> st.getDeletedAt() == null)
                                .map(st -> statusRepository.findById(st.getStatusId()).orElse(null))
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
}
