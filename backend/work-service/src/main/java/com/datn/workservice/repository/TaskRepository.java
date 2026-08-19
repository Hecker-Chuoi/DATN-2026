package com.datn.workservice.repository;

import com.datn.workservice.entity.Task;
import com.datn.workservice.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {
    List<Task> findByProjectId(UUID projectId);
    List<Task> findByTeamId(UUID teamId);
    List<Task> findByAssigneeId(UUID assigneeId);
    List<Task> findByProjectIdAndStatus(UUID projectId, TaskStatus status);
    List<Task> findByParentTaskId(UUID parentTaskId);
    List<Task> findByMilestoneId(UUID milestoneId);
    long countByProjectId(UUID projectId);
    long countByMilestoneId(UUID milestoneId);
    long countByMilestoneIdAndStatus(UUID milestoneId, TaskStatus status);
    long countByParentTaskId(UUID parentTaskId);
    long countByParentTaskIdAndStatus(UUID parentTaskId, TaskStatus status);
    Optional<Task> findByProjectIdAndCode(UUID projectId, String code);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE Task t SET t.assigneeId = null WHERE t.assigneeId = :userId AND (t.teamId = :teamId OR t.projectId IN (SELECT p.id FROM Project p WHERE p.teamId = :teamId))")
    int unassignTasksForTeamMember(@org.springframework.data.repository.query.Param("teamId") UUID teamId, @org.springframework.data.repository.query.Param("userId") UUID userId);
}
