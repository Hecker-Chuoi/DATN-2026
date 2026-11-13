package com.datn.aiservice.dto;

import java.util.List;
import java.util.Map;

/**
 * ASK_USER: {selectedValues, freeText}; APPROVE_ACTION: {approved, editedInput?}.
 * {@code context} giống khi gửi tin (để biết dự án/múi giờ khi chạy tiếp).
 */
public record InteractionAnswerRequest(
        List<String> selectedValues,
        String freeText,
        Boolean approved,
        Map<String, Object> editedInput,
        ChatContextRequest context
) {}
