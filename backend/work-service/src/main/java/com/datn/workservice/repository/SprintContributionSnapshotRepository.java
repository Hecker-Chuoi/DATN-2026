package com.datn.workservice.repository;

import com.datn.workservice.entity.SprintContributionSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SprintContributionSnapshotRepository extends JpaRepository<SprintContributionSnapshot, UUID> {

    List<SprintContributionSnapshot> findBySprintId(UUID sprintId);

    List<SprintContributionSnapshot> findByProjectIdAndSprintId(UUID projectId, UUID sprintId);

    Optional<SprintContributionSnapshot> findBySprintIdAndUserId(UUID sprintId, UUID userId);

    boolean existsBySprintId(UUID sprintId);
}
