package com.datn.common.exception;

/**
 * Ném ra khi thao tác xung đột với trạng thái hiện tại của tài nguyên (HTTP 409).
 * Ví dụ: bắt đầu Sprint trong khi dự án đã có Sprint ACTIVE, hoặc kéo thẻ đã bị
 * người khác cập nhật (optimistic locking).
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }

    public ConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
