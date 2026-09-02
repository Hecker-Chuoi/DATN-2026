# 📋 Tài liệu Kỹ thuật: Work Service – DATN-2026
## HỆ THỐNG QUẢN LÝ DỰ ÁN & CÔNG VIỆC DOANH NGHIỆP (JIRA-GRADE WORK ENGINE)

> **Module:** `work-service` (Core Project & Work Item Management Service)  
> **Port:** `8083`  
> **Tech Stack:** Java 21, Spring Boot 3.x, Spring Data JPA, PostgreSQL 16 (JSONB & GIN Index), Redis (Pub/Sub & Ephemeral Presence), Apache Kafka, Spring WebSocket / STOMP  
> **Tài liệu căn cứ & tham chiếu:** `2_WORK_MAIN_DOC.md`, `3_WORK_IMPLEMENTATION_PLAN.md`, `4_WORK_DATABASE_DESIGN.md`, `5_WORK_SEQUENCE_DIAGRAMS.md`, `Chi_tiet_thanh_phan_cot_loi_Work_Service.docx`, `Tai_lieu_quan_ly_du_an_Jira.docx`

---

## 1. TỔNG QUAN SERVICE

`work-service` là dịch vụ lõi trung tâm quản lý toàn bộ công việc, dự án, tiến độ và cộng tác nhóm trong từng Workspace theo mô hình Agile (Scrum & Kanban) chuẩn Enterprise (tương tự Jira Cloud và Linear).
  
Dịch vụ được thiết kế theo kiến trúc **Jira-Grade Work Engine**, thoát khỏi mô hình Todo/Kanban tĩnh thông thường để trở thành một động cơ quản trị công việc mạnh mẽ với khả năng:
* Quản lý phân cấp công việc 3 tầng nghiêm ngặt (Epic $\rightarrow$ Story / Task / Bug $\rightarrow$ Sub-task).
* Máy trạng thái hữu hạn (Dynamic Finite State Machine Workflow) cho phép tùy biến trạng thái, bước chuyển (Transition), điều kiện kiểm tra (Conditions, Validators) và hành động tự động (Post-functions).
* Quản trị chu kỳ Agile Scrum (Backlog, Sprint Lifecycle, Burndown, Velocity, CFD) và Kanban Board hỗ trợ giới hạn công việc đang xử lý (WIP Limits) cùng phân nhóm luồng ngang (Swimlanes).
* Tối ưu hóa hiệu năng phản hồi tức thì (Sub-50ms) với thuật toán sắp xếp thứ tự $O(1)$ LexoRank, đồng bộ Realtime WebSocket STOMP + Redis Pub/Sub Backplane, kiểm soát xung đột Optimistic Locking và đảm bảo toàn vẹn phân tán qua Transactional Outbox Pattern.

```
                      ┌────────────────────────┐
                      │   API Gateway (:8081)  │
                      └───────────┬────────────┘
                                  │ Forward /api/v1/work/** & /ws-work/**
                                  ▼
                      ┌────────────────────────┐
                      │      Work Service      │  :8083
                      │   (Jira-Grade Engine)  │
                      └───────────┬────────────┘
                                  │
          ┌───────────────────────┼────────────────────────┐
          ▼                       ▼                        ▼
┌───────────────────┐   ┌───────────────────┐    ┌───────────────────┐
│ PostgreSQL 16 DB  │   │ Redis (In-Memory) │    │ Kafka Producer    │
│ • Relational Data │   │ • Pub/Sub Backpl. │    │ (Transactional    │
│ • JSONB + GIN     │   │ • Live Presence   │    │  Outbox Pattern:  │
│ • Atomic Sequence │   │ • Fast Board Cache│    │  work-events)     │
└───────────────────┘   └───────────────────┘    └───────────────────┘
```

---

## 2. DANH SÁCH THỰC THỂ DỮ LIỆU (DATABASE ENTITIES & SCHEMAS)

