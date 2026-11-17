package com.datn.workservice.service;

import com.datn.workservice.dto.response.WorkItemChangelogResponse;
import com.datn.workservice.entity.WorkItem;

import java.util.List;
import java.util.UUID;

public interface WorkItemAuditService {

    /**
     * Records diff between old and new state of a WorkItem
     */
    void recordDiff(WorkItem oldItem, WorkItem newItem, UUID userId);

    /**
     * Records a single changelog event (e.g., status transition, attachment, comment, link)
     */
    void recordCustomEvent(UUID workItemId, String fieldName, String oldValue, String newValue, UUID userId);

    /**
     * Ghi lịch sử giao / hủy giao việc (field ASSIGNEE). Giá trị lưu là TÊN hiển thị;
     * hủy giao thì giá trị mới để trống. Không ghi gì nếu người thực hiện không đổi.
     */
    void recordAssigneeChange(UUID workItemId, UUID oldAssigneeId, UUID newAssigneeId, UUID userId);

    /**
     * Retrieves full changelog history for a work item with author details and localized field names
     */
    List<WorkItemChangelogResponse> getChangelogs(UUID workItemId);
}
