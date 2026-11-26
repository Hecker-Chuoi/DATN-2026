package com.datn.identity_service.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;


/**
 * Gọi workspace-service (HTTP nội bộ) để trigger tự động tạo default workspace khi đăng ký / kích hoạt user.
 * Fail-safe: lỗi chỉ ghi log warn, không làm hỏng luồng tạo user (khi user đăng nhập, workspace-service có fallback tạo tại getMyWorkspaces).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WorkspaceClient {

    private final RestTemplate restTemplate;

    @Value("${app.services.workspace.url:http://localhost:8087}")
    private String workspaceServiceUrl;

    /**
     * @param userId Keycloak sub ({@code users.keycloak_id}): user id dùng chung của mọi service nghiệp vụ.
     *               KHÔNG truyền {@code users.id} của identity.
     */
    @Async
    public void createDefaultWorkspace(String userId) {
        if (userId == null || userId.isBlank()) return;
        try {
            restTemplate.postForEntity(workspaceServiceUrl + "/internal/workspaces/users/" + userId + "/default", null, Void.class);
            log.info("Dispatched default workspace creation for user {} to workspace-service", userId);
        } catch (Exception e) {
            log.warn("Could not dispatch default workspace creation for user {}: {}", userId, e.getMessage());
        }
    }
}
