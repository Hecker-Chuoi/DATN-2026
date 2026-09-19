package com.datn.workspaceservice.security;

import com.datn.common.exception.ForbiddenException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workspaceservice.enums.WorkspaceRole;
import com.datn.workspaceservice.service.WorkspaceMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Cổng phân quyền thô cho các endpoint workspace.
 * <p>
 * Dựng theo ProjectPermissionGuard của work-service nhưng có HAI điểm khác biệt
 * CỐ Ý, cả hai đều là sửa lỗi:
 * <ol>
 *   <li><b>Fail CLOSED.</b> ProjectPermissionGuard khi không trích được id thì
 *       chỉ log debug rồi return - tức là gõ sai tên path variable sẽ âm thầm
 *       VÔ HIỆU HÓA việc kiểm tra quyền. Một ranh giới tenant không được phép
 *       fail open, nên ở đây thiếu id là throw.</li>
 *   <li><b>Không heuristic.</b> ProjectPermissionGuard có chiến lược dự phòng
 *       lấy UUID đầu tiên trong danh sách tham số, nên trên một method nhận
 *       workItemId nó sẽ kiểm tra quyền trên sai thực thể. Ở đây chỉ khớp
 *       PathVariable theo đúng tên đã khai báo.</li>
 * </ol>
 * Guard chỉ là cổng thô; service vẫn tự kiểm tra lại và vẫn nhận currentUserId
 * qua tham số, nên unit test không cần SecurityContext.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class WorkspaceRoleGuard {

    private final WorkspaceMemberService memberService;
    private final CurrentUserResolver currentUserResolver;

    @Before("@annotation(com.datn.workspaceservice.security.RequireWorkspaceRole) "
            + "|| @within(com.datn.workspaceservice.security.RequireWorkspaceRole)")
    public void checkRole(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        RequireWorkspaceRole required = AnnotationUtils.findAnnotation(method, RequireWorkspaceRole.class);
        if (required == null) {
            required = AnnotationUtils.findAnnotation(joinPoint.getTarget().getClass(), RequireWorkspaceRole.class);
        }
        if (required == null) {
            return;
        }

        UUID workspaceId = resolveWorkspaceId(joinPoint, method, required.pathVariable());
        if (workspaceId == null) {
            // FAIL CLOSED: không xác định được phạm vi thì không cho qua.
            log.error("Không trích được path variable [{}] cho {} - từ chối truy cập",
                    required.pathVariable(), signature.toShortString());
            throw new ForbiddenException("Không xác định được workspace để kiểm tra quyền");
        }

        UUID currentUserId = currentUserResolver.currentUserId();
        WorkspaceRole actual = memberService.findRole(workspaceId, currentUserId)
                // Không rò rỉ sự tồn tại: người ngoài nhận 404, không phải 403.
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy workspace: " + workspaceId));

        if (!actual.atLeast(required.value())) {
            throw new ForbiddenException(String.format(
                    "Bạn cần vai trò %s trở lên trong workspace này (vai trò hiện tại: %s)",
                    required.value(), actual));
        }
    }

    /** Chỉ khớp PathVariable theo đúng tên. Không có chiến lược dự phòng nào. */
    private UUID resolveWorkspaceId(JoinPoint joinPoint, Method method, String pathVariableName) {
        Annotation[][] paramAnnotations = method.getParameterAnnotations();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < paramAnnotations.length && i < args.length; i++) {
            for (Annotation annotation : paramAnnotations[i]) {
                if (!(annotation instanceof PathVariable pathVariable)) {
                    continue;
                }

                String declaredName = pathVariable.value();
                if (declaredName.isBlank()) {
                    declaredName = pathVariable.name();
                }
                if (declaredName.isBlank()) {
                    // PathVariable không ghi tên -> lấy tên tham số (pom bật -parameters)
                    declaredName = method.getParameters()[i].getName();
                }

                if (pathVariableName.equalsIgnoreCase(declaredName) && args[i] instanceof UUID id) {
                    return id;
                }
            }
        }
        return null;
    }
}
