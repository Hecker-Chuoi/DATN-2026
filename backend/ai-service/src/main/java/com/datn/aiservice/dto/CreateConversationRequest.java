package com.datn.aiservice.dto;

import java.util.UUID;

public record CreateConversationRequest(UUID workspaceId, UUID projectId) {}
