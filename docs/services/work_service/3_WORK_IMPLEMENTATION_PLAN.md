# 📋 KẾ HOẠCH TRIỂN KHAI CHI TIẾT WORK SERVICE (JIRA-GRADE ENGINE)
## DỰ ÁN: DATN-2026 — QUẢN LÝ DỰ ÁN & CÔNG VIỆC DOANH NGHIỆP

> **Tài liệu căn cứ:** [`2_WORK_MAIN_DOC.md`](file:///d:/DATN-2026/docs/services/work_service/2_WORK_MAIN_DOC.md)  
> **Mục tiêu:** Chia nhỏ toàn bộ quá trình tái cấu trúc & hiện thực `work-service` thành **9 Giai đoạn với 24 Bước nhỏ (Actionable Steps)** có thể thực hiện độc lập, kiểm thử cuốn chiếu và bàn giao từng phần.

---

## 🗺️ TỔNG QUAN LỘ TRÌNH TRIỂN KHAI (ROADMAP SUMMARY)

```
┌────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│ GIAI ĐOẠN 1: CSDL & LÕI THỰC THỂ (Database Migration & Core Entity Layer)                              │
│ ├── Bước 1.1: Migration DDL PostgreSQL (Flyway/Liquibase) tạo 18+ bảng & Indexing (GIN, Sequence)     │
│ ├── Bước 1.2: Hiện thực bộ JPA Entities & Enums chuẩn hóa theo mô hình Jira                            │
│ └── Bước 1.3: Xây dựng tầng Spring Data JPA Repositories & BaseSpecification                           │
├────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ GIAI ĐOẠN 2: THUẬT TOÁN NỀN TẢNG (Core Domain Algorithms & Utilities)                                  │
│ ├── Bước 2.1: Bộ sinh mã việc nguyên tử theo Project Key (AtomicKeySequenceGenerator)                  │
│ ├── Bước 2.2: Thuật toán xếp thứ tự kéo thả O(1) (LexoRankService)                                     │
│ ├── Bước 2.3: Bộ kiểm soát phân cấp nghiêm ngặt 3 tầng (StrictHierarchyValidator)                      │
│ └── Bước 2.4: Bộ phát hiện chu trình khóa liên kết công việc (DependencyCycleDetector)                 │
├────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ GIAI ĐOẠN 3: DỰ ÁN, LOẠI CÔNG VIỆC & PHÂN QUYỀN RBAC (Project, Work Item Types & RBAC)                 │
│ ├── Bước 3.1: DTOs & Validation Requests/Responses cho Project & WorkItemType                          │
│ ├── Bước 3.2: Project Service & Controller (Kèm logic Seed dữ liệu mặc định khi tạo mới)               │
│ └── Bước 3.3: Phân quyền hạt mịn RBAC nội bộ Project (ProjectRole, PermissionGuard AOP)                │
├────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ GIAI ĐOẠN 4: QUY TRÌNH & MÁY TRẠNG THÁI (Dynamic Workflow Engine - FSM)                                │
│ ├── Bước 4.1: DTOs & Mapping cho Status, Workflow, Transition, Scheme                                  │
│ ├── Bước 4.2: Máy trạng thái Finite State Machine (Xử lý Condition, Validator, PostFunction)          │
│ └── Bước 4.3: Workflow Service & Controller (API lấy transition hợp lệ & thực thi chuyển trạng thái)   │
├────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ GIAI ĐOẠN 5: QUẢN TRỊ CÔNG VIỆC & TRƯỜNG TÙY CHỈNH (Work Items, Changelogs & Custom Fields)            │
│ ├── Bước 5.1: DTOs cho Work Item đa năng & JSONB Custom Fields                                         │
│ ├── Bước 5.2: Work Item Service & Controller (CRUD, Rank Update, Optimistic Lock Reconcile)            │
│ ├── Bước 5.3: Lắng nghe & Lưu vết kiểm toán tự động (AOP / EntityListener Changelog)                   │
│ └── Bước 5.4: Quản lý Bình luận, Tệp đính kèm & Liên kết (Comments, Attachments, Links)                │
├────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ GIAI ĐOẠN 6: SPRINT, BACKLOG & BẢNG CÔNG VIỆC (Sprint, Backlog & Scrum/Kanban Boards)                  │
│ ├── Bước 6.1: Sprint Service & Controller (Start, Close Sprint Rollover, Snapshot)                     │
│ ├── Bước 6.2: Board Engine Service & Controller (Scrum/Kanban Data, WIP Limits, Swimlanes)             │
│ └── Bước 6.3: Bộ tính toán Báo cáo Agile (Burndown Chart, Velocity, Cumulative Flow Diagram)           │
├────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ GIAI ĐOẠN 7: PHÂN HỆ, PHIÊN BẢN & NHÂN BẢN DỰ ÁN (Components, Versions & Project Cloning Engine)       │
│ ├── Bước 7.1: Components (Auto-assign Lead) & Versions (Auto Release Notes Generator)                  │
│ ├── Bước 7.2: Labels Service (On-the-fly Normalization)                                                │
│ └── Bước 7.3: Two-Pass Dynamic Timeline Shifting Project Cloning Engine (Nhân bản từ Template)         │
├────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ GIAI ĐOẠN 8: TRUY VẤN JQL, TỰ ĐỘNG HÓA & REALTIME (JQL, Automation, WebSocket & Outbox)                │
│ ├── Bước 8.1: JQL Parser Engine (AST -> JPA Criteria / JSONB query) & Saved Filters                    │
│ ├── Bước 8.2: Lightweight Event-Driven Automation Engine (Trigger -> Condition -> Action)              │
│ ├── Bước 8.3: STOMP WebSocket + Redis Pub/Sub Backplane & Ephemeral Live Presence                      │
│ └── Bước 8.4: Transactional Outbox Poller & Kafka Event Publisher                                      │
├────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ GIAI ĐOẠN 9: KIỂM THỬ TÍCH HỢP & HOÀN TẤT TÀI LIỆU (Integration Testing & Documentation)               │
│ ├── Bước 9.1: Bộ kiểm thử tự động Unit Tests & Integration Tests (Postman Collection / MockMvc)        │
│ └── Bước 9.2: Swagger / OpenAPI 3.0 UI Document & Cập nhật README bàn giao                             │
└────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 🛠️ CHI TIẾT TỪNG GIAI ĐOẠN & CÁC BƯỚC THỰC THI (ACTIONABLE STEPS)

---

### GIAI ĐOẠN 1: CSDL & LÕI THỰC THỂ (DATABASE & ENTITIES)

#### 🔹 Bước 1.1: Migration DDL PostgreSQL tạo 18+ bảng & Indexing
* **Mục tiêu:** Tạo file Migration script chuẩn cho PostgreSQL 16 (Flyway: `V1__init_work_jira_schema.sql`).
* **Nhiệm vụ cụ thể:**
  1. Tạo toàn bộ 18 bảng: `projects`, `project_roles`, `role_assignments`, `permission_scheme_entries`, `work_item_types`, `statuses`, `workflows`, `workflow_transitions`, `workflow_schemes`, `workflow_scheme_mappings`, `sprints`, `sprint_report_snapshots`, `components`, `versions`, `labels`, `work_items`, `work_item_component_maps`, `work_item_label_maps`, `work_item_links`, `boards`, `board_columns`, `board_column_statuses`, `board_swimlanes`, `work_item_changelogs`, `work_item_comments`, `work_item_attachments`, `custom_field_definitions`, `saved_filters`, `automation_rules`, `outbox_events`, `project_clones`.
  2. Tạo các Index quan trọng: B-Tree trên `(project_id, rank)`, `(project_id, state)`, và **GIN Index** trên `custom_fields JSONB`.
* **Tiêu chí nghiệm thu (Definition of Done - DoD):**
  - Chạy migration không lỗi trên PostgreSQL, sinh đầy đủ bảng và khóa ngoại.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 1.1: Tạo file migration V1__init_work_jira_schema.sql cho PostgreSQL."`

#### 🔹 Bước 1.2: Hiện thực bộ JPA Entities & Enums chuẩn hóa
* **Mục tiêu:** Tạo đầy đủ các Java Entity classes tương ứng trong package `com.datn.workservice.entity` và `enums`.
* **Nhiệm vụ cụ thể:**
  - Tạo các Enums: `HierarchyLevel`, `StatusCategory` (`TODO`, `IN_PROGRESS`, `DONE`), `WorkItemPriority`, `SprintState` (`FUTURE`, `ACTIVE`, `CLOSED`), `BoardType` (`SCRUM`, `KANBAN`), `SwimlaneStrategy`, `LinkType` (`BLOCKS`, `RELATES_TO`, `DUPLICATES`, `CAUSES`), `CustomFieldType`, `OutboxStatus`.
  - Tạo các Entity JPA: `Project`, `WorkItem`, `WorkItemType`, `Status`, `Workflow`, `WorkflowTransition`, `WorkflowScheme`, `Sprint`, `SprintReportSnapshot`, `Component`, `Version`, `Label`, `WorkItemLink`, `Board`, `BoardColumn`, `BoardSwimlane`, `WorkItemChangelog`, `WorkItemComment`, `WorkItemAttachment`, `CustomFieldDefinition`, `SavedFilter`, `AutomationRule`, `OutboxEvent`, `ProjectClone`.
  - Cấu hình `@Version private Integer version` trên `WorkItem` cho Optimistic Locking.
  - Cấu hình `@Type(JsonBinaryType.class)` hoặc `@JdbcTypeCode(SqlTypes.JSON)` trên các cột JSONB.
* **Tiêu chí nghiệm thu (DoD):**
  - Dự án compile thành công không có lỗi JPA mapping.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 1.2: Tạo bộ Java Entities và Enums cho Work Service."`

#### 🔹 Bước 1.3: Xây dựng tầng Spring Data JPA Repositories
* **Mục tiêu:** Tạo các Repository interfaces cho toàn bộ các thực thể, tích hợp `JpaSpecificationExecutor`.
* **Nhiệm vụ cụ thể:**
  - Tạo Repositories trong `com.datn.workservice.repository`.
  - Viết các custom query: Tìm Sprint active, tìm WorkItem theo rank, tìm Column ánh xạ Status, tìm Changelog theo thời gian...
* **Tiêu chí nghiệm thu (DoD):**
  - Tất cả các repository kế thừa `JpaRepository` và `JpaSpecificationExecutor`.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 1.3: Tạo các JPA Repositories cho Work Service."`

---

### GIAI ĐOẠN 2: THUẬT TOÁN NỀN TẢNG (CORE DOMAIN ALGORITHMS & UTILS)

#### 🔹 Bước 2.1: Bộ sinh mã việc nguyên tử theo Project Key (`AtomicKeySequenceGenerator`)
* **Mục tiêu:** Sinh mã công việc tuần tự không trùng lặp theo từng dự án (`PRJ-1`, `PRJ-2`).
* **Nhiệm vụ cụ thể:**
  - Tạo Service `AtomicKeySequenceGenerator` gọi hàm PostgreSQL `nextval('work_item_seq_' || projectKey)`.
  - Viết hàm tự động tạo Sequence `CREATE SEQUENCE IF NOT EXISTS work_item_seq_KEY` khi tạo Project mới.
* **Tiêu chí nghiệm thu (DoD):**
  - Chạy test sinh 100 mã đồng thời không bị trùng mã và không nhảy cách quãng.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 2.1: Xây dựng AtomicKeySequenceGenerator."`

#### 🔹 Bước 2.2: Thuật toán xếp thứ tự kéo thả $O(1)$ (`LexoRankService`)
* **Mục tiêu:** Sinh chuỗi LexoRank (Base-36) để chèn phần tử vào giữa 2 phần tử bất kỳ mà chỉ cần update 1 bản ghi.
* **Nhiệm vụ cụ thể:**
  - Tạo `LexoRankService` với các phương thức: `generateInitialRank()`, `calculateBetween(String prevRank, String nextRank)`, `calculateBefore(String rank)`, `calculateAfter(String rank)`.
* **Tiêu chí nghiệm thu (DoD):**
  - Unit test chèn hàng trăm lần liên tiếp vẫn sinh ra chuỗi có thứ tự từ điển (`Comparable.compareTo`) tăng dần chính xác.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 2.2: Xây dựng LexoRankService."`

#### 🔹 Bước 2.3: Bộ kiểm soát phân cấp 3 tầng (`StrictHierarchyValidator`)
* **Mục tiêu:** Enforce quy tắc phân cấp: Epic (1) $\rightarrow$ Story/Task/Bug (0) $\rightarrow$ Sub-task (-1).
* **Nhiệm vụ cụ thể:**
  - Chặn Sub-task có con, chặn Epic có cha, chặn gắn cha sai cấp độ.
  - Chống chu trình (A là cha B, B là cha A).
* **Tiêu chí nghiệm thu (DoD):**
  - Ném `BadRequestException` hoặc `InvalidHierarchyException` rõ ràng khi vi phạm.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 2.3: Xây dựng StrictHierarchyValidator."`

#### 🔹 Bước 2.4: Bộ phát hiện chu trình liên kết (`DependencyCycleDetector`)
* **Mục tiêu:** Chặn tạo liên kết kiểu `BLOCKS` tạo thành vòng tròn kín.
* **Nhiệm vụ cụ thể:**
  - Sử dụng thuật toán DFS duyệt đồ thị liên kết `work_item_links`.
* **Tiêu chí nghiệm thu (DoD):**
  - Báo lỗi ngay lập tức khi phát hiện chu trình khóa $A \rightarrow B \rightarrow C \rightarrow A$.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 2.4: Xây dựng DependencyCycleDetector."`

---

### GIAI ĐOẠN 3: DỰ ÁN, LOẠI CÔNG VIỆC & PHÂN QUYỀN RBAC (PROJECT & RBAC)

#### 🔹 Bước 3.1: DTOs & Validation Requests/Responses cho Project & WorkItemType
* **Mục tiêu:** Tạo các class Request/Response DTOs chuẩn Jakarta Validation (`@NotBlank`, `@Size`).
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 3.1: Tạo DTOs cho Project và WorkItemType."`

#### 🔹 Bước 3.2: Project Service & Controller (Kèm logic Seed dữ liệu mặc định)
* **Mục tiêu:** CRUD Project và tự động khởi tạo hệ sinh thái ban đầu khi tạo một Project mới:
  1. Tạo sequence mã task: `work_item_seq_{KEY}`.
  2. Tạo 5 WorkItemTypes chuẩn: `Epic`, `Story`, `Task`, `Bug`, `Sub-task`.
  3. Tạo Workflow mặc định kèm các trạng thái: `To Do`, `In Progress`, `In Review`, `Done`.
  4. Tạo Board mặc định (Scrum hoặc Kanban tùy `project_type`) với 4 cột ánh xạ tương ứng.
  5. Tạo 4 Project Roles mặc định: `Project Admin`, `PM`, `Developer`, `Viewer`.
* **Tiêu chí nghiệm thu (DoD):**
  - Gọi API `POST /api/v1/work/projects` tạo ra đầy đủ cả Project lẫn hệ sinh thái mặc định đi kèm.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 3.2: Xây dựng ProjectService và ProjectController kèm logic Seed mặc định."`

#### 🔹 Bước 3.3: Phân quyền hạt mịn RBAC nội bộ Project (`ProjectPermissionGuard`)
* **Mục tiêu:** Xây dựng AOP Aspect / Security Guard kiểm tra quyền hạn của User trong Project tương ứng trước khi cho phép gọi API.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 3.3: Xây dựng ProjectPermissionGuard và RoleAssignmentService."`

---

### GIAI ĐOẠN 4: QUY TRÌNH & MÁY TRẠNG THÁI (DYNAMIC WORKFLOW ENGINE)

#### 🔹 Bước 4.1: DTOs & Mapping cho Workflow, Status, Transition
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 4.1: Tạo DTOs cho Workflow và Status."`

#### 🔹 Bước 4.2: Máy trạng thái Finite State Machine (`WorkflowEngine`)
* **Mục tiêu:** Xử lý logic chuyển đổi trạng thái:
  - **Kiểm tra Condition**: Người dùng có quyền chuyển không? Có phải Assignee không?
  - **Kiểm tra Validator**: Có thiếu trường bắt buộc không? Các sub-tasks đã Done hết chưa?
  - **Thực thi Post-function**: Tự động gán `resolved_at`, tự động chuyển trạng thái Story cha khi tất cả Sub-tasks Done.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 4.2: Xây dựng Finite State Machine WorkflowEngine."`

#### 🔹 Bước 4.3: Workflow Service & Controller
* **Mục tiêu:**
  - `GET /api/v1/work/items/{itemId}/transitions`: Lấy danh sách nút chuyển hợp lệ cho user hiện tại.
  - `POST /api/v1/work/items/{itemId}/transitions`: Thực hiện chuyển trạng thái.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 4.3: Xây dựng WorkflowService và WorkflowController."`

---

### GIAI ĐOẠN 5: QUẢN TRỊ CÔNG VIỆC, LỊCH SỬ & TRƯỜNG TÙY CHỈNH (WORK ITEMS)

#### 🔹 Bước 5.1: DTOs cho Work Item & Custom Fields
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 5.1: Tạo DTOs cho WorkItem và CustomFields."`

#### 🔹 Bước 5.2: Work Item Service & Controller (CRUD, Rank, Optimistic Lock)
* **Mục tiêu:**
  - `POST /api/v1/work/items`: Tạo mới việc, tự gán mã tuần tự, gán rank cuối danh sách.
  - `GET /api/v1/work/items`: Tìm kiếm phân trang lọc động theo `Specification`.
  - `PUT /api/v1/work/items/{itemId}`: Sửa việc, kiểm tra `@Version` bắt `409 Conflict`.
  - `PATCH /api/v1/work/items/{itemId}/rank`: Kéo thả cập nhật LexoRank.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 5.2: Xây dựng WorkItemService và WorkItemController."`

#### 🔹 Bước 5.3: Lắng nghe & Lưu vết kiểm toán tự động (AOP / EntityListener Changelog)
* **Mục tiêu:** Tự động so sánh `old_state` và `new_state` khi WorkItem thay đổi $\rightarrow$ Ghi vào `work_item_changelogs`.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 5.3: Xây dựng cơ chế Changelog Audit Logging tự động."`

#### 🔹 Bước 5.4: Quản lý Bình luận, Tệp đính kèm & Liên kết (Comments, Attachments, Links)
* **Mục tiêu:** APIs thảo luận cây bình luận, upload tệp đính kèm, tạo liên kết kiểu `BLOCKS`, `RELATES_TO`.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 5.4: Xây dựng Service & Controller cho Comments, Attachments và WorkItemLinks."`

---

### GIAI ĐOẠN 6: SPRINT, BACKLOG & BẢNG CÔNG VIỆC (SPRINT & BOARDS)

#### 🔹 Bước 6.1: Sprint Service & Controller (Start, Close Rollover, Snapshot)
* **Mục tiêu:**
  - `POST /sprints/{id}/start`: Bắt đầu sprint (`ACTIVE` — đảm bảo chỉ 1 sprint active).
  - `POST /sprints/{id}/close`: Đóng sprint, chuyển task tồn đọng sang Next Sprint / Backlog, ghi bản ghi `sprint_report_snapshots`.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 6.1: Xây dựng SprintService và SprintController."`

#### 🔹 Bước 6.2: Board Engine Service & Controller (Scrum/Kanban Data, WIP, Swimlanes)
* **Mục tiêu:**
  - `GET /boards/{boardId}/data`: Gom nhóm thẻ theo Cột và Swimlane (Active Sprint cho Scrum hoặc toàn bộ cho Kanban).
  - Kiểm tra và gắn cờ cảnh báo khi vi phạm WIP Limit.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 6.2: Xây dựng BoardService và BoardController."`

#### 🔹 Bước 6.3: Bộ tính toán Báo cáo Agile (Burndown Chart, Velocity, CFD)
* **Mục tiêu:**
  - API trả về dữ liệu biểu đồ Burndown Chart (Đường lý tưởng vs Đường thực tế từ changelog).
  - API biểu đồ Velocity qua các Sprint.
  - API biểu đồ Luồng tích lũy Cumulative Flow Diagram (CFD).
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 6.3: Xây dựng AgileReportService và ReportController."`

---

### GIAI ĐOẠN 7: PHÂN HỆ, PHIÊN BẢN & NHÂN BẢN DỰ ÁN (COMPONENTS & CLONING)

#### 🔹 Bước 7.1: Components (Auto-assign Lead) & Versions (Release Notes Generator)
* **Mục tiêu:** Quản lý Component (gán Lead mặc định) và Version (sinh Release Notes Markdown tự động khi bấm Release).
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 7.1: Xây dựng ComponentService và VersionService."`

#### 🔹 Bước 7.2: Labels Service (On-the-fly Normalization)
* **Mục tiêu:** Tự động tạo nhãn mới nếu chưa có khi user nhập on-the-fly.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 7.2: Xây dựng LabelService."`

#### 🔹 Bước 7.3: Two-Pass Dynamic Timeline Shifting Project Cloning Engine
* **Mục tiêu:** Nhân bản dự án từ Template:
  - Tính $\Delta\text{days} = \text{TargetStartDate} - \text{TemplateStartDate}$.
  - Pass 1: Clone toàn bộ cấu trúc, tịnh tiến ngày, reset status về `TODO`, lưu `Map<OldId, NewId>`.
  - Pass 2: Tái lập quan hệ cha-con 3 tầng (`parent_id`) và liên kết `work_item_links`.
  - Lưu bản ghi lịch sử vào `project_clones`.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 7.3: Xây dựng ProjectCloningService (Two-Pass Dynamic Shifting)."`

---

### GIAI ĐOẠN 8: TRUY VẤN JQL, TỰ ĐỘNG HÓA & REALTIME (JQL, AUTOMATION, REALTIME)

#### 🔹 Bước 8.1: JQL Parser Engine & Saved Filters
* **Mục tiêu:** Chuyển đổi chuỗi JQL (`project = "PRJ" AND status != "Done" AND assignee = currentUser()`) thành JPA Specification.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 8.1: Xây dựng JqlQueryParser và SavedFilterService."`

#### 🔹 Bước 8.2: Lightweight Event-Driven Automation Engine
* **Mục tiêu:** Xử lý tự động hóa: Trigger $\rightarrow$ Condition $\rightarrow$ Action (VD: Subtasks xong hết $\rightarrow$ Đổi status Story cha sang Done).
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 8.2: Xây dựng AutomationEngineService."`

#### 🔹 Bước 8.3: STOMP WebSocket + Redis Pub/Sub Backplane & Live Presence
* **Mục tiêu:**
  - Đồng bộ kéo thả thẻ Kanban real-time đa người dùng qua STOMP `/topic/project/{id}/board`.
  - Hiển thị avatar những ai đang xem task (Live Presence) qua Redis Key TTL 15s.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 8.3: Xây dựng WebSocketSTOMP, RedisPubSub và RedisPresenceService."`

#### 🔹 Bước 8.4: Transactional Outbox Poller & Kafka Event Publisher
* **Mục tiêu:** Ghi `outbox_events` trong transaction $\rightarrow$ Poller quét phát event lên Kafka Topic `work-events-topic`.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 8.4: Xây dựng Transactional Outbox Poller và KafkaEventPublisher."`

---

### GIAI ĐOẠN 9: KIỂM THỬ TÍCH HỢP & HOÀN TẤT TÀI LIỆU (TESTING & DOCS)

#### 🔹 Bước 9.1: Bộ kiểm thử tự động Unit Tests & Integration Tests
* **Mục tiêu:** Viết bộ test kiểm thử luồng trọn vẹn: Tạo Project $\rightarrow$ Tạo Epic/Story/Subtask $\rightarrow$ Kéo thả Sprint $\rightarrow$ Chuyển trạng thái Workflow $\rightarrow$ Đóng Sprint & Kiểm tra Burndown.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 9.1: Viết bộ Unit & Integration Tests cho Work Service."`

#### 🔹 Bước 9.2: Swagger / OpenAPI 3.0 UI Document & README
* **Mục tiêu:** Cấu hình OpenAPI Springdoc, hiển thị trực quan toàn bộ API groups trên Swagger UI `http://localhost:8083/swagger-ui.html`.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 9.2: Cấu hình Springdoc OpenAPI 3.0 và hoàn thiện tài liệu."`

---

## 🚀 HƯỚNG DẪN BẮT ĐẦU THỰC HIỆN

Bạn có thể bắt đầu bất cứ lúc nào bằng cách yêu cầu thực hiện từng bước theo cú pháp:
> **"Hãy thực hiện Bước 1.1: Tạo file migration V1__init_work_jira_schema.sql"**

Tôi sẽ tiến hành code từng bước một cách cẩn thận, chạy kiểm thử, báo cáo kết quả và đợi bạn duyệt trước khi chuyển sang bước tiếp theo!
