package com.datn.workservice.security;

import com.datn.workservice.entity.Team;
import com.datn.workservice.entity.TeamMember;
import com.datn.workservice.enums.TeamRole;
import com.datn.workservice.repository.TeamMemberRepository;
import com.datn.workservice.repository.TeamRepository;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamSecurityTest {

    @Mock
    private TeamRepository teamRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private WorkspaceAccessChecker workspaceAccessChecker;

    @InjectMocks
    private TeamSecurity teamSecurity;

    private UUID teamId;
    private UUID workspaceId;
    private UUID ownerUserId;
    private UUID leaderUserId;
    private UUID memberUserId;
    private UUID outsiderUserId;
    private Team team;

    @BeforeEach
    void setUp() {
        teamId = UUID.randomUUID();
        workspaceId = UUID.randomUUID();
        ownerUserId = UUID.randomUUID();
        leaderUserId = UUID.randomUUID();
        memberUserId = UUID.randomUUID();
        outsiderUserId = UUID.randomUUID();

        team = Team.builder()
                .id(teamId)
                .workspaceId(workspaceId)
                .name("Dev Squad")
                .ownerId(leaderUserId)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockCurrentUser(UUID userId) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(userId.toString());
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(jwt);
        when(auth.isAuthenticated()).thenReturn(true);
        SecurityContext sc = mock(SecurityContext.class);
        when(sc.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(sc);
    }

    @Test
    @DisplayName("Workspace Owner có quyền Leader tối cao (Bóng ma Siêu Quản Trị) dù không có trong team")
    void workspaceOwner_shouldHaveLeaderPrivileges() {
        mockCurrentUser(ownerUserId);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(workspaceAccessChecker.getRole(workspaceId, ownerUserId)).thenReturn("OWNER");

        boolean isLeader = teamSecurity.isLeader(teamId);

        assertThat(isLeader).isTrue();
        verify(teamMemberRepository, never()).findByTeamIdAndUserId(any(), any());
    }

    @Test
    @DisplayName("Active Leader (ownerId của Team) có quyền Leader")
    void activeLeader_shouldHaveLeaderPrivileges() {
        mockCurrentUser(leaderUserId);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(workspaceAccessChecker.getRole(workspaceId, leaderUserId)).thenReturn("MEMBER");

        boolean isLeader = teamSecurity.isLeader(teamId);

        assertThat(isLeader).isTrue();
    }

    @Test
    @DisplayName("Thành viên thường có role LEADER trong team_members có quyền Leader")
    void memberWithLeaderRole_shouldHaveLeaderPrivileges() {
        UUID coLeaderId = UUID.randomUUID();
        mockCurrentUser(coLeaderId);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(workspaceAccessChecker.getRole(workspaceId, coLeaderId)).thenReturn("MEMBER");
        when(teamMemberRepository.findByTeamIdAndUserId(teamId, coLeaderId))
                .thenReturn(Optional.of(TeamMember.builder().teamId(teamId).userId(coLeaderId).role(TeamRole.LEADER).build()));

        boolean isLeader = teamSecurity.isLeader(teamId);

        assertThat(isLeader).isTrue();
    }

    @Test
    @DisplayName("Thành viên MEMBER thường không có quyền Leader")
    void regularMember_shouldNotHaveLeaderPrivileges() {
        mockCurrentUser(memberUserId);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(workspaceAccessChecker.getRole(workspaceId, memberUserId)).thenReturn("MEMBER");
        when(teamMemberRepository.findByTeamIdAndUserId(teamId, memberUserId))
                .thenReturn(Optional.of(TeamMember.builder().teamId(teamId).userId(memberUserId).role(TeamRole.MEMBER).build()));

        boolean isLeader = teamSecurity.isLeader(teamId);

        assertThat(isLeader).isFalse();
    }

    @Test
    @DisplayName("Workspace Admin có quyền canManageTeam để quản trị nhóm và bổ nhiệm Lead")
    void workspaceAdmin_shouldCanManageTeam() {
        UUID adminUserId = UUID.randomUUID();
        mockCurrentUser(adminUserId);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(workspaceAccessChecker.getRole(workspaceId, adminUserId)).thenReturn("ADMIN");

        boolean canManage = teamSecurity.canManageTeam(teamId);

        assertThat(canManage).isTrue();
    }

    @Test
    @DisplayName("Workspace Admin và Workspace Owner có thể truy cập xem thông tin nhóm")
    void workspaceAdminAndOwner_shouldBeConsideredMembersForViewing() {
        UUID adminUserId = UUID.randomUUID();
        mockCurrentUser(adminUserId);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(workspaceAccessChecker.getRole(workspaceId, adminUserId)).thenReturn("ADMIN");

        boolean isMember = teamSecurity.isMember(teamId);

        assertThat(isMember).isTrue();
        verify(teamMemberRepository, never()).existsByTeamIdAndUserId(any(), any());
    }

    @Test
    @DisplayName("Người ngoài không thuộc team và không phải Admin/Owner sẽ bị từ chối")
    void outsider_shouldBeDenied() {
        mockCurrentUser(outsiderUserId);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(workspaceAccessChecker.getRole(workspaceId, outsiderUserId)).thenReturn("MEMBER");
        when(teamMemberRepository.existsByTeamIdAndUserId(teamId, outsiderUserId)).thenReturn(false);

        boolean isMember = teamSecurity.isMember(teamId);

        assertThat(isMember).isFalse();
    }
}
