package com.datn.workservice.service;

import com.datn.workservice.dto.request.CreateTaskRequest;
import com.datn.workservice.dto.request.UpdateTaskRequest;
import com.datn.workservice.dto.response.TaskResponse;
import com.datn.workservice.enums.TaskStatus;

import java.util.List;
import java.util.UUID;

public interface TaskService {

    TaskResponse createTask(CreateTaskRequest request, UUID currentUserId);

    List<TaskResponse> getTasks(UUID projectId, UUID milestoneId, TaskStatus status, UUID assigneeId, String search);

    TaskResponse getTaskById(UUID taskId);

    List<TaskResponse> getSubtasks(UUID parentTaskId);

    TaskResponse updateTask(UUID taskId, UpdateTaskRequest request);

    TaskResponse assignTask(UUID taskId, UUID assigneeId, UUID assignerId);

    void deleteTask(UUID taskId);
}
