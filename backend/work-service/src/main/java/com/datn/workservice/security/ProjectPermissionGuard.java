package com.datn.workservice.security;

import com.datn.workservice.entity.WorkItem;
import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.repository.WorkItemRepository;
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
import java.util.Set;
import java.util.UUID;

/**
 * AOP guard cho @RequireProjectPermission / @RequireTaskPermission.
 *
 * Nguyên tắc FAIL-CLOSED: nếu không xác định được projectId/taskId từ tham số
 * thì từ chối (403) thay vì bỏ qua kiểm tra.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ProjectPermissionGuard {

    private static final Set<String> TASK_ID_PARAM_NAMES = Set.of("id", "itemid", "workitemid", "taskid");

    private final ProjectMemberService projectMemberService;
    private final WorkItemRepository workItemRepository;

    @Before("@annotation(com.datn.workservice.security.RequireProjectPermission) || @within(com.datn.workservice.security.RequireProjectPermission) || @annotation(com.datn.workservice.security.RequireTaskPermission) || @within(com.datn.workservice.security.RequireTaskPermission)")
    public void checkPermission(JoinPoint joinPoint) {
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
            log.debug("Access granted for Global ADMIN user {} on method {}", currentUserId, signature.toShortString());
            return;
        }

        if (requireProjectPerm != null) {
            UUID projectId = resolveProjectId(joinPoint, method);
            if (projectId == null) {
                log.warn("Access denied: cannot resolve projectId for method {}", signature.toShortString());
                throw new AccessDeniedException("Không xác định được dự án để kiểm tra quyền");
            }
            check(projectId, currentUserId, requireProjectPerm.value(), "trên dự án");
        }

        if (requireTaskPerm != null) {
            UUID taskId = resolveTaskId(joinPoint, method);
            if (taskId == null) {
                log.warn("Access denied: cannot resolve work item id for method {}", signature.toShortString());
                throw new AccessDeniedException("Không xác định được công việc để kiểm tra quyền");
            }
            UUID projectId = workItemRepository.findById(taskId)
                    .map(WorkItem::getProjectId)
                    .orElseThrow(() -> new AccessDeniedException("Không tìm thấy công việc hoặc bạn không có quyền truy cập"));
            check(projectId, currentUserId, requireTaskPerm.value(), "trên công việc");
        }
    }

    private void check(UUID projectId, UUID userId, PermissionKey required, String scopeLabel) {
        boolean hasPermission = projectMemberService.hasPermission(projectId, userId, required);
        if (!hasPermission) {
            log.warn("Access denied for user {} requesting permission {} on project {}", userId, required, projectId);
            throw new AccessDeniedException(
                    "Bạn không có quyền thực hiện thao tác này (" + required.name() + ") " + scopeLabel);
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

        // 1. @PathVariable("projectId")
        for (int i = 0; i < args.length; i++) {
            if (i < paramAnnotations.length && args[i] instanceof UUID uuid) {
                for (Annotation ann : paramAnnotations[i]) {
                    if (ann instanceof PathVariable pv) {
                        String pvName = pv.value().isEmpty() ? pv.name() : pv.value();
                        if ("projectId".equalsIgnoreCase(pvName)) {
                            return uuid;
                        }
                    }
                }
            }
        }

        // 2. Tên tham số chứa "project"
        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length && i < args.length; i++) {
                if (args[i] instanceof UUID uuid && parameterNames[i].toLowerCase().contains("project")) {
                    return uuid;
                }
            }
        }

        // 3. DTO có getProjectId()
        for (Object arg : args) {
            if (arg != null && !(arg instanceof UUID)) {
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

        // 1. @PathVariable id/itemId/workItemId/taskId
        for (int i = 0; i < args.length; i++) {
            if (i < paramAnnotations.length && args[i] instanceof UUID uuid) {
                for (Annotation ann : paramAnnotations[i]) {
                    if (ann instanceof PathVariable pv) {
                        String pvName = pv.value().isEmpty() ? pv.name() : pv.value();
                        if (TASK_ID_PARAM_NAMES.contains(pvName.toLowerCase())) {
                            return uuid;
                        }
                    }
                }
            }
        }

        // 2. Tên tham số
        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length && i < args.length; i++) {
                if (args[i] instanceof UUID uuid && TASK_ID_PARAM_NAMES.contains(parameterNames[i].toLowerCase())) {
                    return uuid;
                }
            }
        }

        return null;
    }
}
