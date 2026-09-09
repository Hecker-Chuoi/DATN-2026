package com.datn.workservice.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workservice.client.IdentityClient;
import com.datn.workservice.client.dto.UserResponse;
import com.datn.workservice.dto.response.TeamMemberResponse;
import com.datn.workservice.entity.Project;
import com.datn.workservice.entity.ProjectRole;
import com.datn.workservice.entity.RoleAssignment;
import com.datn.workservice.entity.Team;
import com.datn.workservice.entity.TeamMember;
import com.datn.workservice.enums.TeamRole;
import com.datn.workservice.repository.ProjectRepository;
import com.datn.workservice.repository.ProjectRoleRepository;
import com.datn.workservice.repository.RoleAssignmentRepository;
import com.datn.workservice.repository.TeamMemberRepository;
import com.datn.workservice.repository.TeamRepository;
import com.datn.workservice.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.parameters.P;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Quản lý Team. Phân quyền bằng {@code @PreAuthorize} + bean {@code teamSecurity}:
 * <ul>
 *   <li>Xem team / thành viên: thành viên hoặc owner của team (hoặc Global ADMIN)</li>
 *   <li>Sửa team, thêm/xóa/đổi vai trò thành viên: LEADER hoặc owner</li>
 *   <li>Xóa team: owner</li>
 *   <li>Liệt kê toàn bộ team: Global ADMIN</li>
 * </ul>
 * Định danh user luôn là Keycloak id (JWT sub); id nhận từ client được chuẩn hóa qua identity-service.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/work/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final ProjectRepository projectRepository;
    private final ProjectRoleRepository projectRoleRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final IdentityClient identityClient;

    /**
     * Danh sách tất cả Team trong hệ thống (chỉ dành cho Global ADMIN).
     */
    @GetMapping
    @PreAuthorize("@teamSecurity.isGlobalAdmin()")
    public ResponseEntity<ApiResponse<List<Team>>> listAllTeams() {
        return ResponseEntity.ok(ApiResponse.success(teamRepository.findAll()));
    }

    /**
     * Danh sách Team mà người dùng hiện tại tham gia (Owner hoặc Member).
     */
    @GetMapping({"/my", "/me"})
    public ResponseEntity<ApiResponse<List<Team>>> listMyTeams() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        Set<UUID> teamIds = new HashSet<>();
        teamMemberRepository.findByUserId(currentUserId).forEach(m -> teamIds.add(m.getTeamId()));
        teamRepository.findByOwnerId(currentUserId).forEach(t -> teamIds.add(t.getId()));

        if (teamIds.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(Collections.emptyList()));
        }
        return ResponseEntity.ok(ApiResponse.success(teamRepository.findByIdIn(teamIds)));
    }

    @GetMapping("/{teamId}")
    @PreAuthorize("@teamSecurity.isMember(#teamId)")
    public ResponseEntity<ApiResponse<Team>> getTeam(@PathVariable("teamId") @P("teamId") UUID teamId) {
        Team team = requireTeam(teamId);
        return ResponseEntity.ok(ApiResponse.success(team));
    }

    @Transactional
    @PostMapping
    public ResponseEntity<ApiResponse<Team>> createTeam(@RequestBody Map<String, String> body) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        Team team = Team.builder()
                .name(body.getOrDefault("name", "Nhóm mới"))
                .description(body.get("description"))
                .ownerId(currentUserId)
                .workspaceId(currentUserId) // Placeholder workspace
                .build();
        Team saved = teamRepository.save(team);

        // Người tạo luôn là LEADER của nhóm
        teamMemberRepository.save(TeamMember.builder()
                .teamId(saved.getId())
                .userId(currentUserId)
                .role(TeamRole.LEADER)
                .build());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(saved, "Tạo nhóm thành công"));
    }

    @Transactional
    @PutMapping("/{teamId}")
    @PreAuthorize("@teamSecurity.isLeader(#teamId)")
    public ResponseEntity<ApiResponse<Team>> updateTeam(
            @PathVariable("teamId") @P("teamId") UUID teamId,
            @RequestBody Map<String, String> body
    ) {
        Team team = requireTeam(teamId);

        if (body.containsKey("name") && body.get("name") != null && !body.get("name").isBlank()) {
            team.setName(body.get("name").trim());
        }
        if (body.containsKey("description")) {
            team.setDescription(body.get("description"));
        }

        Team saved = teamRepository.save(team);
        return ResponseEntity.ok(ApiResponse.success(saved, "Cập nhật thông tin nhóm thành công"));
    }

    /**
     * Xóa nhóm (chỉ Chủ sở hữu / Owner).
     */
    @Transactional
    @DeleteMapping("/{teamId}")
    @PreAuthorize("@teamSecurity.isOwner(#teamId)")
    public ResponseEntity<ApiResponse<Void>> deleteTeam(@PathVariable("teamId") @P("teamId") UUID teamId) {
        Team team = requireTeam(teamId);

        // 1. Gỡ liên kết teamId khỏi các dự án thuộc nhóm
        List<Project> projects = projectRepository.findByTeamId(teamId);
        if (!projects.isEmpty()) {
            for (Project p : projects) {
                p.setTeamId(null);
            }
            projectRepository.saveAll(projects);
        }

        // 2. Xóa tất cả thành viên trong nhóm
        teamMemberRepository.deleteByTeamId(teamId);

        // 3. Xóa nhóm
        teamRepository.delete(team);

        return ResponseEntity.ok(ApiResponse.success(null, "Xóa nhóm thành công"));
    }

    /**
     * Rời khỏi nhóm (Thành viên tự rời, không áp dụng cho Chủ sở hữu).
     */
    @Transactional
    @PostMapping("/{teamId}/leave")
    public ResponseEntity<ApiResponse<Void>> leaveTeam(@PathVariable("teamId") UUID teamId) {
        Team team = requireTeam(teamId);
        UUID currentUserId = SecurityUtils.getCurrentUserId();

        if (currentUserId.equals(team.getOwnerId())) {
            throw new BadRequestException("Chủ sở hữu nhóm không thể rời nhóm. Bạn có thể xóa nhóm hoặc chuyển quyền sở hữu.");
        }

        TeamMember member = teamMemberRepository.findByTeamIdAndUserId(teamId, currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Bạn không phải là thành viên của nhóm này"));

        teamMemberRepository.delete(member);
        removeMemberFromTeamProjects(teamId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(null, "Bạn đã rời khỏi nhóm thành công"));
    }

    @GetMapping("/{teamId}/members")
    @PreAuthorize("@teamSecurity.isMember(#teamId)")
    public ResponseEntity<ApiResponse<List<TeamMemberResponse>>> getTeamMembers(
            @PathVariable("teamId") @P("teamId") UUID teamId
    ) {
        Team team = requireTeam(teamId);
        List<TeamMember> members = new ArrayList<>(teamMemberRepository.findByTeamId(teamId));

        // Owner luôn được hiển thị là LEADER (không ghi DB trong GET)
        if (team.getOwnerId() != null && members.stream().noneMatch(m -> team.getOwnerId().equals(m.getUserId()))) {
            members.add(0, TeamMember.builder()
                    .teamId(teamId)
                    .userId(team.getOwnerId())
                    .role(TeamRole.LEADER)
                    .joinedAt(team.getCreatedAt())
                    .build());
        }

        List<UUID> userIds = members.stream().map(TeamMember::getUserId).toList();
        Map<UUID, UserResponse> userMap = fetchUsers(userIds);

        List<TeamMemberResponse> result = new ArrayList<>();
        for (TeamMember m : members) {
            UserResponse u = userMap.get(m.getUserId());
            String fullName = null;
            String username = null;
            String email = null;
            if (u != null) {
                username = u.getUsername();
                email = u.getEmail();
                String first = u.getFirstName() != null ? u.getFirstName() : "";
                String last = u.getLastName() != null ? u.getLastName() : "";
                fullName = (first + " " + last).trim();
                if (fullName.isBlank()) {
                    fullName = username != null ? username : email;
                }
            }

            result.add(TeamMemberResponse.builder()
                    .id(m.getId())
                    .teamId(m.getTeamId())
                    .userId(m.getUserId())
                    .role(m.getRole())
                    .joinedAt(m.getJoinedAt())
                    .username(username)
                    .email(email)
                    .fullName(fullName != null ? fullName : "Thành viên (" + m.getUserId().toString().substring(0, 8) + ")")
                    .build());
        }

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Transactional
    @PostMapping("/{teamId}/members")
    @PreAuthorize("@teamSecurity.isLeader(#teamId)")
    public ResponseEntity<ApiResponse<TeamMemberResponse>> addMember(
            @PathVariable("teamId") @P("teamId") UUID teamId,
            @RequestBody Map<String, String> body
    ) {
        requireTeam(teamId);

        String identifier = firstNonBlank(body.get("email"), body.get("username"), body.get("userId"));
        if (identifier == null) {
            throw new BadRequestException("Vui lòng nhập Email, Username hoặc User ID của thành viên");
        }

        UUID targetUserId = resolveUserId(identifier);
        if (targetUserId == null) {
            throw new ResourceNotFoundException("Không tìm thấy người dùng với thông tin: " + identifier);
        }

        TeamRole role = parseRole(body.get("role"), TeamRole.MEMBER);

        TeamMember savedMember = teamMemberRepository.findByTeamIdAndUserId(teamId, targetUserId)
                .map(existing -> {
                    existing.setRole(role);
                    return teamMemberRepository.save(existing);
                })
                .orElseGet(() -> teamMemberRepository.save(TeamMember.builder()
                        .teamId(teamId)
                        .userId(targetUserId)
                        .role(role)
                        .build()));

        // Đồng bộ vào các dự án của nhóm
        syncMemberToTeamProjects(teamId, savedMember.getUserId(), savedMember.getRole());

        return ResponseEntity.ok(ApiResponse.success(toResponse(savedMember), "Thêm thành viên thành công"));
    }

    @Transactional
    @PutMapping("/{teamId}/members/{userId}/role")
    @PreAuthorize("@teamSecurity.isLeader(#teamId)")
    public ResponseEntity<ApiResponse<TeamMemberResponse>> updateMemberRole(
            @PathVariable("teamId") @P("teamId") UUID teamId,
            @PathVariable("userId") UUID userId,
            @RequestBody Map<String, String> body
    ) {
        requireTeam(teamId);
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        UUID targetUserId = canonicalUserId(userId);

        if (currentUserId.equals(targetUserId)) {
            throw new BadRequestException("Bạn không thể tự thay đổi vai trò của chính mình");
        }

        String roleStr = body.get("role");
        if (roleStr == null || roleStr.isBlank()) {
            throw new BadRequestException("Vai trò không được để trống");
        }
        TeamRole newRole;
        try {
            newRole = TeamRole.valueOf(roleStr.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Vai trò không hợp lệ: " + roleStr);
        }

        TeamMember member = findMember(teamId, userId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thành viên này trong nhóm"));

        member.setRole(newRole);
        member = teamMemberRepository.save(member);

        syncMemberToTeamProjects(teamId, member.getUserId(), member.getRole());

        return ResponseEntity.ok(ApiResponse.success(toResponse(member), "Cập nhật vai trò thành công"));
    }

    @Transactional
    @DeleteMapping("/{teamId}/members/{userId}")
    @PreAuthorize("@teamSecurity.isLeader(#teamId)")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable("teamId") @P("teamId") UUID teamId,
            @PathVariable("userId") UUID userId
    ) {
        Team team = requireTeam(teamId);
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        UUID targetUserId = canonicalUserId(userId);

        if (currentUserId.equals(targetUserId)) {
            throw new BadRequestException("Bạn không thể tự xóa chính mình khỏi nhóm (vui lòng sử dụng tính năng Rời nhóm)");
        }
        if (targetUserId.equals(team.getOwnerId())) {
            throw new BadRequestException("Không thể xóa Trưởng nhóm / Chủ sở hữu nhóm");
        }

        TeamMember targetMember = findMember(teamId, userId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thành viên này trong nhóm"));

        teamMemberRepository.delete(targetMember);
        removeMemberFromTeamProjects(teamId, targetMember.getUserId());

        return ResponseEntity.ok(ApiResponse.success(null, "Đã xóa thành viên khỏi nhóm"));
    }

    // ─── Helper Methods ──────────────────────────────────────────────────────────

    private Team requireTeam(UUID teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm: " + teamId));
    }

    private Optional<TeamMember> findMember(UUID teamId, UUID rawUserId, UUID canonicalUserId) {
        Optional<TeamMember> member = teamMemberRepository.findByTeamIdAndUserId(teamId, canonicalUserId);
        if (member.isEmpty() && rawUserId != null && !rawUserId.equals(canonicalUserId)) {
            member = teamMemberRepository.findByTeamIdAndUserId(teamId, rawUserId);
        }
        return member;
    }

    private void syncMemberToTeamProjects(UUID teamId, UUID userId, TeamRole teamRole) {
        if (teamId == null || userId == null || teamRole == null) return;
        List<Project> projects = projectRepository.findByTeamId(teamId);

        for (Project project : projects) {
            List<ProjectRole> roles = projectRoleRepository.findByProjectId(project.getId());
            if (roles.isEmpty()) continue;

            ProjectRole adminRole = findRole(roles, "ADMINISTRATOR");
            ProjectRole memberRole = findRole(roles, "MEMBER");
            ProjectRole viewerRole = findRole(roles, "VIEWER");

            ProjectRole targetRole = memberRole;
            if (teamRole == TeamRole.LEADER) {
                targetRole = adminRole;
            } else if (teamRole == TeamRole.VIEWER) {
                targetRole = viewerRole;
            }
            if (targetRole == null) continue;

            List<RoleAssignment> existing = roleAssignmentRepository.findByProjectIdAndUserId(project.getId(), userId);
            if (existing.isEmpty()) {
                roleAssignmentRepository.save(RoleAssignment.builder()
                        .projectId(project.getId())
                        .userId(userId)
                        .roleId(targetRole.getId())
                        .build());
            } else {
                for (RoleAssignment ra : existing) {
                    ra.setRoleId(targetRole.getId());
                    roleAssignmentRepository.save(ra);
                }
            }
        }
    }

    private void removeMemberFromTeamProjects(UUID teamId, UUID userId) {
        if (teamId == null || userId == null) return;
        for (Project project : projectRepository.findByTeamId(teamId)) {
            // Project Lead giữ quyền quản trị ngầm định, không cần xóa assignment
            if (userId.equals(project.getLeadUserId())) continue;
            roleAssignmentRepository.deleteByProjectIdAndUserId(project.getId(), userId);
        }
    }

    private static ProjectRole findRole(List<ProjectRole> roles, String name) {
        return roles.stream().filter(r -> name.equalsIgnoreCase(r.getName())).findFirst().orElse(null);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
    }

    private static TeamRole parseRole(String roleStr, TeamRole fallback) {
        if (roleStr == null || roleStr.isBlank()) return fallback;
        try {
            return TeamRole.valueOf(roleStr.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    /**
     * Resolve email / username / (DB id | Keycloak id) -> Keycloak id.
     */
    private UUID resolveUserId(String identifier) {
        try {
            ApiResponse<UserResponse> resp = identityClient.getUserByIdentifier(identifier);
            UUID fromIdentity = toKeycloakId(resp != null ? resp.getData() : null);
            if (fromIdentity != null) return fromIdentity;
        } catch (Exception ignored) {
            // Có thể identifier là UUID -> thử tra theo id
        }
        try {
            UUID id = UUID.fromString(identifier);
            return canonicalUserId(id);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Chuẩn hóa bất kỳ id nào (DB id hoặc Keycloak id) về Keycloak id; nếu identity không trả lời thì giữ nguyên.
     */
    private UUID canonicalUserId(UUID anyId) {
        if (anyId == null) return null;
        try {
            ApiResponse<UserResponse> resp = identityClient.getUserById(anyId);
            UUID kc = toKeycloakId(resp != null ? resp.getData() : null);
            if (kc != null) return kc;
        } catch (Exception ignored) {
        }
        return anyId;
    }

    private static UUID toKeycloakId(UserResponse user) {
        if (user == null) return null;
        if (user.getKeycloakId() != null) {
            try {
                return UUID.fromString(user.getKeycloakId());
            } catch (IllegalArgumentException ignored) {
            }
        }
        return user.getId();
    }

    private Map<UUID, UserResponse> fetchUsers(List<UUID> userIds) {
        Map<UUID, UserResponse> userMap = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) return userMap;
        try {
            ApiResponse<List<UserResponse>> usersResp = identityClient.getUsersByIds(userIds);
            if (usersResp != null && usersResp.getData() != null) {
                for (UserResponse u : usersResp.getData()) {
                    if (u.getId() != null) {
                        userMap.put(u.getId(), u);
                    }
                    if (u.getKeycloakId() != null) {
                        try {
                            userMap.put(UUID.fromString(u.getKeycloakId()), u);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Could not fetch user details from identityClient: {}", ex.getMessage());
        }
        return userMap;
    }

    private static TeamMemberResponse toResponse(TeamMember m) {
        return TeamMemberResponse.builder()
                .id(m.getId())
                .teamId(m.getTeamId())
                .userId(m.getUserId())
                .role(m.getRole())
                .joinedAt(m.getJoinedAt())
                .build();
    }
}
