-- Chỉ PR ghi "Closes/Fixes <mã Task>" mới hoàn thành Task khi merge (PR chỉ nhắc mã thì giữ trạng thái).
-- Mặc định bật cho cả repo đã liên kết; tắt để quay về cách cũ (mọi PR nhắc mã đều hoàn thành Task).
ALTER TABLE project_github_repos
    ADD COLUMN IF NOT EXISTS pr_merge_requires_close_keyword BOOLEAN NOT NULL DEFAULT TRUE;
