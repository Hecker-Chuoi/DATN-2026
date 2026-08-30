package com.datn.workservice.repository;

import com.datn.workservice.entity.SavedFilter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SavedFilterRepository extends JpaRepository<SavedFilter, UUID>, JpaSpecificationExecutor<SavedFilter> {
    List<SavedFilter> findByOwnerId(UUID ownerId);
    List<SavedFilter> findByIsSharedTrue();
    List<SavedFilter> findByOwnerIdOrIsSharedTrue(UUID ownerId);
    List<SavedFilter> findByProjectId(UUID projectId);
}
