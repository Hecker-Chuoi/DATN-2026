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

@Slf4j
@Component
public class KeycloakJwtConverter implements Converter<Jwt, Mono<AbstractAuthenticationToken>> {

    /**
     * Default Spring converter that reads the standard "scope" claim.
     */
    private final JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();

    /**
     * Converts a Keycloak-issued JWT into a reactiv
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
