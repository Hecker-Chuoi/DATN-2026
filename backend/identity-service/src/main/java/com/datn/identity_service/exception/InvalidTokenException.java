package com.datn.identity_service.exception;

/** Token xác thực / đặt lại mật khẩu không tồn tại, đã dùng hoặc hết hạn → 400. */
public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException(String message) {
        super(message);
    }
}
