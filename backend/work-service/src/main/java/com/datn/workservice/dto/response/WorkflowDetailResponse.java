package com.datn.workservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDetailResponse {

    private UUID id;
    private UUID projectId;
    private String name;
    private String description;
    private Boolean isDefault;
    private List<WorkflowTransitionResponse> transitions;
    private List<StatusResponse> statuses;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
