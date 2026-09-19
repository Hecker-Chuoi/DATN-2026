package com.datn.workspaceservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Workspace - gốc của hệ phân cấp Workspace > Team > Project > Issue.
 * <p>
 * KHÔNG có cột {@code ownerId}: khác với {@code Team} (mang cả {@code owner_id}
 * lẫn role LEADER, khiến TeamController phải có ~6 helper hòa giải hai nguồn sự
 * thật). Ở đây hàng {@link WorkspaceMember} với role OWNER là nguồn sự thật duy
 * nhất; {@code createdBy} chỉ để audit.
 */
@Entity
@Table(name = "workspaces", uniqueConstraints = {
        @UniqueConstraint(name = "uq_workspaces_slug", columnNames = {"slug"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Workspace {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 100)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    /** users.id của identity-service. Chỉ để audit, không quyết định quyền. */
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Soft delete. Workspace là gốc tenant nên xóa cứng sẽ bỏ mồ côi
     * teams/projects/work_items nằm trong bảng của work-service mà không có
     * transaction phân tán nào bảo vệ. Theo tiền lệ {@code Project.deletedAt}.
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
