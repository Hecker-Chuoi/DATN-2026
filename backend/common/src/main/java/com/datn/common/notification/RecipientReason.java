package com.datn.common.notification;

/**
 * Lý do một người nhận được thông báo. Khi một sự kiện có nhiều lý do cho cùng một người,
 * notification-service chỉ giữ lý do mạnh nhất (quy tắc R3): giá trị đứng trước mạnh hơn.
 */
public enum RecipientReason {
    MENTIONED,
    NEW_ASSIGNEE,
    TARGET_USER,
    REQUESTER,
    INVITEE,
    ASSIGNEE,
    PREVIOUS_ASSIGNEE,
    REPORTER,
    COMMENT_AUTHOR,
    INVITER,
    PROJECT_MANAGER,
    MEMBER;

    /** Số càng nhỏ càng mạnh. */
    public int strength() {
        return ordinal();
    }

    public static RecipientReason parse(String value) {
        if (value == null) return MEMBER;
        try {
            return RecipientReason.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            return MEMBER;
        }
    }
}
