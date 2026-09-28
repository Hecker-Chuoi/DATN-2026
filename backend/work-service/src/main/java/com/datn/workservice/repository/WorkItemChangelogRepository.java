package com.datn.workservice.repository;

import com.datn.workservice.entity.WorkItemChangelog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface WorkItemChangelogRepository extends JpaRepository<WorkItemChangelog, UUID>, JpaSpecificationExecutor<WorkItemChangelog> {
    List<WorkItemChangelog> findByWorkItemId(UUID workItemId);
    List<WorkItemChangelog> findByWorkItemIdOrderByChangedAtDesc(UUID workItemId);
    List<WorkItemChangelog> findByWorkItemIdInAndFieldNameInOrderByChangedAtAsc(
            Collection<UUID> workItemIds, Collection<String> fieldNames);

}
