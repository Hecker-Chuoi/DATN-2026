package com.datn.aiservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Cấu hình agent (prefix {@code ai.agent}); giá trị mặc định đặt trong compact constructor. */
@ConfigurationProperties("ai.agent")
public record AgentProperties(
        String model,
        String lightModel,
        String geminiApiKey,
        int maxOutputTokens,
        int maxIterations,
        int maxToolResultChars,
        int historyCharBudget,
        int dailyRequestLimitPerUser,
        int retry429,
        int requestTimeoutMs,
        boolean titleWithLlm,
        String workServiceUrl,
        String toolsApiKey
) {
    public AgentProperties {
        if (model == null || model.isBlank()) model = "gemini-3.5-flash";
        if (lightModel == null || lightModel.isBlank()) lightModel = model;
        if (maxOutputTokens <= 0) maxOutputTokens = 4096;
        if (maxIterations <= 0) maxIterations = 8;
        if (maxToolResultChars <= 0) maxToolResultChars = 20000;
        if (historyCharBudget <= 0) historyCharBudget = 120000;
        if (dailyRequestLimitPerUser <= 0) dailyRequestLimitPerUser = 40;
        if (retry429 < 0) retry429 = 2;
        if (requestTimeoutMs <= 0) requestTimeoutMs = 60000;
        if (workServiceUrl == null || workServiceUrl.isBlank()) workServiceUrl = "http://localhost:8083";
    }

    public boolean hasGeminiKey() {
        return geminiApiKey != null && !geminiApiKey.isBlank();
    }
}
