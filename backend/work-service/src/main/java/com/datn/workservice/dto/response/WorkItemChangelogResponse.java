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
public class WorkItemChangelogResponse {
    private UUID id;
    private UUID workItemId;
    private String fieldName;
    private String fieldDisplayName;
    private String oldValue;
    private String newValue;
    private UUID changedBy;
    private String authorName;
    private String authorAvatar;
    private LocalDateTime changedAt;
}
