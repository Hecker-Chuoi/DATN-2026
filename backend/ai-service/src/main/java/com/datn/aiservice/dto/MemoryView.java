package com.datn.aiservice.dto;

import com.datn.aiservice.entity.AiUserMemory;

import java.time.Instant;
import java.util.UUID;

public record MemoryView(UUID id, String content, String scope, String category, UUID projectId, Instant updatedAt) {
    public static MemoryView of(AiUserMemory m) {
        return new MemoryView(m.getId(), m.getContent(), m.getScope().name(), m.getCategory().name(),
                m.getProjectId(), m.getUpdatedAt());
    }
}
