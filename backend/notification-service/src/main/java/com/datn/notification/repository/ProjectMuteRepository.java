package com.datn.notification.repository;

import com.datn.notification.entity.ProjectMute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProjectMuteRepository extends JpaRepository<ProjectMute, ProjectMute.Key> {

    List<ProjectMute> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<ProjectMute> findByProjectIdAndUserIdIn(UUID projectId, Collection<UUID> userIds);

    /** Dọn các lượt tắt tiếng có thời hạn đã hết. */
    @Modifying
    @Query("DELETE FROM ProjectMute m WHERE m.mutedUntil IS NOT NULL AND m.mutedUntil < :now")
    int deleteExpired(@Param("now") Instant now);
}
