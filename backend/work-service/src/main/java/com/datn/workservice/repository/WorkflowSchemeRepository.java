package com.datn.workservice.repository;

import com.datn.workservice.entity.WorkflowScheme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkflowSchemeRepository extends JpaRepository<WorkflowScheme, UUID> {
    List<WorkflowScheme> findByProjectId(UUID projectId);
}
