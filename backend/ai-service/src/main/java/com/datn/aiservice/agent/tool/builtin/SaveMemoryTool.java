package com.datn.aiservice.agent.tool.builtin;

import com.datn.aiservice.agent.tool.AgentTool;
import com.datn.aiservice.agent.tool.ToolContext;
import com.datn.aiservice.agent.tool.ToolKind;
import com.datn.aiservice.agent.tool.ToolResult;
import com.datn.aiservice.entity.AiUserMemory;
import com.datn.aiservice.enums.MemoryCategory;
import com.datn.aiservice.enums.MemoryScope;
import com.datn.aiservice.service.MemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Ghi bộ nhớ dài hạn của chính user. Chạy ngay (READ) vì chỉ ghi bảng của ai-service, nhưng phát notice. */
@Component
@RequiredArgsConstructor
public class SaveMemoryTool implements AgentTool {

    private final MemoryService memoryService;

    @Override public String name() { return "save_memory"; }
    @Override public ToolKind kind() { return ToolKind.READ; }

    @Override
    public String description() {
        return "Ghi nhớ lâu dài một sở thích, thói quen hoặc bối cảnh của người dùng (vd. \"luôn trả lời ngắn gọn\", "
                + "\"tôi phụ trách backend\"). Chỉ dùng khi người dùng yêu cầu rõ hoặc nhắc lại nhiều lần. "
                + "Không lưu dữ liệu công việc hay thông tin nhạy cảm. scope=PROJECT chỉ áp dụng cho dự án đang mở.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "content", Map.of("type", "string", "description", "Nội dung cần nhớ, một câu ngắn"),
                        "scope", Map.of("type", "string", "enum", List.of("GLOBAL", "PROJECT"), "description", "GLOBAL (mặc định) hoặc PROJECT"),
                        "category", Map.of("type", "string", "enum", List.of("PREFERENCE", "CONTEXT", "HABIT"), "description", "Loại ghi nhớ")),
                "required", List.of("content"));
    }

    @Override
    public String summarize(Map<String, Object> args) {
        return "Ghi nhớ: " + args.getOrDefault("content", "");
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
        if (!memoryService.isEnabled(ctx.userId())) {
            return ToolResult.error("Người dùng đã tắt bộ nhớ dài hạn, không lưu được");
        }
        String content = String.valueOf(args.getOrDefault("content", "")).trim();
        MemoryScope scope = parse(args.get("scope"), MemoryScope.class, MemoryScope.GLOBAL);
        MemoryCategory category = parse(args.get("category"), MemoryCategory.class, MemoryCategory.PREFERENCE);
        AiUserMemory saved = memoryService.save(ctx.userId(), content, scope, category, ctx.projectId(), ctx.conversationId());
        return ToolResult.ok("{\"memoryId\":\"" + saved.getId() + "\",\"saved\":true}", "Đã ghi nhớ: " + content);
    }

    static <E extends Enum<E>> E parse(Object v, Class<E> type, E dflt) {
        if (v == null) return dflt;
        try {
            return Enum.valueOf(type, String.valueOf(v).trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return dflt;
        }
    }
}
