-- Từ khoá hoàn thành do Admin dự án tự đặt (VD: "done, final"), cách nhau bằng dấu phẩy.
-- NULL = dùng bộ mặc định Closes / Fixes / Resolves / Hoàn thành.
ALTER TABLE project_github_repos ADD COLUMN IF NOT EXISTS pr_close_keywords VARCHAR(500);
