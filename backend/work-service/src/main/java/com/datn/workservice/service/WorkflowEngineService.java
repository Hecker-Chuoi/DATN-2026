package com.datn.workservice.service;

import com.datn.workservice.dto.request.ExecuteTransitionRequest;
import com.datn.workservice.dto.response.AvailableTransitionResponse;
import com.datn.workservice.entity.WorkItem;
import com.datn.workservice.entity.Workflow;

import java.util.List;
import java.util.UUID;

public interface WorkflowEngineService {

    /**
     * Resolves the active Workflow entity for a given project and work item type.
     * Hierarchy: Project Scheme -> Scheme Mapping -> Project Default -> Global Default.
     */
    Workflow resolveWorkflow(UUID projectId, UUID workItemTypeId);

    /**
     * Retrieves all available transitions from the current status of a work item,
     * evaluating all configured transition conditions against the current user.
     */
    List<AvailableTransitionResponse> getAvailableTransitions(UUID workItemId, UUID currentUserId);

    /**
     * Executes a workflow transition on a work item, evaluating conditions,
     * enforcing validators, updating status/resolution, running post-functions,
     * and recording changelog entries.
     */
    WorkItem executeTransition(UUID workItemId, UUID transitionId, ExecuteTransitionRequest request, UUID currentUserId);
}
