package com.datn.workservice.dto.response;

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
    private UUID workspaceId;
    private String name;
    private String description;
    private UUID ownerId;
    private Long memberCount;
    private Long projectCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
