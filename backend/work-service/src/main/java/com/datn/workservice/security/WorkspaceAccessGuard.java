package com.datn.workservice.security;

import com.datn.common.exception.ForbiddenException;
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
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

/**
 * AOP Guard kiểm tra quyền truy cập Workspace (@RequireWorkspaceAccess) từ phía work-service.
 * <p>
 * Thay thế cho các lệnh gọi thủ công `workspaceAccessChecker.requireAtLeast(...)` trong thân Controller.
 * Áp dụng triết lý FAIL-CLOSED: không trích xuất được workspaceId khi required=true sẽ lập tức từ chối.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class WorkspaceAccessGuard {

    private final WorkspaceAccessChecker workspaceAccessChecker;

    @Before("@annotation(com.datn.workservice.security.RequireWorkspaceAccess) " +
            "|| @within(com.datn.workservice.security.RequireWorkspaceAccess)")
    public void checkWorkspaceAccess(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        RequireWorkspaceAccess requireAccess = AnnotationUtils.findAnnotation(method, RequireWorkspaceAccess.class);
        if (requireAccess == null && joinPoint.getTarget() != null) {
            requireAccess = AnnotationUtils.findAnnotation(joinPoint.getTarget().getClass(), RequireWorkspaceAccess.class);
        }
        if (requireAccess == null) {
            return;
        }

        Optional<UUID> currentUserIdOpt = SecurityUtils.getCurrentUserIdOptional();
        if (currentUserIdOpt.isEmpty()) {
            log.warn("Access denied: Unauthenticated user accessing method {}", signature.toShortString());
            throw new AccessDeniedException("Yêu cầu xác thực để thực hiện thao tác này");
        }

        if (SecurityUtils.isGlobalAdmin()) {
            log.debug("Access granted for Global ADMIN on method {}", signature.toShortString());
            return;
        }

        UUID workspaceId = resolveWorkspaceId(joinPoint, method, requireAccess.param());
        if (workspaceId == null) {
            if (!requireAccess.required()) {
                return; // Optional workspace parameter, bỏ qua kiểm tra
            }
            log.warn("Access denied: Cannot resolve workspaceId for method {}", signature.toShortString());
            throw new ForbiddenException("Không xác định được workspace để kiểm tra quyền");
        }

        UUID currentUserId = currentUserIdOpt.get();
        String minRole = requireAccess.value();

        // Ủy quyền cho WorkspaceAccessChecker kiểm tra thang bậc vai trò (OWNER > ADMIN > MEMBER > GUEST)
        workspaceAccessChecker.requireAtLeast(workspaceId, currentUserId, minRole);
    }

    private UUID resolveWorkspaceId(JoinPoint joinPoint, Method method, String preferredParamName) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        Annotation[][] paramAnnotations = method.getParameterAnnotations();

        if (args == null || args.length == 0) {
            return null;
        }

        // 1. Khớp theo @PathVariable hoặc @RequestParam chỉ định trong preferredParamName
        if (preferredParamName != null && !preferredParamName.isBlank()) {
            for (int i = 0; i < args.length; i++) {
                if (!(args[i] instanceof UUID uuid)) continue;

                if (i < paramAnnotations.length) {
                    for (Annotation ann : paramAnnotations[i]) {
                        if (ann instanceof PathVariable pv && preferredParamName.equalsIgnoreCase(pv.value().isEmpty() ? pv.name() : pv.value())) {
                            return uuid;
                        }
                        if (ann instanceof RequestParam rp && preferredParamName.equalsIgnoreCase(rp.value().isEmpty() ? rp.name() : rp.value())) {
                            return uuid;
                        }
                    }
                }
                if (parameterNames != null && i < parameterNames.length
                        && preferredParamName.equalsIgnoreCase(parameterNames[i])) {
                    return uuid;
                }
            }
        }

        // 2. Khớp theo @PathVariable hoặc @RequestParam có tên "workspaceId"
        for (int i = 0; i < args.length; i++) {
            if (i < paramAnnotations.length && args[i] instanceof UUID uuid) {
                for (Annotation ann : paramAnnotations[i]) {
                    if (ann instanceof PathVariable pv) {
                        String pvName = pv.value().isEmpty() ? pv.name() : pv.value();
                        if ("workspaceId".equalsIgnoreCase(pvName)) {
                            return uuid;
                        }
                    }
                    if (ann instanceof RequestParam rp) {
                        String rpName = rp.value().isEmpty() ? rp.name() : rp.value();
                        if ("workspaceId".equalsIgnoreCase(rpName)) {
                            return uuid;
                        }
                    }
                }
            }
        }

        // 3. Khớp theo tên tham số chứa "workspace"
        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length && i < args.length; i++) {
                if (args[i] instanceof UUID uuid && parameterNames[i].toLowerCase().contains("workspace")) {
                    return uuid;
                }
            }
        }

        // 4. Khớp qua DTO Body có hàm getWorkspaceId() (VD: CreateProjectRequest)
        for (Object arg : args) {
            if (arg != null && !(arg instanceof UUID)) {
                try {
                    Method getWorkspaceIdMethod = arg.getClass().getMethod("getWorkspaceId");
                    Object result = getWorkspaceIdMethod.invoke(arg);
                    if (result instanceof UUID uuid) {
                        return uuid;
                    }
                } catch (Exception ignored) {
                }
            }
        }

        return null;
    }
}
