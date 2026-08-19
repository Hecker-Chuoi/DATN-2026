package com.datn.workservice.service.impl;

import com.datn.common.dto.ApiResponse;
import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ForbiddenException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workservice.client.IdentityClient;
import com.datn.workservice.client.dto.UserResponse;
import com.datn.workservice.dto.request.AddTeamMemberRequest;
import com.datn.workservice.dto.request.CreateTeamRequest;
import com.datn.workservice.dto.request.UpdateTeamMemberRoleRequest;
import com.datn.workservice.dto.response.TeamMemberResponse;
import com.datn.workservice.dto.response.TeamResponse;
import com.datn.workservice.entity.Team;
import com.datn.workservice.entity.TeamMember;
import com.datn.workservice.enums.TeamRole;
import com.datn.workservice.repository.TeamMemberRepository;
import com.datn.workservice.repository.TeamRepository;
import com.datn.workservice.service.TeamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamServiceImpl implements TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final IdentityClient identityClient;
    private final com.datn.workservice.service.TaskAsyncCleanupService taskAsyncCleanupService;

    @Override
    @Transactional
    public TeamResponse createTeam(CreateTeamRequest request, UUID currentUserId) {
        UUID owner = request.getOwnerId() != null ? request.getOwnerId() : currentUserId;
        if (owner == null) {
            owner = UUID.randomUUID(); // Fallback nếu chưa đăng nhập khi test
        }

        Team team = Team.builder()
                .name(request.getName().trim())
                .key(request.getKey() != null ? request.getKey().trim().toUpperCase() : null)
                .description(request.getDescription())
                .ownerId(owner)
                .build();

        team = teamRepository.save(team);

        // Tự động gán Owner là Leader của Team
        TeamMember leader = TeamMember.builder()
                .id(new TeamMember.TeamMemberId(team.getId(), owner))
                .roleInTeam(TeamRole.TEAM_LEAD.name())
                .joinedAt(LocalDateTime.now())
                .build();
        teamMemberRepository.save(leader);

        TeamResponse response = TeamResponse.from(team);
        response.setMemberCount(1);
        return response;
    }

    @Override
    public List<TeamResponse> getAllTeams() {
        List<Team> teams = teamRepository.findAll();
        if (teams.isEmpty()) {
            return Collections.emptyList();
        }

        return teams.stream().map(team -> {
            TeamResponse res = TeamResponse.from(team);
            List<TeamMember> members = teamMemberRepository.findByIdTeamId(team.getId());
            res.setMemberCount(members.size());
            return res;
        }).collect(Collectors.toList());
    }

    @Override
    public TeamResponse getTeamById(UUID teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found with id: " + teamId));

        TeamResponse res = TeamResponse.from(team);
        List<TeamMember> members = teamMemberRepository.findByIdTeamId(team.getId());
        res.setMemberCount(members.size());
        return res;
    }

    @Override
    @Transactional
    public TeamMemberResponse addMember(UUID teamId, AddTeamMemberRequest request) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found with id: " + teamId));

        UUID targetUserId = request.getUserId();
        UserResponse userResponse = null;

        // Nếu chưa có userId nhưng có emailOrUsername / email / username, tra cứu qua Identity Service
        String identifier = null;
        if (request.getEmailOrUsername() != null && !request.getEmailOrUsername().isBlank()) {
            identifier = request.getEmailOrUsername().trim();
        } else if (request.getEmail() != null && !request.getEmail().isBlank()) {
            identifier = request.getEmail().trim();
        } else if (request.getUsername() != null && !request.getUsername().isBlank()) {
            identifier = request.getUsername().trim();
        }

        if (targetUserId == null) {
            if (identifier == null || identifier.isBlank()) {
                throw new BadRequestException("Vui lòng cung cấp Email, Username hoặc User ID");
            }
            try {
                ApiResponse<UserResponse> userApi = identityClient.getUserByIdentifier(identifier);
                if (userApi != null && userApi.getData() != null) {
                    userResponse = userApi.getData();
                    targetUserId = userResponse.getId();
                } else {
                    throw new ResourceNotFoundException("Không tìm thấy người dùng với thông tin: " + identifier);
                }
            } catch (ResourceNotFoundException e) {
                throw e;
            } catch (Exception e) {
                log.error("Error looking up user by identifier '{}': {}", identifier, e.getMessage());
                throw new BadRequestException("Không thể tìm thấy tài khoản người dùng: " + identifier);
            }
        }

        if (targetUserId == null) {
            throw new BadRequestException("Không xác định được người dùng cần thêm vào nhóm");
        }

        TeamMember.TeamMemberId memberId = new TeamMember.TeamMemberId(teamId, targetUserId);
        if (teamMemberRepository.existsById(memberId)) {
            throw new BadRequestException("Thành viên đã tồn tại trong nhóm này");
        }

        TeamRole initialRole = TeamRole.fromString(request.getRoleInTeam());

        TeamMember member = TeamMember.builder()
                .id(memberId)
                .roleInTeam(initialRole.name())
                .joinedAt(LocalDateTime.now())
                .build();

        member = teamMemberRepository.save(member);

        // Nếu chưa có userResponse thì lấy từ Identity Service
        if (userResponse == null) {
            try {
                ApiResponse<UserResponse> userApi = identityClient.getUserById(targetUserId);
                if (userApi != null && userApi.getData() != null) {
                    userResponse = userApi.getData();
                }
            } catch (Exception e) {
                log.warn("Could not fetch user details from identity-service for userId={}: {}", targetUserId, e.getMessage());
            }
        }

        return TeamMemberResponse.builder()
                .teamId(teamId)
                .userId(targetUserId)
                .username(userResponse != null ? userResponse.getUsername() : null)
                .fullName(userResponse != null ? userResponse.getFullName() : null)
                .email(userResponse != null ? userResponse.getEmail() : null)
                .roleInTeam(member.getRoleInTeam())
                .joinedAt(member.getJoinedAt())
                .build();
    }

    @Override
    @Transactional
    public void removeMember(UUID teamId, UUID userId) {
        TeamMember.TeamMemberId memberId = new TeamMember.TeamMemberId(teamId, userId);
        if (!teamMemberRepository.existsById(memberId)) {
            throw new ResourceNotFoundException("Thành viên không tồn tại trong nhóm này");
        }
        teamMemberRepository.deleteById(memberId);

        // Chạy ngầm hủy gán (Unassign) tất cả các task của thành viên này trong Team
        try {
            taskAsyncCleanupService.unassignTasksForRemovedMember(teamId, userId);
        } catch (Exception e) {
            log.warn("Could not dispatch async task unassignment for member {} in team {}: {}", userId, teamId, e.getMessage());
        }
    }

    @Override
    public List<TeamMemberResponse> getTeamMembers(UUID teamId) {
        if (!teamRepository.existsById(teamId)) {
            throw new ResourceNotFoundException("Team not found with id: " + teamId);
        }

        List<TeamMember> members = teamMemberRepository.findByIdTeamId(teamId);
        if (members.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> userIds = members.stream()
                .map(m -> m.getId().getUserId())
                .collect(Collectors.toList());

        Map<UUID, UserResponse> userMap = new HashMap<>();
        try {
            ApiResponse<List<UserResponse>> usersApi = identityClient.getUsersByIds(userIds);
            if (usersApi != null && usersApi.getData() != null) {
                for (UserResponse u : usersApi.getData()) {
                    userMap.put(u.getId(), u);
                }
            }
        } catch (Exception e) {
            log.warn("Batch fetch users failed from identity-service: {}", e.getMessage());
        }

        return members.stream().map(m -> {
            UUID uId = m.getId().getUserId();
            UserResponse user = userMap.get(uId);
            return TeamMemberResponse.builder()
                    .teamId(teamId)
                    .userId(uId)
                    .username(user != null ? user.getUsername() : null)
                    .fullName(user != null ? user.getFullName() : null)
                    .email(user != null ? user.getEmail() : null)
                    .roleInTeam(m.getRoleInTeam())
                    .joinedAt(m.getJoinedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public TeamMemberResponse updateMemberRole(UUID teamId, UUID targetUserId, UpdateTeamMemberRoleRequest request, UUID currentUserId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found with id: " + teamId));

        // Kiểm tra phân quyền: Chỉ TEAM_LEAD hoặc Team Owner mới có quyền thay đổi quyền thành viên
        if (currentUserId != null && !currentUserId.equals(team.getOwnerId())) {
            TeamMember currentMember = teamMemberRepository.findById(new TeamMember.TeamMemberId(teamId, currentUserId))
                    .orElseThrow(() -> new ForbiddenException("Bạn không phải là thành viên của nhóm này"));

            if (TeamRole.fromString(currentMember.getRoleInTeam()) != TeamRole.TEAM_LEAD) {
                throw new ForbiddenException("Chỉ Trưởng nhóm (TEAM_LEAD) mới có quyền chỉnh sửa quyền của thành viên trong nhóm");
            }
        }

        TeamMember.TeamMemberId memberId = new TeamMember.TeamMemberId(teamId, targetUserId);
        TeamMember targetMember = teamMemberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Thành viên không tồn tại trong nhóm này"));

        TeamRole newRole = TeamRole.fromString(request.getRoleInTeam());
        targetMember.setRoleInTeam(newRole.name());
        targetMember = teamMemberRepository.save(targetMember);

        // Fetch thông tin user để trả về Response
        String username = null;
        String fullName = null;
        String email = null;
        try {
            ApiResponse<UserResponse> userApi = identityClient.getUserById(targetUserId);
            if (userApi != null && userApi.getData() != null) {
                UserResponse u = userApi.getData();
                username = u.getUsername();
                fullName = u.getFullName();
                email = u.getEmail();
            }
        } catch (Exception e) {
            log.warn("Could not fetch user details from identity-service for userId={}: {}", targetUserId, e.getMessage());
        }

        return TeamMemberResponse.builder()
                .teamId(teamId)
                .userId(targetUserId)
                .username(username)
                .fullName(fullName)
                .email(email)
                .roleInTeam(targetMember.getRoleInTeam())
                .joinedAt(targetMember.getJoinedAt())
                .build();
    }

    @Override
    public String getUserRoleInTeam(UUID teamId, UUID userId) {
        if (userId == null) return null;
        Team team = teamRepository.findById(teamId).orElse(null);
        if (team != null && userId.equals(team.getOwnerId())) {
            return TeamRole.TEAM_LEAD.name();
        }
        return teamMemberRepository.findById(new TeamMember.TeamMemberId(teamId, userId))
                .map(m -> TeamRole.fromString(m.getRoleInTeam()).name())
                .orElse(null);
    }
}
