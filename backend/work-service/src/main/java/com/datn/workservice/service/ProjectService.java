package com.datn.workservice.service;

import com.datn.common.dto.PageResponse;
import com.datn.workservice.dto.request.CreateProjectRequest;
import com.datn.workservice.dto.request.UpdateProjectRequest;
import com.datn.workservice.dto.response.ProjectDetailResponse;
import com.datn.workservice.dto.response.ProjectResponse;
import com.datn.workservice.entity.Project;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ProjectService {

    /**
     * Creates a new project with auto-seeding of ecosystem (Sequence, Types, Workflow, Board, Roles).
     */
    ProjectResponse createProject(CreateProjectRequest request, UUID currentUserId);

    /**
     * Retrieves full project details by ID with lead user info, types, and roles.
     */
    ProjectDetailResponse getProjectById(UUID projectId);

    /**
     * Retrieves full project details by unique Project Key.
     */
    ProjectDetailResponse getProjectByKey(String key);

    /**
     * Lists and filters projects within workspace with pagination and search.
     *
     * <p>Hai tầng lọc bổ sung cho nhau:
     * <ul>
     *   <li>{@code workspaceIds} — phạm vi workspace được phép xem. Controller giải
     *       phạm vi này (một workspace đã xác minh quyền, hoặc toàn bộ workspace của
     *       người gọi). Danh sách rỗng cho ra trang trống.</li>
     *   <li>{@code includeAll} — false thì chỉ trả về dự án người dùng truy cập được
     *       (member, lead hoặc người tạo); true dành cho Global ADMIN.</li>
     * </ul>
     */
    PageResponse<ProjectResponse> getAllProjects(
            List<UUID> workspaceIds,
            String search,
            Boolean isTemplate,
            Pageable pageable,
            UUID currentUserId,
            boolean includeAll
    );

    /**
     * Lists template projects accessible to the current user.
     */
    PageResponse<ProjectResponse> getTemplates(
            List<UUID> workspaceIds,
            Pageable pageable,
            UUID currentUserId,
            boolean includeAll
    );

    /**
     * Updates an existing project.
     */
    ProjectResponse updateProject(UUID projectId, UpdateProjectRequest request, UUID currentUserId);

    /**
     * Quy tắc tạo dự án dùng chung (tạo mới, tạo từ mẫu, lưu thành mẫu, nhân bản):
     * người dùng có vai trò MEMBER trở lên trong Workspace hoặc Global ADMIN.
     */
    void assertCanCreateProject(UUID workspaceId, UUID currentUserId, UUID leadUserId);

    ProjectResponse toProjectResponse(Project project);

    /**
     * Soft-deletes a project by setting deletedAt.
     */
    void deleteProject(UUID projectId, UUID currentUserId);

    /**
     * Restores a soft-deleted project.
     */
    ProjectResponse restoreProject(UUID projectId, UUID currentUserId);
}
