package com.datn.workspaceservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateWorkspaceRequest {

    @NotBlank(message = "Tên workspace không được để trống")
    @Size(max = 255, message = "Tên workspace tối đa 255 ký tự")
    private String name;

    @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự")
    private String description;

    @Size(max = 500)
    private String avatarUrl;
}
