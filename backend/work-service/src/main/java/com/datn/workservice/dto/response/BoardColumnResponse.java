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
public class BoardColumnResponse {

    private UUID id;
    private UUID boardId;
    private String name;
    private Integer columnOrder;
    private Integer minWipLimit;
    private Integer maxWipLimit;

    /** Cac trang thai duoc gom vao cot nay. */
    private List<StatusResponse> statuses;

    /** So the hien co - de UI ve "(hien tai / toi da)" ma khong phai thu keo. */
    private Integer currentCount;
    private Boolean isOverMax;
    private Boolean isBelowMin;
    private BigDecimal storyPointsSum;
}
