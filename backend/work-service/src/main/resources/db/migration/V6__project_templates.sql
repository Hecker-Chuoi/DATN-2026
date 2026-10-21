-- ============================================================================
-- DATN-2026: WORK SERVICE
-- Flyway Migration: V6__project_templates.sql
-- Description: Dự án mẫu (Project Template) - phân loại mẫu và nhật ký nhân bản
-- Tài liệu: docs/services/work_service/template/2_TEMPLATE_IMPLEMENTATION_DB.md
-- ============================================================================

-- 1. Phân loại mẫu (SOFTWARE, MARKETING, ...). Chỉ có nghĩa khi is_template = TRUE.
ALTER TABLE projects ADD COLUMN IF NOT EXISTS template_category VARCHAR(50);

-- 2. Đường nạp thư viện mẫu theo workspace (tab "Chọn mẫu dự án" ở form tạo dự án).
CREATE INDEX IF NOT EXISTS idx_projects_templates
    ON projects(workspace_id, template_category)
    WHERE is_template = TRUE AND deleted_at IS NULL;

-- 3. project_clones đã có từ V1 nhưng chưa có code ghi. Bổ sung ngữ cảnh nhân bản:
--    clone_type       : FROM_TEMPLATE (tạo dự án từ mẫu) | SAVE_AS_TEMPLATE (lưu dự án thành mẫu) | DUPLICATE (nhân bản dự án)
--    date_offset_days : số ngày đã dời mốc thời gian (sprint, work item, version) so với nguồn
--    clone_options    : tùy chọn người dùng chọn (includeSprints, includeWorkItems, includeVersions)
--    clone_stats      : số lượng bản ghi đã sao chép theo từng loại (để audit / hiển thị)
ALTER TABLE project_clones ADD COLUMN IF NOT EXISTS clone_type VARCHAR(30) NOT NULL DEFAULT 'FROM_TEMPLATE';
ALTER TABLE project_clones ADD COLUMN IF NOT EXISTS date_offset_days INT NOT NULL DEFAULT 0;
ALTER TABLE project_clones ADD COLUMN IF NOT EXISTS clone_options JSONB;
ALTER TABLE project_clones ADD COLUMN IF NOT EXISTS clone_stats JSONB;

-- 4. Mỗi dự án đích chỉ sinh ra từ đúng một nguồn; không tự nhân bản chính mình.
CREATE UNIQUE INDEX IF NOT EXISTS uq_project_clones_cloned ON project_clones(cloned_project_id);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_project_clone_not_self') THEN
        ALTER TABLE project_clones
            ADD CONSTRAINT chk_project_clone_not_self CHECK (source_project_id <> cloned_project_id);
    END IF;
END $$;
