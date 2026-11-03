package com.datn.workservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContributionLedgerEntryResponse {
    private UUID id;
    private OffsetDateTime createdAt;
    private UUID userId;
    private String userName;
    private String userAvatar;
    private UUID workItemId;
    private String workItemCode;
    private String workItemTitle;
    private BigDecimal points;
    private String entryType; // COMPLETED, REOPENED, DELETED, MANUAL_ADJUSTMENT
    private String note;
    private UUID actorId;
    private String actorName;
}
