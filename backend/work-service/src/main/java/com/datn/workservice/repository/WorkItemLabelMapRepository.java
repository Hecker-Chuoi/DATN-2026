package com.datn.workservice.repository;

import com.datn.workservice.entity.WorkItemLabelMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkItemLabelMapRepository extends JpaRepository<WorkItemLabelMap, WorkItemLabelMap.WorkItemLabelId> {
    List<WorkItemLabelMap> findByWorkItemId(UUID workItemId);
    List<WorkItemLabelMap> findByLabelId(UUID labelId);
    void deleteByWorkItemId(UUID workItemId);
    void deleteByWorkItemIdAndLabelId(UUID workItemId, UUID labelId);
}
