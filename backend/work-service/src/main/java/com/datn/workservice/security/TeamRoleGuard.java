package com.datn.workservice.security;

import com.datn.workservice.enums.TeamRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

/**
 * AOP Guard đánh chặn các endpoint yêu cầu quyền trên Team (@RequireTeamRole)
 * và quyền Quản trị viên toàn hệ thống (@RequireGlobalAdmin).
 * <p>
 * Áp dụng triết lý FAIL-CLOSED: không trích xuất được teamId hoặc người dùng thiếu quyền
 * sẽ lập tức ném AccessDeniedException (403 Forbidden).
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class TeamRoleGuard {

    private final TeamSecurity teamSecurity;

    @Before("@annotation(com.datn.workservice.security.RequireTeamRole) " +
            "|| @within(com.datn.workservice.security.RequireTeamRole) " +
            "|| @annotation(com.datn.workservice.security.RequireGlobalAdmin) " +
            "|| @within(com.datn.workservice.security.RequireGlobalAdmin)")
    public void checkTeamRole(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 1. Kiểm tra quyền Global Admin nếu có @RequireGlobalAdmin
        RequireGlobalAdmin globalAdminAnn = AnnotationUtils.findAnnotation(method, RequireGlobalAdmin.class);
        if (globalAdminAnn == null && joinPoint.getTarget() != null) {
            globalAdminAnn = AnnotationUtils.findAnnotation(joinPoint.getTarget().getClass(), RequireGlobalAdmin.class);
        }
        if (globalAdminAnn != null) {
            if (!SecurityUtils.isGlobalAdmin()) {
                log.warn("Access denied: User {} does not have Global Admin role for method {}",
                        SecurityUtils.getCurrentUserIdOptional().orElse(null), signature.toShortString());
                throw new AccessDeniedException("Yêu cầu quyền Quản trị viên hệ thống (Global Admin)");
            }
            return;
        }

        // 2. Kiểm tra @RequireTeamRole
        RequireTeamRole requireTeamRole = AnnotationUtils.findAnnotation(method, RequireTeamRole.class);
        if (requireTeamRole == null && joinPoint.getTarget() != null) {
            requireTeamRole = AnnotationUtils.findAnnotation(joinPoint.getTarget().getClass(), RequireTeamRole.class);
        }
        if (requireTeamRole == null) {
            return;
        }

        Optional<UUID> currentUserIdOpt = SecurityUtils.getCurrentUserIdOptional();
        if (currentUserIdOpt.isEmpty()) {
            log.warn("Access denied: Unauthenticated user accessing {}", signature.toShortString());
            throw new AccessDeniedException("Yêu cầu xác thực để thực hiện thao tác này");
        }

        if (SecurityUtils.isGlobalAdmin()) {
            log.debug("Access granted for Global ADMIN on method {}", signature.toShortString());
            return;
        }

        UUID teamId = resolveTeamId(joinPoint, method, requireTeamRole.param());
        if (teamId == null) {
            log.warn("Access denied: Cannot resolve teamId for method {}", signature.toShortString());
            throw new AccessDeniedException("Không xác định được nhóm để kiểm tra quyền");
        }

        TeamRole requiredRole = requireTeamRole.value();
        if (requiredRole == TeamRole.LEADER) {
            if (!teamSecurity.isLeader(teamId)) {
                log.warn("Access denied: User {} is not LEADER of team {}", currentUserIdOpt.get(), teamId);
                throw new AccessDeniedException("Bạn không có quyền Trưởng nhóm (Leader) trên nhóm này");
            }
        } else {
            if (!teamSecurity.isMember(teamId)) {
                log.warn("Access denied: User {} is not MEMBER of team {}", currentUserIdOpt.get(), teamId);
                throw new AccessDeniedException("Bạn không phải thành viên của nhóm này");
            }
        }
    }

    private UUID resolveTeamId(JoinPoint joinPoint, Method method, String preferredParamName) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        Annotation[][] paramAnnotations = method.getParameterAnnotations();

        if (args == null || args.length == 0) {
            return null;
        }

        // 1. Khớp theo @PathVariable hoặc tham số chỉ định trong preferredParamName
        if (preferredParamName != null && !preferredParamName.isBlank()) {
            for (int i = 0; i < args.length; i++) {
                if (!(args[i] instanceof UUID uuid)) continue;

                if (i < paramAnnotations.length) {
                    for (Annotation ann : paramAnnotations[i]) {
                        if (ann instanceof PathVariable pv) {
                            String pvName = pv.value().isEmpty() ? pv.name() : pv.value();
                            if (preferredParamName.equalsIgnoreCase(pvName)) {
                                return uuid;
                            }
                        }
                    }
                }
                if (parameterNames != null && i < parameterNames.length
                        && preferredParamName.equalsIgnoreCase(parameterNames[i])) {
                    return uuid;
                }
            }
        }

        // 2. Khớp theo @PathVariable("teamId") hoặc "id"
        for (int i = 0; i < args.length; i++) {
            if (i < paramAnnotations.length && args[i] instanceof UUID uuid) {
                for (Annotation ann : paramAnnotations[i]) {
                    if (ann instanceof PathVariable pv) {
                        String pvName = pv.value().isEmpty() ? pv.name() : pv.value();
                        if ("teamId".equalsIgnoreCase(pvName) || "id".equalsIgnoreCase(pvName)) {
                            return uuid;
                        }
                    }
                }
            }
        }

        // 3. Khớp theo tên tham số chứa "team"
        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length && i < args.length; i++) {
                if (args[i] instanceof UUID uuid && parameterNames[i].toLowerCase().contains("team")) {
                    return uuid;
                }
            }
        }

        // 4. Khớp qua DTO có hàm getTeamId()
        for (Object arg : args) {
            if (arg != null && !(arg instanceof UUID)) {
                try {
                    Method getTeamIdMethod = arg.getClass().getMethod("getTeamId");
                    Object result = getTeamIdMethod.invoke(arg);
                    if (result instanceof UUID uuid) {
                        return uuid;
                    }
                } catch (Exception ignored) {
                }
            }
        }

        // 5. Nếu method chỉ có đúng 1 tham số UUID duy nhất
        UUID sole = null;
        int count = 0;
        for (Object arg : args) {
            if (arg instanceof UUID uuid) {
                sole = uuid;
                count++;
            }
        }
        return count == 1 ? sole : null;
    }
}