Service quản lý trực tiếp 25 thực thể dữ liệu trong CSDL PostgreSQL 16, chia thành 6 phân hệ cốt lõi:

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                               25 THỰC THỂ CSDL WORK SERVICE                                      │
├─────────────────────────┬──────────────────────────┬─────────────────────────────────────────────┤
│ Phân hệ                 │ Các bảng CSDL            │ Ý nghĩa & Vai trò                           │
├─────────────────────────┼──────────────────────────┼─────────────────────────────────────────────┤
│ 1. Tổ chức, Dự án &     │ • teams                  │ Nhóm làm việc đa chức năng                  │
│    Phân quyền RBAC      │ • team_members           │ Thành viên nhóm (LEADER, MEMBER, VIEWER)    │
│                         │ • projects               │ Dự án (Chứa cờ is_template, project_type)   │
│                         │ • project_clones         │ Nhật ký nhân bản dự án từ template          │
│                         │ • project_roles          │ Vai trò nội bộ dự án (Admin, PM, Dev...)    │
│                         │ • role_assignments      │ Gán vai trò cho User trong dự án            │
│                         │ • permission_scheme_entr.│ Khóa quyền (CREATE_ISSUE, SPRINT_MANAGE...) │
├─────────────────────────┼──────────────────────────┼─────────────────────────────────────────────┤
│ 2. Loại công việc &     │ • work_item_types        │ Định nghĩa Epic (1), Story/Task/Bug (0),    │
│    Máy trạng thái (FSM) │                          │ Subtask (-1) kèm cờ is_subtask              │
│                         │ • statuses               │ Trạng thái (Category: TODO, PROGRESS, DONE) │
│                         │ • workflows              │ Quy trình trạng thái                        │
│                         │ • workflow_transitions   │ Bước chuyển kèm Condition/Validator/PostFn  │
│                         │ • workflow_schemes       │ Bộ quy tắc áp dụng workflow theo type       │
│                         │ • workflow_scheme_map.   │ Ánh xạ WorkItemType -> Workflow             │
├─────────────────────────┼──────────────────────────┼─────────────────────────────────────────────┤
│ 3. Agile Scrum, Sprint, │ • sprints                │ Vòng đời Sprint (FUTURE -> ACTIVE -> CLOSED)│
│    Component & Release  │ • sprint_report_snapshots│ Snapshot cố định số liệu báo cáo Agile      │
│                         │ • components             │ Module kiến trúc kỹ thuật & Lead User       │
│                         │ • versions               │ Phiên bản phát hành & Auto Release Notes    │
│                         │ • labels                 │ Nhãn phân loại chuẩn hóa on-the-fly         │
├─────────────────────────┼──────────────────────────┼─────────────────────────────────────────────┤
│ 4. Trọng tâm Công việc  │ • work_items             │ Bảng trung tâm (Single-Table Inheritance,   │
│    & Phụ thuộc          │                          │ Mã tuần tự PRJ-101, LexoRank, JSONB fields) │
│                         │ • work_item_comp_maps    │ Bảng N-N gắn nhiều Components phụ           │
│                         │ • work_item_label_maps   │ Bảng N-N gắn nhiều Labels                   │
│                         │ • work_item_links        │ Liên kết BLOCKS, RELATES_TO, DUPLICATES     │
├─────────────────────────┼──────────────────────────┼─────────────────────────────────────────────┤
│ 5. Bảng trực quan       │ • boards                 │ Bảng Scrum / Kanban                         │
│    (Boards & Columns)   │ • board_columns          │ Cột hiển thị kèm WIP Limits (min/max)       │
│                         │ • board_column_statuses  │ Ánh xạ N-N nhiều status vào 1 cột           │
│                         │ • board_swimlanes        │ Phân nhóm ngang (BY_EPIC, BY_ASSIGNEE, JQL) │
├─────────────────────────┼──────────────────────────┼─────────────────────────────────────────────┤
│ 6. Audit, Tự động hóa & │ • work_item_changelogs   │ Nhật ký kiểm toán diff JSONB tự động        │
│    Mở rộng              │ • work_item_comments     │ Cây bình luận thảo luận đa cấp              │
│                         │ • work_item_attachments  │ Tệp tin đính kèm                            │
│                         │ • custom_field_defs      │ Định nghĩa trường dữ liệu mở rộng           │
│                         │ • saved_filters          │ Bộ lọc truy vấn JQL đã lưu                  │
│                         │ • automation_rules       │ Quy tắc tự động hóa Trigger->Cond->Action   │
│                         │ • outbox_events          │ Lưu sự kiện phát hành Kafka đảm bảo toàn vẹn│
└─────────────────────────┴──────────────────────────┴─────────────────────────────────────────────┘
```

---

## 3. DANH SÁCH REST API CHÍNH (`/api/v1/work`)

Tất cả các API tuân thủ tiêu chuẩn RESTful của DATN-2026, đóng gói qua `ApiResponse<T>`:

### 3.1. Quản lý Đội nhóm & Dự án (`/api/v1/work/teams`, `/api/v1/work/projects`)
| Method | Endpoint | Quyền hạn | Mô tả |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/work/teams` | `team.create` | Tạo nhóm mới trong Workspace |
| `GET` | `/api/v1/work/teams?workspaceId={id}` | `team.view` | Danh sách nhóm theo Workspace |
| `GET` | `/api/v1/work/teams/{teamId}` | `team.view` | Chi tiết nhóm & danh sách thành viên |
| `POST` | `/api/v1/work/teams/{teamId}/members` | `team.manage` | Thêm thành viên vào nhóm (`LEADER`, `MEMBER`, `VIEWER`) |
| `DELETE`| `/api/v1/work/teams/{teamId}/members/{userId}`| `team.manage` | Xóa thành viên khỏi nhóm |
| `POST` | `/api/v1/work/projects` | `project.create` | Tạo dự án mới (Tự sinh Sequence mã `KEY`, Default Workflow, Default Board, Roles) |
| `GET` | `/api/v1/work/projects?workspaceId={id}` | `project.view` | Danh sách dự án trong Workspace (phân trang, lọc) |
| `GET` | `/api/v1/work/projects/templates?workspaceId={id}`| `project.view` | Danh sách các Dự án Mẫu (`is_template = true`) |
| `GET` | `/api/v1/work/projects/{projectId}` | `project.view` | Chi tiết dự án, Project Lead, các schemes |
| `PUT` | `/api/v1/work/projects/{projectId}` | `project.edit` | Cập nhật thông tin dự án |
| `DELETE`| `/api/v1/work/projects/{projectId}` | `project.delete`| Lưu trữ / Soft delete dự án |
| `POST` | `/api/v1/work/projects/{projectId}/clone` | `project.create`| **Nhân bản Dự án / Áp dụng Template**: Sao chép toàn bộ cấu trúc, Sprints, Work Items kèm **Tịnh tiến Timeline động** theo `startDate` |

