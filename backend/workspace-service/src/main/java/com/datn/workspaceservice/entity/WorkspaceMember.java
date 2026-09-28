package com.datn.workspaceservice.entity;

import com.datn.workspaceservice.enums.WorkspaceRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Vai trò của một user trong MỘT workspace.
 * <p>
 * {@code uq_workspace_member(workspace_id, user_id)} chính là ràng buộc hiện
 * thực hóa yêu cầu "một user tham gia nhiều workspace, mỗi nơi một vai trò khác
 * nhau": một hàng cho mỗi cặp, cột {@code role} riêng từng hàng.
 * <p>
 * {@code userId} LUÔN là identity-service {@code users.id}, KHÔNG BAO GIỜ là
 * Keycloak {@code sub} - xem {@code CurrentUserResolver} để biết lý do.
 */
@Entity
@Table(name = "workspace_members", uniqueConstraints = {
        @UniqueConstraint(name = "uq_workspace_member", columnNames = {"workspace_id", "user_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkspaceMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    /** identity-service users.id (không phải Keycloak sub). */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private WorkspaceRole role = WorkspaceRole.MEMBER;

    @Column(name = "invited_by")
    private UUID invitedBy;

    @CreationTimestamp
    @Column(name = "joined_at", updatable = false, nullable = false)
    private LocalDateTime joinedAt;
}
