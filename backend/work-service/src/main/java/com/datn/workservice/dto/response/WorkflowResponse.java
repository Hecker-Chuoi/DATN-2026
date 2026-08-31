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
public class WorkflowResponse {

    private UUID id;
    private UUID projectId;
    private String name;
    private String description;
    private Boolean isDefault;
    private Integer transitionCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