### 3.2. Quản trị Công việc Tập trung (`/api/v1/work/items`)
| Method | Endpoint | Quyền hạn | Mô tả |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/work/items` | `ISSUE_CREATE` | Tạo mới Work Item (Epic, Story, Task, Bug, Subtask). Tự động sinh `code` tuần tự (`PRJ-101`) và gán LexoRank ở cuối danh sách |
| `GET` | `/api/v1/work/items` | `PROJECT_VIEW` | Tìm kiếm & lọc danh sách Work Items (hỗ trợ phân trang, `sprintId`, `typeId`, `statusId`, `assigneeId`, `componentId`, `fixVersionId`) |
| `GET` | `/api/v1/work/items/{itemId}` | `PROJECT_VIEW` | Xem chi tiết Work Item kèm Sub-tasks, Parent Epic, Links, Attachments, Comments, Custom Fields |
| `PUT` | `/api/v1/work/items/{itemId}` | `ISSUE_EDIT` | Chỉnh sửa thông tin công việc (Kiểm tra Optimistic Lock `version`) |
| `PATCH`| `/api/v1/work/items/{itemId}/rank` | `ISSUE_TRANSITION`| Cập nhật vị trí kéo thả LexoRank $O(1)$ (`prevItemRank`, `nextItemRank`) |
| `DELETE`| `/api/v1/work/items/{itemId}` | `ISSUE_DELETE` | Xóa hoặc chuyển vào thùng rác |

### 3.3. Quy trình & Máy trạng thái (`/api/v1/work/workflows`, `/api/v1/work/items/{id}/transitions`)
| Method | Endpoint | Quyền hạn | Mô tả |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/work/items/{itemId}/transitions` | `PROJECT_VIEW` | Lấy danh sách các nút chuyển trạng thái khả dụng cho user hiện tại (sau khi thẩm định qua Conditions) |
| `POST` | `/api/v1/work/items/{itemId}/transitions` | `ISSUE_TRANSITION`| Thực thi chuyển trạng thái (`transitionId`, `resolution`, `comment`, kiểm tra Validators & chạy Post-functions) |
| `GET` | `/api/v1/work/projects/{projectId}/workflows` | `PROJECT_VIEW` | Xem danh sách Workflows & Workflow Schemes của dự án |
| `POST` | `/api/v1/work/projects/{projectId}/workflows` | `PROJECT_ADMIN`| Tạo / Cấu hình Workflow mới |

