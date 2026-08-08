package com.datn.identity_service.controller;

import com.datn.identity_service.dto.request.GoogleCallbackRequest;
import com.datn.identity_service.dto.request.LoginRequest;
import com.datn.identity_service.dto.request.RegisterRequest;
import com.datn.identity_service.dto.response.ApiResponse;
import com.datn.identity_service.dto.response.TokenResponse;
import com.datn.identity_service.dto.response.UserResponse;
import com.datn.identity_service.service.KeycloakService;
import com.datn.identity_service.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String DEFAULT_REDIRECT_URI = "http://localhost:5173/auth/callback";
    private static final String COOKIE_NAME          = "access_token";

    private final UserService     userService;
    private final KeycloakService keycloakService;


    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @Valid @RequestBody RegisterRequest request) {

        UserResponse user = userService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(user, "User registered successfully"));
    }


    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        TokenResponse token = keycloakService.login(request.getUsername(), request.getPassword());
        ResponseCookie cookie = buildAccessTokenCookie(token.getAccessToken(), token.getExpiresIn());
        
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.success(token, "Login successful"));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        ResponseCookie cookie = buildClearCookie();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.success(null, "Logged out successfully"));
    }

    /**
     * Step 1: Return the Keycloak Authorization URL so the frontend can redirect the user.
     */
    @GetMapping("/google/init")
    public ResponseEntity<ApiResponse<Map<String, String>>> googleInit(
            @RequestParam(name = "redirectUri", required = false) String redirectUri) {

        String uri = (redirectUri != null && !redirectUri.isBlank())
                ? redirectUri
                : DEFAULT_REDIRECT_URI;

        String authorizationUrl = keycloakService.getGoogleAuthorizationUrl(uri);
        return ResponseEntity.ok(
                ApiResponse.success(Map.of("authorizationUrl", authorizationUrl), "OK"));
    }

    /**
     * Step 2: Exchange authorization code → JWT, sync user to DB, set httpOnly cookie.
     */
    @PostMapping("/google/callback")
    public ResponseEntity<ApiResponse<TokenResponse>> googleCallback(
            @Valid @RequestBody GoogleCallbackRequest request) {

        TokenResponse tokenResponse = keycloakService.exchangeCodeForToken(
                request.getCode(), request.getRedirectUri());

        Map<String, Object> userInfo = keycloakService.getUserInfoFromKeycloak(
                tokenResponse.getAccessToken());

        userService.findOrCreateGoogleUser(userInfo);

        ResponseCookie cookie = buildAccessTokenCookie(tokenResponse.getAccessToken(), tokenResponse.getExpiresIn());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.success(tokenResponse, "Google login successful"));
    }

    // ──────────────────────────────────────────────────────────────
    // Cookie helpers
    // ──────────────────────────────────────────────────────────────

    /**
     * Builds the {@code access_token} httpOnly cookie.
     * HttpOnly=true  → not readable by JavaScript (XSS protection)
     * SameSite=Lax   → sent on top-level navigations (needed for Google OAuth redirect)
     * Secure=false   → set to true in production (HTTPS)
     */
    private ResponseCookie buildAccessTokenCookie(String token, long maxAgeSeconds) {
        return ResponseCookie.from(COOKIE_NAME, token)
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(maxAgeSeconds)
                .sameSite("Lax")
                .build();
    }

    /** Builds cookie with Max-Age=0 → browser deletes it immediately. */
    private ResponseCookie buildClearCookie() {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
    }
}
