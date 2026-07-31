package com.datn.identity_service.controller;

import com.datn.identity_service.dto.request.UpdateRolesRequest;
import com.datn.identity_service.dto.response.ApiResponse;
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

/**
 * User-facing and admin endpoints (JWT required).
 *
 * GET /api/v1/users/me                    – Get current user's profile  (USER or ADMIN)
 * GET /api/v1/admin/users                 – List all users               (ADMIN only)
 * PUT /api/v1/admin/users/{id}/roles      – Update user roles            (ADMIN only)
 */
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // ──────────────────────────────────────────────────────────────
    // User endpoints
    // ──────────────────────────────────────────────────────────────

    /**
     * Returns the profile of the currently authenticated user.
     * Accessible by any authenticated user (USER or ADMIN role).
     */
    @GetMapping("/api/v1/users/me")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> getMyProfile(Principal principal) {
        UserResponse user = userService.getCurrentUser(principal);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    // ──────────────────────────────────────────────────────────────
    // Admin endpoints
    // ──────────────────────────────────────────────────────────────

    /**
     * Lists all registered users.
     * Restricted to ADMIN role only.
     */
    @GetMapping("/api/v1/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {
        List<UserResponse> users = userService.getAllUsers();
        return ResponseEntity.ok(ApiResponse.success(users, "Users retrieved successfully"));
    }

    /**
     * Assigns / replaces the roles for a specific user.
     * Restricted to ADMIN role only.
     * Updates roles both in Keycloak (realm roles) and in the local database.
     *
     * @param id      local database UUID of the user
     * @param request new set of roles
     */
    @PutMapping("/api/v1/admin/users/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> updateUserRoles(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRolesRequest request) {

        UserResponse updatedUser = userService.updateUserRoles(id, request);
        return ResponseEntity.ok(ApiResponse.success(updatedUser, "User roles updated successfully"));
    }
}
