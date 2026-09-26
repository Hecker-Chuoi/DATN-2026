package com.datn.identity_service.controller;

import com.datn.identity_service.dto.request.GoogleCallbackRequest;
import com.datn.identity_service.dto.request.LoginRequest;
import com.datn.identity_service.dto.request.RegisterRequest;
import com.datn.common.dto.ApiResponse;
import com.datn.identity_service.dto.response.TokenResponse;
import com.datn.identity_service.dto.response.UserResponse;
import com.datn.identity_service.service.AccountVerificationService;
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
    private final AccountVerificationService accountVerificationService;


    // ─── Xác thực email & quên mật khẩu (public, email gửi qua notification-service) ───

    /** Link trong email xác thực trỏ tới frontend /verify-email?token=..., frontend gọi endpoint này. */
    @GetMapping("/verify-email")
    public ResponseEntity<ApiResponse<UserResponse>> verifyEmail(@RequestParam("token") String token) {
        UserResponse user = UserResponse.from(accountVerificationService.verifyEmail(token));
        return ResponseEntity.ok(ApiResponse.success(user, "Xác thực email thành công. Bạn có thể đăng nhập."));
    }

    /** body: { "identifier": "<email hoặc username>" } */
    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<Void>> resendVerification(@RequestBody Map<String, String> body) {
        String identifier = firstNonBlank(body.get("identifier"), body.get("email"), body.get("username"));
        if (identifier == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Vui lòng nhập email hoặc username"));
        }
        accountVerificationService.resendVerification(identifier.trim());
        return ResponseEntity.ok(ApiResponse.success(null, "Đã gửi lại email xác thực. Vui lòng kiểm tra hộp thư."));
    }

    /** body: { "email": "..." } — luôn trả 200 để không lộ email nào tồn tại */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Vui lòng nhập email"));
        }
        accountVerificationService.requestPasswordReset(email.trim());
        return ResponseEntity.ok(ApiResponse.success(null,
                "Nếu email tồn tại trong hệ thống, chúng tôi đã gửi hướng dẫn đặt lại mật khẩu."));
    }

    /** body: { "token": "...", "newPassword": "..." } */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        String newPassword = body.get("newPassword");
        if (newPassword == null || newPassword.length() < 6) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Mật khẩu mới phải có ít nhất 6 ký tự"));
        }
        accountVerificationService.resetPassword(token, newPassword);
        return ResponseEntity.ok(ApiResponse.success(null, "Đặt lại mật khẩu thành công. Hãy đăng nhập bằng mật khẩu mới."));
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

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

        userService.assertEmailVerified(request.getUsername()); // 403 nếu chưa xác thực email
        TokenResponse token = keycloakService.login(request.getUsername(), request.getPassword());
        ResponseCookie cookie = buildAccessTokenCookie(token.getAccessToken(), token.getExpiresIn());
        
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.success(token, "Login successful"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refreshToken(
            @RequestBody(required = false) Map<String, String> body,
            @CookieValue(name = "refresh_token", required = false) String cookieRefreshToken) {
        String refreshToken = body != null ? body.get("refreshToken") : null;
        if (refreshToken == null || refreshToken.isBlank()) {
            refreshToken = cookieRefreshToken;
        }
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Refresh token is required"));
        }

        TokenResponse token = keycloakService.refreshToken(refreshToken);
        ResponseCookie cookie = buildAccessTokenCookie(token.getAccessToken(), token.getExpiresIn());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.success(token, "Token refreshed successfully"));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestBody(required = false) Map<String, String> body,
            @CookieValue(name = "refresh_token", required = false) String cookieRefreshToken) {

        String refreshToken = body != null ? body.get("refreshToken") : null;
        if (refreshToken == null || refreshToken.isBlank()) {
            refreshToken = cookieRefreshToken;
        }

        if (refreshToken != null && !refreshToken.isBlank()) {
            keycloakService.logout(refreshToken);
        }

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
            @RequestParam(name = "redirectUri", required = false) String redirectUri,
            @RequestParam(name = "redirect_uri", required = false) String redirect_uri) {

        String uri = redirectUri != null && !redirectUri.isBlank()
                ? redirectUri
                : (redirect_uri != null && !redirect_uri.isBlank() ? redirect_uri : DEFAULT_REDIRECT_URI);

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
