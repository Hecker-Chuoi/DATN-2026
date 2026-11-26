package com.datn.workservice.notification;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nhắc tên trong bình luận: ô soạn bình luận chèn {@code @[Tên hiển thị](keycloak-sub)}.
 * Hiển thị trên giao diện là "@Tên hiển thị"; server lấy sub để gửi thông báo D1.
 */
public final class MentionParser {

    private static final Pattern MENTION = Pattern.compile("@\\[([^\\[\\]\\n]{1,100})]\\(([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})\\)");

    private MentionParser() {
    }

    /** Các người được nhắc, theo thứ tự xuất hiện, không trùng. */
    public static Set<UUID> mentionedUserIds(String body) {
        Set<UUID> ids = new LinkedHashSet<>();
        if (body == null || body.isBlank()) return ids;
        Matcher m = MENTION.matcher(body);
        while (m.find()) {
            try {
                ids.add(UUID.fromString(m.group(2)));
            } catch (IllegalArgumentException ignored) {
                // bỏ qua id hỏng
            }
        }
        return ids;
    }

    /** Văn bản thuần để trích dẫn trong thông báo: "@[An](uuid)" → "@An". */
    public static String plainText(String body) {
        if (body == null) return "";
        return MENTION.matcher(body).replaceAll(r -> Matcher.quoteReplacement("@" + r.group(1)));
    }
}
