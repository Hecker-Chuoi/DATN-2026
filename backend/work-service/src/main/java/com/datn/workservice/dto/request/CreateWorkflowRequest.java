package com.datn.workservice.dto.request;

import jakarta.validation.constraints.NotBlank;
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
public class CreateWorkflowRequest {

    /**
     * Optional Project ID (null for system global workflow templates)
     */
    private UUID projectId;

    @NotBlank(message = "Tên quy trình không được để trống")
    @Size(max = 255, message = "Tên quy trình tối đa 255 ký tự")
    private String name;

    @Size(max = 1000, message = "Mô tả quy trình tối đa 1000 ký tự")
    private String description;

    @Builder.Default
    private Boolean isDefault = false;
}