### 3.4. Bảng Trực quan & Quản lý Backlog (`/api/v1/work/boards`, `/api/v1/work/backlog`)
| Method | Endpoint | Quyền hạn | Mô tả |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/work/boards/{boardId}/data` | `PROJECT_VIEW` | Lấy toàn bộ dữ liệu thẻ trên Board phân theo cột (`BoardColumn`) và Swimlanes (Áp dụng cho Active Sprint hoặc Kanban) |
| `PUT` | `/api/v1/work/boards/{boardId}/items/{itemId}/move`| `ISSUE_TRANSITION`| Kéo thả Card: Đổi cột trạng thái & cập nhật vị trí LexoRank $O(1)$, kiểm tra WIP Limits |
| `GET` | `/api/v1/work/projects/{projectId}/backlog` | `PROJECT_VIEW` | Lấy danh sách công việc trong Backlog kèm danh sách các Future Sprints (Sắp xếp theo LexoRank) |
| `POST` | `/api/v1/work/boards/{boardId}/columns` | `PROJECT_ADMIN`| Cấu hình cột Board, ánh xạ Status và đặt WIP Limits |

### 3.5. Chu kỳ Sprint & Báo cáo Agile (`/api/v1/work/sprints`, `/api/v1/work/reports`)
| Method | Endpoint | Quyền hạn | Mô tả |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/work/projects/{projectId}/sprints` | `SPRINT_MANAGE` | Tạo Sprint mới ở trạng thái `FUTURE` |
| `POST` | `/api/v1/work/sprints/{sprintId}/start` | `SPRINT_MANAGE` | Kích hoạt Sprint sang `ACTIVE` (Kiểm tra điều kiện chỉ tối đa 1 Sprint Active) |
| `POST` | `/api/v1/work/sprints/{sprintId}/close` | `SPRINT_MANAGE` | Đóng Sprint (`CLOSED`): Tự động dồn task dở dang sang Sprint sau / Backlog, lưu Snapshot báo cáo |
| `POST` | `/api/v1/work/sprints/{sprintId}/items` | `SPRINT_MANAGE` | Thêm / bớt công việc vào Sprint |
| `GET` | `/api/v1/work/sprints/{sprintId}/burndown` | `PROJECT_VIEW` | Dữ liệu biểu đồ Burndown Chart (Lý tưởng vs Thực tế) |
| `GET` | `/api/v1/work/projects/{projectId}/reports/velocity`| `PROJECT_VIEW` | Dữ liệu biểu đồ Velocity (Cam kết vs Hoàn thành qua các Sprint) |
| `GET` | `/api/v1/work/projects/{projectId}/reports/cfd`| `PROJECT_VIEW` | Biểu đồ luồng tích lũy Cumulative Flow Diagram |

### 3.6. Components, Versions, Labels & Dependencies
| Method | Endpoint | Quyền hạn | Mô tả |
| :--- | :--- | :--- | :--- |
| `GET|POST`| `/api/v1/work/projects/{projectId}/components` | `PROJECT_ADMIN` | Quản lý Phân hệ Kiến trúc & Lead User (Hỗ trợ Vertical Ownership) |
| `GET|POST`| `/api/v1/work/projects/{projectId}/versions` | `RELEASE_MANAGE` | Quản lý các Version phát hành |
| `POST` | `/api/v1/work/versions/{versionId}/release` | `RELEASE_MANAGE` | Kích hoạt phát hành Version & Tự động sinh Release Notes từ các issue đã Done |
| `GET|POST`| `/api/v1/work/projects/{projectId}/labels` | `PROJECT_VIEW` | Quản lý danh sách Nhãn (Tự động chuẩn hóa on-the-fly khi gõ nhãn mới) |
| `POST` | `/api/v1/work/items/{itemId}/links` | `ISSUE_EDIT` | Tạo liên kết phụ thuộc (`BLOCKS`, `RELATES_TO`, `DUPLICATES`) kèm DFS Cycle Detection |
| `POST` | `/api/v1/work/items/{itemId}/comments` | `COMMENT_CREATE`| Thêm bình luận thảo luận (Hỗ trợ @mention) |
| `POST` | `/api/v1/work/items/{itemId}/attachments` | `ISSUE_EDIT` | Đính kèm tệp tin tài liệu |

