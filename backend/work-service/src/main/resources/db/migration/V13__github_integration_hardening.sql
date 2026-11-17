-- ============================================================================
-- V13: GIA CỐ TÍCH HỢP GITHUB
--  1. Ghi nhận ai đã kết nối repo (dùng token của người đó thay vì token "bất kỳ")
--  2. Cờ riêng cho chiều GitHub -> DATN: đóng/mở Issue thì chuyển trạng thái Task
--  3. Webhook secret riêng cho từng repo: secret chung cũ đã lộ trong mã nguồn nên bị xoá,
--     repo đang liên kết phải kết nối lại để sinh secret mới (webhook cũ sẽ bị từ chối)
--  4. Chống tạo trùng Task cho cùng một Issue (webhook gửi lại / chạy song song với đồng bộ tay)
-- ============================================================================

ALTER TABLE project_github_repos
    ADD COLUMN IF NOT EXISTS connected_by_user_id UUID,
    ADD COLUMN IF NOT EXISTS auto_close_task_on_issue_close BOOLEAN DEFAULT TRUE;

UPDATE project_github_repos SET webhook_secret = NULL;

ALTER TABLE work_item_github_links
    ADD COLUMN IF NOT EXISTS project_id UUID;

UPDATE work_item_github_links l
SET project_id = w.project_id
FROM work_items w
WHERE l.work_item_id = w.id
  AND l.project_id IS NULL;

-- Dọn liên kết Issue trùng (giữ bản tạo sớm nhất) trước khi tạo chỉ mục duy nhất
DELETE FROM work_item_github_links a
USING work_item_github_links b
WHERE a.link_type = 'ISSUE'
  AND b.link_type = 'ISSUE'
  AND a.project_id = b.project_id
  AND a.github_number = b.github_number
  AND (a.created_at > b.created_at OR (a.created_at = b.created_at AND a.id > b.id));

CREATE UNIQUE INDEX IF NOT EXISTS uq_github_issue_link_per_project
    ON work_item_github_links (project_id, github_number)
    WHERE link_type = 'ISSUE';

CREATE INDEX IF NOT EXISTS idx_project_github_repos_full_name
    ON project_github_repos (LOWER(repo_full_name));

CREATE INDEX IF NOT EXISTS idx_user_github_tokens_github_user
    ON user_github_tokens (github_user_id);
