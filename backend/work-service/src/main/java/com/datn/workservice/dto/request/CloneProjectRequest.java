package com.datn.workservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class CloneProjectRequest {

    private UUID sourceProjectId;

    private UUID teamId;

    @NotBlank(message = "Mã dự án mới (Key) không được để trống")
    @Size(min = 2, max = 100, message = "Mã dự án phải từ 2-100 ký tự (VD: du_an_moi_a1b2c3d4)")
    private String newKey;

    @NotBlank(message = "Tên dự án mới không được để trống")
    @Size(max = 255, message = "Tên dự án tối đa 255 ký tự")
    private String newName;

    private String newDescription;

    @Builder.Default
    private Boolean isPublic = false;
}
