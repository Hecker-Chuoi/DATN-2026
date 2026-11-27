package com.datn.aiservice.entity;

import com.datn.aiservice.enums.InteractionStatus;
import com.datn.aiservice.enums.InteractionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_pending_interactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiPendingInteraction {

    @Id
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    /** Tin nhắn MODEL chứa lời gọi hàm. */
    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "call_id", nullable = false, length = 64)
    private String callId;

    @Column(name = "call_index", nullable = false)
    private Integer callIndex;

    @Column(name = "tool_name", nullable = false, length = 64)
    private String toolName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InteractionType type;

    /** JSON: {summary, args} với thẻ duyệt; nguyên input của ask_user; với READ_RESULT là args. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InteractionStatus status;

    /** JSON của FunctionResponse.response ({"result":...} hoặc {"error":...}) sau khi xử lý. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String response;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = InteractionStatus.PENDING;
        if (createdAt == null) createdAt = Instant.now();
    }
}
