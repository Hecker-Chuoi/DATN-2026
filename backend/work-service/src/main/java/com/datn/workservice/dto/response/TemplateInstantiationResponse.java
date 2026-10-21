package com.datn.workservice.dto.response;

import com.datn.workservice.enums.CloneType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * Kết quả tạo dự án từ mẫu / lưu dự án thành mẫu / nhân bản dự án.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateInstantiationResponse {

    private ProjectResponse project;
    private UUID sourceProjectId;
    private CloneType cloneType;
    private long dateOffsetDays;
    /** Số bản ghi đã sao chép theo loại: statuses, workflows, sprints, workItems, ... */
    private Map<String, Integer> stats;
    /** Cảnh báo toàn vẹn của dự án đích (không chặn). */
    private TemplateIntegrityReport integrity;
}
