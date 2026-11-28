package com.datn.workservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Bảo vệ {@code /internal/ai-tools/**}: chỉ ai-service (biết {@code AI_TOOLS_API_KEY}) gọi được.
 * <p>
 * API key chỉ chứng minh "đây là ai-service", KHÔNG thay quyền người dùng: filter dựng một {@link Jwt} giả
 * chỉ có claim {@code sub} = {@code X-User-Id} để {@code SecurityUtils.getCurrentUserId()} và changelog
 * ghi đúng người thực hiện; quyền trong project vẫn do {@code AiToolService} kiểm tra bằng
 * {@code hasPermission(projectId, userId, key)}. Jwt giả không có realm role → Global ADMIN không bypass khi đi qua AI (chủ ý).
 */
@Slf4j
public class AiToolApiKeyFilter extends OncePerRequestFilter {

    public static final String PATH_PREFIX = "/internal/ai-tools/";

    private final byte[] expectedKey;

    public AiToolApiKeyFilter(String apiKey) {
        this.expectedKey = apiKey == null ? new byte[0] : apiKey.getBytes(StandardCharsets.UTF_8);
        if (expectedKey.length == 0) {
            log.warn("ai-tools.api-key (AI_TOOLS_API_KEY) chưa cấu hình – mọi request /internal/ai-tools/** sẽ bị từ chối");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader("X-API-Key");
        if (expectedKey.length == 0 || key == null
                || !MessageDigest.isEqual(expectedKey, key.getBytes(StandardCharsets.UTF_8))) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "API key không hợp lệ");
            return;
        }
        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isBlank()) {
            reject(response, HttpServletResponse.SC_BAD_REQUEST, "Thiếu header X-User-Id");
            return;
        }
        try {
            UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            reject(response, HttpServletResponse.SC_BAD_REQUEST, "X-User-Id không phải UUID");
            return;
        }

        Jwt jwt = Jwt.withTokenValue("ai-service")
                .header("alg", "none")
                .subject(userId)
                .claim("azp", "ai-service")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(), userId);
        SecurityContextHolder.getContext().setAuthentication(auth);

        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            log.info("[AI-TOOL] user={} tool={} status={} {}ms", userId,
                    request.getRequestURI().substring(PATH_PREFIX.length()), response.getStatus(),
                    System.currentTimeMillis() - start);
        }
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"success\":false,\"message\":\"" + message + "\"}");
    }
}
