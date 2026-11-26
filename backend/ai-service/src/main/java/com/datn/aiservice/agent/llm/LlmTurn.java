package com.datn.aiservice.agent.llm;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

/**
 * Một lượt trả lời của Claude. {@code modelMessage} là JSON nguyên bản của tin assistant (giữ thinking + signature)
 * để lưu và gửi lại; {@code model} là model đã trả lời (khác model yêu cầu khi có fallback phía server).
 */
public record LlmTurn(ObjectNode modelMessage, String text, List<ToolCall> calls, Usage usage, String model, String stopReason) {

    public record ToolCall(String id, String name, Map<String, Object> args) {}

    /** {@code inputTokens} = toàn bộ token đầu vào (kể cả phần đọc/ghi cache); {@code cachedTokens} = phần đọc từ cache. */
    public record Usage(int inputTokens, int outputTokens, int cachedTokens) {
        public static final Usage EMPTY = new Usage(0, 0, 0);
    }

    public boolean truncated() {
        return "max_tokens".equals(stopReason);
    }
}
