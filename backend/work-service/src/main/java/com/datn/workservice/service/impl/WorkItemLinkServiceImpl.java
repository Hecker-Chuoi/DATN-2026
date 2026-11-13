package com.datn.workservice.service.impl;

import com.datn.common.dto.ApiResponse;
import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workservice.client.IdentityClient;
import com.datn.workservice.client.dto.UserResponse;
import com.datn.workservice.dto.request.CreateWorkItemLinkRequest;
import com.datn.workservice.dto.response.WorkItemLinkResponse;
import com.datn.workservice.entity.*;
import com.datn.workservice.enums.LinkType;
import com.datn.workservice.enums.StatusCategory;
import com.datn.workservice.enums.WorkItemResolution;
import com.datn.workservice.repository.*;
import com.datn.workservice.service.DependencyCycleDetector;
import com.datn.workservice.service.WorkItemAuditService;
import com.datn.workservice.service.WorkItemCompletionGate;
import com.datn.workservice.service.WorkItemLinkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkItemLinkServiceImpl implements WorkItemLinkService {

    private final WorkItemLinkRepository workItemLinkRepository;
    private final WorkItemRepository workItemRepository;
    private final WorkItemTypeRepository workItemTypeRepository;
    private final StatusRepository statusRepository;
    private final DependencyCycleDetector dependencyCycleDetector;
    private final WorkItemAuditService auditService;
    private final IdentityClient identityClient;
    private final WorkItemCompletionGate completionGate;

    @Override
    @Transactional(readOnly = true)
    public List<WorkItemLinkResponse> getLinks(UUID workItemId) {
        WorkItem currentItem = workItemRepository.findById(workItemId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkItem not found with ID: " + workItemId));

        List<WorkItemLink> links = workItemLinkRepository.findBySourceItemIdOrTargetItemId(workItemId, workItemId);
        if (links.isEmpty()) {
            return Collections.emptyList();
        }

        // Collect other work item IDs and creator IDs
        Set<UUID> otherItemIds = new HashSet<>();
        Set<UUID> creatorIds = new HashSet<>();

        for (WorkItemLink link : links) {
            UUID otherId = link.getSourceItemId().equals(workItemId) ? link.getTargetItemId() : link.getSourceItemId();
            otherItemIds.add(otherId);
            if (link.getCreatedBy() != null) {
                creatorIds.add(link.getCreatedBy());
            }
        }

        Map<UUID, WorkItem> itemMap = workItemRepository.findAllById(otherItemIds).stream()
                .collect(Collectors.toMap(WorkItem::getId, item -> item));

        Map<UUID, UserResponse> userMap = fetchUsersBatch(creatorIds);

        List<WorkItemLinkResponse> responses = new ArrayList<>();
        for (WorkItemLink link : links) {
            boolean isOutward = link.getSourceItemId().equals(workItemId);
            UUID linkedItemId = isOutward ? link.getTargetItemId() : link.getSourceItemId();
            WorkItem linkedItem = itemMap.get(linkedItemId);

            if (linkedItem == null || linkedItem.getDeletedAt() != null) {
                continue;
            }

            WorkItemType type = workItemTypeRepository.findById(linkedItem.getTypeId()).orElse(null);
            Status status = statusRepository.findById(linkedItem.getStatusId()).orElse(null);
            UserResponse creator = userMap.get(link.getCreatedBy());
            String createdByName = creator != null
                    ? (creator.getFullName() != null && !creator.getFullName().isBlank() ? creator.getFullName() : creator.getUsername())
                    : "Người dùng (" + link.getCreatedBy().toString().substring(0, 8) + ")";

            String relationshipDesc = resolveRelationshipDescription(link.getLinkType(), isOutward);

            responses.add(WorkItemLinkResponse.builder()
                    .id(link.getId())
                    .sourceItemId(link.getSourceItemId())
                    .targetItemId(link.getTargetItemId())
                    .linkType(link.getLinkType())
                    .relationshipDescription(relationshipDesc)
                    .isOutward(isOutward)
                    .linkedItemId(linkedItem.getId())
                    .linkedItemCode(linkedItem.getCode())
                    .linkedItemSummary(linkedItem.getSummary())
                    .linkedItemTypeId(linkedItem.getTypeId())
                    .linkedItemTypeName(type != null ? type.getName() : null)
                    .linkedItemTypeCode(type != null ? type.getCode() : null)
                    .linkedItemTypeColor(type != null ? type.getColorHex() : null)
                    .linkedItemTypeIcon(type != null ? type.getIconName() : null)
                    .linkedItemStatusId(linkedItem.getStatusId())
                    .linkedItemStatusName(status != null ? status.getName() : null)
                    .linkedItemStatusCategory(status != null ? status.getCategory() : null)
                    .linkedItemStatusColorHex(status != null ? status.getColorHex() : null)
                    .linkedItemPriority(linkedItem.getPriority())
                    .createdBy(link.getCreatedBy())
                    .createdByName(createdByName)
                    .createdAt(link.getCreatedAt())
                    .build());
        }

        return responses;
    }

    @Override
    @Transactional
    public WorkItemLinkResponse createLink(UUID sourceItemId, CreateWorkItemLinkRequest request, UUID userId) {
        if (sourceItemId.equals(request.getTargetItemId())) {
            throw new BadRequestException("Không thể tự liên kết công việc với chính nó");
        }

        WorkItem sourceItem = workItemRepository.findById(sourceItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Source WorkItem not found: " + sourceItemId));
        WorkItem targetItem = workItemRepository.findById(request.getTargetItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Target WorkItem not found: " + request.getTargetItemId()));

        if (sourceItem.getDeletedAt() != null || targetItem.getDeletedAt() != null) {
            throw new BadRequestException("Không thể liên kết với công việc đã bị xóa");
        }

        LinkType linkType = request.getLinkType();

        // Rule 1: Anti-Self-Parent link check (cannot link parent to child as related links)
        if ((sourceItem.getParentId() != null && sourceItem.getParentId().equals(targetItem.getId())) ||
                (targetItem.getParentId() != null && targetItem.getParentId().equals(sourceItem.getId()))) {
            throw new BadRequestException("Không thể tạo liên kết thủ công giữa công việc Cha và Con ruột (đã được liên kết qua phân cấp Subtask)");
        }

        // Rule 2: Scale Mismatch check (cannot link BLOCKS directly between Epic and Subtask)
        WorkItemType sourceItemType = workItemTypeRepository.findById(sourceItem.getTypeId()).orElse(null);
        WorkItemType targetItemType = workItemTypeRepository.findById(targetItem.getTypeId()).orElse(null);
        if (linkType == LinkType.BLOCKS && sourceItemType != null && targetItemType != null) {
            int srcLvl = sourceItemType.getHierarchyLevel() != null ? sourceItemType.getHierarchyLevel() : 0;
            int tgtLvl = targetItemType.getHierarchyLevel() != null ? targetItemType.getHierarchyLevel() : 0;
            if ((srcLvl == 1 && tgtLvl == -1) || (srcLvl == -1 && tgtLvl == 1)) {
                throw new BadRequestException("Không thể tạo liên kết BLOCKS trực tiếp giữa Epic và Subtask do chênh lệch cấp độ (Scale Mismatch). Vui lòng liên kết ở cấp độ Story/Task.");
            }
        }

        // Check if link already exists
        if (workItemLinkRepository.existsBySourceItemIdAndTargetItemIdAndLinkType(sourceItemId, targetItem.getId(), linkType)) {
            throw new BadRequestException("Liên kết giữa hai công việc này đã tồn tại");
        }

        // Validate Directed Cycle when BLOCKS or CAUSES
        if (linkType == LinkType.BLOCKS || linkType == LinkType.CAUSES) {
            dependencyCycleDetector.validateNoCycle(sourceItemId, targetItem.getId(), linkType);
        }

        // Rule 3: Auto-resolve source item on DUPLICATES (B duplicates A -> mark B as DONE with DUPLICATE resolution)
        if (linkType == LinkType.DUPLICATES) {
            Status sourceStatus = statusRepository.findById(sourceItem.getStatusId()).orElse(null);
            if (sourceStatus == null || sourceStatus.getCategory() != StatusCategory.DONE) {
                // Ưu tiên trạng thái DONE của chính dự án, chỉ dùng trạng thái toàn cục khi dự án không có
                List<Status> doneStatuses = statusRepository.findByProjectId(sourceItem.getProjectId()).stream()
                        .filter(s -> s.getCategory() == StatusCategory.DONE)
                        .toList();
                if (doneStatuses.isEmpty()) {
                    doneStatuses = statusRepository.findByCategory(StatusCategory.DONE);
                }
                if (!doneStatuses.isEmpty() && !completionGate.canConfirmCompletion(sourceItem.getProjectId(), userId)) {
                    // BR-W03: không phải Admin dự án -> chỉ tạo yêu cầu "Tạm hoàn thành" với kết quả DUPLICATE
                    completionGate.submit(sourceItem, null, sourceStatus, doneStatuses.get(0),
                            WorkItemResolution.DUPLICATE.name(), false,
                            "Trùng lặp với " + targetItem.getCode(), userId);
                } else if (!doneStatuses.isEmpty()) {
                    Status doneStatus = doneStatuses.get(0);
                    sourceItem.setStatusId(doneStatus.getId());
                    sourceItem.setResolution(WorkItemResolution.DUPLICATE);
                    sourceItem.setResolvedAt(LocalDateTime.now());
                    workItemRepository.save(sourceItem);
                    completionGate.onStatusChanged(sourceItem, doneStatus, true, userId);
                    auditService.recordCustomEvent(
                            sourceItemId,
                            "STATUS",
                            sourceStatus != null ? sourceStatus.getName() : null,
                            "Tự động hoàn thành với kết quả DUPLICATE do phát hiện trùng lặp với " + targetItem.getCode(),
                            userId
                    );
                }
            }
        }

        WorkItemLink link = WorkItemLink.builder()
                .sourceItemId(sourceItemId)
                .targetItemId(targetItem.getId())
                .linkType(linkType)
                .createdBy(userId)
                .build();

        WorkItemLink saved = workItemLinkRepository.save(link);
        log.info("Created link '{}' between {} and {} by user {}", linkType, sourceItem.getCode(), targetItem.getCode(), userId);

        // Audit Trail on both items
        String relDesc = resolveRelationshipDescription(linkType, true);
        auditService.recordCustomEvent(
                sourceItemId,
                "LINK",
                null,
                "Đã thêm liên kết: " + relDesc + " " + targetItem.getCode() + " - " + targetItem.getSummary(),
                userId
        );

        String reverseDesc = resolveRelationshipDescription(linkType, false);
        auditService.recordCustomEvent(
                targetItem.getId(),
                "LINK",
                null,
                "Đã thêm liên kết: " + reverseDesc + " " + sourceItem.getCode() + " - " + sourceItem.getSummary(),
                userId
        );

        WorkItemType targetType = workItemTypeRepository.findById(targetItem.getTypeId()).orElse(null);
        Status targetStatus = statusRepository.findById(targetItem.getStatusId()).orElse(null);
        UserResponse creator = fetchSingleUser(userId);
        String createdByName = creator != null
                ? (creator.getFullName() != null && !creator.getFullName().isBlank() ? creator.getFullName() : creator.getUsername())
                : "Người dùng (" + userId.toString().substring(0, 8) + ")";

        return WorkItemLinkResponse.builder()
                .id(saved.getId())
                .sourceItemId(sourceItemId)
                .targetItemId(targetItem.getId())
                .linkType(linkType)
                .relationshipDescription(relDesc)
                .isOutward(true)
                .linkedItemId(targetItem.getId())
                .linkedItemCode(targetItem.getCode())
                .linkedItemSummary(targetItem.getSummary())
                .linkedItemTypeId(targetItem.getTypeId())
                .linkedItemTypeName(targetType != null ? targetType.getName() : null)
                .linkedItemTypeCode(targetType != null ? targetType.getCode() : null)
                .linkedItemTypeColor(targetType != null ? targetType.getColorHex() : null)
                .linkedItemTypeIcon(targetType != null ? targetType.getIconName() : null)
                .linkedItemStatusId(targetItem.getStatusId())
                .linkedItemStatusName(targetStatus != null ? targetStatus.getName() : null)
                .linkedItemStatusCategory(targetStatus != null ? targetStatus.getCategory() : null)
                .linkedItemStatusColorHex(targetStatus != null ? targetStatus.getColorHex() : null)
                .linkedItemPriority(targetItem.getPriority())
                .createdBy(userId)
                .createdByName(createdByName)
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Override
    @Transactional
    public void deleteLink(UUID workItemId, UUID linkId, UUID userId) {
        WorkItemLink link = workItemLinkRepository.findById(linkId)
                .orElseThrow(() -> new ResourceNotFoundException("Link not found with ID: " + linkId));

        if (!link.getSourceItemId().equals(workItemId) && !link.getTargetItemId().equals(workItemId)) {
            throw new BadRequestException("Liên kết không thuộc về công việc này");
        }

        UUID otherItemId = link.getSourceItemId().equals(workItemId) ? link.getTargetItemId() : link.getSourceItemId();
        WorkItem otherItem = workItemRepository.findById(otherItemId).orElse(null);

        workItemLinkRepository.delete(link);
        log.info("Deleted link ID {} on WorkItem {}", linkId, workItemId);

        // Audit trail
        auditService.recordCustomEvent(
                workItemId,
                "LINK",
                "Liên kết: " + (otherItem != null ? otherItem.getCode() : otherItemId.toString()),
                "Đã xóa liên kết",
                userId
        );
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String resolveRelationshipDescription(LinkType linkType, boolean isOutward) {
        if (linkType == null) return "Liên kết với";
        return switch (linkType) {
            case BLOCKS -> isOutward ? "Chặn (blocks)" : "Bị chặn bởi (is blocked by)";
            case RELATES_TO -> "Liên quan đến (relates to)";
            case DUPLICATES -> isOutward ? "Trùng lặp với (duplicates)" : "Bị trùng bởi (is duplicated by)";
            case CAUSES -> isOutward ? "Gây ra (causes)" : "Gây ra bởi (is caused by)";
        };
    }

    private UserResponse fetchSingleUser(UUID userId) {
        if (userId == null) return null;
        try {
            ApiResponse<UserResponse> res = identityClient.getUserById(userId);
            if (res != null && res.getData() != null) {
                return res.getData();
            }
        } catch (Exception e) {
            log.debug("Failed to fetch user {}: {}", userId, e.getMessage());
        }
        return null;
    }

    private Map<UUID, UserResponse> fetchUsersBatch(Set<UUID> userIds) {
        if (userIds.isEmpty()) return Collections.emptyMap();
        try {
            ApiResponse<List<UserResponse>> res = identityClient.getUsersByIds(new ArrayList<>(userIds));
            if (res != null && res.getData() != null) {
                return res.getData().stream()
                        .filter(u -> u.getId() != null)
                        .collect(Collectors.toMap(UserResponse::getId, u -> u, (a, b) -> a));
            }
        } catch (Exception e) {
            log.warn("Failed to fetch user batch for links: {}", e.getMessage());
        }
        return Collections.emptyMap();
    }
}
