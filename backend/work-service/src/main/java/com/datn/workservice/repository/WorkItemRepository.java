package com.datn.workservice.repository;

import com.datn.workservice.entity.WorkItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkItemRepository extends JpaRepository<WorkItem, UUID>, JpaSpecificationExecutor<WorkItem> {
    Optional<WorkItem> findByCode(String code);
    Optional<WorkItem> findByProjectIdAndCode(UUID projectId, String code);
    List<WorkItem> findByProjectId(UUID projectId);
    Page<WorkItem> findByProjectId(UUID projectId, Pageable pageable);
    List<WorkItem> findBySprintId(UUID sprintId);
    List<WorkItem> findBySprintIdOrderByRankAsc(UUID sprintId);
    List<WorkItem> findByProjectIdAndSprintIdIsNull(UUID projectId);
    List<WorkItem> findByParentId(UUID parentId);
    List<WorkItem> findByParentIdIn(List<UUID> parentIds);
    List<WorkItem> findByProjectIdAndStatusId(UUID projectId, UUID statusId);
    List<WorkItem> findByAssigneeId(UUID assigneeId);
    List<WorkItem> findByReporterId(UUID reporterId);
    long countByProjectId(UUID projectId);
    long countBySprintId(UUID sprintId);
    long countBySprintIdAndStatusId(UUID sprintId, UUID statusId);

    @Query("SELECT wi FROM WorkItem wi WHERE wi.projectId = :projectId AND wi.deletedAt IS NULL ORDER BY wi.rank ASC")
    List<WorkItem> findActiveByProjectIdOrderByRankAsc(@Param("projectId") UUID projectId);
}
