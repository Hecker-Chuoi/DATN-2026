package com.datn.workservice.dto.response;

import com.datn.workservice.enums.LinkType;
import com.datn.workservice.enums.StatusCategory;
import com.datn.workservice.enums.WorkItemPriority;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkItemLinkResponse {
    private UUID id;
    private UUID sourceItemId;
    private UUID targetItemId;
    private LinkType linkType;
    private String relationshipDescription; // e.g. "blocks", "is blocked by", "relates to", "duplicates", "is duplicated by", "causes", "is caused by"
    private boolean isOutward; // true if current item is source, false if current item is target

    // Linked item metadata (the other work item in this relationship)
    private UUID linkedItemId;
    private String linkedItemCode;
    private String linkedItemSummary;
    private UUID linkedItemTypeId;
    private String linkedItemTypeName;
    private String linkedItemTypeCode;
    private String linkedItemTypeColor;
    private String linkedItemTypeIcon;
    private UUID linkedItemStatusId;
    private String linkedItemStatusName;
    private StatusCategory linkedItemStatusCategory;
    private String linkedItemStatusColorHex;
    private WorkItemPriority linkedItemPriority;

    private UUID createdBy;
    private String createdByName;
    private LocalDateTime createdAt;
}
