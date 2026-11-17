# 🎨 TÀI LIỆU QUY CHUẨN GIAO DIỆN FRONTEND CHUẨN JIRA CLOUD (JIRA UI SPEC)
## DỰ ÁN: DATN-2026 — QUẢN LÝ DỰ ÁN & CÔNG VIỆC DOANH NGHIỆP

> **Phân hệ:** Frontend Web Application (`frontend/`)  
> **Tech Stack:** React 19, Vite, CSS thuần (Design Tokens chuẩn Atlassian), Lucide Icons, @dnd-kit/core, Recharts  
> **Mục tiêu cốt lõi:** **Tái hiện 100% ngôn ngữ thiết kế và trải nghiệm tương tác của Atlassian Jira Cloud** (Navigation, Backlog, Kanban/Scrum Board, Issue Detail Modal, Agile Reports).  
> **Tài liệu tham chiếu:**  
> - [`1_TONG_QUAN_KIEN_TRUC_WORK_SERVICE.md`](file:///d:/DATN-2026/docs/services/work_service/1_TONG_QUAN_KIEN_TRUC_WORK_SERVICE.md)  
> - [`2_THIET_KE_CO_SO_DU_LIEU.md`](file:///d:/DATN-2026/docs/services/work_service/2_THIET_KE_CO_SO_DU_LIEU.md)  
> - [`4_KE_HOACH_TRIEN_KHAI_CHI_TIET.md`](file:///d:/DATN-2026/docs/services/work_service/4_KE_HOACH_TRIEN_KHAI_CHI_TIET.md)

---

## 📑 MỤC LỤC
1. [Hệ Thống Design Tokens & Bảng Màu Chuẩn Jira (Atlassian Design Tokens)](#1-hệ-thống-design-tokens--bảng-màu-chuẩn-jira-atlassian-design-tokens)
2. [Cấu Trúc Layout Dự Án Tổng Thể (Sidebar & Navigation)](#2-cấu-trúc-layout-dự-án-tổng-thể-sidebar--navigation)
3. [Màn Hình 1: Quản Lý Backlog & Kế Hoạch Sprint (Backlog Planning)](#3-màn-hình-1-quản-lý-backlog--kế-hoạch-sprint-backlog-planning)
4. [Màn Hình 2: Bảng Công Việc Tương Tác (Active Sprint / Kanban Board)](#4-màn-hình-2-bảng-công-việc-tương-tác-active-sprint--kanban-board)
5. [Màn Hình 3: Modal Chi Tiết Issue Chuẩn Jira (Issue Detail Modal)](#5-màn-hình-3-modal-chi-tiết-issue-chuẩn-jira-issue-detail-modal)
6. [Màn Hình 4: Báo Cáo Agile & Biểu Đồ Burndown (Agile Reports & Analytics)](#6-màn-hình-4-báo-cáo-agile--biểu-đồ-burndown-agile-reports--analytics)
7. [Quy Chuẩn Component Anatomy (Iconography, Badges & Avatars)](#7-quy-chuẩn-component-anatomy-iconography-badges--avatars)

---

## 1. Hệ Thống Design Tokens & Bảng Màu Chuẩn Jira (Atlassian Design Tokens)

Hệ thống giao diện sử dụng chuẩn màu sắc và độ bo góc của Jira Cloud:

### 1.1. Bảng màu Trạng thái & Độ ưu tiên (Status & Priority Palette)
| Loại | Tên | Mã Màu Hex | Tailwind Class | Ứng dụng |
| :--- | :--- | :--- | :--- | :--- |
| **Category TODO** | Slate Gray | `#42526E` / `#DFE1E6` | `bg-slate-100 text-slate-700` | Trạng thái Chờ làm (`TO DO`, `OPEN`, `BACKLOG`) |
| **Category IN_PROGRESS** | Primary Blue | `#0052CC` / `#DEEBFF` | `bg-blue-50 text-blue-700 border-blue-200` | Trạng thái Đang làm (`IN PROGRESS`, `IN REVIEW`) |
| **Category DONE** | Success Green | `#00875A` / `#E3FCEF` | `bg-emerald-50 text-emerald-700 border-emerald-200` | Trạng thái Hoàn thành (`DONE`, `RESOLVED`, `CLOSED`) |
| **Priority HIGHEST** | Bold Red | `#DE350B` | `text-red-600` | Cực kỳ khẩn cấp (Mũi tên đỏ kép hướng lên) |
| **Priority HIGH** | Vivid Orange | `#FF5630` | `text-orange-500` | Khẩn cấp (Mũi tên cam hướng lên) |
| **Priority MEDIUM** | Amber Yellow | `#FFAB00` | `text-amber-500` | Bình thường (Hai vạch ngang vàng) |
| **Priority LOW** | Subtle Blue/Green| `#36B37E` | `text-emerald-500` | Thấp (Mũi tên xanh lá hướng xuống) |
| **Priority LOWEST** | Muted Gray | `#6B778C` | `text-slate-400` | Rất thấp (Mũi tên xám kép hướng xuống) |

### 1.2. Màu sắc Loại Công việc (Issue Types)
- 🟣 **Epic:** Màu tím `#6554C0` (`bg-purple-600`, Icon: `Bookmark` hoặc `Zap`)
- 🟢 **User Story:** Màu xanh lá `#36B37E` (`bg-emerald-600`, Icon: `Bookmark`)
- 🔵 **Task:** Màu xanh dương `#0052CC` (`bg-blue-600`, Icon: `CheckSquare`)
- 🔴 **Bug:** Màu đỏ tươi `#E5493A` (`bg-red-600`, Icon: `CircleDot` hoặc `Bug`)
- ⚪ **Sub-task:** Màu lam nhạt `#4C9AFF` (`bg-sky-500`, Icon: `GitCommitHorizontal`)

---

## 2. Cấu Trúc Layout Dự Án Tổng Thể (Sidebar & Navigation)

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│ TOP NAVBAR: [App Launcher] [Logo DATN Jira] [Projects v] [Filters v] [Search...]   [🔔] [⚙️] [Avatar]│
├──────────────────────┬───────────────────────────────────────────────────────────────────────────┤
│ PROJECT SIDEBAR      │ MAIN WORKSPACE VIEW (Dynamic Routing)                                     │
│ 📁 Data Platform     │ ───────────────────────────────────────────────────────────────────────── │
│    Software project  │ 🔍 [Search input]  [Epic: All v]  [Assignees (👥)]  [⚡ Quick Filters]    │
│                      │                                                                           │
│ 📊 Summary           │                                                                           │
│ 📋 Backlog           │  [ NỘI DUNG MÀN HÌNH CHÍNH THEO ROUTE:                                  │
│ 📌 Active Sprints    │    • /projects/:key/backlog      -> Màn hình Backlog                      │
│ 📈 Reports           │    • /projects/:key/board        -> Màn hình Kanban / Scrum Board         │
│ 🧩 Components        │    • /projects/:key/reports      -> Màn hình Biểu đồ Burndown / Velocity  │
│ 🚀 Releases          │    • /projects/:key/settings     -> Cài đặt Dự án & Quyền ]               │
│ ⚙️ Project Settings  │                                                                           │
└──────────────────────┴───────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Màn Hình 1: Quản Lý Backlog & Kế Hoạch Sprint (Backlog Planning)

> **File ảnh tham khảo UI:** [`1_jira_scrum_backlog.jpg`](file:///d:/DATN-2026/docs/services/work_service/images/1_jira_scrum_backlog.jpg)

### Điểm nhấn thiết kế theo chuẩn Jira:
1. **Thanh Header & Bộ lọc nhanh:**
   - Ô tìm kiếm text theo tên hoặc mã task.
   - Dropdown lọc theo Epic.
   - Nhóm Avatar thành viên (bấm vào avatar ai thì lọc ra issue của người đó).
   - Nút *Quick Filters* (ví dụ: *Only My Issues*, *Recently Updated*).
2. **Khối Sprint Accordion (Collapsible Sprint Card):**
   - Header hiển thị: Tên Sprint (ví dụ: `Sprint 1 (Active)`), nhãn trạng thái `ACTIVE`, ngày bắt đầu - kết thúc, mục tiêu (Sprint Goal).
   - Nút bấm hành động phía góc phải: **`Complete sprint`** (hoặc `Start sprint` nếu ở trạng thái Future). Giống Jira, `Start sprint` **chỉ bấm được ở khối Future trên cùng** (Sprint đứng đầu kế hoạch, BR-S15); các khối Future phía dưới hiện nút bị khoá, rê chuột thấy lý do (đang có Sprint chạy / chưa tới lượt / chưa có việc).
   - Khối Future có nút bút chì để sửa tên, mục tiêu, ngày dự kiến (đổi ngày bắt đầu dự kiến chính là cách đổi thứ tự các Sprint) và nút thùng rác để xoá Sprint (có hộp xác nhận; công việc bên trong trở về Backlog).
   - Danh sách Issue dạng Card/Row nằm ngang hiển thị: Icon loại, Mã Key (`DATN-101`), Độ ưu tiên, Tóm tắt tiêu đề, Điểm Story Points, và Avatar người được gán.
3. **Khối Backlog Container:**
   - Danh sách các Issue chưa đưa vào Sprint.
   - Nút `+ Create issue` ở cuối danh sách cho phép tạo nhanh inline issue (chỉ cần nhập summary và bấm Enter).

---

## 4. Màn Hình 2: Bảng Công Việc Tương Tác (Active Sprint / Kanban Board)

> **File ảnh tham khảo UI:** [`2_jira_kanban_board.jpg`](file:///d:/DATN-2026/docs/services/work_service/images/2_jira_kanban_board.jpg)

### Điểm nhấn thiết kế theo chuẩn Jira:
1. **Các Cột Trạng Thái (Kanban Columns):**
   - Cột `TO DO (3)`, `IN PROGRESS (2/4 WIP Limit)`, `CODE REVIEW (1)`, `DONE (5)`.
   - Header mỗi cột có màu nền badge phân biệt (`TODO: Xám`, `IN PROGRESS: Xanh`, `DONE: Xanh lá`).
   - Cột có thiết lập **WIP Limits** hiển thị rõ `(Hiện tại / Tối đa)`, khi vượt quá số lượng sẽ chuyển sang viền cảnh báo màu đỏ nhẹ.
2. **Thẻ Công Việc (Card Anatomy):**
   - Góc trên trái: Icon loại công việc + Mã code `DATN-101` (bấm vào mở Modal chi tiết).
   - Góc trên phải: Icon mũi tên Độ ưu tiên + Nút menu `...` thao tác nhanh.
   - Thân thẻ: Tiêu đề công việc 2 dòng tối đa (ellipsis nếu quá dài).
   - Góc dưới: Badge Story Points (`5pts`) + Avatar Assignee tròn góc dưới phải.
3. **Kéo thả mượt mà (Smooth DnD):**
   - Kéo thả giữa các cột tự động kích hoạt tính toán $O(1)$ LexoRank.
   - Phân nhóm luồng ngang (Swimlanes theo Epics hoặc theo Assignees).

---

## 5. Màn Hình 3: Modal Chi Tiết Issue Chuẩn Jira (Issue Detail Modal)

> **File ảnh tham khảo UI:** [`3_jira_issue_detail.jpg`](file:///d:/DATN-2026/docs/services/work_service/images/3_jira_issue_detail.jpg)

### Bố cục 2 Cột Chuẩn Mực của Jira Cloud:

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│ Breadcrumbs: Project DATN / DATN-101                  [✏️] [🔗 Share] [⋯ Menu] [✕ Đóng]          │
├──────────────────────────────────────────────────────┬───────────────────────────────────────────┤
│ CỘT TRÁI (NỘI DUNG CHÍNH - 65% WIDTH)                │ CỘT PHẢI (THUỘC TÍNH METADATA - 35% WIDTH)│
│                                                      │                                           │
│ 📌 Tiêu đề Issue (Click-to-edit inline)              │ 🔹 Status                                 │
│    Implement OAuth2 and Social Login Integration     │    [ IN PROGRESS               v ]        │
│                                                      │                                           │
│ 📝 Description (Rich text Editor / Markdown)         │ 👤 Assignee                               │
│    Chi tiết yêu cầu, checklist, code snippets...     │    [ 👩 Sarah Chen (Avatar)    v ]        │
│                                                      │                                           │
│ 📋 Sub-tasks (Checklist kèm Progress Bar)            │ 👤 Reporter                               │
│    [████████████▒▒▒▒▒▒▒▒] 2 of 4 completed          │    [ 👨 Mike Ross               v ]        │
│    ☑️ DATN-102: Tạo bảng CSDL Keycloak               │                                           │
│    ☑️ DATN-103: Viết API Controller                  │ ⚡ Priority                               │
│    ⬜ DATN-104: Unit testing & Postman               │    [ 🔴 High                   v ]        │
│    ⬜ DATN-105: Tích hợp Frontend                    │                                           │
│    [+ Add subtask]                                   │ 🔢 Story Points: [ 5 ]                    │
│                                                      │ 🏃 Sprint: [ Sprint 1 (Active) v ]        │
│ 💬 Activity Tabs: [All] [Comments] [History] [Logs]  │ 🧩 Component: [ Auth Module    v ]        │
│    • Sarah Chen: "Đã xong phần backend rồi nhé..."   │ 📅 Custom Fields:                         │
│    • System: "Nguyễn Văn A changed status to DONE"   │    • Acceptance Date: 12/13/2023          │
└──────────────────────────────────────────────────────┴───────────────────────────────────────────┘
```

---

## 6. Màn Hình 4: Báo Cáo Agile & Biểu Đồ Burndown (Agile Reports & Analytics)

> **File ảnh tham khảo UI:** [`4_jira_agile_reports.jpg`](file:///d:/DATN-2026/docs/services/work_service/images/4_jira_agile_reports.jpg)

### Điểm nhấn thiết kế theo chuẩn Jira:
1. **Biểu đồ Sprint Burndown Chart:**
   - Trục hoành ($X$): Các ngày trong chu kỳ Sprint (Day 0 $\rightarrow$ Day 14).
   - Trục tung ($Y$): Tổng số điểm Story Points (SP) còn lại.
   - **Đường màu đỏ (Ideal Burndown):** Đường tiến độ lý thuyết từ tổng điểm về 0.
   - **Đường màu xanh bậc thang (Actual Burndown):** Tiến độ thực tế giảm theo từng ngày khi các issue chuyển sang `DONE`.
   - **Vùng bóng mờ xám (Scope Change):** Đánh dấu lượng điểm phát sinh được thêm vào Sprint giữa chừng.
2. **Bảng Thống kê Tóm tắt (Metrics Summary Cards):**
   - **Story Points Completed:** `38 / 45`
   - **Scope changes:** `+5 SP`
   - **Issues Done:** `14 / 16`
3. **Menu Báo cáo Mở rộng:**
   - Hỗ trợ chuyển đổi tab xem: *Velocity Chart*, *Cumulative Flow Diagram (CFD)*, *Control Chart*, *Epic Report*.

---

## 7. Quy Chuẩn Component Anatomy (Iconography, Badges & Avatars)

Để đảm bảo Frontend tái hiện chân thực 100% cảm giác Jira:
- **Font chữ:** `Inter`, `Segoe UI`, `-apple-system, BlinkMacSystemFont` sắc nét.
- **Micro-animations:** Hover thẻ card nhấc nhẹ (`hover:-translate-y-0.5 hover:shadow-md transition-all duration-150`).
- **Avatar Stack:** Nhóm avatar xếp chồng nhẹ (`-space-x-1.5`) với viền trắng (`ring-2 ring-white`).
- **Click-to-edit:** Toàn bộ trường văn bản (Summary, Description, Story points) hỗ trợ click vào sửa trực tiếp và bấm ra ngoài (blur) để tự động lưu mà không cần nút Save cồng kềnh.

---
> **Ảnh mẫu tham khảo đầy đủ đã được lưu trữ tại:** [`docs/services/work_service/images/`](file:///d:/DATN-2026/docs/services/work_service/images/)
