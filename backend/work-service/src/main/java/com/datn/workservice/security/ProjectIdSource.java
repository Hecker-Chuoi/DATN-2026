package com.datn.workservice.security;

/**
 * Cho biết path variable trên endpoint đang mang ID của loại tài nguyên nào, để
 * {@link ProjectScopeResolver} truy ngược ra projectId phục vụ kiểm tra RBAC.
 *
 * <p>Cần thiết vì các endpoint của Giai đoạn 6 được định danh theo boardId / sprintId
 * chứ không phải projectId.
 */
public enum ProjectIdSource {
    /** Path variable đã chính là projectId (mặc định, hành vi cũ). */
    PROJECT,
    BOARD,
    SPRINT,
    WORK_ITEM
}
