package com.datn.workservice.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ForbiddenException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workservice.client.IdentityClient;
import com.datn.workservice.client.WorkspaceClient;
import com.datn.workservice.client.dto.UserResponse;
import com.datn.workservice.dto.request.AssignTeamLeaderRequest;
import com.datn.workservice.dto.request.CreateTeamRequest;
import com.datn.workservice.dto.response.TeamMemberResponse;
import com.datn.workservice.dto.response.InvitationResponse;
import com.datn.workservice.dto.response.TeamResponse;
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
import com.datn.workservice.security.*;
import com.datn.workservice.service.InvitationService;
import com.datn.workservice.service.ProjectMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Quản lý Team. Phân quyền thống nhất bằng AOP (@RequireTeamRole, @RequireWorkspaceAccess, @RequireGlobalAdmin):
 * <ul>
 *   <li>Xem team / thành viên: thành viên hoặc owner của team (hoặc Global ADMIN)</li>
 *   <li>Sửa team, thêm/xóa/đổi vai trò thành viên: LEADER hoặc owner</li>
 *   <li>Xóa team: LEADER hoặc owner</li>
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
    private final InvitationService invitationService;
    private final ProjectMemberService projectMemberService;
    private final TeamSecurity teamSecurity;
    private final WorkspaceAccessChecker workspaceAccessChecker;

    /**
     * Danh sách Team TRONG MỘT workspace.
     * <p>
     * Phân quyền AOP: người gọi phải là thành viên của workspace (GUEST trở lên).
     */
    @GetMapping
    @RequireWorkspaceAccess("GUEST")
    public ResponseEntity<ApiResponse<List<Team>>> listTeamsInWorkspace(
            @RequestParam("workspaceId") UUID workspaceId) {
        return ResponseEntity.ok(ApiResponse.success(teamRepository.findByWorkspaceId(workspaceId)));
    }

    /**
     * Danh sách tất cả Team trong hệ thống, bỏ qua ranh giới workspace.
     * Chỉ Global ADMIN mới gọi được - dùng cho màn hình quản trị.
     */
    @GetMapping("/all")
    @RequireGlobalAdmin
    public ResponseEntity<ApiResponse<List<Team>>> listAllTeams() {
        return ResponseEntity.ok(ApiResponse.success(teamRepository.findAll()));
    }

    /**
     * Danh sách Team mà người dùng hiện tại tham gia (Owner hoặc Member).
     */
    @GetMapping({"/my", "/me"})
    @RequireWorkspaceAccess(value = "GUEST", required = false)
    public ResponseEntity<ApiResponse<List<TeamResponse>>> listMyTeams(
            @RequestParam(value = "workspaceId", required = false) UUID workspaceId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();

        // Nếu user là Workspace Owner hoặc Workspace Admin của workspace được chỉ định: Trả về toàn bộ Team thuộc Workspace
        if (workspaceId != null) {
            try {
                String wsRole = workspaceAccessChecker.getRole(workspaceId, currentUserId);
                if ("OWNER".equalsIgnoreCase(wsRole) || "ADMIN".equalsIgnoreCase(wsRole)) {
                    List<TeamResponse> allWsTeams = teamRepository.findByWorkspaceId(workspaceId).stream()
                            .map(t -> TeamResponse.builder()
                                    .id(t.getId())
                                    .workspaceId(t.getWorkspaceId())
                                    .name(t.getName())
                                    .description(t.getDescription())
                                    .ownerId(t.getOwnerId())
                                    .createdAt(t.getCreatedAt())
                                    .updatedAt(t.getUpdatedAt())
                                    .myRole(TeamRole.LEADER)
                                    .build())
                            .toList();
                    return ResponseEntity.ok(ApiResponse.success(allWsTeams));
                }
            } catch (Exception ignored) {
            }
        }

        UUID canonicalCurrentId = canonicalUserId(currentUserId);
        Map<UUID, TeamRole> roleByTeam = new HashMap<>();
        teamMemberRepository.findByUserId(currentUserId).forEach(m -> roleByTeam.put(m.getTeamId(), m.getRole()));
        if (canonicalCurrentId != null && !canonicalCurrentId.equals(currentUserId)) {
            teamMemberRepository.findByUserId(canonicalCurrentId).forEach(m -> roleByTeam.put(m.getTeamId(), m.getRole()));
        }
        teamRepository.findByOwnerId(currentUserId).forEach(t -> roleByTeam.put(t.getId(), TeamRole.LEADER));
        if (canonicalCurrentId != null && !canonicalCurrentId.equals(currentUserId)) {
            teamRepository.findByOwnerId(canonicalCurrentId).forEach(t -> roleByTeam.put(t.getId(), TeamRole.LEADER));
        }

        if (roleByTeam.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(Collections.emptyList()));
        }

        // Kèm myRole để frontend biết user có phải trưởng nhóm không (chỉ trưởng nhóm được tạo dự án)
        List<TeamResponse> result = teamRepository.findByIdIn(roleByTeam.keySet()).stream()
                .filter(t -> workspaceId == null || workspaceId.equals(t.getWorkspaceId()))
                .map(t -> TeamResponse.builder()
                        .id(t.getId())
                        .workspaceId(t.getWorkspaceId())
                        .name(t.getName())
                        .description(t.getDescription())
                        .ownerId(t.getOwnerId())
                        .createdAt(t.getCreatedAt())
                        .updatedAt(t.getUpdatedAt())
                        .myRole(currentUserId.equals(t.getOwnerId()) ? TeamRole.LEADER : roleByTeam.get(t.getId()))
                        .build())
                .toList();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{teamId}")
    @RequireTeamRole(TeamRole.MEMBER)
    public ResponseEntity<ApiResponse<Team>> getTeam(@PathVariable("teamId") UUID teamId) {
        Team team = requireTeam(teamId);
        return ResponseEntity.ok(ApiResponse.success(team));
    }

    /**
     * Tạo Team trong một workspace.
     * Phân quyền AOP: người gọi phải có vai trò MEMBER trở lên trong workspace.
     */
    @Transactional
    @PostMapping
    @RequireWorkspaceAccess("MEMBER")
    public ResponseEntity<ApiResponse<Team>> createTeam(@Valid @RequestBody CreateTeamRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        // Bắt buộc người tạo phải là thành viên hợp lệ của Workspace (kể cả Global Admin)
        workspaceAccessChecker.requireMember(request.getWorkspaceId(), currentUserId);

        Team team = Team.builder()
                .name(request.getName())
                .description(request.getDescription())
                .ownerId(currentUserId)
                .workspaceId(request.getWorkspaceId())
                .build();
        Team saved = teamRepository.save(team);

        // Người tạo là LEADER.
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
    public ResponseEntity<ApiResponse<Team>> updateTeam(
            @PathVariable("teamId") UUID teamId,
            @RequestBody Map<String, String> body
    ) {
        if (!teamSecurity.canManageTeam(teamId)) {
            throw new AccessDeniedException("Bạn không có quyền chỉnh sửa thông tin nhóm");
        }
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
     * Bổ nhiệm Trưởng nhóm mới cho Team.
     * Thẩm quyền: Chỉ Workspace Owner hoặc Workspace Admin (hoặc Global Admin).
     */
    @Transactional
    @PutMapping("/{teamId}/leader")
    public ResponseEntity<ApiResponse<TeamResponse>> assignTeamLeader(
            @PathVariable("teamId") UUID teamId,
            @Valid @RequestBody AssignTeamLeaderRequest request
    ) {
        Team team = requireTeam(teamId);
        UUID currentUserId = SecurityUtils.getCurrentUserId();

        // 1. Phải là Workspace Owner hoặc Workspace Admin hoặc Global Admin
        if (!SecurityUtils.isGlobalAdmin() && !teamSecurity.isWorkspaceAdminOrOwner(team, currentUserId)) {
            throw new AccessDeniedException("Chỉ Quản trị viên hoặc Chủ sở hữu Workspace mới có quyền bổ nhiệm Trưởng nhóm");
        }

        UUID newLeaderUserId = canonicalUserId(request.getNewLeaderUserId());
        if (newLeaderUserId == null) {
            throw new BadRequestException("Người dùng được chỉ định không hợp lệ");
        }

        // 2. Người được chỉ định phải thuộc Workspace này
        workspaceAccessChecker.requireMember(team.getWorkspaceId(), newLeaderUserId);

        UUID oldLeaderId = team.getOwnerId();

        // 3. Nếu người mới khác người cũ:
        if (oldLeaderId != null && !oldLeaderId.equals(newLeaderUserId)) {
            // Hạ Leader cũ xuống làm MEMBER
            teamMemberRepository.findByTeamIdAndUserId(teamId, oldLeaderId)
                    .ifPresent(oldMember -> {
                        oldMember.setRole(TeamRole.MEMBER);
                        teamMemberRepository.save(oldMember);
                    });
            // Đồng bộ sang các Project thuộc Team: role hạ xuống MEMBER
            syncMemberToTeamProjects(teamId, oldLeaderId, TeamRole.MEMBER);
        }

        // 4. Gán hoặc thêm Leader mới với role LEADER
        TeamMember newMember = teamMemberRepository.findByTeamIdAndUserId(teamId, newLeaderUserId)
                .orElseGet(() -> TeamMember.builder()
                        .teamId(teamId)
                        .userId(newLeaderUserId)
                        .build());
        newMember.setRole(TeamRole.LEADER);
        teamMemberRepository.save(newMember);

        // 5. Cập nhật teams.owner_id trỏ tới Active Leader mới
        team.setOwnerId(newLeaderUserId);
        Team saved = teamRepository.save(team);

        // 6. Đồng bộ sang các Project thuộc Team: role nâng lên ADMINISTRATOR
        syncMemberToTeamProjects(teamId, newLeaderUserId, TeamRole.LEADER);

        log.info("Team {} changed leader from {} to {} by user {}", teamId, oldLeaderId, newLeaderUserId, currentUserId);

        TeamResponse resp = TeamResponse.builder()
                .id(saved.getId())
                .workspaceId(saved.getWorkspaceId())
                .name(saved.getName())
                .description(saved.getDescription())
                .ownerId(saved.getOwnerId())
                .createdAt(saved.getCreatedAt())
                .updatedAt(saved.getUpdatedAt())
                .myRole(currentUserId.equals(saved.getOwnerId()) ? TeamRole.LEADER : TeamRole.MEMBER)
                .build();

        return ResponseEntity.ok(ApiResponse.success(resp, "Bổ nhiệm Trưởng nhóm thành công"));
    }

    /**
     * Xóa nhóm: Trưởng nhóm, Workspace Owner, Workspace Admin, hoặc Global ADMIN.
     */
    @Transactional
    @DeleteMapping("/{teamId}")
    public ResponseEntity<ApiResponse<Void>> deleteTeam(@PathVariable("teamId") UUID teamId) {
        if (!teamSecurity.canManageTeam(teamId)) {
            throw new AccessDeniedException("Bạn không có quyền xóa nhóm");
        }
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
    @RequireTeamRole(TeamRole.MEMBER)
    public ResponseEntity<ApiResponse<List<TeamMemberResponse>>> getTeamMembers(
            @PathVariable("teamId") UUID teamId
    ) {
        Team team = requireTeam(teamId);
        List<TeamMember> members = new ArrayList<>(teamMemberRepository.findByTeamId(teamId));

        // Kiểm tra xem ownerId có thực sự là thành viên của Workspace này không
        boolean isOwnerInWorkspace = false;
        if (team.getOwnerId() != null && team.getWorkspaceId() != null) {
            try {
                isOwnerInWorkspace = workspaceAccessChecker.getRole(team.getWorkspaceId(), team.getOwnerId()) != null;
            } catch (Exception ignored) {
            }
        }

        // CHỈ hiển thị ownerId là LEADER nếu người đó THỰC SỰ ĐANG LÀ THÀNH VIÊN CỦA WORKSPACE!
        if (team.getOwnerId() != null && isOwnerInWorkspace) {
            if (members.stream().noneMatch(m -> team.getOwnerId().equals(m.getUserId()))) {
                members.add(0, TeamMember.builder()
                        .teamId(teamId)
                        .userId(team.getOwnerId())
                        .role(TeamRole.LEADER)
                        .joinedAt(team.getCreatedAt())
                        .build());
            }
        } else if (team.getOwnerId() != null && !isOwnerInWorkspace) {
            // Owner cũ đã không còn trong Workspace -> Tự động dọn dẹp khỏi team_members nếu còn sót
            teamMemberRepository.findByTeamIdAndUserId(teamId, team.getOwnerId())
                    .ifPresent(teamMemberRepository::delete);
            log.warn("Team {} có ownerId {} không còn trong workspace {}. Đã thu hồi quyền hiển thị Leader.",
                    teamId, team.getOwnerId(), team.getWorkspaceId());
        }

        List<UUID> userIds = members.stream().map(TeamMember::getUserId).toList();
        Map<UUID, UserResponse> userMap = fetchUsers(userIds);

        // Gộp theo người dùng để tránh trùng lặp khi dữ liệu cũ tồn tại cả KeycloakId và DbId
        Map<String, TeamMemberResponse> dedupMap = new LinkedHashMap<>();
        for (TeamMember m : members) {
            // Bỏ qua thành viên nếu đã không còn thuộc Workspace
            if (team.getWorkspaceId() != null) {
                boolean inWs = false;
                try {
                    inWs = workspaceAccessChecker.getRole(team.getWorkspaceId(), m.getUserId()) != null;
                } catch (Exception ignored) {
                }
                if (!inWs) {
                    teamMemberRepository.delete(m);
                    removeMemberFromTeamProjects(teamId, m.getUserId());
                    continue;
                }
            }
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
                // Giữ vai trò LEADER nếu một trong hai bản ghi là LEADER
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
    public ResponseEntity<ApiResponse<InvitationResponse>> addMember(
            @PathVariable("teamId") UUID teamId,
            @RequestBody Map<String, String> body
    ) {
        if (!teamSecurity.canManageTeam(teamId)) {
            throw new AccessDeniedException("Bạn không có quyền mời thành viên vào nhóm này");
        }
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

        // Không thêm trực tiếp: gửi lời mời, người được mời chấp nhận mới thành thành viên (InvitationService.accept)
        InvitationResponse invitation = invitationService.inviteToTeam(teamId, targetUserId, role, SecurityUtils.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(invitation, "Đã gửi lời mời tham gia nhóm"));
    }

    @Transactional
    @PutMapping("/{teamId}/members/{userId}/role")
    public ResponseEntity<ApiResponse<TeamMemberResponse>> updateMemberRole(
            @PathVariable("teamId") UUID teamId,
            @PathVariable("userId") UUID userId,
            @RequestBody Map<String, String> body
    ) {
        if (!teamSecurity.canManageTeam(teamId)) {
            throw new AccessDeniedException("Bạn không có quyền thay đổi vai trò thành viên của nhóm");
        }
        Team team = requireTeam(teamId);

        if (getValidTeamMemberUserIds(team).size() <= 1) {
            throw new BadRequestException("Không thể thay đổi vai trò khi nhóm chỉ có 1 thành viên duy nhất");
        }

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
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable("teamId") UUID teamId,
            @PathVariable("userId") UUID userId
    ) {
        if (!teamSecurity.canManageTeam(teamId)) {
            throw new AccessDeniedException("Bạn không có quyền xóa thành viên khỏi nhóm");
        }
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

    /**
     * Lấy tập hợp UUID (canonical) của tất cả thành viên hợp lệ hiện tại trong Team.
     * Tự động tính cả Trưởng nhóm (ownerId) nếu còn thuộc Workspace và loại bỏ thành viên đã rời Workspace.
     */
    private Set<UUID> getValidTeamMemberUserIds(Team team) {
        Set<UUID> memberUserIds = new HashSet<>();
        if (team == null) return memberUserIds;

        UUID teamId = team.getId();
        UUID workspaceId = team.getWorkspaceId();

        // 1. Kiểm tra ownerId
        if (team.getOwnerId() != null) {
            boolean isOwnerInWs = true;
            if (workspaceId != null) {
                try {
                    isOwnerInWs = workspaceAccessChecker.getRole(workspaceId, team.getOwnerId()) != null;
                } catch (Exception e) {
                    isOwnerInWs = false;
                }
            }
            if (isOwnerInWs) {
                UUID canonicalOwnerId = canonicalUserId(team.getOwnerId());
                if (canonicalOwnerId != null) {
                    memberUserIds.add(canonicalOwnerId);
                }
            }
        }

        // 2. Kiểm tra các thành viên trong teamMemberRepository
        List<TeamMember> members = teamMemberRepository.findByTeamId(teamId);
        for (TeamMember m : members) {
            if (workspaceId != null) {
                boolean inWs = false;
                try {
                    inWs = workspaceAccessChecker.getRole(workspaceId, m.getUserId()) != null;
                } catch (Exception ignored) {
                }
                if (!inWs) {
                    continue;
                }
            }
            UUID canonicalId = canonicalUserId(m.getUserId());
            if (canonicalId != null) {
                memberUserIds.add(canonicalId);
            }
        }

        return memberUserIds;
    }

    private Optional<TeamMember> findMember(UUID teamId, UUID rawUserId, UUID canonicalUserId) {
        Optional<TeamMember> member = teamMemberRepository.findByTeamIdAndUserId(teamId, canonicalUserId);
        if (member.isEmpty() && rawUserId != null && !rawUserId.equals(canonicalUserId)) {
            member = teamMemberRepository.findByTeamIdAndUserId(teamId, rawUserId);
        }
        return member;
    }

    private void syncMemberToTeamProjects(UUID teamId, UUID userId, TeamRole teamRole) {
        projectMemberService.syncTeamMemberToProjects(teamId, userId, teamRole);
    }

    private void removeMemberFromTeamProjects(UUID teamId, UUID userId) {
        if (teamId == null || userId == null) return;
        for (Project project : projectRepository.findByTeamId(teamId)) {
            // Project Lead giữ quyền quản trị ngầm định, không cần xóa assignment
            if (userId.equals(project.getLeadUserId())) continue;
            roleAssignmentRepository.deleteByProjectIdAndUserId(project.getId(), userId);
        }
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
