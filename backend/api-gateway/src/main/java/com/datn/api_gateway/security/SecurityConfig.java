package com.datn.api_gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Reactive Security configuration for the API Gateway.
 * <p>
 * Key responsibilities:
 * <ul>
 *   <li>Define public endpoints that bypass JWT authentication</li>
 *   <li>Require valid JWT for all other requests</li>
 *   <li>Configure OAuth2 Resource Server with custom Keycloak JWT converter</li>
 *   <li>Disable CSRF (stateless API gateway)</li>
 * </ul>
 * </p>
 * <p>
 * IMPORTANT: Gateway uses WebFlux (reactive), so we use
 * {@code SecurityWebFilterChain} instead of {@code SecurityFilterChain}.
 * </p>
 *
 * @see KeycloakJwtConverter
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/identity/v1/auth/**",
            "/actuator/health",
            "/actuator/info"
    };

    private final KeycloakJwtConverter keycloakJwtConverter;

    public SecurityConfig(KeycloakJwtConverter keycloakJwtConverter) {
        this.keycloakJwtConverter = keycloakJwtConverter;
    }

    /**
     * Configures the reactive security filter chain.
     * <p>
     * - CSRF disabled: Gateway is a stateless reverse proxy, no session/cookies.
     * - Public endpoints: Auth endpoints and actuator health/info are open.
     * - All other requests: Must carry a valid JWT (Bearer token).
     * - JWT validation: Uses JWKS URI from Keycloak (cached, no per-request call).
     * </p>
     *
     * @param http the reactive {@link ServerHttpSecurity} builder
     * @return configured {@link SecurityWebFilterChain}
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(keycloakJwtConverter)
                        )
                ).build();
    }
}
