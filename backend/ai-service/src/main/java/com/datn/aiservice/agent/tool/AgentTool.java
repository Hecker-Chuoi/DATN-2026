package com.datn.aiservice.agent.tool;

import java.util.Map;

public interface AgentTool {

    /** snake_case, duy nhất trong registry. */
    String name();

    /** Khi nào dùng, trả về gì (tiếng Việt) – Gemini đọc để chọn tool. */
    String description();

    /** JSON Schema: {"type":"object","properties":{…},"required":[…]}. */
    Map<String, Object> parameters();

    ToolKind kind();

    default boolean isAvailable(ToolContext ctx) {
        return true;
    }

    /** Chữ hiện trên chip tiến trình / thẻ duyệt. */
    default String summarize(Map<String, Object> args) {
        return name();
    }

    /** WRITE: chỉ gọi sau khi người dùng duyệt; INTERRUPT: không bao giờ gọi. */
    ToolResult execute(Map<String, Object> args, ToolContext ctx);
}
