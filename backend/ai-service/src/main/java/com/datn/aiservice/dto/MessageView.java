package com.datn.aiservice.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Dạng hiển thị của lịch sử: text, tool đã gọi (kèm ok/lỗi), thẻ tương tác (đang chờ hoặc đã xử lý). */
public record MessageView(UUID id, String role, String text, List<ToolCallView> tools,
                          List<InteractionView> interactions, Instant createdAt) {

    public record ToolCallView(String id, String name, String summary, Boolean ok) {}

    public record InteractionView(UUID interactionId, String type, String toolName, String summary,
                                  Map<String, Object> payload, String status, Map<String, Object> response) {}
}
