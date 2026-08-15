package com.datn.workservice.repository;

import com.datn.workservice.entity.TaskDependency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaskDependencyRepository extends JpaRepository<TaskDependency, TaskDependency.TaskDependencyId> {
    List<TaskDependency> findByIdTaskId(UUID taskId);
    List<TaskDependency> findByIdDependsOnTaskId(UUID dependsOnTaskId);
}
