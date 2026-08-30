package com.datn.workservice.repository;

import com.datn.workservice.entity.WorkItemType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkItemTypeRepository extends JpaRepository<WorkItemType, UUID>, JpaSpecificationExecutor<WorkItemType> {
    List<WorkItemType> findByProjectId(UUID projectId);
    List<WorkItemType> findByProjectIdOrProjectIdIsNull(UUID projectId);
    List<WorkItemType> findByHierarchyLevel(Integer hierarchyLevel);
    Optional<WorkItemType> findByProjectIdAndName(UUID projectId, String name);
    Optional<WorkItemType> findByProjectIdAndCode(UUID projectId, String code);
    List<WorkItemType> findByIsSystemDefaultTrue();
}
