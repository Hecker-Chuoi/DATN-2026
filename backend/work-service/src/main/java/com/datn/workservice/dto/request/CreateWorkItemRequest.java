package com.datn.workservice.dto.request;

import com.datn.workservice.enums.WorkItemPriority;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Request payload to create a new Work Item (Epic, Story, Task, Bug, Sub-task).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateWorkItemRequest {

    @NotNull(message = "ID dự án (projectId) là bắt buộc")
    private UUID projectId;

    @NotNull(message = "Loại công việc (typeId) là bắt buộc")
    private UUID typeId;

    @NotBlank(message = "Tiêu đề công việc (summary) không được để trống")
    @Size(max = 500, message = "Tiêu đề công việc tối đa 500 ký tự")
    private String summary;

    private String description;

    @Builder.Default
    private WorkItemPriority priority = WorkItemPriority.MEDIUM;

    /**
     * Parent Work Item ID (e.g. Epic for Story/Task/Bug, or Story/Task for Subtask).
     */
    private UUID parentId;

    /**
     * Optional Sprint ID (null = Backlog).
     */
    private UUID sprintId;

    /**
     * Assigned user ID.
     */
    private UUID assigneeId;

    /**
     * Reporter user ID (if null, resolved from current authenticated user).
     */
    private UUID reporterId;

    /**
     * Story Points estimation (e.g. 1.0, 2.0, 3.0, 5.0, 8.0, 13.0).
     */
    @DecimalMin(value = "0.0", message = "Điểm Story Points không thể âm")
    @DecimalMax(value = "999.99", message = "Điểm Story Points tối đa 999.99")
    private BigDecimal storyPoints;

    /**
     * Original estimate in seconds (e.g. 28800s = 8 hours).
     */
    @PositiveOrZero(message = "Thời gian ước lượng không thể âm")
    private Long originalEstimateSeconds;

    /**
     * Remaining estimate in seconds.
     */
    @PositiveOrZero(message = "Thời gian còn lại không thể âm")
    private Long remainingEstimateSeconds;

    private UUID componentId;

    private UUID fixVersionId;

    private LocalDate startDate;

    private LocalDate dueDate;

    /**
     * List of Label IDs to associate.
     */
    private List<UUID> labelIds;

    /**
     * JSONB Custom fields key-value dictionary (e.g. {"environment": "Staging", "severity": "CRITICAL"}).
     */
    private Map<String, Object> customFields;
}
