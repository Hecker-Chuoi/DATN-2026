package com.datn.workspaceservice.security;

import com.datn.workspaceservice.enums.WorkspaceRole;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Yêu cầu người gọi là thành viên workspace với bậc tối thiểu {@link #value()}.
 * <p>
 * {@code workspaceId} được lấy từ {@code @PathVariable} có TÊN khớp
 * {@link #pathVariable()}. Cố tình yêu cầu tên tường minh - xem
 * {@link WorkspaceRoleGuard} để biết vì sao không dùng heuristic.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireWorkspaceRole {

    /** Bậc tối thiểu. Ví dụ ADMIN nghĩa là OWNER hoặc ADMIN đều qua. */
    WorkspaceRole value();

    /** Tên path variable chứa workspace id. */
    String pathVariable() default "workspaceId";
}
