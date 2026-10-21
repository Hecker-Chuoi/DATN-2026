package com.datn.workservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.Valid;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Tạo dự án thật từ một dự án mẫu.
 * <p>
 * Toàn bộ cấu hình (trạng thái, loại công việc, workflow, board, vai trò & quyền,
 * nhãn, component, custom field, automation) luôn được sao chép. Sprint, công việc
 * và version là tùy chọn. Mốc thời gian của mẫu được dời theo {@code startDate}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProjectFromTemplateRequest {

    @NotNull(message = "Workspace ID is required")
    private UUID workspaceId;

    private UUID teamId;

    @NotBlank(message = "Project name is required")
    @Size(max = 255, message = "Project name cannot exceed 255 characters")
    private String name;

    @NotBlank(message = "Project key is required")
    @Size(min = 2, max = 10, message = "Project key must be between 2 and 10 characters")
    @Pattern(
        regexp = "^[A-Z][A-Z0-9_]*$",
        message = "Project key must start with an uppercase letter and contain only uppercase alphanumeric characters and underscores"
    )
    private String key;

    private String description;

    private UUID leadUserId;

    /** Mốc bắt đầu của dự án mới; mọi ngày trong mẫu được dời tương ứng. Mặc định: hôm nay. */
    private LocalDate startDate;

    /** Bỏ trống thì tự tính = startDate + độ dài timeline của mẫu. */
    private LocalDate endDate;

    @Builder.Default
    private Boolean includeSprints = true;

    @Builder.Default
    private Boolean includeWorkItems = true;

    @Builder.Default
    private Boolean includeVersions = true;

    @Valid
    private TemplateCustomizationsRequest customizations;
}
