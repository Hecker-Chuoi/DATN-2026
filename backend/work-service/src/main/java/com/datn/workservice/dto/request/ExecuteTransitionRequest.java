package com.datn.workservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * Request payload when user triggers a transition on a Work Item.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteTransitionRequest {

    @NotNull(message = "ID bước chuyển (transitionId) là bắt buộc")
    private UUID transitionId;

    /**
     * Optional resolution (e.g. DONE, FIXED, WON'T DO, DUPLICATE)
     */
    private String resolution;

    /**
     * Optional comment submitted during transition
     */
    private String comment;

    /**
     * Optional field updates captured during transition dialog (e.g. fixVersionId, assigneeId, customFields)
     */
    private Map<String, Object> fields;
}
