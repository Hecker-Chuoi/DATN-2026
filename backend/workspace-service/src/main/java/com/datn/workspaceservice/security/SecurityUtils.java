package com.datn.workspaceservice.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;
import java.util.UUID;

/**
 * Đọc thông tin xác thực thô từ SecurityContext.
 * <p>
 * KHÁC {@code SecurityUtils} của work-service: lớp này KHÔNG bao giờ trả về
 * Keycloak {@code sub} như thể nó là user id. Việc đổi sub sang users.id là việc
 * của {@link CurrentUserResolver}. Ở đây chỉ bóc dữ liệu thô ra khỏi token.
 */
@Slf4j
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<Jwt> getCurrentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return authentication.getPrincipal() instanceof Jwt jwt ? Optional.of(jwt) : Optional.empty();
    }

    /**
     * Claim {@code user_id} (hoặc {@code userId}) - chính là users.id của
     * identity-service. Chỉ có mặt khi realm datn-realm đã cấu hình protocol
     * mapper cho attribute user_id; nếu chưa thì trả về empty.
     */
    public static Optional<UUID> getUserIdClaim() {
        return getCurrentJwt().flatMap(jwt -> {
            String claim = jwt.getClaimAsString("user_id");
            if (claim == null || claim.isBlank()) {
                claim = jwt.getClaimAsString("userId");
            }
            if (claim == null || claim.isBlank()) {
                return Optional.empty();
            }
            try {
                return Optional.of(UUID.fromString(claim));
            } catch (IllegalArgumentException e) {
                log.warn("Claim user_id không phải UUID hợp lệ: {}", claim);
                return Optional.empty();
            }
        });
    }

    /** Keycloak sub dạng chuỗi thô. KHÔNG dùng làm user id. */
    public static Optional<String> getRawSubject() {
        return getCurrentJwt()
                .map(Jwt::getSubject)
                .filter(sub -> sub != null && !sub.isBlank());
    }
}
