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

    @NotNull(message = "Dự án nguồn (Template) không được để trống")
    private UUID sourceProjectId;

    private UUID teamId;

    @NotBlank(message = "Mã dự án mới (Key) không được để trống")
    @Size(min = 2, max = 20, message = "Mã dự án phải từ 2-20 ký tự (VD: PTMS)")
    private String newKey;

    @NotBlank(message = "Tên dự án mới không được để trống")
    @Size(max = 255, message = "Tên dự án tối đa 255 ký tự")
    private String newName;

    private String newDescription;

    @Builder.Default
    private Boolean isPublic = false;
}
