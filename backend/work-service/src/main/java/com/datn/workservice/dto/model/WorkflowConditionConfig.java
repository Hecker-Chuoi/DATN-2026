package com.datn.workservice.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Configuration model for Workflow Transition Conditions (e.g. ASSIGNEE_ONLY, PERMISSION_REQUIRED, ROLE_REQUIRED).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowConditionConfig {

    /**
     * Condition type key:
     * - ASSIGNEE_ONLY: Only the assigned user can execute this transition
     * - PERMISSION_REQUIRED: Requires specific project permission (e.g. ISSUE_TRANSITION, SPRINT_MANAGE)
     * - ROLE_REQUIRED: Requires a specific project role (e.g. ADMINISTRATOR, QA_LEAD)
     * - ONLY_CREATOR: Only the reporter can execute
     */
    private String type;

    /**
     * Optional configuration parameters for the condition.
     * Example: {"permissionKey": "ISSUE_TRANSITION"} or {"roleName": "ADMINISTRATOR"}
     */
    private Map<String, Object> params;
}
