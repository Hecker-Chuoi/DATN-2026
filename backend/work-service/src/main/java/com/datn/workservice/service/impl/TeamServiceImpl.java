package com.datn.workservice.service.impl;

import com.datn.common.dto.ApiResponse;
import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workservice.client.IdentityClient;
import com.datn.workservice.client.dto.UserResponse;
import com.datn.workservice.dto.request.AddTeamMemberRequest;
import com.datn.workservice.dto.request.CreateTeamRequest;
import com.datn.workservice.dto.response.TeamMemberResponse;
import com.datn.workservice.dto.response.TeamResponse;
import com.datn.workservice.entity.Team;
import com.datn.workservice.entity.TeamMember;
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
                .roleInTeam("leader")
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

        TeamMember.TeamMemberId memberId = new TeamMember.TeamMemberId(teamId, request.getUserId());
        if (teamMemberRepository.existsById(memberId)) {
            throw new BadRequestException("Thành viên đã tồn tại trong nhóm này");
        }

        TeamMember member = TeamMember.builder()
                .id(memberId)
                .roleInTeam(request.getRoleInTeam() != null ? request.getRoleInTeam() : "member")
                .joinedAt(LocalDateTime.now())
                .build();

        member = teamMemberRepository.save(member);

        // Lấy thông tin user từ Identity Service
        String username = null;
        String fullName = null;
        String email = null;
        try {
            ApiResponse<UserResponse> userApi = identityClient.getUserById(request.getUserId());
            if (userApi != null && userApi.getData() != null) {
                UserResponse user = userApi.getData();
                username = user.getUsername();
                fullName = user.getFullName();
                email = user.getEmail();
            }
        } catch (Exception e) {
            log.warn("Could not fetch user details from identity-service for userId={}: {}", request.getUserId(), e.getMessage());
        }

        return TeamMemberResponse.builder()
                .teamId(teamId)
                .userId(request.getUserId())
                .username(username)
                .fullName(fullName)
                .email(email)
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
}
