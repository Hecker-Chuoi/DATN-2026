package com.datn.workservice.event;

import com.datn.workservice.enums.StatusCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkItemStatusChangedEvent {

    private UUID workItemId;

    private UUID projectId;

    private StatusCategory oldCategory;

    private StatusCategory newCategory;

    private UUID changedByUserId;
}
