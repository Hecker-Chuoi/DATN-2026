package com.datn.api_gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/identity/api/v1/auth/**",
            "/work/api/v1/work/github/webhook",   // GitHub gọi vào, work-service tự xác thực HMAC
            "/actuator/health",
            "/actuator/info"
    };

    private final KeycloakJwtConverter keycloakJwtConverter;

    public SecurityConfig(KeycloakJwtConverter keycloakJwtConverter) {
        this.keycloakJwtConverter = keycloakJwtConverter;
    }

    /**
     * Configures the reactive security filter chain.
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
                        // Read JWT from Authorization header OR access_token httpOnly cookie
                        .bearerTokenConverter(new CookieOrBearerTokenConverter())
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(keycloakJwtConverter)
                        )
                ).build();
    }
}
