package com.datn.workservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Cumulative Flow Diagram - so cong viec theo tung nhom trang thai qua thoi gian. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CfdResponse {

    private UUID projectId;
    private UUID sprintId;

    /** Cac dai to, xep tu duoi len: DONE -> IN_PROGRESS -> TODO. */
    private List<CfdBucket> buckets;
    private List<CfdPointResponse> points;

    private LocalDateTime generatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CfdBucket {
        private String key;
        private String name;
        private String colorHex;
        private Integer order;
    }
}
