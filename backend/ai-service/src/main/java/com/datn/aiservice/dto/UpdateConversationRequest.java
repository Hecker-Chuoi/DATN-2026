package com.datn.aiservice.dto;

import jakarta.validation.constraints.Size;

public record UpdateConversationRequest(@Size(max = 255) String title, Boolean archived) {}
