package com.datn.aiservice.entity;

import com.datn.aiservice.enums.MessageRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_messages")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiMessage {

    @Id
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(nullable = false)
    private Integer seq;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MessageRole role;

    /** Gemini {@code Content.toJson()} nguyên bản (giữ thoughtSignature). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String content;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }
}
