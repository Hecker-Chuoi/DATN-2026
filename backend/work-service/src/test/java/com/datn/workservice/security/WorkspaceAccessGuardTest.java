package com.datn.workservice.security;

import com.datn.common.exception.ForbiddenException;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkspaceAccessGuardTest {

    @Mock
    private WorkspaceAccessChecker workspaceAccessChecker;

    @Mock
    private JoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private WorkspaceAccessGuard workspaceAccessGuard;

    private UUID currentUserId;
    private UUID workspaceId;

    // Helper dummy methods for reflection testing
    @RequireWorkspaceAccess("MEMBER")
    public void dummyMemberMethod(@PathVariable("workspaceId") UUID workspaceId) {}

    @RequireWorkspaceAccess(value = "GUEST", required = false)
    public void dummyOptionalWorkspaceMethod(@RequestParam(value = "workspaceId", required = false) UUID workspaceId) {}

    @RequireWorkspaceAccess("MEMBER")
    public void dummyDtoMethod(TestWorkspaceDto dto) {}

    @RequireWorkspaceAccess("ADMIN")
    public void dummyMissingParamMethod(String otherParam) {}

    public static class TestWorkspaceDto {
        private final UUID workspaceId;

        public TestWorkspaceDto(UUID workspaceId) {
            this.workspaceId = workspaceId;
        }

        public UUID getWorkspaceId() {
            return workspaceId;
        }
    }

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
        workspaceId = UUID.randomUUID();
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockAuthentication(boolean isGlobalAdmin) {
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .claim("sub", currentUserId.toString())
                .build();

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(jwt);

        if (isGlobalAdmin) {
            doReturn(Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")))
                    .when(authentication).getAuthorities();
        } else {
            doReturn(Collections.emptyList())
                    .when(authentication).getAuthorities();
        }
    }

    @Test
    @DisplayName("checkWorkspaceAccess: Thành công khi user có quyền trong workspace")
    void testCheckWorkspaceAccess_Success() throws NoSuchMethodException {
        mockAuthentication(false);
        Method method = getClass().getMethod("dummyMemberMethod", UUID.class);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[]{workspaceId});

        assertThatCode(() -> workspaceAccessGuard.checkWorkspaceAccess(joinPoint))
                .doesNotThrowAnyException();

        verify(workspaceAccessChecker).requireAtLeast(workspaceId, currentUserId, "MEMBER");
    }

    @Test
    @DisplayName("checkWorkspaceAccess: Global Admin bypass kiểm tra workspace")
    void testCheckWorkspaceAccess_GlobalAdmin_Bypasses() throws NoSuchMethodException {
        mockAuthentication(true);
        Method method = getClass().getMethod("dummyMemberMethod", UUID.class);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);

        assertThatCode(() -> workspaceAccessGuard.checkWorkspaceAccess(joinPoint))
                .doesNotThrowAnyException();

        verifyNoInteractions(workspaceAccessChecker);
    }

    @Test
    @DisplayName("checkWorkspaceAccess: Bắt exception khi WorkspaceAccessChecker từ chối quyền")
    void testCheckWorkspaceAccess_AccessDenied_Throws() throws NoSuchMethodException {
        mockAuthentication(false);
        Method method = getClass().getMethod("dummyMemberMethod", UUID.class);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[]{workspaceId});

        doThrow(new ForbiddenException("Bạn không có quyền truy cập Workspace này"))
                .when(workspaceAccessChecker).requireAtLeast(workspaceId, currentUserId, "MEMBER");

        assertThatThrownBy(() -> workspaceAccessGuard.checkWorkspaceAccess(joinPoint))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Bạn không có quyền");
    }

    @Test
    @DisplayName("checkWorkspaceAccess: Bỏ qua khi required = false và workspaceId là null")
    void testCheckWorkspaceAccess_OptionalWorkspace_Null_Allows() throws NoSuchMethodException {
        mockAuthentication(false);
        Method method = getClass().getMethod("dummyOptionalWorkspaceMethod", UUID.class);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[]{null});

        assertThatCode(() -> workspaceAccessGuard.checkWorkspaceAccess(joinPoint))
                .doesNotThrowAnyException();

        verifyNoInteractions(workspaceAccessChecker);
    }

    @Test
    @DisplayName("checkWorkspaceAccess: Tự động trích xuất workspaceId từ Request DTO")
    void testCheckWorkspaceAccess_ExtractFromDto_Success() throws NoSuchMethodException {
        mockAuthentication(false);
        Method method = getClass().getMethod("dummyDtoMethod", TestWorkspaceDto.class);
        TestWorkspaceDto dto = new TestWorkspaceDto(workspaceId);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[]{dto});

        assertThatCode(() -> workspaceAccessGuard.checkWorkspaceAccess(joinPoint))
                .doesNotThrowAnyException();

        verify(workspaceAccessChecker).requireAtLeast(workspaceId, currentUserId, "MEMBER");
    }

    @Test
    @DisplayName("checkWorkspaceAccess: Fail-closed ném ForbiddenException khi không tìm thấy workspaceId và required = true")
    void testCheckWorkspaceAccess_MissingWorkspaceId_FailClosed() throws NoSuchMethodException {
        mockAuthentication(false);
        Method method = getClass().getMethod("dummyMissingParamMethod", String.class);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"someValue"});

        assertThatThrownBy(() -> workspaceAccessGuard.checkWorkspaceAccess(joinPoint))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Không xác định được workspace");

        verifyNoInteractions(workspaceAccessChecker);
    }
}
