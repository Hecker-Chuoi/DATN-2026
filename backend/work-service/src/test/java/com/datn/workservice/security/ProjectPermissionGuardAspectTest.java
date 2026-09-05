package com.datn.workservice.security;

import com.datn.workservice.enums.PermissionKey;
import com.datn.workservice.service.ProjectMemberService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectPermissionGuardAspectTest {

    @Mock
    private ProjectMemberService projectMemberService;

    @Mock
    private JoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private ProjectPermissionGuard projectPermissionGuard;

    private UUID currentUserId;
    private UUID projectId;

    // Helper dummy method with annotations for testing
    @RequireProjectPermission(PermissionKey.PROJECT_ADMIN)
    public void dummyAdminMethod(@PathVariable("projectId") UUID projectId) {}

    @RequireProjectPermission(PermissionKey.ISSUE_CREATE)
    public void dummyCreateMethod(UUID projectId) {}

    public void dummyUnprotectedMethod() {}

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockAuthentication() {
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .claim("sub", currentUserId.toString())
                .build();

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(jwt);
    }

    @Test
    @DisplayName("checkPermission: Passes when user has required permission")
    void testCheckPermission_Authorized_Success() throws NoSuchMethodException {
        mockAuthentication();
        Method method = getClass().getMethod("dummyAdminMethod", UUID.class);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[]{projectId});
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"projectId"});
        when(projectMemberService.hasPermission(projectId, currentUserId, PermissionKey.PROJECT_ADMIN)).thenReturn(true);

        assertThatCode(() -> projectPermissionGuard.checkPermission(joinPoint))
                .doesNotThrowAnyException();

        verify(projectMemberService).hasPermission(projectId, currentUserId, PermissionKey.PROJECT_ADMIN);
    }

    @Test
    @DisplayName("checkPermission: Throws AccessDeniedException when user lacks permission")
    void testCheckPermission_Unauthorized_ThrowsAccessDenied() throws NoSuchMethodException {
        mockAuthentication();
        Method method = getClass().getMethod("dummyAdminMethod", UUID.class);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[]{projectId});
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"projectId"});
        when(projectMemberService.hasPermission(projectId, currentUserId, PermissionKey.PROJECT_ADMIN)).thenReturn(false);

        assertThatThrownBy(() -> projectPermissionGuard.checkPermission(joinPoint))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Bạn không có quyền thực hiện thao tác này (PROJECT_ADMIN)");
    }

    @Test
    @DisplayName("checkPermission: Throws AccessDeniedException when unauthenticated")
    void testCheckPermission_Unauthenticated_ThrowsAccessDenied() throws NoSuchMethodException {
        when(securityContext.getAuthentication()).thenReturn(null);
        Method method = getClass().getMethod("dummyAdminMethod", UUID.class);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(methodSignature.toShortString()).thenReturn("dummyAdminMethod()");

        assertThatThrownBy(() -> projectPermissionGuard.checkPermission(joinPoint))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Yêu cầu xác thực");
    }

    @Test
    @DisplayName("checkPermission: Ignores methods without @RequireProjectPermission")
    void testCheckPermission_NoAnnotation_Bypassed() throws NoSuchMethodException {
        Method method = getClass().getMethod("dummyUnprotectedMethod");

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getTarget()).thenReturn(this);

        assertThatCode(() -> projectPermissionGuard.checkPermission(joinPoint))
                .doesNotThrowAnyException();

        verify(projectMemberService, never()).hasPermission(any(), any(), any());
    }
}
