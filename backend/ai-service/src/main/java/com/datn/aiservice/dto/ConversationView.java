package com.datn.aiservice.dto;

import com.datn.aiservice.entity.AiConversation;

import java.time.Instant;
import java.util.UUID;

public record ConversationView(UUID id, String title, String status, UUID workspaceId, UUID projectId,
                               Instant createdAt, Instant updatedAt) {
    public static ConversationView of(AiConversation c) {
        return new ConversationView(c.getId(), c.getTitle(), c.getStatus().name(), c.getWorkspaceId(),
                c.getProjectId(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
