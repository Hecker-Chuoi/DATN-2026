package com.datn.aiservice.repository;

import com.datn.aiservice.entity.AiConversation;
import com.datn.aiservice.enums.ConversationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AiConversationRepository extends JpaRepository<AiConversation, UUID> {
    List<AiConversation> findTop50ByUserIdAndStatusNotOrderByUpdatedAtDesc(String userId, ConversationStatus excluded);
    List<AiConversation> findTop50ByUserIdAndProjectIdAndStatusNotOrderByUpdatedAtDesc(String userId, UUID projectId, ConversationStatus excluded);
}
