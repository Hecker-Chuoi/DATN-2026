package com.datn.workservice.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tùy chỉnh nhanh trước khi tạo dự án từ mẫu (Quick Customization).
 * Cho phép đổi tên trạng thái, loại việc, sprint, công việc hoặc loại bỏ bớt sprint/công việc không cần thiết.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateCustomizationsRequest {

    /** Map sourceStatusId -> tên mới */
    private Map<UUID, String> renamedStatuses;

    /** Map sourceTypeId -> tên mới */
    private Map<UUID, String> renamedTypes;

    /** Danh sách sourceSprintId cần bỏ qua không nhân bản */
    private List<UUID> excludedSprintIds;

    /** Map sourceSprintId -> tên sprint mới */
    private Map<UUID, String> renamedSprints;

    /** Danh sách sourceWorkItemId cần bỏ qua (tự động loại trừ các subtask con của nó) */
    private List<UUID> excludedWorkItemIds;

    /** Map sourceWorkItemId -> tên tóm tắt (summary) mới */
    private Map<UUID, String> renamedWorkItems;
}
