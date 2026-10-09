-- ============================================================================
-- DATN-2026: WORK SERVICE
-- Flyway Migration: V6__github_integration.sql
-- Description: Tích hợp GitHub (OAuth App + webhook) cho dự án phần mềm.
-- user id = Keycloak sub (giống invitations / team_members).
-- ============================================================================

-- Tài khoản GitHub đã kết nối của từng user (token mã hóa AES-GCM, xem TokenCrypto)
CREATE TABLE IF NOT EXISTS github_connections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE,                 -- Keycloak sub
    github_user_id BIGINT NOT NULL,
    github_login VARCHAR(100) NOT NULL,
    avatar_url TEXT,
    access_token_enc TEXT NOT NULL,               -- base64(iv || ciphertext)
    scope VARCHAR(100),
    invalid BOOLEAN NOT NULL DEFAULT FALSE,       -- GitHub trả 401 -> đánh dấu, FE nhắc kết nối lại
    connected_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Repo liên kết với dự án (MVP: 1 repo / 1 dự án, mỗi chiều unique)
CREATE TABLE IF NOT EXISTS github_repo_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL UNIQUE REFERENCES projects(id) ON DELETE CASCADE,
    repo_id BIGINT NOT NULL UNIQUE,               -- webhook tìm theo repository.id
    repo_owner VARCHAR(100) NOT NULL,
    repo_name VARCHAR(100) NOT NULL,
    html_url TEXT NOT NULL,
    default_branch VARCHAR(255) NOT NULL DEFAULT 'main',
    webhook_id BIGINT,
    linked_by UUID NOT NULL,                      -- Keycloak sub; actor cho auto-transition
    auto_transition_on_pr_open BOOLEAN NOT NULL DEFAULT TRUE,
    auto_transition_on_pr_merge BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Branch / commit / PR gắn với work item (nhận từ webhook, đợt sau mới ghi)
CREATE TABLE IF NOT EXISTS work_item_dev_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    work_item_id UUID NOT NULL REFERENCES work_items(id) ON DELETE CASCADE,
    repo_link_id UUID NOT NULL REFERENCES github_repo_links(id) ON DELETE CASCADE,
    kind VARCHAR(20) NOT NULL,                    -- BRANCH | COMMIT | PULL_REQUEST
    external_id VARCHAR(255) NOT NULL,            -- BRANCH: tên; COMMIT: sha; PR: số
    title TEXT,
    url TEXT NOT NULL,
    state VARCHAR(20),                            -- PR: OPEN | MERGED | CLOSED
    author_login VARCHAR(100),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_dev_link UNIQUE (work_item_id, kind, external_id)
);

CREATE INDEX IF NOT EXISTS idx_dev_links_item ON work_item_dev_links(work_item_id);
