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

@Slf4j
@Component
@Order(-1)
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    public GlobalExceptionHandler() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Handles all exceptions thrown during request processing.
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
