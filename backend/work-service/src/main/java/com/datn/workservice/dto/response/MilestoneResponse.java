package com.datn.workservice.dto.response;

import com.datn.workservice.entity.Milestone;
import com.datn.workservice.enums.MilestoneStatus;
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
public class MilestoneResponse {

    private UUID id;
    private UUID projectId;
    private String name;
    private String description;
    private LocalDateTime dueDate;
    private MilestoneStatus status;
    private long totalTasks;
    private long completedTasks;
    private int progressPercentage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static MilestoneResponse from(Milestone m) {
        return MilestoneResponse.builder()
                .id(m.getId())
                .projectId(m.getProjectId())
                .name(m.getName())
                .description(m.getDescription())
                .dueDate(m.getDueDate())
                .status(m.getStatus())
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }
}
