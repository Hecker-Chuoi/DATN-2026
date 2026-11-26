package com.datn.notification.catalog;

import com.datn.notification.enums.NotificationSeverity;

/**
 * Nội dung đã dựng cho một người nhận.
 *
 * @param title    HTML đã escape, chỉ có thẻ &lt;b&gt;
 * @param body     văn bản thuần (có thể rỗng)
 * @param link     đường dẫn tương đối của frontend
 * @param severity ghi đè mức mặc định của loại; {@code null} = dùng mức mặc định
 */
public record Rendered(String title, String body, String link, NotificationSeverity severity) {

    public static Rendered of(String title, String body, String link) {
        return new Rendered(title, body, link, null);
    }

    public Rendered withSeverity(NotificationSeverity s) {
        return new Rendered(title, body, link, s);
    }
}
