package com.datn.workservice.security;

import java.lang.annotation.*;

/**
 * Annotation AOP kiểm tra quyền hạn và vai trò người dùng trong phạm vi một Workspace
 * ngay từ tầng Controller của work-service, thay vì gọi thủ công trong thân method.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireWorkspaceAccess {

    /**
     * Bậc vai trò tối thiểu trong workspace ("OWNER", "ADMIN", "MEMBER", "GUEST").
     * Mặc định là "MEMBER" (cho phép tạo team/project).
     */
    String value() default "MEMBER";

    /**
     * Tên tham số, query param hoặc path variable chứa workspaceId.
     * Nếu để trống, guard sẽ tự động nhận diện từ @RequestParam, @PathVariable hoặc DTO body.
     */
    String param() default "";

    /**
     * Nếu đặt là false, khi không truyền workspaceId (null hoặc optional), guard sẽ bỏ qua kiểm tra
     * thay vì ném lỗi ForbiddenException. Mặc định là true (bắt buộc).
     */
    boolean required() default true;
}
