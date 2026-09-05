package com.datn.workservice.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.workservice.dto.request.AddTeamMemberRequest;
import com.datn.workservice.dto.request.CreateTeamRequest;
import com.datn.workservice.dto.response.TeamMemberResponse;
import com.datn.workservice.dto.response.TeamResponse;
import com.datn.workservice.service.TeamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.parameters.P;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/work/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @PostMapping
    public ResponseEntity<ApiResponse<TeamResponse>> createTeam(
            @Valid @RequestBody CreateTeamRequest request,
            Principal principal) {
        UUID currentUserId = extractUserId(principal);
        TeamResponse team = teamService.createTeam(request, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(team, "Tạo nhóm thành công"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TeamResponse>>> getAllTeams() {
        List<TeamResponse> teams = teamService.getAllTeams();
        return ResponseEntity.ok(ApiResponse.success(teams, "Lấy danh sách nhóm thành công"));
    }

    @GetMapping("/{teamId}")
    public ResponseEntity<ApiResponse<TeamResponse>> getTeamById(@PathVariable("teamId") UUID teamId) {
        TeamResponse team = teamService.getTeamById(teamId);
        return ResponseEntity.ok(ApiResponse.success(team));
    }

    @PostMapping("/{teamId}/members")
    @PreAuthorize("@projectSecurity.hasTeamPermission(#teamId, 'MEMBER_INVITE')")
    public ResponseEntity<ApiResponse<TeamMemberResponse>> addMember(
            @PathVariable("teamId") @P("teamId") UUID teamId,
            @Valid @RequestBody AddTeamMemberRequest request) {
        TeamMemberResponse member = teamService.addMember(teamId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(member, "Thêm thành viên vào nhóm thành công"));
    }

    @DeleteMapping("/{teamId}/members/{userId}")
    @PreAuthorize("@projectSecurity.hasTeamPermission(#teamId, 'MEMBER_REMOVE')")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable("teamId") @P("teamId") UUID teamId,
            @PathVariable("userId") UUID userId) {
        teamService.removeMember(teamId, userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa thành viên khỏi nhóm thành công"));
    }

    @PutMapping("/{teamId}/members/{userId}/role")
    @PreAuthorize("@projectSecurity.hasTeamPermission(#teamId, 'MEMBER_ROLE_UPDATE')")
    public ResponseEntity<ApiResponse<TeamMemberResponse>> updateMemberRole(
            @PathVariable("teamId") @P("teamId") UUID teamId,
            @PathVariable("userId") UUID userId,
            @Valid @RequestBody com.datn.workservice.dto.request.UpdateTeamMemberRoleRequest request,
            Principal principal) {
        UUID currentUserId = extractUserId(principal);
        TeamMemberResponse updatedMember = teamService.updateMemberRole(teamId, userId, request, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(updatedMember, "Cập nhật quyền thành viên thành công"));
    }

    @GetMapping("/{teamId}/members/{userId}/role")
    public ResponseEntity<ApiResponse<String>> getMemberRole(
            @PathVariable("teamId") UUID teamId,
            @PathVariable("userId") UUID userId) {
        String role = teamService.getUserRoleInTeam(teamId, userId);
        return ResponseEntity.ok(ApiResponse.success(role, "Lấy vai trò thành viên thành công"));
    }

    @GetMapping("/{teamId}/my-role")
    public ResponseEntity<ApiResponse<String>> getMyRole(
            @PathVariable("teamId") UUID teamId,
            Principal principal) {
        UUID currentUserId = extractUserId(principal);
        String role = teamService.getUserRoleInTeam(teamId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(role, "Lấy vai trò của tôi trong nhóm thành công"));
    }

    @GetMapping("/{teamId}/members")
    public ResponseEntity<ApiResponse<List<TeamMemberResponse>>> getTeamMembers(@PathVariable("teamId") UUID teamId) {
        List<TeamMemberResponse> members = teamService.getTeamMembers(teamId);
        return ResponseEntity.ok(ApiResponse.success(members));
    }

    private UUID extractUserId(Principal principal) {
        if (principal instanceof JwtAuthenticationToken jwtToken) {
            String sub = jwtToken.getToken().getSubject();
            try {
                return UUID.fromString(sub);
            } catch (Exception ignored) {}
        }
        return null;
    }
}
