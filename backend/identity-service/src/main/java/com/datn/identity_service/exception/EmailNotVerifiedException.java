package com.datn.identity_service.exception;

/** Đăng nhập khi tài khoản chưa xác thực email → 403. */
public class EmailNotVerifiedException extends RuntimeException {
    public EmailNotVerifiedException(String message) {
        super(message);
    }
}
