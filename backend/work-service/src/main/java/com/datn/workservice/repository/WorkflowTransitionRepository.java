package com.datn.workservice.repository;

import com.datn.workservice.entity.WorkflowTransition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkflowTransitionRepository extends JpaRepository<WorkflowTransition, UUID> {
    List<WorkflowTransition> findByWorkflowId(UUID workflowId);
    List<WorkflowTransition> findByWorkflowIdAndFromStatusId(UUID workflowId, UUID fromStatusId);
    Optional<WorkflowTransition> findByWorkflowIdAndFromStatusIdAndToStatusId(UUID workflowId, UUID fromStatusId, UUID toStatusId);
    Optional<WorkflowTransition> findByWorkflowIdAndFromStatusIdIsNullAndToStatusId(UUID workflowId, UUID toStatusId);
    Optional<WorkflowTransition> findByWorkflowIdAndNameIgnoreCase(UUID workflowId, String name);
    boolean existsByWorkflowIdAndFromStatusIdAndToStatusId(UUID workflowId, UUID fromStatusId, UUID toStatusId);
    boolean existsByWorkflowIdAndFromStatusIdIsNullAndToStatusId(UUID workflowId, UUID toStatusId);
    boolean existsByWorkflowIdAndNameIgnoreCase(UUID workflowId, String name);
    List<WorkflowTransition> findByFromStatusId(UUID fromStatusId);
    List<WorkflowTransition> findByToStatusId(UUID toStatusId);
}
