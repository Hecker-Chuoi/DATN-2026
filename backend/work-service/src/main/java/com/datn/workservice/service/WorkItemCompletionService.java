package com.datn.workservice.service;

import com.datn.workservice.dto.response.CompletionRequestResponse;
import com.datn.workservice.entity.WorkItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Nghiệp vụ "Tạm hoàn thành" → Admin dự án xác nhận / từ chối (BR-W03..BR-W08).
 */
public interface WorkItemCompletionService {

    /** Các yêu cầu đang chờ xác nhận của dự án, cũ nhất trước. */
    List<CompletionRequestResponse> listPending(UUID projectId);

    /** Yêu cầu đang chờ của một công việc (nếu có). */
    Optional<CompletionRequestResponse> getPending(UUID workItemId);

    /** Toàn bộ lịch sử yêu cầu xác nhận của một công việc, mới nhất trước. */
    List<CompletionRequestResponse> getHistory(UUID workItemId);

    /** Admin dự án xác nhận: công việc thật sự chuyển sang trạng thái Hoàn thành, ghi công cho người thực hiện. */
    WorkItem approve(UUID workItemId, UUID adminId);

    /** Admin dự án từ chối: công việc giữ nguyên trạng thái, lý do được ghi lại. */
    CompletionRequestResponse reject(UUID workItemId, String reason, UUID adminId);

    /** Người gửi yêu cầu (hoặc Admin dự án) rút lại yêu cầu. */
    CompletionRequestResponse cancel(UUID workItemId, UUID userId);
}
