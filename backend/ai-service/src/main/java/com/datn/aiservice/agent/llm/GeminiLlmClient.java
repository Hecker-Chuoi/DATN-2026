package com.datn.aiservice.agent.llm;

import com.datn.aiservice.config.AgentProperties;
import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.types.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Gọi Gemini không stream, tắt automatic function calling (agent tự điều khiển vòng lặp), thử lại khi 429/503. */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiLlmClient {

    private static final long[] BACKOFF_MS = {2000, 5000, 10000};

    private final Client client;
    private final AgentProperties props;

    public LlmTurn generate(String model, List<Content> history, Content systemInstruction, Tool tools) {
        requireKey();
        GenerateContentConfig.Builder cfg = GenerateContentConfig.builder()
                .systemInstruction(systemInstruction)
                .maxOutputTokens(props.maxOutputTokens())
                .automaticFunctionCalling(AutomaticFunctionCallingConfig.builder().disable(true).build());
        if (tools != null) {
            cfg.tools(List.of(tools));
        }
        GenerateContentConfig config = cfg.build();
        GenerateContentResponse res = withRetry(() -> client.models.generateContent(model, history, config));
        return toTurn(res);
    }

    /** Việc nhẹ (đặt tiêu đề, tóm tắt): không tool, không system prompt. */
    public String generateText(String model, String prompt) {
        requireKey();
        GenerateContentConfig config = GenerateContentConfig.builder()
                .maxOutputTokens(1024)
                .build();
        GenerateContentResponse res = withRetry(() -> client.models.generateContent(model, prompt, config));
        String text = res.text();
        return text == null ? "" : text.trim();
    }

    private void requireKey() {
        if (!props.hasGeminiKey()) {
            throw new IllegalStateException("GEMINI_API_KEY chưa được cấu hình trên ai-service");
        }
    }

    private LlmTurn toTurn(GenerateContentResponse res) {
        Content modelContent = res.candidates()
                .filter(c -> !c.isEmpty())
                .flatMap(c -> c.get(0).content())
                .orElseGet(() -> Content.builder().role("model").parts(List.of(Part.fromText(""))).build());
        if (modelContent.role().isEmpty()) {
            modelContent = Content.builder().role("model").parts(modelContent.parts().orElse(List.of())).build();
        }

        StringBuilder text = new StringBuilder();
        List<FunctionCall> calls = new ArrayList<>();
        for (Part p : modelContent.parts().orElse(List.of())) {
            if (p.functionCall().isPresent()) {
                calls.add(p.functionCall().get());
            } else if (p.text().isPresent() && !p.thought().orElse(false)) {
                text.append(p.text().get());
            }
        }

        LlmTurn.Usage usage = res.usageMetadata()
                .map(u -> new LlmTurn.Usage(
                        u.promptTokenCount().orElse(0),
                        u.candidatesTokenCount().orElse(0),
                        u.thoughtsTokenCount().orElse(0),
                        u.cachedContentTokenCount().orElse(0)))
                .orElse(LlmTurn.Usage.EMPTY);

        return new LlmTurn(modelContent, text.toString(), calls, usage);
    }

    private <T> T withRetry(Supplier<T> call) {
        int attempts = 0;
        while (true) {
            try {
                return call.get();
            } catch (ApiException e) {
                boolean retryable = e.code() == 429 || e.code() == 503;
                if (!retryable || attempts >= props.retry429()) {
                    throw e;
                }
                long wait = BACKOFF_MS[Math.min(attempts, BACKOFF_MS.length - 1)];
                attempts++;
                log.warn("Gemini trả {} – thử lại lần {} sau {} ms", e.code(), attempts, wait);
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    /** Thông báo thân thiện cho người dùng từ lỗi Gemini. */
    public static String friendlyMessage(Exception e) {
        if (e instanceof ApiException api) {
            return switch (api.code()) {
                case 429 -> "AI đang quá tải hoặc đã hết hạn mức miễn phí, vui lòng thử lại sau ít phút.";
                case 503 -> "Dịch vụ AI tạm thời không sẵn sàng, vui lòng thử lại sau.";
                case 400, 401, 403 -> "Gemini từ chối yêu cầu (" + api.code() + "): " + Optional.ofNullable(api.message()).orElse("");
                default -> "Lỗi từ Gemini (" + api.code() + "): " + Optional.ofNullable(api.message()).orElse("");
            };
        }
        if (e instanceof IllegalStateException) {
            return e.getMessage();
        }
        return "Không gọi được mô hình AI: " + e.getMessage();
    }
}
