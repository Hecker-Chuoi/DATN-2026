package com.datn.workservice.security;

import com.datn.workservice.enums.TeamRole;

import java.lang.annotation.*;

/**
 * Annotation AOP kiểm tra quyền hạn và vai trò người dùng trong phạm vi một Team.
 * Thay thế cho @PreAuthorize("@teamSecurity.isLeader(#teamId)") để đồng nhất phong cách AOP.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireTeamRole {

    /**
     * Vai trò yêu cầu trong Team.
     * Mặc định là MEMBER (bất kỳ thành viên nào trong nhóm).
     * Đặt LEADER nếu yêu cầu quyền Trưởng nhóm (Leader/Owner).
     */
    TeamRole value() default TeamRole.MEMBER;

    /**
     * Tên tham số hoặc path variable chứa teamId. Nếu để trống, guard sẽ tự động nhận diện.
     */
    String param() default "";
}
