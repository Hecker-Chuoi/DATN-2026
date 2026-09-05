package com.datn.workservice.dto.request;

import jakarta.validation.constraints.NotBlank;
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
public class UpdateTeamRequest {

    @NotBlank(message = "Team name is required")
    @Size(max = 255, message = "Team name cannot exceed 255 characters")
    private String name;

    private String description;

    private UUID ownerId;
}
