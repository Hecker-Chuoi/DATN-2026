package com.datn.workservice.dto.request;

import com.datn.workservice.enums.TaskPriority;
import com.datn.workservice.enums.TaskStatus;
import com.datn.workservice.enums.TaskType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class CreateTaskRequest {

    @NotNull(message = "Project ID không được để trống")
    private UUID projectId;

    private UUID teamId;

    private UUID milestoneId;

    private UUID parentTaskId;

    @NotBlank(message = "Tiêu đề tác vụ không được để trống")
    @Size(max = 255, message = "Tiêu đề tối đa 255 ký tự")
    private String title;

    private String description;

    @Builder.Default
    private TaskType type = TaskType.TASK;

    @Builder.Default
    private TaskStatus status = TaskStatus.TODO;

    @Builder.Default
    private TaskPriority priority = TaskPriority.MEDIUM;

    private Integer estimate;

    private UUID assigneeId;

    private LocalDateTime dueDate;

    private Integer position;
}
