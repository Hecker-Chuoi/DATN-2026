package com.datn.aiservice.agent;

import com.anthropic.models.beta.messages.BetaTool;
import com.datn.aiservice.agent.llm.ClaudeLlmClient;
import com.datn.aiservice.agent.llm.LlmTurn;
import com.datn.aiservice.agent.llm.MessageJson;
import com.datn.aiservice.agent.prompt.SystemPromptBuilder;
import com.datn.aiservice.agent.store.ConversationStore;
import com.datn.aiservice.agent.store.ConversationSummarizer;
import com.datn.aiservice.agent.store.HistoryWindow;
import com.datn.aiservice.agent.tool.AgentTool;
import com.datn.aiservice.agent.tool.ToolContext;
import com.datn.aiservice.agent.tool.ToolRegistry;
import com.datn.aiservice.agent.tool.ToolResult;
import com.datn.aiservice.config.AgentProperties;
import com.datn.aiservice.dto.InteractionAnswerRequest;
import com.datn.aiservice.entity.AiConversation;
import com.datn.aiservice.entity.AiMessage;
import com.datn.aiservice.entity.AiPendingInteraction;
import com.datn.aiservice.enums.ConversationStatus;
import com.datn.aiservice.enums.InteractionStatus;
import com.datn.aiservice.enums.InteractionType;
import com.datn.aiservice.enums.MessageRole;
import com.datn.aiservice.repository.AiPendingInteractionRepository;
import com.datn.aiservice.service.QuotaService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Vòng lặp agent (Phụ lục F của kế hoạch): gọi Claude → chạy tool READ ngay → tool WRITE/INTERRUPT tạo
 * bản ghi chờ và dừng (WAITING_INPUT) → người dùng duyệt/trả lời → gửi đủ tool_result → tiếp tục.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentOrchestrator {

    private final ToolRegistry registry;
    private final ClaudeLlmClient llm;
    private final SystemPromptBuilder promptBuilder;
    private final ConversationStore store;
    private final HistoryWindow historyWindow;
    private final ConversationSummarizer summarizer;
    private final QuotaService quota;
    private final AiPendingInteractionRepository pendingRepo;
    private final AgentProperties props;
    private final ObjectMapper mapper;
    private final ExecutorService agentExecutor;

    // ponytail: khóa trong bộ nhớ theo hội thoại, đủ cho một instance; chuyển sang Redis lock nếu chạy nhiều bản.
    private final ConcurrentHashMap<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();

    /** Người dùng gửi tin mới. Tương tác đang chờ (nếu có) bị bỏ qua và trả lời cho model là "Người dùng bỏ qua". */
    public void chat(ToolContext ctx, String userText, AgentEventSink sink) {
        runLocked(ctx.conversationId(), sink, () -> {
            UUID convId = ctx.conversationId();
            List<ObjectNode> blocks = new ArrayList<>();
            List<AiPendingInteraction> pending = pendingRepo.findByConversationIdAndStatus(convId, InteractionStatus.PENDING);
            if (!pending.isEmpty()) {
                Instant now = Instant.now();
                for (AiPendingInteraction p : pending) {
                    p.setStatus(InteractionStatus.EXPIRED);
                    p.setResponse(json(Map.of("result", "Người dùng bỏ qua, không thực hiện")));
                    p.setResolvedAt(now);
                }
                pendingRepo.saveAll(pending);
                // tool_result phải đứng trước text trong tin USER
                blocks.addAll(toolResultsFor(pending.get(0).getMessageId()));
            }
            blocks.add(MessageJson.text(promptBuilder.context(ctx)));
            blocks.add(MessageJson.text(userText));
            store.append(convId, MessageRole.USER, MessageJson.user(blocks), null, null);
            store.setStatus(convId, ConversationStatus.ACTIVE);
            ensureTitle(convId, userText);
            runLoop(ctx, sink);
        });
    }

    /** Người dùng duyệt / từ chối / trả lời một tương tác đang chờ. */
    public void resume(ToolContext ctx, UUID interactionId, InteractionAnswerRequest answer, AgentEventSink sink) {
        runLocked(ctx.conversationId(), sink, () -> {
            UUID convId = ctx.conversationId();
            AiPendingInteraction pi = pendingRepo.findByIdAndConversationId(interactionId, convId).orElse(null);
            if (pi == null) {
                sink.onError("Không tìm thấy tương tác đang chờ");
                return;
            }
            if (pi.getStatus() != InteractionStatus.PENDING) {
                sink.onError("Tương tác này đã được xử lý trước đó");
                return;
            }

            Map<String, Object> response;
            if (pi.getType() == InteractionType.ASK_USER) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("selectedValues", answer.selectedValues() == null ? List.of() : answer.selectedValues());
                if (answer.freeText() != null && !answer.freeText().isBlank()) {
                    r.put("freeText", answer.freeText().trim());
                }
                response = Map.of("result", r);
                pi.setStatus(InteractionStatus.ANSWERED);
            } else if (Boolean.TRUE.equals(answer.approved())) {
                AgentTool tool = registry.find(pi.getToolName()).orElse(null);
                Map<String, Object> args = answer.editedInput() != null && !answer.editedInput().isEmpty()
                        ? answer.editedInput() : payloadArgs(pi);
                if (tool == null) {
                    response = Map.of("error", "Không có công cụ tên " + pi.getToolName());
                } else {
                    sink.onToolStarted(pi.getCallId(), tool.name(), tool.summarize(args));
                    ToolResult r = safeExecute(tool, args, ctx);
                    sink.onToolFinished(pi.getCallId(), tool.name(), !r.error());
                    if (r.notice() != null) sink.onNotice(r.notice());
                    response = toResponse(r);
                }
                pi.setStatus(InteractionStatus.APPROVED);
            } else {
                response = Map.of("result", "Người dùng đã từ chối, không thực hiện");
                pi.setStatus(InteractionStatus.REJECTED);
            }
            pi.setResponse(json(response));
            pi.setResolvedAt(Instant.now());
            pendingRepo.save(pi);

            boolean stillPending = pendingRepo.findByMessageIdOrderByCallIndexAsc(pi.getMessageId()).stream()
                    .anyMatch(r -> r.getStatus() == InteractionStatus.PENDING);
            if (stillPending) {
                sink.onDone("WAITING_INPUT");
                return;
            }
            store.append(convId, MessageRole.USER, MessageJson.user(toolResultsFor(pi.getMessageId())), null, null);
            store.setStatus(convId, ConversationStatus.ACTIVE);
            runLoop(ctx, sink);
        });
    }

    // ─── Vòng lặp chính ────────────────────────────────────────────────────────

    private void runLoop(ToolContext ctx, AgentEventSink sink) {
        UUID convId = ctx.conversationId();
        List<AgentTool> tools = registry.availableFor(ctx);
        List<BetaTool> claudeTools = ToolRegistry.toClaudeTools(tools);
        String system = promptBuilder.system();

        for (int iteration = 0; iteration < props.maxIterations(); iteration++) {
            if (sink.isCancelled()) {
                sink.onDone("CANCELLED");
                return;
            }
            if (!quota.tryConsume(ctx.userId())) {
                sink.onError("Bạn đã dùng hết lượt hỏi AI hôm nay. Vui lòng quay lại vào ngày mai.");
                return;
            }

            AiConversation conv = store.getOwned(convId, ctx.userId());
            List<AiMessage> all = store.messages(convId);
            List<ObjectNode> history = all.stream().map(ConversationStore::toNode).toList();
            HistoryWindow.Result window = historyWindow.apply(history);
            String summary = summarizer.summaryFor(conv, all, window.cutIndex());
            List<ObjectNode> messages = window.cutIndex() > 0 && summary != null && !summary.isBlank()
                    ? withSummary(window.window(), summary) : window.window();

            LlmTurn turn;
            try {
                turn = llm.generate(props.model(), system, messages, claudeTools);
            } catch (Exception e) {
                log.warn("Gọi Claude thất bại (conversation {}): {}", convId, e.toString());
                sink.onError(ClaudeLlmClient.friendlyMessage(e));
                return;
            }

            AiMessage modelMsg = store.append(convId, MessageRole.MODEL, turn.modelMessage(),
                    turn.usage().inputTokens(), turn.usage().outputTokens());
            quota.record(ctx.userId(), convId, turn.model(), turn.usage());

            if (!turn.text().isBlank()) {
                sink.onTextDelta(turn.text());
            }
            if (turn.truncated()) {
                sink.onNotice("Câu trả lời bị cắt vì vượt giới hạn độ dài. Hãy hỏi tiếp để tôi trả lời nốt.");
            }
            if (turn.calls().isEmpty()) {
                sink.onDone("COMPLETED");
                return;
            }

            List<ObjectNode> responses = new ArrayList<>();
            List<AiPendingInteraction> rows = new ArrayList<>();
            boolean hasPending = false;

            for (int idx = 0; idx < turn.calls().size(); idx++) {
                LlmTurn.ToolCall call = turn.calls().get(idx);
                String callId = call.id();
                String name = call.name();
                Map<String, Object> args = call.args();
                AgentTool tool = tools.stream().filter(t -> t.name().equals(name)).findFirst().orElse(null);

                if (tool == null) {
                    Map<String, Object> resp = Map.of("error", "Không có công cụ tên " + name + " trong ngữ cảnh hiện tại");
                    responses.add(toolResult(callId, resp));
                    rows.add(row(convId, modelMsg, callId, idx, name, InteractionType.READ_RESULT,
                            InteractionStatus.ANSWERED, json(args), json(resp)));
                    continue;
                }
                switch (tool.kind()) {
                    case READ -> {
                        sink.onToolStarted(callId, name, tool.summarize(args));
                        ToolResult r = safeExecute(tool, args, ctx);
                        sink.onToolFinished(callId, name, !r.error());
                        if (r.notice() != null) sink.onNotice(r.notice());
                        Map<String, Object> resp = toResponse(r);
                        responses.add(toolResult(callId, resp));
                        rows.add(row(convId, modelMsg, callId, idx, name, InteractionType.READ_RESULT,
                                InteractionStatus.ANSWERED, json(args), json(resp)));
                    }
                    case WRITE -> {
                        hasPending = true;
                        Map<String, Object> payload = new LinkedHashMap<>();
                        payload.put("summary", tool.summarize(args));
                        payload.put("args", args);
                        rows.add(row(convId, modelMsg, callId, idx, name, InteractionType.APPROVE_ACTION,
                                InteractionStatus.PENDING, json(payload), null));
                    }
                    case INTERRUPT -> {
                        hasPending = true;
                        rows.add(row(convId, modelMsg, callId, idx, name, InteractionType.ASK_USER,
                                InteractionStatus.PENDING, json(args), null));
                    }
                }
            }

            if (hasPending) {
                pendingRepo.saveAll(rows);
                store.setStatus(convId, ConversationStatus.WAITING_INPUT);
                for (AiPendingInteraction r : rows) {
                    if (r.getStatus() != InteractionStatus.PENDING) continue;
                    Map<String, Object> payload = readMap(r.getPayload());
                    String summary2 = r.getType() == InteractionType.ASK_USER
                            ? String.valueOf(payload.getOrDefault("question", r.getToolName()))
                            : String.valueOf(payload.getOrDefault("summary", r.getToolName()));
                    sink.onInteractionRequired(new InteractionRequest(r.getId(), r.getType(), r.getToolName(), summary2, payload));
                }
                sink.onDone("WAITING_INPUT");
                return;
            }

            store.append(convId, MessageRole.USER, MessageJson.user(responses), null, null);
        }

        sink.onNotice("Đã đạt giới hạn " + props.maxIterations() + " vòng xử lý, tạm dừng. Hãy hỏi tiếp để tôi làm tiếp.");
        sink.onDone("ITERATION_LIMIT");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void runLocked(UUID convId, AgentEventSink sink, Runnable body) {
        ReentrantLock lock = locks.computeIfAbsent(convId, k -> new ReentrantLock());
        if (!lock.tryLock()) {
            sink.onError("Hội thoại đang được xử lý, vui lòng chờ lượt trước hoàn tất.");
            return;
        }
        try {
            body.run();
        } catch (Exception e) {
            log.error("Lỗi vòng lặp agent (conversation {})", convId, e);
            sink.onError("Đã xảy ra lỗi khi xử lý: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    private ToolResult safeExecute(AgentTool tool, Map<String, Object> args, ToolContext ctx) {
        try {
            return tool.execute(args, ctx).truncated(props.maxToolResultChars());
        } catch (Exception e) {
            log.warn("Tool {} lỗi: {}", tool.name(), e.toString());
            return ToolResult.error("Công cụ " + tool.name() + " lỗi: " + e.getMessage());
        }
    }

    /** ToolResult → FunctionResponse.response: {"result": …} hoặc {"error": …}; nội dung JSON được gửi dạng cấu trúc. */
    private Map<String, Object> toResponse(ToolResult r) {
        if (r.error()) return Map.of("error", r.content());
        String c = r.content() == null ? "" : r.content().trim();
        if (c.startsWith("{") || c.startsWith("[")) {
            try {
                return Map.of("result", mapper.readValue(c, Object.class));
            } catch (Exception ignored) {
                // không phải JSON hợp lệ (vd. đã bị cắt) → gửi dạng chuỗi
            }
        }
        return Map.of("result", c);
    }

    /** Dựng đủ tool_result cho mọi tool_use của tin MODEL, đúng thứ tự call_index (callId = id của tool_use). */
    private List<ObjectNode> toolResultsFor(UUID messageId) {
        List<ObjectNode> out = new ArrayList<>();
        for (AiPendingInteraction row : pendingRepo.findByMessageIdOrderByCallIndexAsc(messageId)) {
            Map<String, Object> response = row.getResponse() == null
                    ? Map.of("result", "Người dùng bỏ qua, không thực hiện") : readMap(row.getResponse());
            out.add(toolResult(row.getCallId(), response));
        }
        return out;
    }

    private ObjectNode toolResult(String toolUseId, Map<String, Object> response) {
        return MessageJson.toolResult(toolUseId, json(response), response.containsKey("error"));
    }

    /** Lịch sử vừa bị cắt: chèn tóm tắt phần bị cắt vào tin đầu tiên của cửa sổ (sau các tool_result nếu có). */
    private static List<ObjectNode> withSummary(List<ObjectNode> window, String summary) {
        if (window.isEmpty()) return window;
        List<ObjectNode> out = new ArrayList<>(window);
        List<ObjectNode> blocks = new ArrayList<>(MessageJson.blocks(window.get(0)));
        int at = (int) blocks.stream().filter(b -> "tool_result".equals(MessageJson.type(b))).count();
        blocks.add(at, MessageJson.text(SystemPromptBuilder.summaryBlock(summary)));
        out.set(0, MessageJson.user(blocks));
        return out;
    }

    private static AiPendingInteraction row(UUID convId, AiMessage msg, String callId, int idx, String tool,
                                            InteractionType type, InteractionStatus status, String payload, String response) {
        return AiPendingInteraction.builder()
                .conversationId(convId).messageId(msg.getId()).callId(callId).callIndex(idx).toolName(tool)
                .type(type).status(status).payload(payload).response(response)
                .resolvedAt(status == InteractionStatus.PENDING ? null : Instant.now())
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> payloadArgs(AiPendingInteraction pi) {
        Object args = readMap(pi.getPayload()).get("args");
        return args instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private void ensureTitle(UUID convId, String userText) {
        String t = userText.strip().replaceAll("\\s+", " ");
        store.setTitleIfEmpty(convId, t.length() > 60 ? t.substring(0, 57) + "…" : t);
        if (props.titleWithLlm()) {
            agentExecutor.submit(() -> {
                try {
                    String title = llm.generateText(props.lightModel(),
                            "Đặt một tiêu đề ngắn (tối đa 8 từ, tiếng Việt, không dấu ngoặc kép) cho hội thoại bắt đầu bằng câu: " + t);
                    if (!title.isBlank()) store.setTitle(convId, title.lines().findFirst().orElse(title).trim());
                } catch (Exception e) {
                    log.debug("Không đặt được tiêu đề bằng LLM: {}", e.getMessage());
                }
            });
        }
    }

    private String json(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException("Không serialize được JSON", e);
        }
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            return mapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }
}
