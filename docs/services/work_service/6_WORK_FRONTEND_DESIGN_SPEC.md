# 🎨 TÀI LIỆU ĐẶC TẢ THIẾT KẾ GIAO DIỆN & FRONTEND (FRONTEND DESIGN SPEC)
## DỰ ÁN: DATN-2026 — HỆ THỐNG QUẢN LÝ DỰ ÁN & CÔNG VIỆC DOANH NGHIỆP (JIRA / LINEAR STYLE)

> **Module:** `frontend` (React 19 + Vite + Vanilla CSS / Inline Design System + Lucide Icons)  
> **Tài liệu tham chiếu:** `1_WORK_SERVICE.md`, `2_WORK_MAIN_DOC.md`, `3_WORK_IMPLEMENTATION_PLAN.md`

---

## 1. HỆ THỐNG DESIGN SYSTEM, MÀU SẮC & TYPOGRAPHY

### 1.1. Bảng màu Chuẩn (Core Color Palette)
* **Primary / Brand Accent**: `#2563EB` (Blue 600) - `#1D4ED8` (Blue 700)
* **Backgrounds**:
  - Main App Background: `#F8FAFC` (Slate 50)
  - Card & Content Background: `#FFFFFF` (Pure White)
  - Sidebar Background: `#FFFFFF`
  - Subtle Hover Background: `#F1F5F9` (Slate 100)
  - Dark Badges & Tooltips: `#0F172A` (Slate 900)
* **Borders & Dividers**:
  - Primary Border: `#E2E8F0` (Slate 200)
  - Subtle Border: `#F1F5F9` (Slate 100)
* **Typography Colors**:
  - Heading & Primary Text: `#0F172A` (Slate 900)
  - Body Text: `#334155` (Slate 700)
  - Secondary & Caption Text: `#64748B` (Slate 500)
  - Muted / Placeholder: `#94A3B8` (Slate 400)

### 1.2. Màu sắc Trạng thái & Độ ưu tiên (Status & Priority Badges)
* **Status Categories**:
  - `TODO`: Text `#475569`, Background `#F1F5F9`, Border `#CBD5E1` (Xám)
  - `IN_PROGRESS`: Text `#1D4ED8`, Background `#EFF6FF`, Border `#BFDBFE` (Xanh dương)
  - `IN_REVIEW`: Text `#D97706`, Background `#FEF3C7`, Border `#FDE68A` (Vàng cam)
  - `DONE`: Text `#15803D`, Background `#F0FDF4`, Border `#BBF7D0` (Xanh lá)
* **Work Item Priority**:
  - `URGENT / HIGHEST`: Đỏ tươi (`#EF4444`)
  - `HIGH`: Cam (`#F97316`)
  - `MEDIUM`: Vàng hổ phách (`#EAB308`)
  - `LOW / LOWEST`: Xám lam (`#94A3B8`)
* **Work Item Types Badges**:
  - `Epic`: Màu tím (`#8B5CF6`, `#7C3AED`)
  - `Story`: Màu xanh lá (`#10B981`)
  - `Task`: Màu xanh lam (`#3B82F6`)
  - `Bug`: Màu đỏ (`#EF4444`)
  - `Sub-task`: Màu xám đậm (`#64748B`)

---

## 2. CẤU TRÚC LAYOUT & PHÂN BỔ MÀN HÌNH

