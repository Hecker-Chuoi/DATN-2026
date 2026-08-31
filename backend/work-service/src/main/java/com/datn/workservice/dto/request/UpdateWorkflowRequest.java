package com.datn.workservice.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateWorkflowRequest {

    @Size(max = 255, message = "Tên quy trình tối đa 255 ký tự")
    private String name;

    @Size(max = 1000, message = "Mô tả quy trình tối đa 1000 ký tự")
    private String description;

    private Boolean isDefault;
}
