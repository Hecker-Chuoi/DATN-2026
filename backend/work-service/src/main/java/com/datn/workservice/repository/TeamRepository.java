package com.datn.workservice.repository;

import com.datn.workservice.entity.Team;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface TeamRepository extends JpaRepository<Team, UUID>, JpaSpecificationExecutor<Team> {
    List<Team> findByWorkspaceId(UUID workspaceId);
    Page<Team> findByWorkspaceId(UUID workspaceId, Pageable pageable);
    List<Team> findByOwnerId(UUID ownerId);
    List<Team> findByIdIn(Collection<UUID> ids);
}
