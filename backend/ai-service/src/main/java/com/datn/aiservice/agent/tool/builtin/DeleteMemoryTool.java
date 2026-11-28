package com.datn.aiservice.agent.tool.builtin;

import com.datn.aiservice.agent.tool.AgentTool;
import com.datn.aiservice.agent.tool.ToolContext;
import com.datn.aiservice.agent.tool.ToolKind;
import com.datn.aiservice.agent.tool.ToolResult;
import com.datn.aiservice.service.MemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DeleteMemoryTool implements AgentTool {

    private final MemoryService memoryService;

    @Override public String name() { return "delete_memory"; }
    @Override public ToolKind kind() { return ToolKind.READ; }

    @Override
    public String description() {
        return "Xóa một mục ghi nhớ của người dùng khi họ yêu cầu quên. memoryId lấy trong khối <user_memory> của ngữ cảnh.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "memoryId", Map.of("type", "string", "description", "UUID của mục ghi nhớ")),
                "required", List.of("memoryId"));
    }

    @Override
    public String summarize(Map<String, Object> args) {
        return "Xóa ghi nhớ";
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
        UUID id;
        try {
            id = UUID.fromString(String.valueOf(args.get("memoryId")));
        } catch (Exception e) {
            return ToolResult.error("memoryId không hợp lệ");
        }
        memoryService.delete(ctx.userId(), id);
        return ToolResult.ok("{\"deleted\":true}", "Đã xóa một mục ghi nhớ");
    }
}
