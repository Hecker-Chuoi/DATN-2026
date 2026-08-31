package com.datn.workservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateWorkflowTransitionRequest {

    @NotNull(message = "ID quy trình (workflowId) là bắt buộc")
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
     * Conditions config JSONB (e.g. List of WorkflowConditionConfig or raw object)
     */
    private Object conditionsConfig;

    /**
     * Validators config JSONB (e.g. List of WorkflowValidatorConfig or raw object)
     */
    private Object validatorsConfig;

    /**
     * Post-functions config JSONB (e.g. List of WorkflowPostFunctionConfig or raw object)
     */
    private Object postFunctionsConfig;
}
