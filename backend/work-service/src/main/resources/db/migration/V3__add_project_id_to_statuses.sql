-- ============================================================================
-- DATN-2026: WORK SERVICE
-- Flyway Migration: V3__add_project_id_to_statuses.sql
-- Description: Add project_id column to statuses table to support project-scoped statuses
-- ============================================================================

-- 1. Thêm cột project_id tham chiếu tới bảng projects
ALTER TABLE statuses ADD COLUMN IF NOT EXISTS project_id UUID REFERENCES projects(id) ON DELETE CASCADE;

-- 2. Tạo Index cho project_id để tối ưu truy vấn theo dự án
CREATE INDEX IF NOT EXISTS idx_statuses_project_id ON statuses(project_id);

-- 3. Backfill project_id cho các statuses hiện có nếu đang được tham chiếu bởi workflow của dự án
UPDATE statuses s
SET project_id = w.project_id
FROM workflow_transitions wt
JOIN workflows w ON wt.workflow_id = w.id
WHERE (wt.from_status_id = s.id OR wt.to_status_id = s.id)
  AND s.project_id IS NULL
  AND w.project_id IS NOT NULL;
