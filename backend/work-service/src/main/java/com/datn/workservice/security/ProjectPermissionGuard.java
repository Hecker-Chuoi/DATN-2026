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
    private final ProjectSecurity projectSecurity;

    @Before("@annotation(com.datn.workservice.security.RequireProjectPermission) || @within(com.datn.workservice.security.RequireProjectPermission) || @annotation(com.datn.workservice.security.RequireTaskPermission) || @within(com.datn.workservice.security.RequireTaskPermission)")
    public void checkPermission(JoinPoint joinPoint) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();

            RequireProjectPermission requireProjectPerm = AnnotationUtils.findAnnotation(method, RequireProjectPermission.class);
            if (requireProjectPerm == null && joinPoint.getTarget() != null) {
                requireProjectPerm = AnnotationUtils.findAnnotation(joinPoint.getTarget().getClass(), RequireProjectPermission.class);
            }

            RequireTaskPermission requireTaskPerm = AnnotationUtils.findAnnotation(method, RequireTaskPermission.class);
            if (requireTaskPerm == null && joinPoint.getTarget() != null) {
                requireTaskPerm = AnnotationUtils.findAnnotation(joinPoint.getTarget().getClass(), RequireTaskPermission.class);
            }

            if (requireProjectPerm == null && requireTaskPerm == null) {
                return;
            }

            Optional<UUID> currentUserIdOpt = SecurityUtils.getCurrentUserIdOptional();
            if (currentUserIdOpt.isEmpty()) {
                log.warn("Access denied: Unauthenticated user accessing method {}", signature.toShortString());
                throw new AccessDeniedException("Yêu cầu xác thực để thực hiện thao tác này");
            }

            UUID currentUserId = currentUserIdOpt.get();
            if (SecurityUtils.isGlobalAdmin()) {
                log.info("Access granted for Global ADMIN user {} on method {}", currentUserId, signature.toShortString());
                return;
            }

            if (requireProjectPerm != null) {
                UUID projectId = resolveProjectId(joinPoint, method);
                if (projectId != null) {
                    PermissionKey requiredPermission = requireProjectPerm.value();
                    log.debug("Checking project permission {} for user {} on project {}", requiredPermission, currentUserId, projectId);
                    boolean hasPermission = projectMemberService.hasPermission(projectId, currentUserId, requiredPermission);

                    if (!hasPermission) {
                        log.warn("Access denied for user {} requesting permission {} on project {}",
                                currentUserId, requiredPermission, projectId);
                        throw new AccessDeniedException(
                                "Bạn không có quyền thực hiện thao tác này (" + requiredPermission.name() + ") trong dự án"
                        );
                    }
                }
            }

            if (requireTaskPerm != null) {
                UUID taskId = resolveTaskId(joinPoint, method);
                if (taskId != null) {
                    PermissionKey requiredPermission = requireTaskPerm.value();
                    log.debug("Checking task permission {} for user {} on task {}", requiredPermission, currentUserId, taskId);
                    boolean hasPermission = projectSecurity.hasTaskPermission(taskId, requiredPermission.name());

                    if (!hasPermission) {
                        log.warn("Access denied for user {} requesting permission {} on task {}",
                                currentUserId, requiredPermission, taskId);
                        throw new AccessDeniedException(
                                "Bạn không có quyền thực hiện thao tác này (" + requiredPermission.name() + ") trên công việc"
                        );
                    }
                }
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

    private UUID resolveTaskId(JoinPoint joinPoint, Method method) {
        Object[] args = joinPoint.getArgs();
        Annotation[][] paramAnnotations = method.getParameterAnnotations();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();

        if (args == null || args.length == 0) {
            return null;
        }

        // 1. Inspect @PathVariable annotations for task/item ID
        for (int i = 0; i < args.length; i++) {
            if (i < paramAnnotations.length && args[i] instanceof UUID uuid) {
                for (Annotation ann : paramAnnotations[i]) {
                    if (ann instanceof PathVariable pv) {
                        String pvName = pv.value().isEmpty() ? pv.name() : pv.value();
                        if ("id".equalsIgnoreCase(pvName) || "itemId".equalsIgnoreCase(pvName)
                                || "workItemId".equalsIgnoreCase(pvName) || "taskId".equalsIgnoreCase(pvName)) {
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
                    if ("id".equalsIgnoreCase(paramName) || "itemId".equalsIgnoreCase(paramName)
                            || "workItemId".equalsIgnoreCase(paramName) || "taskId".equalsIgnoreCase(paramName)) {
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

        return null;
    }
}
