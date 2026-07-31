package com.datn.identity_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Spring Security configuration.
 * <p>
 * - Stateless (JWT-only, no HTTP sessions)
 * - OAuth2 Resource Server validating Keycloak-issued JWT tokens
 * - Custom JWT converter extracting Keycloak realm_access + resource_access roles
 * - Method-level security enabled (@PreAuthorize / @Secured / @RolesAllowed)
 * </p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
public class SecurityConfig {

    // ─── Public endpoints (no token required) ────────────────────────
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/**",
            "/actuator/health",
            "/actuator/info"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF – stateless REST API
                .csrf(AbstractHttpConfigurer::disable)

                // Stateless session (no HttpSession)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Authorization rules
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated()
                )

                // JWT Resource Server
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(keycloakJwtAuthenticationConverter())
                        )
                );

        return http.build();
    }

    /**
     * Custom JWT Authentication Converter that:
     * 1. Extracts roles from Keycloak's `realm_access.roles` claim
     * 2. Extracts roles from Keycloak's `resource_access.<client-id>.roles` claim
     * 3. Prepends "ROLE_" prefix so Spring Security @PreAuthorize("hasRole('ADMIN')") works
     */
    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> keycloakJwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(keycloakGrantedAuthoritiesConverter());
        return converter;
    }

    @Bean
    public Converter<Jwt, Collection<GrantedAuthority>> keycloakGrantedAuthoritiesConverter() {
        return jwt -> {
            // Default scope-based authorities
            JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();
            Collection<GrantedAuthority> scopeAuthorities = scopeConverter.convert(jwt);

            // Extract realm_access.roles
            Collection<GrantedAuthority> realmRoles = extractRealmRoles(jwt);

            // Merge both sets
            return Stream.concat(
                    scopeAuthorities != null ? scopeAuthorities.stream() : Stream.empty(),
                    realmRoles.stream()
            ).collect(Collectors.toSet());
        };
    }

    /**
     * Extract roles from Keycloak JWT claim:
     * {@code "realm_access": { "roles": ["ADMIN", "USER"] }}
     * and prefix each with "ROLE_".
     */
    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !realmAccess.containsKey("roles")) {
            return Collections.emptyList();
        }

        List<String> roles = (List<String>) realmAccess.get("roles");
        return roles.stream()
                // Filter out Keycloak internal roles
                .filter(role -> !role.startsWith("default-roles") && !role.equals("offline_access") && !role.equals("uma_authorization"))
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .collect(Collectors.toList());
    }
}
