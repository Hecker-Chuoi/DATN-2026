package com.datn.identity_service.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.identity_service.dto.response.UserResponse;
import com.datn.identity_service.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller dành riêng cho các microservices nội bộ (work-service, notification-service, ai-service)
 * gọi trực tiếp qua OpenFeign mà không cần truyền JWT Token người dùng.
 */
@Slf4j
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;

    /**
     * Lấy chi tiết thông tin User theo ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable("id") UUID id) {
        log.debug("Internal API: Fetching user by id={}", id);
        UserResponse user = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    /**
     * Tìm kiếm User theo Email (dùng khi mời thành viên vào Workspace/Project)
     */
    @GetMapping("/by-email")
    public ResponseEntity<ApiResponse<UserResponse>> getUserByEmail(@RequestParam("email") String email) {
        log.debug("Internal API: Fetching user by email={}", email);
        UserResponse user = userService.getUserByEmail(email);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    /**
     * Tìm kiếm User theo Username (dùng khi mời thành viên vào Workspace/Project)
     */
    @GetMapping("/by-username")
    public ResponseEntity<ApiResponse<UserResponse>> getUserByUsername(@RequestParam("username") String username) {
        log.debug("Internal API: Fetching user by username={}", username);
        UserResponse user = userService.getUserByUsername(username);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    /**
     * Tìm kiếm User theo Email hoặc Username (dùng khi mời thành viên vào Workspace/Project)
     */
    @GetMapping("/by-identifier")
    public ResponseEntity<ApiResponse<UserResponse>> getUserByIdentifier(@RequestParam("identifier") String identifier) {
        log.debug("Internal API: Fetching user by identifier={}", identifier);
        UserResponse user = userService.getUserByIdentifier(identifier);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    /**
     * Truy vấn thông tin hàng loạt User theo danh sách ID
     */
    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getUsersByIds(@RequestBody List<UUID> ids) {
        log.debug("Internal API: Batch fetching {} users", ids != null ? ids.size() : 0);
        List<UserResponse> users = userService.getUsersByIds(ids);
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    /**
     * Kiểm tra nhanh xem User ID có tồn tại hay không
     */
    @GetMapping("/exists/{id}")
    public ResponseEntity<ApiResponse<Boolean>> checkUserExists(@PathVariable("id") UUID id) {
        boolean exists = userService.existsById(id);
        return ResponseEntity.ok(ApiResponse.success(exists));
    }
}
