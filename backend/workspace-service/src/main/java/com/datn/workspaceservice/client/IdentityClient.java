package com.datn.workspaceservice.client;

import com.datn.common.dto.ApiResponse;
import com.datn.workspaceservice.client.dto.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * Feign Client tới các endpoint nội bộ của identity-service (/internal/users/**).
 * Chạy trong mạng backend, không cần kèm JWT của người dùng.
 */
@FeignClient(
        name = "identity-service",
        url = "${app.services.identity.url:http://localhost:8082}"
)
public interface IdentityClient {

    /**
     * Lấy user theo id. Endpoint này resolve được CẢ users.id LẪN keycloak_id
     * (UserService.getUserById làm findById(id).or(findByKeycloakId(id))) - đó là
     * cơ chế mà CurrentUserResolver dựa vào để đổi Keycloak sub thành users.id.
     */
    @GetMapping("/internal/users/{id}")
    ApiResponse<UserResponse> getUserById(@PathVariable("id") UUID id);

    /** Tìm user theo email hoặc username - dùng khi mời thành viên vào workspace. */
    @GetMapping("/internal/users/by-identifier")
    ApiResponse<UserResponse> getUserByIdentifier(@RequestParam("identifier") String identifier);

    /**
     * Hydrate nhiều user trong MỘT lần gọi. Mọi chỗ render danh sách thành viên
     * phải dùng endpoint này, không bao giờ gọi getUserById theo từng member.
     */
    @PostMapping("/internal/users/batch")
    ApiResponse<List<UserResponse>> getUsersByIds(@RequestBody List<UUID> ids);
}
