package com.datn.workservice.dto.response;

import com.datn.workservice.enums.SprintState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Mot khoi tren man hinh Backlog: mot Sprint, hoac chinh Backlog khi sprintId = null. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacklogGroupResponse {

    /** null nghia la khoi Backlog. */
    private UUID sprintId;
    private String name;
    private String goal;
    private SprintState state;
    private LocalDateTime startDate;
    private LocalDateTime endDate;

    private List<BoardCardResponse> items;

    private Integer itemCount;
    private BigDecimal totalStoryPoints;
    private BigDecimal todoStoryPoints;
    private BigDecimal inProgressStoryPoints;
    private BigDecimal doneStoryPoints;
    private Integer todoCount;
    private Integer inProgressCount;
    private Integer doneCount;

    private Boolean canStart;
    private Boolean canComplete;
}
