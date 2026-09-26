package com.datn.workservice.client;

import com.datn.common.dto.ApiResponse;
import com.datn.workservice.client.dto.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * Feign Client giao tiếp trực tiếp với Identity Service qua các endpoint nội bộ (/internal/users/**)
 * Hoạt động hoàn toàn tự do trong mạng backend mà không cần truyền kèm JWT Token người dùng.
 */
@FeignClient(
        name = "identity-service",
        url = "${app.services.identity.url:http://localhost:9992}"
)
public interface IdentityClient {

    /**
     * Lấy thông tin chi tiết một User theo ID
     */
    @GetMapping("/internal/users/{id}")
    ApiResponse<UserResponse> getUserById(@PathVariable("id") UUID id);

    /**
     * Tìm kiếm User theo Email (dùng khi mời thành viên vào Team)
     */
    @GetMapping("/internal/users/by-email")
    ApiResponse<UserResponse> getUserByEmail(@RequestParam("email") String email);

    /**
     * Tìm kiếm User theo Username (dùng khi mời thành viên vào Team)
     */
    @GetMapping("/internal/users/by-username")
    ApiResponse<UserResponse> getUserByUsername(@RequestParam("username") String username);

    /**
     * Tìm kiếm User theo Email hoặc Username (dùng khi mời thành viên vào Team)
     */
    @GetMapping("/internal/users/by-identifier")
    ApiResponse<UserResponse> getUserByIdentifier(@RequestParam("identifier") String identifier);

    /**
     * Lấy thông tin danh sách nhiều User theo danh sách ID (dùng render Avatar/Tên trên Kanban Board)
     */
    @PostMapping("/internal/users/batch")
    ApiResponse<List<UserResponse>> getUsersByIds(@RequestBody List<UUID> ids);

    /**
     * Kiểm tra xem User ID có tồn tại trong hệ thống hay không
     */
    @GetMapping("/internal/users/exists/{id}")
    ApiResponse<Boolean> checkUserExists(@PathVariable("id") UUID id);
}
