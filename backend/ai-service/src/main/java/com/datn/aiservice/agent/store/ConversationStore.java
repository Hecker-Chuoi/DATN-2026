package com.datn.aiservice.agent.store;

import com.datn.aiservice.entity.AiConversation;
import com.datn.aiservice.entity.AiMessage;
import com.datn.aiservice.enums.ConversationStatus;
import com.datn.aiservice.enums.MessageRole;
import com.datn.aiservice.repository.AiConversationRepository;
import com.datn.aiservice.repository.AiMessageRepository;
import com.datn.common.exception.ForbiddenException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.aiservice.agent.llm.MessageJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Đọc/ghi ai_conversations + ai_messages. {@code content} là JSON một tin theo Messages API của Claude (xem {@link MessageJson}). */
@Service
@RequiredArgsConstructor
public class ConversationStore {

    private final AiConversationRepository conversationRepo;
    private final AiMessageRepository messageRepo;

    @Transactional
    public AiConversation create(String userId, UUID workspaceId, UUID projectId) {
        return conversationRepo.save(AiConversation.builder()
                .userId(userId).workspaceId(workspaceId).projectId(projectId)
                .status(ConversationStatus.ACTIVE).build());
    }

    /** Hội thoại phải tồn tại và thuộc về user, nếu không ném lỗi 404/403. */
    @Transactional(readOnly = true)
    public AiConversation getOwned(UUID id, String userId) {
        AiConversation c = conversationRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hội thoại"));
        if (!c.getUserId().equals(userId)) {
            throw new ForbiddenException("Hội thoại này không thuộc về bạn");
        }
        return c;
    }

    @Transactional(readOnly = true)
    public List<AiMessage> messages(UUID conversationId) {
        return messageRepo.findByConversationIdOrderBySeqAsc(conversationId);
    }

    @Transactional(readOnly = true)
    public AiMessage message(UUID messageId) {
        return messageRepo.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tin nhắn"));
    }

    public static ObjectNode toNode(AiMessage m) {
        return MessageJson.parse(m.getContent());
    }

    @Transactional
    public void setTitle(UUID conversationId, String title) {
        conversationRepo.findById(conversationId).ifPresent(c -> {
            c.setTitle(title);
            conversationRepo.save(c);
        });
    }

    @Transactional
    public AiMessage append(UUID conversationId, MessageRole role, ObjectNode content, Integer inputTokens, Integer outputTokens) {
        int seq = messageRepo.findTopByConversationIdOrderBySeqDesc(conversationId)
                .map(m -> m.getSeq() + 1).orElse(1);
        AiMessage saved = messageRepo.save(AiMessage.builder()
                .conversationId(conversationId).seq(seq).role(role)
                .content(content.toString()).inputTokens(inputTokens).outputTokens(outputTokens)
                .build());
        conversationRepo.findById(conversationId).ifPresent(c -> {
            c.setUpdatedAt(Instant.now());
            conversationRepo.save(c);
        });
        return saved;
    }

    @Transactional
    public void setStatus(UUID conversationId, ConversationStatus status) {
        conversationRepo.findById(conversationId).ifPresent(c -> {
            c.setStatus(status);
            conversationRepo.save(c);
        });
    }

    @Transactional
    public void setTitleIfEmpty(UUID conversationId, String title) {
        conversationRepo.findById(conversationId).ifPresent(c -> {
            if (c.getTitle() == null || c.getTitle().isBlank()) {
                c.setTitle(title);
                conversationRepo.save(c);
            }
        });
    }

    @Transactional
    public void saveSummary(UUID conversationId, String summary, int summarySeq) {
        conversationRepo.findById(conversationId).ifPresent(c -> {
            c.setSummary(summary);
            c.setSummarySeq(summarySeq);
            conversationRepo.save(c);
        });
    }

    @Transactional
    public AiConversation save(AiConversation c) {
        return conversationRepo.save(c);
    }

    @Transactional(readOnly = true)
    public List<AiConversation> listOwned(String userId, UUID projectId) {
        return projectId == null
                ? conversationRepo.findTop50ByUserIdAndStatusNotOrderByUpdatedAtDesc(userId, ConversationStatus.ARCHIVED)
                : conversationRepo.findTop50ByUserIdAndProjectIdAndStatusNotOrderByUpdatedAtDesc(userId, projectId, ConversationStatus.ARCHIVED);
    }
}
