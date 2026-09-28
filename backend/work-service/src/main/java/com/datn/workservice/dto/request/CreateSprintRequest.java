package com.datn.workservice.dto.request;

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
public class CreateSprintRequest {

    @NotBlank(message = "Tên Sprint là bắt buộc")
    @Size(max = 255, message = "Tên Sprint tối đa 255 ký tự")
    private String name;

    private String goal;

    private LocalDateTime startDate;

    private LocalDateTime endDate;
}
