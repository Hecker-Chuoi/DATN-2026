package com.datn.workservice.service.impl;

import com.datn.common.dto.PageResponse;
import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workservice.dto.request.CreateWorkItemRequest;
import com.datn.workservice.dto.request.UpdateWorkItemRequest;
import com.datn.workservice.dto.request.WorkItemFilterRequest;
import com.datn.workservice.dto.response.AvailableTransitionResponse;
import com.datn.workservice.dto.response.WorkItemDetailResponse;
import com.datn.workservice.dto.response.WorkItemResponse;
import com.datn.workservice.entity.*;
import com.datn.workservice.enums.StatusCategory;
import com.datn.workservice.enums.WorkItemPriority;
import com.datn.workservice.repository.*;
import com.datn.workservice.service.AtomicKeySequenceGenerator;
import com.datn.workservice.service.LexoRankService;
import com.datn.workservice.service.StrictHierarchyValidator;
import com.datn.workservice.service.WorkItemService;
import com.datn.workservice.specification.WorkItemSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkItemServiceImpl implements WorkItemService {

    private final WorkItemRepository workItemRepository;
    private final ProjectRepository projectRepository;
    private final WorkItemTypeRepository workItemTypeRepository;
    private final StatusRepository statusRepository;
    private final SprintRepository sprintRepository;
    private final ComponentRepository componentRepository;
    private final VersionRepository versionRepository;
    private final LabelRepository labelRepository;
    private final WorkItemLabelMapRepository workItemLabelMapRepository;
    private final WorkflowTransitionRepository workflowTransitionRepository;
    private final WorkflowRepository workflowRepository;

    private final AtomicKeySequenceGenerator atomicKeySequenceGenerator;
    private final LexoRankService lexoRankService;
    private final StrictHierarchyValidator strictHierarchyValidator;
    private final com.datn.workservice.service.WorkItemAuditService workItemAuditService;

    @Override
    @Transactional
    public WorkItemDetailResponse createWorkItem(UUID projectId, CreateWorkItemRequest request, UUID userId) {
        log.info("Creating WorkItem in project: {}, summary: {}", projectId, request.getSummary());

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with ID: " + projectId));

        WorkItemType type = workItemTypeRepository.findById(request.getTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("WorkItemType not found with ID: " + request.getTypeId()));

        // Validate 3-level strict hierarchy
        strictHierarchyValidator.validateHierarchy(null, request.getTypeId(), request.getParentId(), projectId);

        // Resolve initial status
        UUID initialStatusId = resolveInitialStatus(projectId);

        // Generate Atomic Issue Code
        String code = atomicKeySequenceGenerator.generateNextCode(project.getKey());

        // Calculate initial LexoRank (append at end of sprint/backlog)
        String rank = calculateInitialRankForNewItem(projectId, request.getSprintId());

        UUID reporterId = request.getReporterId() != null ? request.getReporterId() : userId;

        WorkItem workItem = WorkItem.builder()
                .projectId(projectId)
                .typeId(type.getId())
                .statusId(initialStatusId)
                .parentId(request.getParentId())
                .sprintId(request.getSprintId())
                .code(code)
                .summary(request.getSummary())
                .description(request.getDescription())
                .priority(request.getPriority() != null ? request.getPriority() : WorkItemPriority.MEDIUM)
                .reporterId(reporterId)
                .assigneeId(request.getAssigneeId())
                .storyPoints(request.getStoryPoints())
                .originalEstimateSeconds(request.getOriginalEstimateSeconds() != null ? request.getOriginalEstimateSeconds() : 0L)
                .remainingEstimateSeconds(request.getRemainingEstimateSeconds() != null ? request.getRemainingEstimateSeconds() : 0L)
                .timeSpentSeconds(0L)
                .rank(rank)
                .componentId(request.getComponentId())
                .fixVersionId(request.getFixVersionId())
                .customFields(request.getCustomFields())
                .startDate(request.getStartDate())
                .dueDate(request.getDueDate())
                .version(0)
                .build();

        WorkItem saved = workItemRepository.save(workItem);

        // Save labels
        if (request.getLabelIds() != null && !request.getLabelIds().isEmpty()) {
            List<WorkItemLabelMap> labelMaps = request.getLabelIds().stream()
                    .map(labelId -> WorkItemLabelMap.builder()
                            .workItemId(saved.getId())
                            .labelId(labelId)
                            .build())
                    .toList();
            workItemLabelMapRepository.saveAll(labelMaps);
        }

        // Record initial creation changelog
        workItemAuditService.recordCustomEvent(
                saved.getId(),
                "SUMMARY",
                null,
                "Đã tạo công việc " + saved.getCode() + ": " + saved.getSummary(),
                userId
        );

        return getWorkItemDetail(saved.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public WorkItemDetailResponse getWorkItemDetail(UUID itemId) {
        WorkItem item = workItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkItem not found with ID: " + itemId));

        return mapToDetailResponse(item);
    }

    @Override
    @Transactional(readOnly = true)
    public WorkItemDetailResponse getWorkItemByCode(String code) {
        WorkItem item = workItemRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("WorkItem not found with code: " + code));

        return mapToDetailResponse(item);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<WorkItemResponse> listWorkItems(UUID projectId, WorkItemFilterRequest filter, Pageable pageable) {
        if (filter == null) {
            filter = new WorkItemFilterRequest();
        }
        filter.setProjectId(projectId);

        Page<WorkItem> page = workItemRepository.findAll(WorkItemSpecification.withFilter(filter), pageable);

        List<WorkItemResponse> content = page.getContent().stream()
                .map(this::mapToSummaryResponse)
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
    public List<WorkItemResponse> listSubtasks(UUID parentId) {
        List<WorkItem> subtasks = workItemRepository.findByParentId(parentId);
        return subtasks.stream()
                .filter(item -> item.getDeletedAt() == null)
                .map(this::mapToSummaryResponse)
                .toList();
    }

    @Override
    @Transactional
    public WorkItemDetailResponse createSubtask(UUID parentId, com.datn.workservice.dto.request.CreateSubtaskRequest request, UUID userId) {
        WorkItem parent = workItemRepository.findById(parentId)
                .orElseThrow(() -> new ResourceNotFoundException("Parent WorkItem not found with ID: " + parentId));

        if (parent.getDeletedAt() != null) {
            throw new BadRequestException("Cannot create subtask for deleted work item: " + parent.getCode());
        }

        UUID projectId = parent.getProjectId();

        // Find Subtask type in project
        List<WorkItemType> types = workItemTypeRepository.findByProjectId(projectId);
        WorkItemType subtaskType = types.stream()
                .filter(t -> (t.getHierarchyLevel() != null && t.getHierarchyLevel() == -1)
                        || "SUBTASK".equalsIgnoreCase(t.getCode())
                        || Boolean.TRUE.equals(t.getIsSubtask()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Loại công việc Sub-task chưa được cấu hình cho dự án này"));

        CreateWorkItemRequest fullRequest = CreateWorkItemRequest.builder()
                .projectId(projectId)
                .typeId(subtaskType.getId())
                .parentId(parentId)
                .summary(request.getSummary())
                .description(request.getDescription())
                .priority(request.getPriority() != null ? request.getPriority() : WorkItemPriority.MEDIUM)
                .assigneeId(request.getAssigneeId())
                .originalEstimateSeconds(request.getOriginalEstimateSeconds())
                .dueDate(request.getDueDate())
                .sprintId(parent.getSprintId()) // inherit parent sprint
                .build();

        return createWorkItem(projectId, fullRequest, userId);
    }

    @Override
    @Transactional
    public WorkItemDetailResponse updateWorkItem(UUID itemId, UpdateWorkItemRequest request, UUID userId) {
        WorkItem item = workItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkItem not found with ID: " + itemId));

        if (item.getDeletedAt() != null) {
            throw new BadRequestException("Cannot update a deleted work item: " + item.getCode());
        }

        // Validate hierarchy if parentId or typeId changed
        UUID targetTypeId = request.getTypeId() != null ? request.getTypeId() : item.getTypeId();
        UUID targetParentId = request.getParentId() != null ? request.getParentId() : item.getParentId();
        strictHierarchyValidator.validateHierarchy(itemId, targetTypeId, targetParentId, item.getProjectId());

        // Snapshot old state for audit diffing
        WorkItem oldState = WorkItem.builder()
                .id(item.getId())
                .projectId(item.getProjectId())
                .typeId(item.getTypeId())
                .statusId(item.getStatusId())
                .parentId(item.getParentId())
                .sprintId(item.getSprintId())
                .code(item.getCode())
                .summary(item.getSummary())
                .description(item.getDescription())
                .priority(item.getPriority())
                .resolution(item.getResolution())
                .reporterId(item.getReporterId())
                .assigneeId(item.getAssigneeId())
                .storyPoints(item.getStoryPoints())
                .originalEstimateSeconds(item.getOriginalEstimateSeconds())
                .remainingEstimateSeconds(item.getRemainingEstimateSeconds())
                .timeSpentSeconds(item.getTimeSpentSeconds())
                .rank(item.getRank())
                .componentId(item.getComponentId())
                .fixVersionId(item.getFixVersionId())
                .startDate(item.getStartDate())
                .dueDate(item.getDueDate())
                .resolvedAt(item.getResolvedAt())
                .build();

        if (request.getSummary() != null && !request.getSummary().isBlank()) {
            item.setSummary(request.getSummary());
        }
        if (request.getDescription() != null) {
            item.setDescription(request.getDescription());
        }
        if (request.getTypeId() != null) {
            item.setTypeId(request.getTypeId());
        }
        if (request.getPriority() != null) {
            item.setPriority(request.getPriority());
        }
        if (request.getAssigneeId() != null) {
            item.setAssigneeId(request.getAssigneeId());
        }
        if (request.getParentId() != null) {
            item.setParentId(request.getParentId());
        }
        if (request.getSprintId() != null) {
            item.setSprintId(request.getSprintId());
        }
        if (request.getStoryPoints() != null) {
            item.setStoryPoints(request.getStoryPoints());
        }
        if (request.getOriginalEstimateSeconds() != null) {
            item.setOriginalEstimateSeconds(request.getOriginalEstimateSeconds());
        }
        if (request.getRemainingEstimateSeconds() != null) {
            item.setRemainingEstimateSeconds(request.getRemainingEstimateSeconds());
        }
        if (request.getTimeSpentSeconds() != null) {
            item.setTimeSpentSeconds(request.getTimeSpentSeconds());
        }
        if (request.getComponentId() != null) {
            item.setComponentId(request.getComponentId());
        }
        if (request.getFixVersionId() != null) {
            item.setFixVersionId(request.getFixVersionId());
        }
        if (request.getStartDate() != null) {
            item.setStartDate(request.getStartDate());
        }
        if (request.getDueDate() != null) {
            item.setDueDate(request.getDueDate());
        }
        if (request.getResolution() != null) {
            item.setResolution(request.getResolution());
            if (item.getResolvedAt() == null) {
                item.setResolvedAt(LocalDateTime.now());
            }
        }
        if (request.getCustomFields() != null) {
            item.setCustomFields(request.getCustomFields());
        }

        WorkItem saved = workItemRepository.save(item);

        // Record diff in audit trail
        workItemAuditService.recordDiff(oldState, saved, userId);

        // Update labels if specified
        if (request.getLabelIds() != null) {
            workItemLabelMapRepository.deleteByWorkItemId(itemId);
            if (!request.getLabelIds().isEmpty()) {
                List<WorkItemLabelMap> labelMaps = request.getLabelIds().stream()
                        .map(labelId -> WorkItemLabelMap.builder()
                                .workItemId(saved.getId())
                                .labelId(labelId)
                                .build())
                        .toList();
                workItemLabelMapRepository.saveAll(labelMaps);
            }
        }

        return getWorkItemDetail(saved.getId());
    }

    @Override
    @Transactional
    public void updateRank(UUID itemId, String newRank, UUID userId) {
        WorkItem item = workItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkItem not found with ID: " + itemId));

        if (newRank == null || newRank.isBlank()) {
            throw new BadRequestException("New rank cannot be empty");
        }

        item.setRank(newRank);
        workItemRepository.save(item);
    }

    @Override
    @Transactional
    public void deleteWorkItem(UUID itemId, UUID userId) {
        WorkItem item = workItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkItem not found with ID: " + itemId));

        // Soft delete
        item.setDeletedAt(LocalDateTime.now());
        workItemRepository.save(item);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private UUID resolveInitialStatus(UUID projectId) {
        List<Status> todoStatuses = statusRepository.findByCategory(StatusCategory.TODO);
        if (!todoStatuses.isEmpty()) {
            return todoStatuses.get(0).getId();
        }
        List<Status> all = statusRepository.findAll();
        if (!all.isEmpty()) {
            return all.get(0).getId();
        }
        throw new BadRequestException("No statuses defined in system");
    }

    private String calculateInitialRankForNewItem(UUID projectId, UUID sprintId) {
        List<WorkItem> items = sprintId != null
                ? workItemRepository.findBySprintIdOrderByRankAsc(sprintId)
                : workItemRepository.findActiveByProjectIdOrderByRankAsc(projectId);

        if (items.isEmpty()) {
            return lexoRankService.calculateInitialRank();
        }

        String lastRank = items.get(items.size() - 1).getRank();
        return lexoRankService.calculateAfter(lastRank);
    }

    private WorkItemResponse mapToSummaryResponse(WorkItem item) {
        WorkItemType type = workItemTypeRepository.findById(item.getTypeId()).orElse(null);
        Status status = statusRepository.findById(item.getStatusId()).orElse(null);
        Project project = projectRepository.findById(item.getProjectId()).orElse(null);

        String sprintName = null;
        if (item.getSprintId() != null) {
            sprintName = sprintRepository.findById(item.getSprintId()).map(Sprint::getName).orElse(null);
        }

        String componentName = null;
        if (item.getComponentId() != null) {
            componentName = componentRepository.findById(item.getComponentId()).map(Component::getName).orElse(null);
        }

        String fixVersionName = null;
        if (item.getFixVersionId() != null) {
            fixVersionName = versionRepository.findById(item.getFixVersionId()).map(Version::getName).orElse(null);
        }

        String parentCode = null;
        String parentSummary = null;
        if (item.getParentId() != null) {
            WorkItem parent = workItemRepository.findById(item.getParentId()).orElse(null);
            if (parent != null) {
                parentCode = parent.getCode();
                parentSummary = parent.getSummary();
            }
        }

        List<WorkItem> subtasks = workItemRepository.findByParentId(item.getId());
        int subtaskCount = subtasks.size();
        int subtaskDoneCount = 0;
        for (WorkItem sub : subtasks) {
            Status subStatus = statusRepository.findById(sub.getStatusId()).orElse(null);
            if (subStatus != null && subStatus.getCategory() == StatusCategory.DONE) {
                subtaskDoneCount++;
            }
        }

        return WorkItemResponse.builder()
                .id(item.getId())
                .projectId(item.getProjectId())
                .projectKey(project != null ? project.getKey() : null)
                .typeId(item.getTypeId())
                .typeName(type != null ? type.getName() : null)
                .typeCode(type != null ? type.getCode() : null)
                .typeColor(type != null ? type.getColorHex() : null)
                .typeIcon(type != null ? type.getIconName() : null)
                .hierarchyLevel(type != null ? type.getHierarchyLevel() : 0)
                .isSubtask(type != null ? type.getIsSubtask() : false)
                .statusId(item.getStatusId())
                .statusName(status != null ? status.getName() : null)
                .statusCategory(status != null ? status.getCategory() : null)
                .statusColorHex(status != null ? status.getColorHex() : null)
                .parentId(item.getParentId())
                .parentCode(parentCode)
                .parentSummary(parentSummary)
                .sprintId(item.getSprintId())
                .sprintName(sprintName)
                .code(item.getCode())
                .summary(item.getSummary())
                .priority(item.getPriority())
                .resolution(item.getResolution())
                .reporterId(item.getReporterId())
                .assigneeId(item.getAssigneeId())
                .storyPoints(item.getStoryPoints())
                .originalEstimateSeconds(item.getOriginalEstimateSeconds())
                .remainingEstimateSeconds(item.getRemainingEstimateSeconds())
                .timeSpentSeconds(item.getTimeSpentSeconds())
                .rank(item.getRank())
                .componentId(item.getComponentId())
                .componentName(componentName)
                .fixVersionId(item.getFixVersionId())
                .fixVersionName(fixVersionName)
                .startDate(item.getStartDate())
                .dueDate(item.getDueDate())
                .resolvedAt(item.getResolvedAt())
                .subtaskCount(subtaskCount)
                .subtaskDoneCount(subtaskDoneCount)
                .version(item.getVersion())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    private WorkItemDetailResponse mapToDetailResponse(WorkItem item) {
        WorkItemResponse summary = mapToSummaryResponse(item);

        WorkItemResponse parentDto = null;
        if (item.getParentId() != null) {
            WorkItem parent = workItemRepository.findById(item.getParentId()).orElse(null);
            if (parent != null) {
                parentDto = mapToSummaryResponse(parent);
            }
        }

        List<WorkItemResponse> subtasks = listSubtasks(item.getId());

        // Available transitions strictly for this item's project workflow
        Optional<Workflow> projectWorkflow = workflowRepository.findByProjectIdAndIsDefaultTrue(item.getProjectId());
        if (projectWorkflow.isEmpty()) {
            List<Workflow> list = workflowRepository.findByProjectId(item.getProjectId());
            if (!list.isEmpty()) {
                projectWorkflow = Optional.of(list.get(0));
            }
        }

        List<WorkflowTransition> transitions = projectWorkflow.isPresent()
                ? workflowTransitionRepository.findByWorkflowId(projectWorkflow.get().getId()).stream()
                    .filter(t -> t.getFromStatusId() == null || t.getFromStatusId().equals(item.getStatusId()))
                    .toList()
                : Collections.emptyList();

        List<AvailableTransitionResponse> availableTransitions = transitions.stream()
                .map(t -> {
                    Status toStatus = statusRepository.findById(t.getToStatusId()).orElse(null);
                    return AvailableTransitionResponse.builder()
                            .transitionId(t.getId())
                            .name(t.getName())
                            .toStatusId(t.getToStatusId())
                            .toStatusName(toStatus != null ? toStatus.getName() : null)
                            .toStatusCategory(toStatus != null ? toStatus.getCategory() : null)
                            .toStatusColorHex(toStatus != null ? toStatus.getColorHex() : null)
                            .build();
                })
                .collect(Collectors.toList());

        return WorkItemDetailResponse.builder()
                .id(summary.getId())
                .projectId(summary.getProjectId())
                .projectKey(summary.getProjectKey())
                .typeId(summary.getTypeId())
                .typeName(summary.getTypeName())
                .typeCode(summary.getTypeCode())
                .typeColor(summary.getTypeColor())
                .typeIcon(summary.getTypeIcon())
                .hierarchyLevel(summary.getHierarchyLevel())
                .isSubtask(summary.getIsSubtask())
                .statusId(summary.getStatusId())
                .statusName(summary.getStatusName())
                .statusCategory(summary.getStatusCategory())
                .statusColorHex(summary.getStatusColorHex())
                .code(summary.getCode())
                .summary(summary.getSummary())
                .description(item.getDescription())
                .priority(summary.getPriority())
                .resolution(summary.getResolution())
                .parentId(summary.getParentId())
                .parent(parentDto)
                .subtasks(subtasks)
                .sprintId(summary.getSprintId())
                .sprintName(summary.getSprintName())
                .reporterId(summary.getReporterId())
                .assigneeId(summary.getAssigneeId())
                .storyPoints(summary.getStoryPoints())
                .originalEstimateSeconds(summary.getOriginalEstimateSeconds())
                .remainingEstimateSeconds(summary.getRemainingEstimateSeconds())
                .timeSpentSeconds(summary.getTimeSpentSeconds())
                .rank(summary.getRank())
                .componentId(summary.getComponentId())
                .componentName(summary.getComponentName())
                .fixVersionId(summary.getFixVersionId())
                .fixVersionName(summary.getFixVersionName())
                .customFields(item.getCustomFields())
                .availableTransitions(availableTransitions)
                .startDate(summary.getStartDate())
                .dueDate(summary.getDueDate())
                .resolvedAt(summary.getResolvedAt())
                .version(summary.getVersion())
                .createdAt(summary.getCreatedAt())
                .updatedAt(summary.getUpdatedAt())
                .build();
    }
}
