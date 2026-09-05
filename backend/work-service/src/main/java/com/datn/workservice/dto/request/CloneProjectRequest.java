package com.datn.workservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CloneProjectRequest {

    @NotBlank(message = "New project name is required")
    @Size(max = 255, message = "Project name cannot exceed 255 characters")
    private String name;

    @NotBlank(message = "New project key is required")
    @Size(min = 2, max = 10, message = "Project key must be between 2 and 10 characters")
    @Pattern(
        regexp = "^[A-Z][A-Z0-9_]*$",
        message = "Project key must start with an uppercase letter and contain only uppercase alphanumeric characters and underscores"
    )
    private String key;

    @NotNull(message = "New project start date is required for dynamic timeline shifting")
    private LocalDate startDate;

    @Builder.Default
    private Boolean includeItems = true;

    @Builder.Default
    private Boolean includeSprints = true;
}
