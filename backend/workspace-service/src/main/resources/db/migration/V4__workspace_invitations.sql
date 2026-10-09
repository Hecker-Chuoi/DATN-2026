-- ============================================================================
-- DATN-2026: WORKSPACE SERVICE
-- Flyway Migration: V4__workspace_invitations.sql
-- Description: Lời mời tham gia workspace (thay cho thêm thành viên trực tiếp).
-- Mô hình theo work-service V4__invitations; user id LUÔN là users.id.
-- ============================================================================

CREATE TABLE IF NOT EXISTS workspace_invitations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    invitee_user_id UUID NOT NULL,                  -- users.id (không FK: cross-service)
    invited_by UUID NOT NULL,                       -- users.id
    role VARCHAR(50) NOT NULL DEFAULT 'MEMBER',     -- OWNER | ADMIN | MEMBER | GUEST
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING | ACCEPTED | DECLINED | CANCELLED
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_ws_invitations_invitee_status ON workspace_invitations(invitee_user_id, status);
CREATE INDEX IF NOT EXISTS idx_ws_invitations_workspace_status ON workspace_invitations(workspace_id, status);

-- Mỗi người chỉ có một lời mời đang chờ cho cùng một workspace
CREATE UNIQUE INDEX IF NOT EXISTS uq_ws_invitations_pending
    ON workspace_invitations(workspace_id, invitee_user_id) WHERE status = 'PENDING';
