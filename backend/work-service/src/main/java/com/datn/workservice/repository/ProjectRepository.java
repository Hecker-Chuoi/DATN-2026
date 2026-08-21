package com.datn.workservice.repository;

import com.datn.workservice.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID>, JpaSpecificationExecutor<Project> {
    List<Project> findByTeamId(UUID teamId);
    List<Project> findByIsTemplateTrue();
    Optional<Project> findByKey(String key);

    @Query("""
        SELECT DISTINCT p FROM Project p
        WHERE p.isTemplate = false
          AND (
            p.createdBy = :userId
            OR (p.teamId IS NOT NULL AND p.teamId IN (
                SELECT tm.id.teamId FROM TeamMember tm WHERE tm.id.userId = :userId
            ))
            OR (p.teamId IS NOT NULL AND p.teamId IN (
                SELECT t.id FROM Team t WHERE t.ownerId = :userId
            ))
          )
        ORDER BY p.createdAt DESC
    """)
    List<Project> findMyProjects(@Param("userId") UUID userId);

    @Query("""
        SELECT DISTINCT p FROM Project p
        WHERE p.isTemplate = false
          AND p.teamId = :teamId
          AND (
            p.createdBy = :userId
            OR (p.teamId IN (
                SELECT tm.id.teamId FROM TeamMember tm WHERE tm.id.userId = :userId
            ))
            OR (p.teamId IN (
                SELECT t.id FROM Team t WHERE t.ownerId = :userId
            ))
          )
        ORDER BY p.createdAt DESC
    """)
    List<Project> findMyProjectsByTeam(@Param("userId") UUID userId, @Param("teamId") UUID teamId);
}
