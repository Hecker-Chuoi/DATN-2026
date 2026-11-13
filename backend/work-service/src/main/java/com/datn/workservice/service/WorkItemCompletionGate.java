package com.datn.workservice.service;

import com.datn.workservice.entity.Status;
import com.datn.workservice.entity.WorkItem;
import com.datn.workservice.entity.WorkItemChangelog;
import com.datn.workservice.entity.WorkItemCompletionRequest;
import com.datn.workservice.enums.CompletionRequestStatus;
import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.repository.WorkItemChangelogRepository;
import com.datn.workservice.repository.WorkItemCompletionRequestRepository;
import com.datn.workservice.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Cổng "Tạm hoàn thành": quyết định ai được xác nhận Hoàn thành và quản lý vòng đời
 * yêu cầu xác nhận (BR-W03..BR-W07).
 *
 * Tách riêng khỏi {@link WorkItemCompletionService} để WorkflowEngine và WorkItemLinkService
 * dùng được mà không tạo phụ thuộc vòng (service xác nhận lại gọi ngược WorkflowEngine).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkItemCompletionGate {

    /** fieldName trong work_item_changelogs cho các sự kiện xác nhận hoàn thành. */
    public static final String CHANGELOG_FIELD = "COMPLETION";
    public static final String PENDING_LABEL = "Tạm hoàn thành – chờ Admin xác nhận";

    private final WorkItemCompletionRequestRepository requestRepository;
    private final WorkItemChangelogRepository changelogRepository;
    private final ProjectMemberService projectMemberService;

    /**
     * Người dùng có được XÁC NHẬN hoàn thành trong dự án hay không: Global ADMIN, Lead/người tạo dự án
     * hoặc vai trò có quyền PROJECT_ADMIN (mặc định là ADMINISTRATOR).
     */
    public boolean canConfirmCompletion(UUID projectId, UUID userId) {
        if (userId == null) {
            return false;
        }
        boolean actingAsCurrentUser = SecurityUtils.getCurrentUserIdOptional()
                .map(userId::equals)
                .orElse(false);
        if (actingAsCurrentUser && SecurityUtils.isGlobalAdmin()) {
            return true;
        }
        return projectMemberService.hasPermission(projectId, userId, PermissionKey.PROJECT_ADMIN);
    }

    public Optional<WorkItemCompletionRequest> findPending(UUID workItemId) {
        if (workItemId == null) {
            return Optional.empty();
        }
        return requestRepository.findFirstByWorkItemIdAndStatus(workItemId, CompletionRequestStatus.PENDING);
    }

    /**
     * Tạo yêu cầu PENDING. Nếu công việc đã có yêu cầu đang chờ thì yêu cầu cũ bị thay thế (CANCELLED).
     * Không đổi trạng thái công việc.
     */
    public WorkItemCompletionRequest submit(WorkItem item, UUID transitionId, Status currentStatus, Status targetStatus,
                                            String resolution, boolean resolveAllSubtasks, String note, UUID requesterId) {
        findPending(item.getId()).ifPresent(old -> {
            close(old, CompletionRequestStatus.CANCELLED, requesterId, "Được thay thế bởi yêu cầu mới");
            // Flush ngay để chỉ mục duy nhất "1 PENDING / công việc" không vướng bản ghi cũ
            requestRepository.saveAndFlush(old);
        });

        WorkItemCompletionRequest request = requestRepository.save(WorkItemCompletionRequest.builder()
                .projectId(item.getProjectId())
                .workItemId(item.getId())
                .transitionId(transitionId)
                .targetStatusId(targetStatus.getId())
                .resolution(resolution)
                .resolveAllSubtasks(resolveAllSubtasks)
                .requestNote(note)
                .status(CompletionRequestStatus.PENDING)
                .requestedBy(requesterId)
                .requestedAt(LocalDateTime.now())
                .build());

        recordChangelog(item.getId(),
                currentStatus != null ? currentStatus.getName() : null,
                PENDING_LABEL + " (→ " + targetStatus.getName() + ")",
                requesterId);

        log.info("WorkItem '{}': user {} gửi yêu cầu xác nhận hoàn thành → '{}'",
                item.getCode(), requesterId, targetStatus.getName());
        return request;
    }

    /**
     * Gọi sau MỌI lần trạng thái công việc thực sự thay đổi. Nếu đang có yêu cầu chờ:
     * vào DONE → APPROVED (người chuyển là Admin), sang trạng thái khác → CANCELLED.
     *
     * @return yêu cầu vừa được đóng (nếu có)
     */
    public Optional<WorkItemCompletionRequest> onStatusChanged(WorkItem item, Status newStatus, boolean enteredDone, UUID actorId) {
        Optional<WorkItemCompletionRequest> pending = findPending(item.getId());
        pending.ifPresent(req -> {
            if (enteredDone) {
                close(req, CompletionRequestStatus.APPROVED, actorId, "Đã xác nhận hoàn thành");
                recordChangelog(item.getId(), PENDING_LABEL, "Admin đã xác nhận hoàn thành", actorId);
            } else {
                String statusName = newStatus != null ? newStatus.getName() : "trạng thái khác";
                close(req, CompletionRequestStatus.CANCELLED, actorId,
                        "Trạng thái đổi sang '" + statusName + "' trước khi được xác nhận");
                recordChangelog(item.getId(), PENDING_LABEL, "Yêu cầu tự hủy do chuyển sang '" + statusName + "'", actorId);
            }
            requestRepository.save(req);
        });
        return pending;
    }

    public void close(WorkItemCompletionRequest request, CompletionRequestStatus outcome, UUID actorId, String note) {
        request.setStatus(outcome);
        request.setDecidedBy(actorId);
        request.setDecidedAt(LocalDateTime.now());
        request.setDecisionNote(note);
    }

    public void recordChangelog(UUID workItemId, String oldValue, String newValue, UUID actorId) {
        if (workItemId == null || actorId == null) {
            return;
        }
        changelogRepository.save(WorkItemChangelog.builder()
                .workItemId(workItemId)
                .fieldName(CHANGELOG_FIELD)
                .oldValue(oldValue)
                .newValue(newValue)
                .changedBy(actorId)
                .changedAt(LocalDateTime.now())
                .build());
    }
}
