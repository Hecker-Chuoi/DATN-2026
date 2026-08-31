package com.datn.identity_service.controller;
 
import com.datn.identity_service.dto.request.UpdateRolesRequest;
import com.datn.identity_service.dto.request.AdminCreateUserRequest;
import com.datn.identity_service.dto.request.ResetPasswordRequest;
import com.datn.common.dto.ApiResponse;
import com.datn.common.dto.PageResponse;
import com.datn.identity_service.dto.response.UserResponse;
import com.datn.identity_service.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
 
import java.security.Principal;
import java.util.UUID;
 
@RestController
@RequiredArgsConstructor
public class UserController {
 
    private final UserService userService;
 
    @GetMapping("/api/v1/users/me")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> getMyProfile(Principal principal) {
        UserResponse user = userService.getCurrentUser(principal);
        return ResponseEntity.ok(ApiResponse.success(user));
    }
 
    @GetMapping("/api/v1/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> getAllUsers(
            @RequestParam(value = "search", required = false) String search,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<UserResponse> users = userService.getAllUsers(search, pageable);
        return ResponseEntity.ok(ApiResponse.success(users, "Users retrieved successfully"));
    }
 
    @PutMapping("/api/v1/admin/users/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> updateUserRoles(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateRolesRequest request) {
 
        UserResponse updatedUser = userService.updateUserRoles(id, request);
        return ResponseEntity.ok(ApiResponse.success(updatedUser, "User roles updated successfully"));
    }
 
    @PostMapping("/api/v1/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> adminCreateUser(
            @Valid @RequestBody AdminCreateUserRequest request) {
        UserResponse createdUser = userService.adminCreateUser(request);
        return ResponseEntity.ok(ApiResponse.success(createdUser, "User created successfully by admin"));
    }
 
    @PutMapping("/api/v1/admin/users/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(id, request);
        return ResponseEntity.ok(ApiResponse.success(null, "User password reset successfully"));
    }
 
    @DeleteMapping("/api/v1/admin/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable("id") UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.success(null, "User deleted successfully"));
    }
}
