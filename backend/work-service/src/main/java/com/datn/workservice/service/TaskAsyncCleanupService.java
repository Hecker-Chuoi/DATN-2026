package com.datn.workservice.service;

import com.datn.workservice.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskAsyncCleanupService {

    private final TaskRepository taskRepository;

    /**
     * Chạy ngầm (bất đồng bộ): Khi xóa thành viên khỏi Team, tự động hủy gán (Unassign)
     * tất cả các tác vụ mà thành viên đó đang phụ trách trong các dự án của Team.
     */
    @Async
    @Transactional
    public void unassignTasksForRemovedMember(UUID teamId, UUID userId) {
        log.info("Background async task: unassigning tasks for removed member userId={} in teamId={}", userId, teamId);
        try {
            int count = taskRepository.unassignTasksForTeamMember(teamId, userId);
            log.info("Background async task completed: unassigned {} tasks for member userId={} in teamId={}", count, userId, teamId);
        } catch (Exception e) {
            log.error("Failed to unassign tasks for member userId={} in teamId={}: {}", userId, teamId, e.getMessage(), e);
        }
    }
}
