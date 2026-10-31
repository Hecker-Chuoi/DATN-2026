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
     * Xử lý callback OAuth từ GitHub sau khi người dùng chấp thuận cấp quyền
     */
    ProjectGitHubStatusResponse handleOAuthCallback(UUID projectId, String code, UUID currentUserId);

    /**
     * Lấy danh sách Repository của tài khoản GitHub đã kết nối
     */
    List<GitHubRepoDto> getAvailableRepositories(UUID currentUserId);

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
    void syncWorkItemStatusToGitHub(UUID workItemId, com.datn.workservice.enums.StatusCategory oldCategory, com.datn.workservice.enums.StatusCategory newCategory);

    /**
     * Hủy liên kết tài khoản GitHub của người dùng (xóa token)
     */
    void unlinkUserAccount(UUID currentUserId);
}
