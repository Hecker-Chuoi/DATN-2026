package com.datn.workservice.repository;

import com.datn.workservice.entity.ProjectClone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectCloneRepository extends JpaRepository<ProjectClone, UUID> {
    List<ProjectClone> findBySourceProjectId(UUID sourceProjectId);
    List<ProjectClone> findByClonedBy(UUID clonedBy);
}
