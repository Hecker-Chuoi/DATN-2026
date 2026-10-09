package com.datn.workspaceservice.dto.response;

import com.datn.workspaceservice.enums.InvitationStatus;
import com.datn.workspaceservice.enums.WorkspaceRole;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class WorkspaceInvitationResponse {
    private UUID id;
    private UUID workspaceId;
    private String workspaceName;
    private WorkspaceRole role;
    private UUID invitedBy;
    private String inviterName;
    private UUID inviteeUserId;
    private String inviteeName;
    private String inviteeEmail;
    private InvitationStatus status;
    private LocalDateTime createdAt;
}
