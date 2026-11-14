package com.datn.common.notification;

/**
 * Mã loại thông báo, dùng chung giữa phía phát (work-service, workspace-service)
 * và phía nhận (notification-service).
 *
 * Danh mục đầy đủ (khi nào gửi, gửi cho ai, mức độ…): docs/services/notification/1_DANH_MUC_LOAI_THONG_BAO.md.
 * Mã trong ngoặc là số hiệu trong tài liệu đó.
 *
 * Các loại đánh dấu "nội bộ" không tạo thông báo cho ai; chúng chỉ dùng để đóng
 * các thông báo "Cần xử lý" đang mở (ví dụ lời mời bị huỷ).
 */
public enum NotificationType {

    // ─── A. Công việc ───
    /** (A1) Được giao công việc. */
    TASK_ASSIGNED,
    /** (A2) Bị chuyển khỏi công việc / bị huỷ giao. */
    TASK_UNASSIGNED,
    /** (A3) Công việc đổi trạng thái. */
    TASK_STATUS_CHANGED,
    /** (A4) Ưu tiên được nâng lên Cao / Rất cao. */
    TASK_PRIORITY_RAISED,
    /** (A5) Hạn chót thay đổi. */
    TASK_DUE_DATE_CHANGED,
    /** (A6) Công việc bị xoá. */
    TASK_DELETED,
    /** (A7) Công việc cha tự mở lại vì công việc con chưa xong. */
    PARENT_AUTO_REOPENED,
    /** (A8) Công việc con được hoàn thành theo công việc cha. */
    SUBTASKS_AUTO_RESOLVED,
    /** (A9) Bị đóng do trùng lặp. */
    TASK_MARKED_DUPLICATE,
    /** (A10) Công việc đổi chu kỳ. */
    TASK_SPRINT_CHANGED,

    // ─── B. Phụ thuộc ───
    /** (B1) Công việc bị cản trở (BLOCKS). */
    TASK_BLOCKED,
    /** (B2) Hết bị cản trở. */
    TASK_UNBLOCKED,

    // ─── C. Xác nhận hoàn thành ───
    /** (C1) Có yêu cầu xác nhận hoàn thành, chờ Quản lý. */
    COMPLETION_REQUESTED,
    /** (C2) Được xác nhận hoàn thành. */
    COMPLETION_APPROVED,
    /** (C3) Không được xác nhận hoàn thành. */
    COMPLETION_REJECTED,
    /** (C4) Nhắc Quản lý: yêu cầu chờ quá lâu (job). */
    COMPLETION_PENDING_REMINDER,
    /** (C5) Yêu cầu xác nhận bị huỷ. */
    COMPLETION_CANCELLED,

    // ─── D. Bình luận ───
    /** (D1) Được nhắc tên trong bình luận. */
    COMMENT_MENTION,
    /** (D2) Có bình luận mới. */
    COMMENT_ADDED,
    /** (D3) Có người trả lời bình luận của mình. */
    COMMENT_REPLIED,

    // ─── E. Chu kỳ / Giai đoạn ───
    /** (E1) Chu kỳ bắt đầu. */
    SPRINT_STARTED,
    /** (E2) Chu kỳ hoàn tất. */
    SPRINT_COMPLETED,
    /** (E3) Chu kỳ quá hạn (job). */
    SPRINT_OVERDUE,
    /** (E4) Chu kỳ sắp kết thúc (job). */
    SPRINT_ENDING_SOON,
    /** (E5) Được thêm việc vào chu kỳ đang chạy. */
    SPRINT_SCOPE_ADDED,
    /** (E6) Đến ngày bắt đầu chu kỳ tiếp theo (job). */
    NEXT_SPRINT_DUE,
    /** (E7) Bị đưa ra khỏi chu kỳ đang chạy. */
    SPRINT_SCOPE_REMOVED,
    /** (E8) Chu kỳ đổi ngày. */
    SPRINT_DATES_CHANGED,
    /** (E9) Chu kỳ bị xoá. */
    SPRINT_DELETED,

