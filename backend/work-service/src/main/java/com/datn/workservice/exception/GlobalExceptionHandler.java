package com.datn.workservice.exception;

import com.datn.common.dto.ApiResponse;
import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ForbiddenException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.common.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(BadRequestException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException ex) {
        log.warn("No resource found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("API endpoint không tồn tại: " + ex.getResourcePath()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedException ex) {
        log.warn("Unauthorized: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));

        return ResponseEntity.badRequest().body(ApiResponse.error(message));
    }

    /**
     * ForbiddenException -> 403.
     * <p>
     * Thiếu handler này thì mọi ForbiddenException rơi vào handleGeneral và trả
     * về 500 kèm thông điệp chung vô nghĩa. WorkspaceAccessChecker throw kiểu này
     * cho mọi lần từ chối truy cập workspace, nên nó phải ra đúng 403 kèm lý do.
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(ForbiddenException ex) {
        log.warn("Forbidden: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ex.getMessage() != null ? ex.getMessage() : "Access denied: insufficient permissions"));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        log.warn("Authentication error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Authentication required"));
    }

    @ExceptionHandler(UndeclaredThrowableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUndeclaredThrowable(UndeclaredThrowableException ex) {
        Throwable undeclared = ex.getUndeclaredThrowable();
        if (undeclared instanceof AccessDeniedException ade) {
            return handleAccessDenied(ade);
        }
        if (undeclared instanceof ForbiddenException fe) {
            return handleForbidden(fe);
        }
        if (undeclared instanceof BadRequestException bre) {
            return handleBadRequest(bre);
        }
        if (undeclared instanceof ResourceNotFoundException rnfe) {
            return handleNotFound(rnfe);
        }
        if (undeclared instanceof UnauthorizedException ue) {
            return handleUnauthorized(ue);
        }
        return handleGeneral(ex);
    }

    @ExceptionHandler(InvocationTargetException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvocationTarget(InvocationTargetException ex) {
        Throwable target = ex.getTargetException();
        if (target instanceof AccessDeniedException ade) {
            return handleAccessDenied(ade);
        }
        if (target instanceof ForbiddenException fe) {
            return handleForbidden(fe);
        }
        if (target instanceof BadRequestException bre) {
            return handleBadRequest(bre);
        }
        if (target instanceof ResourceNotFoundException rnfe) {
            return handleNotFound(rnfe);
        }
        if (target instanceof UnauthorizedException ue) {
            return handleUnauthorized(ue);
        }
        return handleGeneral(ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau."));
    }
}
