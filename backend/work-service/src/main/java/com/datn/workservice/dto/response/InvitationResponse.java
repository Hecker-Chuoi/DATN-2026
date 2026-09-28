package com.datn.workservice.dto.response;

import com.datn.workservice.enums.InvitationStatus;
import com.datn.workservice.enums.InvitationType;
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
public class InvitationResponse {
    private UUID id;
    private InvitationType type;
    private UUID targetId;
    /** Tên nhóm hoặc tên dự án */
    private String targetName;
    /** LEADER/MEMBER/VIEWER với TEAM; tên project role với PROJECT */
    private String roleName;
    private InvitationStatus status;
    private UUID invitedBy;
    private String inviterName;
    private UUID inviteeUserId;
    private String inviteeName;
    private String inviteeEmail;
    private LocalDateTime createdAt;
    private LocalDateTime respondedAt;
}
