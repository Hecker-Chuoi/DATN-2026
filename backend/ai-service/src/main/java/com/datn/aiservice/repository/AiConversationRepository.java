package com.datn.aiservice.repository;

import com.datn.aiservice.entity.AiConversation;
import com.datn.aiservice.enums.ConversationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AiConversationRepository extends JpaRepository<AiConversation, UUID> {
    /**
     * 50 hội thoại mới nhất của người dùng, trừ trạng thái thường là ARCHIVED
     * SELECT * FROM ai_conversations
     * WHERE user_id = ? AND status &lt;&gt; ?      -- status lưu dạng chuỗi, vd 'ARCHIVED'
     * ORDER BY updated_at DESC
     * FETCH FIRST 50 ROWS ONLY;
     */
    List<AiConversation> findTop50ByUserIdAndStatusNotOrderByUpdatedAtDesc(String userId, ConversationStatus excluded);

    /**
     * Như trên nhưng chỉ lấy hội thoại của một dự án:
     * SELECT * FROM ai_conversations
     * WHERE user_id = ? AND project_id = ? AND status &lt;&gt; ?
     * ORDER BY updated_at DESC
     * FETCH FIRST 50 ROWS ONLY;
     */
    List<AiConversation> findTop50ByUserIdAndProjectIdAndStatusNotOrderByUpdatedAtDesc(String userId, UUID projectId, ConversationStatus excluded);
}
