package com.datn.workservice.dto.response;

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
public class VelocityPointResponse {

    private UUID sprintId;
    private String sprintName;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private LocalDateTime completedDate;

    private BigDecimal committedStoryPoints;
    private BigDecimal completedStoryPoints;
    private Integer committedItems;
    private Integer completedItems;

    /** true khi Sprint dong truoc khi co snapshot - so lieu la uoc tinh, khong phai ban goc. */
    private Boolean estimated;
}
