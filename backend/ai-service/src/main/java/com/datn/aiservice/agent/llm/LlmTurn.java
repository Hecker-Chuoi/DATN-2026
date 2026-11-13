package com.datn.aiservice.agent.llm;

import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;

import java.util.List;

/** Một lượt trả lời của Gemini. {@code modelContent} giữ nguyên để lưu và gửi lại (có thoughtSignature). */
public record LlmTurn(Content modelContent, String text, List<FunctionCall> calls, Usage usage) {

    public record Usage(int inputTokens, int outputTokens, int thinkingTokens, int cachedTokens) {
        public static final Usage EMPTY = new Usage(0, 0, 0, 0);
    }
}
