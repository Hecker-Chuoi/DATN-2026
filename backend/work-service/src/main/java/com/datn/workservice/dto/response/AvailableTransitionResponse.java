package com.datn.workservice.dto.response;

import com.datn.workservice.enums.StatusCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Response DTO describing a transition available for the current user and work item.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailableTransitionResponse {

    private UUID transitionId;
    private String name;
    private UUID toStatusId;
    private String toStatusName;
    private StatusCategory toStatusCategory;
    private String toStatusColorHex;
    private Boolean hasScreen;
    private List<String> requiredFields;
}
