-- Cài đặt bật / tắt thông báo theo nhóm × kênh, và tắt tiếng từng dự án.
-- user_id là Keycloak sub (giống recipient_id của bảng notifications).
-- Chưa có dòng nào cho một nhóm = theo mặc định của danh mục (trong ứng dụng: bật; email: theo loại).

CREATE TABLE IF NOT EXISTS notification_preferences (
    user_id     UUID        NOT NULL,
    category    VARCHAR(30) NOT NULL,
    in_app      BOOLEAN     NOT NULL DEFAULT TRUE,
    email       BOOLEAN     NOT NULL DEFAULT TRUE,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, category)
);

-- muted_until NULL = tắt đến khi người dùng bật lại.
CREATE TABLE IF NOT EXISTS notification_project_mutes (
    user_id      UUID         NOT NULL,
    project_id   UUID         NOT NULL,
    project_name VARCHAR(255),
    muted_until  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, project_id)
);

CREATE INDEX IF NOT EXISTS idx_notification_project_mutes_project
    ON notification_project_mutes (project_id);
