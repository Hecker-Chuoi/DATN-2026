package com.datn.workservice.dto.request;

import com.datn.workservice.enums.DefaultAssigneeType;
import com.datn.workservice.enums.ProjectType;
import com.datn.workservice.enums.TemplateCategory;
import jakarta.validation.constraints.NotBlank;
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
public class UpdateProjectRequest {

    @NotBlank(message = "Project name is required")
    @Size(max = 255, message = "Project name cannot exceed 255 characters")
    private String name;

    private UUID teamId;

    private String description;

    @Size(max = 500, message = "Icon URL cannot exceed 500 characters")
    private String iconUrl;

    private UUID leadUserId;

    private DefaultAssigneeType defaultAssigneeType;

    private ProjectType projectType;

    private Boolean isTemplate;

    private TemplateCategory templateCategory;

    private LocalDate startDate;

    private LocalDate endDate;
}
