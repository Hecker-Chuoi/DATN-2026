package com.datn.aiservice.agent;

import com.datn.aiservice.enums.InteractionType;

import java.util.Map;
import java.util.UUID;

/** Payload sự kiện SSE {@code interaction_required}. */
public record InteractionRequest(UUID interactionId, InteractionType type, String toolName,
                                 String summary, Map<String, Object> payload) {}
