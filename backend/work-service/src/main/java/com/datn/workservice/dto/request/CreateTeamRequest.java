package com.datn.workservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTeamRequest {

    @NotNull(message = "Workspace ID is required")
    private UUID workspaceId;

    @NotBlank(message = "Team name is required")
    @Size(max = 255, message = "Team name cannot exceed 255 characters")
    private String name;

    private String description;
}
