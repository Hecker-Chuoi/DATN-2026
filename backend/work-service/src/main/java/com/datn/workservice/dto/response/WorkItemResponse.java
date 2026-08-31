package com.datn.workservice.dto.response;

import com.datn.workservice.enums.StatusCategory;
import com.datn.workservice.enums.WorkItemPriority;
import com.datn.workservice.enums.WorkItemResolution;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Summary Response DTO for Kanban Board, Backlog list, and Table views.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkItemResponse {

    private UUID id;
    private UUID projectId;
    private String projectKey;

    // Type info
    private UUID typeId;
    private String typeName;
    private String typeCode;
    private String typeColor;
    private String typeIcon;
    private Integer hierarchyLevel;
    private Boolean isSubtask;

    // Status info
    private UUID statusId;
    private String statusName;
    private StatusCategory statusCategory;
    private String statusColorHex;

    // Parent & Sprint
    private UUID parentId;
    private String parentCode;
    private String parentSummary;
    private UUID sprintId;
    private String sprintName;

    // Core content
    private String code; // e.g. "PRJ-101"
    private String summary;
    private WorkItemPriority priority;
    private WorkItemResolution resolution;

    // Users
    private UUID reporterId;
    private String reporterName;
    private UUID assigneeId;
    private String assigneeName;
    private String assigneeAvatar;

    // Estimates & Rank
    private BigDecimal storyPoints;
    private Long originalEstimateSeconds;
    private Long remainingEstimateSeconds;
    private Long timeSpentSeconds;
    private String rank; // LexoRank string

    // Component & Version
    private UUID componentId;
    private String componentName;
    private UUID fixVersionId;
    private String fixVersionName;

    // Dates
    private LocalDate startDate;
    private LocalDate dueDate;
    private LocalDateTime resolvedAt;

    // Subtask counts
    private Integer subtaskCount;
    private Integer subtaskDoneCount;

    // Labels
    private List<String> labels;

    // Timestamps & Concurrency
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
