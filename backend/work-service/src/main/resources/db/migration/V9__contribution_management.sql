-- ============================================================================
-- DATN-2026: WORK SERVICE
-- Flyway Migration: V9__contribution_management.sql
-- Description: Bảng Sổ ghi công (Append-Only Ledger) và Bảng Snapshot Đóng băng Chu kỳ
-- ============================================================================

-- 1. Bảng Sổ ghi công (Append-only Ledger: chỉ viết thêm, không sửa xóa)
CREATE TABLE IF NOT EXISTS contribution_ledger_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    sprint_id UUID REFERENCES sprints(id) ON DELETE SET NULL,
    work_item_id UUID REFERENCES work_items(id) ON DELETE SET NULL,
    user_id UUID NOT NULL,               -- Keycloak user sub được ghi nhận điểm
    points DECIMAL(6, 2) NOT NULL,      -- Dương (+5.00) hoặc Âm (-5.00)
    entry_type VARCHAR(50) NOT NULL,    -- COMPLETED, REOPENED, DELETED, MANUAL_ADJUSTMENT
    note VARCHAR(255),                  -- "Đúng hạn", "Trễ 1 ngày", "Bị mở lại", "Hoàn thành lại", "Quản lý điều chuyển",...
    actor_id UUID NOT NULL,             -- Người thực hiện thao tác (Dev, QA, Leader)
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Chỉ mục tối ưu truy vấn Sổ ghi công
CREATE INDEX IF NOT EXISTS idx_contrib_ledger_proj_created ON contribution_ledger_entries(project_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_contrib_ledger_proj_sprint ON contribution_ledger_entries(project_id, sprint_id);
CREATE INDEX IF NOT EXISTS idx_contrib_ledger_user ON contribution_ledger_entries(project_id, user_id);
CREATE INDEX IF NOT EXISTS idx_contrib_ledger_work_item ON contribution_ledger_entries(work_item_id);

-- 2. Bảng Đóng băng Dữ liệu Chu kỳ đã kết thúc (Sprint Contribution Snapshots)
CREATE TABLE IF NOT EXISTS sprint_contribution_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sprint_id UUID NOT NULL REFERENCES sprints(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    total_points DECIMAL(6, 2) NOT NULL DEFAULT 0,
    contribution_percentage DECIMAL(5, 2) NOT NULL DEFAULT 0,
    completed_tasks INT NOT NULL DEFAULT 0,
    discussion_count INT NOT NULL DEFAULT 0,
    bugs_reported INT NOT NULL DEFAULT 0,
    on_time_rate DECIMAL(5, 2) NOT NULL DEFAULT 0,
    reopened_count INT NOT NULL DEFAULT 0,
    frozen_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_sprint_user_snapshot UNIQUE(sprint_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_sprint_contrib_snapshot_proj ON sprint_contribution_snapshots(project_id, sprint_id);
