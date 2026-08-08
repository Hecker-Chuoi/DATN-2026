package com.datn.identity_service.exception;

/**
 * Exception thrown when user login fails due to incorrect credentials
 * (wrong username or password).
 */
public class BadCredentialsException extends RuntimeException {
    public BadCredentialsException(String message) {
        super(message);
    }
}
