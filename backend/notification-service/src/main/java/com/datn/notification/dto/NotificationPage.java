package com.datn.notification.dto;

import java.util.List;

/**
 * Một trang thông báo, phân trang theo con trỏ (updated_at, id) để thông báo mới chen vào
 * không làm trùng hay sót dòng khi cuộn.
 */
public record NotificationPage(
        List<NotificationResponse> items,
        String nextCursor,
        long unreadCount,
        long actionCount
) {}
