package com.datn.workservice.repository;

import com.datn.workservice.entity.ProjectClone;
import com.datn.workservice.enums.CloneType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectCloneRepository extends JpaRepository<ProjectClone, UUID> {
    List<ProjectClone> findBySourceProjectId(UUID sourceProjectId);
    Optional<ProjectClone> findByClonedProjectId(UUID clonedProjectId);
    long countBySourceProjectIdAndCloneType(UUID sourceProjectId, CloneType cloneType);
}
