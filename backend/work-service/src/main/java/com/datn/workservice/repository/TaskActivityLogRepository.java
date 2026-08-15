package com.datn.workservice.repository;

import com.datn.workservice.entity.TaskActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaskActivityLogRepository extends JpaRepository<TaskActivityLog, UUID> {
    List<TaskActivityLog> findByTaskIdOrderByCreatedAtDesc(UUID taskId);
}
