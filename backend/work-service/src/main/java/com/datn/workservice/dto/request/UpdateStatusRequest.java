package com.datn.workservice.dto.request;

import com.datn.workservice.enums.StatusCategory;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStatusRequest {

    @Size(max = 100, message = "Tên trạng thái tối đa 100 ký tự")
    private String name;

    private StatusCategory category;

    @Size(max = 500, message = "Mô tả trạng thái tối đa 500 ký tự")
    private String description;

    @Pattern(regexp = "^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$", message = "Mã màu phải là định dạng Hex (VD: #10B981)")
    private String colorHex;
}
