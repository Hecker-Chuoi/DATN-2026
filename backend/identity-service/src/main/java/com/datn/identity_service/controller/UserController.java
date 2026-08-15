package com.datn.identity_service.controller;

import com.datn.identity_service.dto.request.UpdateRolesRequest;
import com.datn.common.dto.ApiResponse;
import com.datn.identity_service.dto.response.UserResponse;
import com.datn.identity_service.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
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
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {
        List<UserResponse> users = userService.getAllUsers();
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
}
