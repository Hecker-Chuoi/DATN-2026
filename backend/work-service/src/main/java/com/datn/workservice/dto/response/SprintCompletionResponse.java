package com.datn.workservice.dto.response;

import com.datn.workservice.enums.SprintState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Ket qua sau khi dong Sprint, dung de hien thi man hinh tong ket. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SprintCompletionResponse {

    private UUID sprintId;
    private String sprintName;
    private SprintState state;
    private LocalDateTime completedDate;

    private Integer completedItems;
    private Integer incompleteItems;
    private BigDecimal completedStoryPoints;
    private BigDecimal incompleteStoryPoints;
    private BigDecimal totalStoryPoints;

    /** Null nghia la cac cong viec do da duoc tra ve Backlog. */
    private UUID movedToSprintId;
    private String movedToSprintName;
    private Integer movedItemCount;
    private Integer cascadedSubtaskCount;

    private UUID snapshotId;
}
