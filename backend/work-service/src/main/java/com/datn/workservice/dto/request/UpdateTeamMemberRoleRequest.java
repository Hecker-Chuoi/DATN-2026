package com.datn.workservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTeamMemberRoleRequest {

    @NotBlank(message = "Vai trò không được để trống")
    private String roleInTeam;
}
