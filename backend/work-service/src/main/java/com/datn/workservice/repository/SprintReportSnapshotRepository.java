package com.datn.workservice.repository;

import com.datn.workservice.entity.SprintReportSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SprintReportSnapshotRepository extends JpaRepository<SprintReportSnapshot, UUID> {
    Optional<SprintReportSnapshot> findBySprintId(UUID sprintId);
    List<SprintReportSnapshot> findBySprintIdIn(Collection<UUID> sprintIds);
    boolean existsBySprintId(UUID sprintId);
}
