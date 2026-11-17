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
 * Chỉ bóc dữ liệu thô ra khỏi token; user id của người gọi lấy qua {@link CurrentUserResolver}.
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

    /** Keycloak {@code sub} dạng chuỗi thô ({@link CurrentUserResolver} đổi sang UUID). */
    public static Optional<String> getRawSubject() {
        return getCurrentJwt()
                .map(Jwt::getSubject)
                .filter(sub -> sub != null && !sub.isBlank());
    }
}
