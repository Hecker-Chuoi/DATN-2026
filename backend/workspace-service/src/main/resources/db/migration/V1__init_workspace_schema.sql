-- ============================================================================
-- DATN-2026: WORKSPACE SERVICE
-- Flyway Migration: V1__init_workspace_schema.sql
-- Description: Cấp cao nhất của hệ phân cấp (Workspace > Team > Project > Issue)
-- ============================================================================

-- Bật extension pgcrypto nếu cần thiết (PostgreSQL 13+ đã có sẵn gen_random_uuid())
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================================
-- 1. BẢNG KHÔNG GIAN LÀM VIỆC & THÀNH VIÊN (WORKSPACES & WORKSPACE MEMBERS)
-- ============================================================================

-- BẢNG: workspaces -> Gốc tenant. Chứa nhiều Team, mỗi Team chứa nhiều Project.
--
-- Soft delete (deleted_at) là CÓ CHỦ ĐÍCH, khác với teams (hard delete):
-- workspace là gốc tenant, xóa cứng sẽ bỏ mồ côi teams/projects/work_items nằm
-- trong bảng của work-service — không có transaction phân tán, không có cascade.
-- Theo tiền lệ projects.deleted_at + endpoint POST /{id}/restore.
--
-- KHÔNG có cột owner_id (khác teams.owner_id): teams mang cả owner_id LẪN role
-- LEADER nên TeamController phải có ~6 helper hòa giải hai nguồn sự thật. Ở đây
-- hàng workspace_members là nguồn sự thật duy nhất; created_by chỉ để audit.
CREATE TABLE IF NOT EXISTS workspaces (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    description TEXT,
    avatar_url VARCHAR(500),
    created_by UUID NOT NULL,                       -- users.id (không FK: cross-service)
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_workspaces_slug UNIQUE (slug)
);

CREATE INDEX IF NOT EXISTS idx_workspaces_created_by ON workspaces(created_by) WHERE deleted_at IS NULL;

-- BẢNG: workspace_members -> Vai trò của user trong TỪNG workspace.
--
-- uq_workspace_member(workspace_id, user_id) chính là ràng buộc hiện thực hóa
-- yêu cầu "một user tham gia nhiều workspace, mỗi nơi một vai trò khác nhau":
-- một hàng cho mỗi cặp (workspace, user), cột role riêng từng hàng.
--
-- user_id LUÔN LUÔN là identity-service users.id, KHÔNG BAO GIỜ là Keycloak sub.
-- Lý do: UserService.getCurrentUser ghi lại user.keycloakId khi email khớp một
-- Keycloak account khác, nên keycloak_id là MUTABLE. Khóa membership theo id
-- mutable sẽ âm thầm mất quyền. Xem CurrentUserResolver.
CREATE TABLE IF NOT EXISTS workspace_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,                          -- users.id (không FK: cross-service)
    role VARCHAR(50) NOT NULL DEFAULT 'MEMBER',     -- OWNER, ADMIN, MEMBER, GUEST
    invited_by UUID,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_workspace_member UNIQUE (workspace_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_workspace_members_user ON workspace_members(user_id);

-- Index này là chỗ COUNT(*) WHERE role = 'OWNER' của assertNotLastOwner chạy trên.
CREATE INDEX IF NOT EXISTS idx_workspace_members_role ON workspace_members(workspace_id, role);
