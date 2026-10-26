package com.datn.workspaceservice.enums;

/**
 * Vai trò của một user trong MỘT workspace cụ thể.
 * <p>
 * Khác {@code PermissionKey} của work-service (kiểm tra thuộc tập hợp), đây là
 * thang bậc CÓ THỨ TỰ: OWNER > ADMIN > MEMBER > GUEST. Mọi kiểm tra quyền trong
 * workspace-service là so sánh thứ tự qua {@link #atLeast(WorkspaceRole)}.
 * <p>
 * Thứ tự khai báo đi từ cao xuống thấp nên {@code ordinal()} nhỏ = quyền lớn.
 */
public enum WorkspaceRole {

    /** Toàn quyền, kể cả xóa workspace và cấp vai trò OWNER cho người khác. */
    OWNER,

    /** Quản trị thành viên và cấu hình workspace, nhưng không xóa được workspace. */
    ADMIN,

    /** Thành viên thường: tạo được project trong workspace. */
    MEMBER,

    /** Chỉ xem: không tạo được gì, không thấy nút quản trị. */
    GUEST;

    /**
     * Vai trò này có bậc bằng hoặc cao hơn {@code min} hay không.
     *
     * @param min bậc tối thiểu yêu cầu
     * @return true nếu đủ bậc
     */
    public boolean atLeast(WorkspaceRole min) {
        if (min == null) {
            return true;
        }
        // ordinal nhỏ hơn = quyền cao hơn (OWNER = 0)
        return this.ordinal() <= min.ordinal();
    }
}
