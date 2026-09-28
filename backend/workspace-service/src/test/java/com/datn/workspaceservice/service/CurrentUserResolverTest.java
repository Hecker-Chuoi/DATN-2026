package com.datn.workspaceservice.service;

import com.datn.common.dto.ApiResponse;
import com.datn.common.exception.ForbiddenException;
import com.datn.common.exception.UnauthorizedException;
import com.datn.workspaceservice.client.IdentityClient;
import com.datn.workspaceservice.client.dto.UserResponse;
import com.datn.workspaceservice.security.CurrentUserResolver;
import com.datn.workspaceservice.security.IdentityUserLookup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bất biến duy nhất được kiểm ở đây: KHÔNG BAO GIỜ trả về Keycloak sub như thể
 * nó là user id. Vi phạm bất biến này sẽ ghi các hàng workspace_members khóa
 * theo một id mutable (keycloak_id bị UserService ghi lại khi link account),
 * và quyền sẽ âm thầm biến mất.
 */
@ExtendWith(MockitoExtension.class)
class CurrentUserResolverTest {

    @Mock
    private IdentityClient identityClient;

    @InjectMocks
    private IdentityUserLookup identityUserLookup;

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
    @DisplayName("Có claim user_id -> dùng luôn, KHÔNG gọi identity-service")
    void userIdClaimPresent_skipsFeign() {
        UUID canonicalId = UUID.randomUUID();
        givenJwt(Map.of("sub", UUID.randomUUID().toString(), "user_id", canonicalId.toString()));
        CurrentUserResolver resolver = new CurrentUserResolver(identityUserLookup);

        assertThat(resolver.currentUserId()).isEqualTo(canonicalId);
        verify(identityClient, never()).getUserById(any());
    }

    @Test
    @DisplayName("Thiếu claim user_id -> đổi sub thành users.id qua 1 lời gọi identity-service")
    void userIdClaimAbsent_resolvesViaSub() {
        UUID keycloakSub = UUID.randomUUID();
        UUID canonicalId = UUID.randomUUID();
        givenJwt(Map.of("sub", keycloakSub.toString()));
        when(identityClient.getUserById(keycloakSub)).thenReturn(
                ApiResponse.success(UserResponse.builder().id(canonicalId).build()));
        CurrentUserResolver resolver = new CurrentUserResolver(identityUserLookup);

        UUID resolved = resolver.currentUserId();

        assertThat(resolved).isEqualTo(canonicalId);
        // Điểm cốt lõi: trả về users.id, KHÔNG phải sub.
        assertThat(resolved).isNotEqualTo(keycloakSub);
        verify(identityClient).getUserById(keycloakSub);
    }

    @Test
    @DisplayName("identity-service khong ket noi duoc -> 403 (KHONG phai 401) va khong fallback ve sub")
    void identityFailure_failsClosedWith403() {
        UUID keycloakSub = UUID.randomUUID();
        givenJwt(Map.of("sub", keycloakSub.toString()));
        when(identityClient.getUserById(keycloakSub)).thenThrow(new RuntimeException("connection refused"));
        CurrentUserResolver resolver = new CurrentUserResolver(identityUserLookup);

        // PHAI la 403, khong duoc la 401: luc nay JWT da duoc resource server xac
        // thuc xong, nen loi nay khong bao gio chua duoc bang refresh token. Tra
        // 401 se khien interceptor cua frontend goi /auth/refresh (thanh cong vi
        // token von hop le), retry, roi lai 401 - sinh ra bao refresh vo nghia.
        assertThatThrownBy(resolver::currentUserId)
                .isInstanceOf(ForbiddenException.class)
                .isNotInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("identity-service");
    }

    @Test
    @DisplayName("Tai khoan co tren Keycloak nhung chua co hang trong bang users -> 403 kem huong dan")
    void identityReturnsNoData_failsClosedWithGuidance() {
        UUID keycloakSub = UUID.randomUUID();
        givenJwt(Map.of("sub", keycloakSub.toString()));
        when(identityClient.getUserById(keycloakSub)).thenReturn(ApiResponse.success(null));
        CurrentUserResolver resolver = new CurrentUserResolver(identityUserLookup);

        assertThatThrownBy(resolver::currentUserId)
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Keycloak")
                .hasMessageContaining(keycloakSub.toString());
    }

    @Test
    @DisplayName("Chưa xác thực -> 401")
    void notAuthenticated_isUnauthorized() {
        SecurityContextHolder.clearContext();
        CurrentUserResolver resolver = new CurrentUserResolver(identityUserLookup);

        assertThatThrownBy(resolver::currentUserId)
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Yêu cầu xác thực");
    }
}
