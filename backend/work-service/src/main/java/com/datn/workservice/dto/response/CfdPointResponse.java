package com.datn.workservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CfdPointResponse {
    private LocalDate date;
    /** Khoa la ma nhom (TODO / IN_PROGRESS / DONE), gia tri la so cong viec cuoi ngay. */
    private Map<String, Integer> counts;
    private Integer total;
}
