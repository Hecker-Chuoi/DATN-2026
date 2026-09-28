package com.datn.workspaceservice.repository;

import com.datn.workspaceservice.entity.WorkspaceMember;
import com.datn.workspaceservice.enums.WorkspaceRole;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, UUID> {

    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    boolean existsByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    List<WorkspaceMember> findByUserId(UUID userId);

    List<WorkspaceMember> findByWorkspaceId(UUID workspaceId);

    /** Nạp thành viên của NHIỀU workspace trong một query - tránh N+1 ở getMyWorkspaces. */
    List<WorkspaceMember> findByWorkspaceIdIn(List<UUID> workspaceIds);

    Page<WorkspaceMember> findByWorkspaceId(UUID workspaceId, Pageable pageable);

    Page<WorkspaceMember> findByWorkspaceIdAndRole(UUID workspaceId, WorkspaceRole role, Pageable pageable);

    long countByWorkspaceIdAndRole(UUID workspaceId, WorkspaceRole role);

    /**
     * Khóa ghi (SELECT ... FOR UPDATE) toàn bộ các hàng OWNER của workspace.
     * <p>
     * BẮT BUỘC gọi trước khi đếm OWNER trong {@code assertNotLastOwner}. Không
     * có nó, hai OWNER gọi /leave đồng thời đều đọc được count = 2, đều pass, đều
     * xóa -> workspace về ZERO OWNER và không ai quản trị được nữa. Lock buộc
     * transaction thứ hai chờ transaction thứ nhất commit rồi đọc lại count = 1
     * và throw.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM WorkspaceMember m WHERE m.workspaceId = :workspaceId AND m.role = com.datn.workspaceservice.enums.WorkspaceRole.OWNER")
    List<WorkspaceMember> lockOwners(@Param("workspaceId") UUID workspaceId);
}
