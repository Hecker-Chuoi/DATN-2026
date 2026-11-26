package com.datn.workservice.service;

import com.datn.workservice.dto.request.ExecuteTransitionRequest;
import com.datn.workservice.dto.response.AvailableTransitionResponse;
import com.datn.workservice.entity.WorkItem;
import com.datn.workservice.entity.WorkItemCompletionRequest;
import com.datn.workservice.entity.Workflow;

import java.util.List;
import java.util.UUID;

public interface WorkflowEngineService {

    /**
     * Resolves the active Workflow entity for a given project and work item type.
     * Hierarchy: Project Scheme -> Scheme Mapping -> Project Default -> Global Default.
     */
    Workflow resolveWorkflow(UUID projectId, UUID workItemTypeId);

    /**
     * Retrieves all available transitions from the current status of a work item,
     * evaluating all configured transition conditions against the current user.
     */
    List<AvailableTransitionResponse> getAvailableTransitions(UUID workItemId, UUID currentUserId);

    /**
     * Executes a workflow transition on a work item, evaluating conditions,
     * enforcing validators, updating status/resolution, running post-functions,
     * and recording changelog entries.
     *
     * Nếu bước chuyển đưa công việc vào nhóm DONE mà người dùng không phải Admin dự án thì trạng thái
     * KHÔNG đổi: hệ thống chỉ tạo yêu cầu "Tạm hoàn thành" và trả về công việc với
     * {@code completionApprovalRequested = true} (BR-W03).
     */
    WorkItem executeTransition(UUID workItemId, UUID transitionId, ExecuteTransitionRequest request, UUID currentUserId);

    /**
     * Admin dự án xác nhận một yêu cầu "Tạm hoàn thành": chạy lại bước chuyển đã lưu (hoặc bước chuyển
     * tương đương nếu quy trình đã đổi). Condition của bước chuyển KHÔNG đánh giá lại với Admin vì đã được
     * kiểm tra với người yêu cầu; các guard còn lại (việc tiền đề, Sub-task, validator) vẫn chạy lại.
     */
    WorkItem executeApprovedCompletion(UUID workItemId, WorkItemCompletionRequest request, UUID adminId);
}
