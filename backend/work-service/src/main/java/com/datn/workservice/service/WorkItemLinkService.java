package com.datn.workservice.service;

import com.datn.workservice.dto.request.CreateWorkItemLinkRequest;
import com.datn.workservice.dto.response.WorkItemLinkResponse;

import java.util.List;
import java.util.UUID;

public interface WorkItemLinkService {

    /**
     * Retrieves all dependency links for a work item (both outward and inward directions)
     */
    List<WorkItemLinkResponse> getLinks(UUID workItemId);

    /**
     * Creates a dependency or relational link between two work items
     * Validates DAG / cycle detection via DependencyCycleDetector when linkType is BLOCKS or CAUSES
     */
    WorkItemLinkResponse createLink(UUID sourceItemId, CreateWorkItemLinkRequest request, UUID userId);

    /**
     * Deletes an existing link between work items
     */
    void deleteLink(UUID workItemId, UUID linkId, UUID userId);
}
