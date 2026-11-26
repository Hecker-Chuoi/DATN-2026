package com.datn.workservice.event;

import com.datn.workservice.service.GitHubIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubSyncEventListener {

    private final GitHubIntegrationService gitHubIntegrationService;

    /**
     * Đồng bộ 2 chiều sang GitHub Issue SAU KHI transaction đổi trạng thái đã commit và trên luồng riêng:
     * <ul>
     *   <li>đổi trạng thái bị rollback thì GitHub không bị đóng/mở nhầm;</li>
     *   <li>gọi HTTP sang GitHub không giữ transaction DB và không làm người dùng phải chờ khi kéo thẻ;</li>
     *   <li>lỗi phía GitHub không thể đánh dấu rollback-only transaction nghiệp vụ.</li>
     * </ul>
     * {@code fallbackExecution = true}: vẫn chạy nếu sự kiện được phát ngoài transaction.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onWorkItemStatusChanged(WorkItemStatusChangedEvent event) {
        if (event == null || event.getWorkItemId() == null) {
            return;
        }

        try {
            gitHubIntegrationService.syncWorkItemStatusToGitHub(
                    event.getWorkItemId(),
                    event.getOldCategory(),
                    event.getNewCategory(),
                    event.getChangedByUserId()
            );
        } catch (Exception e) {
            log.warn("Lỗi khi đồng bộ trạng thái WorkItem sang GitHub: {}", e.getMessage(), e);
        }
    }

    /** Đổi người được giao trong DATN thì đổi assignee của GitHub Issue đã liên kết (sau commit, luồng riêng). */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onWorkItemAssigneeChanged(WorkItemAssigneeChangedEvent event) {
        if (event == null || event.getWorkItemId() == null) {
            return;
        }
        try {
            gitHubIntegrationService.syncAssigneeToGitHub(
                    event.getWorkItemId(), event.getNewAssigneeId(), event.getChangedByUserId());
        } catch (Exception e) {
            log.warn("Lỗi khi đồng bộ người được giao sang GitHub: {}", e.getMessage(), e);
        }
    }
}
