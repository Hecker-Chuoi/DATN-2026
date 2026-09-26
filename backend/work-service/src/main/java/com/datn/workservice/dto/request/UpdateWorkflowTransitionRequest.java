package com.datn.workservice.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateWorkflowTransitionRequest {

    private UUID fromStatusId;

    private UUID toStatusId;

    @Size(max = 150, message = "Tên bước chuyển tối đa 150 ký tự")
    private String name;

    private List<Map<String, Object>> conditionsConfig;

    private List<Map<String, Object>> validatorsConfig;

    private List<Map<String, Object>> postFunctionsConfig;
}
