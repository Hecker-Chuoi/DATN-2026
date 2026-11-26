package com.datn.workservice.service;

import com.datn.workservice.dto.github.*;

import java.util.List;
import java.util.UUID;

public interface GitHubIntegrationService {

    /**
     * Lấy URL chuyển hướng người dùng sang trang cấp quyền OAuth của GitHub
     */
    String getOAuthAuthorizeUrl(UUID projectId, UUID currentUserId);

    /**
     * Xử lý callback OAuth từ GitHub sau khi người dùng chấp thuận cấp quyền.
     * {@code state} phải là giá trị do {@link #getOAuthAuthorizeUrl} phát cho đúng người dùng và dự án.
     */
    ProjectGitHubStatusResponse handleOAuthCallback(UUID projectId, String code, String state, UUID currentUserId);

    /**
     * Lấy danh sách Repository của tài khoản GitHub đã kết nối
     */
    List<GitHubRepoDto> getAvailableRepositories(UUID currentUserId);

    /**
     * Lấy danh sách nhánh của một Repository (nhánh mặc định trên GitHub đứng đầu)
     */
    List<GitHubBranchDto> listBranches(UUID projectId, String repoFullName, UUID currentUserId);

    /**
     * Liên kết một Repository cụ thể với Project hiện tại
     */
    ProjectGitHubStatusResponse connectRepository(UUID projectId, ConnectRepoRequest request, UUID currentUserId);

    /**
     * Ngắt kết nối Repository khỏi Project
     */
    void disconnectRepository(UUID projectId, UUID currentUserId);

    /**
     * Lấy trạng thái tích hợp GitHub hiện tại của Project
     */
    ProjectGitHubStatusResponse getProjectGitHubStatus(UUID projectId, UUID currentUserId);

    /**
     * Cập nhật các thiết lập đồng bộ tự động hóa (Sync config rules)
     */
    ProjectGitHubStatusResponse updateSyncConfig(UUID projectId, UpdateGitHubSyncConfigRequest request, UUID currentUserId);

    /**
     * Đồng bộ Issues từ GitHub Repository về làm Task trên Board (Manual Sync / Pull)
     */
    GitHubSyncResultResponse syncIssues(UUID projectId, UUID currentUserId);

    /**
     * Tiếp nhận và xử lý sự kiện Webhook từ GitHub
     */
    void handleWebhook(String event, String signature, String rawPayload);

    /**
     * Đồng bộ thay đổi trạng thái WorkItem sang GitHub Issue (Reverse Sync)
     */
    void syncWorkItemStatusToGitHub(UUID workItemId, com.datn.workservice.enums.StatusCategory oldCategory,
                                    com.datn.workservice.enums.StatusCategory newCategory, UUID changedByUserId);

    /**
     * Đổi assignee của GitHub Issue đã liên kết theo người được giao mới của Task
     * ({@code newAssigneeId} null hoặc chưa liên kết GitHub thì gỡ assignee).
     */
    void syncAssigneeToGitHub(UUID workItemId, UUID newAssigneeId, UUID changedByUserId);

    /**
     * Hủy liên kết tài khoản GitHub của người dùng (xóa token)
     */
    void unlinkUserAccount(UUID currentUserId);

    /** Bảng "Phát triển" của Task cho người quản lý: PR / Issue đã liên kết và quy ước đặt tên cho dev. */
    com.datn.workservice.dto.github.WorkItemDevelopmentResponse getDevelopmentInfo(UUID workItemId);

    /** Tạo GitHub Issue cho Task (bấm tay) và gắn liên kết ISSUE hai chiều. */
    com.datn.workservice.dto.github.WorkItemDevelopmentResponse createIssue(UUID workItemId, UUID currentUserId);
}
