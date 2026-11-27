package com.datn.aiservice.repository;

import com.datn.aiservice.entity.AiMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiMessageRepository extends JpaRepository<AiMessage, UUID> {
    List<AiMessage> findByConversationIdOrderBySeqAsc(UUID conversationId);
    Optional<AiMessage> findTopByConversationIdOrderBySeqDesc(UUID conversationId);

    /** Tìm trong JSON text của tin nhắn thuộc hội thoại của chính user (ILIKE, đơn giản, đủ cho demo). */
    @Query(value = """
            SELECT m.* FROM ai_messages m
            JOIN ai_conversations c ON c.id = m.conversation_id
            WHERE c.user_id = :userId AND m.content::text ILIKE :pattern
            ORDER BY m.created_at DESC LIMIT 10
            """, nativeQuery = true)
    List<AiMessage> searchByUser(@Param("userId") String userId, @Param("pattern") String pattern);
}
