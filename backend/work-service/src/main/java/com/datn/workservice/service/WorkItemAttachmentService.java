package com.datn.workservice.service;

import com.datn.workservice.dto.response.WorkItemAttachmentResponse;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface WorkItemAttachmentService {

    /**
     * Gets all attachments for a work item
     */
    List<WorkItemAttachmentResponse> getAttachments(UUID workItemId);

    /**
     * Uploads and stores a new attachment for a work item
     */
    WorkItemAttachmentResponse uploadAttachment(UUID workItemId, MultipartFile file, UUID userId);

    /**
     * Deletes an attachment and removes the stored file from disk
     */
    void deleteAttachment(UUID workItemId, UUID attachmentId, UUID userId);

    /**
     * Loads the attachment file resource for download
     */
    Resource loadAttachmentResource(UUID attachmentId);

    /**
     * Gets attachment entity by ID for metadata (filename, content-type)
     */
    WorkItemAttachmentResponse getAttachmentById(UUID attachmentId);
}
