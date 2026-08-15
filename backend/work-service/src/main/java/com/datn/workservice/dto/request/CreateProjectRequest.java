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
public class CreateProjectRequest {

    private UUID teamId;

    @NotBlank(message = "Mã dự án (Key) không được để trống")
    @Size(min = 2, max = 20, message = "Mã dự án phải từ 2-20 ký tự (VD: PTMS)")
    private String key;

    @NotBlank(message = "Tên dự án không được để trống")
    @Size(max = 255, message = "Tên dự án tối đa 255 ký tự")
    private String name;

    private String description;

    @Builder.Default
    private ProjectStatus status = ProjectStatus.PLANNING;

    private LocalDate startDate;

    private LocalDate endDate;

    @Builder.Default
    private Boolean isPublic = false;

    @Builder.Default
    private Boolean isTemplate = false;
}
