package com.datn.workservice.service;

import com.datn.workservice.dto.request.ManualAdjustmentRequest;
import com.datn.workservice.dto.response.ContributionLedgerEntryResponse;
import com.datn.workservice.dto.response.ContributionOverviewResponse;
import com.datn.workservice.dto.response.MemberContributionDetailResponse;
import com.datn.workservice.entity.Status;
import com.datn.workservice.entity.WorkItem;
import jakarta.servlet.http.HttpServletResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ContributionReportService {

    /**
     * Bắt sự kiện chuyển trạng thái WorkItem để ghi sổ ghi công (COMPLETED hoặc REOPENED)
     */
    void handleWorkItemStatusTransition(WorkItem item, Status oldStatus, Status newStatus, UUID actorId);

    /**
     * Như trên nhưng tính phạt trễ hạn theo {@code completedOn} — ngày thành viên báo hoàn thành
     * (gửi yêu cầu "Tạm hoàn thành"), để Admin xác nhận muộn không làm người làm bị trừ điểm.
     */
    default void handleWorkItemStatusTransition(WorkItem item, Status oldStatus, Status newStatus, UUID actorId,
                                                java.time.LocalDate completedOn) {
        handleWorkItemStatusTransition(item, oldStatus, newStatus, actorId);
    }

    /**
     * Bắt sự kiện xóa WorkItem để ghi dòng hủy điểm nếu task đó đã từng tính điểm
     */
    void handleWorkItemDeleted(WorkItem item, UUID actorId);

    /**
     * Lấy dữ liệu tổng quan báo cáo đóng góp (Màn hình 1)
     */
    ContributionOverviewResponse getContributionOverview(
            UUID projectId, UUID sprintId, LocalDate fromDate, LocalDate toDate, UUID currentUserId);

    /**
     * Lấy danh sách Sổ ghi công (Màn hình 2)
     */
    List<ContributionLedgerEntryResponse> getLedgerEntries(UUID projectId, UUID sprintId, UUID currentUserId);

    /**
     * Lấy chi tiết đóng góp của một thành viên (Màn hình 3)
     */
    MemberContributionDetailResponse getMemberDetail(
            UUID projectId, UUID targetUserId, UUID sprintId, UUID currentUserId);

    /**
     * Leader điều chỉnh điểm thủ công
     */
    ContributionLedgerEntryResponse manualAdjustment(
            UUID projectId, ManualAdjustmentRequest request, UUID leaderUserId);

    /**
     * Đóng băng số liệu đóng góp khi Sprint chuyển sang CLOSED
     */
    void freezeSprintContributionSnapshot(UUID sprintId, UUID projectId);

    /**
     * Xuất dữ liệu báo cáo đóng góp ra file Excel / CSV
     */
    void exportContributionCsv(UUID projectId, UUID sprintId, HttpServletResponse response);
}
