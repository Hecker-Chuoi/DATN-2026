package com.datn.workservice.client.dto;

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

    private UUID userId;

    private String role;

    private UUID invitedBy;
}
