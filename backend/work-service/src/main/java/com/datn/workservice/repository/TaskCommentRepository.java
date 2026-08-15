package com.datn.workservice.repository;

import com.datn.workservice.entity.TaskComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaskCommentRepository extends JpaRepository<TaskComment, UUID> {
    List<TaskComment> findByTaskIdOrderByCreatedAtAsc(UUID taskId);
    List<TaskComment> findByTaskIdAndParentCommentIdIsNullOrderByCreatedAtAsc(UUID taskId);
    List<TaskComment> findByParentCommentIdOrderByCreatedAtAsc(UUID parentCommentId);
}
