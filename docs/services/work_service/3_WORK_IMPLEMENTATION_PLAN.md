# 📋 KẾ HOẠCH TRIỂN KHAI TOÀN DIỆN FULLSTACK WORK SERVICE (JIRA-GRADE)
## DỰ ÁN: DATN-2026 — QUẢN LÝ DỰ ÁN & CÔNG VIỆC DOANH NGHIỆP

> **Tài liệu căn cứ:** [`2_WORK_MAIN_DOC.md`](file:///d:/DATN-2026/docs/services/work_service/2_WORK_MAIN_DOC.md), [`4_WORK_DATABASE_DESIGN.md`](file:///d:/DATN-2026/docs/services/work_service/4_WORK_DATABASE_DESIGN.md), [`5_WORK_SEQUENCE_DIAGRAMS.md`](file:///d:/DATN-2026/docs/services/work_service/5_WORK_SEQUENCE_DIAGRAMS.md).  
> **Phạm vi triển khai:** **Fullstack (Backend Java 21 / Spring Boot 3.x + Frontend React 19 / Vite / TailwindCSS / Lucide)**.  
> **Cấu trúc lộ trình:** **9 Giai đoạn với 24 Bước chi tiết (Actionable Steps)** thực thi song hành Backend & Frontend, kiểm thử cuốn chiếu và bàn giao từng phần.

---

## 🗺️ TỔNG QUAN LỘ TRÌNH TRIỂN KHAI FULLSTACK

```
┌────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│ GIAI ĐOẠN 1: CSDL, LÕI THỰC THỂ & CLIENT TYPES (Database, Entities & Frontend API Base)                │
│ ├── Bước 1.1: Migration DDL PostgreSQL (Flyway) tạo 25 bảng CSDL & GIN/B-Tree Indexing                 │
│ ├── Bước 1.2: Bộ JPA Entities & 13 Enums chuẩn hóa theo Jira Model                                     │
│ └── Bước 1.3: Spring Data Repositories (BE) & Cấu hình API Client + Type/Enum Definitions (FE)         │
├────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ GIAI ĐOẠN 2: THUẬT TOÁN NỀN TẢNG & XỬ LÝ CLIENT (Core Algorithms & Optimistic Helpers)                │
│ ├── Bước 2.1: Bộ sinh mã việc nguyên tử AtomicKeySequenceGenerator (BE)                                │
│ ├── Bước 2.2: Thuật toán LexoRank O(1) Reordering (BE: LexoRankService + FE: LexoRank Helper)          │
│ ├── Bước 2.3: Kiểm soát phân cấp nghiêm ngặt 3 tầng (BE: StrictHierarchyValidator + FE: Drag Guards)   │
│ └── Bước 2.4: Bộ phát hiện chu trình phụ thuộc DFS (BE: DependencyCycleDetector)                       │
├────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ GIAI ĐOẠN 3: DỰ ÁN, LOẠI CÔNG VIỆC & PHÂN QUYỀN (Project Management, Types & RBAC)                     │
│ ├── Bước 3.1: DTOs & Validation Requests/Responses cho Project & WorkItemType                          │
│ ├── Bước 3.2: Project CRUD & Auto-Seeding (BE) + Màn hình Projects & Modal Tạo mới (FE)                │
│ └── Bước 3.3: Phân quyền Project RBAC (BE: ProjectRole/PermissionGuard + FE: Project Members/Role Tab)│
├────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ GIAI ĐOẠN 4: QUY TRÌNH & MÁY TRẠNG THÁI (Dynamic Workflow Engine - FSM)                                │
│ ├── Bước 4.1: DTOs & Mapping cho Status, Workflow, Transition, Scheme                                  │
│ ├── Bước 4.2: Máy trạng thái FSM (BE: Condition, Validator, Post-function Engine)                      │
│ └── Bước 4.3: Workflow APIs (BE) + Nút Chuyển Trạng thái Động & Workflow Diagram Viewer (FE)            │
├────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ GIAI ĐOẠN 5: QUẢN TRỊ CÔNG VIỆC & TRƯỜNG ĐỘNG (Work Items, Custom Fields & Collaboration)              │
│ ├── Bước 5.1: DTOs cho Work Item đa năng & JSONB Custom Fields                                         │
│ ├── Bước 5.2: Work Item CRUD & Optimistic Lock (BE) + Modal Chi tiết Issue chuẩn Jira (FE)             │
│ ├── Bước 5.3: Nhật ký Audit Trail (BE: Changelog EntityListener + FE: Tab Lịch sử thay đổi Timeline)   │
│ └── Bước 5.4: Comments Threaded, Attachments Dropzone & Linked Issues Widget (BE + FE)                 │
├────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ GIAI ĐOẠN 6: SPRINT, BACKLOG & BẢNG CÔNG VIỆC (Sprint Lifecycle, Boards & Agile Reports)               │
│ ├── Bước 6.1: Sprint Service (BE) + Màn hình Backlog (FE: Accordion Sprints, Start/Complete Modal)     │
│ ├── Bước 6.2: Board Engine (BE: WIP Limits, Swimlanes) + Interactive Kanban/Scrum Board (FE: DnD)     │
│ └── Bước 6.3: Báo cáo Agile (BE: Snapshot Calculator + FE: Burndown, Velocity & CFD Charts)            │
├────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ GIAI ĐOẠN 7: PHÂN HỆ, PHIÊN BẢN & NHÂN BẢN DỰ ÁN (Components, Versions & Template Cloning)             │
│ ├── Bước 7.1: Components (Auto-lead) & Versions (BE) + Tab Quản lý Phân hệ & Release Notes (FE)        │
│ ├── Bước 7.2: Labels Service (BE) + On-the-fly Tag Input & Badge Color Picker (FE)                     │
│ └── Bước 7.3: Two-Pass Timeline Shifting Engine (BE) + Modal Chọn Template Dự án Thông minh (FE)       │
├────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ GIAI ĐOẠN 8: TRUY VẤN JQL, TỰ ĐỘNG HÓA & REALTIME (JQL Engine, Automation & Realtime Collab)           │
│ ├── Bước 8.1: JQL Parser Engine (BE) + Thanh Tìm kiếm JQL Thông minh & Filter Sidebar (FE)             │
│ ├── Bước 8.2: Automation Engine (BE) + Giao diện Cấu hình Rule Tự động hóa Trigger -> Action (FE)      │
│ ├── Bước 8.3: STOMP WebSocket & Presence (BE) + Live Card Sync & Avatars Đang Xem Issue (FE)           │
│ └── Bước 8.4: Transactional Outbox & Kafka Event Publisher (BE)                                        │
├────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ GIAI ĐOẠN 9: KIỂM THỬ E2E, TỐI ƯU HÓA & TÀI LIỆU (Integration Testing, UX Polish & Docs)               │
│ ├── Bước 9.1: Kiểm thử Tích hợp E2E (BE MockMvc / Postman + FE Optimistic UI Rollback on 409 Conflict) │
│ └── Bước 9.2: Swagger / OpenAPI 3.0 Documentation & Hoàn tất Bàn giao Hệ thống                         │
└────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 🛠️ CHI TIẾT 9 GIAI ĐOẠN & CÁC BƯỚC THỰC THI (ACTIONABLE STEPS)

---

### GIAI ĐOẠN 1: CSDL, LÕI THỰC THỂ & CLIENT TYPES (DATABASE & FRONTEND BASE)

#### 🔹 Bước 1.1: Migration DDL PostgreSQL tạo 25 bảng & Indexing (Đã xong)
* **Backend:**
  - Script Flyway: [`V1__init_work_jira_schema.sql`](file:///d:/DATN-2026/backend/work-service/src/main/resources/db/migration/V1__init_work_jira_schema.sql).
  - Khởi tạo 25 bảng CSDL kèm ràng buộc khóa ngoại, B-Tree và GIN Index trên `custom_fields JSONB`.
* **Tiêu chí nghiệm thu (DoD):** Flyway migrate thành công trên PostgreSQL 16.

#### 🔹 Bước 1.2: Hiện thực bộ JPA Entities & Enums chuẩn hóa (Đã xong)
* **Backend:**
  - 13 Enums trong `com.datn.workservice.enums` (`HierarchyLevel`, `StatusCategory`, `WorkItemPriority`, `SprintState`, `BoardType`...).
  - 33 JPA Entities trong `com.datn.workservice.entity` (hỗ trợ `@Version` Optimistic Locking, JSONB columns).
* **Tiêu chí nghiệm thu (DoD):** Clean compile thành công không lỗi mapping.

#### 🔹 Bước 1.3: Xây dựng tầng Repositories & Cấu hình Frontend API Client
* **Backend:**
  - Tạo Repositories trong `com.datn.workservice.repository`: `ProjectRepository`, `TeamRepository`, `WorkItemRepository`, `SprintRepository`, `BoardRepository`, `WorkflowRepository`, `StatusRepository`, `ComponentRepository`, `VersionRepository`, `LabelRepository`, `WorkItemLinkRepository`, `WorkItemChangelogRepository`, `CustomFieldDefinitionRepository`, `SavedFilterRepository`, `AutomationRuleRepository`, `OutboxEventRepository`.
  - Tích hợp `JpaSpecificationExecutor` cho tìm kiếm động.
* **Frontend:**
  - Tạo file cấu hình API service: `frontend/src/api/workServiceApi.js`.
  - Định nghĩa Type Constants & Enums trên FE: `frontend/src/constants/workConstants.js` (`STATUS_CATEGORIES`, `PRIORITY_CONFIG`, `HIERARCHY_LEVELS`, `SPRINT_STATES`, `LINK_TYPES`).
* **Tiêu chí nghiệm thu (DoD):**
  - Tất cả Repository có unit query test; Frontend API module import và gọi được Axios client chuẩn.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 1.3: Tạo các JPA Repositories và cấu hình Frontend API Client."`

---

### GIAI ĐOẠN 2: THUẬT TOÁN NỀN TẢNG & XỬ LÝ CLIENT (CORE DOMAIN ALGORITHMS)

#### 🔹 Bước 2.1: Bộ sinh mã việc nguyên tử theo Project Key (`AtomicKeySequenceGenerator`)
* **Backend:**
  - Tạo `AtomicKeySequenceGenerator` gọi sequence PostgreSQL `nextval('work_item_seq_' || projectKey)`.
  - Tự động sinh `CREATE SEQUENCE IF NOT EXISTS work_item_seq_KEY` khi tạo Project.
* **Tiêu chí nghiệm thu (DoD):** Test 100 luồng đồng thời sinh mã không bị trùng (`SCRUM-1`, `SCRUM-2`...).
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 2.1: Xây dựng AtomicKeySequenceGenerator."`

#### 🔹 Bước 2.2: Thuật toán xếp thứ tự kéo thả $O(1)$ (`LexoRankService` & FE Helper)
* **Backend:**
  - `LexoRankService` với `calculateBetween(prevRank, nextRank)`, `calculateBefore()`, `calculateAfter()`.
* **Frontend:**
  - Tạo tiện ích `frontend/src/utils/lexorankHelper.js` để tính toán tạm thời rank client-side cho Optimistic UI update ngay khi thả card.
* **Tiêu chí nghiệm thu (DoD):** Chèn hàng trăm lần liên tiếp vẫn sinh chuỗi thứ tự từ điển (`Comparable.compareTo`) tăng dần chính xác.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 2.2: Xây dựng LexoRankService và frontend helper."`

#### 🔹 Bước 2.3: Bộ kiểm soát phân cấp 3 tầng (`StrictHierarchyValidator` & FE Drag Guards)
* **Backend:**
  - Kiểm tra tính hợp lệ: Epic (1) $\rightarrow$ Story/Task/Bug (0) $\rightarrow$ Sub-task (-1). Chặn gắn cha sai cấp hoặc tự làm cha chính mình.
* **Frontend:**
  - Thêm Drag Validation Guards trong UI: Không cho phép kéo Subtask ra ngoài làm Epic, chỉ cho phép kéo Subtask vào các Story/Task.
* **Tiêu chí nghiệm thu (DoD):** Ném lỗi `InvalidHierarchyException` rõ ràng; UI hiển thị icon cấm thả (Drop Disabled) khi kéo sai cấp.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 2.3: Xây dựng StrictHierarchyValidator và UI drag guards."`

#### 🔹 Bước 2.4: Bộ phát hiện chu trình liên kết (`DependencyCycleDetector`)
* **Backend:**
  - Thuật toán DFS duyệt đồ thị `work_item_links` để chặn chu trình khóa $A \rightarrow B \rightarrow C \rightarrow A$.
* **Tiêu chí nghiệm thu (DoD):** Ném `CyclicDependencyException` khi phát hiện chu trình deadlock.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 2.4: Xây dựng DependencyCycleDetector."`

---

### GIAI ĐOẠN 3: DỰ ÁN, LOẠI CÔNG VIỆC & PHÂN QUYỀN (PROJECT & RBAC)

#### 🔹 Bước 3.1: DTOs & Validation Requests/Responses cho Project & WorkItemType
* **Backend:** Request/Response DTOs cho Project, Team, WorkItemType kèm Jakarta Validation.
* **Frontend:** Viết Zod Schema / Form Validation cho Form tạo dự án.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 3.1: Tạo DTOs cho Project và WorkItemType."`

#### 🔹 Bước 3.2: Project Service & Controller (BE) + Màn hình Danh sách & Tạo Dự án (FE)
* **Backend:**
  - `ProjectService` & `ProjectController`: CRUD dự án.
  - Tự động seed hệ sinh thái mặc định khi tạo mới (Sequence, 5 Types, Default Workflow, Board, Roles).
* **Frontend:**
  - Xây dựng trang danh sách dự án: `frontend/src/pages/ProjectsPage.jsx`.
  - Modal tạo dự án mới: `frontend/src/components/project/CreateProjectModal.jsx` (Chọn Icon, Key, Loại Scrum/Kanban, Lead).
* **Tiêu chí nghiệm thu (DoD):** Tạo dự án từ Frontend thành công, điều hướng ngay vào Board làm việc của dự án mới.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 3.2: Xây dựng ProjectService và Màn hình Quản lý Dự án."`

#### 🔹 Bước 3.3: Phân quyền hạt mịn RBAC nội bộ Project (BE + FE)
* **Backend:** `ProjectPermissionGuard` (AOP Aspect) kiểm tra quyền `@RequireProjectPermission(PermissionKey.XXX)`.
* **Frontend:**
  - Component `frontend/src/components/project/ProjectMembersTab.jsx` quản lý thành viên & gán Role.
  - Custom Hook `useProjectPermission(permissionKey)` ẩn/hiện các nút thao tác tương ứng (Create Issue, Start Sprint, Settings...).
* **Tiêu chí nghiệm thu (DoD):** User không có quyền sẽ bị chặn ở cả tầng UI và trả về `403 FORBIDDEN` ở API.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 3.3: Xây dựng Project RBAC và giao diện phân quyền."`

---

### GIAI ĐOẠN 4: QUY TRÌNH & MÁY TRẠNG THÁI (DYNAMIC WORKFLOW ENGINE)

#### 🔹 Bước 4.1: DTOs & Mapping cho Workflow, Status, Transition
* **Backend:** DTOs cho Workflow, Transition, Conditions/Validators config JSONB.
* **Frontend:** Type definitions cho Workflow transitions.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 4.1: Tạo DTOs cho Workflow và Status."`

#### 🔹 Bước 4.2: Máy trạng thái Finite State Machine (`WorkflowEngine`)
* **Backend:** Xử lý luồng chuyển đổi trạng thái: Condition $\rightarrow$ Validator $\rightarrow$ Post-Function.
* **Tiêu chí nghiệm thu (DoD):** Chuyển trạng thái hợp lệ cập nhật đúng `resolved_at` và ngăn chặn chuyển trái phép.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 4.2: Xây dựng Finite State Machine WorkflowEngine."`

#### 🔹 Bước 4.3: Workflow APIs & Giao diện Chuyển Trạng thái (BE + FE)
* **Backend:**
  - `GET /api/v1/work/items/{itemId}/transitions`: Lấy danh sách nút chuyển hợp lệ cho user hiện tại.
  - `POST /api/v1/work/items/{itemId}/transitions/{transitionId}`: Thực hiện chuyển đổi.
* **Frontend:**
  - Component `frontend/src/components/workflow/StatusTransitionDropdown.jsx` (Nút chuyển trạng thái động hiển thị đúng các bước hợp lệ).
  - Modal xem sơ đồ quy trình trực quan: `frontend/src/components/workflow/WorkflowDiagramModal.jsx`.
* **Tiêu chí nghiệm thu (DoD):** Bấm nút trên Dropdown đổi trạng thái thành công, card trên Board tự nhảy sang cột mới.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 4.3: Xây dựng Workflow Controller và Status Transition Dropdown."`

---

### GIAI ĐOẠN 5: QUẢN TRỊ CÔNG VIỆC & TRƯỜNG ĐỘNG (WORK ITEMS & COLLABORATION)

#### 🔹 Bước 5.1: DTOs cho Work Item đa năng & JSONB Custom Fields
* **Backend:** Request/Response DTOs cho WorkItem, CustomFieldDefinition, LinkedIssues.
* **Frontend:** State management & Form helpers cho Custom Fields.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 5.1: Tạo DTOs cho WorkItem và CustomFields."`

#### 🔹 Bước 5.2: Work Item CRUD & Modal Chi tiết Công việc (BE + FE)
* **Backend:**
  - `WorkItemService` & `WorkItemController` (CRUD, Rank Update, Optimistic Locking check `@Version`).
* **Frontend:**
  - Modal chi tiết công việc chuẩn Jira: `frontend/src/components/workitem/WorkItemDetailModal.jsx`.
  - Trình render trường tùy chỉnh động: `frontend/src/components/workitem/DynamicCustomFieldsRenderer.jsx` (hỗ trợ Text, Number, Select, Date, User, Checkbox).
  - Hiển thị Breadcrumbs: `Project / Epic / Parent Story / Item Code`.
* **Tiêu chí nghiệm thu (DoD):** Mở xem, chỉnh sửa inline (Title, Assignee, Story Points, Estimate Time, Custom Fields) mượt mà với debounce autosave.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 5.2: Xây dựng WorkItemService và Modal Chi tiết Công việc."`

#### 🔹 Bước 5.3: Nhật ký Audit Trail & Tab Lịch sử Thay đổi (BE + FE)
* **Backend:** Tự động bắt sự kiện `@EntityListeners(WorkItemChangelogListener.class)` ghi vết mọi trường thay đổi vào `work_item_changelogs`.
* **Frontend:** Component `frontend/src/components/workitem/ChangelogTimelineTab.jsx` hiển thị dòng thời gian ai đã đổi gì lúc nào.
* **Tiêu chí nghiệm thu (DoD):** Thay đổi Assignee/Status lập tức xuất hiện trong timeline lịch sử.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 5.3: Xây dựng Changelog Audit Logging và UI Timeline."`

#### 🔹 Bước 5.4: Comments Cây, Attachments Dropzone & Linked Issues Widget (BE + FE)
* **Backend:** APIs cho thảo luận cây đa cấp, tải tệp đính kèm, tạo liên kết `BLOCKS` / `RELATES_TO`.
* **Frontend:**
  - Cây bình luận: `frontend/src/components/workitem/CommentsThread.jsx` (hỗ trợ rich text / markdown, reply lồng nhau).
  - Kéo thả tệp tin: `frontend/src/components/workitem/AttachmentsDropzone.jsx` (preview hình ảnh, tải về).
  - Widget liên kết: `frontend/src/components/workitem/IssueLinksWidget.jsx` (tìm kiếm issue theo mã để link, hiển thị trạng thái blocker).
* **Tiêu chí nghiệm thu (DoD):** Thêm comment, upload file và liên kết 2 issue thành công trên giao diện.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 5.4: Xây dựng Comments, Attachments và Linked Issues Widget."`

---

### GIAI ĐOẠN 6: SPRINT, BACKLOG & BẢNG CÔNG VIỆC (SPRINT & BOARDS)

#### 🔹 Bước 6.1: Sprint Lifecycle (BE) & Màn hình Backlog (FE)
* **Backend:**
  - `SprintService` & `SprintController` (`createSprint`, `startSprint` [đảm bảo chỉ 1 active], `completeSprint` [chốt sổ snapshot, dồn việc dở dang sang sprint sau]).
* **Frontend:**
  - Màn hình Backlog: `frontend/src/pages/BacklogPage.jsx`.
  - Danh sách Accordion Sprints và Backlog Pool (kéo thả công việc giữa các Sprint).
  - Modal bắt đầu Sprint: `StartSprintModal.jsx` (chọn ngày, nhập Goal).
  - Modal hoàn thành Sprint: `CompleteSprintModal.jsx` (tổng kết số task hoàn thành, chọn Sprint đích dồn việc).
* **Tiêu chí nghiệm thu (DoD):** Quản lý chu kỳ Sprint từ Backlog $\rightarrow$ Active $\rightarrow$ Closed mượt mà.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 6.1: Xây dựng SprintService và Màn hình Backlog."`

#### 🔹 Bước 6.2: Board Engine (BE) & Bảng Kanban/Scrum Board Tương tác cao (FE)
* **Backend:**
  - `BoardService` & `BoardController`: Lấy dữ liệu Board theo Cột và Swimlane, kiểm tra WIP Limit.
* **Frontend:**
  - Màn hình Board làm việc: `frontend/src/pages/ActiveBoardPage.jsx`.
  - Kéo thả mượt mà với `@hello-pangea/dnd` cập nhật LexoRank $O(1)$.
  - Cảnh báo trực quan WIP Limit (đổi màu header cột vàng khi chạm ngưỡng, đỏ khi vượt mức).
  - Chế độ phân làn Swimlanes (Theo Epic, Theo Assignee, Theo Priority).
* **Tiêu chí nghiệm thu (DoD):** Kéo thả card giữa các cột đổi trạng thái tức thì, vị trí giữ nguyên sau khi F5.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 6.2: Xây dựng BoardService và Interactive Board Component."`

#### 🔹 Bước 6.3: Bộ Báo cáo Agile Thông minh (BE + FE)
* **Backend:**
  - `AgileReportService`: Tính toán dữ liệu Burndown Chart (Đường lý tưởng vs Thực tế), Velocity qua các Sprint, Cumulative Flow Diagram (CFD).
* **Frontend:**
  - Màn hình Báo cáo: `frontend/src/pages/AgileReportsPage.jsx`.
  - Vẽ biểu đồ chuyên nghiệp với `Recharts` / `Chart.js`:
    - **Burndown Chart:** Thể hiện tiến độ đốt Story Points theo từng ngày trong Sprint.
    - **Velocity Chart:** Biểu đồ cột so sánh điểm cam kết (Commitment) vs Hoàn thành (Delivered).
    - **CFD Chart:** Biểu đồ diện tích tích lũy phát hiện nút thắt cổ chai (Bottlenecks).
* **Tiêu chí nghiệm thu (DoD):** Biểu đồ hiển thị chính xác số liệu từ các Snapshot đã chốt sổ.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 6.3: Xây dựng AgileReportService và Màn hình Báo cáo Agile."`

---

### GIAI ĐOẠN 7: PHÂN HỆ, PHIÊN BẢN & NHÂN BẢN DỰ ÁN (COMPONENTS & CLONING)

#### 🔹 Bước 7.1: Components (Auto-lead) & Versions Release Notes (BE + FE)
* **Backend:**
  - Quản lý Component (tự gán Lead khi tạo issue).
  - Quản lý Release Version (tự động sinh Release Notes Markdown từ danh sách issues đã Done).
* **Frontend:**
  - Tab quản lý phân hệ: `frontend/src/components/project/ComponentsTab.jsx`.
  - Tab quản lý phiên bản: `frontend/src/components/project/ReleasesTab.jsx` (Nút bấm "Generate & Copy Release Notes").
* **Tiêu chí nghiệm thu (DoD):** Tạo issue gắn Component tự điền Assignee; bấm Release tự sinh bản tin phát hành đầy đủ.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 7.1: Xây dựng Components, Versions và Release Notes Generator."`

#### 🔹 Bước 7.2: Labels Service & Tag Input Component (BE + FE)
* **Backend:** Tự động chuẩn hóa và lưu nhãn mới on-the-fly.
* **Frontend:** Component `frontend/src/components/common/LabelTagInput.jsx` (gợi ý nhãn có sẵn, tạo nhãn mới với mã màu ngẫu nhiên).
* **Tiêu chí nghiệm thu (DoD):** Gõ nhãn mới lưu ngay vào danh mục nhãn của dự án.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 7.2: Xây dựng Labels Service và Tag Input Component."`

#### 🔹 Bước 7.3: Two-Pass Dynamic Timeline Shifting Cloning Engine & Template Modal (BE + FE)
* **Backend:**
  - `ProjectCloningService`: Nhân bản sâu toàn bộ Dự án mẫu (Pass 1: cấu trúc + tịnh tiến $\Delta\text{days}$; Pass 2: tái lập quan hệ cha-con 3 cấp và links).
* **Frontend:**
  - Modal chọn Template: `frontend/src/components/project/TemplateSelectorModal.jsx`.
  - Xem trước cấu trúc Template (Scrum E-Commerce, Microservices Cloud Migration, Marketing Launch), chọn ngày bắt đầu mong muốn $\rightarrow$ Hệ thống tự động dịch chuyển toàn bộ timeline.
* **Tiêu chí nghiệm thu (DoD):** Nhân bản thành công dự án mới với hàng chục Task/Subtask/Sprint có timeline thực tế tính từ ngày người dùng chọn.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 7.3: Xây dựng Two-Pass Project Cloning Engine và Template Selector Modal."`

---

### GIAI ĐOẠN 8: TRUY VẤN JQL, TỰ ĐỘNG HÓA & REALTIME (JQL, AUTOMATION, REALTIME)

#### 🔹 Bước 8.1: JQL Parser Engine & Thanh Tìm kiếm Thông minh (BE + FE)
* **Backend:** `JqlQueryParser` chuyển đổi chuỗi JQL (`project = "SCRUM" AND priority = "HIGH"`) thành `Specification<WorkItem>` truy vấn kết hợp chỉ mục GIN JSONB.
* **Frontend:**
  - Thanh tìm kiếm JQL: `frontend/src/components/search/JqlSearchBar.jsx` (Auto-complete từ khóa `status`, `assignee`, `priority`...).
  - Filter Sidebar: Bộ lọc trực quan bằng checkbox kết hợp chuyển đổi 2 chiều với JQL query.
* **Tiêu chí nghiệm thu (DoD):** Tìm kiếm lọc kết quả siêu tốc $< 50\text{ms}$ với cả trường chuẩn lẫn trường tùy biến JSONB.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 8.1: Xây dựng JQL Parser Engine và Thanh Tìm kiếm JQL."`

#### 🔹 Bước 8.2: Automation Rule Engine & Giao diện Cấu hình Rule (BE + FE)
* **Backend:** Event-driven Automation Engine: Lắng nghe sự kiện `ISSUE_TRANSITIONED` $\rightarrow$ Đánh giá Condition $\rightarrow$ Kích hoạt Action (VD: Đóng mọi Subtasks khi Story cha Done).
* **Frontend:** Modal cấu hình Automation: `frontend/src/components/automation/AutomationRulesModal.jsx` (dạng `WHEN [Trigger] IF [Condition] THEN [Action]`).
* **Tiêu chí nghiệm thu (DoD):** Cấu hình rule trên UI, kích hoạt sự kiện backend tự động thực thi hành động tương ứng.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 8.2: Xây dựng Automation Engine và Giao diện Cấu hình Rule."`

#### 🔹 Bước 8.3: STOMP WebSocket & Live Presence (BE + FE)
* **Backend:** WebSocket STOMP endpoint `/ws/work` + Redis Pub/Sub Backplane đồng bộ multi-instance.
* **Frontend:**
  - Custom Hook `frontend/src/hooks/useWorkRealtime.js` kết nối STOMP.
  - Tự động cập nhật vị trí Card khi người khác kéo thả.
  - Hiển thị Avatar những ai đang mở cùng xem Work Item (Live Presence).
* **Tiêu chí nghiệm thu (DoD):** Mở 2 tab trình duyệt, kéo thả ở Tab 1 $\rightarrow$ Tab 2 tự động cập nhật không cần reload.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 8.3: Xây dựng Realtime WebSocket STOMP và Live Presence."`

#### 🔹 Bước 8.4: Transactional Outbox Poller & Kafka Event Publisher (BE)
* **Backend:** Quét `outbox_events` định kỳ và gửi tin nhắn JSON chuẩn hóa lên Apache Kafka Topic `work-events-topic`.
* **Tiêu chí nghiệm thu (DoD):** Mọi thay đổi quan trọng đều phát sinh sự kiện Kafka chống mất mát tin cậy.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 8.4: Xây dựng Transactional Outbox Poller và Kafka Publisher."`

---

### GIAI ĐOẠN 9: KIỂM THỬ E2E, TỐI ƯU HÓA & TÀI LIỆU (TESTING & DOCS)

#### 🔹 Bước 9.1: Bộ kiểm thử tích hợp E2E & Xử lý 409 Conflict Rollback (BE + FE)
* **Backend:** Bộ Integration Tests kiểm thử trọn vẹn luồng đời dự án (MockMvc / JUnit 5).
* **Frontend:**
  - Kiểm thử cơ chế Optimistic UI Rollback: Khi Client kéo thẻ với version cũ $\rightarrow$ Backend trả về `409 Conflict` $\rightarrow$ UI tự động bật toast cảnh báo và hoàn trả thẻ về vị trí cũ an toàn.
* **Tiêu chí nghiệm thu (DoD):** 100% kịch bản kiểm thử vượt qua mà không phát sinh lỗi đồng thời.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 9.1: Viết bộ E2E Tests và hoàn thiện cơ chế Optimistic Rollback."`

#### 🔹 Bước 9.2: Swagger / OpenAPI 3.0 UI Document & Cập nhật README bàn giao
* **Backend:** Cấu hình Springdoc OpenAPI 3.0 hiển thị toàn bộ API groups trên Swagger UI `http://localhost:8083/swagger-ui.html`.
* **Tài liệu:** Cập nhật README và tài liệu hướng dẫn vận hành hệ thống.
* **Lệnh kích hoạt:** `"Hãy thực hiện Bước 9.2: Hoàn thiện Swagger OpenAPI 3.0 và tài liệu bàn giao."`

---

## 🚀 HƯỚNG DẪN BẮT ĐẦU THỰC HIỆN TIẾP THEO

Bước tiếp theo trong lộ trình triển khai:
> **"Hãy thực hiện Bước 1.3: Tạo các JPA Repositories và cấu hình Frontend API Client."**

Tôi sẽ tiến hành code từng bước một cách cẩn thận, chạy kiểm thử, báo cáo kết quả và đợi bạn duyệt trước khi chuyển sang bước tiếp theo!
