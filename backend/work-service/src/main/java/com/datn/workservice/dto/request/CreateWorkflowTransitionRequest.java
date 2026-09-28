package com.datn.workservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateWorkflowTransitionRequest {

    /**
     * Workflow ID (optional in body as it is provided via path variable /workflows/{workflowId}/transitions)
     */
    private UUID workflowId;

    /**
     * Source Status ID (null if initial creation transition)
     */
    private UUID fromStatusId;

    @NotNull(message = "ID trạng thái đích (toStatusId) là bắt buộc")
    private UUID toStatusId;

    @NotBlank(message = "Tên bước chuyển (transition name) không được để trống")
    @Size(max = 150, message = "Tên bước chuyển tối đa 150 ký tự")
    private String name;

    /**
     * Conditions config JSONB array (e.g. [{type: "ASSIGNEE_ONLY", params: {}}])
     */
    private List<Map<String, Object>> conditionsConfig;

    /**
     * Validators config JSONB array (e.g. [{type: "ALL_SUBTASKS_DONE", params: {}}])
     */
    private List<Map<String, Object>> validatorsConfig;

    /**
     * Post-functions config JSONB array (e.g. [{type: "EMIT_AUDIT_LOG", params: {}}])
     */
    private List<Map<String, Object>> postFunctionsConfig;
}
