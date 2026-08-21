package com.datn.workservice.event;

import com.datn.workservice.enums.TaskStatus;
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
public class TaskStatusChangedEvent implements Serializable {

    private UUID taskId;
    private UUID projectId;
    private String taskCode;
    private TaskStatus oldStatus;
    private TaskStatus newStatus;
    private UUID actorId;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
