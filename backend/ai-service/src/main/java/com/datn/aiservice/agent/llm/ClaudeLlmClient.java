package com.datn.aiservice.agent.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.beta.messages.BetaCacheControlEphemeral;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaMessageParam;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaTextBlockParam;
import com.anthropic.models.beta.messages.BetaThinkingBlockBinding;
import com.anthropic.models.beta.messages.BetaThinkingConfigAdaptive;
import com.anthropic.models.beta.messages.BetaThinkingPrefixMismatchBehavior;
import com.anthropic.models.beta.messages.BetaTool;
import com.anthropic.models.beta.messages.BetaUsage;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.datn.aiservice.config.AgentProperties;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Gọi Claude Messages API (không stream). Agent tự điều khiển vòng lặp tool; SDK tự thử lại 429/5xx/529.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaudeLlmClient {

    /** Cho phép đặt {@code thinking.block_binding} (xem {@link #generate}). */
    static final String BETA_THINKING_BINDING = "thinking-binding-controls-2026-08-01";
    /** Server-side fallback khi model chính từ chối do bộ lọc an toàn. */
    static final String BETA_FALLBACK = "server-side-fallback-2026-07-01";

    /** Các model hỗ trợ {@code fallbacks: "default"} (Haiku không có fallback phía server). */
    private static final List<String> FALLBACK_MODELS =
            List.of("claude-opus-5-5", "claude-opus-5", "claude-sonnet-5-5", "claude-fable-5-1", "claude-fable-5-2");

    private final AnthropicClient client;
    private final AgentProperties props;

    /**
     * Một vòng của agent.
     * <ul>
     *   <li>{@code system} cố định cho mọi lượt; ngữ cảnh thay đổi theo lượt nằm trong tin người dùng, nên
     *       system + tools được cache và thinking cũ không bị vô hiệu hoá.</li>
     *   <li>{@code prefix_mismatch_behavior = drop_block}: khi lịch sử bị cắt/lược ({@code HistoryWindow}) hoặc bộ
     *       tool đổi (mở/đóng dự án), API bỏ các thinking block cũ thay vì trả 400.</li>
     * </ul>
     */
    public LlmTurn generate(String model, String system, List<ObjectNode> history, List<BetaTool> tools) {
        requireKey();
        MessageCreateParams.Builder b = MessageCreateParams.builder()
                .model(model)
                .maxTokens(props.maxOutputTokens())
                .systemOfBetaTextBlockParams(List.of(BetaTextBlockParam.builder().text(system).build()))
                .messages(history.stream().map(MessageJson::toParam).toList())
                .cacheControl(BetaCacheControlEphemeral.builder().build())
                .thinking(BetaThinkingConfigAdaptive.builder()
                        .blockBinding(BetaThinkingBlockBinding.builder()
                                .prefixMismatchBehavior(BetaThinkingPrefixMismatchBehavior.DROP_BLOCK)
                                .build())
                        .build())
                .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.of(props.effort())).build())
                .addBeta(BETA_THINKING_BINDING);
        if (FALLBACK_MODELS.contains(model)) {
            b.fallbacksDefault().addBeta(BETA_FALLBACK);
        }
        tools.forEach(b::addTool);

        BetaMessage res = client.beta().messages().create(b.build());
        return toTurn(res, model);
    }

    /** Việc nhẹ (tóm tắt, đặt tiêu đề): không tool, effort thấp. */
    public String generateText(String model, String prompt) {
        requireKey();
        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(4096)
                .messages(List.of(BetaMessageParam.ofUser(prompt)))
                .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.LOW).build())
                .build();
        BetaMessage res = client.beta().messages().create(params);
        if (isRefusal(res)) return "";
        StringBuilder sb = new StringBuilder();
        res.content().forEach(c -> c.text().ifPresent(t -> sb.append(t.text())));
        return sb.toString().trim();
    }

    private void requireKey() {
        if (!props.hasApiKey()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY chưa được cấu hình trên ai-service");
        }
    }

    private static boolean isRefusal(BetaMessage res) {
        return res.stopReason().map(r -> "refusal".equals(r.asString())).orElse(false);
    }

    private LlmTurn toTurn(BetaMessage res, String requestedModel) {
        String stop = res.stopReason().map(r -> r.asString()).orElse("");
        if ("refusal".equals(stop)) {
            throw new RefusalException("Claude từ chối trả lời yêu cầu này. Hãy diễn đạt lại câu hỏi.");
        }
        ObjectNode msg = MessageJson.fromResponse(res);

        // Bị cắt vì max_tokens: tool_use có thể chưa đủ tham số → bỏ đi để không chạy tool dở dang
        // (và để lịch sử không còn tool_use thiếu tool_result).
        if ("max_tokens".equals(stop) && msg.get("content") instanceof ArrayNode arr) {
            for (int i = arr.size() - 1; i >= 0; i--) {
                if ("tool_use".equals(arr.get(i).path("type").asText())) arr.remove(i);
            }
        }

        StringBuilder text = new StringBuilder();
        List<LlmTurn.ToolCall> calls = new ArrayList<>();
        for (ObjectNode block : MessageJson.blocks(msg)) {
            switch (MessageJson.type(block)) {
                case "text" -> text.append(block.path("text").asText(""));
                case "tool_use" -> calls.add(new LlmTurn.ToolCall(
                        block.path("id").asText(), block.path("name").asText(), MessageJson.toolInput(block)));
                default -> { /* thinking / redacted_thinking: chỉ lưu để gửi lại */ }
            }
        }

        BetaUsage u = res.usage();
        int cacheRead = u.cacheReadInputTokens().orElse(0L).intValue();
        int cacheWrite = u.cacheCreationInputTokens().orElse(0L).intValue();
        LlmTurn.Usage usage = new LlmTurn.Usage(
                (int) u.inputTokens() + cacheRead + cacheWrite, (int) u.outputTokens(), cacheRead);

        String answeredBy = Optional.ofNullable(res.model()).map(m -> m.asString()).orElse(requestedModel);
        return new LlmTurn(msg, text.toString(), calls, usage, answeredBy, stop);
    }

    /** Claude từ chối do bộ lọc an toàn (kể cả sau khi đã thử model dự phòng). */
    public static class RefusalException extends RuntimeException {
        public RefusalException(String message) {
            super(message);
        }
    }

    /** Thông báo thân thiện cho người dùng từ lỗi gọi Claude. */
    public static String friendlyMessage(Exception e) {
        if (e instanceof RefusalException || e instanceof IllegalStateException) {
            return e.getMessage();
        }
        if (e instanceof UnauthorizedException) {
            return "API key Claude không hợp lệ hoặc đã bị thu hồi. Kiểm tra lại ANTHROPIC_API_KEY.";
        }
        if (e instanceof PermissionDeniedException) {
            return "Tài khoản Claude không có quyền dùng model này.";
        }
        if (e instanceof RateLimitException) {
            return "AI đang vượt giới hạn tốc độ của tài khoản, vui lòng thử lại sau ít phút.";
        }
        if (e instanceof BadRequestException bad) {
            String msg = Optional.ofNullable(bad.getMessage()).orElse("");
            if (msg.toLowerCase().contains("credit balance")) {
                return "Tài khoản Claude đã hết credit. Nạp thêm tại platform.claude.com → Billing.";
            }
            return "Claude từ chối yêu cầu (400): " + msg;
        }
        if (e instanceof AnthropicServiceException svc) {
            if (svc.statusCode() == 529 || svc.statusCode() >= 500) {
                return "Dịch vụ Claude đang quá tải hoặc gián đoạn, vui lòng thử lại sau.";
            }
            return "Lỗi từ Claude (" + svc.statusCode() + "): " + Optional.ofNullable(svc.getMessage()).orElse("");
        }
        if (e instanceof AnthropicIoException) {
            return "Không kết nối được tới Claude hoặc quá thời gian chờ, vui lòng thử lại.";
        }
        return "Không gọi được mô hình AI: " + e.getMessage();
    }
}
