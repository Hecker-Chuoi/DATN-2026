# 📋 KẾ HOẠCH TRIỂN KHAI TOÀN DIỆN FULLSTACK WORK SERVICE (V2 JIRA-GRADE)
## DỰ ÁN: DATN-2026 — HỆ THỐNG QUẢN LÝ DỰ ÁN & CÔNG VIỆC DOANH NGHIỆP

> **Tài liệu căn cứ:**  
> - [`1_WORK_OVERVIEW_REFACTOR.md`](file:///d:/DATN-2026/docs/services/work_service_new/1_WORK_OVERVIEW_REFACTOR.md) (Kiến trúc & Chiến lược Refactor)  
> - [`2_WORK_DATABASE_DESIGN.md`](file:///d:/DATN-2026/docs/services/work_service_new/2_WORK_DATABASE_DESIGN.md) (Thiết kế CSDL 25 bảng & Indexing)  
> - [`3_WORK_SEQUENCE_DIAGRAMS.md`](file:///d:/DATN-2026/docs/services/work_service_new/3_WORK_SEQUENCE_DIAGRAMS.md) (Sơ đồ tuần tự các luồng nghiệp vụ)  
> - [`5_WORK_FRONTEND_UI_JIRA_SPEC.md`](file:///d:/DATN-2026/docs/services/work_service_new/5_WORK_FRONTEND_UI_JIRA_SPEC.md) (Quy chuẩn giao diện Frontend chuẩn Jira Cloud & Ảnh mẫu)  
> 
> **Phạm vi triển khai:** **Fullstack (Backend Java 21 / Spring Boot 3.x + Frontend React 19 / Vite / TailwindCSS / Lucide)**.  
> **Phương châm:** **Triển khai cuốn chiếu — Đảm bảo hệ thống luôn build và chạy được — Bảo toàn tính năng cũ**.

---

## 🗺️ TỔNG QUAN LỘ TRÌNH 9 GIAI ĐOẠN (24 BƯỚC THỰC THI)

```
┌────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│ GIAI ĐOẠN 1: CSDL, LÕI THỰC THỂ & API CLIENT BASE (Database, Entities & Frontend Setup)                │
│ ├── Bước 1.1: Migration DDL PostgreSQL (Flyway) tạo 25 bảng CSDL & GIN/B-Tree Indexing                 │
│ ├── Bước 1.2: Hoàn thiện 33 JPA Entities & 13 Enums chuẩn hóa theo Jira Model                          │
│ └── Bước 1.3: Spring Data Repositories (BE) & Cấu hình API Client + Type Definitions (FE)               │
├────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ GIAI ĐOẠN 2: THUẬT TOÁN NỀN TẢNG & CORE ENGINES (Core Algorithms & Optimistic Helpers)                │
│ ├── Bước 2.1: Bộ sinh mã việc nguyên tử AtomicKeySequenceGenerator (BE)                                │
│ ├── Bước 2.2: Thuật toán LexoRank O(1) Reordering (BE: LexoRankService + FE: LexoRank Helper)          │
│ ├── Bước 2.3: Kiểm soát phân cấp nghiêm ngặt 3 tầng (BE: StrictHierarchyValidator + FE: Drag Guards)   │
│ └── Bước 2.4: Bộ phát hiện chu trình phụ thuộc DFS (BE: DependencyCycleDetector)                       │
├────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ GIAI ĐOẠN 3: DỰ ÁN, LOẠI CÔNG VIỆC & PHÂN QUYỀN RBAC (Projects, Types & Security)                     │
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
│ ├── Bước 8.1: JQL Parser Engine (BE: JPA Spec + GIN) + Thanh Tìm kiếm JQL Thông minh & Filter (FE)     │
│ ├── Bước 8.2: Automation Engine (BE) + Giao diện Cấu hình Rule Tự động hóa Trigger -> Action (FE)      │
│ ├── Bước 8.3: STOMP WebSocket & Presence (BE) + Live Card Sync & Avatars Đang Xem Issue (FE)           │
│ └── Bước 8.4: Transactional Outbox & Kafka Event Publisher (BE)                                        │
├────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ GIAI ĐOẠN 9: KIỂM THỬ TÍCH HỢP E2E, TỐI ƯU HÓA & BÀN GIAO (Integration Testing & Docs)                 │
│ ├── Bước 9.1: Kiểm thử Tích hợp E2E (BE MockMvc / Unit Tests + FE Optimistic UI Rollback on 409)       │
│ └── Bước 9.2: Swagger / OpenAPI 3.0 Documentation & Hoàn tất Bàn giao Hệ thống                         │
└────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 🛠️ CHI TIẾT CÁC BƯỚC THỰC THI (ACTIONABLE SPECIFICATIONS)

---

### GIAI ĐOẠN 1: CSDL, LÕI THỰC THỂ & API CLIENT BASE

#### 🔹 Bước 1.1: Migration DDL PostgreSQL (Flyway) tạo 25 bảng CSDL
* **Backend:**
  - Script Flyway: [`V1__init_work_jira_schema.sql`](file:///d:/DATN-2026/backend/work-service/src/main/resources/db/migration/V1__init_work_jira_schema.sql).
  - Tạo 25 bảng CSDL kèm đầy đủ Foreign Keys, Constraints và Indexes (B-Tree + GIN trên `custom_fields JSONB`).
* **Tiêu chí nghiệm thu (DoD):** Flyway migrate thành công trên PostgreSQL 16 của Docker infra mà không gặp bất kỳ lỗi cú pháp nào.

#### 🔹 Bước 1.2: Hoàn thiện 33 JPA Entities & 13 Enums chuẩn hóa
* **Backend:**
  - 13 Enums chuẩn: `HierarchyLevel`, `StatusCategory`, `WorkItemPriority`, `SprintState`, `BoardType`, `SwimlaneStrategy`, `LinkType`...
  - 33 JPA Entities: Ánh xạ đầy đủ quan hệ JPA (`@ManyToOne`, `@OneToMany`, `@Version` cho Optimistic Locking).
* **Tiêu chí nghiệm thu (DoD):** `mvn compile` thành công 100%, Hibernate mapping schema validation khớp hoàn toàn với DDL.

#### 🔹 Bước 1.3: Spring Data Repositories & API Client Frontend
* **Backend:** Tạo 25 interface `JpaRepository` kèm `JpaSpecificationExecutor` cho tìm kiếm động.
* **Frontend:** Cấu hình `workServiceApi.js` với Axios Interceptor, baseURL trỏ qua API Gateway (`http://localhost:8081/work`).
* **Tiêu chí nghiệm thu (DoD):** Frontend gọi được endpoint test và nhận phản hồi chuẩn `ApiResponse<T>`.

---

### GIAI ĐOẠN 2: THUẬT TOÁN NỀN TẢNG & CORE ENGINES

#### 🔹 Bước 2.1: Bộ sinh mã việc nguyên tử (AtomicKeySequenceGenerator)
* **Backend:**
  - Thực thi atomic increment trên trường `projects.current_issue_sequence` bằng native SQL `RETURNING` hoặc pessimistic lock.
  - Đảm bảo khi 100 request tạo issue đồng thời, mã issue luôn tăng tuần tự không bị trùng lặp (ví dụ `DATN-1`, `DATN-2`, `DATN-3`).
* **Tiêu chí nghiệm thu (DoD):** Unit test multi-thread (100 concurrent threads) sinh đúng 100 mã liên tiếp không duplicate.

#### 🔹 Bước 2.2: Thuật toán LexoRank $O(1)$ Reordering
* **Backend:** `LexoRankService.java` — Sinh rank trung gian giữa `prevRank` và `nextRank` theo bảng chữ cái base-36 (`0-9`, `a-z`).
* **Frontend:** `lexoRankHelper.js` — Tính toán rank tạm thời trên client để hiển thị tức thì (Optimistic UI).
* **Tiêu chí nghiệm thu (DoD):** Kéo thả thẻ giữa 2 thẻ bất kỳ chỉ phát sinh duy nhất 1 lệnh update `rank` của thẻ đó.

#### 🔹 Bước 2.3: Kiểm soát Phân cấp Nghiêm ngặt 3 Tầng (StrictHierarchyValidator)
* **Backend:** `StrictHierarchyValidator.java`
  - Level 1 (Epic): `parent_id` bắt buộc phải là `null`.
  - Level 0 (Story/Task/Bug): `parent_id` phải là `null` hoặc là một Epic (Level 1).
  - Level -1 (Sub-task): `parent_id` bắt buộc phải là một Issue Level 0. Không được trỏ tới Sub-task khác.
* **Frontend:** Vô hiệu hóa kéo thả hoặc chọn parent không hợp lệ trên giao diện.
* **Tiêu chí nghiệm thu (DoD):** Gửi request tạo Subtask cho Subtask bị trả về lỗi `400 BAD REQUEST` với thông báo rõ ràng.

#### 🔹 Bước 2.4: Bộ phát hiện Chu trình Phụ thuộc DFS (DependencyCycleDetector)
* **Backend:** `DependencyCycleDetector.java` — Chạy thuật toán Depth-First Search kiểm tra đồ thị có hướng khi tạo liên kết `BLOCKS`.
* **Tiêu chí nghiệm thu (DoD):** Ngăn chặn thành công chu trình $A \rightarrow B \rightarrow C \rightarrow A$.

---

### GIAI ĐOẠN 3: DỰ ÁN, LOẠI CÔNG VIỆC & PHÂN QUYỀN RBAC

#### 🔹 Bước 3.1: DTOs & Validation Requests/Responses cho Project & WorkItemType
* **Backend:** `CreateProjectRequest`, `UpdateProjectRequest`, `ProjectResponse`, `WorkItemTypeResponse` kèm Jakarta Bean Validation.

#### 🔹 Bước 3.2: Project CRUD & Auto-Seeding (BE + FE)
* **Backend:**
  - `ProjectService.java` & `ProjectController.java`.
  - Khi tạo dự án mới, tự động khởi tạo mặc định: 5 WorkItemTypes (Epic, Story, Task, Bug, Subtask), 4 Statuses (To Do, In Progress, In Review, Done), 1 Workflow mặc định, 1 Scrum Board và 1 Kanban Board.
* **Frontend:**
  - Cập nhật trang `ProjectListPage.jsx` và Modal `CreateProjectModal.jsx` hỗ trợ chọn Project Type (`SCRUM`, `KANBAN`) và Key tự sinh.
* **Tiêu chí nghiệm thu (DoD):** Tạo dự án mới trên FE thành công, kiểm tra DB thấy đầy đủ Board, Workflow và Types được seed tự động.

#### 🔹 Bước 3.3: Phân quyền Project RBAC
* **Backend:** `ProjectRoleService.java`, gán vai trò `Project Admin`, `Member`, `Viewer` và kiểm tra quyền qua AOP `@PreAuthorize` hoặc Security Guard.
* **Frontend:** Tab `Members & Roles` trong Project Settings cho phép thêm thành viên và đổi quyền.
* **Tiêu chí nghiệm thu (DoD):** User vai trò `Viewer` không thể bấm nút Tạo Issue hoặc Chỉnh sửa Dự án.

---

### GIAI ĐOẠN 4: QUY TRÌNH & MÁY TRẠNG THÁI (DYNAMIC WORKFLOW ENGINE)

#### 🔹 Bước 4.1: DTOs & Mapping cho Status, Workflow, Transition, Scheme
* **Backend:** `StatusResponse`, `WorkflowResponse`, `TransitionRequest`, `WorkflowTransitionResponse`.

#### 🔹 Bước 4.2: Máy trạng thái FSM (WorkflowEngine)
* **Backend:**
  - `WorkflowEngine.java` điều phối các bước chuyển.
  - Hỗ trợ `ConditionEvaluator` (Kiểm tra quyền người dùng), `ValidatorEvaluator` (Kiểm tra Subtasks closed, required fields), `PostFunctionRunner` (Ghi changelog, update resolution date, publish event).
* **Tiêu chí nghiệm thu (DoD):** Unit test chuyển trạng thái từ `In Progress` sang `Done` thất bại nếu vẫn còn Subtask đang mở (khi có validator).

#### 🔹 Bước 4.3: Workflow APIs & Giao diện Nút Chuyển Trạng thái Động
* **Backend:** `GET /api/v1/work/items/{id}/available-transitions` — Lấy danh sách các bước chuyển hợp lệ mà user hiện tại được phép bấm.
* **Frontend:** Dropdown chuyển trạng thái động trên Modal chi tiết Issue (chỉ hiển thị các transition hợp lệ).
* **Tiêu chí nghiệm thu (DoD):** Nút trạng thái cập nhật động theo cấu hình workflow của từng loại công việc.

---

### GIAI ĐOẠN 5: QUẢN TRỊ CÔNG VIỆC & TRƯỜNG ĐỘNG (WORK ITEMS & COLLABORATION)

#### 🔹 Bước 5.1: DTOs cho Work Item & JSONB Custom Fields
* **Backend:** `CreateWorkItemRequest`, `UpdateWorkItemRequest`, `WorkItemResponse`, `WorkItemDetailResponse`.

#### 🔹 Bước 5.2: Work Item CRUD & Optimistic Lock (BE + FE)
* **Backend:** `WorkItemService.java`, `WorkItemController.java` hỗ trợ tìm kiếm phân trang (`PageResponse`), lọc theo Sprint, Type, Assignee, Status.
* **Frontend:**
  - Modal `WorkItemDetailModal.jsx` chuẩn Jira: Chia 2 cột (Cột trái: Summary, Description Rich Text, Subtasks list, Activity logs; Cột phải: Status dropdown, Assignee, Reporter, Priority, Story Points, Due Date, Custom Fields).
* **Tiêu chí nghiệm thu (DoD):** Tạo, xem, sửa issue mượt mà; nếu 2 tab cùng sửa, tab sửa sau nhận thông báo lỗi xung đột 409 và được gợi ý reload dữ liệu.

#### 🔹 Bước 5.3: Nhật ký Audit Trail (EntityListener + Changelog UI)
* **Backend:** `WorkItemEntityListener.java` bắt diff tự động và ghi vào `work_item_changelogs`.
* **Frontend:** Tab `Activity / History` trên modal chi tiết hiển thị timeline các lần thay đổi dạng: *"Nguyễn Văn A đã đổi Trạng thái từ In Progress sang Done (10 phút trước)"*.
* **Tiêu chí nghiệm thu (DoD):** Mọi thao tác sửa field đều sinh log chính xác trong DB và hiển thị trên giao diện.

#### 🔹 Bước 5.4: Comments Threaded, Attachments Dropzone & Linked Issues
* **Backend:** `WorkItemCommentService`, `WorkItemAttachmentService`, `WorkItemLinkService`.
* **Frontend:** Khu vực bình luận Markdown, kéo thả tải tệp đính kèm và widget liên kết issue (Blocks, Relates to).
* **Tiêu chí nghiệm thu (DoD):** Đăng bình luận, đính kèm file ảnh/PDF và tạo liên kết thành công.

---

### GIAI ĐOẠN 6: SPRINT, BACKLOG & BẢNG CÔNG VIỆC (SCRUM & KANBAN)

#### 🔹 Bước 6.1: Sprint Service & Màn hình Backlog (FE)
* **Backend:** `SprintService.java` — Quản lý vòng đời Sprint (`FUTURE` $\rightarrow$ `ACTIVE` $\rightarrow$ `CLOSED`). API kéo thả issue vào/ra Sprint hoặc Backlog.
* **Frontend:** Trang `ProjectBacklogPage.jsx` với các khối Accordion: *Active Sprint*, *Future Sprints*, và *Backlog*. Modal *Start Sprint* & *Complete Sprint*.
* **Tiêu chí nghiệm thu (DoD):** Kéo thả issue từ Backlog vào Sprint cập nhật `sprint_id` và LexoRank tức thì.

#### 🔹 Bước 6.2: Board Engine & Interactive Board (FE DnD)
* **Backend:** `BoardService.java` — Cấu hình Columns, WIP limits, Swimlanes (`BY_EPIC`, `BY_ASSIGNEE`).
* **Frontend:** Trang `ProjectKanbanBoardPage.jsx` / `ProjectSprintBoardPage.jsx` — Kéo thả card giữa các cột, hiển thị cảnh báo đỏ khi cột vượt quá Max WIP Limit.
* **Tiêu chí nghiệm thu (DoD):** Kéo thả thẻ cập nhật vị trí $O(1)$ qua LexoRank, UI cập nhật mượt mà không bị giật lag.

#### 🔹 Bước 6.3: Báo cáo Agile (Burndown, Velocity, CFD)
* **Backend:** `SprintReportSnapshotEngine.java` — Tính toán snapshot khi đóng Sprint.
* **Frontend:** Trang `ProjectReportsPage.jsx` vẽ biểu đồ Burndown Chart (đường lý thuyết vs thực tế) và Velocity Chart bằng Recharts.
* **Tiêu chí nghiệm thu (DoD):** Biểu đồ hiển thị số liệu chính xác từ snapshot của các Sprint đã hoàn thành.

---

### GIAI ĐOẠN 7: PHÂN HỆ, PHIÊN BẢN & NHÂN BẢN DỰ ÁN

#### 🔹 Bước 7.1: Components & Versions (Release Management)
* **Backend:** CRUD Components (Auto-assignee Lead) & CRUD Versions (Status: `UNRELEASED`, `RELEASED`).
* **Frontend:** Tab `Components` và `Releases` trong Project.
* **Tiêu chí nghiệm thu (DoD):** Gắn component vào issue tự động đề xuất Assignee là Component Lead; Release version khóa các issue đã phát hành.

#### 🔹 Bước 7.2: Labels Service & Tag Input
* **Backend:** `LabelService.java` tạo nhãn on-the-fly.
* **Frontend:** Input gắn label với autocomplete gợi ý nhãn có sẵn và badge đổi màu sinh động.

#### 🔹 Bước 7.3: Two-Pass Timeline Shifting Engine (Template Cloning)
* **Backend:** `DeepCloneTemplateEngine.java` — Sao chép toàn bộ dự án từ mẫu kèm tính toán dịch chuyển ngày bắt đầu/kết thúc ($\Delta days$).
* **Frontend:** Modal chọn Template có sẵn khi tạo dự án mới.
* **Tiêu chí nghiệm thu (DoD):** Nhân bản dự án mẫu 50 tasks sang dự án mới, toàn bộ ngày tháng của Sprint và Tasks được dịch chuyển chính xác theo ngày bắt đầu mới.

---

### GIAI ĐOẠN 8: TRUY VẤN JQL, TỰ ĐỘNG HÓA & REALTIME COLLABORATION

#### 🔹 Bước 8.1: JQL Parser Engine (JPA Specification + GIN Index)
* **Backend:** `WorkItemSpecification.java` hỗ trợ tìm kiếm linh hoạt theo cú pháp: `type = 'BUG' AND priority = 'HIGH' AND sprint = 1`.
* **Frontend:** Thanh tìm kiếm Search Bar có autocomplete từ khóa JQL và bộ lọc nhanh (Quick Filters).

#### 🔹 Bước 8.2: Automation Engine (Trigger $\rightarrow$ Conditions $\rightarrow$ Actions)
* **Backend:** `AutomationRuleService.java` — Lắng nghe sự kiện (ví dụ: Tất cả subtask Done $\rightarrow$ Tự động chuyển Story cha sang Done).

#### 🔹 Bước 8.3: STOMP WebSocket & Live Presence
* **Backend:** Cấu hình Spring WebSocket STOMP (`/ws-work`), tích hợp Redis Pub/Sub Backplane.
* **Frontend:** Hiển thị Avatar người đang xem cùng một Issue và tự động cập nhật thẻ trên Board khi người khác kéo thả mà không cần F5.

#### 🔹 Bước 8.4: Transactional Outbox & Kafka Publisher
* **Backend:** `OutboxScheduledWorker.java` quét bảng `outbox_events` mỗi 500ms để phát hành sự kiện lên topic `work-events` của Kafka đảm bảo độ tin cậy At-Least-Once.

---

### GIAI ĐOẠN 9: KIỂM THỬ TÍCH HỢP, TỐI ƯU HÓA & BÀN GIAO

#### 🔹 Bước 9.1: Kiểm thử Tích hợp E2E & Optimistic UI Rollback
* Chạy bộ kiểm thử tự động MockMvc cho toàn bộ 24 REST APIs.
* Kiểm thử trường hợp mất mạng hoặc lỗi 409 Conflict trên Frontend: Card tự động trượt về vị trí cũ kèm thông báo Toast lỗi nhẹ nhàng.

#### 🔹 Bước 9.2: Swagger / OpenAPI 3.0 Documentation & Bàn giao
* Tích hợp SpringDoc OpenAPI tại `http://localhost:8083/swagger-ui.html`.
* Cập nhật toàn bộ tài liệu vận hành và hướng dẫn API.

---

## 🏁 BẢNG TỔNG KẾT TIẾN ĐỘ & TRẠNG THÁI TRIỂN KHAI

| Giai đoạn | Nội dung trọng tâm | Trạng thái | Ghi chú kỹ thuật |
| :---: | :--- | :---: | :--- |
| **Giai đoạn 1** | CSDL 25 bảng, 33 JPA Entities, Enums & Base Client | 🟢 **Sẵn sàng** | Flyway V1 DDL & JPA Entities đã hoàn thiện |
| **Giai đoạn 2** | Thuật toán Atomic Sequence, LexoRank $O(1)$, Hierarchy | 🟡 **Tiếp theo** | Các service thuật toán cốt lõi |
| **Giai đoạn 3** | Quản trị Dự án, WorkItemTypes, Auto-seed & RBAC | ⚪ Chờ thực thi | API CRUD Project + Frontend Project List |
| **Giai đoạn 4** | Dynamic Workflow Engine FSM (Conditions, Validators, Post-fns) | ⚪ Chờ thực thi | Động cơ chuyển trạng thái linh hoạt |
| **Giai đoạn 5** | Work Items, JSONB Custom Fields, Modal Chi tiết & Audit Trail | ⚪ Chờ thực thi | Modal Issue chuẩn Jira & EntityListener Changelog |
| **Giai đoạn 6** | Sprint Lifecycle, Backlog Accordion, Kanban Board & Reports | ⚪ Chờ thực thi | Kéo thả DnD Board & Burndown Chart |
| **Giai đoạn 7** | Components, Versions, Labels & Template Deep Cloning | ⚪ Chờ thực thi | Two-Pass Shifting Date Engine |
| **Giai đoạn 8** | JQL Search Engine, Automation Rules, WebSocket & Outbox | ⚪ Chờ thực thi | Realtime STOMP & Kafka Outbox |
| **Giai đoạn 9** | Kiểm thử Tích hợp E2E, Swagger Docs & Bàn giao | ⚪ Chờ thực thi | Hoàn tất nghiệm thu hệ thống |
