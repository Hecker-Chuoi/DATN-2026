package com.datn.workservice.repository;

import com.datn.workservice.entity.WorkItemCompletionRequest;
import com.datn.workservice.enums.CompletionRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkItemCompletionRequestRepository extends JpaRepository<WorkItemCompletionRequest, UUID> {

    Optional<WorkItemCompletionRequest> findFirstByWorkItemIdAndStatus(UUID workItemId, CompletionRequestStatus status);

    List<WorkItemCompletionRequest> findByWorkItemIdInAndStatus(Collection<UUID> workItemIds, CompletionRequestStatus status);

    List<WorkItemCompletionRequest> findByProjectIdAndStatusOrderByRequestedAtAsc(UUID projectId, CompletionRequestStatus status);

    List<WorkItemCompletionRequest> findByWorkItemIdOrderByRequestedAtDesc(UUID workItemId);
}
