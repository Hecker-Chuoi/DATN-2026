package com.datn.workservice.enums;

import lombok.Getter;

@Getter
public enum TeamRole {
    TEAM_LEAD("Trưởng nhóm (Leader)", "Quản lý toàn bộ hoạt động của Team: duyệt/thêm/xóa thành viên trong Team, tạo và phân bổ các Project cho Team, quản lý tài nguyên chung của Team."),
    TEAM_MEMBER("Thành viên Team", "Nhân sự thuộc Team, có thể tham gia và thao tác trong các Project được gán cho Team."),
    TEAM_VIEWER("Người quan sát Team", "Được theo dõi danh sách thành viên và các dự án của Team nhưng không có quyền chỉnh sửa cấu hình hay thành viên của Team.");

    private final String title;
    private final String description;

    TeamRole(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public static TeamRole fromString(String role) {
        if (role == null || role.isBlank()) {
            return TEAM_MEMBER;
        }
        String clean = role.trim().toUpperCase();
        if (clean.equals("TEAM_LEAD") || clean.equals("LEADER") || clean.equals("LEAD")) {
            return TEAM_LEAD;
        }
        if (clean.equals("TEAM_VIEWER") || clean.equals("VIEWER")) {
            return TEAM_VIEWER;
        }
        return TEAM_MEMBER;
    }
}
