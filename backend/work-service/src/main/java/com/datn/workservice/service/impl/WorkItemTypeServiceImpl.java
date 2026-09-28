package com.datn.workservice.service.impl;

import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workservice.dto.request.CreateWorkItemTypeRequest;
import com.datn.workservice.dto.request.UpdateWorkItemTypeRequest;
import com.datn.workservice.dto.response.WorkItemTypeResponse;
import com.datn.workservice.entity.Project;
import com.datn.workservice.entity.WorkItemType;
import com.datn.workservice.entity.Workflow;
import com.datn.workservice.entity.WorkflowScheme;
import com.datn.workservice.entity.WorkflowSchemeMapping;
import com.datn.workservice.repository.*;
import com.datn.workservice.service.StrictHierarchyValidator;
import com.datn.workservice.service.WorkItemTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkItemTypeServiceImpl implements WorkItemTypeService {

    private final ProjectRepository projectRepository;
    private final WorkItemTypeRepository workItemTypeRepository;
    private final WorkItemRepository workItemRepository;
    private final WorkflowRepository workflowRepository;
    private final WorkflowSchemeRepository workflowSchemeRepository;
    private final WorkflowSchemeMappingRepository workflowSchemeMappingRepository;

    @Override
    @Transactional(readOnly = true)
    public List<WorkItemTypeResponse> getProjectWorkItemTypes(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dự án với ID: " + projectId));

        if (project.getDeletedAt() != null) {
            throw new BadRequestException("Dự án đã bị xóa");
        }

        List<WorkItemType> types = workItemTypeRepository.findByProjectId(projectId);
        return types.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public WorkItemTypeResponse createWorkItemType(UUID projectId, CreateWorkItemTypeRequest request, UUID currentUserId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dự án với ID: " + projectId));

        if (project.getDeletedAt() != null) {
            throw new BadRequestException("Không thể tạo loại công việc cho dự án đã bị xóa");
        }

        String name = request.getName().trim();
        String code = request.getCode().trim().toUpperCase();

        if (workItemTypeRepository.findByProjectIdAndName(projectId, name).isPresent()) {
            throw new BadRequestException("Tên loại công việc '" + name + "' đã tồn tại trong dự án");
        }

        if (workItemTypeRepository.findByProjectIdAndCode(projectId, code).isPresent()) {
            throw new BadRequestException("Mã loại công việc '" + code + "' đã tồn tại trong dự án");
        }

        Integer hierarchyLevel = request.getHierarchyLevel();
        if (hierarchyLevel == null || (hierarchyLevel != StrictHierarchyValidator.LEVEL_EPIC
                && hierarchyLevel != StrictHierarchyValidator.LEVEL_STANDARD
                && hierarchyLevel != StrictHierarchyValidator.LEVEL_SUBTASK)) {
            throw new BadRequestException("Cấp bậc (Hierarchy Level) phải là 1 (Epic), 0 (Standard) hoặc -1 (Subtask)");
        }

        boolean isSubtask = (hierarchyLevel == StrictHierarchyValidator.LEVEL_SUBTASK);

        WorkItemType type = WorkItemType.builder()
                .projectId(projectId)
                .name(name)
                .code(code)
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .iconName(request.getIconName() != null && !request.getIconName().isBlank() ? request.getIconName().trim() : "CheckSquare")
                .colorHex(request.getColorHex() != null && !request.getColorHex().isBlank() ? request.getColorHex().trim() : "#3B82F6")
                .hierarchyLevel(hierarchyLevel)
                .isSubtask(isSubtask)
                .isSystemDefault(false)
                .build();

        WorkItemType saved = workItemTypeRepository.save(type);
        log.info("Created WorkItemType: ID={}, Code={}, Name={}, Level={}, Project={}",
                saved.getId(), saved.getCode(), saved.getName(), saved.getHierarchyLevel(), projectId);

        // Tự động gán vào WorkflowScheme của Dự án với Workflow mặc định
        List<WorkflowScheme> schemes = workflowSchemeRepository.findByProjectId(projectId);
        if (!schemes.isEmpty()) {
            WorkflowScheme scheme = schemes.get(0);
            Workflow defaultWorkflow = workflowRepository.findByProjectIdAndIsDefaultTrue(projectId)
                    .orElseGet(() -> {
                        List<Workflow> wfs = workflowRepository.findByProjectId(projectId);
                        return !wfs.isEmpty() ? wfs.get(0) : null;
                    });
            if (defaultWorkflow != null) {
                WorkflowSchemeMapping mapping = WorkflowSchemeMapping.builder()
                        .schemeId(scheme.getId())
                        .workItemTypeId(saved.getId())
                        .workflowId(defaultWorkflow.getId())
                        .build();
                workflowSchemeMappingRepository.save(mapping);
                log.info("Mapped new WorkItemType {} to Workflow {} in Scheme {}",
                        saved.getId(), defaultWorkflow.getId(), scheme.getId());
            }
        }

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public WorkItemTypeResponse updateWorkItemType(UUID projectId, UUID typeId, UpdateWorkItemTypeRequest request, UUID currentUserId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dự án với ID: " + projectId));

        if (project.getDeletedAt() != null) {
            throw new BadRequestException("Dự án đã bị xóa");
        }

        WorkItemType type = workItemTypeRepository.findById(typeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại công việc với ID: " + typeId));

        if (!projectId.equals(type.getProjectId())) {
            throw new BadRequestException("Loại công việc không thuộc dự án này");
        }

        String name = request.getName().trim();
        if (!name.equalsIgnoreCase(type.getName())) {
            if (workItemTypeRepository.findByProjectIdAndName(projectId, name).isPresent()) {
                throw new BadRequestException("Tên loại công việc '" + name + "' đã tồn tại trong dự án");
            }
            type.setName(name);
        }

        if (request.getCode() != null && !request.getCode().isBlank()) {
            String code = request.getCode().trim().toUpperCase();
            if (!code.equalsIgnoreCase(type.getCode())) {
                if (workItemTypeRepository.findByProjectIdAndCode(projectId, code).isPresent()) {
                    throw new BadRequestException("Mã loại công việc '" + code + "' đã tồn tại trong dự án");
                }
                type.setCode(code);
            }
        }

        // Kiểm tra thay đổi Hierarchy Level
        if (request.getHierarchyLevel() != null && !request.getHierarchyLevel().equals(type.getHierarchyLevel())) {
            int newLevel = request.getHierarchyLevel();
            if (newLevel != StrictHierarchyValidator.LEVEL_EPIC
                    && newLevel != StrictHierarchyValidator.LEVEL_STANDARD
                    && newLevel != StrictHierarchyValidator.LEVEL_SUBTASK) {
                throw new BadRequestException("Cấp bậc (Hierarchy Level) phải là 1 (Epic), 0 (Standard) hoặc -1 (Subtask)");
            }

            // 1. Chặn đổi cấp bậc nếu đã có công việc sử dụng (tránh vỡ cây quan hệ dữ liệu)
            if (workItemRepository.existsByTypeId(typeId)) {
                throw new BadRequestException("Không thể thay đổi cấp bậc (Level) vì loại công việc này đã có dữ liệu công việc trong dự án");
            }

            // 2. Bảo toàn tối thiểu 1 loại cho cấp bậc cũ
            int oldLevel = type.getHierarchyLevel();
            long countAtOldLevel = workItemTypeRepository.findByProjectId(projectId).stream()
                    .filter(t -> t.getHierarchyLevel() != null && t.getHierarchyLevel() == oldLevel)
                    .count();
            if (countAtOldLevel <= 1) {
                throw new BadRequestException("Không thể đổi cấp bậc loại công việc này. Dự án luôn phải duy trì ít nhất 1 loại công việc cho Cấp bậc " + oldLevel);
            }

            type.setHierarchyLevel(newLevel);
            type.setIsSubtask(newLevel == StrictHierarchyValidator.LEVEL_SUBTASK);
        }

        if (request.getDescription() != null) {
            type.setDescription(request.getDescription().trim());
        }
        if (request.getIconName() != null && !request.getIconName().isBlank()) {
            type.setIconName(request.getIconName().trim());
        }
        if (request.getColorHex() != null && !request.getColorHex().isBlank()) {
            type.setColorHex(request.getColorHex().trim());
        }

        WorkItemType updated = workItemTypeRepository.save(type);
        log.info("Updated WorkItemType ID {}: Name={}, Code={}, Level={}", updated.getId(), updated.getName(), updated.getCode(), updated.getHierarchyLevel());
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public void deleteWorkItemType(UUID projectId, UUID typeId, UUID currentUserId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dự án với ID: " + projectId));

        if (project.getDeletedAt() != null) {
            throw new BadRequestException("Dự án đã bị xóa");
        }

        WorkItemType type = workItemTypeRepository.findById(typeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại công việc với ID: " + typeId));

        if (!projectId.equals(type.getProjectId())) {
            throw new BadRequestException("Loại công việc không thuộc dự án này");
        }

        // 1. Quy tắc bất biến: Luôn phải có ít nhất 1 Level 1, 1 Level 0, 1 Level -1
        int level = type.getHierarchyLevel();
        long countAtSameLevel = workItemTypeRepository.findByProjectId(projectId).stream()
                .filter(t -> t.getHierarchyLevel() != null && t.getHierarchyLevel() == level)
                .count();

        if (countAtSameLevel <= 1) {
            String levelName = level == 1 ? "Level 1 (Epic)" : level == 0 ? "Level 0 (Standard)" : "Level -1 (Subtask)";
            throw new BadRequestException("Không thể xóa loại công việc này. Dự án luôn phải duy trì tối thiểu 1 loại công việc cho " + levelName);
        }

        // 2. Chặn xóa nếu đang có WorkItem sử dụng
        long inUseCount = workItemRepository.countByTypeId(typeId);
        if (inUseCount > 0) {
            throw new BadRequestException("Không thể xóa loại công việc '" + type.getName() + "' vì đang có " + inUseCount + " công việc đang sử dụng. Vui lòng chuyển đổi các công việc sang loại khác trước khi xóa.");
        }

        // 3. Xóa mapping trong workflow scheme
        workflowSchemeMappingRepository.deleteByWorkItemTypeId(typeId);

        // 4. Xóa WorkItemType
        workItemTypeRepository.delete(type);
        log.info("Deleted WorkItemType ID {} from project {}", typeId, projectId);
    }

    private WorkItemTypeResponse mapToResponse(WorkItemType type) {
        return WorkItemTypeResponse.builder()
                .id(type.getId())
                .projectId(type.getProjectId())
                .name(type.getName())
                .code(type.getCode())
                .description(type.getDescription())
                .iconName(type.getIconName())
                .colorHex(type.getColorHex())
                .hierarchyLevel(type.getHierarchyLevel())
                .isSubtask(type.getIsSubtask())
                .isSystemDefault(type.getIsSystemDefault())
                .createdAt(type.getCreatedAt())
                .build();
    }
}
