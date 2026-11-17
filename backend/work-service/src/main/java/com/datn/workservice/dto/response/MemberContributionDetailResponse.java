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
public class MemberContributionDetailResponse {
    private UUID userId;
    private String name;
    private String avatarUrl;
    private String sprintName;
    private BigDecimal totalPoints;
    private Integer completedTasks;
    private BigDecimal onTimeRatePercent;
    private List<WeeklyPointResponse> weeklyPoints;
    private List<MemberWorkItemContributionResponse> workItems;
}
