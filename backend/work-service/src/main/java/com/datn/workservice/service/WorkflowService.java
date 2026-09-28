package com.datn.workservice.service;

import com.datn.workservice.dto.request.*;
import com.datn.workservice.dto.response.*;
import com.datn.workservice.entity.Status;
import com.datn.workservice.entity.Workflow;
import com.datn.workservice.entity.WorkflowTransition;

import java.util.List;
import java.util.UUID;

public interface WorkflowService {

    /**
     * Retrieves full workflow details for a project (workflow metadata, statuses, and transitions).
     */
    WorkflowDetailResponse getProjectWorkflowDetail(UUID projectId);

    /**
     * Retrieves all workflows configured for a project.
     */
    List<WorkflowResponse> getProjectWorkflows(UUID projectId);

    /**
     * Retrieves a workflow by ID with full transition details.
     */
    WorkflowDetailResponse getWorkflowById(UUID workflowId);

    /**
     * Creates a new custom workflow.
     */
    WorkflowResponse createWorkflow(CreateWorkflowRequest request);

    /**
     * Updates an existing workflow.
     */
    WorkflowResponse updateWorkflow(UUID workflowId, UpdateWorkflowRequest request);

    /**
     * Adds a new transition to a workflow.
     */
    WorkflowTransitionResponse createTransition(UUID workflowId, CreateWorkflowTransitionRequest request);

    /**
     * Updates an existing transition.
     */
    WorkflowTransitionResponse updateTransition(UUID workflowId, UUID transitionId, UpdateWorkflowTransitionRequest request);

    /**
     * Deletes a transition from a workflow.
     */
    void deleteTransition(UUID workflowId, UUID transitionId);

    /**
     * Retrieves all statuses available/used in a project.
     */
    List<StatusResponse> getProjectStatuses(UUID projectId);

    /**
     * Creates a new status in the system.
     */
    StatusResponse createStatus(CreateStatusRequest request);

    /**
     * Updates an existing status in the system.
     */
    StatusResponse updateStatus(UUID statusId, UpdateStatusRequest request);

    /**
     * Deletes a status from the system if not used by any work item.
     */
    void deleteStatus(UUID statusId);

    /**
     * Retrieves the active workflow scheme and its issue type mappings for a project.
     */
    WorkflowSchemeResponse getProjectWorkflowScheme(UUID projectId);

    /**
     * Updates workflow scheme mappings for a project.
     */
    WorkflowSchemeResponse updateProjectWorkflowScheme(UUID projectId, UpdateWorkflowSchemeRequest request);
}
