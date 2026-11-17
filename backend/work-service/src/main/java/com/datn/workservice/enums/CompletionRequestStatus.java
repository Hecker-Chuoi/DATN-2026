package com.datn.workservice.enums;

/**
 * Trạng thái của một yêu cầu xác nhận hoàn thành ("Tạm hoàn thành").
 */
public enum CompletionRequestStatus {
    /** Đang chờ Admin dự án xác nhận. */
    PENDING,
    /** Admin đã xác nhận — công việc đã thật sự chuyển sang trạng thái Hoàn thành. */
    APPROVED,
    /** Admin từ chối — công việc giữ nguyên trạng thái cũ. */
    REJECTED,
    /** Người yêu cầu/Admin hủy, hoặc trạng thái công việc đổi trước khi được duyệt. */
    CANCELLED
}
