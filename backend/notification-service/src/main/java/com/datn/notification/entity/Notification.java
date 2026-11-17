package com.datn.notification.entity;

import com.datn.notification.enums.NotificationCategory;
import com.datn.notification.enums.NotificationSeverity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Một thông báo của một người nhận. Nội dung (title/body/link) được dựng sẵn lúc tạo,
 * giống Jira/GitHub: đổi tên công việc về sau không làm đổi thông báo cũ.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    private UUID id;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Column(nullable = false, length = 60)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationSeverity severity;

    @Column(name = "action_required", nullable = false)
    @Builder.Default
    private boolean actionRequired = false;

    @Column(name = "action_resolved_at")
    private Instant actionResolvedAt;

    /** Đã escape HTML, chỉ có thể chứa thẻ &lt;b&gt;. */
    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 1000)
    private String body;

    /** Đường dẫn tương đối của frontend, ví dụ /browse/MOC-2. */
    @Column(length = 500)
    private String link;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "entity_type", length = 30)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_count", nullable = false)
    @Builder.Default
    private int actorCount = 1;

    /** Ảnh chụp dữ liệu hiển thị: tên dự án, tên người thao tác, danh sách actor khi gộp… */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> data = new LinkedHashMap<>();

    @Column(name = "group_key", length = 200)
    private String groupKey;

    @Column(name = "dedup_key", nullable = false, length = 255)
    private String dedupKey;

    @Column(name = "source_event_id", length = 64)
    private String sourceEventId;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        if (updatedAt == null) updatedAt = Instant.now();
    }

    public boolean isRead() {
        return readAt != null;
    }

    /** Đang chờ người nhận xử lý (chưa ai giải quyết). */
    public boolean isPendingAction() {
        return actionRequired && actionResolvedAt == null;
    }
}
