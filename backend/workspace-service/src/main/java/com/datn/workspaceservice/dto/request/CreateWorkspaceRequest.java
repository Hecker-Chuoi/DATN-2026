package com.datn.workspaceservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateWorkspaceRequest {

    @NotBlank(message = "Tên workspace không được để trống")
    @Size(max = 255, message = "Tên workspace tối đa 255 ký tự")
    private String name;

    /**
     * Bỏ trống thì service tự sinh slug từ name (và thêm hậu tố -2, -3... nếu trùng).
     */
    @Size(max = 100, message = "Slug tối đa 100 ký tự")
    @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
            message = "Slug chỉ gồm chữ thường, số và dấu gạch ngang")
    private String slug;

    @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự")
    private String description;

    @Size(max = 500)
    private String avatarUrl;
}
