package com.datn.workservice.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Configuration model for Workflow Transition Validators (e.g. FIELD_REQUIRED, ALL_SUBTASKS_DONE).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowValidatorConfig {

    /**
     * Validator type key:
     * - FIELD_REQUIRED: Requires specific fields to be populated (e.g. resolution, fixVersionId, description)
     * - ALL_SUBTASKS_DONE: Validates that all sub-tasks under this work item are in status category DONE
     * - ESTIMATE_REQUIRED: Requires original estimate / story points to be present
     */
    private String type;

    /**
     * Parameters for the validator.
     * Example: {"fields": ["resolution", "comment"]}
     */
    private Map<String, Object> params;
}
