package com.datn.workservice.repository;

import com.datn.workservice.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {
    boolean existsByRoleNameIgnoreCaseAndPermissionCodeIgnoreCase(String roleName, String permissionCode);
}
