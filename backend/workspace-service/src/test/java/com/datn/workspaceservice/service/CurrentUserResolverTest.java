package com.datn.workspaceservice.service;

import com.datn.common.exception.UnauthorizedException;
import com.datn.workspaceservice.security.CurrentUserResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Quy ước toàn hệ thống: user id là Keycloak sub (không đổi sang users.id của identity-service). */
class CurrentUserResolverTest {

    private final CurrentUserResolver resolver = new CurrentUserResolver();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void givenJwt(Map<String, Object> claims) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claims(c -> c.putAll(claims))
                .build();
        TestingAuthenticationToken auth = new TestingAuthenticationToken(jwt, null, "ROLE_USER");
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("Trả về đúng Keycloak sub trong token")
    void returnsKeycloakSub() {
        UUID sub = UUID.randomUUID();
        givenJwt(Map.of("sub", sub.toString()));
        assertThat(resolver.currentUserId()).isEqualTo(sub);
    }

    @Test
    @DisplayName("Claim user_id (users.id của identity) bị bỏ qua: luôn dùng sub")
    void ignoresIdentityUserIdClaim() {
        UUID sub = UUID.randomUUID();
        givenJwt(Map.of("sub", sub.toString(), "user_id", UUID.randomUUID().toString()));
        assertThat(resolver.currentUserId()).isEqualTo(sub);
    }

    @Test
    @DisplayName("Chưa xác thực hoặc sub không phải UUID -> 401")
    void missingOrInvalidSub_is401() {
        assertThatThrownBy(resolver::currentUserId).isInstanceOf(UnauthorizedException.class);
        givenJwt(Map.of("sub", "khong-phai-uuid"));
        assertThatThrownBy(resolver::currentUserId).isInstanceOf(UnauthorizedException.class);
    }
}
