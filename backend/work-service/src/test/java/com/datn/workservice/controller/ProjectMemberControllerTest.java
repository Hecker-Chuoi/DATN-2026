package com.datn.workservice.controller;

import com.datn.workservice.dto.request.AssignProjectRoleRequest;
import com.datn.workservice.dto.response.MyProjectPermissionsResponse;
import com.datn.workservice.dto.response.ProjectRoleResponse;
import com.datn.workservice.dto.response.RoleAssignmentResponse;
import com.datn.workservice.service.ProjectMemberService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ProjectMemberControllerTest {

    @Mock
    private ProjectMemberService projectMemberService;

    @Mock
    private com.datn.workservice.service.InvitationService invitationService;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private ProjectMemberController projectMemberController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private UUID projectId;
    private UUID currentUserId;
    private UUID roleId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(projectMemberController).build();
        objectMapper = new ObjectMapper();

        projectId = UUID.randomUUID();
        currentUserId = UUID.randomUUID();
        roleId = UUID.randomUUID();

        SecurityContextHolder.setContext(securityContext);
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .claim("sub", currentUserId.toString())
                .build();
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.isAuthenticated()).thenReturn(true);
        lenient().when(authentication.getPrincipal()).thenReturn(jwt);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/v1/work/projects/{projectId}/permissions/my - Returns role & permissions")
    void testGetMyPermissions_Success() throws Exception {
        MyProjectPermissionsResponse response = MyProjectPermissionsResponse.builder()
                .projectId(projectId)
                .roleName("MEMBER")
                .isLead(false)
                .isGlobalAdmin(false)
                .permissions(List.of("PROJECT_VIEW", "ISSUE_CREATE", "SPRINT_MANAGE"))
                .build();
        when(projectMemberService.getMyPermissions(projectId, currentUserId, false)).thenReturn(response);

        mockMvc.perform(get("/api/v1/work/projects/{projectId}/permissions/my", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.roleName").value("MEMBER"))
                .andExpect(jsonPath("$.data.isLead").value(false))
                .andExpect(jsonPath("$.data.permissions[0]").value("PROJECT_VIEW"))
                .andExpect(jsonPath("$.data.permissions[1]").value("ISSUE_CREATE"))
                .andExpect(jsonPath("$.data.permissions[2]").value("SPRINT_MANAGE"));

        verify(projectMemberService).getMyPermissions(projectId, currentUserId, false);
    }

    @Test
    @DisplayName("GET /api/v1/work/projects/{projectId}/members - Returns project members")
    void testGetProjectMembers_Success() throws Exception {
        RoleAssignmentResponse member = RoleAssignmentResponse.builder()
                .id(UUID.randomUUID())
                .projectId(projectId)
                .userId(UUID.randomUUID())
                .roleId(roleId)
                .roleName("MEMBER")
                .username("jane_dev")
                .email("jane@example.com")
                .fullName("Jane Doe")
                .assignedAt(LocalDateTime.now())
                .build();

        when(projectMemberService.getProjectMembers(projectId)).thenReturn(List.of(member));

        mockMvc.perform(get("/api/v1/work/projects/{projectId}/members", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].roleName").value("MEMBER"))
                .andExpect(jsonPath("$.data[0].username").value("jane_dev"));

        verify(projectMemberService).getProjectMembers(projectId);
    }

    @Test
    @DisplayName("POST /api/v1/work/projects/{projectId}/members - Sends an invitation instead of adding directly")
    void testAddMemberToProject_CreatesInvitation() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        AssignProjectRoleRequest request = AssignProjectRoleRequest.builder()
                .userId(targetUserId)
                .roleId(roleId)
                .build();

        com.datn.workservice.dto.response.InvitationResponse response = com.datn.workservice.dto.response.InvitationResponse.builder()
                .id(UUID.randomUUID())
                .type(com.datn.workservice.enums.InvitationType.PROJECT)
                .targetId(projectId)
                .inviteeUserId(targetUserId)
                .inviteeName("new_member")
                .roleName("MEMBER")
                .status(com.datn.workservice.enums.InvitationStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        when(invitationService.inviteToProject(eq(projectId), eq(targetUserId), eq(roleId), eq(currentUserId)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/work/projects/{projectId}/members", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.inviteeName").value("new_member"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.message").value("Đã gửi lời mời tham gia dự án"));

        verify(projectMemberService, never()).addMemberToProject(any(), any(), any());
    }

    @Test
    @DisplayName("PUT /api/v1/work/projects/{projectId}/members/{userId}/role - Updates role successfully")
    void testUpdateMemberRole_Success() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        UUID newRoleId = UUID.randomUUID();
        Map<String, UUID> body = Map.of("roleId", newRoleId);

        RoleAssignmentResponse response = RoleAssignmentResponse.builder()
                .id(UUID.randomUUID())
                .projectId(projectId)
                .userId(targetUserId)
                .roleId(newRoleId)
                .roleName("ADMINISTRATOR")
                .assignedAt(LocalDateTime.now())
                .build();

        when(projectMemberService.updateMemberRole(projectId, targetUserId, newRoleId, currentUserId))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/work/projects/{projectId}/members/{userId}/role", projectId, targetUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.roleName").value("ADMINISTRATOR"));
    }

    @Test
    @DisplayName("DELETE /api/v1/work/projects/{projectId}/members/{userId} - Removes member")
    void testRemoveMemberFromProject_Success() throws Exception {
        UUID targetUserId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/work/projects/{projectId}/members/{userId}", projectId, targetUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đã xóa thành viên khỏi dự án"));

        verify(projectMemberService).removeMemberFromProject(projectId, targetUserId, currentUserId);
    }

    @Test
    @DisplayName("GET /api/v1/work/projects/{projectId}/roles - Returns project roles")
    void testGetProjectRoles_Success() throws Exception {
        ProjectRoleResponse role = ProjectRoleResponse.builder()
                .id(roleId)
                .projectId(projectId)
                .name("DEVELOPER")
                .description("Software developer")
                .build();

        when(projectMemberService.getProjectRoles(projectId)).thenReturn(List.of(role));

        mockMvc.perform(get("/api/v1/work/projects/{projectId}/roles", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("DEVELOPER"));

        verify(projectMemberService).getProjectRoles(projectId);
    }
}
