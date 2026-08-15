package com.datn.workservice.repository;

import com.datn.workservice.entity.TaskWatcher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaskWatcherRepository extends JpaRepository<TaskWatcher, TaskWatcher.TaskWatcherId> {
    List<TaskWatcher> findByIdTaskId(UUID taskId);
    List<TaskWatcher> findByIdUserId(UUID userId);
}
