package com.datn.workspaceservice.repository;

import com.datn.workspaceservice.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

    /** Chỉ workspace còn sống. Dùng cho mọi truy vấn thường. */
    Optional<Workspace> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Bao gồm cả workspace đã soft-delete. CHỈ dùng cho luồng restore -
     * mọi chỗ khác phải dùng {@link #findByIdAndDeletedAtIsNull(UUID)}.
     */
    Optional<Workspace> findById(UUID id);

    List<Workspace> findByIdInAndDeletedAtIsNull(List<UUID> ids);

    boolean existsByIdAndDeletedAtIsNull(UUID id);

    boolean existsBySlug(String slug);
}
