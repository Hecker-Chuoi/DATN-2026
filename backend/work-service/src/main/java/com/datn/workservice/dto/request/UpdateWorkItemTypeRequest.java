package com.datn.workservice.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateWorkItemTypeRequest {

    @NotBlank(message = "Work item type name is required")
    @Size(max = 100, message = "Work item type name cannot exceed 100 characters")
    private String name;

    @Size(max = 50, message = "Work item type code cannot exceed 50 characters")
    private String code;

    private String description;

    @Size(max = 100, message = "Icon name cannot exceed 100 characters")
    private String iconName;

    @Pattern(
        regexp = "^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$",
        message = "Color must be a valid hex color code (e.g. #FF5733 or #FFF)"
    )
    private String colorHex;

    @Min(value = -1, message = "Hierarchy level must be between -1 (Subtask) and 1 (Epic)")
    @Max(value = 1, message = "Hierarchy level must be between -1 (Subtask) and 1 (Epic)")
    private Integer hierarchyLevel;

    private Boolean isSubtask;
}
