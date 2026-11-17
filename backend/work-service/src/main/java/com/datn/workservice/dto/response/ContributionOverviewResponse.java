package com.datn.workservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContributionOverviewResponse {
    private UUID projectId;
    private UUID sprintId;
    private String sprintName;
    private BigDecimal totalPoints;
    private Integer totalTasksCompleted;
    private BigDecimal averageCycleTimeDays;
    private BigDecimal onTimeRatePercent;
    private List<MemberContributionResponse> members;
    private List<String> alerts;
    private Integer unassignedCompletedCount;
    private Boolean isFrozen;
}
