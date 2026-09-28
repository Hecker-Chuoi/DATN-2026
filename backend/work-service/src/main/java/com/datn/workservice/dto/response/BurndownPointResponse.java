package com.datn.workservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Mot diem (mot ngay) tren bieu do Burndown. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BurndownPointResponse {

    private LocalDate date;

    /** Diem con lai thuc te cuoi ngay (duong bac thang mau xanh). */
    private BigDecimal remainingPoints;

    /** Diem da hoan thanh cong don. */
    private BigDecimal completedPoints;

    /** Tong diem trong Sprint tai thoi diem do - dung de ve vung "thay doi pham vi". */
    private BigDecimal scopePoints;

    /** Duong ly thuyet mau do. */
    private BigDecimal idealRemaining;

    private Integer remainingItems;
    private Integer completedItems;
    private Boolean isToday;
}
