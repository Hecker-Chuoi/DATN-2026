package com.datn.workservice.dto.response;

import com.datn.workservice.entity.Team;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamResponse {

    private UUID id;
    private String name;
    private String key;
    private String description;
    private UUID ownerId;
    private String ownerName;
    private int memberCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static TeamResponse from(Team team) {
        return TeamResponse.builder()
                .id(team.getId())
                .name(team.getName())
                .key(team.getKey())
                .description(team.getDescription())
                .ownerId(team.getOwnerId())
                .createdAt(team.getCreatedAt())
                .updatedAt(team.getUpdatedAt())
                .build();
    }
}
