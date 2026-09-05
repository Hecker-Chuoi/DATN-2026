package com.datn.workservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowSchemeMappingResponse {

    private UUID id;
    private UUID schemeId;
    private UUID workItemTypeId;
    private String workItemTypeName;
    private String workItemTypeCode;
    private Integer hierarchyLevel;
    private UUID workflowId;
    private String workflowName;
}
