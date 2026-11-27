package com.datn.aiservice.repository;

import com.datn.aiservice.entity.AiPendingInteraction;
import com.datn.aiservice.enums.InteractionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiPendingInteractionRepository extends JpaRepository<AiPendingInteraction, UUID> {
    List<AiPendingInteraction> findByConversationIdAndStatus(UUID conversationId, InteractionStatus status);
    List<AiPendingInteraction> findByMessageIdOrderByCallIndexAsc(UUID messageId);
    List<AiPendingInteraction> findByConversationIdOrderByCallIndexAsc(UUID conversationId);
    Optional<AiPendingInteraction> findByIdAndConversationId(UUID id, UUID conversationId);
}