### 3.7. Truy vấn JQL & Bộ lọc Đã lưu (`/api/v1/work/jql`, `/api/v1/work/saved-filters`)
| Method | Endpoint | Quyền hạn | Mô tả |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/work/jql/search` | `PROJECT_VIEW` | Thực thi truy vấn JQL tùy biến (AST $\rightarrow$ JPA Specification & GIN JSONB query) |
| `GET|POST`| `/api/v1/work/saved-filters` | `PROJECT_VIEW` | Quản lý các bộ lọc JQL đã lưu để chia sẻ hoặc hiển thị lên Dashboard Gadgets |

---

## 4. CÁC CƠ CHẾ KỸ THUẬT NỔI BẬT & ĐỘNG CƠ XỬ LÝ (CORE ENGINES)

Để mang lại trải nghiệm mượt mà, phản hồi tức thì (Sub-50ms) và năng lực quản trị quy mô doanh nghiệp tương tự **Jira** và **Linear**, `work-service` áp dụng 12 cơ chế kỹ thuật cốt lõi:

---

### 4.1. Bộ kiểm soát Phân cấp Nghiêm ngặt 3 Tầng (Strict Hierarchy Engine)
* **Quy tắc cấp bậc**:
  - **Level 1 (Epic)**: Gom cụm sáng kiến/tính năng lớn, không có cha (`parent_id = null`), chứa Story/Task/Bug.
  - **Level 0 (Story, Task, Bug)**: Yêu cầu nghiệp vụ, nhiệm vụ kỹ thuật hoặc lỗi, có thể gắn vào Epic cha, chứa Sub-tasks.
  - **Level -1 (Sub-task)**: Nhiệm vụ con phân rã, **bắt buộc** có cha Level 0 và **tuyệt đối không được có con**.
* **Đảm bảo toàn vẹn**: Chặn toàn bộ chu trình phân cấp vòng lặp ($A \rightarrow B \rightarrow A$), đảm bảo tính toán Roadmap, Epic Burndown và Rollup Story Points chính xác 100%.

---

### 4.2. Thuật toán Xếp thứ tự Kéo thả $O(1)$ (LexoRank / Fractional Indexing)
* **Vấn đề**: Khi kéo chèn một Card vào giữa danh sách, nếu dùng số nguyên (`position = 1, 2, 3...`), hệ thống phải cập nhật lại vị trí của hàng trăm Card phía sau, gây nghẽn DB và nguy cơ Deadlock.
* **Cách hoạt động**:
  1. Sử dụng chuỗi ký tự cơ số 36 (Base-36) có thể tính toán điểm giữa (Fractional Indexing).
  2. Khi chèn Card vào giữa $A$ (`rank = "0|hzzzzz:"`) và $B$ (`rank = "0|i00000:"`), thuật toán sinh rank trung vị mới $C$ (`rank = "0|hzzzzz:8"`).
  3. **Chỉ cần UPDATE đúng 1 bản ghi duy nhất** với độ phức tạp $O(1)$, không ảnh hưởng bất kỳ bản ghi xung quanh nào.

---

### 4.3. Động cơ Máy trạng thái Hữu hạn (Dynamic Finite State Machine Workflow Engine)
* **Không fix cứng trạng thái bằng Enum**: Cho phép mỗi loại công việc (Bug vs Story) có Workflow riêng biệt thông qua `WorkflowScheme`.
* **Cấu trúc Transition**:
  $$\text{Transition} = \langle \text{from\_status}, \text{to\_status}, \text{action\_name}, [\text{Conditions}], [\text{Validators}], [\text{PostFunctions}] \rangle$$
* **Hooks xử lý**:
  - **Conditions**: Chỉ Assignee hoặc người có quyền `TRANSITION_ISSUE` mới thấy nút chuyển.
  - **Validators**: Bắt buộc nhập `resolution` khi Done; bắt buộc **toàn bộ Sub-tasks phải đạt category `DONE`** trước khi Story cha được chuyển sang `DONE`.
  - **Post-Functions**: Tự động set `resolved_at = NOW()`, tự động chuyển Story cha khi các con hoàn thành.

---

### 4.4. Kiến trúc Đồng bộ Realtime Đa người dùng (Real-time Multiplayer Sync & Ephemeral Presence)
* **WebSocket STOMP Channels**:
  - `/topic/projects/{projectId}/board`: Broadcast thay đổi di chuyển thẻ, trạng thái Kanban/Scrum cho toàn bộ thành viên.
  - `/topic/work-items/{itemId}`: Cập nhật chi tiết công việc, bình luận trực tiếp và Live Typing.
* **Redis Pub/Sub Backplane**: Làm cầu nối giữa các instance `work-service` trong cụm cluster, đảm bảo broadcast tin nhắn tới 100% Client kết nối.
* **Ephemeral Live Presence (Ai đang xem task)**: Lưu danh sách `userId` đang xem vào Redis Key kèm TTL (15s). Client gửi heartbeat định kỳ; khi đóng tab, Redis tự giải phóng và broadcast `PRESENCE_LEAVE`.

---

### 4.5. Kiểm soát Xung đột Đồng thời & Tự hòa giải (Optimistic Locking & State Reconciliation)
* Cột `@Version private Integer version` trong bảng `work_items`.
* Frontend áp dụng **Optimistic UI Update** (cập nhật giao diện ngay lập tức khi kéo thả).
* Khi 2 người cùng sửa một việc, server phát hiện xung đột ném `OptimisticLockException` $\rightarrow$ Trả về mã lỗi `409 CONFLICT` kèm authoritative data mới nhất để giao diện tự động Reconcile mà không làm mất dữ liệu người dùng.

---

### 4.6. Bộ sinh Mã Công việc Tự động Nguyên tử (PostgreSQL Atomic Sequence Key Generator)
* Mã hiển thị chuẩn Jira: `{PROJECT_KEY}-{SEQUENCE}` (Ví dụ: `DATN-1`, `DATN-101`).
* Khi tạo Project mới, hệ thống tự động tạo PostgreSQL Sequence riêng: `CREATE SEQUENCE IF NOT EXISTS work_item_seq_KEY`.
* Khi tạo việc, gọi hàm nguyên tử `SELECT nextval('work_item_seq_KEY')`. Đảm bảo sinh mã tuần tự tuyệt đối, Lock-free, chịu tải hàng trăm request tạo việc đồng thời không trùng mã.

---

### 4.7. Phát hành Sự kiện Đảm bảo Toàn vẹn (Transactional Outbox Pattern & Kafka)
* Tránh hoàn toàn tình trạng "Dual Write" (Ghi CSDL thành công nhưng bắn Kafka thất bại hoặc ngược lại).
* Trong cùng một DB Transaction: Lưu `WorkItem` và ghi 1 bản ghi vào bảng `outbox_events (id, aggregate_type, payload, status = 'PENDING')`.
* Background Poller quét bảng `outbox_events` $\rightarrow$ Bắn vào Kafka Topic `work-events-topic` $\rightarrow$ Đánh dấu `PROCESSED`.
* `notification-service` và `ai-service` consume topic này để gửi thông báo/email và tổng hợp báo cáo.

---

### 4.8. Lưu vết Kiểm toán Phi xâm lấn Tự động (AOP / EntityListener Changelog Logging)
* Bắt sự kiện `@PreUpdate` / `@PostUpdate` trên thực thể `WorkItem`.
* So sánh trạng thái trước (`old_value`) và sau (`new_value`) của các trường (`status`, `assignee`, `story_points`, `sprint`...) $\rightarrow$ Tự động ghi vào bảng `work_item_changelogs` dưới dạng diff.
* Dữ liệu changelog là nguồn chính xác để dựng biểu đồ **Burndown Chart** và **Cumulative Flow Diagram (CFD)**.

---

### 4.9. Cơ chế Nhân bản Dự án & Tịnh tiến Mốc Thời gian Động (Dynamic Timeline Shifting Engine)
* Khi tạo dự án mới từ Template:
  1. **Tính độ lệch thời gian**: $\Delta \text{days} = \text{TargetStartDate} - \text{TemplateStartDate}$.
  2. **Pass 1 (Nhân bản & Tịnh tiến)**: Sao chép Project, Sprints, Components, Versions, Work Items, cộng thêm $\Delta \text{days}$ cho `start_date`, `due_date`, `release_date`. Lưu bảng ánh xạ `Map<UUID, UUID> idMapping`.
  3. **Pass 2 (Tái lập Quan hệ Phân cấp)**: Cập nhật lại toàn bộ `parent_id = idMapping.get(old_parent_id)` và liên kết `work_item_links`. Toàn bộ trạng thái được reset về `TODO`.

---

### 4.10. Trường Tùy chỉnh Động & Chỉ mục Siêu tốc (PostgreSQL JSONB + GIN Indexing)
* Bảng `custom_field_definitions`: Lưu định nghĩa kiểu trường (`TEXT`, `NUMBER`, `SELECT`, `DATE`, `USER`, `CHECKBOX`).
* Bảng `work_items`: Lưu giá trị trong cột `custom_fields JSONB` (Ví dụ: `{"environment": "staging", "risk_level": "HIGH"}`).
* Đánh chỉ mục **GIN Index** (`CREATE INDEX idx_work_items_custom_fields_gin ON work_items USING gin (custom_fields);`), cho phép tìm kiếm và lọc trường tùy chỉnh với tốc độ cực cao mà không cần sửa cấu trúc bảng CSDL (No `ALTER TABLE`).

---

### 4.11. Ngôn ngữ Truy vấn JQL & Bộ phân tích Cú pháp (JQL Parser Engine)
* Hỗ trợ cú pháp JQL mạnh mẽ:
  ```sql
  project = "AUTH" AND status != "Done" AND assignee = currentUser() AND sprint in openSprints() ORDER BY priority DESC
  ```
* Bộ `JqlQueryParser` phân tích chuỗi thành cây cú pháp (AST), kiểm tra hợp lệ ngữ nghĩa và chuyển dịch thành **Spring Data JPA `Specification<WorkItem>`** kết hợp native JSONB queries.

---

### 4.12. Giải pháp Xung đột Thời gian & Cảnh báo Mềm (Non-blocking Soft Warning UX)
* Khi `workItem.dueDate > sprint.endDate`, hệ thống **không chặn cứng (Non-blocking)** để phản ánh đúng thực tế trễ hạn.
* `WorkItemResponse` đính kèm thông tin hạn chót của Sprint/Milestone. Giao diện tự động gắn cờ cảnh báo màu vàng cam `⚠️ Hạn chót trễ hơn Sprint` và cung cấp nút 1-click **[Đồng bộ về hạn Sprint]**.

---

## 5. CHI TIẾT TƯƠNG TÁC VỚI REDIS & KAFKA (INTEGRATION ARCHITECTURE)

Trong kiến trúc của `work-service`, **Redis** và **Kafka** được phân định ranh giới rõ ràng:
* **Redis**: Phục vụ **Sub-millisecond In-Memory Real-time State & Synchronization** (Dữ liệu tạm thời, độ trễ cực thấp < 2ms).
* **Kafka**: Phục vụ **Persistent Event Streaming & Inter-Service Communication** (Giao tiếp liên microservice bền vững, đảm bảo không mất tin).

```
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                                       WORK-SERVICE                                          │
└───────────────────────────┬─────────────────────────────────────────────┬───────────────────┘
                            │                                             │
      [REAL-TIME / IN-MEMORY / EPHEMERAL]                        [ASYNC / DURABLE / PERSISTENT]
                            │                                             │
                            ▼                                             ▼
               ┌────────────────────────┐                    ┌────────────────────────┐
               │         REDIS          │                    │         KAFKA          │
               ├────────────────────────┤                    ├────────────────────────┤
               │ • WebSocket Cluster    │                    │ • Giao tiếp đa Service │
               │ • Live Presence        │                    │ • Gửi Email / Webhook  │
               │ • Caching Kanban Board │                    │ • Event Sourcing Audit │
               └────────────────────────┘                    └────────────────────────┘
