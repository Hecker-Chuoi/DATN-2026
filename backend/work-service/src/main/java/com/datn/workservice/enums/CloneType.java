package com.datn.workservice.enums;

/**
 * Ngữ cảnh của một lần nhân bản dự án (bảng project_clones.clone_type).
 */
public enum CloneType {
    /** Tạo dự án thật từ một dự án mẫu. */
    FROM_TEMPLATE,
    /** Lưu cấu hình (và tùy chọn: sprint, công việc) của một dự án thành dự án mẫu mới. */
    SAVE_AS_TEMPLATE,
    /** Nhân bản một dự án thường thành dự án thường khác. */
    DUPLICATE
}
