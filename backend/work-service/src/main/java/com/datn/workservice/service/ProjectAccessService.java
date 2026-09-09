package com.datn.workservice.service;

import com.datn.workservice.enums.PermissionKey;

import java.util.UUID;

/**
 * Cổng kiểm tra quyền dùng ở tầng service cho NGƯỜI DÙNG HIỆN TẠI (lấy từ SecurityContext).
 * Dùng khi endpoint không thể gắn @RequireProjectPermission trực tiếp
 * (ví dụ phải tra project qua key, work item code, attachment id, workflow id...).
 *
 * Mọi phương thức assert* ném {@link org.springframework.security.access.AccessDeniedException} (HTTP 403)
 * khi không đủ quyền.
 */
public interface ProjectAccessService {

    /** User id (Keycloak sub) hiện tại; ném UnauthorizedException nếu chưa đăng nhập. */
    UUID currentUserId();

    /** Người dùng hiện tại có realm role ADMIN hay không. */
    boolean isGlobalAdmin();

    /** Người dùng hiện tại có quyền {@code key} trong dự án hay không (Global ADMIN luôn true). */
    boolean hasProjectPermission(UUID projectId, PermissionKey key);

    void assertProjectPermission(UUID projectId, PermissionKey key);

    /** Kiểm tra quyền trên dự án chứa work item. */
    void assertTaskPermission(UUID workItemId, PermissionKey key);

    /** Chỉ Global ADMIN mới được đi tiếp. */
    void assertGlobalAdmin();
}
