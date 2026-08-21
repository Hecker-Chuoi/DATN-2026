package com.datn.workservice.dto.request;

import com.datn.workservice.enums.MilestoneStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMilestoneRequest {

    @NotNull(message = "Project ID không được để trống")
    private UUID projectId;

    @NotBlank(message = "Tên cột mốc không được để trống")
    @Size(max = 255, message = "Tên cột mốc tối đa 255 ký tự")
    private String name;

    private String description;

    private LocalDateTime dueDate;

    @Builder.Default
    private MilestoneStatus status = MilestoneStatus.OPEN;
}
