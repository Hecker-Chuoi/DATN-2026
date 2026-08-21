package com.datn.workservice.service;

import com.datn.workservice.dto.request.AddTeamMemberRequest;
import com.datn.workservice.dto.request.CreateTeamRequest;
import com.datn.workservice.dto.response.TeamMemberResponse;
import com.datn.workservice.dto.response.TeamResponse;

import java.util.List;
import java.util.UUID;

public interface TeamService {

    TeamResponse createTeam(CreateTeamRequest request, UUID currentUserId);

    List<TeamResponse> getAllTeams();

    TeamResponse getTeamById(UUID teamId);

    TeamMemberResponse addMember(UUID teamId, AddTeamMemberRequest request);

    TeamMemberResponse updateMemberRole(UUID teamId, UUID targetUserId, com.datn.workservice.dto.request.UpdateTeamMemberRoleRequest request, UUID currentUserId);

    void removeMember(UUID teamId, UUID userId);

    List<TeamMemberResponse> getTeamMembers(UUID teamId);

    String getUserRoleInTeam(UUID teamId, UUID userId);
}
