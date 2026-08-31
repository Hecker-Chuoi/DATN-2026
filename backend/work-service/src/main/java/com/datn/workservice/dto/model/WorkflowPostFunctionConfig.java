package com.datn.workservice.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Configuration model for Workflow Transition Post-Functions (e.g. SET_RESOLVED_TIME, CLEAR_RESOLUTION, AUTO_ASSIGN_LEAD).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowPostFunctionConfig {

    /**
     * Post-function type key:
     * - SET_RESOLVED_TIME: Automatically sets resolved_at = CURRENT_TIMESTAMP
     * - CLEAR_RESOLUTION: Clears resolution and resolved_at fields (e.g. upon Reopen)
     * - AUTO_ASSIGN_LEAD: Auto-assigns the component lead or project lead
     * - AUTO_TRANSITION_PARENT: If all sibling sub-tasks are done, transition parent Story to IN_REVIEW or DONE
     * - EMIT_AUDIT_LOG: Records change in work_item_changelogs table
     */
    private String type;

    /**
     * Optional parameters for the post-function.
     */
    private Map<String, Object> params;
}
