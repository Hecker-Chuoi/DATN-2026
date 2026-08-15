package com.datn.workservice.dto.response;

import com.datn.workservice.entity.Task;
import com.datn.workservice.enums.TaskPriority;
import com.datn.workservice.enums.TaskStatus;
import com.datn.workservice.enums.TaskType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskResponse {

    private UUID id;
    private UUID projectId;
    private UUID teamId;
    private UUID milestoneId;
    private String milestoneName;
    private UUID parentTaskId;
    private String code;
    private String title;
    private String description;
    private TaskType type;
    private TaskStatus status;
    private TaskPriority priority;
    private Integer estimate;
    private UUID reporterId;
    private UUID assigneeId;
    private String assigneeName;
    private String assigneeUsername;
    private LocalDateTime dueDate;
    private Integer position;
    private long subtaskCount;
    private long completedSubtaskCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static TaskResponse from(Task task) {
        return TaskResponse.builder()
                .id(task.getId())
                .projectId(task.getProjectId())
                .teamId(task.getTeamId())
                .milestoneId(task.getMilestoneId())
                .parentTaskId(task.getParentTaskId())
                .code(task.getCode())
                .title(task.getTitle())
                .description(task.getDescription())
                .type(task.getType())
                .status(task.getStatus())
                .priority(task.getPriority())
                .estimate(task.getEstimate())
                .reporterId(task.getReporterId())
                .assigneeId(task.getAssigneeId())
                .dueDate(task.getDueDate())
                .position(task.getPosition())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
}
