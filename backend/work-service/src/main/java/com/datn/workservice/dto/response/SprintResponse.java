package com.datn.workservice.dto.response;

import com.datn.workservice.enums.SprintState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SprintResponse {

    private UUID id;
    private UUID projectId;
    private String name;
    private String goal;
    private SprintState state;

    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private LocalDateTime completedDate;

    // ─── So lieu tong hop (chi tinh khi caller yeu cau) ───
    private Integer totalItems;
    private Integer completedItems;
    private BigDecimal totalStoryPoints;
    private BigDecimal completedStoryPoints;
    private BigDecimal remainingStoryPoints;
    private Integer todoItems;
    private Integer inProgressItems;
    private Integer doneItems;

    /** So ngay con lai toi endDate (am neu da qua han). Null khi chua co endDate. */
    private Integer daysRemaining;
    private Boolean isOverdue;
    private Boolean hasSnapshot;

    /** Dieu kien bat/tat nut tren UI. */
    private Boolean canStart;
    private Boolean canComplete;

    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
