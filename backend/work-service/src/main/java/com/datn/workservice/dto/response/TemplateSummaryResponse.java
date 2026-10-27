package com.datn.workservice.dto.response;

import com.datn.workservice.enums.ProjectType;
import com.datn.workservice.enums.StatusCategory;
import com.datn.workservice.enums.TemplateCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Dữ liệu một thẻ mẫu trong thư viện mẫu (tab "Chọn mẫu dự án").
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateSummaryResponse {

    private UUID id;
    private UUID workspaceId;
    private String name;
    private String key;
    private String description;
    private ProjectType projectType;
    private TemplateCategory templateCategory;
    private LocalDateTime updatedAt;

    private List<StatusChip> statuses;

    private long workItemTypeCount;
    private long sprintCount;
    private long workItemCount;
    private long epicCount;

    /** Độ dài timeline của mẫu (ngày) - FE dùng để gợi ý hạn chót. null nếu mẫu không có mốc thời gian. */
    private Long timelineSpanDays;

    /** Số dự án đã được tạo từ mẫu này. */
    private long usageCount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusChip {
        private String name;
        private StatusCategory category;
        private String colorHex;
    }
}
