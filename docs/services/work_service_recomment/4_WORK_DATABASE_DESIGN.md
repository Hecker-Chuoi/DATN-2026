# 🗄️ TÀI LIỆU THIẾT KẾ CƠ SỞ DỮ LIỆU TOÀN DIỆN — WORK SERVICE (JIRA-GRADE)
## DỰ ÁN: DATN-2026 — HỆ THỐNG QUẢN LÝ DỰ ÁN & CÔNG VIỆC DOANH NGHIỆP

> **Hệ quản trị CSDL:** PostgreSQL 16+  
> **Schema Migration:** Flyway (`V1__init_work_jira_schema.sql`)  
> **Tài liệu tham chiếu:** `2_WORK_MAIN_DOC.md`, `3_WORK_IMPLEMENTATION_PLAN.md`

---

## 📑 MỤC LỤC
1. [Sơ đồ Phân cấp Tổng thể (High-Level Hierarchy Diagram)](#1-sơ-đồ-phân-cấp-tổng-thể-high-level-hierarchy-diagram)
2. [Sơ đồ CSDL Phân hệ 1: Tổ chức, Dự án & Phân quyền (Org, Project & RBAC)](#2-sơ-đồ-csdl-phân-hệ-1-tổ-chức-dự-án--phân-quyền)
3. [Sơ đồ CSDL Phân hệ 2: Loại công việc & Máy trạng thái (Work Item Types & Workflow FSM)](#3-sơ-đồ-csdl-phân-hệ-2-loại-công-việc--máy-trạng-thái)
4. [Sơ đồ CSDL Phân hệ 3: Agile Scrum, Sprint, Component & Release Version](#4-sơ-đồ-csdl-phân-hệ-3-agile-scrum-sprint-component--release-version)
5. [Sơ đồ CSDL Phân hệ 4: Trọng tâm Work Items & Liên kết phụ thuộc (Work Items & Cross-links)](#5-sơ-đồ-csdl-phân-hệ-4-trọng-tâm-work-items--liên-kết-phụ-thuộc)
6. [Sơ đồ CSDL Phân hệ 5: Bảng trực quan (Boards, Columns, Swimlanes & WIP Limits)](#6-sơ-đồ-csdl-phân-hệ-5-bảng-trực-quan-boards-columns-swimlanes)
7. [Sơ đồ CSDL Phân hệ 6: Nhật ký Audit, Cộng tác, Mở rộng & Transactional Outbox](#7-sơ-đồ-csdl-phân-hệ-6-nhật-ký-audit-cộng-tác-mở-rộng--outbox)
8. [Bảng Tra cứu Tổng hợp 25 Bảng Dữ liệu & Khóa ngoại (Data Dictionary)](#8-bảng-tra-cứu-tổng-hợp-25-bảng-dữ-liệu--khóa-ngoại)

---

## 1. Sơ đồ Phân cấp Tổng thể (High-Level Hierarchy Diagram)

Sơ đồ thể hiện luồng phân cấp quản trị từ cấp độ toàn hệ thống xuống từng công việc chi tiết:

```mermaid
graph TD
    classDef global fill:#0f172a,stroke:#475569,stroke-width:2px,color:#fff;
    classDef team fill:#0f766e,stroke:#14b8a6,stroke-width:2px,color:#fff;
    classDef project fill:#0369a1,stroke:#38bdf8,stroke-width:2px,color:#fff;
    classDef workitem fill:#1e1b4b,stroke:#818cf8,stroke-width:2px,color:#fff;
    classDef epic fill:#581c87,stroke:#c084fc,stroke-width:2px,color:#fff;
    classDef std fill:#1e3a8a,stroke:#60a5fa,stroke-width:2px,color:#fff;
    classDef sub fill:#334155,stroke:#94a3b8,stroke-width:2px,color:#fff;

    WS["🏢 WORKSPACE — Identity Service<br/><i>Không gian làm việc tổ chức</i>"]:::global
    TEAM["👥 TEAMS — Work Service<br/><i>Nhóm làm việc đa chức năng</i>"]:::team
    PROJ["📁 PROJECTS — Work Service<br/><i>Dự án Scrum / Kanban</i>"]:::project
    WI["🗂️ WORK ITEMS — Bảng trung tâm work_items<br/><i>Thực thể hợp nhất Single-Table Inheritance</i>"]:::workitem

    L1["🟣 LEVEL 1: EPIC<br/><i>Sáng kiến lớn / Tính năng cấp cao</i>"]:::epic
    STORY["🟢 LEVEL 0: USER STORY<br/><i>Yêu cầu nghiệp vụ tính năng</i>"]:::std
    TASK["🔵 LEVEL 0: TECHNICAL TASK<br/><i>Công việc / Nhiệm vụ kỹ thuật</i>"]:::std
    BUG["🔴 LEVEL 0: BUG<br/><i>Lỗi kiểm thử & Vận hành</i>"]:::std
    L_SUB["⚪ LEVEL -1: SUB-TASK<br/><i>Nhiệm vụ con phân rã chi tiết</i>"]:::sub

    WS -->|1-to-N| TEAM
    TEAM -->|1-to-N| PROJ
    PROJ -->|Quản lý tập hợp| WI

    WI -->|Level 1| L1
    WI -->|Level 0| STORY
    WI -->|Level 0| TASK
    WI -->|Level 0| BUG

    L1 -.->|Parent của| STORY
    L1 -.->|Parent của| TASK
    L1 -.->|Parent của| BUG

    STORY -->|Phân rã 1-to-N| L_SUB
    TASK -->|Phân rã 1-to-N| L_SUB
    BUG -->|Phân rã 1-to-N| L_SUB
```

---

## 2. Sơ đồ CSDL Phân hệ 1: Tổ chức, Dự án & Phân quyền

Quản lý thông tin Đội nhóm (Team), Dự án (Project), Nhân bản mẫu (Template Clones) và Phân quyền nội bộ dự án (Project RBAC):

```mermaid
erDiagram
    TEAMS ||--o{ TEAM_MEMBERS : "has members"
    TEAMS ||--o{ PROJECTS : "owns"
    PROJECTS ||--o{ PROJECT_CLONES : "cloned as source"
    PROJECTS ||--o{ PROJECT_ROLES : "defines roles"
    PROJECT_ROLES ||--o{ ROLE_ASSIGNMENTS : "assigned to users"
    PROJECTS ||--o{ ROLE_ASSIGNMENTS : "scoped in"
    PROJECT_ROLES ||--o{ PERMISSION_SCHEME_ENTRIES : "grants permissions"
    PROJECTS ||--o{ PERMISSION_SCHEME_ENTRIES : "scoped in"

    TEAMS {
        uuid id PK
        uuid workspace_id "FK -> Identity Workspace"
        varchar name "Tên nhóm"
        text description
        uuid owner_id "Trưởng nhóm"
        timestamp created_at
        timestamp updated_at
    }

    TEAM_MEMBERS {
        uuid id PK
        uuid team_id FK
        uuid user_id "FK -> Identity User"
        varchar role "LEADER, MEMBER, VIEWER"
        timestamp joined_at
    }

    PROJECTS {
        uuid id PK
        uuid workspace_id "FK -> Identity Workspace"
        uuid team_id FK "FK -> teams (nullable)"
        varchar name "Tên dự án"
        varchar key UK "Mã tiền tố (VD: SCRUM, PROJ)"
        uuid lead_user_id "Trưởng dự án"
        varchar project_type "SOFTWARE_SCRUM, SOFTWARE_KANBAN"
        boolean is_template "Cờ dự án mẫu"
        date start_date
        date end_date
        uuid created_by
    }

    PROJECT_CLONES {
        uuid id PK
        uuid source_project_id FK "Dự án nguồn"
        uuid cloned_project_id FK "Dự án đích"
        uuid cloned_by
        timestamp cloned_at
    }

    PROJECT_ROLES {
        uuid id PK
        uuid project_id FK
        varchar name "Admin, PM, Dev, QA, Viewer"
        text description
    }

    ROLE_ASSIGNMENTS {
        uuid id PK
        uuid project_id FK
        uuid user_id "Người dùng"
        uuid role_id FK "Vai trò gán"
        timestamp assigned_at
    }

    PERMISSION_SCHEME_ENTRIES {
        uuid id PK
        uuid project_id FK
        uuid role_id FK
        varchar permission_key "CREATE_ISSUE, SPRINT_MANAGE..."
    }
```

---

## 3. Sơ đồ CSDL Phân hệ 2: Loại công việc & Máy trạng thái (Workflow FSM)

Động cơ Finite State Machine (FSM) kiểm soát các bước chuyển trạng thái hợp lệ, điều kiện bảo vệ (Guards/Validators) và hành động tự động (Post-functions):

```mermaid
erDiagram
    PROJECTS ||--o{ WORK_ITEM_TYPES : "defines types"
    PROJECTS ||--o{ WORKFLOWS : "owns workflows"
    PROJECTS ||--o{ WORKFLOW_SCHEMES : "configures schemes"
    WORKFLOW_SCHEMES ||--o{ WORKFLOW_SCHEME_MAPPINGS : "maps"
    WORK_ITEM_TYPES ||--o{ WORKFLOW_SCHEME_MAPPINGS : "bound to"
    WORKFLOWS ||--o{ WORKFLOW_SCHEME_MAPPINGS : "applied workflow"
    WORKFLOWS ||--o{ WORKFLOW_TRANSITIONS : "has transitions"
    STATUSES ||--o{ WORKFLOW_TRANSITIONS : "from_status"
    STATUSES ||--o{ WORKFLOW_TRANSITIONS : "to_status"

    WORK_ITEM_TYPES {
        uuid id PK
        uuid project_id FK
        varchar name "Epic, Story, Task, Bug, Subtask"
        varchar code "EPIC, STORY, TASK, BUG, SUBTASK"
        int hierarchy_level "1 (Epic), 0 (Standard), -1 (Subtask)"
        boolean is_subtask
        boolean is_system_default
    }

    STATUSES {
        uuid id PK
        varchar name "To Do, In Progress, Code Review, Done"
        varchar category "TODO, IN_PROGRESS, DONE"
        varchar color_hex
    }

    WORKFLOWS {
        uuid id PK
        uuid project_id FK
        varchar name "Standard Scrum Workflow"
        boolean is_default
    }

    WORKFLOW_TRANSITIONS {
        uuid id PK
        uuid workflow_id FK
        uuid from_status_id FK "Trạng thái bắt đầu (null: Any)"
        uuid to_status_id FK "Trạng thái đích đến"
        varchar name "Start Progress, Submit Review, Resolve"
        jsonb conditions_config "Điều kiện ai được bấm"
        jsonb validators_config "Kiểm tra bắt buộc (subtasks closed...)"
        jsonb post_functions_config "Hành động phụ (set resolution, auto-assign)"
    }

    WORKFLOW_SCHEMES {
        uuid id PK
        uuid project_id FK
        varchar name "Main Project Scheme"
    }

    WORKFLOW_SCHEME_MAPPINGS {
        uuid id PK
        uuid scheme_id FK
        uuid work_item_type_id FK
        uuid workflow_id FK
    }
```

---

## 4. Sơ đồ CSDL Phân hệ 3: Agile Scrum, Sprint, Component & Release Version

Mô hình quản lý chu kỳ lặp (Sprint), cố định số liệu báo cáo (Snapshots), phân hệ kiến trúc (Component) và phiên bản phát hành độc lập (Versions):

```mermaid
erDiagram
    PROJECTS ||--o{ SPRINTS : "plans sprints"
    SPRINTS ||--o| SPRINT_REPORT_SNAPSHOTS : "generates upon close"
    PROJECTS ||--o{ COMPONENTS : "structures architecture"
    PROJECTS ||--o{ VERSIONS : "manages releases"
    PROJECTS ||--o{ LABELS : "categorizes tags"

    SPRINTS {
        uuid id PK
        uuid project_id FK
        varchar name "Sprint 1, Sprint 2..."
        text goal "Mục tiêu Sprint"
        varchar state "FUTURE, ACTIVE, CLOSED"
        timestamp start_date
        timestamp end_date
        timestamp completed_date
    }

    SPRINT_REPORT_SNAPSHOTS {
        uuid id PK
        uuid sprint_id FK "1 Sprint có duy nhất 1 Snapshot (UK)"
        decimal total_story_points
        decimal completed_story_points
        decimal incomplete_story_points
        int total_items
        int completed_items
        jsonb snapshot_data "Lưu mảng items & timestamps vẽ chart vĩnh viễn"
        uuid closed_by
        timestamp closed_at
    }

    COMPONENTS {
        uuid id PK
        uuid project_id FK
        varchar name "Auth, Billing, Catalog, Core-API"
        text description
        uuid lead_user_id "Lead phụ trách phân hệ (Auto-Assignee)"
    }

    VERSIONS {
        uuid id PK
        uuid project_id FK
        varchar name "v1.0.0, v1.1.0-RC1"
        date release_date
        boolean is_released
        boolean is_archived
    }

    LABELS {
        uuid id PK
        uuid project_id FK
        varchar name "Frontend, Backend, Security, P0"
        varchar color_hex
    }
```

---

## 5. Sơ đồ CSDL Phân hệ 4: Trọng tâm Work Items & Liên kết phụ thuộc

Bảng `work_items` là trái tim của hệ thống theo mẫu Single-Table Inheritance kết hợp JSONB Dynamic Fields, mã tuần tự Key-Sequence và thứ tự LexoRank:

```mermaid
erDiagram
    PROJECTS ||--o{ WORK_ITEMS : "contains"
    WORK_ITEM_TYPES ||--o{ WORK_ITEMS : "type of"
    STATUSES ||--o{ WORK_ITEMS : "current status"
    SPRINTS ||--o{ WORK_ITEMS : "assigned sprint"
    COMPONENTS ||--o{ WORK_ITEMS : "primary component"
    VERSIONS ||--o{ WORK_ITEMS : "target fix version"
    WORK_ITEMS ||--o{ WORK_ITEMS : "parent-child (Level 1->0 hoặc 0-> -1)"
    
    WORK_ITEMS ||--o{ WORK_ITEM_COMPONENT_MAPS : "has multiple components"
    COMPONENTS ||--o{ WORK_ITEM_COMPONENT_MAPS : "mapped to"
    
    WORK_ITEMS ||--o{ WORK_ITEM_LABEL_MAPS : "has labels"
    LABELS ||--o{ WORK_ITEM_LABEL_MAPS : "mapped to"
    
    WORK_ITEMS ||--o{ WORK_ITEM_LINKS : "source issue"
    WORK_ITEMS ||--o{ WORK_ITEM_LINKS : "target issue"

    WORK_ITEMS {
        uuid id PK
        uuid project_id FK
        uuid type_id FK
        uuid status_id FK
        uuid parent_id FK "Tự tham chiếu Parent Issue"
        uuid sprint_id FK "Sprint hiện tại (null: Backlog)"
        varchar code UK "Mã hiển thị (VD: SCRUM-101)"
        varchar summary "Tiêu đề công việc"
        text description
        varchar priority "LOWEST, LOW, MEDIUM, HIGH, HIGHEST"
        varchar resolution "DONE, WONT_DO, DUPLICATE..."
        uuid reporter_id "Người tạo"
        uuid assignee_id "Người xử lý"
        decimal story_points "Điểm ước lượng Agile"
        bigint original_estimate_seconds
        bigint remaining_estimate_seconds
        bigint time_spent_seconds
        varchar rank "LexoRank (VD: 0|i00002:)"
        uuid component_id FK "Component chính"
        uuid fix_version_id FK "Phiên bản sửa đổi"
        jsonb custom_fields "Dữ liệu trường tùy biến linh hoạt"
        date start_date
        date due_date
        timestamp resolved_at
        int version "Optimistic Locking (@Version)"
        timestamp created_at
        timestamp updated_at
        timestamp deleted_at "Soft delete"
    }

    WORK_ITEM_LINKS {
        uuid id PK
        uuid source_item_id FK "Công việc gốc"
        uuid target_item_id FK "Công việc đích"
        varchar link_type "BLOCKS, RELATES_TO, DUPLICATES, CAUSES"
        uuid created_by
        timestamp created_at
    }

    WORK_ITEM_COMPONENT_MAPS {
        uuid work_item_id PK "Khóa ngoại -> work_items(id)"
        uuid component_id PK "Khóa ngoại -> components(id)"
    }

    WORK_ITEM_LABEL_MAPS {
        uuid work_item_id PK "Khóa ngoại -> work_items(id)"
        uuid label_id PK "Khóa ngoại -> labels(id)"
    }
```

---

## 6. Sơ đồ CSDL Phân hệ 5: Bảng trực quan (Boards, Columns, Swimlanes & WIP Limits)

Bảng trực quan hỗ trợ cấu hình đa cột, giới hạn WIP (Work In Progress Limit) và phân làn bơi (Swimlanes):

```mermaid
erDiagram
    PROJECTS ||--o{ BOARDS : "has boards"
    BOARDS ||--o{ BOARD_COLUMNS : "contains columns"
    BOARD_COLUMNS ||--o{ BOARD_COLUMN_STATUSES : "maps statuses"
    STATUSES ||--o{ BOARD_COLUMN_STATUSES : "mapped into column"
    BOARDS ||--o{ BOARD_SWIMLANES : "configures swimlanes"

    BOARDS {
        uuid id PK
        uuid project_id FK
        varchar name "Sprint Active Board / Team Kanban"
        varchar type "SCRUM, KANBAN"
        text filter_jql "Câu lọc JQL phụ"
    }

    BOARD_COLUMNS {
        uuid id PK
        uuid board_id FK
        varchar name "To Do, In Progress, In Review, Done"
        int column_order "Thứ tự từ trái qua phải (1, 2, 3...)"
        int min_wip_limit "Giới hạn tối thiểu thẻ"
        int max_wip_limit "Giới hạn tối đa thẻ (Báo động vàng/đỏ)"
    }

    BOARD_COLUMN_STATUSES {
        uuid column_id PK "Khóa ngoại -> board_columns(id)"
        uuid status_id PK "Khóa ngoại -> statuses(id)"
    }

    BOARD_SWIMLANES {
        uuid id PK
        uuid board_id FK
        varchar name "Làn bơi"
        varchar strategy "BY_EPIC, BY_ASSIGNEE, BY_PRIORITY, CUSTOM_JQL"
        text custom_jql
        int swimlane_order "Thứ tự sắp xếp dòng"
    }
```

---

## 7. Sơ đồ CSDL Phân hệ 6: Nhật ký Audit, Cộng tác, Mở rộng & Transactional Outbox

```mermaid
erDiagram
    WORK_ITEMS ||--o{ WORK_ITEM_CHANGELOGS : "audits history"
    WORK_ITEMS ||--o{ WORK_ITEM_COMMENTS : "has comments"
    WORK_ITEM_COMMENTS ||--o{ WORK_ITEM_COMMENTS : "threaded replies (parent-child)"
    WORK_ITEMS ||--o{ WORK_ITEM_ATTACHMENTS : "has files"
    PROJECTS ||--o{ CUSTOM_FIELD_DEFINITIONS : "defines custom schema"
    PROJECTS ||--o{ SAVED_FILTERS : "stores JQL queries"
    PROJECTS ||--o{ AUTOMATION_RULES : "configures triggers"

    WORK_ITEM_CHANGELOGS {
        uuid id PK
        uuid work_item_id FK
        varchar field_name "status, assignee, story_points, sprint..."
        text old_value
        text new_value
        uuid changed_by
        timestamp changed_at
    }

    WORK_ITEM_COMMENTS {
        uuid id PK
        uuid work_item_id FK
        uuid parent_comment_id FK "Hỗ trợ trả lời lồng nhau"
        uuid author_id
        text body
        timestamp created_at
        timestamp updated_at
        timestamp deleted_at
    }

    WORK_ITEM_ATTACHMENTS {
        uuid id PK
        uuid work_item_id FK
        varchar file_name
        varchar file_url
        bigint file_size
        varchar content_type
        uuid uploaded_by
        timestamp uploaded_at
    }

    CUSTOM_FIELD_DEFINITIONS {
        uuid id PK
        uuid project_id FK
        varchar field_key UK "customfield_1001"
        varchar name "Severity, Environment, Root Cause..."
        varchar field_type "TEXT, NUMBER, SELECT, DATE, USER..."
        jsonb options "Danh sách lựa chọn nếu là dropdown"
        jsonb validation_rules "Kiểm tra bắt buộc, regex..."
    }

    SAVED_FILTERS {
        uuid id PK
        uuid project_id FK
        varchar name "My Open High-Priority Bugs"
        text jql_query "type = 'BUG' AND priority IN ('HIGH', 'HIGHEST')"
        uuid owner_id
        boolean is_shared
    }

    AUTOMATION_RULES {
        uuid id PK
        uuid project_id FK
        varchar name "Auto-close Subtasks when Parent Done"
        varchar trigger_type "ISSUE_TRANSITIONED, ISSUE_CREATED"
        jsonb conditions_config
        jsonb actions_config
        boolean is_active
    }

    OUTBOX_EVENTS {
        uuid id PK
        varchar aggregate_type "WORK_ITEM, SPRINT, PROJECT"
        varchar aggregate_id
        varchar event_type "WORK_ITEM_UPDATED, SPRINT_CLOSED"
        jsonb payload
        varchar status "PENDING, SENT, FAILED"
        int retry_count
        timestamp created_at
        timestamp processed_at
    }
```

---

## 8. Bảng Tra cứu Tổng hợp 25 Bảng Dữ liệu & Khóa ngoại (Data Dictionary)

| STT | Tên bảng CSDL | Mục đích nghiệp vụ chính | Khóa ngoại chính (Foreign Keys) | Chỉ mục quan trọng |
| :---: | :--- | :--- | :--- | :--- |
| **1** | `teams` | Nhóm làm việc đa chức năng | `workspace_id` | `idx_teams_workspace`, `idx_teams_owner` |
| **2** | `team_members` | Thành viên tham gia nhóm & vai trò | `team_id -> teams(id)` | `uq_team_member(team_id, user_id)` |
| **3** | `projects` | Dự án trung tâm (Scrum/Kanban) | `team_id -> teams(id)` | `idx_projects_key` (UNIQUE), `idx_projects_workspace` |
| **4** | `project_clones` | Nhật ký nhân bản từ Template | `source_project_id`, `cloned_project_id` | `idx_project_clones_source` |
| **5** | `project_roles` | Danh mục vai trò trong dự án | `project_id -> projects(id)` | `idx_project_roles_project` |
| **6** | `role_assignments` | Phân quyền User vào Role dự án | `project_id`, `role_id` | `uq_user_project_role`, `idx_role_assignments_lookup` |
| **7** | `permission_scheme_entries`| Ma trận quyền hạn chi tiết | `project_id`, `role_id` | `uq_perm_entry`, `idx_perm_entries_lookup` |
| **8** | `work_item_types` | Định nghĩa loại công việc & Level | `project_id -> projects(id)` | `idx_work_item_types_level` |
| **9** | `statuses` | Trạng thái công việc toàn cục | Không | B-Tree trên `category` |
| **10**| `workflows` | Định nghĩa quy trình máy trạng thái | `project_id -> projects(id)` | `idx_workflows_project` |
| **11**| `workflow_transitions` | Các bước chuyển trạng thái | `workflow_id`, `from_status_id`, `to_status_id` | `idx_transitions_from_to` |
| **12**| `workflow_schemes` | Sơ đồ quy trình áp dụng dự án | `project_id -> projects(id)` | Khóa chính UUID |
| **13**| `workflow_scheme_mappings`| Gán Loại công việc với Workflow | `scheme_id`, `work_item_type_id`, `workflow_id` | `uq_scheme_item_type` |
| **14**| `sprints` | Chu kỳ Sprint Agile Scrum | `project_id -> projects(id)` | `idx_sprints_project_state(project_id, state)` |
| **15**| `sprint_report_snapshots`| Snapshot cố định số liệu Sprint | `sprint_id -> sprints(id)` (UNIQUE 1:1) | Khóa ngoại UNIQUE `sprint_id` |
| **16**| `components` | Phân hệ kiến trúc kỹ thuật | `project_id -> projects(id)` | `uq_project_component_name` |
| **17**| `versions` | Đợt phát hành Release | `project_id -> projects(id)` | `uq_project_version_name` |
| **18**| `labels` | Nhãn dán phân loại | `project_id -> projects(id)` | `idx_labels_project_name` |
| **19**| `work_items` | **TRỌNG TÂM**: Lưu trữ toàn bộ Issues | `project_id`, `type_id`, `status_id`, `parent_id`, `sprint_id` | `idx_work_items_rank`, GIN `idx_work_items_custom_fields_gin` |
| **20**| `work_item_component_maps`| Gán nhiều Component phụ | `work_item_id`, `component_id` | Composite PK `(work_item_id, component_id)` |
| **21**| `work_item_label_maps` | Gán nhiều Nhãn dán | `work_item_id`, `label_id` | Composite PK `(work_item_id, label_id)` |
| **22**| `work_item_links` | Quan hệ liên kết & phụ thuộc | `source_item_id`, `target_item_id` | `idx_item_links_source`, `idx_item_links_target` |
| **23**| `boards` | Bảng trực quan Scrum/Kanban | `project_id -> projects(id)` | `idx_boards_project` |
| **24**| `board_columns` | Cột hiển thị & Giới hạn WIP | `board_id -> boards(id)` | `idx_board_columns_board_order` |
| **25**| `board_column_statuses` | Gom Status vào Cột hiển thị | `column_id`, `status_id` | Composite PK `(column_id, status_id)` |
| **26**| `board_swimlanes` | Làn bơi phân nhóm ngang | `board_id -> boards(id)` | `idx_swimlanes_board` |
| **27**| `work_item_changelogs` | Nhật ký lưu vết thay đổi trường | `work_item_id -> work_items(id)` | `idx_changelog_item_time` |
| **28**| `work_item_comments` | Cây thảo luận & bình luận | `work_item_id`, `parent_comment_id` | `idx_comments_item` |
| **29**| `work_item_attachments` | Tệp tin đính kèm | `work_item_id -> work_items(id)` | `idx_attachments_item` |
| **30**| `custom_field_definitions`| Định nghĩa schema trường động | `project_id -> projects(id)` | `uq_proj_field_key(project_id, field_key)` |
| **31**| `saved_filters` | Bộ lọc câu truy vấn JQL | `project_id -> projects(id)` | `idx_saved_filters_owner` |
| **32**| `automation_rules` | Quy tắc tự động hóa nội bộ | `project_id -> projects(id)` | `idx_automation_project` |
| **33**| `outbox_events` | Bảng Transactional Outbox Kafka | Không | `idx_outbox_status_time` (`status='PENDING'`) |
