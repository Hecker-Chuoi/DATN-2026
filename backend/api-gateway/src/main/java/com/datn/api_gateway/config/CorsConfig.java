package com.datn.api_gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    /**
     * Origin được phép gọi chéo nguồn. Mặc định là 2 dev server; khi deploy đặt
     * {@code APP_CORS_ALLOWED_ORIGINS=https://app.example.com} (nhiều giá trị cách nhau dấu phẩy).
     */
    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private List<String> allowedOrigins;

    /**
     * Creates a reactive CORS filter that applies CORS headers to all routes.
     */
    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();

        // Allowed origins — mặc định là frontend dev server, ghi đè qua app.cors.allowed-origins
        corsConfig.setAllowedOrigins(allowedOrigins);

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
