package com.datn.workservice.service;

import com.datn.workservice.dto.request.CreateWorkItemCommentRequest;
import com.datn.workservice.dto.request.UpdateWorkItemCommentRequest;
import com.datn.workservice.dto.response.WorkItemCommentResponse;

import java.util.List;
import java.util.UUID;

public interface WorkItemCommentService {

    /**
     * Gets threaded/nested comments for a given work item
     */
    List<WorkItemCommentResponse> getCommentsTree(UUID workItemId);

    /**
     * Adds a new comment or a reply to an existing comment
     */
    WorkItemCommentResponse addComment(UUID workItemId, CreateWorkItemCommentRequest request, UUID userId);

    /**
     * Updates an existing comment
     */
    WorkItemCommentResponse updateComment(UUID workItemId, UUID commentId, UpdateWorkItemCommentRequest request, UUID userId);

    /**
     * Soft-deletes a comment
     */
    void deleteComment(UUID workItemId, UUID commentId, UUID userId);
}
