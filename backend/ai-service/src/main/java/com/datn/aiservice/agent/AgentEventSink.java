package com.datn.aiservice.agent;

/** Nơi nhận sự kiện của vòng lặp agent (SSE cho frontend, bộ gom cho eval). */
public interface AgentEventSink {
    void onTextDelta(String text);
    void onToolStarted(String id, String name, String summary);
    void onToolFinished(String id, String name, boolean ok);
    void onInteractionRequired(InteractionRequest request);
    void onNotice(String message);
    /** status: COMPLETED | WAITING_INPUT | ITERATION_LIMIT | CANCELLED */
    void onDone(String status);
    void onError(String message);
    boolean isCancelled();
}
