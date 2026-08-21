package com.datn.workservice.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddTeamMemberRequest {

    private UUID userId;

    private String emailOrUsername;

    private String email;

    private String username;

    @Builder.Default
    private String roleInTeam = "member";
}
