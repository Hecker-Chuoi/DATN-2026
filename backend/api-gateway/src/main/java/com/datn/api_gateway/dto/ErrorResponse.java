package com.datn.api_gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Unified error response DTO for the API Gateway.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private boolean success;

    private int status;

    private String message;

    private String path;

    private LocalDateTime timestamp;

    /**
     * Factory method to create a standardized error response.
     *
     * @param status  HTTP status code
     * @param message error message
     * @param path    request path
     * @return constructed {@link ErrorResponse}
     */
    public static ErrorResponse of(int status, String message, String path) {
        return ErrorResponse.builder()
                .success(false)
                .status(status)
                .message(message)
                .path(path)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
