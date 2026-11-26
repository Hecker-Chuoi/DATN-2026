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
import java.util.Map;
import java.util.UUID;

/**
 * Detailed Response DTO for Work Item Drawer / Modal Dialog view.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkItemDetailResponse {

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

    // Core content
    private String code;
    private String summary;
    private String description;
    private WorkItemPriority priority;
    private WorkItemResolution resolution;

    // Parent & Hierarchy
    private UUID parentId;
    private WorkItemResponse parent;
    private List<WorkItemResponse> subtasks;
    private List<WorkItemResponse> childStories; // Populated when this item is an Epic
    private List<WorkItemResponse> descendants;  // All recursive descendants (Stories + Subtasks)

    // Sprint info
    private UUID sprintId;
    private String sprintName;

    // Users
    private UUID reporterId;
    private String reporterName;
    private UUID assigneeId;
    private String assigneeName;
    private String assigneeAvatar;

    // Estimates & Tracking
    private BigDecimal storyPoints;
    private Long originalEstimateSeconds;
    private Long remainingEstimateSeconds;
    private Long timeSpentSeconds;
    private Long totalTimeSpentSeconds;
    private Long totalRemainingEstimateSeconds;
    private BigDecimal totalStoryPoints;
    private BigDecimal doneStoryPoints;
    private String rank;

    // Timeline & Link Conflict Flags
    private Boolean hasTimelineConflict;
    private String timelineConflictMessage;
    private Boolean isBlocked;
    private List<WorkItemResponse> blockingWorkItems;
    private Boolean hasDependencyConflict;
    private String dependencyConflictMessage;

    // Component & Version
    private UUID componentId;
    private String componentName;
    private UUID fixVersionId;
    private String fixVersionName;

    // JSONB Custom Fields
    private Map<String, Object> customFields;

    // Labels
    private List<String> labels;

    // Available transitions from current status
    private List<AvailableTransitionResponse> availableTransitions;

    // "Tạm hoàn thành" (BR-W03..BR-W08)
    private Boolean completionPending;
    private CompletionRequestResponse completionRequest;
    /** Người dùng hiện tại có quyền xác nhận / từ chối hoàn thành (Admin dự án) hay không. */
    private Boolean canConfirmCompletion;

    // Counts
    private Integer commentCount;
    private Integer attachmentCount;

    // Dates & Version
    private LocalDate startDate;
    private LocalDate dueDate;
    private LocalDateTime resolvedAt;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // GitHub Integration Links
    private List<com.datn.workservice.dto.github.WorkItemGitHubLinkDto> githubLinks;
}
