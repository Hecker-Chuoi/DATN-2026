package com.datn.workspaceservice.entity;

import com.datn.workspaceservice.enums.InvitationStatus;
import com.datn.workspaceservice.enums.WorkspaceRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Lời mời tham gia workspace. Chấp nhận mới tạo hàng {@link WorkspaceMember}.
 * {@code inviteeUserId}/{@code invitedBy} LUÔN là identity-service users.id.
 */
@Entity
@Table(name = "workspace_invitations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkspaceInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "invitee_user_id", nullable = false)
    private UUID inviteeUserId;

    @Column(name = "invited_by", nullable = false)
    private UUID invitedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private WorkspaceRole role = WorkspaceRole.MEMBER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private InvitationStatus status = InvitationStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;
}
