package com.datn.workservice.repository;

import com.datn.workservice.entity.ContributionLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContributionLedgerEntryRepository extends JpaRepository<ContributionLedgerEntry, UUID> {

    List<ContributionLedgerEntry> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    List<ContributionLedgerEntry> findByProjectIdAndSprintIdOrderByCreatedAtDesc(UUID projectId, UUID sprintId);

    List<ContributionLedgerEntry> findByProjectIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            UUID projectId, OffsetDateTime fromDate, OffsetDateTime toDate);

    List<ContributionLedgerEntry> findByProjectIdAndUserIdOrderByCreatedAtDesc(UUID projectId, UUID userId);

    List<ContributionLedgerEntry> findByProjectIdAndSprintIdAndUserIdOrderByCreatedAtDesc(
            UUID projectId, UUID sprintId, UUID userId);

    List<ContributionLedgerEntry> findByWorkItemIdOrderByCreatedAtAsc(UUID workItemId);

    @Query("SELECT e FROM ContributionLedgerEntry e WHERE e.workItemId = :workItemId AND e.entryType = 'COMPLETED' ORDER BY e.createdAt DESC")
    List<ContributionLedgerEntry> findCompletedEntriesByWorkItemId(@Param("workItemId") UUID workItemId);
}
