package com.datn.workservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/** Mot cot trong yeu cau cau hinh lai bang. id = null nghia la them cot moi. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoardColumnConfigRequest {

    private UUID id;

    @NotBlank(message = "Tên cột là bắt buộc")
    @Size(max = 150, message = "Tên cột tối đa 150 ký tự")
    private String name;

    @NotNull(message = "Thứ tự cột là bắt buộc")
    private Integer columnOrder;

    @Min(value = 0, message = "Giới hạn WIP tối thiểu không được âm")
    private Integer minWipLimit;

    @Min(value = 1, message = "Giới hạn WIP tối đa phải lớn hơn 0")
    private Integer maxWipLimit;

    @NotEmpty(message = "Cột phải ánh xạ với ít nhất một trạng thái")
    private List<UUID> statusIds;
}
