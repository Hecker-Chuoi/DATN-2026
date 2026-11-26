package com.datn.aiservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Cấu hình agent (prefix {@code ai.agent}); giá trị mặc định đặt trong compact constructor. */
@ConfigurationProperties("ai.agent")
public record AgentProperties(
        String model,
        String lightModel,
        String anthropicApiKey,
        String effort,
        int maxOutputTokens,
        int maxIterations,
        int maxToolResultChars,
        int historyCharBudget,
        int dailyRequestLimitPerUser,
        int requestTimeoutMs,
        boolean titleWithLlm,
        String workServiceUrl,
        String toolsApiKey
) {
    public AgentProperties {
        if (model == null || model.isBlank()) model = "claude-opus-5-5";
        if (lightModel == null || lightModel.isBlank()) lightModel = "claude-haiku-5-5";
        if (effort == null || effort.isBlank()) effort = "medium";
        // Thinking của Claude tính vào max_tokens nên không để thấp như trước.
        if (maxOutputTokens <= 0) maxOutputTokens = 16000;
        if (maxIterations <= 0) maxIterations = 8;
        if (maxToolResultChars <= 0) maxToolResultChars = 20000;
        if (historyCharBudget <= 0) historyCharBudget = 120000;
        if (dailyRequestLimitPerUser <= 0) dailyRequestLimitPerUser = 40;
        if (requestTimeoutMs <= 0) requestTimeoutMs = 120000;
        if (workServiceUrl == null || workServiceUrl.isBlank()) workServiceUrl = "http://localhost:8083";
    }

    public boolean hasApiKey() {
        return anthropicApiKey != null && !anthropicApiKey.isBlank();
    }
}
