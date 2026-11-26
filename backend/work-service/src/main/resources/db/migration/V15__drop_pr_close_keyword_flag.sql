-- Bỏ công tắc riêng của V14: bật "Merge PR -> Task hoàn thành" thì luôn bắt buộc PR ghi "Closes/Fixes <mã Task>".
ALTER TABLE project_github_repos DROP COLUMN IF EXISTS pr_merge_requires_close_keyword;
