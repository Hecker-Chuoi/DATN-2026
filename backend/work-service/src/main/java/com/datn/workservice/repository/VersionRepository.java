package com.datn.workservice.repository;

import com.datn.workservice.entity.Version;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VersionRepository extends JpaRepository<Version, UUID>, JpaSpecificationExecutor<Version> {
    List<Version> findByProjectId(UUID projectId);
    List<Version> findByProjectIdAndIsReleased(UUID projectId, Boolean isReleased);
    List<Version> findByProjectIdAndIsArchived(UUID projectId, Boolean isArchived);
    Optional<Version> findByProjectIdAndName(UUID projectId, String name);
    boolean existsByProjectIdAndName(UUID projectId, String name);
}
