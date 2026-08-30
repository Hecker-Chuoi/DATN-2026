package com.datn.workservice.repository;

import com.datn.workservice.entity.WorkflowSchemeMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkflowSchemeMappingRepository extends JpaRepository<WorkflowSchemeMapping, UUID> {
    List<WorkflowSchemeMapping> findBySchemeId(UUID schemeId);
    Optional<WorkflowSchemeMapping> findBySchemeIdAndWorkItemTypeId(UUID schemeId, UUID workItemTypeId);
    void deleteBySchemeId(UUID schemeId);
}
