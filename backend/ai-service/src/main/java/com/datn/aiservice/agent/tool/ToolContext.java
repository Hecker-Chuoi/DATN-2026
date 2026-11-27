package com.datn.aiservice.agent.tool;

import java.time.ZoneId;
import java.util.UUID;

/** Ngữ cảnh một lượt chat. {@code userId} luôn là Keycloak {@code sub}. */
public record ToolContext(String userId, UUID workspaceId, UUID projectId,
                          UUID workItemId, UUID conversationId, ZoneId zoneId) {

    public static ZoneId zoneOrDefault(String tz) {
        try {
            return tz == null || tz.isBlank() ? ZoneId.of("Asia/Ho_Chi_Minh") : ZoneId.of(tz);
        } catch (Exception e) {
            return ZoneId.of("Asia/Ho_Chi_Minh");
        }
    }
}
