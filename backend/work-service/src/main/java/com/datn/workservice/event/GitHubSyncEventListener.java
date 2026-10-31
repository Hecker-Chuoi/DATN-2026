package com.datn.workservice.event;

import com.datn.workservice.service.GitHubIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubSyncEventListener {

    private final GitHubIntegrationService gitHubIntegrationService;

    /**
     * Lắng nghe sự kiện chuyển đổi trạng thái của WorkItem để đồng bộ 2 chiều sang GitHub Issue
     */
    @EventListener
    public void onWorkItemStatusChanged(WorkItemStatusChangedEvent event) {
        if (event == null || event.getWorkItemId() == null) {
            return;
        }

        try {
            gitHubIntegrationService.syncWorkItemStatusToGitHub(
                    event.getWorkItemId(),
                    event.getOldCategory(),
                    event.getNewCategory()
            );
        } catch (Exception e) {
            log.warn("Lỗi khi đồng bộ trạng thái WorkItem sang GitHub: {}", e.getMessage(), e);
        }
    }
}
