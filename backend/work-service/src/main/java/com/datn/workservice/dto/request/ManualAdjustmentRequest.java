package com.datn.workservice.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualAdjustmentRequest {

    @NotNull(message = "Thành viên được điều chỉnh không được để trống")
    private UUID userId;

    private UUID sprintId;

    private UUID workItemId;

    @NotNull(message = "Số điểm điều chỉnh không được để trống")
    @DecimalMin(value = "-8.00", message = "Điểm điều chỉnh tối thiểu là -8")
    @DecimalMax(value = "8.00", message = "Điểm điều chỉnh tối đa là +8")
    private BigDecimal points;

    @NotBlank(message = "Lý do điều chỉnh không được để trống")
    @Size(min = 15, max = 255, message = "Lý do điều chỉnh phải có độ dài từ 15 đến 255 ký tự")
    private String reason;
}
