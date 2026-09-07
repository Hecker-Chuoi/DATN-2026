package com.datn.workservice.security;

import com.datn.common.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;
import java.util.UUID;

@Slf4j
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * Extracts the current authenticated User ID from Spring Security context.
     * Checks JWT 'sub' claim or authentication name.
     */
    public static Optional<UUID> getCurrentUserIdOptional() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            // 1. Prioritize custom claim 'user_id' or 'userId' (PostgreSQL database User ID)
            String userIdClaim = jwt.getClaimAsString("user_id");
            if (userIdClaim == null) {
                userIdClaim = jwt.getClaimAsString("userId");
            }
            if (userIdClaim != null && !userIdClaim.isBlank()) {
                try {
                    return Optional.of(UUID.fromString(userIdClaim));
                } catch (IllegalArgumentException ignored) {
                }
            }

            // 2. Fallback to 'sub' claim (Keycloak User ID)
            String subject = jwt.getSubject();
            if (subject != null && !subject.isBlank()) {
                try {
                    return Optional.of(UUID.fromString(subject));
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid UUID in JWT sub claim: {}", subject);
                }
            }
        }

        // Fallback to name if it is a valid UUID
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
     * Gets current user ID or throws UnauthorizedException.
     */
    public static UUID getCurrentUserId() {
        return getCurrentUserIdOptional()
                .orElseThrow(() -> new UnauthorizedException("User is not authenticated or user ID is missing"));
    }

    /**
     * Checks if current authenticated user has global ADMIN role in Keycloak JWT or Authorities.
     */
    public static boolean isGlobalAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return false;

        boolean inAuthorities = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equalsIgnoreCase("ADMIN") || a.getAuthority().equalsIgnoreCase("ROLE_ADMIN"));
        if (inAuthorities) return true;

        if (authentication.getPrincipal() instanceof Jwt jwt) {
            java.util.Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess != null && realmAccess.get("roles") instanceof java.util.List<?> roles) {
                return roles.stream().anyMatch(r -> "ADMIN".equalsIgnoreCase(String.valueOf(r)));
            }
        }
        return false;
    }
}
