package com.datn.workservice.repository;

import com.datn.workservice.entity.WorkItemAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkItemAttachmentRepository extends JpaRepository<WorkItemAttachment, UUID> {
    List<WorkItemAttachment> findByWorkItemId(UUID workItemId);
    List<WorkItemAttachment> findByWorkItemIdOrderByUploadedAtDesc(UUID workItemId);
}
