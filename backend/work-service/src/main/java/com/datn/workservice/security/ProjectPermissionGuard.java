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
 *
 * <p>Vì fail-closed, các endpoint KHÔNG mang projectId trên đường dẫn (Board, Sprint,
 * Báo cáo của Giai đoạn 6) bắt buộc phải khai báo {@code source}/{@code param} trên
 * annotation để guard truy ngược ra projectId — xem bước 0 trong {@link #resolveProjectId}.
 * Thiếu bước đó thì toàn bộ các endpoint này bị từ chối vô điều kiện.
 *
 * <p>Cố ý KHÔNG bọc try/catch quanh thân method: ResourceNotFoundException do
 * {@link ProjectScopeResolver} ném khi board/sprint không tồn tại phải đi thẳng tới
 * GlobalExceptionHandler để thành 404, không bị biến thành 403 hay 500.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ProjectPermissionGuard {

    private static final Set<String> TASK_ID_PARAM_NAMES = Set.of("id", "itemid", "workitemid", "taskid");

    private final ProjectMemberService projectMemberService;
    private final ProjectScopeResolver projectScopeResolver;
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
            UUID projectId = resolveProjectId(joinPoint, method, requireProjectPerm);
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

    private UUID resolveProjectId(JoinPoint joinPoint, Method method, RequireProjectPermission requirePerm) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        Annotation[][] paramAnnotations = method.getParameterAnnotations();

        if (args == null || args.length == 0) {
            return null;
        }

        // 0. Endpoint định danh theo tài nguyên con (board/sprint/work item):
        //    lấy ID theo tên khai báo rồi truy ngược ra projectId.
        if (requirePerm.source() != ProjectIdSource.PROJECT) {
            UUID rawId = findUuidArg(parameterNames, args, paramAnnotations, requirePerm.param());
            return rawId == null ? null : projectScopeResolver.resolveProjectId(requirePerm.source(), rawId);
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
        //    Cả hai nhánh đều đã bỏ fallback "UUID đầu tiên": với /sprints/{sprintId}
        //    nó coi sprintId là projectId và từ chối mọi endpoint sprint/board.
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

    /**
     * Tìm tham số UUID mang ID tài nguyên: ưu tiên @PathVariable đúng tên, rồi tên tham số,
     * cuối cùng mới tới tham số UUID duy nhất của method.
     */
    private UUID findUuidArg(String[] parameterNames, Object[] args,
                             Annotation[][] paramAnnotations, String preferredName) {
        if (preferredName != null && !preferredName.isBlank()) {
            for (int i = 0; i < args.length; i++) {
                if (!(args[i] instanceof UUID uuid)) {
                    continue;
                }
                if (i < paramAnnotations.length) {
                    for (Annotation ann : paramAnnotations[i]) {
                        if (ann instanceof PathVariable pv) {
                            String pvName = pv.value().isEmpty() ? pv.name() : pv.value();
                            if (preferredName.equalsIgnoreCase(pvName)) {
                                return uuid;
                            }
                        }
                    }
                }
                if (parameterNames != null && i < parameterNames.length
                        && preferredName.equalsIgnoreCase(parameterNames[i])) {
                    return uuid;
                }
            }
        }

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
