-- ============================================================================
-- V6: GIAI ĐOẠN TÍCH HỢP GITHUB (OAuth, Repositories, Smart Commits, PRs, Issues)
-- ============================================================================

-- 1. Bảng lưu tài khoản GitHub cá nhân của người dùng (Token mã hóa an toàn)
CREATE TABLE IF NOT EXISTS user_github_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE,
    github_user_id BIGINT,
    github_username VARCHAR(255),
    github_avatar_url VARCHAR(512),
    access_token_encrypted TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. Bảng liên kết giữa Dự án và GitHub Repository
CREATE TABLE IF NOT EXISTS project_github_repos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    repo_owner VARCHAR(255) NOT NULL,
    repo_name VARCHAR(255) NOT NULL,
    repo_full_name VARCHAR(512) NOT NULL, -- vd: "datn-2026/work-service"
    repo_url VARCHAR(512) NOT NULL,
    default_branch VARCHAR(100) DEFAULT 'main',
    webhook_id BIGINT,
    webhook_secret VARCHAR(255),
    sync_issues_enabled BOOLEAN DEFAULT TRUE,
    auto_move_in_review_on_pr BOOLEAN DEFAULT TRUE,
    auto_close_task_on_pr_merge BOOLEAN DEFAULT TRUE,
    auto_close_github_issue_on_done BOOLEAN DEFAULT TRUE,
    last_synced_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_project_repo UNIQUE(project_id, repo_full_name)
);

-- 3. Bảng liên kết chi tiết giữa Work Item và các thực thể GitHub (Commits, PRs, Issues)
CREATE TABLE IF NOT EXISTS work_item_github_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    work_item_id UUID NOT NULL REFERENCES work_items(id) ON DELETE CASCADE,
    link_type VARCHAR(50) NOT NULL, -- 'ISSUE', 'COMMIT', 'PULL_REQUEST', 'BRANCH'
    github_id VARCHAR(255),          -- Commit Hash SHA hoặc GitHub ID
    github_number INT,              -- Issue #12, PR #34
    title VARCHAR(512),
    url VARCHAR(512) NOT NULL,
    status VARCHAR(50),             -- 'OPEN', 'MERGED', 'CLOSED'
    author_name VARCHAR(255),
    author_avatar_url VARCHAR(512),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Chỉ mục tối ưu hóa truy vấn
CREATE INDEX IF NOT EXISTS idx_github_links_item ON work_item_github_links(work_item_id);
CREATE INDEX IF NOT EXISTS idx_github_links_type ON work_item_github_links(link_type, github_number);
CREATE INDEX IF NOT EXISTS idx_github_links_commit ON work_item_github_links(github_id) WHERE link_type = 'COMMIT';
CREATE INDEX IF NOT EXISTS idx_project_github_repos_project ON project_github_repos(project_id);
