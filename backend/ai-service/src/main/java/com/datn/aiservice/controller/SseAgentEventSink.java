package com.datn.aiservice.controller;

import com.datn.aiservice.agent.AgentEventSink;
import com.datn.aiservice.agent.InteractionRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hợp đồng SSE (frontend dựa vào tên sự kiện này):
 * text_delta, tool_started, tool_finished, interaction_required, notice, done, error.
 */
@Slf4j
public class SseAgentEventSink implements AgentEventSink {

    private final SseEmitter emitter;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean completed = new AtomicBoolean(false);

    public SseAgentEventSink(SseEmitter emitter) {
        this.emitter = emitter;
        emitter.onCompletion(() -> cancelled.set(true));
        emitter.onTimeout(() -> cancelled.set(true));
        emitter.onError(e -> cancelled.set(true));
    }

    @Override
    public void onTextDelta(String text) {
        send("text_delta", Map.of("text", text));
    }

    @Override
    public void onToolStarted(String id, String name, String summary) {
        send("tool_started", Map.of("id", id, "name", name, "summary", summary == null ? name : summary));
    }

    @Override
    public void onToolFinished(String id, String name, boolean ok) {
        send("tool_finished", Map.of("id", id, "name", name, "ok", ok));
    }

    @Override
    public void onInteractionRequired(InteractionRequest r) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("interactionId", r.interactionId());
        data.put("type", r.type().name());
        data.put("toolName", r.toolName());
        data.put("summary", r.summary());
        data.put("payload", r.payload());
        send("interaction_required", data);
    }

    @Override
    public void onNotice(String message) {
        send("notice", Map.of("message", message));
    }

    @Override
    public void onDone(String status) {
        send("done", Map.of("status", status));
        complete();
    }

    @Override
    public void onError(String message) {
        send("error", Map.of("message", message));
        complete();
    }

    @Override
    public boolean isCancelled() {
        return cancelled.get();
    }

    private void send(String event, Object data) {
        if (completed.get() || cancelled.get()) return;
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (Exception e) {
            // client đã đóng kết nối
            cancelled.set(true);
        }
    }

    private void complete() {
        if (completed.compareAndSet(false, true)) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // đã đóng
            }
        }
    }
}
