package com.datn.workservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamDetailResponse {

    private UUID id;
    private UUID workspaceId;
    private String name;
    private String description;
    private UUID ownerId;
    private List<TeamMemberResponse> members;
    private Long projectCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
