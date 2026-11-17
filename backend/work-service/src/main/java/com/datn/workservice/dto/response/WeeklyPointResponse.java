package com.datn.workservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyPointResponse {
    private String weekLabel; // "Tuần 1", "Tuần 2", "Tuần 3"...
    private BigDecimal points;
}
