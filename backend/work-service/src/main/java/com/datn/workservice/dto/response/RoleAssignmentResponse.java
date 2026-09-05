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
public class RoleAssignmentResponse {

    private UUID id;
    private UUID projectId;
    private UUID userId;
    private UUID roleId;
    private String roleName;
    private LocalDateTime assignedAt;

    private String username;
    private String email;
    private String fullName;
    private String avatarUrl;
}
