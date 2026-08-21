package com.datn.workservice.dto.request;

import com.datn.workservice.enums.ProjectStatus;
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

    private UUID teamId;

    @NotBlank(message = "Tên dự án không được để trống")
    @Size(max = 255, message = "Tên dự án tối đa 255 ký tự")
    private String name;

    private String description;

    private ProjectStatus status;

    private LocalDate startDate;

    private LocalDate endDate;

    private Boolean isPublic;

    private Boolean isTemplate;
}
