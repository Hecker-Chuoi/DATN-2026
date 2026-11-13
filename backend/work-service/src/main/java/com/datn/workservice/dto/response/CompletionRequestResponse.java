package com.datn.workservice.dto.response;

import com.datn.workservice.enums.CompletionRequestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Một yêu cầu xác nhận hoàn thành ("Tạm hoàn thành") kèm thông tin hiển thị.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompletionRequestResponse {

    private UUID id;
    private UUID projectId;
    private CompletionRequestStatus status;

    // Công việc
    private UUID workItemId;
    private String workItemCode;
    private String workItemSummary;
    private UUID currentStatusId;
    private String currentStatusName;
    private UUID assigneeId;
    private String assigneeName;

    // Đề xuất
    private UUID targetStatusId;
    private String targetStatusName;
    private String resolution;
    private Boolean resolveAllSubtasks;
    private String requestNote;

    private UUID requestedBy;
    private String requestedByName;
    private LocalDateTime requestedAt;

    // Quyết định
    private UUID decidedBy;
    private String decidedByName;
    private LocalDateTime decidedAt;
    private String decisionNote;
}
