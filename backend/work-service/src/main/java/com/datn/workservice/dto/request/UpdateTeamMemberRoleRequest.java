package com.datn.workservice.dto.request;

import com.datn.workservice.enums.TeamRole;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTeamMemberRoleRequest {

    @NotNull(message = "Team role is required")
    private TeamRole role;
}
