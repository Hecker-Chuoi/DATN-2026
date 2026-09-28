package com.datn.workspaceservice.service;

import com.datn.workspaceservice.dto.request.CreateWorkspaceRequest;
import com.datn.workspaceservice.dto.request.UpdateWorkspaceRequest;
import com.datn.workspaceservice.dto.response.WorkspaceResponse;

import java.util.List;
import java.util.UUID;

public interface WorkspaceService {

    /** Tạo workspace và gán người tạo làm OWNER trong cùng một transaction. */
    WorkspaceResponse createWorkspace(CreateWorkspaceRequest request, UUID currentUserId);

    /**
     * Chỉ những workspace mà user có hàng membership, và chưa soft-delete.
     * Cố tình KHÔNG có endpoint liệt kê toàn cục - TeamController.listAllTeams
     * trả về mọi team trong DB cho bất kỳ ai đã đăng nhập, đó là lỗi không lặp lại.
     */
    List<WorkspaceResponse> getMyWorkspaces(UUID currentUserId);

    WorkspaceResponse getWorkspaceById(UUID workspaceId, UUID currentUserId);

    WorkspaceResponse updateWorkspace(UUID workspaceId, UpdateWorkspaceRequest request, UUID currentUserId);

    /** Soft delete: chỉ set deletedAt, giữ nguyên toàn bộ hàng membership. */
    void deleteWorkspace(UUID workspaceId, UUID currentUserId);

    WorkspaceResponse restoreWorkspace(UUID workspaceId, UUID currentUserId);

    /** Danh sách id workspace của user - dùng cho work-service scope query. */
    List<UUID> getWorkspaceIdsOfUser(UUID userId);

    boolean existsActive(UUID workspaceId);
}
