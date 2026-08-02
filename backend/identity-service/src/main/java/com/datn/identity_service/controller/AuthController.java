package com.datn.identity_service.controller;

import com.datn.identity_service.dto.request.LoginRequest;
import com.datn.identity_service.dto.request.RegisterRequest;
import com.datn.identity_service.dto.response.ApiResponse;
import com.datn.identity_service.dto.response.TokenResponse;
import com.datn.identity_service.dto.response.UserResponse;
import com.datn.identity_service.service.KeycloakService;
import com.datn.identity_service.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final KeycloakService keycloakService;

    /**
     * Register a new user.
     * Creates the user in Keycloak, assigns role 'USER', and syncs to the local DB.
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @Valid @RequestBody RegisterRequest request) {

        UserResponse user = userService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(user, "User registered successfully"));
    }

    /**
     * Login with username & password.
     * Exchanges credentials against Keycloak and returns the JWT token pair.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        TokenResponse token = keycloakService.login(request.getUsername(), request.getPassword());
        return ResponseEntity.ok(ApiResponse.success(token, "Login successful"));
    }
}
