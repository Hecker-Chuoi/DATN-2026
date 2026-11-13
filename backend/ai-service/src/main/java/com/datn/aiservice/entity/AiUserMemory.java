package com.datn.aiservice.entity;

import com.datn.aiservice.enums.MemoryCategory;
import com.datn.aiservice.enums.MemoryScope;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_user_memories")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiUserMemory {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MemoryScope scope;

    @Column(name = "project_id")
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MemoryCategory category;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "source_conversation_id")
    private UUID sourceConversationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
