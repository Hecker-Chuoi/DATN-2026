package com.datn.workservice.repository;

import com.datn.workservice.entity.WorkItemComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkItemCommentRepository extends JpaRepository<WorkItemComment, UUID> {
    List<WorkItemComment> findByWorkItemId(UUID workItemId);
    List<WorkItemComment> findByWorkItemIdOrderByCreatedAtDesc(UUID workItemId);
    List<WorkItemComment> findByWorkItemIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID workItemId);
    List<WorkItemComment> findByWorkItemIdInAndDeletedAtIsNull(List<UUID> workItemIds);
}
