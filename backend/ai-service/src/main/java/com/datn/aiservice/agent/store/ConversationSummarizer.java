package com.datn.aiservice.agent.store;

import com.datn.aiservice.agent.llm.ClaudeLlmClient;
import com.datn.aiservice.agent.llm.MessageJson;
import com.datn.aiservice.config.AgentProperties;
import com.datn.aiservice.entity.AiConversation;
import com.datn.aiservice.entity.AiMessage;
import com.datn.aiservice.enums.MessageRole;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tóm tắt dồn: phần lịch sử bị {@link HistoryWindow} cắt khỏi cửa sổ được gộp vào
 * {@code ai_conversations.summary} bằng light-model, rồi đưa vào system prompt. Fail-safe.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationSummarizer {

    private final ClaudeLlmClient llm;
    private final ConversationStore store;
    private final AgentProperties props;

    /** @return summary hiện hành (có thể vừa được cập nhật); null nếu chưa có gì bị cắt. */
    public String summaryFor(AiConversation conv, List<AiMessage> all, int cutIndex) {
        if (cutIndex <= 0) return conv.getSummary();
        int maxDroppedSeq = all.get(cutIndex - 1).getSeq();
        Integer done = conv.getSummarySeq();
        if (done != null && done >= maxDroppedSeq) return conv.getSummary();

        StringBuilder transcript = new StringBuilder();
        for (AiMessage m : all.subList(0, cutIndex)) {
            if (done != null && m.getSeq() <= done) continue;
            String text = textOf(ConversationStore.toNode(m));
            if (!text.isBlank()) {
                transcript.append(m.getRole() == MessageRole.MODEL ? "Trợ lý: " : "Người dùng: ").append(text).append('\n');
            }
        }
        if (transcript.isEmpty()) {
            store.saveSummary(conv.getId(), conv.getSummary(), maxDroppedSeq);
            return conv.getSummary();
        }
        try {
            String prompt = "Tóm tắt ngắn gọn (tối đa 150 từ, tiếng Việt) hội thoại sau để làm ngữ cảnh cho các lượt tiếp theo. "
                    + "Giữ lại mã công việc, tên sprint, tên người và các quyết định đã chốt.\n"
                    + (conv.getSummary() == null ? "" : "Tóm tắt trước đó:\n" + conv.getSummary() + "\n\n")
                    + "Hội thoại:\n" + transcript;
            String summary = llm.generateText(props.lightModel(), prompt);
            if (summary.isBlank()) return conv.getSummary();
            store.saveSummary(conv.getId(), summary, maxDroppedSeq);
            return summary;
        } catch (Exception e) {
            log.warn("Không tóm tắt được hội thoại {}: {}", conv.getId(), e.getMessage());
            return conv.getSummary();
        }
    }

    /** Text của một tin: text thường (bỏ khối ngữ cảnh) + tên tool đã gọi; bỏ qua tool_result và thinking. */
    public static String textOf(ObjectNode message) {
        StringBuilder sb = new StringBuilder(MessageJson.visibleText(message));
        for (ObjectNode tu : MessageJson.blocksOfType(message, "tool_use")) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append("[gọi ").append(tu.path("name").asText("tool")).append(']');
        }
        return sb.toString().trim();
    }
}