```

### 5.1. Các kịch bản sử dụng REDIS trong `work-service`
| Kịch bản | Cấu trúc & Key Format | TTL | Mô tả chi tiết |
| :--- | :--- | :---: | :--- |
| **1. WebSocket Pub/Sub Backplane** | `Channel: work:events` | Không (Stream) | Cầu nối phát tán sự kiện kéo thả Card sang tất cả các instance server để broadcast xuống Client. |
| **2. Live Presence (Ai đang xem việc)** | `Set: presence:work-item:{itemId}` | `15s` (Gia hạn heartbeat) | Client gửi heartbeat định kỳ mỗi 5s. Khi đóng tab, key tự hết hạn sau 15s $\rightarrow$ Không tốn tài nguyên DB. |
| **3. Live Typing (Đang nhập bình luận)**| `String: typing:work-item:{itemId}:{userId}` | `3s` | Bật/tắt chỉ báo *"Nguyễn Văn A đang gõ bình luận..."* tức thì. |
| **4. Cache Bảng Board (Fast Acceleration)**| `String (JSON): cache:board:{boardId}` | `5 phút` (Cache-Aside) | Giảm 90% truy vấn `JOIN` phức tạp khi tải Board. Tự động Invalidate khi có thao tác di chuyển/sửa thẻ. |

### 5.2. Các kịch bản sử dụng KAFKA trong `work-service`
`work-service` đóng vai trò là **Kafka Producer** chính, phát các Domain Events có cấu trúc lên Broker:

| Kafka Topic | Event Type | Payload Data (JSON) | Service tiếp nhận (Consumer) & Hành động |
| :--- | :--- | :--- | :--- |
| **`work-events-topic`** | `WORK_ITEM_ASSIGNED` | `{ itemId, projectId, assigneeId, assignerId, summary, dueDate }` | `notification-service` $\rightarrow$ Gửi email thông báo & In-app alert cho người được giao việc. |
| **`work-events-topic`** | `STATUS_TRANSITIONED` | `{ itemId, projectId, oldStatus, newStatus, actorId, code }` | • `notification-service` $\rightarrow$ Báo cho Watchers.<br>• `ai-service` $\rightarrow$ Cập nhật tiến độ Sprint tổng quan. |
| **`work-events-topic`** | `DUE_DATE_APPROACHING`| `{ itemId, summary, assigneeId, dueDate, hoursLeft }` | `notification-service` $\rightarrow$ Gửi email nhắc nhở deadline sắp hết hạn (trước 24h / 2h). |
| **`comment-events-topic`**| `COMMENT_CREATED` | `{ commentId, itemId, authorId, mentionedUserIds: [...], snippet }` | `notification-service` $\rightarrow$ Gửi thông báo cho người được `@mention` trong bình luận. |
| **`project-events-topic`**| `SPRINT_CLOSED` | `{ sprintId, projectId, completedPoints, incompletePoints, timestamp }` | `ai-service` $\rightarrow$ Tự động sinh báo cáo tóm tắt hiệu suất Sprint (Sprint Insights). |
| **`project-events-topic`**| `VERSION_RELEASED` | `{ versionId, projectId, versionName, releaseNotesMarkdown }` | `notification-service` $\rightarrow$ Gửi thông báo đợt phát hành Release tới các bên liên quan. |

---

## 6. CẤU TRÚC THƯ MỤC SOURCE CODE (`work-service`)

```
backend/work-service/
├── pom.xml
└── src/main/java/com/datn/workservice/
    ├── WorkServiceApplication.java
    ├── config/                 # WebSocketConfig, SecurityConfig, RedisConfig, KafkaProducerConfig, JpaConfig
    ├── controller/             # TeamController, ProjectController, WorkItemController, WorkflowController,
    │                           # BoardController, SprintController, ComponentController, VersionController,
    │                           # LabelController, JqlQueryController, SavedFilterController
    ├── dto/
    │   ├── request/            # CreateProjectRequest, CreateWorkItemRequest, MoveCardRequest,
    │   │                       # ExecuteTransitionRequest, StartSprintRequest, CloseSprintRequest,...
    │   └── response/           # ProjectDetailResponse, WorkItemDetailResponse, BoardDataResponse,
    │                           # SprintReportResponse, BurndownChartResponse, VelocityResponse,...
    ├── engine/                 # StrictHierarchyEngine, FiniteStateMachineEngine, LexoRankService,
    │                           # JqlQueryParser, ProjectCloningEngine, DependencyCycleDetector,
    │                           # LightweightAutomationEngine, AtomicKeySequenceGenerator
    ├── entity/                 # Project, ProjectRole, RoleAssignment, PermissionSchemeEntry,
    │                           # WorkItem, WorkItemType, Status, Workflow, WorkflowTransition, WorkflowScheme,
    │                           # Sprint, SprintReportSnapshot, Component, Version, Label, WorkItemLink,
    │                           # Board, BoardColumn, BoardSwimlane, WorkItemChangelog, WorkItemComment,
    │                           # WorkItemAttachment, CustomFieldDefinition, SavedFilter, AutomationRule,
    │                           # OutboxEvent, ProjectClone, Team, TeamMember
    ├── enums/                  # HierarchyLevel, StatusCategory, WorkItemPriority, SprintState,
    │                           # BoardType, SwimlaneStrategy, LinkType, CustomFieldType, OutboxStatus
    ├── event/                  # WorkItemAssignedEvent, StatusTransitionedEvent, OutboxEventPublisher
    ├── exception/              # ResourceNotFoundException, OptimisticLockConflictException,
    │                           # InvalidHierarchyException, DependencyCycleException, WorkflowValidationException
    ├── repository/             # ProjectRepository, WorkItemRepository, WorkflowRepository, BoardRepository,
    │                           # SprintRepository, ComponentRepository, VersionRepository, OutboxEventRepository,...
    ├── security/               # ProjectPermissionGuard (AOP Aspect), SecurityContextHelper
    ├── service/                # ProjectService, WorkItemService, WorkflowService, BoardService,
    │                           # SprintService, ComponentService, VersionService, JqlSearchService, OutboxPoller
    └── service/impl/           # Implementation Classes & WebSocket/Redis/Kafka Integration Handlers
