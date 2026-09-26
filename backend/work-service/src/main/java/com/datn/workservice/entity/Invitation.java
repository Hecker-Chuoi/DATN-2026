package com.datn.workservice.entity;

import com.datn.workservice.enums.InvitationStatus;
import com.datn.workservice.enums.InvitationType;
import com.datn.workservice.enums.TeamRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Lời mời tham gia nhóm (TEAM) hoặc dự án (PROJECT). Người được mời phải chấp nhận thì mới trở thành thành viên.
 * user id luôn là Keycloak sub (giống team_members / role_assignments).
 */
@Entity
@Table(name = "invitations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invitation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvitationType type;

    /** teams.id hoặc projects.id tùy type */
    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "invitee_user_id", nullable = false)
    private UUID inviteeUserId;

    @Column(name = "invited_by", nullable = false)
    private UUID invitedBy;

    /** Vai trò trong nhóm khi type = TEAM */
    @Enumerated(EnumType.STRING)
    @Column(name = "team_role", length = 50)
    private TeamRole teamRole;

    /** project_roles.id khi type = PROJECT */
    @Column(name = "project_role_id")
    private UUID projectRoleId;

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
