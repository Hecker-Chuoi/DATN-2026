package com.datn.workservice.dto.request;

import com.datn.workservice.enums.StatusCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
public class CreateStatusRequest {

    private UUID projectId;

    @NotBlank(message = "Tên trạng thái không được để trống")
    @Size(max = 100, message = "Tên trạng thái tối đa 100 ký tự")
    private String name;

    @NotNull(message = "Danh mục trạng thái là bắt buộc (TODO, IN_PROGRESS, DONE)")
    private StatusCategory category;

    @Size(max = 500, message = "Mô tả trạng thái tối đa 500 ký tự")
    private String description;

    @Pattern(regexp = "^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$", message = "Mã màu phải là định dạng Hex (VD: #10B981)")
    private String colorHex;


}
