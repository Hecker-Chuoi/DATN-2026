package com.datn.workservice.dto.request;

import com.datn.workservice.enums.WorkItemPriority;
import com.datn.workservice.enums.WorkItemResolution;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
 * Request payload to update an existing Work Item.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateWorkItemRequest {

    @Size(max = 500, message = "Tiêu đề công việc tối đa 500 ký tự")
    private String summary;

    private String description;

    private UUID typeId;

    private WorkItemPriority priority;

    /**
     * Parent Work Item ID (can be set to null to unlink).
     */
    private UUID parentId;

    /**
     * Sprint ID (can be set to null to move back to Backlog).
     */
    private UUID sprintId;

    /**
     * Assignee user ID (can be set to null to unassign).
     */
    private UUID assigneeId;

    @DecimalMin(value = "0.0", message = "Điểm Story Points không thể âm")
    @DecimalMax(value = "999.99", message = "Điểm Story Points tối đa 999.99")
    private BigDecimal storyPoints;

    @PositiveOrZero(message = "Thời gian ước lượng không thể âm")
    private Long originalEstimateSeconds;

    @PositiveOrZero(message = "Thời gian còn lại không thể âm")
    private Long remainingEstimateSeconds;

    @PositiveOrZero(message = "Thời gian đã làm không thể âm")
    private Long timeSpentSeconds;

    private UUID componentId;

    private UUID fixVersionId;

    private LocalDate startDate;

    private LocalDate dueDate;

    private WorkItemResolution resolution;

    private List<UUID> labelIds;

    /**
     * JSONB Custom fields key-value dictionary.
     */
    private Map<String, Object> customFields;
}
