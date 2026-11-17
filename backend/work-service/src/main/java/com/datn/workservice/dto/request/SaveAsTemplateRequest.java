package com.datn.workservice.dto.request;

import com.datn.workservice.enums.TemplateCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lưu một dự án đang có thành dự án mẫu mới (dự án nguồn giữ nguyên).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveAsTemplateRequest {

    @NotBlank(message = "Template name is required")
    @Size(max = 255, message = "Template name cannot exceed 255 characters")
    private String name;

    @NotBlank(message = "Template key is required")
    @Size(min = 2, max = 10, message = "Project key must be between 2 and 10 characters")
    @Pattern(
        regexp = "^[A-Z][A-Z0-9_]*$",
        message = "Project key must start with an uppercase letter and contain only uppercase alphanumeric characters and underscores"
    )
    private String key;

    private String description;

    private TemplateCategory templateCategory;

    @Builder.Default
    private Boolean includeSprints = true;

    @Builder.Default
    private Boolean includeWorkItems = true;

    @Builder.Default
    private Boolean includeVersions = true;
}
