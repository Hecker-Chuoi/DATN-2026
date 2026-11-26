package com.datn.workservice.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Người được giao của Task vừa đổi trong DATN (giao, đổi người, hủy giao). Listener chạy sau commit để
 * đẩy người được giao sang GitHub Issue đã liên kết. Đổi do webhook GitHub thì KHÔNG phát (tránh vòng lặp).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkItemAssigneeChangedEvent {

    private UUID workItemId;

    private UUID oldAssigneeId;

    /** null = hủy giao. */
    private UUID newAssigneeId;

    private UUID changedByUserId;
}
