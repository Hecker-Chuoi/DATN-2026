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
    private final WorkItemRepository workItemRepository;
    private final BoardColumnStatusRepository boardColumnStatusRepository;

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

        List<Status> projectStatuses;
        if (workflow.getProjectId() != null) {
            projectStatuses = statusRepository.findByProjectId(workflow.getProjectId());
            if (projectStatuses.isEmpty()) {
                projectStatuses = statusRepository.findAll();
            }
        } else {
            projectStatuses = statusRepository.findAll();
        }

        Map<UUID, Status> statusMap = new HashMap<>();
        for (Status s : projectStatuses) {
            statusMap.put(s.getId(), s);
        }

        // Ensure any status referenced by transitions is in statusMap even if not in projectStatuses
        Set<UUID> missingIds = new HashSet<>();
        for (WorkflowTransition t : transitions) {
            if (t.getFromStatusId() != null && !statusMap.containsKey(t.getFromStatusId())) {
                missingIds.add(t.getFromStatusId());
            }
            if (t.getToStatusId() != null && !statusMap.containsKey(t.getToStatusId())) {
                missingIds.add(t.getToStatusId());
            }
        }
        if (!missingIds.isEmpty()) {
            statusRepository.findAllById(missingIds).forEach(s -> statusMap.put(s.getId(), s));
        }

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

        List<StatusResponse> statusResponses = projectStatuses.stream()
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
        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("Quy trình không tồn tại với ID: " + workflowId));

        if (request.getToStatusId() == null) {
            throw new BadRequestException("Trạng thái đích (toStatusId) là bắt buộc");
        }

        if (request.getName() == null || request.getName().trim().isBlank()) {
            throw new BadRequestException("Tên bước chuyển không được để trống");
        }
        String trimmedName = request.getName().trim();

        if (request.getFromStatusId() != null && request.getFromStatusId().equals(request.getToStatusId())) {
            throw new BadRequestException("Trạng thái đích không được trùng với trạng thái bắt đầu");
        }

        Status fromStatus = null;
        if (request.getFromStatusId() != null) {
            fromStatus = statusRepository.findById(request.getFromStatusId())
                    .orElseThrow(() -> new ResourceNotFoundException("Trạng thái bắt đầu không tồn tại với ID: " + request.getFromStatusId()));
            if (workflow.getProjectId() != null && fromStatus.getProjectId() != null && !fromStatus.getProjectId().equals(workflow.getProjectId())) {
                throw new BadRequestException("Trạng thái bắt đầu không thuộc phạm vi dự án của quy trình này");
            }
        }

        Status toStatus = statusRepository.findById(request.getToStatusId())
                .orElseThrow(() -> new ResourceNotFoundException("Trạng thái đích không tồn tại với ID: " + request.getToStatusId()));
        if (workflow.getProjectId() != null && toStatus.getProjectId() != null && !toStatus.getProjectId().equals(workflow.getProjectId())) {
            throw new BadRequestException("Trạng thái đích không thuộc phạm vi dự án của quy trình này");
        }

        // Kiểm tra trùng lặp bước chuyển giữa 2 trạng thái trong quy trình
        boolean duplicatePath = request.getFromStatusId() == null
                ? workflowTransitionRepository.existsByWorkflowIdAndFromStatusIdIsNullAndToStatusId(workflowId, request.getToStatusId())
                : workflowTransitionRepository.existsByWorkflowIdAndFromStatusIdAndToStatusId(workflowId, request.getFromStatusId(), request.getToStatusId());
        if (duplicatePath) {
            throw new BadRequestException("Đã tồn tại bước chuyển giữa hai trạng thái này trong quy trình");
        }

        // Kiểm tra trùng tên bước chuyển trong cùng quy trình (không phân biệt hoa thường)
        if (workflowTransitionRepository.existsByWorkflowIdAndNameIgnoreCase(workflowId, trimmedName)) {
            throw new BadRequestException("Tên bước chuyển '" + trimmedName + "' đã tồn tại trong quy trình");
        }

        WorkflowTransition transition = WorkflowTransition.builder()
                .workflowId(workflowId)
                .fromStatusId(request.getFromStatusId())
                .toStatusId(request.getToStatusId())
                .name(trimmedName)
                .conditionsConfig(request.getConditionsConfig())
                .validatorsConfig(request.getValidatorsConfig())
                .postFunctionsConfig(request.getPostFunctionsConfig())
                .build();

        WorkflowTransition saved = workflowTransitionRepository.save(transition);

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
        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("Quy trình không tồn tại với ID: " + workflowId));

        WorkflowTransition transition = workflowTransitionRepository.findById(transitionId)
                .orElseThrow(() -> new ResourceNotFoundException("Bước chuyển không tồn tại với ID: " + transitionId));

        if (!transition.getWorkflowId().equals(workflowId)) {
            throw new BadRequestException("Bước chuyển không thuộc quy trình: " + workflowId);
        }

        // Xác định toStatus và fromStatus sau khi update
        UUID targetToStatusId = request.getToStatusId() != null ? request.getToStatusId() : transition.getToStatusId();
        UUID targetFromStatusId = request.getToStatusId() != null ? request.getFromStatusId() : (request.getFromStatusId() != null ? request.getFromStatusId() : transition.getFromStatusId());

        if (targetFromStatusId != null && targetFromStatusId.equals(targetToStatusId)) {
            throw new BadRequestException("Trạng thái đích không được trùng với trạng thái bắt đầu");
        }

        Status fromStatus = null;
        if (targetFromStatusId != null) {
            fromStatus = statusRepository.findById(targetFromStatusId)
                    .orElseThrow(() -> new ResourceNotFoundException("Trạng thái bắt đầu không tồn tại với ID: " + targetFromStatusId));
            if (workflow.getProjectId() != null && fromStatus.getProjectId() != null && !fromStatus.getProjectId().equals(workflow.getProjectId())) {
                throw new BadRequestException("Trạng thái bắt đầu không thuộc phạm vi dự án của quy trình này");
            }
        }

        Status toStatus = statusRepository.findById(targetToStatusId)
                .orElseThrow(() -> new ResourceNotFoundException("Trạng thái đích không tồn tại với ID: " + targetToStatusId));
        if (workflow.getProjectId() != null && toStatus.getProjectId() != null && !toStatus.getProjectId().equals(workflow.getProjectId())) {
            throw new BadRequestException("Trạng thái đích không thuộc phạm vi dự án của quy trình này");
        }

        // Kiểm tra trùng lặp đường chuyển với bước chuyển khác trong cùng quy trình
        Optional<WorkflowTransition> existingPath = targetFromStatusId == null
                ? workflowTransitionRepository.findByWorkflowIdAndFromStatusIdIsNullAndToStatusId(workflowId, targetToStatusId)
                : workflowTransitionRepository.findByWorkflowIdAndFromStatusIdAndToStatusId(workflowId, targetFromStatusId, targetToStatusId);
        if (existingPath.isPresent() && !existingPath.get().getId().equals(transitionId)) {
            throw new BadRequestException("Đã tồn tại bước chuyển giữa hai trạng thái này trong quy trình");
        }

        // Cập nhật tên nếu có truyền
        if (request.getName() != null && !request.getName().isBlank()) {
            String newName = request.getName().trim();
            Optional<WorkflowTransition> existingName = workflowTransitionRepository.findByWorkflowIdAndNameIgnoreCase(workflowId, newName);
            if (existingName.isPresent() && !existingName.get().getId().equals(transitionId)) {
                throw new BadRequestException("Tên bước chuyển '" + newName + "' đã tồn tại trong quy trình");
            }
            transition.setName(newName);
        }

        transition.setFromStatusId(targetFromStatusId);
        transition.setToStatusId(targetToStatusId);

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

        return WorkflowTransitionResponse.builder()
                .id(updated.getId())
                .workflowId(updated.getWorkflowId())
                .fromStatusId(updated.getFromStatusId())
                .fromStatusName(fromStatus != null ? fromStatus.getName() : "Khởi tạo (Any / Initial)")
                .fromStatusCategory(fromStatus != null ? fromStatus.getCategory() : StatusCategory.TODO)
                .toStatusId(updated.getToStatusId())
                .toStatusName(toStatus.getName())
                .toStatusCategory(toStatus.getCategory())
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
        List<Status> statuses = statusRepository.findByProjectId(projectId);
        if (statuses.isEmpty()) {
            statuses = statusRepository.findByProjectIdOrProjectIdIsNull(projectId);
        }
        return statuses.stream()
                .sorted(Comparator.comparing(Status::getCategory).thenComparing(Status::getName))
                .map(this::mapToStatusResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public StatusResponse createStatus(CreateStatusRequest request) {
        if (request.getProjectId() != null) {
            if (statusRepository.existsByProjectIdAndNameIgnoreCase(request.getProjectId(), request.getName().trim())) {
                throw new BadRequestException("Trạng thái với tên '" + request.getName() + "' đã tồn tại trong dự án này");
            }
        } else {
            if (statusRepository.existsByName(request.getName().trim())) {
                throw new BadRequestException("Trạng thái với tên '" + request.getName() + "' đã tồn tại");
            }
        }

        Status status = Status.builder()
                .projectId(request.getProjectId())
                .name(request.getName().trim())
                .category(request.getCategory())
                .description(request.getDescription())
                .colorHex(request.getColorHex() != null ? request.getColorHex() : defaultColorForCategory(request.getCategory()))
                .build();

        Status saved = statusRepository.save(status);
        log.info("Created new status '{}' (ID={}) for project {}", saved.getName(), saved.getId(), saved.getProjectId());
        return mapToStatusResponse(saved);
    }

    @Override
    @Transactional
    public StatusResponse updateStatus(UUID statusId, UpdateStatusRequest request) {
        Status status = statusRepository.findById(statusId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy trạng thái với id: " + statusId));

        if (request.getName() != null && !request.getName().trim().equalsIgnoreCase(status.getName())) {
            if (status.getProjectId() != null) {
                if (statusRepository.existsByProjectIdAndNameIgnoreCase(status.getProjectId(), request.getName().trim())) {
                    throw new BadRequestException("Trạng thái với tên '" + request.getName() + "' đã tồn tại trong dự án này");
                }
            } else if (statusRepository.existsByName(request.getName().trim())) {
                throw new BadRequestException("Trạng thái với tên '" + request.getName() + "' đã tồn tại");
            }
            status.setName(request.getName().trim());
        }

        if (request.getCategory() != null && request.getCategory() != status.getCategory()) {
            if (status.getProjectId() != null) {
                long count = statusRepository.countByProjectIdAndCategory(status.getProjectId(), status.getCategory());
                if (count <= 1) {
                    throw new BadRequestException("Không thể đổi danh mục của trạng thái này vì đây là trạng thái duy nhất thuộc danh mục "
                            + status.getCategory() + ". Mỗi dự án bắt buộc phải duy trì tối thiểu 1 trạng thái cho từng danh mục (TODO, IN_PROGRESS, DONE)!");
                }
            }
            status.setCategory(request.getCategory());
        }

        if (request.getDescription() != null) {
            status.setDescription(request.getDescription().trim());
        }

        if (request.getColorHex() != null) {
            status.setColorHex(request.getColorHex());
        }

        Status saved = statusRepository.save(status);
        log.info("Updated status ID {} ('{}')", statusId, saved.getName());
        return mapToStatusResponse(saved);
    }

    @Override
    @Transactional
    public void deleteStatus(UUID statusId) {
        Status status = statusRepository.findById(statusId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy trạng thái với id: " + statusId));

        // 1. Minimum 1 per category check
        if (status.getProjectId() != null) {
            long count = statusRepository.countByProjectIdAndCategory(status.getProjectId(), status.getCategory());
            if (count <= 1) {
                throw new BadRequestException("Không thể xóa trạng thái cuối cùng thuộc danh mục " + status.getCategory()
                        + ". Dự án bắt buộc phải duy trì tối thiểu 1 trạng thái cho mỗi danh mục (TODO, IN_PROGRESS, DONE)!");
            }
        }

        // 2. WorkItem in-use check
        long inUseCount = workItemRepository.countByStatusId(statusId);
        if (inUseCount > 0) {
            throw new BadRequestException("Không thể xóa trạng thái '" + status.getName()
                    + "' vì đang có " + inUseCount + " công việc đang áp dụng trạng thái này!");
        }

        // 3. Delete any transitions using this status
        List<WorkflowTransition> fromTransitions = workflowTransitionRepository.findByFromStatusId(statusId);
        List<WorkflowTransition> toTransitions = workflowTransitionRepository.findByToStatusId(statusId);
        if (!fromTransitions.isEmpty()) {
            workflowTransitionRepository.deleteAll(fromTransitions);
        }
        if (!toTransitions.isEmpty()) {
            workflowTransitionRepository.deleteAll(toTransitions);
        }

        // 4. Delete board column status mappings
        boardColumnStatusRepository.deleteByStatusId(statusId);

        statusRepository.delete(status);
        log.info("Deleted status ID {} ('{}') from project {}", statusId, status.getName(), status.getProjectId());
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
                .projectId(s.getProjectId())
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
