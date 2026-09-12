package com.datn.workservice.dto.response;

import com.datn.workservice.enums.StatusCategory;
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
public class WorkflowTransitionResponse {

    private UUID id;
    private UUID workflowId;
    private UUID fromStatusId;
    private String fromStatusName;
    private StatusCategory fromStatusCategory;
    private UUID toStatusId;
    private String toStatusName;
    private StatusCategory toStatusCategory;
    private String name;
    private List<Map<String, Object>> conditionsConfig;
    private List<Map<String, Object>> validatorsConfig;
    private List<Map<String, Object>> postFunctionsConfig;
}
