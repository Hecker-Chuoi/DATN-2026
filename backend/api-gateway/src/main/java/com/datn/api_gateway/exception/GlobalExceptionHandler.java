package com.datn.api_gateway.exception;

import com.datn.api_gateway.dto.ErrorResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global exception handler for the API Gateway.
 * <p>
 * Intercepts all unhandled exceptions at the gateway level and returns
 * a consistent JSON error response. This replaces the default Whitelabel
 * error page and ensures the client always receives structured JSON.
 * </p>
 * <p>
 * Handles the following error scenarios:
 * <ul>
 *   <li><b>401 Unauthorized</b> — Missing/invalid/expired JWT token</li>
 *   <li><b>403 Forbidden</b> — Valid JWT but insufficient permissions</li>
 *   <li><b>5xx errors</b> — Downstream service unavailable or internal errors</li>
 * </ul>
 * </p>
 * <p>
 * NOTE: This is a reactive {@link ErrorWebExceptionHandler}, NOT a
 * {@code @RestControllerAdvice} (which is Spring MVC only).
 * The {@code @Order(-1)} ensures this handler runs BEFORE the default
 * Spring Boot error handler.
 * </p>
 */
@Slf4j
@Component
@Order(-1) // Run before DefaultErrorWebExceptionHandler
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    /**
     * Constructor injection with pre-configured ObjectMapper.
     * Registers JavaTimeModule for LocalDateTime serialization.
     */
    public GlobalExceptionHandler() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Handles all exceptions thrown during request processing.
     * <p>
     * Determines the appropriate HTTP status and message based on the
     * exception type, then writes a JSON {@link ErrorResponse} to the response body.
     * </p>
     *
     * @param exchange the current server exchange
     * @param ex       the exception that occurred
     * @return a {@link Mono} that completes when the response is written
     */
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        String path = exchange.getRequest().getPath().value();

        // Determine HTTP status and error message based on exception type
        HttpStatus status;
        String message;

        if (ex instanceof InvalidBearerTokenException) {
            // Invalid or expired JWT token
            status = HttpStatus.UNAUTHORIZED;
            message = "Authentication failed: invalid or expired JWT token";
            log.warn("JWT authentication failed for path '{}': {}", path, ex.getMessage());

        } else if (ex instanceof AuthenticationCredentialsNotFoundException) {
            // No JWT token provided
            status = HttpStatus.UNAUTHORIZED;
            message = "Authentication required: missing JWT token";
            log.warn("Missing credentials for path '{}': {}", path, ex.getMessage());

        } else if (ex instanceof AuthenticationException) {
            // Other authentication failures
            status = HttpStatus.UNAUTHORIZED;
            message = "Authentication required: " + ex.getMessage();
            log.warn("Authentication error for path '{}': {}", path, ex.getMessage());

        } else if (ex instanceof AccessDeniedException) {
            // Valid JWT but insufficient permissions
            status = HttpStatus.FORBIDDEN;
            message = "Access denied: insufficient permissions";
            log.warn("Access denied for path '{}': {}", path, ex.getMessage());

        } else if (ex instanceof ResponseStatusException responseStatusException) {
            // Explicit response status (e.g., 404, 503)
            status = HttpStatus.valueOf(responseStatusException.getStatusCode().value());
            message = responseStatusException.getReason() != null
                    ? responseStatusException.getReason()
                    : status.getReasonPhrase();
            log.error("Response status exception for path '{}': {} - {}",
                    path, status, ex.getMessage());

        } else {
            // Catch-all for unexpected errors
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            message = "An unexpected error occurred";
            log.error("Unexpected error for path '{}': {}", path, ex.getMessage(), ex);
        }

        // Prevent writing if response is already committed
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        // Build and write JSON error response
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ErrorResponse errorResponse = ErrorResponse.of(status.value(), message, path);

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(errorResponse);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException jsonEx) {
            log.error("Failed to serialize error response: {}", jsonEx.getMessage());
            return Mono.error(ex);
        }
    }
}
