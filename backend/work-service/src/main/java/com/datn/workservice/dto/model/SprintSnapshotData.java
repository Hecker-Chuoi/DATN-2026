package com.datn.workservice.dto.model;

import com.datn.workservice.dto.response.BurndownPointResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Noi dung cot sprint_report_snapshots.snapshot_data (JSONB).
 *
 * Bang co rang buoc UNIQUE(sprint_id) nen moi Sprint chi co dung mot ban ghi;
 * chuoi Burndown vi vay duoc dong bang o day thay vi luu theo tung ngay.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SprintSnapshotData {

    private UUID sprintId;
    private String sprintName;
    private String goal;

    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private LocalDateTime completedDate;
    private LocalDateTime closedAt;
    private UUID closedBy;

    private BigDecimal committedStoryPoints;
    private BigDecimal completedStoryPoints;
    private BigDecimal incompleteStoryPoints;

    private Integer totalItems;
    private Integer completedItems;
    private Integer incompleteItems;

    /** Bang velocity chinh la so diem hoan thanh cua Sprint. */
    private BigDecimal velocityPoints;

    private Boolean excludeWeekends;

    /** Chuoi Burndown dong bang. Rong neu Sprint duoc dong truoc khi co bo tinh Burndown. */
    private List<BurndownPointResponse> burndown;

    private List<SprintItemSnapshot> completed;
    private List<SprintItemSnapshot> incomplete;

    private UUID movedToSprintId;
    private String movedToSprintName;
}
