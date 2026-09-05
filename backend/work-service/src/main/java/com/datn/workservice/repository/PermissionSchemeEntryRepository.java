package com.datn.workservice.repository;

import com.datn.workservice.entity.PermissionSchemeEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PermissionSchemeEntryRepository extends JpaRepository<PermissionSchemeEntry, UUID> {
    List<PermissionSchemeEntry> findByProjectId(UUID projectId);
    List<PermissionSchemeEntry> findByProjectIdAndRoleId(UUID projectId, UUID roleId);
    List<PermissionSchemeEntry> findByRoleId(UUID roleId);
    boolean existsByProjectIdAndRoleIdAndPermissionKey(UUID projectId, UUID roleId, String permissionKey);
    void deleteByProjectIdAndRoleId(UUID projectId, UUID roleId);
}
