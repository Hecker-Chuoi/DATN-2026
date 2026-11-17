package com.datn.workspaceservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnsureWorkspaceMemberRequest {

    @NotNull(message = "userId không được để trống")
    private UUID userId;

    private String role;

    private UUID invitedBy;
}
