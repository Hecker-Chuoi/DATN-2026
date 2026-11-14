package com.datn.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.UUID;

/**
 * Thông điệp đẩy xuống trình duyệt qua {@code /user/queue/notifications}.
 *
 * kind: CREATED, UPDATED (gộp hoặc "Cần xử lý" đã được giải quyết), READ, UNREAD, READ_ALL,
 * DELETED, RESTORED.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PushMessage(
        String kind,
        NotificationResponse notification,
        List<UUID> ids,
        Long unreadCount,
        Long actionCount
) {

    public static PushMessage of(String kind, NotificationResponse n) {
        return new PushMessage(kind, n, null, null, null);
    }

    public static PushMessage ofIds(String kind, List<UUID> ids) {
        return new PushMessage(kind, null, ids, null, null);
    }

    public PushMessage withCounts(long unread, long action) {
        return new PushMessage(kind, notification, ids, unread, action);
    }
}
