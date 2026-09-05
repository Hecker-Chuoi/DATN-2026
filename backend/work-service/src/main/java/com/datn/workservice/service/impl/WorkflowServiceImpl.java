package com.datn.workservice.service.impl;

import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workservice.dto.request.*;
import com.datn.workservice.dto.response.*;
import com.datn.workservice.entity.*;
import com.datn.workservice.enums.StatusCategory;
import com.datn.workservice.repository.*;
import com.datn.workservice.service.WorkflowEngineService;
import com.datn.workservice.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowTransitionRepository workflowTransitionRepository;
    private final WorkflowSchemeRepository workflowSchemeRepository;
    private final WorkflowSchemeMappingRepository workflowSchemeMappingRepository;
    private final StatusRepository statusRepository;
    private final WorkItemTypeRepository workItemTypeRepository;
    private final WorkflowEngineService workflowEngineService;

    @Override
    @Transactional(readOnly = true)
    public WorkflowDetailResponse getProjectWorkflowDetail(UUID projectId) {
        // Resolve project's main default workflow
        Workflow workflow = workflowRepository.findByProjectIdAndIsDefaultTrue(projectId)
                .or(() -> {
                    List<Workflow> list = workflowRepository.findByProjectId(projectId);
                    return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
                })
                .or(workflowRepository::findFirstByIsDefaultTrue)
                .orElseThrow(() -> new ResourceNotFoundException("No workflow found for project: " + projectId));

        return getWorkflowById(workflow.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkflowResponse> getProjectWorkflows(UUID projectId) {
        List<Workflow> workflows = workflowRepository.findByProjectIdOrProjectIdIsNull(projectId);
        return workflows.stream().map(this::mapToWorkflowResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public WorkflowDetailResponse getWorkflowById(UUID workflowId) {
        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow not found with id: " + workflowId));

        List<WorkflowTransition> transitions = workflowTransitionRepository.findByWorkflowId(workflowId);

        // Fetch all statuses in system or used by transitions
        Map<UUID, Status> statusMap = statusRepository.findAll().stream()
                .collect(Collectors.toMap(Status::getId, s -> s, (s1, s2) -> s1));

        List<WorkflowTransitionResponse> transitionResponses = transitions.stream().map(t -> {
            Status from = t.getFromStatusId() != null ? statusMap.get(t.getFromStatusId()) : null;
            Status to = statusMap.get(t.getToStatusId());

            return WorkflowTransitionResponse.builder()
                    .id(t.getId())
                    .workflowId(t.getWorkflowId())
                    .fromStatusId(t.getFromStatusId())
                    .fromStatusName(from != null ? from.getName() : "Khởi tạo (Any / Initial)")
                    .fromStatusCategory(from != null ? from.getCategory() : StatusCategory.TODO)
                    .toStatusId(t.getToStatusId())
                    .toStatusName(to != null ? to.getName() : "Unknown")
                    .toStatusCategory(to != null ? to.getCategory() : StatusCategory.TODO)
                    .name(t.getName())
                    .conditionsConfig(t.getConditionsConfig())
                    .validatorsConfig(t.getValidatorsConfig())
                    .postFunctionsConfig(t.getPostFunctionsConfig())
                    .build();
        }).collect(Collectors.toList());

        List<StatusResponse> statusResponses = statusMap.values().stream()
                .sorted(Comparator.comparing(Status::getCategory).thenComparing(Status::getName))
                .map(this::mapToStatusResponse)
                .collect(Collectors.toList());

        return WorkflowDetailResponse.builder()
                .id(workflow.getId())
                .projectId(workflow.getProjectId())
                .name(workflow.getName())
                .description(workflow.getDescription())
                .isDefault(workflow.getIsDefault())
                .transitions(transitionResponses)
                .statuses(statusResponses)
                .createdAt(workflow.getCreatedAt())
                .updatedAt(workflow.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional
    public WorkflowResponse createWorkflow(CreateWorkflowRequest request) {
        Workflow workflow = Workflow.builder()
                .projectId(request.getProjectId())
                .name(request.getName().trim())
                .description(request.getDescription())
                .isDefault(Boolean.TRUE.equals(request.getIsDefault()))
                .build();

        Workflow saved = workflowRepository.save(workflow);
        log.info("Created new workflow '{}' for project '{}'", saved.getName(), saved.getProjectId());
        return mapToWorkflowResponse(saved);
    }

    @Override
    @Transactional
    public WorkflowResponse updateWorkflow(UUID workflowId, UpdateWorkflowRequest request) {
        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow not found with id: " + workflowId));

        if (request.getName() != null && !request.getName().isBlank()) {
            workflow.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            workflow.setDescription(request.getDescription());
        }
        if (request.getIsDefault() != null) {
            workflow.setIsDefault(request.getIsDefault());
        }

        Workflow updated = workflowRepository.save(workflow);
        return mapToWorkflowResponse(updated);
    }

    @Override
    @Transactional
    public WorkflowTransitionResponse createTransition(UUID workflowId, CreateWorkflowTransitionRequest request) {
        if (!workflowRepository.existsById(workflowId)) {
            throw new ResourceNotFoundException("Workflow not found with id: " + workflowId);
        }

        if (request.getFromStatusId() != null && !statusRepository.existsById(request.getFromStatusId())) {
            throw new ResourceNotFoundException("From Status not found with id: " + request.getFromStatusId());
        }

        Status toStatus = statusRepository.findById(request.getToStatusId())
                .orElseThrow(() -> new ResourceNotFoundException("To Status not found with id: " + request.getToStatusId()));

        WorkflowTransition transition = WorkflowTransition.builder()
                .workflowId(workflowId)
                .fromStatusId(request.getFromStatusId())
                .toStatusId(request.getToStatusId())
                .name(request.getName().trim())
                .conditionsConfig(request.getConditionsConfig())
                .validatorsConfig(request.getValidatorsConfig())
                .postFunctionsConfig(request.getPostFunctionsConfig())
                .build();

        WorkflowTransition saved = workflowTransitionRepository.save(transition);
        Status fromStatus = request.getFromStatusId() != null ? statusRepository.findById(request.getFromStatusId()).orElse(null) : null;

        return WorkflowTransitionResponse.builder()
                .id(saved.getId())
                .workflowId(saved.getWorkflowId())
                .fromStatusId(saved.getFromStatusId())
                .fromStatusName(fromStatus != null ? fromStatus.getName() : "Khởi tạo (Any / Initial)")
                .fromStatusCategory(fromStatus != null ? fromStatus.getCategory() : StatusCategory.TODO)
                .toStatusId(saved.getToStatusId())
                .toStatusName(toStatus.getName())
                .toStatusCategory(toStatus.getCategory())
                .name(saved.getName())
                .conditionsConfig(saved.getConditionsConfig())
                .validatorsConfig(saved.getValidatorsConfig())
                .postFunctionsConfig(saved.getPostFunctionsConfig())
                .build();
    }

    @Override
    @Transactional
    public WorkflowTransitionResponse updateTransition(UUID workflowId, UUID transitionId, UpdateWorkflowTransitionRequest request) {
        WorkflowTransition transition = workflowTransitionRepository.findById(transitionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transition not found with id: " + transitionId));

        if (!transition.getWorkflowId().equals(workflowId)) {
            throw new BadRequestException("Transition does not belong to workflow: " + workflowId);
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            transition.setName(request.getName().trim());
        }
        if (request.getFromStatusId() != null) {
            transition.setFromStatusId(request.getFromStatusId());
        }
        if (request.getToStatusId() != null) {
            transition.setToStatusId(request.getToStatusId());
        }
        if (request.getConditionsConfig() != null) {
            transition.setConditionsConfig(request.getConditionsConfig());
        }
        if (request.getValidatorsConfig() != null) {
            transition.setValidatorsConfig(request.getValidatorsConfig());
        }
        if (request.getPostFunctionsConfig() != null) {
            transition.setPostFunctionsConfig(request.getPostFunctionsConfig());
        }

        WorkflowTransition updated = workflowTransitionRepository.save(transition);
        Status fromStatus = updated.getFromStatusId() != null ? statusRepository.findById(updated.getFromStatusId()).orElse(null) : null;
        Status toStatus = statusRepository.findById(updated.getToStatusId()).orElse(null);

        return WorkflowTransitionResponse.builder()
                .id(updated.getId())
                .workflowId(updated.getWorkflowId())
                .fromStatusId(updated.getFromStatusId())
                .fromStatusName(fromStatus != null ? fromStatus.getName() : "Khởi tạo (Any / Initial)")
                .fromStatusCategory(fromStatus != null ? fromStatus.getCategory() : StatusCategory.TODO)
                .toStatusId(updated.getToStatusId())
                .toStatusName(toStatus != null ? toStatus.getName() : "Unknown")
                .toStatusCategory(toStatus != null ? toStatus.getCategory() : StatusCategory.TODO)
                .name(updated.getName())
                .conditionsConfig(updated.getConditionsConfig())
                .validatorsConfig(updated.getValidatorsConfig())
                .postFunctionsConfig(updated.getPostFunctionsConfig())
                .build();
    }

    @Override
    @Transactional
    public void deleteTransition(UUID workflowId, UUID transitionId) {
        WorkflowTransition transition = workflowTransitionRepository.findById(transitionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transition not found with id: " + transitionId));
        if (!transition.getWorkflowId().equals(workflowId)) {
            throw new BadRequestException("Transition does not belong to workflow: " + workflowId);
        }
        workflowTransitionRepository.delete(transition);
        log.info("Deleted transition '{}' (ID={}) from workflow {}", transition.getName(), transitionId, workflowId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StatusResponse> getProjectStatuses(UUID projectId) {
        return statusRepository.findAll().stream()
                .sorted(Comparator.comparing(Status::getCategory).thenComparing(Status::getName))
                .map(this::mapToStatusResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public StatusResponse createStatus(CreateStatusRequest request) {
        if (statusRepository.existsByName(request.getName().trim())) {
            throw new BadRequestException("Trạng thái với tên '" + request.getName() + "' đã tồn tại");
        }

        Status status = Status.builder()
                .name(request.getName().trim())
                .category(request.getCategory())
                .description(request.getDescription())
                .colorHex(request.getColorHex() != null ? request.getColorHex() : defaultColorForCategory(request.getCategory()))
                .build();

        Status saved = statusRepository.save(status);
        log.info("Created new status '{}' with category {}", saved.getName(), saved.getCategory());
        return mapToStatusResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public WorkflowSchemeResponse getProjectWorkflowScheme(UUID projectId) {
        List<WorkflowScheme> schemes = workflowSchemeRepository.findByProjectId(projectId);
        WorkflowScheme scheme = schemes.isEmpty() ? null : schemes.get(0);

        if (scheme == null) {
            // Return empty/default scheme representation
            return WorkflowSchemeResponse.builder()
                    .projectId(projectId)
                    .name("Sơ đồ quy trình mặc định")
                    .description("Quy trình chuẩn áp dụng cho tất cả các loại công việc")
                    .mappings(List.of())
                    .build();
        }

        List<WorkflowSchemeMapping> mappings = workflowSchemeMappingRepository.findBySchemeId(scheme.getId());
        Map<UUID, WorkItemType> typeMap = workItemTypeRepository.findAll().stream()
                .collect(Collectors.toMap(WorkItemType::getId, t -> t, (t1, t2) -> t1));
        Map<UUID, Workflow> workflowMap = workflowRepository.findAll().stream()
                .collect(Collectors.toMap(Workflow::getId, w -> w, (w1, w2) -> w1));

        List<WorkflowSchemeMappingResponse> mappingResponses = mappings.stream().map(m -> {
            WorkItemType type = typeMap.get(m.getWorkItemTypeId());
            Workflow wf = workflowMap.get(m.getWorkflowId());
            return WorkflowSchemeMappingResponse.builder()
                    .id(m.getId())
                    .schemeId(m.getSchemeId())
                    .workItemTypeId(m.getWorkItemTypeId())
                    .workItemTypeName(type != null ? type.getName() : "Unknown")
                    .workItemTypeCode(type != null ? type.getCode() : "UNKNOWN")
                    .hierarchyLevel(type != null ? type.getHierarchyLevel() : 0)
                    .workflowId(m.getWorkflowId())
                    .workflowName(wf != null ? wf.getName() : "Unknown Workflow")
                    .build();
        }).collect(Collectors.toList());

        return WorkflowSchemeResponse.builder()
                .id(scheme.getId())
                .projectId(scheme.getProjectId())
                .name(scheme.getName())
                .description(scheme.getDescription())
                .mappings(mappingResponses)
                .createdAt(scheme.getCreatedAt())
                .build();
    }

    @Override
    @Transactional
    public WorkflowSchemeResponse updateProjectWorkflowScheme(UUID projectId, UpdateWorkflowSchemeRequest request) {
        List<WorkflowScheme> schemes = workflowSchemeRepository.findByProjectId(projectId);
        WorkflowScheme scheme;
        if (schemes.isEmpty()) {
            scheme = WorkflowScheme.builder()
                    .projectId(projectId)
                    .name(request.getName() != null ? request.getName() : "Sơ đồ quy trình dự án")
                    .description(request.getDescription())
                    .build();
            scheme = workflowSchemeRepository.save(scheme);
        } else {
            scheme = schemes.get(0);
            if (request.getName() != null && !request.getName().isBlank()) {
                scheme.setName(request.getName());
            }
            if (request.getDescription() != null) {
                scheme.setDescription(request.getDescription());
            }
            scheme = workflowSchemeRepository.save(scheme);
        }

        if (request.getMappings() != null) {
            workflowSchemeMappingRepository.deleteBySchemeId(scheme.getId());
            for (CreateWorkflowSchemeRequest.WorkflowSchemeMappingItem item : request.getMappings()) {
                WorkflowSchemeMapping mapping = WorkflowSchemeMapping.builder()
                        .schemeId(scheme.getId())
                        .workItemTypeId(item.getWorkItemTypeId())
                        .workflowId(item.getWorkflowId())
                        .build();
                workflowSchemeMappingRepository.save(mapping);
            }
        }

        return getProjectWorkflowScheme(projectId);
    }

    // ─── Mapping Helpers ────────────────────────────────────────────────────────

    private WorkflowResponse mapToWorkflowResponse(Workflow w) {
        int count = workflowTransitionRepository.findByWorkflowId(w.getId()).size();
        return WorkflowResponse.builder()
                .id(w.getId())
                .projectId(w.getProjectId())
                .name(w.getName())
                .description(w.getDescription())
                .isDefault(w.getIsDefault())
                .transitionCount(count)
                .createdAt(w.getCreatedAt())
                .updatedAt(w.getUpdatedAt())
                .build();
    }

    private StatusResponse mapToStatusResponse(Status s) {
        return StatusResponse.builder()
                .id(s.getId())
                .name(s.getName())
                .category(s.getCategory())
                .description(s.getDescription())
                .colorHex(s.getColorHex())
                .createdAt(s.getCreatedAt())
                .build();
    }

    private String defaultColorForCategory(StatusCategory category) {
        if (category == null) return "#94A3B8";
        return switch (category) {
            case TODO -> "#94A3B8";
            case IN_PROGRESS -> "#3B82F6";
            case DONE -> "#10B981";
        };
    }
}