    // ─── F. Nhắc hạn (job) ───
    /** (F1) Sắp đến hạn. */
    TASK_DUE_SOON,
    /** (F2) Đến hạn hôm nay. */
    TASK_DUE_TODAY,
    /** (F3) Quá hạn. */
    TASK_OVERDUE,
    /** (F4) Tổng hợp hằng ngày cho Quản lý. */
    PROJECT_DAILY_DIGEST,
    /** (F5) Hôm nay là ngày bắt đầu. */
    TASK_START_TODAY,
    /** (F6) Lâu không cập nhật. */
    TASK_STALE,
    /** (F7) Dự án sắp kết thúc. */
    PROJECT_ENDING_SOON,
    /** (F8) Dự án quá hạn. */
    PROJECT_OVERDUE,

    // ─── G. Thành viên & quyền ───
    /** (G1) Được mời vào dự án. */
    PROJECT_INVITATION_RECEIVED,
    /** (G2) Bị xoá khỏi dự án. */
    PROJECT_MEMBER_REMOVED,
    /** (G3) Thành viên rời đi còn việc dở. */
    MEMBER_REMOVED_OPEN_ITEMS,
    /** (G4) Lời mời vào dự án được trả lời. */
    PROJECT_INVITATION_ANSWERED,
    /** (G5) Được thêm thẳng vào dự án. */
    PROJECT_MEMBER_ADDED,
    /** (G6) Vai trò trong dự án thay đổi. */
    PROJECT_ROLE_CHANGED,
    /** (G7) Đổi Trưởng dự án. */
    PROJECT_LEAD_CHANGED,
    /** (G8) Được mời vào không gian làm việc. */
    WORKSPACE_INVITATION_RECEIVED,
    /** (G9) Bị xoá khỏi không gian làm việc. */
    WORKSPACE_MEMBER_REMOVED,
    /** (G10) Vai trò trong không gian làm việc thay đổi. */
    WORKSPACE_ROLE_CHANGED,
    /** (G11) Lời mời vào không gian làm việc được trả lời. */
    WORKSPACE_INVITATION_ANSWERED,

    // ─── H. Dự án ───
    /** (H1) Dự án bị lưu trữ. */
    PROJECT_ARCHIVED,
    /** (H2) Dự án được khôi phục. */
    PROJECT_RESTORED,
    /** (H3) Dự án đổi lịch. */
    PROJECT_SCHEDULE_CHANGED,

    // ─── I. Đóng góp ───
    /** (I1) Điểm đóng góp được điều chỉnh. */
    CONTRIBUTION_ADJUSTED,
    /** (I2) Chốt bảng đóng góp. */
    CONTRIBUTION_FROZEN,

    // ─── Nội bộ: chỉ đóng thông báo "Cần xử lý", không tạo thông báo mới ───
    /** Lời mời vào dự án bị huỷ: đóng G1. */
    PROJECT_INVITATION_CANCELLED,
    /** Lời mời vào không gian làm việc bị huỷ: đóng G8. */
    WORKSPACE_INVITATION_CANCELLED,
    /** Người gửi tự rút lại yêu cầu xác nhận hoàn thành: đóng C1 của các Quản lý. */
    COMPLETION_WITHDRAWN;

    /** Loại chỉ dùng để đóng thông báo khác, không có nội dung hiển thị. */
    public boolean isInternal() {
        return this == PROJECT_INVITATION_CANCELLED || this == WORKSPACE_INVITATION_CANCELLED || this == COMPLETION_WITHDRAWN;
    }

    /** Đọc an toàn từ chuỗi; trả về {@code null} nếu không khớp loại nào. */
    public static NotificationType parse(String value) {
        if (value == null) return null;
        try {
            return NotificationType.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
