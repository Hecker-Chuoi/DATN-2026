package com.datn.workservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VelocityResponse {

    private UUID projectId;
    private List<VelocityPointResponse> sprints;

    private BigDecimal averageVelocity;
    private BigDecimal averageCommitment;
    /** Ty le hoan thanh so voi cam ket (%). */
    private BigDecimal commitmentReliabilityPercent;

    private Integer sprintCount;
    private LocalDateTime generatedAt;
}
