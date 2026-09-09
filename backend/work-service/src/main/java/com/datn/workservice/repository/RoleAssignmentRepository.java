package com.datn.workservice.repository;

import com.datn.workservice.entity.RoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RoleAssignmentRepository extends JpaRepository<RoleAssignment, UUID> {
    List<RoleAssignment> findByProjectId(UUID projectId);
    List<RoleAssignment> findByProjectIdAndUserId(UUID projectId, UUID userId);
    List<RoleAssignment> findByUserId(UUID userId);
    List<RoleAssignment> findByProjectIdAndRoleId(UUID projectId, UUID roleId);
    boolean existsByProjectIdAndUserIdAndRoleId(UUID projectId, UUID userId, UUID roleId);
    void deleteByProjectIdAndUserId(UUID projectId, UUID userId);
    void deleteByProjectIdAndUserIdAndRoleId(UUID projectId, UUID userId, UUID roleId);

    @Query("SELECT DISTINCT ra.projectId FROM RoleAssignment ra WHERE ra.userId = :userId")
    List<UUID> findDistinctProjectIdsByUserId(@Param("userId") UUID userId);
}
