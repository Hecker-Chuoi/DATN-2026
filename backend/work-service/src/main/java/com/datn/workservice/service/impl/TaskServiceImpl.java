package com.datn.workservice.service.impl;

import com.datn.common.dto.ApiResponse;
import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workservice.client.IdentityClient;
import com.datn.workservice.client.dto.UserResponse;
import com.datn.workservice.dto.request.CreateTaskRequest;
import com.datn.workservice.dto.request.UpdateTaskRequest;
import com.datn.workservice.dto.response.TaskResponse;
import com.datn.workservice.entity.Milestone;
import com.datn.workservice.entity.Project;
import com.datn.workservice.entity.Task;
import com.datn.workservice.enums.TaskPriority;
import com.datn.workservice.enums.TaskStatus;
import com.datn.workservice.enums.TaskType;
import com.datn.workservice.repository.MilestoneRepository;
import com.datn.workservice.repository.ProjectRepository;
import com.datn.workservice.repository.TaskRepository;
import com.datn.workservice.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final MilestoneRepository milestoneRepository;
    private final IdentityClient identityClient;

    @Override
    @Transactional
    public TaskResponse createTask(CreateTaskRequest request, UUID currentUserId) {
        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + request.getProjectId()));

        if (request.getMilestoneId() != null && !milestoneRepository.existsById(request.getMilestoneId())) {
            throw new ResourceNotFoundException("Milestone not found with id: " + request.getMilestoneId());
        }

        if (request.getParentTaskId() != null && !taskRepository.existsById(request.getParentTaskId())) {
            throw new ResourceNotFoundException("Parent task not found with id: " + request.getParentTaskId());
        }

        UUID reporter = currentUserId != null ? currentUserId : UUID.randomUUID();

        // 1. Tự động sinh mã Task Code duy nhất dạng {PROJECT_KEY}-{NUM}
        long seq = taskRepository.countByProjectId(project.getId()) + 1;
        String taskCode = project.getKey() + "-" + seq;
        while (taskRepository.findByProjectIdAndCode(project.getId(), taskCode).isPresent()) {
            seq++;
            taskCode = project.getKey() + "-" + seq;
        }

        // 2. Tính toán position nếu chưa có
        int position = request.getPosition() != null ? request.getPosition() : (int) (seq * 1000);

        UUID teamId = request.getTeamId() != null ? request.getTeamId() : project.getTeamId();

        Task task = Task.builder()
                .projectId(project.getId())
                .teamId(teamId)
                .milestoneId(request.getMilestoneId())
                .parentTaskId(request.getParentTaskId())
                .code(taskCode)
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .type(request.getType() != null ? request.getType() : TaskType.TASK)
                .status(request.getStatus() != null ? request.getStatus() : TaskStatus.TODO)
                .priority(request.getPriority() != null ? request.getPriority() : TaskPriority.MEDIUM)
                .estimate(request.getEstimate())
                .reporterId(reporter)
                .assigneeId(request.getAssigneeId())
                .dueDate(request.getDueDate())
                .position(position)
                .build();

        task = taskRepository.save(task);
        return enrichTaskResponse(task);
    }

    @Override
    public List<TaskResponse> getTasks(UUID projectId, UUID milestoneId, TaskStatus status, UUID assigneeId, String search) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project not found with id: " + projectId);
        }

        List<Task> tasks = taskRepository.findByProjectId(projectId);

        // Áp dụng bộ lọc in-memory
        return tasks.stream()
                .filter(t -> milestoneId == null || Objects.equals(t.getMilestoneId(), milestoneId))
                .filter(t -> status == null || t.getStatus() == status)
                .filter(t -> assigneeId == null || Objects.equals(t.getAssigneeId(), assigneeId))
                .filter(t -> {
                    if (search == null || search.isBlank()) return true;
                    String s = search.toLowerCase();
                    return t.getTitle().toLowerCase().contains(s) ||
                            t.getCode().toLowerCase().contains(s) ||
                            (t.getDescription() != null && t.getDescription().toLowerCase().contains(s));
                })
                .sorted(Comparator.comparing(Task::getPosition, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::enrichTaskResponse)
                .collect(Collectors.toList());
    }

    @Override
    public TaskResponse getTaskById(UUID taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + taskId));
        return enrichTaskResponse(task);
    }

    @Override
    public List<TaskResponse> getSubtasks(UUID parentTaskId) {
        if (!taskRepository.existsById(parentTaskId)) {
            throw new ResourceNotFoundException("Parent task not found with id: " + parentTaskId);
        }

        return taskRepository.findByParentTaskId(parentTaskId).stream()
                .sorted(Comparator.comparing(Task::getPosition, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::enrichTaskResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public TaskResponse updateTask(UUID taskId, UpdateTaskRequest request) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + taskId));

        if (request.getTitle() != null) task.setTitle(request.getTitle().trim());
        if (request.getDescription() != null) task.setDescription(request.getDescription());
        if (request.getType() != null) task.setType(request.getType());
        if (request.getStatus() != null) task.setStatus(request.getStatus());
        if (request.getPriority() != null) task.setPriority(request.getPriority());
        if (request.getEstimate() != null) task.setEstimate(request.getEstimate());
        if (request.getAssigneeId() != null) task.setAssigneeId(request.getAssigneeId());
        if (request.getMilestoneId() != null) task.setMilestoneId(request.getMilestoneId());
        if (request.getParentTaskId() != null) {
            if (request.getParentTaskId().equals(taskId)) {
                throw new BadRequestException("Task cannot be a subtask of itself");
            }
            task.setParentTaskId(request.getParentTaskId());
        }
        if (request.getDueDate() != null) task.setDueDate(request.getDueDate());
        if (request.getPosition() != null) task.setPosition(request.getPosition());

        task = taskRepository.save(task);
        return enrichTaskResponse(task);
    }

    @Override
    @Transactional
    public void deleteTask(UUID taskId) {
        if (!taskRepository.existsById(taskId)) {
            throw new ResourceNotFoundException("Task not found with id: " + taskId);
        }
        // Xóa hoặc ngắt liên kết các task con
        List<Task> subtasks = taskRepository.findByParentTaskId(taskId);
        for (Task sub : subtasks) {
            sub.setParentTaskId(null);
            taskRepository.save(sub);
        }
        taskRepository.deleteById(taskId);
    }

    private TaskResponse enrichTaskResponse(Task task) {
        TaskResponse res = TaskResponse.from(task);

        // Milestone Name
        if (task.getMilestoneId() != null) {
            milestoneRepository.findById(task.getMilestoneId())
                    .ifPresent(m -> res.setMilestoneName(m.getName()));
        }

        // Subtask counts
        long subCount = taskRepository.countByParentTaskId(task.getId());
        long completedSubCount = taskRepository.countByParentTaskIdAndStatus(task.getId(), TaskStatus.DONE);
        res.setSubtaskCount(subCount);
        res.setCompletedSubtaskCount(completedSubCount);

        // Assignee info from identity-service
        if (task.getAssigneeId() != null) {
            try {
                ApiResponse<UserResponse> userApi = identityClient.getUserById(task.getAssigneeId());
                if (userApi != null && userApi.getData() != null) {
                    UserResponse u = userApi.getData();
                    res.setAssigneeName(u.getFullName());
                    res.setAssigneeUsername(u.getUsername());
                }
            } catch (Exception e) {
                log.warn("Could not fetch assignee info for id={}: {}", task.getAssigneeId(), e.getMessage());
            }
        }

        return res;
    }
}
