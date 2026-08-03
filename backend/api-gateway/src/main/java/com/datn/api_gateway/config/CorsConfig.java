package com.datn.api_gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Centralized CORS configuration for the API Gateway.
 * <p>
 * This configuration works alongside the CORS settings defined in
 * {@code application.yml} under {@code spring.cloud.gateway.globalcors}.
 * The {@link CorsWebFilter} bean ensures CORS headers are applied
 * consistently, including for error responses and preflight (OPTIONS) requests.
 * </p>
 * <p>
 * By handling CORS at the Gateway level, downstream services do NOT need
 * to configure CORS individually, avoiding duplication and potential conflicts.
 * </p>
 */
@Configuration
public class CorsConfig {

    /**
     * Creates a reactive CORS filter that applies CORS headers to all routes.
     * <p>
     * This filter handles:
     * <ul>
     *   <li>Preflight OPTIONS requests</li>
     *   <li>Actual CORS requests with proper headers</li>
     *   <li>Credential support (cookies, Authorization header)</li>
     * </ul>
     * </p>
     *
     * @return configured {@link CorsWebFilter}
     */
    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();

        // Allowed origins — frontend development servers
        corsConfig.setAllowedOrigins(List.of(
                "http://localhost:3000",    // React / Next.js default
                "http://localhost:5173"     // Vite default
        ));

        // Allowed HTTP methods
        corsConfig.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));

        // Allow all request headers
        corsConfig.setAllowedHeaders(List.of("*"));

        // Expose Correlation ID header to frontend JavaScript
        corsConfig.setExposedHeaders(List.of("X-Correlation-Id"));

        // Allow credentials (cookies, Authorization header)
        corsConfig.setAllowCredentials(true);

        // Cache preflight response for 1 hour
        corsConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }
}
