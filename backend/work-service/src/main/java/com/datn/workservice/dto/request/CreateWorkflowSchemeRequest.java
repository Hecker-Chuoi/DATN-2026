package com.datn.workservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateWorkflowSchemeRequest {

    @NotNull(message = "ID dự án (projectId) là bắt buộc")
    private UUID projectId;

    @NotBlank(message = "Tên sơ đồ quy trình không được để trống")
    @Size(max = 255, message = "Tên sơ đồ quy trình tối đa 255 ký tự")
    private String name;

    @Size(max = 1000, message = "Mô tả sơ đồ quy trình tối đa 1000 ký tự")
    private String description;

    /**
     * Mappings of workItemTypeId to workflowId
     */
    private List<WorkflowSchemeMappingItem> mappings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowSchemeMappingItem {
        @NotNull(message = "workItemTypeId là bắt buộc")
        private UUID workItemTypeId;

        @NotNull(message = "workflowId là bắt buộc")
        private UUID workflowId;
    }
}