```
┌────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                             APP CONTAINER                                              │
├──────────────────────┬─────────────────────────────────────────────────────────────────────────────────┤
│ SIDEBAR (256px)      │ MAIN CONTENT AREA (flex: 1)                                                     │
│                      ├─────────────────────────────────────────────────────────────────────────────────┤
│ • Workspace Header   │ HEADER (48px): Breadcrumbs, Search JQL, Views Switcher, Filter Buttons, Actions │
│ • Quick Actions      ├─────────────────────────────────────────────────────────────────────────────────┤
│ • Teams Accordion    │ WORKSPACE OUTLET (Cuộn độc lập):                                                │
│ • Projects List      │ ├── Scrum / Kanban Board View (Cột ngang cuộn mượt mà)                          │
│ • Backlog Link       │ ├── Backlog & Sprint Planning View (Danh sách thẻ xếp theo LexoRank)           │
│ • Components/Version │ ├── Timeline / Roadmap View                                                     │
│ • User Profile & Out │ └── Project Settings & Permissions                                              │
└──────────────────────┴─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. CÁC MÀN HÌNH & THÀNH PHẦN GIAO DIỆN (UI COMPONENTS)

### 3.1. Project List Page (`ProjectListPage.jsx`)
* **Header**: Tiêu đề "Dự án", ô tìm kiếm nhanh, nút `+ Tạo dự án mới`, bộ lọc phân loại (Tất cả, Đang hoạt động, Đã đóng).
* **Danh sách Dự án**: Dạng lưới Grid Card hoặc bảng Table:
  - Icon dự án, Tên dự án, Mã Key (`DATN`, `AUTH`).
  - Phân loại (`SOFTWARE_SCRUM`, `SOFTWARE_KANBAN`).
  - Trưởng dự án (Avatar + Tên), Nhóm trực thuộc, Số lượng thành viên.
  - Tiến độ Sprint hiện tại / Số lượng Work Items dở dang.

### 3.2. Project Detail Page & Agile Boards (`ProjectDetailPage.jsx`)
* **Header điều hướng Sub-views**:
  - Tab 1: **Bảng công việc (Board)** (Scrum Board cho Active Sprint hoặc Kanban Board).
  - Tab 2: **Backlog & Sprints** (Quản lý các Future Sprints, kéo thả Story Points từ Backlog vào Sprint).
  - Tab 3: **Roadmap / Timeline** (Hiển thị các Epic theo thanh thời gian).
  - Tab 4: **Components & Vertical Ownership** (Quản lý các module dịch vụ).
  - Tab 5: **Releases & Versions** (Quản lý các đợt phát hành).
* **Bảng Board (`BoardView.jsx`)**:
  - Hiển thị theo các Cột trạng thái (`BoardColumn`): `To Do`, `In Progress`, `In Review`, `Done`.
  - Hỗ trợ **WIP Limit Indicator**: Hiển thị `(3/5 items)`, chuyển cảnh báo đỏ nếu vượt quá giới hạn.
  - Card Item: Mã việc (`PRJ-101`), Icon Type (Story/Task/Bug), Tiêu đề tóm tắt, Story Points badge, Avatar Assignee, Cờ cảnh báo thời gian trễ Milestone nếu có.
  - Phân nhóm Swimlanes: Gom nhóm ngang theo Epic hoặc Assignee.

### 3.3. Task / Work Item Detail Modal & View (`TaskDetailView.jsx`)
* **Cột trái (Nội dung chính)**:
  - Header: Breadcrumb `Dự án / Parent Epic / PRJ-101`, Tiêu đề tóm tắt inline-editable.
  - Trạng thái: Dropdown chuyển đổi trạng thái FSM (chỉ hiện các Transition hợp lệ).
  - Khung soạn thảo mô tả công việc (Markdown Editor).
  - Danh sách Sub-tasks: Bảng tiến độ, thêm nhanh subtask con, checkbox hoàn thành.
  - Linked Issues: Danh sách liên kết `BLOCKS`, `RELATES_TO` kèm badge cảnh báo.
  - Tab Hoạt động: Cây bình luận trao đổi (hỗ trợ @mention) và Lịch sử thay đổi (Changelog Audit Diff).
* **Cột phải (Thuộc tính & Metadata)**:
  - Người thực hiện (Assignee), Người báo cáo (Reporter).
  - Sprint, Component, Fix Version, Priority.
  - Story Points / Ước lượng thời gian (Original / Remaining).
  - Trường tùy chỉnh (Custom Fields JSONB).
  - Live Presence: Avatar những người đang cùng xem Task này trực tiếp.

### 3.4. Các Hộp thoại Modal Nghiệp vụ (Modals)
1. `CreateProjectModal`: Tạo dự án mới, chọn loại Scrum/Kanban, nhập Key, chọn Team, hoặc chọn **Tạo từ Template mẫu**.
2. `TemplateSelectorModal`: Xem trước cây phân cấp của các Template mẫu (E-Commerce, Cloud Migration), chọn ngày bắt đầu mong muốn để hệ thống tự động tịnh tiến timeline.
3. `CreateWorkItemModal`: Tạo Epic, Story, Task, Bug, Subtask (chọn Parent hợp lệ theo quy tắc phân cấp 3 tầng).
4. `StartSprintModal` & `CloseSprintModal`: Xác nhận bắt đầu Sprint, chọn hành động dồn task dở dang khi đóng Sprint.
5. `CreateTeamModal`: Tạo nhóm làm việc mới và gán thành viên.

---

## 4. QUY CHUẨN ĐỒNG BỘ REALTIME & OPTIMISTIC UI

1. **Optimistic UI Updates**:
   - Khi kéo thả Card trên Board hoặc Backlog: Giao diện cập nhật vị trí ngay lập tức (Zero-latency).
   - Nếu backend trả về `409 CONFLICT` (Optimistic Lock): Hiển thị thông báo Toast cảnh báo và tự động Reconcile lại dữ liệu mới nhất từ server.
2. **WebSocket STOMP Subscriptions**:
   - Subscribe `/topic/projects/{projectId}/board` khi mở trang dự án.
   - Khi nhận event `ITEM_MOVED`, `ITEM_CREATED`, `ITEM_TRANSITIONED`: Tự động cập nhật State của Board mà không reload trang.
3. **Live Typing & Presence**:
   - Gửi heartbeat WebSocket khi mở Task Detail.
   - Nhận diện `typing:work-item:{id}` để hiển thị hoạt ảnh gõ bàn phím.
