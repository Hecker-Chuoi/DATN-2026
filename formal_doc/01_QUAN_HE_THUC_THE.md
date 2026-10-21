# 01 — Tài liệu quan hệ giữa các thực thể

> Nguồn: `work-service/.../db/migration/V1..V7`, `workspace-service/.../db/migration/V1..V3`, các lớp `entity/*` của identity-service, work-service, workspace-service.

---

## Mục lục

1. [Tổng quan: ai sở hữu bảng nào](#1-tổng-quan-ai-sở-hữu-bảng-nào)
2. [Định danh người dùng — hai loại ID](#2-định-danh-người-dùng--hai-loại-id)
3. [Cụm Danh tính (identity-service)](#3-cụm-danh-tính-identity-service)
4. [Cụm Tổ chức: Workspace → Team → Project](#4-cụm-tổ-chức-workspace--team--project)
5. [Cụm Phân quyền dự án](#5-cụm-phân-quyền-dự-án)
6. [Cụm Lời mời](#6-cụm-lời-mời)
7. [Cụm Cấu hình quy trình (Workflow)](#7-cụm-cấu-hình-quy-trình-workflow)
8. [Cụm Board](#8-cụm-board)
9. [Cụm Kế hoạch: Sprint · Version · Component · Label](#9-cụm-kế-hoạch-sprint--version--component--label)
10. [Thực thể trung tâm: Work Item](#10-thực-thể-trung-tâm-work-item)
11. [Cụm Hoạt động trên Work Item](#11-cụm-hoạt-động-trên-work-item)
12. [Cụm Mẫu & Nhân bản](#12-cụm-mẫu--nhân-bản)
    - 12a. [Cụm Tiến độ dự án (V8 – V11)](#12a-cụm-tiến-độ-dự-án-v8--v11)
13. [Cụm Mở rộng (đã có bảng, chưa có nghiệp vụ)](#13-cụm-mở-rộng-đã-có-bảng-chưa-có-nghiệp-vụ)
14. [Bảng tổng hợp thực thể](#14-bảng-tổng-hợp-thực-thể)
15. [Ràng buộc toàn vẹn quan trọng](#15-ràng-buộc-toàn-vẹn-quan-trọng)
16. [Tham chiếu "mềm" (không có khóa ngoại)](#16-tham-chiếu-mềm-không-có-khóa-ngoại)

---

## 1. Tổng quan: ai sở hữu bảng nào

Cả ba service có dữ liệu cùng ghi vào **một database** `datn_main_db`, nhưng mỗi service "sở hữu" một nhóm bảng riêng và tự quản lý schema theo cách khác nhau.

```mermaid
flowchart LR
    subgraph ID["identity-service<br/>(Hibernate ddl-auto)"]
        U["users"]
        VT["verification_tokens"]
    end
    subgraph WS["workspace-service<br/>(Flyway riêng)"]
        W["workspaces"]
        WM["workspace_members"]
    end
    subgraph WK["work-service<br/>(Flyway riêng + ddl-auto)"]
        X["~33 bảng:<br/>teams, projects, work_items,<br/>sprints, boards, workflows..."]
    end
    DB[("datn_main_db")]
    ID --> DB
    WS --> DB
    WK --> DB
```

| Service | Số bảng | Quản lý schema | Bảng lịch sử Flyway |
|---------|---------|----------------|---------------------|
| identity-service | 2 | `ddl-auto: update` (không Flyway) | — |
| workspace-service | 2 | Flyway | riêng của workspace |
| work-service | ~33 | Flyway **và** `ddl-auto: update` | `flyway_work_schema_history` |

> ⚠️ Khóa ngoại `teams.workspace_id → workspaces.id` và `projects.workspace_id → workspaces.id` được tạo bởi migration **của workspace-service** (V3), vì phải chờ cả hai bảng tồn tại. Nếu workspace-service khởi động trước work-service trên DB mới, FK sẽ không bao giờ được tạo (xem `SchemaIntegrityChecker`).

---

## 2. Định danh người dùng — hai loại ID

Không có bảng nào ngoài `users` lưu thông tin người dùng. Các service khác chỉ lưu **UUID** của người dùng, nhưng **không thống nhất loại UUID**.

```mermaid
flowchart LR
    KC["Keycloak user<br/>sub = K"] -- "users.keycloak_id = K" --> U["users<br/>id = D"]
    U -- "D" --> WM["workspace_members.user_id<br/>workspaces.created_by"]
    KC -- "K" --> WK["Mọi cột user của work-service<br/>team_members.user_id, role_assignments.user_id,<br/>work_items.assignee_id, ..."]
```

| Nơi lưu | Loại ID | Lý do (theo mã nguồn) |
|---------|---------|------------------------|
| `workspace_members.user_id` | `users.id` (D) | `keycloak_id` có thể bị ghi lại khi email trùng → không ổn định |
| Mọi bảng work-service | Keycloak `sub` (K) | Lấy trực tiếp từ JWT, không cần tra cứu |

Hệ quả: khi work-service hỏi workspace-service "user X có trong workspace không", nó phải đổi K → D qua `CanonicalUserResolver` (gọi identity-service). Ngược lại, khi nhận id từ client, work-service đổi về K qua `ProjectMemberServiceImpl.canonicalUserId()`.

---

## 3. Cụm Danh tính (identity-service)

```mermaid
erDiagram
    USERS ||--o{ VERIFICATION_TOKENS : "có"
    USERS {
        uuid id PK
        varchar keycloak_id "sub của Keycloak"
        varchar username
        varchar email
        varchar first_name
        varchar last_name
        set roles "ADMIN, USER, USER_CREATE"
        enum status "ACTIVE, INACTIVE"
    }
    VERIFICATION_TOKENS {
        uuid id PK
        uuid user_id FK
        varchar token
        enum type "EMAIL_VERIFY, PASSWORD_RESET"
        timestamp expires_at
        timestamp used_at
    }
```

- Mật khẩu **không** lưu ở đây — do Keycloak quản lý.
- `status = INACTIVE` cho tới khi xác thực email (người dùng Google bỏ qua bước này).

---

## 4. Cụm Tổ chức: Workspace → Team → Project

### 4.1. Workspace và thành viên

```mermaid
erDiagram
    WORKSPACES ||--o{ WORKSPACE_MEMBERS : "có thành viên"
    WORKSPACES {
        uuid id PK
        varchar name
        varchar slug UK
        uuid created_by "users.id, chỉ audit"
        timestamp deleted_at "soft delete"
    }
    WORKSPACE_MEMBERS {
        uuid id PK
        uuid workspace_id FK
        uuid user_id "users.id"
        enum role "OWNER, ADMIN, MEMBER, GUEST"
        uuid invited_by
    }
```

- Ràng buộc `UNIQUE(workspace_id, user_id)`: một người một vai trò trong mỗi workspace, nhưng có thể ở nhiều workspace.
- Workspace **không có** cột `owner_id`; quyền sở hữu xác định hoàn toàn qua `workspace_members.role = OWNER`.

### 4.2. Team và thành viên

```mermaid
erDiagram
    WORKSPACES ||--o{ TEAMS : "chứa"
    TEAMS ||--o{ TEAM_MEMBERS : "có thành viên"
    TEAMS {
        uuid id PK
        uuid workspace_id FK
        varchar name
        uuid owner_id "sub người tạo"
    }
    TEAM_MEMBERS {
        uuid id PK
        uuid team_id FK
        uuid user_id "sub"
        enum role "LEADER, MEMBER, VIEWER"
    }
```

- Team **xóa cứng** (`ON DELETE CASCADE` sang `team_members`).
- "Trưởng nhóm" = `owner_id` **hoặc** thành viên có `role = LEADER` → hai nguồn sự thật.

### 4.3. Project gắn vào tổ chức

```mermaid
erDiagram
    WORKSPACES ||--o{ PROJECTS : "chứa"
    TEAMS |o--o{ PROJECTS : "sở hữu (tùy chọn)"
    PROJECTS {
        uuid id PK
        uuid workspace_id FK
        uuid team_id FK "nullable, SET NULL"
        varchar key UK "toàn hệ thống"
        varchar name
        uuid lead_user_id "sub"
        uuid created_by "sub"
        enum project_type "SOFTWARE_SCRUM, SOFTWARE_KANBAN, BUSINESS"
        enum default_assignee_type
        bool is_template
        enum template_category
        date start_date
        date end_date
        timestamp deleted_at "soft delete"
    }
```

- `team_id` để trống được (chỉ Global ADMIN tạo được dự án không team).
- `key` duy nhất **toàn hệ thống**, không phải trong workspace.

---

## 5. Cụm Phân quyền dự án

Không có bảng `permissions`; danh sách quyền là enum `PermissionKey` trong mã nguồn (15 quyền).

```mermaid
erDiagram
    PROJECTS ||--o{ PROJECT_ROLES : "định nghĩa"
    PROJECT_ROLES ||--o{ PERMISSION_SCHEME_ENTRIES : "được cấp"
    PROJECT_ROLES ||--o{ ROLE_ASSIGNMENTS : "gán cho"
    PROJECTS ||--o{ ROLE_ASSIGNMENTS : "thành viên"
    PROJECT_ROLES {
        uuid id PK
        uuid project_id FK
        varchar name "ADMINISTRATOR, MEMBER, VIEWER"
    }
    PERMISSION_SCHEME_ENTRIES {
        uuid id PK
        uuid project_id FK
        uuid role_id FK
        varchar permission_key "giá trị PermissionKey"
    }
    ROLE_ASSIGNMENTS {
        uuid id PK
        uuid project_id FK
        uuid user_id "sub"
        uuid role_id FK
    }
```

- `ROLE_ASSIGNMENTS` **chính là bảng "thành viên dự án"** — không có bảng `project_members` riêng.
- Lead và người tạo dự án có toàn quyền **ngầm định**, kể cả khi không có dòng trong `role_assignments`.

---

## 6. Cụm Lời mời

```mermaid
erDiagram
    INVITATIONS {
        uuid id PK
        enum type "TEAM, PROJECT"
        uuid target_id "teams.id HOẶC projects.id"
        uuid invitee_user_id "sub"
        uuid invited_by "sub"
        enum team_role "khi type=TEAM"
        uuid project_role_id "khi type=PROJECT"
        enum status "PENDING, ACCEPTED, DECLINED, CANCELLED"
    }
```

`target_id` là **khóa đa hình** (polymorphic) — trỏ tới `teams` hoặc `projects` tùy `type`, nên **không có khóa ngoại**. Chỉ mục duy nhất một phần đảm bảo mỗi người chỉ có một lời mời `PENDING` cho cùng một đích.

```mermaid
flowchart LR
    INV["invitations"] -- "type = TEAM" --> T["teams"]
    INV -- "type = PROJECT" --> P["projects"]
    INV -- "project_role_id" --> R["project_roles"]
```

---

## 7. Cụm Cấu hình quy trình (Workflow)

### 7.1. Loại công việc và trạng thái

```mermaid
erDiagram
    PROJECTS ||--o{ WORK_ITEM_TYPES : "định nghĩa"
    PROJECTS ||--o{ STATUSES : "định nghĩa"
    WORK_ITEM_TYPES {
        uuid id PK
        uuid project_id FK
        varchar code "EPIC, TASK, SUBTASK..."
        int hierarchy_level "1, 0, -1"
        bool is_subtask
        bool is_system_default
    }
    STATUSES {
        uuid id PK
        uuid project_id FK "nullable (status toàn cục cũ)"
        varchar name
        enum category "TODO, IN_PROGRESS, DONE"
    }
```

`category` là cầu nối giữa trạng thái tùy biến và các báo cáo (Burndown/Velocity chỉ hiểu `DONE`).

### 7.2. Workflow và bước chuyển

```mermaid
erDiagram
    PROJECTS ||--o{ WORKFLOWS : "có"
    WORKFLOWS ||--o{ WORKFLOW_TRANSITIONS : "gồm"
    STATUSES |o--o{ WORKFLOW_TRANSITIONS : "from (null = Create)"
    STATUSES ||--o{ WORKFLOW_TRANSITIONS : "to"
    WORKFLOW_TRANSITIONS {
        uuid id PK
        uuid workflow_id FK
        uuid from_status_id FK "null = bước khởi tạo"
        uuid to_status_id FK
        varchar name
        jsonb conditions_config
        jsonb validators_config
        jsonb post_functions_config
    }
```

### 7.3. Workflow Scheme (loại nào dùng workflow nào)

```mermaid
erDiagram
    PROJECTS ||--o{ WORKFLOW_SCHEMES : "có"
    WORKFLOW_SCHEMES ||--o{ WORKFLOW_SCHEME_MAPPINGS : "ánh xạ"
    WORK_ITEM_TYPES ||--o{ WORKFLOW_SCHEME_MAPPINGS : "loại"
    WORKFLOWS ||--o{ WORKFLOW_SCHEME_MAPPINGS : "dùng"
    WORKFLOW_SCHEME_MAPPINGS {
        uuid id PK
        uuid scheme_id FK
        uuid work_item_type_id FK
        uuid workflow_id FK
    }
```

Ràng buộc `UNIQUE(scheme_id, work_item_type_id)`: mỗi loại công việc chỉ có một workflow trong một scheme.

---

## 8. Cụm Board

```mermaid
erDiagram
    PROJECTS ||--o{ BOARDS : "có"
    BOARDS ||--o{ BOARD_COLUMNS : "gồm cột"
    BOARD_COLUMNS ||--o{ BOARD_COLUMN_STATUSES : "gom"
    STATUSES ||--o{ BOARD_COLUMN_STATUSES : "thuộc cột"
    BOARDS ||--o{ BOARD_SWIMLANES : "chia hàng"
    BOARDS {
        uuid id PK
        enum type "SCRUM, KANBAN"
        text filter_jql "chưa có engine"
    }
    BOARD_COLUMNS {
        uuid id PK
        int column_order
        int min_wip_limit
        int max_wip_limit
    }
    BOARD_SWIMLANES {
        uuid id PK
        enum strategy "BY_EPIC, BY_ASSIGNEE, BY_PRIORITY, CUSTOM_JQL"
        int swimlane_order
    }
```

Một cột có thể gom nhiều trạng thái (ví dụ "In Progress" gom cả `IN_PROGRESS` và `IN_REVIEW`). Thẻ hiển thị ở cột nào được suy ra từ `work_items.status_id`.

---

## 9. Cụm Kế hoạch: Sprint · Version · Component · Label

### 9.1. Sprint và snapshot báo cáo

```mermaid
erDiagram
    PROJECTS ||--o{ SPRINTS : "có"
    SPRINTS ||--o| SPRINT_REPORT_SNAPSHOTS : "đóng băng khi CLOSED"
    SPRINTS {
        uuid id PK
        uuid project_id FK
        enum state "FUTURE, ACTIVE, CLOSED"
        timestamp start_date
        timestamp end_date
        timestamp completed_date
        int version "optimistic lock"
    }
    SPRINT_REPORT_SNAPSHOTS {
        uuid id PK
        uuid sprint_id FK "UNIQUE"
        decimal total_story_points
        decimal completed_story_points
        jsonb snapshot_data
        uuid closed_by
    }
```

Chỉ mục duy nhất một phần `uq_sprints_one_active_per_project` → **mỗi dự án tối đa một Sprint ACTIVE**.

### 9.2. Version, Component, Label

```mermaid
erDiagram
    PROJECTS ||--o{ VERSIONS : "phát hành"
    PROJECTS ||--o{ COMPONENTS : "phân hệ"
    PROJECTS ||--o{ LABELS : "nhãn"
    VERSIONS {
        uuid id PK
        varchar name "UNIQUE trong dự án"
        date release_date
        bool is_released
    }
    COMPONENTS {
        uuid id PK
        varchar name "UNIQUE trong dự án"
        uuid lead_user_id
    }
    LABELS {
        uuid id PK
        varchar name "UNIQUE trong dự án"
        varchar color_hex
    }
```

> ⚠️ Ba bảng này có entity/repository nhưng **chưa có REST API** (xem tài liệu 05).

---

## 10. Thực thể trung tâm: Work Item

Một bảng duy nhất chứa mọi loại công việc (Epic, Story, Task, Bug, Subtask); loại được phân biệt qua `type_id`.

### 10.1. Work Item và các tham chiếu cấu hình

```mermaid
erDiagram
    PROJECTS ||--o{ WORK_ITEMS : "chứa"
    WORK_ITEM_TYPES ||--o{ WORK_ITEMS : "loại"
    STATUSES ||--o{ WORK_ITEMS : "trạng thái"
    SPRINTS |o--o{ WORK_ITEMS : "nằm trong"
    COMPONENTS |o--o{ WORK_ITEMS : "phân hệ chính"
    VERSIONS |o--o{ WORK_ITEMS : "fix version"
```

### 10.2. Cây cha – con

```mermaid
erDiagram
    WORK_ITEMS |o--o{ WORK_ITEMS : "parent_id"
```

```mermaid
flowchart TD
    E["Epic (level 1)"] --> T1["Story/Task/Bug (level 0)"]
    E --> T2["Story/Task/Bug (level 0)"]
    T1 --> S1["Subtask (level -1)"]
    T1 --> S2["Subtask (level -1)"]
```

Phân cấp 3 tầng được kiểm tra bởi `StrictHierarchyValidator` (cha phải đúng một cấp cao hơn).

### 10.3. Thuộc tính chính

| Nhóm | Cột |
|------|-----|
| Định danh | `id`, `code` (vd `WEB-12`, duy nhất trong dự án) |
| Nội dung | `summary`, `description`, `custom_fields` (JSONB) |
| Phân loại | `priority` (LOWEST → HIGHEST), `resolution` |
| Con người | `reporter_id`, `assignee_id` (đều là `sub`) |
| Ước lượng | `story_points`, `original_estimate_seconds`, `remaining_estimate_seconds`, `time_spent_seconds` |
| Thứ tự | `rank` (LexoRank, chuỗi so sánh được) |
| Thời gian | `start_date`, `due_date`, `resolved_at` |
| Kỹ thuật | `version` (optimistic lock), `deleted_at` (soft delete) |

---

## 11. Cụm Hoạt động trên Work Item

### 11.1. Liên kết giữa các công việc

```mermaid
erDiagram
    WORK_ITEMS ||--o{ WORK_ITEM_LINKS : "source"
    WORK_ITEMS ||--o{ WORK_ITEM_LINKS : "target"
    WORK_ITEM_LINKS {
        uuid id PK
        uuid source_item_id FK
        uuid target_item_id FK
        enum link_type "BLOCKS, RELATES_TO, DUPLICATES, CAUSES"
    }
```

`CHECK(source <> target)` và `DependencyCycleDetector` chặn vòng phụ thuộc `BLOCKS`.

### 11.2. Nhãn và phân hệ phụ (N–N)

```mermaid
erDiagram
    WORK_ITEMS ||--o{ WORK_ITEM_LABEL_MAPS : ""
    LABELS ||--o{ WORK_ITEM_LABEL_MAPS : ""
    WORK_ITEMS ||--o{ WORK_ITEM_COMPONENT_MAPS : ""
    COMPONENTS ||--o{ WORK_ITEM_COMPONENT_MAPS : ""
```

### 11.3. Bình luận, tệp đính kèm, nhật ký thay đổi

```mermaid
erDiagram
    WORK_ITEMS ||--o{ WORK_ITEM_COMMENTS : "thảo luận"
    WORK_ITEM_COMMENTS |o--o{ WORK_ITEM_COMMENTS : "trả lời"
    WORK_ITEMS ||--o{ WORK_ITEM_ATTACHMENTS : "đính kèm"
    WORK_ITEMS ||--o{ WORK_ITEM_CHANGELOGS : "lịch sử"
    WORK_ITEM_COMMENTS {
        uuid author_id
        text body
        timestamp deleted_at
    }
    WORK_ITEM_ATTACHMENTS {
        varchar file_url "MinIO"
        bigint file_size
        uuid uploaded_by
    }
    WORK_ITEM_CHANGELOGS {
        varchar field_name "STATUS, ASSIGNEE..."
        text old_value
        text new_value
        uuid changed_by
        timestamp changed_at
    }
```

`work_item_changelogs` là nguồn để **dựng lại** Burndown/CFD tại mọi thời điểm (`WorkItemStateReplayer`).

---

## 12. Cụm Mẫu & Nhân bản

```mermaid
erDiagram
    PROJECTS ||--o{ PROJECT_CLONES : "là nguồn"
    PROJECTS ||--o| PROJECT_CLONES : "là đích (tối đa 1)"
    PROJECT_CLONES {
        uuid id PK
        uuid source_project_id FK
        uuid cloned_project_id FK "UNIQUE"
        uuid cloned_by
        enum clone_type "FROM_TEMPLATE, SAVE_AS_TEMPLATE, DUPLICATE"
        int date_offset_days
        jsonb clone_options
        jsonb clone_stats
    }
```

- Dự án mẫu **không phải bảng riêng**: là `projects` với `is_template = TRUE`.
- `CHECK(source <> cloned)`; mỗi dự án đích chỉ sinh từ đúng một nguồn.

---

## 12a. Cụm Tiến độ dự án (V8 – V11)

> Thêm bởi kế hoạch [06A](06A_KE_HOACH_TRIEN_KHAI_TIEN_DO.md). Chi tiết nghiệp vụ ở tài liệu 02 – Phần E.

### 12a.1. Cột mới trên `projects`

```mermaid
erDiagram
    PROJECTS {
        varchar status "ACTIVE, ON_HOLD, COMPLETED, ARCHIVED"
        varchar progress_method "STORY_POINTS, ITEM_COUNT, ESTIMATE"
        timestamp completed_at
        varchar health_override "null = dùng giá trị tự động"
        text health_override_reason
        int at_risk_threshold_pct "mặc định 10"
        int off_track_threshold_pct "mặc định 20"
    }
```

### 12a.2. Số liệu hằng ngày

```mermaid
erDiagram
    PROJECTS ||--o{ PROJECT_PROGRESS_SNAPSHOTS : "mỗi ngày một dòng"
    PROJECT_PROGRESS_SNAPSHOTS {
        uuid project_id FK
        date snapshot_date "UNIQUE cùng project_id"
        int total_items
        int done_items
        decimal total_points
        decimal done_points
        decimal progress_pct
        decimal expected_pct
        varchar health
    }
```

### 12a.3. Milestone

```mermaid
erDiagram
    PROJECTS ||--o{ PROJECT_MILESTONES : "có"
    PROJECT_MILESTONES ||--o{ MILESTONE_ITEMS : "gồm"
    WORK_ITEMS ||--o{ MILESTONE_ITEMS : "thuộc"
    PROJECT_MILESTONES {
        uuid id PK
        varchar name
        date target_date
        varchar status "PLANNED, ACHIEVED, MISSED"
        timestamp achieved_at
    }
```

### 12a.4. Baseline và báo cáo tình trạng

```mermaid
erDiagram
    PROJECTS ||--o{ PROJECT_BASELINES : "chốt"
    PROJECT_BASELINES ||--o{ BASELINE_ITEMS : "chứa"
    PROJECTS ||--o{ PROJECT_STATUS_UPDATES : "báo cáo"
    PROJECT_BASELINES {
        uuid id PK
        varchar name
        bool is_active "tối đa 1 / dự án"
        date project_end_date
    }
    BASELINE_ITEMS {
        uuid work_item_id "không FK: giữ cả việc đã xóa"
        date planned_start
        date planned_due
        decimal planned_points
    }
    PROJECT_STATUS_UPDATES {
        varchar health
        text summary
        jsonb metrics "số liệu chụp lúc viết"
    }
```

| Bảng | Có trong mẫu / clone? | Lý do |
|------|:---:|-------|
| `project_milestones`, `milestone_items` | ✅ | Là một phần của kế hoạch; ngày đích được dời theo offset, trạng thái về PLANNED |
| `project_progress_snapshots` | ❌ | Lịch sử số liệu của dự án nguồn |
| `project_baselines`, `baseline_items` | ❌ | Kế hoạch đã chốt của dự án nguồn |
| `project_status_updates` | ❌ | Lịch sử báo cáo |

---

## 13. Cụm Mở rộng (đã có bảng, chưa có nghiệp vụ)

```mermaid
erDiagram
    PROJECTS ||--o{ CUSTOM_FIELD_DEFINITIONS : ""
    PROJECTS ||--o{ SAVED_FILTERS : ""
    PROJECTS ||--o{ AUTOMATION_RULES : ""
    OUTBOX_EVENTS {
        varchar aggregate_type
        varchar event_type
        jsonb payload
        enum status "PENDING, PROCESSED, FAILED"
    }
```

| Bảng | Có ghi dữ liệu? | Có API? | Ghi chú |
|------|-----------------|---------|---------|
| `custom_field_definitions` | Chỉ khi clone | ❌ | `work_items.custom_fields` lưu giá trị nhưng không kiểm tra theo định nghĩa |
| `saved_filters` | ❌ | ❌ | Cột `jql_query` chưa có engine |
| `automation_rules` | Chỉ khi clone | ❌ | Không có bộ chạy rule |
| `outbox_events` | ✅ (Sprint start/close) | — | Không có tiến trình đẩy lên Kafka |

---

## 14. Bảng tổng hợp thực thể

| Thực thể | Service | Phạm vi | Kiểu xóa | API CRUD |
|----------|---------|---------|----------|----------|
| User | identity | Toàn hệ thống | Cứng (+ dọn tham chiếu) | ✅ |
| Workspace | workspace | Gốc | Mềm | ✅ |
| WorkspaceMember | workspace | Workspace | Cứng | ✅ |
| Team | work | Workspace | Cứng | ✅ |
| TeamMember | work | Team | Cứng | ✅ (qua lời mời) |
| Project | work | Workspace | Mềm | ✅ |
| ProjectRole | work | Project | Cascade | ⚠️ chỉ đọc |
| PermissionSchemeEntry | work | Project | Cascade | ❌ |
| RoleAssignment | work | Project | Cứng | ✅ |
| Invitation | work | Team/Project | Đổi trạng thái | ✅ |
| WorkItemType | work | Project | Cứng | ✅ |
| Status | work | Project | Cứng | ✅ |
| Workflow / Transition / Scheme | work | Project | Cứng | ✅ |
| Board / Column / Swimlane | work | Project | Cứng | ✅ |
| Sprint | work | Project | Cứng (chỉ FUTURE) | ✅ |
| SprintReportSnapshot | work | Sprint | Cascade | Chỉ đọc qua báo cáo |
| Version / Component / Label | work | Project | Cascade | ❌ |
| WorkItem | work | Project | Mềm | ✅ |
| WorkItemLink | work | WorkItem | Cứng | ✅ |
| WorkItemComment | work | WorkItem | Mềm | ✅ |
| WorkItemAttachment | work | WorkItem | Cứng | ✅ |
| WorkItemChangelog | work | WorkItem | Cascade | Chỉ đọc |
| ProjectClone | work | Project | Cascade | Gián tiếp |
| CustomField / SavedFilter / AutomationRule | work | Project | Cascade | ❌ |
| OutboxEvent | work | — | — | — |

---

## 15. Ràng buộc toàn vẹn quan trọng

| Ràng buộc | Bảng | Ý nghĩa nghiệp vụ |
|-----------|------|-------------------|
| `UNIQUE(key)` | projects | Mã dự án duy nhất toàn hệ thống |
| `UNIQUE(project_id, code)` | work_items | Mã công việc duy nhất trong dự án |
| `UNIQUE(slug)` | workspaces | Đường dẫn workspace duy nhất |
| `UNIQUE(workspace_id, user_id)` | workspace_members | Một vai trò / người / workspace |
| `UNIQUE(team_id, user_id)` | team_members | Một vai trò / người / team |
| `UNIQUE(project_id, user_id, role_id)` | role_assignments | Không gán trùng vai trò |
| `UNIQUE(project_id) WHERE state='ACTIVE'` | sprints | Tối đa 1 Sprint đang chạy |
| `UNIQUE(type, target_id, invitee) WHERE PENDING` | invitations | Không mời trùng khi đang chờ |
| `UNIQUE(cloned_project_id)` | project_clones | Mỗi dự án chỉ có một nguồn |
| `CHECK(source <> target)` | work_item_links | Không tự liên kết |
| Sequence `work_item_seq_<key>` | PostgreSQL | Sinh mã công việc nguyên tử |

---

## 16. Tham chiếu "mềm" (không có khóa ngoại)

Các cột dưới đây lưu UUID nhưng **không có FK**, nên DB không tự bảo vệ tính toàn vẹn — mã ứng dụng phải tự làm.

```mermaid
flowchart LR
    subgraph User["Trỏ tới người dùng"]
        A["teams.owner_id"]
        B["team_members.user_id"]
        C["role_assignments.user_id"]
        D["projects.lead_user_id / created_by"]
        E["work_items.assignee_id / reporter_id"]
        F["workspace_members.user_id"]
    end
    subgraph Poly["Đa hình"]
        G["invitations.target_id"]
        H["invitations.project_role_id"]
    end
    subgraph Arr["Mảng"]
        I["custom_field_definitions.applicable_type_ids"]
    end
```

Khi xóa người dùng, identity-service tự chạy SQL dọn `team_members`, `role_assignments` và bỏ gán `work_items` (`WorkDataCleanupService`) — nhưng **không** dọn `workspace_members`, `invitations`, `teams.owner_id`, `projects.lead_user_id`.
