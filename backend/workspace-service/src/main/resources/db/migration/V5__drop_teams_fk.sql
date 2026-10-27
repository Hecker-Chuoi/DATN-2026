-- ============================================================================
-- DATN-2026: WORKSPACE SERVICE
-- Flyway Migration: V5__drop_teams_fk.sql
-- Description: Gỡ bỏ ràng buộc khóa ngoại fk_teams_workspace (nếu còn tồn tại).
-- ============================================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_teams_workspace') THEN
        ALTER TABLE IF EXISTS teams DROP CONSTRAINT fk_teams_workspace;
        RAISE NOTICE 'Đã gỡ bỏ constraint fk_teams_workspace.';
    ELSE
        RAISE NOTICE 'Bỏ qua: không tìm thấy constraint fk_teams_workspace.';
    END IF;
END $$;
