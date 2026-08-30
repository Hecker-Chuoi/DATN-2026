package com.datn.workservice.repository;

import com.datn.workservice.entity.WorkItemComponentMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkItemComponentMapRepository extends JpaRepository<WorkItemComponentMap, WorkItemComponentMap.WorkItemComponentId> {
    List<WorkItemComponentMap> findByWorkItemId(UUID workItemId);
    List<WorkItemComponentMap> findByComponentId(UUID componentId);
    void deleteByWorkItemId(UUID workItemId);
    void deleteByWorkItemIdAndComponentId(UUID workItemId, UUID componentId);
}
