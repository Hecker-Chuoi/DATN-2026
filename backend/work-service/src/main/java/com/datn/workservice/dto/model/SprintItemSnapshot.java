package com.datn.workservice.dto.model;

import com.datn.workservice.enums.StatusCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/** Anh chup mot cong viec tai thoi diem dong Sprint (luu trong snapshot_data JSONB). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SprintItemSnapshot {

    private UUID id;
    private String code;
    private String summary;
    private String typeName;
    private String statusName;
    private StatusCategory statusCategory;
    private BigDecimal storyPoints;
    private UUID assigneeId;
}
