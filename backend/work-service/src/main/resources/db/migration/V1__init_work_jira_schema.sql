-- ============================================================================
-- DATN-2026: WORK SERVICE (JIRA-GRADE WORK ENGINE)
-- Flyway Migration: V1__init_work_jira_schema.sql
-- Description: Initialize complete 18+ tables, constraints, indexes & sequences
-- ============================================================================

-- Bật extension pgcrypto nếu cần thiết (PostgreSQL 13+ đã có sẵn gen_random_uuid())
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================================
-- 1. BẢNG NHÓM & THÀNH VIÊN NHÓM (TEAMS & TEAM MEMBERS)
-- ============================================================================

-- BẢNG: teams -> Quản lý Nhóm làm việc trong từng Workspace (Tên nhóm, Mô tả, Trưởng nhóm/Owner)
CREATE TABLE IF NOT EXISTS teams (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    owner_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_teams_workspace ON teams(workspace_id);
CREATE INDEX IF NOT EXISTS idx_teams_owner ON teams(owner_id);

-- BẢNG: team_members -> Danh sách thành viên tham gia Nhóm (Quan hệ N-N, Phân quyền vai trò: LEADER, MEMBER, VIEWER)
CREATE TABLE IF NOT EXISTS team_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'MEMBER', -- LEADER, MEMBER, VIEWER
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_team_member UNIQUE (team_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_team_members_user ON team_members(user_id);

-- ============================================================================
-- 2. BẢNG DỰ ÁN & NHÂN BẢN (PROJECTS & PROJECT CLONES)
-- ============================================================================

-- BẢNG: projects -> Dự án trung tâm (Chứa Mã tiền tố Key, Trưởng dự án, Kiểu Scrum/Kanban, Cờ Template mẫu)
CREATE TABLE IF NOT EXISTS projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL,
    team_id UUID REFERENCES teams(id) ON DELETE SET NULL,
    name VARCHAR(255) NOT NULL,
    key VARCHAR(20) NOT NULL UNIQUE,
    description TEXT,
    icon_url VARCHAR(500),
    lead_user_id UUID NOT NULL,
    default_assignee_type VARCHAR(50) NOT NULL DEFAULT 'PROJECT_LEADER',
    project_type VARCHAR(50) NOT NULL DEFAULT 'SOFTWARE_SCRUM',
    is_template BOOLEAN NOT NULL DEFAULT FALSE,
    start_date DATE,
    end_date DATE,
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_projects_workspace ON projects(workspace_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_projects_key ON projects(key);
CREATE INDEX IF NOT EXISTS idx_projects_lead ON projects(lead_user_id) WHERE deleted_at IS NULL;

-- BẢNG: project_clones -> Nhật ký lịch sử Nhân bản Dự án (Lưu liên kết Template nguồn, Dự án đích và Người clone)
CREATE TABLE IF NOT EXISTS project_clones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    cloned_project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    cloned_by UUID NOT NULL,
    cloned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_project_clones_source ON project_clones(source_project_id);
CREATE INDEX IF NOT EXISTS idx_project_clones_cloned ON project_clones(cloned_project_id);

-- ============================================================================
-- 3. PHÂN QUYỀN TRONG PROJECT (PROJECT RBAC & PERMISSION SCHEMES)
-- ============================================================================

-- BẢNG: project_roles -> Danh mục Vai trò trong phạm vi từng Dự án (Admin, PM, Developer, QA, Viewer)
CREATE TABLE IF NOT EXISTS project_roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID REFERENCES projects(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_project_roles_project ON project_roles(project_id);

-- BẢNG: role_assignments -> Gán Vai trò cụ thể cho Người dùng trong từng Dự án
CREATE TABLE IF NOT EXISTS role_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    role_id UUID NOT NULL REFERENCES project_roles(id) ON DELETE CASCADE,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_project_role UNIQUE (project_id, user_id, role_id)
);

CREATE INDEX IF NOT EXISTS idx_role_assignments_lookup ON role_assignments(project_id, user_id);

-- BẢNG: permission_scheme_entries -> Ma trận cấp Quyền hạn hạt mịn cho từng Vai trò (CREATE_ISSUE, SPRINT_MANAGE...)
CREATE TABLE IF NOT EXISTS permission_scheme_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES project_roles(id) ON DELETE CASCADE,
    permission_key VARCHAR(100) NOT NULL,
    CONSTRAINT uq_perm_entry UNIQUE (project_id, role_id, permission_key)
);

CREATE INDEX IF NOT EXISTS idx_perm_entries_lookup ON permission_scheme_entries(project_id, permission_key);

-- ============================================================================
-- 4. LOẠI CÔNG VIỆC & PHÂN CẤP (WORK ITEM TYPES & HIERARCHY)
-- ============================================================================

-- BẢNG: work_item_types -> Định nghĩa Loại công việc & Phân cấp (Epic=1, Story/Task/Bug=0, Subtask=-1)
CREATE TABLE IF NOT EXISTS work_item_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID REFERENCES projects(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL,
    description TEXT,
    icon_name VARCHAR(100),
    color_hex VARCHAR(20),
    hierarchy_level INT NOT NULL, -- 1: Epic, 0: Story/Task/Bug, -1: Subtask
    is_subtask BOOLEAN NOT NULL DEFAULT FALSE,
    is_system_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_work_item_types_project ON work_item_types(project_id);
CREATE INDEX IF NOT EXISTS idx_work_item_types_level ON work_item_types(hierarchy_level);

-- ============================================================================
-- 5. QUY TRÌNH & MÁY TRẠNG THÁI (WORKFLOW & FINITE STATE MACHINE)
-- ============================================================================

-- BẢNG: statuses -> Trạng thái công việc (Gắn Category: TODO, IN_PROGRESS, DONE để tính Burndown/Velocity)
CREATE TABLE IF NOT EXISTS statuses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    category VARCHAR(50) NOT NULL, -- TODO, IN_PROGRESS, DONE
    description TEXT,
    color_hex VARCHAR(20),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- BẢNG: workflows -> Định nghĩa Quy trình luồng làm việc (Workflow Engine FSM cho từng Dự án)
CREATE TABLE IF NOT EXISTS workflows (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID REFERENCES projects(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_workflows_project ON workflows(project_id);

-- BẢNG: workflow_transitions -> Các bước chuyển trạng thái (From -> To, gắn Condition, Validator, Post-function)
CREATE TABLE IF NOT EXISTS workflow_transitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id UUID NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
    from_status_id UUID REFERENCES statuses(id) ON DELETE CASCADE,
    to_status_id UUID NOT NULL REFERENCES statuses(id) ON DELETE CASCADE,
    name VARCHAR(150) NOT NULL,
    conditions_config JSONB,
    validators_config JSONB,
    post_functions_config JSONB
);

CREATE INDEX IF NOT EXISTS idx_transitions_workflow ON workflow_transitions(workflow_id);
CREATE INDEX IF NOT EXISTS idx_transitions_from_to ON workflow_transitions(workflow_id, from_status_id, to_status_id);

-- BẢNG: workflow_schemes -> Bộ Sơ đồ Quy trình áp dụng cho Dự án (Workflow Scheme)
CREATE TABLE IF NOT EXISTS workflow_schemes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- BẢNG: workflow_scheme_mappings -> Ánh xạ Loại công việc nào sử dụng Workflow nào trong Dự án
CREATE TABLE IF NOT EXISTS workflow_scheme_mappings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scheme_id UUID NOT NULL REFERENCES workflow_schemes(id) ON DELETE CASCADE,
    work_item_type_id UUID NOT NULL REFERENCES work_item_types(id) ON DELETE CASCADE,
    workflow_id UUID NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
    CONSTRAINT uq_scheme_item_type UNIQUE(scheme_id, work_item_type_id)
);

-- ============================================================================
-- 6. SPRINT, COMPONENT, VERSION & LABELS
-- ============================================================================

-- BẢNG: sprints -> Chu kỳ Sprint trong Scrum (Vòng đời: FUTURE -> ACTIVE -> CLOSED, Mục tiêu, Thời hạn)
CREATE TABLE IF NOT EXISTS sprints (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    goal TEXT,
    state VARCHAR(50) NOT NULL DEFAULT 'FUTURE', -- FUTURE, ACTIVE, CLOSED
    start_date TIMESTAMP WITH TIME ZONE,
    end_date TIMESTAMP WITH TIME ZONE,
    completed_date TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sprints_project_state ON sprints(project_id, state);

-- BẢNG: sprint_report_snapshots -> Snapshot cố định số liệu khi Đóng Sprint (Dùng để vẽ Burndown & Velocity vĩnh viễn)
CREATE TABLE IF NOT EXISTS sprint_report_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sprint_id UUID NOT NULL REFERENCES sprints(id) ON DELETE CASCADE UNIQUE,
    total_story_points DECIMAL(10, 2) DEFAULT 0,
    completed_story_points DECIMAL(10, 2) DEFAULT 0,
    incomplete_story_points DECIMAL(10, 2) DEFAULT 0,
    total_items INT DEFAULT 0,
    completed_items INT DEFAULT 0,
    snapshot_data JSONB NOT NULL,
    closed_by UUID NOT NULL,
    closed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- BẢNG: components -> Phân hệ Kiến trúc / Module kỹ thuật (Hỗ trợ Vertical Ownership & Tự gán Lead mặc định)
CREATE TABLE IF NOT EXISTS components (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    lead_user_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_project_component_name UNIQUE(project_id, name)
);

CREATE INDEX IF NOT EXISTS idx_components_project ON components(project_id);

-- BẢNG: versions -> Đợt phát hành Release (Độc lập với Sprint, tự động sinh Release Notes Markdown)
CREATE TABLE IF NOT EXISTS versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    release_date DATE,
    is_released BOOLEAN NOT NULL DEFAULT FALSE,
    is_archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_project_version_name UNIQUE(project_id, name)
);

CREATE INDEX IF NOT EXISTS idx_versions_project ON versions(project_id);

-- BẢNG: labels -> Danh mục Nhãn dán phân loại linh hoạt trong Dự án (Chuẩn hóa tự động on-the-fly)
CREATE TABLE IF NOT EXISTS labels (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    color_hex VARCHAR(20) DEFAULT '#6B7280',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_project_label_name UNIQUE(project_id, name)
);

CREATE INDEX IF NOT EXISTS idx_labels_project_name ON labels(project_id, name);

-- ============================================================================
-- 7. THỰC THỂ TRỌNG TÂM: WORK ITEMS
-- ============================================================================

-- BẢNG: work_items -> BẢNG CÔNG VIỆC TRUNG TÂM (Hợp nhất Epic, Story, Task, Bug, Subtask, LexoRank, JSONB Custom Fields)
CREATE TABLE IF NOT EXISTS work_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    type_id UUID NOT NULL REFERENCES work_item_types(id),
    status_id UUID NOT NULL REFERENCES statuses(id),
    parent_id UUID REFERENCES work_items(id) ON DELETE SET NULL,
    sprint_id UUID REFERENCES sprints(id) ON DELETE SET NULL,
    
    code VARCHAR(50) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    description TEXT,
    
    priority VARCHAR(50) NOT NULL DEFAULT 'MEDIUM',
    resolution VARCHAR(100),
    
    reporter_id UUID NOT NULL,
    assignee_id UUID,
    
    story_points DECIMAL(6, 2),
    original_estimate_seconds BIGINT DEFAULT 0,
    remaining_estimate_seconds BIGINT DEFAULT 0,
    time_spent_seconds BIGINT DEFAULT 0,
    
    rank VARCHAR(255) NOT NULL, -- LexoRank
    
    component_id UUID REFERENCES components(id) ON DELETE SET NULL,
    fix_version_id UUID REFERENCES versions(id) ON DELETE SET NULL,
    
    custom_fields JSONB DEFAULT '{}'::jsonb,
    
    start_date DATE,
    due_date DATE,
    
    resolved_at TIMESTAMP WITH TIME ZONE,
    version INT NOT NULL DEFAULT 0, -- Optimistic Locking
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    
    CONSTRAINT uq_project_work_item_code UNIQUE(project_id, code)
);

-- Chỉ mục B-Tree & GIN cho Work Items
CREATE INDEX IF NOT EXISTS idx_work_items_project ON work_items(project_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_work_items_sprint ON work_items(sprint_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_work_items_parent ON work_items(parent_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_work_items_status ON work_items(status_id);
CREATE INDEX IF NOT EXISTS idx_work_items_assignee ON work_items(assignee_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_work_items_type ON work_items(type_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_work_items_rank ON work_items(project_id, rank);
CREATE INDEX IF NOT EXISTS idx_work_items_custom_fields_gin ON work_items USING gin (custom_fields);

-- BẢNG: work_item_component_maps -> Bảng trung gian gắn nhiều Component phụ cho Work Item (Quan hệ N-N)
CREATE TABLE IF NOT EXISTS work_item_component_maps (
    work_item_id UUID NOT NULL REFERENCES work_items(id) ON DELETE CASCADE,
    component_id UUID NOT NULL REFERENCES components(id) ON DELETE CASCADE,
    PRIMARY KEY (work_item_id, component_id)
);

-- BẢNG: work_item_label_maps -> Bảng trung gian gắn nhiều Nhãn (Labels) cho Work Item (Quan hệ N-N)
CREATE TABLE IF NOT EXISTS work_item_label_maps (
    work_item_id UUID NOT NULL REFERENCES work_items(id) ON DELETE CASCADE,
    label_id UUID NOT NULL REFERENCES labels(id) ON DELETE CASCADE,
    PRIMARY KEY (work_item_id, label_id)
);

-- ============================================================================
-- 8. LIÊN KẾT PHỤ THUỘC (LINKED ISSUES)
-- ============================================================================

-- BẢNG: work_item_links -> Liên kết & Phụ thuộc giữa các công việc (BLOCKS, RELATES_TO, DUPLICATES, CAUSES)
CREATE TABLE IF NOT EXISTS work_item_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_item_id UUID NOT NULL REFERENCES work_items(id) ON DELETE CASCADE,
    target_item_id UUID NOT NULL REFERENCES work_items(id) ON DELETE CASCADE,
    link_type VARCHAR(50) NOT NULL, -- BLOCKS, RELATES_TO, DUPLICATES, CAUSES
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_no_self_link CHECK (source_item_id <> target_item_id),
    CONSTRAINT uq_work_item_link UNIQUE(source_item_id, target_item_id, link_type)
);

CREATE INDEX IF NOT EXISTS idx_item_links_source ON work_item_links(source_item_id);
CREATE INDEX IF NOT EXISTS idx_item_links_target ON work_item_links(target_item_id);

-- ============================================================================
-- 9. BẢNG CÔNG VIỆC (BOARDS, COLUMNS & SWIMLANES)
-- ============================================================================

-- BẢNG: boards -> Bảng làm việc trực quan (Scrum Board cho Active Sprint hoặc Kanban Board liên tục)
CREATE TABLE IF NOT EXISTS boards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL DEFAULT 'SCRUM', -- SCRUM, KANBAN
    filter_jql TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_boards_project ON boards(project_id);

-- BẢNG: board_columns -> Các cột hiển thị trên Board (Thứ tự cột, Giới hạn WIP Limit Min/Max)
CREATE TABLE IF NOT EXISTS board_columns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    board_id UUID NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
    name VARCHAR(150) NOT NULL,
    column_order INT NOT NULL,
    min_wip_limit INT,
    max_wip_limit INT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_board_columns_board_order ON board_columns(board_id, column_order);

-- BẢNG: board_column_statuses -> Ánh xạ 1 Cột trên Board gom nhiều Status thực tế (VD: Cột "In Progress" gom cả IN_PROGRESS và IN_REVIEW)
CREATE TABLE IF NOT EXISTS board_column_statuses (
    column_id UUID NOT NULL REFERENCES board_columns(id) ON DELETE CASCADE,
    status_id UUID NOT NULL REFERENCES statuses(id) ON DELETE CASCADE,
    PRIMARY KEY (column_id, status_id)
);

-- BẢNG: board_swimlanes -> Phân nhóm Hàng ngang trên Board (Phân nhóm theo Epic, Assignee, Priority hoặc Custom JQL)
CREATE TABLE IF NOT EXISTS board_swimlanes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    board_id UUID NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
    name VARCHAR(150) NOT NULL,
    strategy VARCHAR(50) NOT NULL, -- BY_EPIC, BY_ASSIGNEE, BY_PRIORITY, CUSTOM_JQL
    custom_jql TEXT,
    swimlane_order INT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_swimlanes_board ON board_swimlanes(board_id, swimlane_order);

-- ============================================================================
-- 10. AUDIT CHANGELOGS, COMMENTS & ATTACHMENTS
-- ============================================================================

-- BẢNG: work_item_changelogs -> Nhật ký lưu vết thay đổi từng trường (Dùng để dựng lại biểu đồ Burndown/CFD ở mọi thời điểm)
CREATE TABLE IF NOT EXISTS work_item_changelogs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    work_item_id UUID NOT NULL REFERENCES work_items(id) ON DELETE CASCADE,
    field_name VARCHAR(100) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    changed_by UUID NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_changelog_item_time ON work_item_changelogs(work_item_id, changed_at);

-- BẢNG: work_item_comments -> Cây Bình luận và thảo luận trao đổi trên từng Work Item
CREATE TABLE IF NOT EXISTS work_item_comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    work_item_id UUID NOT NULL REFERENCES work_items(id) ON DELETE CASCADE,
    parent_comment_id UUID REFERENCES work_item_comments(id) ON DELETE CASCADE,
    author_id UUID NOT NULL,
    body TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_comments_item ON work_item_comments(work_item_id) WHERE deleted_at IS NULL;

-- BẢNG: work_item_attachments -> Danh sách Tệp tin đính kèm cho Work Item (URL, File size, Content type)
CREATE TABLE IF NOT EXISTS work_item_attachments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    work_item_id UUID NOT NULL REFERENCES work_items(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    file_url VARCHAR(1000) NOT NULL,
    file_size BIGINT NOT NULL,
    content_type VARCHAR(100),
    uploaded_by UUID NOT NULL,
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_attachments_item ON work_item_attachments(work_item_id);

-- ============================================================================
-- 11. CUSTOM FIELD DEFINITIONS, SAVED FILTERS, AUTOMATION & OUTBOX
-- ============================================================================

-- BẢNG: custom_field_definitions -> Định nghĩa Trường tùy chỉnh động (Kiểu TEXT, NUMBER, SELECT, MULTI_SELECT, DATE, USER, CHECKBOX, URL)
CREATE TABLE IF NOT EXISTS custom_field_definitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID REFERENCES projects(id) ON DELETE CASCADE,
    field_key VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    field_type VARCHAR(50) NOT NULL,
    options JSONB,
    validation_rules JSONB,
    applicable_type_ids UUID[],
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_proj_field_key UNIQUE(project_id, field_key)
);

CREATE INDEX IF NOT EXISTS idx_custom_field_defs_project ON custom_field_definitions(project_id);

-- BẢNG: saved_filters -> Bộ lọc câu truy vấn JQL đã lưu (Dùng cho Dashboard Gadget và chia sẻ bộ lọc)
CREATE TABLE IF NOT EXISTS saved_filters (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID REFERENCES projects(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    jql_query TEXT NOT NULL,
    owner_id UUID NOT NULL,
    is_shared BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_saved_filters_owner ON saved_filters(owner_id);

-- BẢNG: automation_rules -> Quy tắc Tự động hóa nội bộ (Cấu trúc Trigger -> Condition -> Action)
CREATE TABLE IF NOT EXISTS automation_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    trigger_type VARCHAR(100) NOT NULL,
    conditions_config JSONB,
    actions_config JSONB,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_automation_project ON automation_rules(project_id, is_active);

-- BẢNG: outbox_events -> Bảng Transactional Outbox phát hành sự kiện tin cậy lên Kafka (Chống lỗi Dual-Write)
CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    retry_count INT DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_outbox_status_time ON outbox_events(status, created_at) WHERE status = 'PENDING';
