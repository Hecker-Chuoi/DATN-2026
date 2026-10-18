package com.datn.workservice.security;

import com.datn.workservice.enums.TeamRole;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TeamRoleGuardTest {

    @Mock
    private TeamSecurity teamSecurity;

    @Mock
    private JoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private TeamRoleGuard teamRoleGuard;

    private UUID currentUserId;
    private UUID teamId;

    // Helper dummy methods for reflection testing
    @RequireTeamRole(TeamRole.LEADER)
    public void dummyLeaderMethod(@PathVariable("teamId") UUID teamId) {}

    @RequireTeamRole(TeamRole.MEMBER)
    public void dummyMemberMethod(@PathVariable("teamId") UUID teamId) {}

    @RequireTeamRole(TeamRole.LEADER)
    public void dummyDtoMethod(TestTeamDto dto) {}

    @RequireTeamRole(TeamRole.LEADER)
    public void dummyMissingParamMethod(String otherParam) {}

    @RequireGlobalAdmin
    public void dummyGlobalAdminMethod() {}

    public static class TestTeamDto {
        private final UUID teamId;

        public TestTeamDto(UUID teamId) {
            this.teamId = teamId;
        }

        public UUID getTeamId() {
            return teamId;
        }
    }

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
        teamId = UUID.randomUUID();
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
    @DisplayName("checkTeamRole: Thành công khi user là LEADER của nhóm")
    void testCheckTeamRole_Leader_Success() throws NoSuchMethodException {
        mockAuthentication(false);
        Method method = getClass().getMethod("dummyLeaderMethod", UUID.class);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[]{teamId});
        when(teamSecurity.isLeader(teamId)).thenReturn(true);

        assertThatCode(() -> teamRoleGuard.checkTeamRole(joinPoint))
                .doesNotThrowAnyException();

        verify(teamSecurity).isLeader(teamId);
    }

    @Test
    @DisplayName("checkTeamRole: Bị từ chối khi user không phải LEADER")
    void testCheckTeamRole_Leader_Unauthorized_Throws() throws NoSuchMethodException {
        mockAuthentication(false);
        Method method = getClass().getMethod("dummyLeaderMethod", UUID.class);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[]{teamId});
        when(teamSecurity.isLeader(teamId)).thenReturn(false);

        assertThatThrownBy(() -> teamRoleGuard.checkTeamRole(joinPoint))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Bạn không có quyền Trưởng nhóm (Leader)");
    }

    @Test
    @DisplayName("checkTeamRole: Thành công khi user là MEMBER của nhóm")
    void testCheckTeamRole_Member_Success() throws NoSuchMethodException {
        mockAuthentication(false);
        Method method = getClass().getMethod("dummyMemberMethod", UUID.class);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[]{teamId});
        when(teamSecurity.isMember(teamId)).thenReturn(true);

        assertThatCode(() -> teamRoleGuard.checkTeamRole(joinPoint))
                .doesNotThrowAnyException();

        verify(teamSecurity).isMember(teamId);
    }

    @Test
    @DisplayName("checkTeamRole: Tự động trích xuất teamId từ Request DTO")
    void testCheckTeamRole_ExtractFromDto_Success() throws NoSuchMethodException {
        mockAuthentication(false);
        Method method = getClass().getMethod("dummyDtoMethod", TestTeamDto.class);
        TestTeamDto dto = new TestTeamDto(teamId);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[]{dto});
        when(teamSecurity.isLeader(teamId)).thenReturn(true);

        assertThatCode(() -> teamRoleGuard.checkTeamRole(joinPoint))
                .doesNotThrowAnyException();

        verify(teamSecurity).isLeader(teamId);
    }

    @Test
    @DisplayName("checkTeamRole: Fail-closed ném 403 khi không tìm thấy teamId")
    void testCheckTeamRole_MissingTeamId_FailClosed() throws NoSuchMethodException {
        mockAuthentication(false);
        Method method = getClass().getMethod("dummyMissingParamMethod", String.class);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"otherValue"});

        assertThatThrownBy(() -> teamRoleGuard.checkTeamRole(joinPoint))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Không xác định được nhóm");

        verifyNoInteractions(teamSecurity);
    }

    @Test
    @DisplayName("checkGlobalAdmin: Cho phép khi người dùng là Global ADMIN")
    void testCheckGlobalAdmin_Success() throws NoSuchMethodException {
        mockAuthentication(true);
        Method method = getClass().getMethod("dummyGlobalAdminMethod");

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);

        assertThatCode(() -> teamRoleGuard.checkTeamRole(joinPoint))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("checkGlobalAdmin: Bị từ chối khi người dùng không phải ADMIN")
    void testCheckGlobalAdmin_Unauthorized_Throws() throws NoSuchMethodException {
        mockAuthentication(false);
        Method method = getClass().getMethod("dummyGlobalAdminMethod");

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);

        assertThatThrownBy(() -> teamRoleGuard.checkTeamRole(joinPoint))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Quản trị viên hệ thống (Global Admin)");
    }
}
