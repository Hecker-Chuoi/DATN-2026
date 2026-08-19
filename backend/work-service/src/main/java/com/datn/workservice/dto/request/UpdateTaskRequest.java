package com.datn.workservice.dto.request;

import com.datn.workservice.enums.TaskPriority;
import com.datn.workservice.enums.TaskStatus;
import com.datn.workservice.enums.TaskType;
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
public class UpdateTaskRequest {

    private UUID milestoneId;

    private UUID parentTaskId;

    @Size(max = 255, message = "Tiêu đề tối đa 255 ký tự")
    private String title;

    private String description;

    private TaskType type;

    private TaskStatus status;

    private TaskPriority priority;

    private Integer estimate;

    private UUID assigneeId;

    private LocalDateTime startDate;

    private LocalDateTime dueDate;

    private Integer position;
}
