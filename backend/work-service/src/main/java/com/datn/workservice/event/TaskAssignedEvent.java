package com.datn.workservice.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskAssignedEvent implements Serializable {

    private UUID taskId;
    private UUID projectId;
    private String taskCode;
    private String taskTitle;
    private UUID assigneeId;
    private UUID assignerId;
    private LocalDateTime dueDate;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
