package com.datn.workservice.security;

import com.datn.common.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Tiện ích đọc thông tin người dùng hiện tại từ JWT của Keycloak.
 *
 * Quy ước định danh: work-service dùng DUY NHẤT claim {@code sub} (Keycloak user id)
 * làm user_id ở mọi bảng (role_assignments, team_members, created_by, assignee_id...).
 */
@Slf4j
public final class SecurityUtils {

    private static final String ADMIN_ROLE = "ADMIN";

    private SecurityUtils() {
    }

    /**
     * Trả về user id (Keycloak {@code sub}) của người dùng hiện tại, nếu đã xác thực.
     */
    public static Optional<UUID> getCurrentUserIdOptional() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            String subject = jwt.getSubject();
            if (subject != null && !subject.isBlank()) {
                try {
                    return Optional.of(UUID.fromString(subject));
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid UUID in JWT sub claim: {}", subject);
                }
            }
        }

        // Fallback: authentication name (dùng trong test hoặc token không phải JWT)
        String name = authentication.getName();
        if (name != null && !name.isBlank() && !"anonymousUser".equalsIgnoreCase(name)) {
            try {
                return Optional.of(UUID.fromString(name));
            } catch (IllegalArgumentException ignored) {
            }
        }

        return Optional.empty();
    }

    /**
     * Trả về user id hiện tại hoặc ném UnauthorizedException.
     */
    public static UUID getCurrentUserId() {
        return getCurrentUserIdOptional()
                .orElseThrow(() -> new UnauthorizedException("User is not authenticated or user ID is missing"));
    }

    /**
     * Người dùng hiện tại có realm role ADMIN (Global Admin) hay không.
     */
    public static boolean isGlobalAdmin() {
        return hasRealmRole(ADMIN_ROLE);
    }

    /**
     * Kiểm tra realm role từ GrantedAuthorities (ROLE_x) hoặc trực tiếp từ claim realm_access.roles.
     */
    public static boolean hasRealmRole(String roleName) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || roleName == null) return false;

        if (authentication.getAuthorities() != null) {
            boolean inAuthorities = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equalsIgnoreCase(roleName)
                            || a.getAuthority().equalsIgnoreCase("ROLE_" + roleName));
            if (inAuthorities) return true;
        }

        if (authentication.getPrincipal() instanceof Jwt jwt) {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess != null && realmAccess.get("roles") instanceof List<?> roles) {
                return roles.stream().anyMatch(r -> roleName.equalsIgnoreCase(String.valueOf(r)));
            }
        }
        return false;
    }
}
