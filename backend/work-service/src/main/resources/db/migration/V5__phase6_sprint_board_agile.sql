-- ============================================================================
-- V2: GIAI DOAN 6 - Sprint lifecycle, Board engine, Agile reports
-- ============================================================================

-- 1. Optimistic locking cho Sprint (khop @Version tren entity Sprint).
--    Bat buoc tao o day chu khong de ddl-auto=update tu them: Hibernate se phat
--    ALTER TABLE ... ADD COLUMN NOT NULL khong co DEFAULT va that bai tren bang co du lieu.
ALTER TABLE sprints ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

-- 2. Chuan hoa du lieu truoc khi tao unique index ben duoi.
--    Giu lai Sprint ACTIVE moi nhat cua moi du an, cac Sprint ACTIVE con lai tra ve FUTURE.
UPDATE sprints s SET state = 'FUTURE'
WHERE s.state = 'ACTIVE'
  AND s.id <> (
      SELECT s2.id FROM sprints s2
      WHERE s2.project_id = s.project_id AND s2.state = 'ACTIVE'
      ORDER BY s2.start_date DESC NULLS LAST, s2.created_at DESC
      LIMIT 1
  );

-- 3. Bat bien "moi du an toi da 1 Sprint ACTIVE" o tang CSDL (chong race condition).
CREATE UNIQUE INDEX IF NOT EXISTS uq_sprints_one_active_per_project
    ON sprints(project_id) WHERE state = 'ACTIVE';

-- 4. Velocity: quet cac Sprint da dong theo thoi gian.
CREATE INDEX IF NOT EXISTS idx_sprints_project_completed
    ON sprints(project_id, completed_date DESC) WHERE state = 'CLOSED';

-- 5. Duong dan nap Board (project + sprint + status + rank).
CREATE INDEX IF NOT EXISTS idx_work_items_board_fetch
    ON work_items(project_id, sprint_id, status_id, rank) WHERE deleted_at IS NULL;

-- 6. Duong dan nap Backlog (sprint_id IS NULL).
CREATE INDEX IF NOT EXISTS idx_work_items_backlog_fetch
    ON work_items(project_id, rank) WHERE deleted_at IS NULL AND sprint_id IS NULL;

-- 7. Dem WIP Limit theo tap status trong 1 sprint.
CREATE INDEX IF NOT EXISTS idx_work_items_sprint_status
    ON work_items(sprint_id, status_id) WHERE deleted_at IS NULL;

-- 8. Tra nguoc status -> cot board (dung khi move card va ve CFD).
CREATE INDEX IF NOT EXISTS idx_board_column_statuses_status
    ON board_column_statuses(status_id);

-- 9. Replay changelog de dung Burndown/CFD (loc theo field_name).
CREATE INDEX IF NOT EXISTS idx_changelog_item_field_time
    ON work_item_changelogs(work_item_id, field_name, changed_at);
