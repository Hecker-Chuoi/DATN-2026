package com.datn.workservice.service.impl;

import com.datn.common.dto.ApiResponse;
import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workservice.client.IdentityClient;
import com.datn.workservice.client.dto.UserResponse;
import com.datn.workservice.dto.response.CompletionRequestResponse;
import com.datn.workservice.entity.Status;
import com.datn.workservice.entity.WorkItem;
import com.datn.workservice.entity.WorkItemComment;
import com.datn.workservice.entity.WorkItemCompletionRequest;
import com.datn.workservice.enums.CompletionRequestStatus;
import com.datn.workservice.repository.StatusRepository;
import com.datn.workservice.repository.WorkItemCommentRepository;
import com.datn.workservice.repository.WorkItemCompletionRequestRepository;
import com.datn.workservice.repository.WorkItemRepository;
import com.datn.workservice.service.WorkItemCompletionGate;
import com.datn.workservice.service.WorkItemCompletionService;
import com.datn.workservice.service.WorkflowEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkItemCompletionServiceImpl implements WorkItemCompletionService {

    private final WorkItemCompletionRequestRepository requestRepository;
    private final WorkItemCompletionGate completionGate;
    private final WorkflowEngineService workflowEngineService;
    private final WorkItemRepository workItemRepository;
    private final StatusRepository statusRepository;
    private final WorkItemCommentRepository commentRepository;
    private final IdentityClient identityClient;
    private final com.datn.workservice.notification.WorkItemNotifier workItemNotifier;

    @Override
    @Transactional(readOnly = true)
    public List<CompletionRequestResponse> listPending(UUID projectId) {
        return toResponses(requestRepository.findByProjectIdAndStatusOrderByRequestedAtAsc(
                projectId, CompletionRequestStatus.PENDING), true);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CompletionRequestResponse> getPending(UUID workItemId) {
        return completionGate.findPending(workItemId)
                .flatMap(req -> toResponses(List.of(req), false).stream().findFirst());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompletionRequestResponse> getHistory(UUID workItemId) {
        return toResponses(requestRepository.findByWorkItemIdOrderByRequestedAtDesc(workItemId), false);
    }

    @Override
    @Transactional
    public WorkItem approve(UUID workItemId, UUID adminId) {
        WorkItem item = loadActiveItem(workItemId);
        assertCanConfirm(item, adminId);
        WorkItemCompletionRequest request = requirePending(workItemId);

        // Engine chạy lại bước chuyển; khi trạng thái vào DONE, gate tự đóng yêu cầu thành APPROVED
        WorkItem updated = workflowEngineService.executeApprovedCompletion(workItemId, request, adminId);
        log.info("Admin {} xác nhận hoàn thành '{}' (yêu cầu của {})", adminId, item.getCode(), request.getRequestedBy());
        return updated;
    }

    @Override
    @Transactional
    public CompletionRequestResponse reject(UUID workItemId, String reason, UUID adminId) {
        WorkItem item = loadActiveItem(workItemId);
        assertCanConfirm(item, adminId);
        WorkItemCompletionRequest request = requirePending(workItemId);

        String trimmed = reason == null || reason.isBlank() ? null : reason.trim();
        completionGate.close(request, CompletionRequestStatus.REJECTED, adminId,
                trimmed != null ? trimmed : "Admin dự án từ chối xác nhận hoàn thành");
        requestRepository.save(request);

        completionGate.recordChangelog(workItemId, WorkItemCompletionGate.PENDING_LABEL,
                "Bị từ chối" + (trimmed != null ? ": " + trimmed : ""), adminId);

        // Để người làm thấy ngay lý do trong phần thảo luận
        if (trimmed != null) {
            commentRepository.save(WorkItemComment.builder()
                    .workItemId(workItemId)
                    .authorId(adminId)
                    .body("Từ chối xác nhận hoàn thành: " + trimmed)
                    .build());
        }
        log.info("Admin {} từ chối yêu cầu hoàn thành '{}'", adminId, item.getCode());
        workItemNotifier.completionRejected(item, request, trimmed, adminId); // C3
        return toResponses(List.of(request), false).get(0);
    }

    @Override
    @Transactional
    public CompletionRequestResponse cancel(UUID workItemId, UUID userId) {
        WorkItem item = loadActiveItem(workItemId);
        WorkItemCompletionRequest request = requirePending(workItemId);

        boolean isRequester = Objects.equals(userId, request.getRequestedBy());
        if (!isRequester && !completionGate.canConfirmCompletion(item.getProjectId(), userId)) {
            throw new AccessDeniedException("Chỉ người gửi yêu cầu hoặc Admin dự án mới được hủy yêu cầu này");
        }

        completionGate.close(request, CompletionRequestStatus.CANCELLED, userId,
                isRequester ? "Người gửi rút lại yêu cầu" : "Admin dự án hủy yêu cầu");
        requestRepository.save(request);
        completionGate.recordChangelog(workItemId, WorkItemCompletionGate.PENDING_LABEL,
                "Đã hủy yêu cầu xác nhận hoàn thành", userId);
        // C5 cho người gửi (Admin rút lại), hoặc chỉ đóng C1 của Quản lý (người gửi tự rút lại)
        workItemNotifier.completionCancelled(item, request, "CANCELLED_BY_ADMIN", null, userId);
        return toResponses(List.of(request), false).get(0);
    }

    // ───────────────────────────── helpers ─────────────────────────────

    private WorkItem loadActiveItem(UUID workItemId) {
        WorkItem item = workItemRepository.findById(workItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy công việc: " + workItemId));
        if (item.getDeletedAt() != null) {
            throw new BadRequestException("Công việc đã bị xóa");
        }
        return item;
    }

    private void assertCanConfirm(WorkItem item, UUID userId) {
        if (!completionGate.canConfirmCompletion(item.getProjectId(), userId)) {
            throw new AccessDeniedException("Chỉ Admin dự án mới được xác nhận hoặc từ chối hoàn thành công việc");
        }
    }

    private WorkItemCompletionRequest requirePending(UUID workItemId) {
        return completionGate.findPending(workItemId)
                .orElseThrow(() -> new BadRequestException("Công việc không có yêu cầu xác nhận hoàn thành nào đang chờ"));
    }

    private List<CompletionRequestResponse> toResponses(List<WorkItemCompletionRequest> requests, boolean skipDeletedItems) {
        if (requests == null || requests.isEmpty()) {
            return Collections.emptyList();
        }

        Set<UUID> itemIds = requests.stream().map(WorkItemCompletionRequest::getWorkItemId).collect(Collectors.toSet());
        Map<UUID, WorkItem> itemById = workItemRepository.findAllById(itemIds).stream()
                .collect(Collectors.toMap(WorkItem::getId, Function.identity(), (a, b) -> a));

        Set<UUID> statusIds = new HashSet<>();
        requests.forEach(r -> statusIds.add(r.getTargetStatusId()));
        itemById.values().forEach(i -> statusIds.add(i.getStatusId()));
        statusIds.remove(null);
        Map<UUID, Status> statusById = statusRepository.findAllById(statusIds).stream()
                .collect(Collectors.toMap(Status::getId, Function.identity(), (a, b) -> a));

        Set<UUID> userIds = new HashSet<>();
        requests.forEach(r -> {
            userIds.add(r.getRequestedBy());
            userIds.add(r.getDecidedBy());
        });
        itemById.values().forEach(i -> userIds.add(i.getAssigneeId()));
        userIds.remove(null);
        Map<UUID, UserResponse> userById = fetchUsers(userIds);

        List<CompletionRequestResponse> result = new ArrayList<>();
        for (WorkItemCompletionRequest r : requests) {
            WorkItem item = itemById.get(r.getWorkItemId());
            if (skipDeletedItems && (item == null || item.getDeletedAt() != null)) {
                continue;
            }
            Status target = statusById.get(r.getTargetStatusId());
            Status current = item == null ? null : statusById.get(item.getStatusId());
            UUID assigneeId = item == null ? null : item.getAssigneeId();

            result.add(CompletionRequestResponse.builder()
                    .id(r.getId())
                    .projectId(r.getProjectId())
                    .status(r.getStatus())
                    .workItemId(r.getWorkItemId())
                    .workItemCode(item == null ? null : item.getCode())
                    .workItemSummary(item == null ? null : item.getSummary())
                    .currentStatusId(item == null ? null : item.getStatusId())
                    .currentStatusName(current == null ? null : current.getName())
                    .assigneeId(assigneeId)
                    .assigneeName(displayName(userById, assigneeId))
                    .targetStatusId(r.getTargetStatusId())
                    .targetStatusName(target == null ? null : target.getName())
                    .resolution(r.getResolution())
                    .resolveAllSubtasks(r.getResolveAllSubtasks())
                    .requestNote(r.getRequestNote())
                    .requestedBy(r.getRequestedBy())
                    .requestedByName(displayName(userById, r.getRequestedBy()))
                    .requestedAt(r.getRequestedAt())
                    .decidedBy(r.getDecidedBy())
                    .decidedByName(displayName(userById, r.getDecidedBy()))
                    .decidedAt(r.getDecidedAt())
                    .decisionNote(r.getDecisionNote())
                    .build());
        }
        return result;
    }

    private Map<UUID, UserResponse> fetchUsers(Set<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            ApiResponse<List<UserResponse>> res = identityClient.getUsersByIds(new ArrayList<>(userIds));
            if (res != null && res.getData() != null) {
                return res.getData().stream()
                        .filter(u -> u.getId() != null)
                        .collect(Collectors.toMap(UserResponse::getId, u -> u, (a, b) -> a));
            }
        } catch (Exception e) {
            // Lỗi identity không được làm hỏng màn hình duyệt
            log.warn("Không lấy được tên người dùng cho yêu cầu xác nhận hoàn thành: {}", e.getMessage());
        }
        return Collections.emptyMap();
    }

    private String displayName(Map<UUID, UserResponse> userById, UUID userId) {
        if (userId == null) {
            return null;
        }
        UserResponse u = userById.get(userId);
        if (u == null) {
            return "Người dùng (" + userId.toString().substring(0, 8) + ")";
        }
        return u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getUsername();
    }
}
