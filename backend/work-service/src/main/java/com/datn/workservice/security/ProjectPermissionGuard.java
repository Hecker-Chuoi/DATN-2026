package com.datn.workservice.security;

import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.service.ProjectMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ProjectPermissionGuard {

    private final ProjectMemberService projectMemberService;

    @Before("@annotation(com.datn.workservice.security.RequireProjectPermission) || @within(com.datn.workservice.security.RequireProjectPermission)")
    public void checkPermission(JoinPoint joinPoint) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();

            RequireProjectPermission requirePerm = AnnotationUtils.findAnnotation(method, RequireProjectPermission.class);
            if (requirePerm == null) {
                requirePerm = AnnotationUtils.findAnnotation(joinPoint.getTarget().getClass(), RequireProjectPermission.class);
            }
            if (requirePerm == null) {
                return;
            }

            Optional<UUID> currentUserIdOpt = SecurityUtils.getCurrentUserIdOptional();
            if (currentUserIdOpt.isEmpty()) {
                log.warn("Access denied: Unauthenticated user accessing method {}", signature.toShortString());
                throw new AccessDeniedException("Yêu cầu xác thực để thực hiện thao tác này");
            }

            UUID currentUserId = currentUserIdOpt.get();
            UUID projectId = resolveProjectId(joinPoint, method);

            if (projectId == null) {
                log.debug("Cannot extract projectId for permission check on method: {}", signature.toShortString());
                return;
            }

            // System Admin (ROLE_ADMIN) has superuser access to all project operations
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getAuthorities() != null && authentication.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_ADMIN".equalsIgnoreCase(a.getAuthority()) || "ADMIN".equalsIgnoreCase(a.getAuthority()))) {
                log.debug("System admin bypassing project permission check for user {}", currentUserId);
                return;
            }

            PermissionKey requiredPermission = requirePerm.value();
            log.debug("Checking permission {} for user {} on project {}", requiredPermission, currentUserId, projectId);
            boolean hasPermission = projectMemberService.hasPermission(projectId, currentUserId, requiredPermission);

            if (!hasPermission) {
                log.warn("Access denied for user {} requesting permission {} on project {}",
                        currentUserId, requiredPermission, projectId);
                throw new AccessDeniedException(
                        "Bạn không có quyền thực hiện thao tác này (" + requiredPermission.name() + ") trong dự án"
                );
            }
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error in ProjectPermissionGuard: {}", e.getMessage(), e);
            throw new RuntimeException("Lỗi hệ thống khi kiểm tra quyền", e);
        }
    }

    private UUID resolveProjectId(JoinPoint joinPoint, Method method) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        Annotation[][] paramAnnotations = method.getParameterAnnotations();

        if (args == null || args.length == 0) {
            return null;
        }

        // 1. Inspect @PathVariable annotations
        for (int i = 0; i < args.length; i++) {
            if (i < paramAnnotations.length && args[i] instanceof UUID uuid) {
                for (Annotation ann : paramAnnotations[i]) {
                    if (ann instanceof PathVariable pv) {
                        String pvName = pv.value().isEmpty() ? pv.name() : pv.value();
                        if ("projectId".equalsIgnoreCase(pvName) || pvName.isEmpty()) {
                            return uuid;
                        }
                    }
                }
            }
        }

        // 2. Check parameter name
        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length; i++) {
                String paramName = parameterNames[i];
                if (i < args.length && args[i] instanceof UUID uuid) {
                    if ("projectId".equalsIgnoreCase(paramName) || paramName.toLowerCase().contains("project")) {
                        return uuid;
                    }
                }
            }
        }

        // 3. Fallback: First UUID argument in method
        for (Object arg : args) {
            if (arg instanceof UUID uuid) {
                return uuid;
            }
        }

        // 4. Inspect DTO argument with getProjectId()
        for (Object arg : args) {
            if (arg != null) {
                try {
                    Method getProjectIdMethod = arg.getClass().getMethod("getProjectId");
                    Object result = getProjectIdMethod.invoke(arg);
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
