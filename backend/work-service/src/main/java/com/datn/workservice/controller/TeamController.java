package com.datn.workservice.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.workservice.entity.Team;
import com.datn.workservice.repository.TeamRepository;
import com.datn.workservice.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Basic Team CRUD controller — enough for FE sidebar to load without 500.
 * Full implementation will be expanded in later steps.
 */
@RestController
@RequestMapping("/api/v1/work/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamRepository teamRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Team>>> listTeams() {
        List<Team> teams = teamRepository.findAll();
        return ResponseEntity.ok(ApiResponse.success(teams));
    }

    @GetMapping("/{teamId}")
    public ResponseEntity<ApiResponse<Team>> getTeam(@PathVariable("teamId") UUID teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nhóm: " + teamId));
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
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(saved, "Tạo nhóm thành công"));
    }

    @GetMapping("/{teamId}/members")
    public ResponseEntity<ApiResponse<List<Object>>> getTeamMembers(@PathVariable("teamId") UUID teamId) {
        // Stub: return empty list until full TeamMember service is implemented
        return ResponseEntity.ok(ApiResponse.success(List.of()));
    }
}
