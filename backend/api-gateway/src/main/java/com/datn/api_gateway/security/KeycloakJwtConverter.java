package com.datn.api_gateway.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reactive JWT converter that extracts Keycloak realm roles from the JWT token.
 * <p>
 * Keycloak stores roles in the {@code realm_access.roles} claim, not in the
 * standard {@code scope} claim. This converter:
 * <ol>
 *   <li>Extracts default scope-based authorities (SCOPE_xxx)</li>
 *   <li>Extracts Keycloak realm roles from {@code realm_access.roles}</li>
 *   <li>Prefixes realm roles with "ROLE_" for Spring Security compatibility</li>
 *   <li>Filters out Keycloak internal roles (default-roles-*, offline_access, uma_authorization)</li>
 *   <li>Merges both sets into the authentication token</li>
 * </ol>
 * </p>
 * <p>
 * NOTE: Gateway only needs to know the JWT is valid. Role-based authorization
 * (e.g., {@code @PreAuthorize("hasRole('ADMIN')")}) is handled by each
 * downstream service independently.
 * </p>
 *
 * @see SecurityConfig
 */
@Slf4j
@Component
public class KeycloakJwtConverter implements Converter<Jwt, Mono<AbstractAuthenticationToken>> {

    /**
     * Default Spring converter that reads the standard "scope" claim.
     */
    private final JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();

    /**
     * Converts a Keycloak-issued JWT into a reactive {@link AbstractAuthenticationToken}.
     * <p>
     * Combines standard scope authorities with Keycloak realm roles.
     * The principal name is extracted from the JWT "sub" claim (Keycloak user UUID).
     * </p>
     *
     * @param jwt the validated JWT token
     * @return a {@link Mono} emitting the authentication token with merged authorities
     */
    @Override
    public Mono<AbstractAuthenticationToken> convert(Jwt jwt) {
        // Extract standard scope-based authorities (SCOPE_xxx)
        Collection<GrantedAuthority> scopeAuthorities = scopeConverter.convert(jwt);

        // Extract Keycloak realm_access.roles
        Collection<GrantedAuthority> realmRoles = extractRealmRoles(jwt);

        // Merge both authority sets
        Set<GrantedAuthority> allAuthorities = Stream.concat(
                scopeAuthorities != null ? scopeAuthorities.stream() : Stream.empty(),
                realmRoles.stream()
        ).collect(Collectors.toSet());

        log.debug("JWT authorities for user '{}': {}", jwt.getSubject(), allAuthorities);

        return Mono.just(new JwtAuthenticationToken(jwt, allAuthorities, jwt.getSubject()));
    }

    /**
     * Extracts roles from Keycloak's {@code realm_access.roles} JWT claim
     * and prefixes each with "ROLE_" for Spring Security's {@code hasRole()} support.
     * <p>
     * Example JWT claim:
     * <pre>
     * {
     *   "realm_access": {
     *     "roles": ["ADMIN", "USER", "default-roles-datn-realm", "offline_access"]
     *   }
     * }
     * </pre>
     * Result: [ROLE_ADMIN, ROLE_USER] (internal roles filtered out)
     * </p>
     *
     * @param jwt the validated JWT token
     * @return collection of granted authorities with ROLE_ prefix
     */
    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");

        // No realm_access claim or no roles key → no realm roles
        if (realmAccess == null || !realmAccess.containsKey("roles")) {
            return Collections.emptyList();
        }

        List<String> roles = (List<String>) realmAccess.get("roles");

        return roles.stream()
                // Filter out Keycloak internal/system roles
                .filter(role -> !role.startsWith("default-roles")
                        && !role.equals("offline_access")
                        && !role.equals("uma_authorization"))
                // Prefix with ROLE_ so @PreAuthorize("hasRole('ADMIN')") works
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .collect(Collectors.toList());
    }
}
