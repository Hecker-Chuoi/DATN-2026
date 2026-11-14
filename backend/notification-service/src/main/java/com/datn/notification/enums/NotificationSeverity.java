package com.datn.notification.enums;

/** Mức độ: quyết định màu và việc có hiện toast hay không. */
public enum NotificationSeverity {
    /** Thông thường. */
    INFO,
    /** Tích cực: được xác nhận, hết bị cản trở. */
    SUCCESS,
    /** Cảnh báo: sắp hạn, bị cản trở, bị từ chối. */
    WARNING,
    /** Khẩn cấp: đã trễ hoặc mất quyền (hiện toast). */
    URGENT
}
