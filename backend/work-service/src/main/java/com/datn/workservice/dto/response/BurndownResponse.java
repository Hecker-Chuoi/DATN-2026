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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BurndownResponse {

    private UUID sprintId;
    private String sprintName;
    private String goal;
    private SprintState state;

    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private LocalDateTime completedDate;

    private BigDecimal committedStoryPoints;
    private BigDecimal completedStoryPoints;
    private BigDecimal remainingStoryPoints;

    private Integer totalItems;
    private Integer completedItems;

    private Boolean excludeWeekends;

    /**
     * SNAPSHOT            - doc tu so lieu da dong bang khi dong Sprint
     * RECONSTRUCTED       - dung lai tu changelog (Sprint dang chay)
     * RECONSTRUCTED_LEGACY- Sprint dong truoc khi co tinh nang bao cao
     */
    private String source;

    private List<BurndownPointResponse> points;

    private LocalDateTime generatedAt;
}
