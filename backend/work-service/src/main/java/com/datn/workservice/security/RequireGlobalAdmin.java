package com.datn.workservice.security;

import java.lang.annotation.*;

/**
 * Annotation AOP yêu cầu quyền Quản trị viên toàn hệ thống (Global Admin realm role từ Keycloak).
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireGlobalAdmin {
}
