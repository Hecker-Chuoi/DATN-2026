package com.datn.workservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberWorkItemContributionResponse {
    private UUID workItemId;
    private String code;
    private String title;
    private BigDecimal points;
    private String note; // e.g. "Đúng hạn", "Trễ 1 ngày", "Bị mở lại", "Hoàn thành lại", "Quản lý kéo hộ"
    private String statusCategory;
    private OffsetDateTime completedAt;
}
