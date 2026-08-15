package com.datn.workservice.dto.request;

import com.datn.workservice.enums.MilestoneStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMilestoneRequest {

    @NotBlank(message = "Tên cột mốc không được để trống")
    @Size(max = 255, message = "Tên cột mốc tối đa 255 ký tự")
    private String name;

    private String description;

    private LocalDateTime dueDate;

    private MilestoneStatus status;
}
