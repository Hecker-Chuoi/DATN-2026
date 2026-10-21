package com.datn.workservice.dto.request;

import com.datn.workservice.enums.DefaultAssigneeType;
import com.datn.workservice.enums.ProjectType;
import com.datn.workservice.enums.TemplateCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProjectRequest {

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

    @Size(max = 500, message = "Icon URL cannot exceed 500 characters")
    private String iconUrl;

    private UUID leadUserId;

    @Builder.Default
    private DefaultAssigneeType defaultAssigneeType = DefaultAssigneeType.PROJECT_LEADER;

    @Builder.Default
    private ProjectType projectType = ProjectType.SOFTWARE_SCRUM;

    @Builder.Default
    private Boolean isTemplate = false;

    private TemplateCategory templateCategory;

    private LocalDate startDate;

    private LocalDate endDate;
}
