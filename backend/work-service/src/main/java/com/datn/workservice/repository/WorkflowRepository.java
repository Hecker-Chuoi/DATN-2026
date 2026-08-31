package com.datn.workservice.repository;

import com.datn.workservice.entity.Workflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, UUID>, JpaSpecificationExecutor<Workflow> {
    List<Workflow> findByProjectId(UUID projectId);
    List<Workflow> findByProjectIdOrProjectIdIsNull(UUID projectId);
    List<Workflow> findByIsDefaultTrue();
    Optional<Workflow> findByProjectIdAndIsDefaultTrue(UUID projectId);
    Optional<Workflow> findFirstByIsDefaultTrue();
    Optional<Workflow> findByName(String name);
}
