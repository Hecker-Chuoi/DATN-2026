package com.datn.workservice.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ForbiddenException;
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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

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
     * Lấy danh sách tất cả các Team trong hệ thống (Dành cho Admin / Quản trị hệ sinh thái)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Team>>> listAllTeams() {
        List<Team> allTeams = teamRepository.findAll();
        return ResponseEntity.ok(ApiResponse.success(allTeams));
    }

    /**
     * Lấy danh sách các Team mà người dùng hiện tại đang tham gia (là Owner hoặc Member).
     * Dùng hiển thị trên Sidebar và danh sách nhóm của người dùng.
     */
    @GetMapping({"/my", "/me"})
    public ResponseEntity<ApiResponse<List<Team>>> listMyTeams() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        Set<UUID> userIds = getUserAliasIds(currentUserId);
        Set<UUID> teamIds = new HashSet<>();

        for (UUID uid : userIds) {
            teamMemberRepository.findByUserId(uid).forEach(m -> teamIds.add(m.getTeamId()));
            teamRepository.findByOwnerId(uid).forEach(t -> teamIds.add(t.getId()));
        }

        if (teamIds.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(Collections.emptyList()));
        }

        List<Team> teams = teamRepository.findByIdIn(teamIds);
        return ResponseEntity.ok(ApiResponse.success(teams));
    }

    @GetMapping("/{teamId}")
    public ResponseEntity<ApiResponse<Team>> getTeam(@PathVariable("teamId") UUID teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm: " + teamId));
        return ResponseEntity.ok(ApiResponse.success(team));
    }

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

        // Automatically assign creator as LEADER
        try {
            TeamMember leader = TeamMember.builder()
                    .teamId(saved.getId())
                    .userId(currentUserId)
                    .role(TeamRole.LEADER)
                    .build();
            teamMemberRepository.save(leader);
        } catch (Exception ex) {
            log.warn("Could not save initial team leader: {}", ex.getMessage());
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(saved, "Tạo nhóm thành công"));
    }

    @Transactional
    @PutMapping("/{teamId}")
    public ResponseEntity<ApiResponse<Team>> updateTeam(
            @PathVariable("teamId") UUID teamId,
            @RequestBody Map<String, String> body
    ) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm: " + teamId));

        UUID currentUserId = SecurityUtils.getCurrentUserId();
        boolean isOwner = isTeamOwner(team, currentUserId);
        boolean isLeader = isOwner || isTeamLeader(teamId, currentUserId);

        if (!isOwner && !isLeader) {
            throw new ForbiddenException("Chỉ Trưởng nhóm (Leader) hoặc Chủ sở hữu mới có quyền chỉnh sửa thông tin nhóm");
        }

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
     * Xóa nhóm (Chỉ Chủ sở hữu / Owner mới có quyền xóa)
     */
    @Transactional
    @DeleteMapping("/{teamId}")
    public ResponseEntity<ApiResponse<Void>> deleteTeam(@PathVariable("teamId") UUID teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm: " + teamId));

        UUID currentUserId = SecurityUtils.getCurrentUserId();
        if (!isTeamOwner(team, currentUserId)) {
            throw new ForbiddenException("Chỉ Chủ sở hữu nhóm (Owner) mới có quyền xóa nhóm này");
        }

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
     * Rời khỏi nhóm (Thành viên tự rời, không áp dụng cho Chủ sở hữu)
     */
    @Transactional
    @PostMapping("/{teamId}/leave")
    public ResponseEntity<ApiResponse<Void>> leaveTeam(@PathVariable("teamId") UUID teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm: " + teamId));

        UUID currentUserId = SecurityUtils.getCurrentUserId();

        if (isTeamOwner(team, currentUserId)) {
            throw new BadRequestException("Chủ sở hữu nhóm không thể rời nhóm. Bạn có thể xóa nhóm hoặc chuyển quyền sở hữu.");
        }

        Set<UUID> aliasIds = getUserAliasIds(currentUserId);
        TeamMember member = null;
        for (UUID uid : aliasIds) {
            Optional<TeamMember> opt = teamMemberRepository.findByTeamIdAndUserId(teamId, uid);
            if (opt.isPresent()) {
                member = opt.get();
                break;
            }
        }

        if (member == null) {
            throw new ResourceNotFoundException("Bạn không phải là thành viên của nhóm này");
        }

        teamMemberRepository.delete(member);
        return ResponseEntity.ok(ApiResponse.success(null, "Bạn đã rời khỏi nhóm thành công"));
    }

    @GetMapping("/{teamId}/members")
    public ResponseEntity<ApiResponse<List<TeamMemberResponse>>> getTeamMembers(@PathVariable("teamId") UUID teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm: " + teamId));

        List<TeamMember> members = teamMemberRepository.findByTeamId(teamId);

        // If no members registered yet, ensure owner is added as LEADER
        if (members.isEmpty() && team.getOwnerId() != null) {
            TeamMember ownerMember = TeamMember.builder()
                    .teamId(teamId)
                    .userId(team.getOwnerId())
                    .role(TeamRole.LEADER)
                    .build();
            ownerMember = teamMemberRepository.save(ownerMember);
            members = List.of(ownerMember);
        }

        List<UUID> userIds = members.stream().map(TeamMember::getUserId).toList();
        Map<UUID, UserResponse> userMap = new HashMap<>();

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

        // Group by user to prevent duplicate rows if legacy data had both KeycloakId and DbId
        Map<String, TeamMemberResponse> dedupMap = new LinkedHashMap<>();
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

            String dedupKey = (email != null && !email.isBlank()) ? email.toLowerCase() : m.getUserId().toString();
            if (dedupMap.containsKey(dedupKey)) {
                // Keep LEADER role if either record was LEADER
                if (m.getRole() == TeamRole.LEADER) {
                    dedupMap.get(dedupKey).setRole(TeamRole.LEADER);
                }
                continue;
            }

            TeamMemberResponse resp = TeamMemberResponse.builder()
                    .id(m.getId())
                    .teamId(m.getTeamId())
                    .userId(m.getUserId())
                    .role(m.getRole())
                    .joinedAt(m.getJoinedAt())
                    .username(username)
                    .email(email)
                    .fullName(fullName != null ? fullName : "Thành viên (" + m.getUserId().toString().substring(0, 8) + ")")
                    .build();

            dedupMap.put(dedupKey, resp);
        }

        return ResponseEntity.ok(ApiResponse.success(new ArrayList<>(dedupMap.values())));
    }

    @Transactional
    @PostMapping("/{teamId}/members")
    @PreAuthorize("@projectSecurity.hasTeamPermission(#teamId, 'MEMBER_INVITE')")
    public ResponseEntity<ApiResponse<TeamMemberResponse>> addMember(
            @PathVariable("teamId") UUID teamId,
            @RequestBody Map<String, String> body
    ) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm: " + teamId));

        UUID currentUserId = SecurityUtils.getCurrentUserId();
        boolean isOwner = isTeamOwner(team, currentUserId);
        boolean isLeader = isOwner || isTeamLeader(teamId, currentUserId);

        if (!isOwner && !isLeader) {
            throw new ForbiddenException("Chỉ Trưởng nhóm (Leader) mới có quyền thêm thành viên vào nhóm");
        }

        String emailOrUser = body.get("email");
        if (emailOrUser == null || emailOrUser.isBlank()) {
            emailOrUser = body.get("username");
        }
        if (emailOrUser == null || emailOrUser.isBlank()) {
            emailOrUser = body.get("userId");
        }

        if (emailOrUser == null || emailOrUser.isBlank()) {
            throw new BadRequestException("Vui lòng nhập Email, Username hoặc User ID của thành viên");
        }

        UUID targetUserId = null;
        UUID dbId = null;
        UUID keycloakId = null;

        try {
            ApiResponse<UserResponse> uResp = identityClient.getUserByIdentifier(emailOrUser);
            if (uResp != null && uResp.getData() != null) {
                UserResponse u = uResp.getData();
                dbId = u.getId();
                if (u.getKeycloakId() != null) {
                    try {
                        keycloakId = UUID.fromString(u.getKeycloakId());
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ex) {
            try {
                targetUserId = UUID.fromString(emailOrUser);
            } catch (Exception notUuid) {
                throw new ResourceNotFoundException("Không tìm thấy người dùng với thông tin: " + emailOrUser);
            }
        }

        // Canonical ID: prioritize Keycloak ID because Spring Security JWT sub uses Keycloak UUID
        if (keycloakId != null) {
            targetUserId = keycloakId;
        } else if (dbId != null) {
            targetUserId = dbId;
        }

        if (targetUserId == null) {
            throw new ResourceNotFoundException("Không tìm thấy người dùng với thông tin: " + emailOrUser);
        }

        TeamRole role = TeamRole.MEMBER;
        String roleStr = body.get("role");
        if (roleStr != null) {
            try {
                role = TeamRole.valueOf(roleStr.toUpperCase());
            } catch (Exception ignored) {}
        }

        // Check if member already exists under targetUserId OR dbId OR keycloakId
        Optional<TeamMember> existing = teamMemberRepository.findByTeamIdAndUserId(teamId, targetUserId);
        if (existing.isEmpty() && dbId != null) {
            existing = teamMemberRepository.findByTeamIdAndUserId(teamId, dbId);
        }
        if (existing.isEmpty() && keycloakId != null) {
            existing = teamMemberRepository.findByTeamIdAndUserId(teamId, keycloakId);
        }

        TeamMember savedMember;
        if (existing.isPresent()) {
            savedMember = existing.get();
            savedMember.setUserId(targetUserId);
            savedMember.setRole(role);
            savedMember = teamMemberRepository.save(savedMember);
        } else {
            TeamMember newMember = TeamMember.builder()
                    .teamId(teamId)
                    .userId(targetUserId)
                    .role(role)
                    .build();
            savedMember = teamMemberRepository.save(newMember);
        }

        // Sync with existing projects of this team
        syncMemberToTeamProjects(teamId, savedMember.getUserId(), savedMember.getRole());

        return ResponseEntity.ok(ApiResponse.success(
                TeamMemberResponse.builder()
                        .id(savedMember.getId())
                        .teamId(savedMember.getTeamId())
                        .userId(savedMember.getUserId())
                        .role(savedMember.getRole())
                        .joinedAt(savedMember.getJoinedAt())
                        .build(),
                "Thêm thành viên thành công"
        ));
    }

    @Transactional
    @PutMapping("/{teamId}/members/{userId}/role")
    @PreAuthorize("@projectSecurity.hasTeamPermission(#teamId, 'MEMBER_ROLE_UPDATE')")
    public ResponseEntity<ApiResponse<TeamMemberResponse>> updateMemberRole(
            @PathVariable("teamId") @P("teamId") UUID teamId,
            @PathVariable("userId") UUID userId,
            @RequestBody Map<String, String> body
    ) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm: " + teamId));
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        boolean isLeader = isTeamLeader(teamId, currentUserId);

        if (!isLeader) {
            throw new ForbiddenException("Chỉ Trưởng nhóm (Leader) mới có quyền thay đổi vai trò thành viên");
        }

        if (isSameUser(currentUserId, userId)) {
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

        // Look up member with alias support
        Set<UUID> aliasIds = getUserAliasIds(userId);
        TeamMember member = null;
        for (UUID uid : aliasIds) {
            Optional<TeamMember> opt = teamMemberRepository.findByTeamIdAndUserId(teamId, uid);
            if (opt.isPresent()) {
                member = opt.get();
                break;
            }
        }

        if (member == null) {
            throw new ResourceNotFoundException("Không tìm thấy thành viên này trong nhóm");
        }

        member.setRole(newRole);
        member = teamMemberRepository.save(member);

        // Sync updated role to team projects
        syncMemberToTeamProjects(teamId, member.getUserId(), member.getRole());

        return ResponseEntity.ok(ApiResponse.success(
                TeamMemberResponse.builder()
                        .id(member.getId())
                        .teamId(member.getTeamId())
                        .userId(member.getUserId())
                        .role(member.getRole())
                        .joinedAt(member.getJoinedAt())
                        .build(),
                "Cập nhật vai trò thành công"
        ));
    }

    @Transactional
    @DeleteMapping("/{teamId}/members/{userId}")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable("teamId") UUID teamId,
            @PathVariable("userId") UUID userId
    ) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm: " + teamId));

        UUID currentUserId = SecurityUtils.getCurrentUserId();

        // 1. Không cho phép tự xóa chính mình khỏi nhóm qua API xóa thành viên (dùng API rời nhóm)
        if (isSameUser(currentUserId, userId)) {
            throw new BadRequestException("Bạn không thể tự xóa chính mình khỏi nhóm (vui lòng sử dụng tính năng Rời nhóm)");
        }

        // 2. Chỉ Leader hoặc Owner của nhóm mới có quyền xóa thành viên
        boolean isOwner = isTeamOwner(team, currentUserId);
        boolean isLeader = isOwner || isTeamLeader(teamId, currentUserId);

        if (!isOwner && !isLeader) {
            throw new ForbiddenException("Chỉ Trưởng nhóm (Leader) mới có quyền xóa thành viên khỏi nhóm");
        }

        // 3. Không cho phép xóa Chủ sở hữu nhóm
        if (isTeamOwner(team, userId)) {
            throw new BadRequestException("Không thể xóa Trưởng nhóm / Chủ sở hữu nhóm");
        }

        // 4. Tìm kiếm thành viên với alias ID
        Set<UUID> aliasIds = getUserAliasIds(userId);
        TeamMember targetMember = null;
        for (UUID uid : aliasIds) {
            Optional<TeamMember> opt = teamMemberRepository.findByTeamIdAndUserId(teamId, uid);
            if (opt.isPresent()) {
                targetMember = opt.get();
                break;
            }
        }

        if (targetMember == null) {
            throw new ResourceNotFoundException("Không tìm thấy thành viên này trong nhóm");
        }

        teamMemberRepository.delete(targetMember);
        removeMemberFromTeamProjects(teamId, targetMember.getUserId());

        return ResponseEntity.ok(ApiResponse.success(null, "Đã xóa thành viên khỏi nhóm"));
    }

    // ─── Helper Methods ──────────────────────────────────────────────────────────

    private void syncMemberToTeamProjects(UUID teamId, UUID userId, TeamRole teamRole) {
        if (teamId == null || userId == null || teamRole == null) return;
        List<Project> projects = projectRepository.findByTeamId(teamId);
        if (projects.isEmpty()) return;

        for (Project project : projects) {
            List<ProjectRole> roles = projectRoleRepository.findByProjectId(project.getId());
            if (roles.isEmpty()) continue;

            ProjectRole adminRole = roles.stream().filter(r -> "ADMINISTRATOR".equalsIgnoreCase(r.getName())).findFirst().orElse(null);
            ProjectRole memberRole = roles.stream().filter(r -> "MEMBER".equalsIgnoreCase(r.getName())).findFirst().orElse(null);
            ProjectRole viewerRole = roles.stream().filter(r -> "VIEWER".equalsIgnoreCase(r.getName())).findFirst().orElse(null);

            ProjectRole targetRole = memberRole;
            if (teamRole == TeamRole.LEADER) {
                targetRole = adminRole;
            } else if (teamRole == TeamRole.VIEWER) {
                targetRole = viewerRole;
            }

            if (targetRole == null) continue;

            Set<UUID> aliasIds = getUserAliasIds(userId);
            List<RoleAssignment> existing = new ArrayList<>();
            for (UUID uid : aliasIds) {
                existing.addAll(roleAssignmentRepository.findByProjectIdAndUserId(project.getId(), uid));
            }

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
        List<Project> projects = projectRepository.findByTeamId(teamId);
        if (projects.isEmpty()) return;

        Set<UUID> aliasIds = getUserAliasIds(userId);
        for (Project project : projects) {
            for (UUID uid : aliasIds) {
                roleAssignmentRepository.deleteByProjectIdAndUserId(project.getId(), uid);
            }
        }
    }

    private Set<UUID> getUserAliasIds(UUID userId) {
        Set<UUID> ids = new HashSet<>();
        if (userId != null) {
            ids.add(userId);
            try {
                ApiResponse<UserResponse> resp = identityClient.getUserById(userId);
                if (resp != null && resp.getData() != null) {
                    UserResponse u = resp.getData();
                    if (u.getId() != null) ids.add(u.getId());
                    if (u.getKeycloakId() != null) {
                        try {
                            ids.add(UUID.fromString(u.getKeycloakId()));
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return ids;
    }

    private boolean isSameUser(UUID userA, UUID userB) {
        if (userA == null || userB == null) return false;
        if (userA.equals(userB)) return true;
        Set<UUID> aliasA = getUserAliasIds(userA);
        return aliasA.contains(userB);
    }

    private boolean isTeamOwner(Team team, UUID userId) {
        if (team == null || team.getOwnerId() == null || userId == null) return false;
        return isSameUser(team.getOwnerId(), userId);
    }

    private boolean isTeamLeader(UUID teamId, UUID userId) {
        if (teamId == null || userId == null) return false;
        Set<UUID> aliases = getUserAliasIds(userId);
        for (UUID uid : aliases) {
            Optional<TeamMember> member = teamMemberRepository.findByTeamIdAndUserId(teamId, uid);
            if (member.isPresent() && member.get().getRole() == TeamRole.LEADER) {
                return true;
            }
        }
        return false;
    }
}
