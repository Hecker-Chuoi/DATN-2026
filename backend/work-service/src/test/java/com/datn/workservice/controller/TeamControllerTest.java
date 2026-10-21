package com.datn.workservice.controller;

import com.datn.common.exception.BadRequestException;
import com.datn.workservice.client.IdentityClient;
import com.datn.workservice.client.dto.UserResponse;
import com.datn.workservice.entity.Team;
import com.datn.workservice.entity.TeamMember;
import com.datn.workservice.enums.TeamRole;
import com.datn.workservice.repository.*;
import com.datn.workservice.security.TeamSecurity;
import com.datn.workservice.security.WorkspaceAccessChecker;
import com.datn.workservice.service.InvitationService;
import com.datn.workservice.service.ProjectMemberService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamControllerTest {

    @Mock
    private TeamRepository teamRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectRoleRepository projectRoleRepository;
    @Mock
    private RoleAssignmentRepository roleAssignmentRepository;
    @Mock
    private IdentityClient identityClient;
    @Mock
    private InvitationService invitationService;
    @Mock
    private ProjectMemberService projectMemberService;
    @Mock
    private TeamSecurity teamSecurity;
    @Mock
    private WorkspaceAccessChecker workspaceAccessChecker;

    @Mock
    private SecurityContext securityContext;
    @Mock
    private Authentication authentication;

    @InjectMocks
    private TeamController teamController;

    private UUID workspaceId;
    private UUID teamId;
    private UUID currentUserId;
    private UUID memberUserId;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        teamId = UUID.randomUUID();
        currentUserId = UUID.randomUUID();
        memberUserId = UUID.randomUUID();

        SecurityContextHolder.setContext(securityContext);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.isAuthenticated()).thenReturn(true);
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .subject(currentUserId.toString())
                .build();
        lenient().when(authentication.getPrincipal()).thenReturn(jwt);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Ném BadRequestException khi nhóm chỉ có 1 thành viên duy nhất")
    void updateMemberRole_WhenTeamHasOnlyOneMember_ThrowsBadRequest() {
        Team team = Team.builder()
                .id(teamId)
                .workspaceId(workspaceId)
                .ownerId(memberUserId)
                .build();

        when(teamSecurity.canManageTeam(teamId)).thenReturn(true);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        // Owner còn trong workspace
        when(workspaceAccessChecker.getRole(workspaceId, memberUserId)).thenReturn("MEMBER");
        // Không có ai khác trong team_members
        when(teamMemberRepository.findByTeamId(teamId)).thenReturn(Collections.emptyList());

        Map<String, String> body = Map.of("role", "MEMBER");

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                teamController.updateMemberRole(teamId, memberUserId, body));

        assertTrue(ex.getMessage().contains("Không thể thay đổi vai trò khi nhóm chỉ có 1 thành viên duy nhất"));
        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cập nhật vai trò thành công khi nhóm có từ 2 thành viên trở lên")
    void updateMemberRole_WhenTeamHasMultipleMembers_Success() {
        UUID otherMemberId = UUID.randomUUID();
        Team team = Team.builder()
                .id(teamId)
                .workspaceId(workspaceId)
                .ownerId(currentUserId)
                .build();

        TeamMember member = TeamMember.builder()
                .id(UUID.randomUUID())
                .teamId(teamId)
                .userId(otherMemberId)
                .role(TeamRole.MEMBER)
                .build();

        when(teamSecurity.canManageTeam(teamId)).thenReturn(true);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        // Cả 2 user đều còn trong workspace
        when(workspaceAccessChecker.getRole(workspaceId, currentUserId)).thenReturn("ADMIN");
        when(workspaceAccessChecker.getRole(workspaceId, otherMemberId)).thenReturn("MEMBER");
        // team_members có otherMemberId
        when(teamMemberRepository.findByTeamId(teamId)).thenReturn(List.of(member));
        when(teamMemberRepository.findByTeamIdAndUserId(teamId, otherMemberId)).thenReturn(Optional.of(member));
        when(teamMemberRepository.save(any(TeamMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, String> body = Map.of("role", "VIEWER");

        var response = teamController.updateMemberRole(teamId, otherMemberId, body);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(TeamRole.VIEWER, response.getBody().getData().getRole());
        verify(teamMemberRepository).save(any(TeamMember.class));
    }
}
