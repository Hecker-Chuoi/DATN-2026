-- ============================================================================
-- DATN-2026: WORK SERVICE
-- Flyway Migration: V4__invitations.sql
-- Description: Lời mời tham gia nhóm / dự án (thay cho thêm thành viên trực tiếp)
-- ============================================================================

CREATE TABLE IF NOT EXISTS invitations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(20) NOT NULL,                      -- TEAM | PROJECT
    target_id UUID NOT NULL,                        -- teams.id hoặc projects.id
    invitee_user_id UUID NOT NULL,                  -- Keycloak sub
    invited_by UUID NOT NULL,                       -- Keycloak sub
    team_role VARCHAR(50),                          -- LEADER | MEMBER | VIEWER (type = TEAM)
    project_role_id UUID,                           -- project_roles.id (type = PROJECT)
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING | ACCEPTED | DECLINED | CANCELLED
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_invitations_invitee_status ON invitations(invitee_user_id, status);
CREATE INDEX IF NOT EXISTS idx_invitations_target_status ON invitations(type, target_id, status);

-- Mỗi người chỉ có một lời mời đang chờ cho cùng một nhóm/dự án
CREATE UNIQUE INDEX IF NOT EXISTS uq_invitations_pending
    ON invitations(type, target_id, invitee_user_id) WHERE status = 'PENDING';
