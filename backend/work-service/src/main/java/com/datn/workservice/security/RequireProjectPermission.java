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
}
