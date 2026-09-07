package com.datn.workservice.security;

import com.datn.workservice.enums.PermissionKey;

import java.lang.annotation.*;

/**
 * Annotation protecting task/work-item scoped API endpoints with granular RBAC permissions.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireTaskPermission {

    /**
     * The required task-level permission key (e.g. PROJECT_VIEW, ISSUE_CREATE, ISSUE_EDIT, ISSUE_DELETE, ISSUE_TRANSITION, COMMENT_CREATE).
     */
    PermissionKey value();
}
