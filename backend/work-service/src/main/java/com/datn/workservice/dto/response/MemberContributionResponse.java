package com.datn.workservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberContributionResponse {
    private UUID userId;
    private String name;
    private String avatarUrl;
    private String email;
    private BigDecimal points;
    private BigDecimal percentage;
    private Integer completedTasks;
    private Integer discussionCount;
    private Integer bugsReported;
    private BigDecimal onTimeRatePercent;
    private Integer reopenedCount;
}
