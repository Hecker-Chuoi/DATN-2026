package com.datn.workservice.dto.response;

import com.datn.workservice.enums.TeamRole;
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
public class TeamMemberResponse {

    private UUID id;
    private UUID teamId;
    private UUID userId;
    private TeamRole role;
    private LocalDateTime joinedAt;

    private String username;
    private String email;
    private String fullName;
    private String avatarUrl;
}
