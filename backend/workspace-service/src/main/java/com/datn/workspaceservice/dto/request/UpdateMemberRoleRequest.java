package com.datn.workspaceservice.dto.request;

import com.datn.workspaceservice.enums.WorkspaceRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateMemberRoleRequest {

    @NotNull(message = "Vai trò không được để trống")
    private WorkspaceRole role;
}
