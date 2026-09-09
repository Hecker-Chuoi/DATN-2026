package com.datn.workservice.service.impl;

import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workservice.entity.WorkItem;
import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.repository.WorkItemRepository;
import com.datn.workservice.security.SecurityUtils;
import com.datn.workservice.service.ProjectAccessService;
import com.datn.workservice.service.ProjectMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectAccessServiceImpl implements ProjectAccessService {

    private final ProjectMemberService projectMemberService;
    private final WorkItemRepository workItemRepository;

    @Override
    public UUID currentUserId() {
        return SecurityUtils.getCurrentUserId();
    }

    @Override
    public boolean isGlobalAdmin() {
        return SecurityUtils.isGlobalAdmin();
    }

    @Override
    public boolean hasProjectPermission(UUID projectId, PermissionKey key) {
        if (projectId == null || key == null) return false;
        if (isGlobalAdmin()) return true;
        UUID userId = SecurityUtils.getCurrentUserIdOptional().orElse(null);
        if (userId == null) return false;
        return projectMemberService.hasPermission(projectId, userId, key);
    }

    @Override
    public void assertProjectPermission(UUID projectId, PermissionKey key) {
        if (projectId == null) {
            throw new AccessDeniedException("Không xác định được dự án để kiểm tra quyền");
        }
        if (!hasProjectPermission(projectId, key)) {
            log.warn("Access denied: user {} lacks {} on project {}",
                    SecurityUtils.getCurrentUserIdOptional().orElse(null), key, projectId);
            throw new AccessDeniedException(
                    "Bạn không có quyền thực hiện thao tác này (" + key.name() + ") trong dự án");
        }
    }

    @Override
    public void assertTaskPermission(UUID workItemId, PermissionKey key) {
        WorkItem item = workItemRepository.findById(workItemId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkItem not found with ID: " + workItemId));
        assertProjectPermission(item.getProjectId(), key);
    }

    @Override
    public void assertGlobalAdmin() {
        if (!isGlobalAdmin()) {
            throw new AccessDeniedException("Chỉ Quản trị viên hệ thống (ADMIN) mới có quyền thực hiện thao tác này");
        }
    }
}
