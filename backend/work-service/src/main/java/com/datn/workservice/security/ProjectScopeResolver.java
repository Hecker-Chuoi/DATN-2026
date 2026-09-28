package com.datn.workservice.security;

import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workservice.repository.BoardRepository;
import com.datn.workservice.repository.SprintRepository;
import com.datn.workservice.repository.WorkItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Truy ngược projectId từ ID của một tài nguyên con (board, sprint, work item)
 * để {@link ProjectPermissionGuard} kiểm tra được quyền trên các endpoint
 * không mang projectId trên đường dẫn.
 */
@Component
@RequiredArgsConstructor
public class ProjectScopeResolver {

    private final BoardRepository boardRepository;
    private final SprintRepository sprintRepository;
    private final WorkItemRepository workItemRepository;

    /**
     * @return projectId tương ứng; ném {@link ResourceNotFoundException} nếu tài nguyên
     *         không tồn tại, để client nhận 404 thay vì 403 gây hiểu nhầm.
     */
    public UUID resolveProjectId(ProjectIdSource source, UUID id) {
        if (id == null) {
            return null;
        }
        return switch (source) {
            case PROJECT -> id;
            case BOARD -> boardRepository.findById(id)
                    .map(b -> b.getProjectId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bảng công việc: " + id));
            case SPRINT -> sprintRepository.findById(id)
                    .map(s -> s.getProjectId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Sprint: " + id));
            case WORK_ITEM -> workItemRepository.findById(id)
                    .map(w -> w.getProjectId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy công việc: " + id));
        };
    }
}
