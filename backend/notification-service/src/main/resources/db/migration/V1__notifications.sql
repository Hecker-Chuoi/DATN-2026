-- notification-service: thông báo trong ứng dụng (chuông).
-- Thiết kế: docs/services/notification/2_KIEN_TRUC_VA_THIET_KE.md §5.
-- recipient_id / actor_id là Keycloak sub (giống mọi bảng của work-service).

CREATE TABLE IF NOT EXISTS notifications (
    id                 UUID PRIMARY KEY,
    recipient_id       UUID          NOT NULL,
    type               VARCHAR(60)   NOT NULL,
    category           VARCHAR(30)   NOT NULL,
    severity           VARCHAR(20)   NOT NULL,
    action_required    BOOLEAN       NOT NULL DEFAULT FALSE,
    action_resolved_at TIMESTAMPTZ,
    title              VARCHAR(500)  NOT NULL,
    body               VARCHAR(1000),
    link               VARCHAR(500),
    workspace_id       UUID,
    project_id         UUID,
    entity_type        VARCHAR(30),
    entity_id          UUID,
    actor_id           UUID,
    actor_count        INT           NOT NULL DEFAULT 1,
    data               JSONB,
    group_key          VARCHAR(200),
    dedup_key          VARCHAR(255)  NOT NULL,
    source_event_id    VARCHAR(64),
    read_at            TIMESTAMPTZ,
    deleted_at         TIMESTAMPTZ,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_notifications_dedup_key UNIQUE (dedup_key)
);

-- Danh sách của một người, mới nhất trước (updated_at để thông báo vừa gộp nổi lên đầu)
CREATE INDEX IF NOT EXISTS idx_notif_list
    ON notifications (recipient_id, updated_at DESC, id DESC) WHERE deleted_at IS NULL;

-- Đếm số chưa đọc trên chuông
CREATE INDEX IF NOT EXISTS idx_notif_unread
    ON notifications (recipient_id) WHERE read_at IS NULL AND deleted_at IS NULL;

-- Tìm thông báo chưa đọc cùng nhóm để gộp (quy tắc R4)
CREATE INDEX IF NOT EXISTS idx_notif_group
    ON notifications (recipient_id, group_key) WHERE read_at IS NULL AND deleted_at IS NULL;

-- Đóng các thông báo "Cần xử lý" theo đối tượng (quy tắc R9)
CREATE INDEX IF NOT EXISTS idx_notif_action
    ON notifications (entity_id, type) WHERE action_required AND action_resolved_at IS NULL;

-- Job dọn dẹp
CREATE INDEX IF NOT EXISTS idx_notif_deleted_at ON notifications (deleted_at) WHERE deleted_at IS NOT NULL;
