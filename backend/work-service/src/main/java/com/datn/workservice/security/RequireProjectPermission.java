package com.datn.workservice.security;

import com.datn.workservice.enums.PermissionKey;

import java.lang.annotation.*;

/**
 * Annotation protecting project-scoped API endpoints with granular RBAC permissions.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireProjectPermission {

    /**
     * The required project-level permission key (e.g. PROJECT_ADMIN, ISSUE_CREATE, SPRINT_MANAGE).
     */
    PermissionKey value();

    /**
     * Loại tài nguyên mà path variable đang mang ID. Mặc định {@link ProjectIdSource#PROJECT}
     * giữ nguyên hành vi cũ. Dùng BOARD / SPRINT / WORK_ITEM cho các endpoint định danh
     * theo tài nguyên con (ví dụ {@code /sprints/{sprintId}/start}).
     */
    ProjectIdSource source() default ProjectIdSource.PROJECT;

    /**
     * Tên path variable chứa ID cần truy ngược. Để trống thì guard tự dò theo tên
     * tham số, rồi tới tham số UUID duy nhất của method.
     */
    String param() default "";
}
