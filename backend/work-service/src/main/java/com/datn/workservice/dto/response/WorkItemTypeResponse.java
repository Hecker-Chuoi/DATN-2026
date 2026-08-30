package com.datn.workservice.dto.response;

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
public class WorkItemTypeResponse {

    private UUID id;
    private UUID projectId;
    private String name;
    private String code;
    private String description;
    private String iconName;
    private String colorHex;
    private Integer hierarchyLevel; // 1: Epic, 0: Standard Issue, -1: Subtask
    private Boolean isSubtask;
    private Boolean isSystemDefault;
    private LocalDateTime createdAt;
}
