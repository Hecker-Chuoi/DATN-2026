package com.datn.aiservice.agent.tool.builtin;

import com.datn.aiservice.agent.store.ConversationStore;
import com.datn.aiservice.agent.store.ConversationSummarizer;
import com.datn.aiservice.agent.tool.AgentTool;
import com.datn.aiservice.agent.tool.ToolContext;
import com.datn.aiservice.agent.tool.ToolKind;
import com.datn.aiservice.agent.tool.ToolResult;
import com.datn.aiservice.entity.AiMessage;
import com.datn.aiservice.repository.AiConversationRepository;
import com.datn.aiservice.repository.AiMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tìm ILIKE trong các hội thoại cũ của chính user, tối đa 10 đoạn trích. */
@Component
@RequiredArgsConstructor
public class SearchPastConversationsTool implements AgentTool {

    private final AiMessageRepository messageRepo;
    private final AiConversationRepository conversationRepo;
    private final ObjectMapper mapper;

    @Override public String name() { return "search_past_conversations"; }
    @Override public ToolKind kind() { return ToolKind.READ; }

    @Override
    public String description() {
        return "Tìm lại nội dung các hội thoại trước đây của người dùng với trợ lý (vd. \"hôm trước tôi hỏi gì về sprint 3\"). "
                + "Trả về tối đa 10 đoạn trích kèm tiêu đề hội thoại và thời gian.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of("type", "string", "description", "Từ khóa cần tìm")),
                "required", List.of("query"));
    }

    @Override
    public String summarize(Map<String, Object> args) {
        return "Tìm hội thoại cũ: " + args.getOrDefault("query", "");
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
        String q = String.valueOf(args.getOrDefault("query", "")).trim();
        if (q.isEmpty()) return ToolResult.error("Thiếu từ khóa");
        List<Map<String, Object>> out = new ArrayList<>();
        for (AiMessage m : messageRepo.searchByUser(ctx.userId(), "%" + q + "%")) {
            if (m.getConversationId().equals(ctx.conversationId())) continue;
            String text = ConversationSummarizer.textOf(ConversationStore.toContent(m));
            if (text.isBlank()) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("conversationId", m.getConversationId());
            row.put("title", conversationRepo.findById(m.getConversationId()).map(c -> c.getTitle()).orElse(null));
            row.put("at", m.getCreatedAt().toString());
            row.put("role", m.getRole().name());
            row.put("snippet", text.length() > 300 ? text.substring(0, 300) + "…" : text);
            out.add(row);
        }
        try {
            return ToolResult.ok(mapper.writeValueAsString(Map.of("totalFound", out.size(), "items", out)));
        } catch (Exception e) {
            return ToolResult.error("Không serialize được kết quả");
        }
    }
}