```

---

## 7. RANH GIỚI HỆ THỐNG VÀ GIAO TIẾP NGOẠI VI (EXTERNAL TOUCHPOINTS)

`work-service` tuân thủ nghiêm ngặt tính độc lập của kiến trúc Microservices (Loosely Coupled):

1. **Với `identity-service` & Keycloak (Xác thực & Danh tính)**:
   - `work-service` chỉ lưu trữ `user_id` (UUID) dạng tham chiếu ngoại vi, không lưu trữ thông tin nhạy cảm.
   - Tiếp nhận JWT Token từ API Gateway, giải mã Claims qua Spring Security Resource Server để lấy `userId` và roles.
2. **Với `notification-service` (Thông báo & Email)**:
   - `work-service` không gửi email hay push notification trực tiếp.
   - Phát hành các Domain Events qua Transactional Outbox $\rightarrow$ Kafka Topic `work-events-topic`.
3. **Với `api-gateway` (Định tuyến & Bảo mật Vành đai)**:
   - Định tuyến `/api/v1/work/**` và WebSocket `/ws-work/**` về cổng `8083`.
4. **Với `frontend` (React 19 / Vite Application)**:
   - Cung cấp API kéo thả tối ưu với độ trễ cực thấp qua chuỗi `rank` LexoRank.
   - Đồng bộ real-time Board qua WebSocket STOMP, hiển thị Live Presence và cập nhật tức thì.
