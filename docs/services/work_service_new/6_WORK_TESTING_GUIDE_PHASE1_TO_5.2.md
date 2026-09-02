# 🧪 HƯỚNG DẪN KIỂM THỬ TOÀN DIỆN (TESTING GUIDE) — WORK SERVICE
## DỰ ÁN: DATN-2026 (TỪ GIAI ĐOẠN 1 ĐẾN BƯỚC 5.2)

> **Mục đích:** Tài liệu hướng dẫn chi tiết các kịch bản kiểm thử (Test Cases, cURL / Postman Payloads, Expected Responses) để kiểm tra từng luồng nghiệp vụ đã hoàn thành từ **Giai đoạn 1 đến Bước 5.2**.  
> **Cổng dịch vụ:**
> - **API Gateway:** `http://localhost:8081` (Khuyên dùng khi test qua Gateway `/api/v1/work/...`)
> - **Direct Work Service:** `http://localhost:8083` (`/api/v1/work/...`)
> - **Frontend React:** `http://localhost:5173`
> - **Tài liệu tham chiếu:** [`4_WORK_IMPLEMENTATION_PLAN.md`](file:///d:/DATN-2026/docs/services/work_service_new/4_WORK_IMPLEMENTATION_PLAN.md), [`5_WORK_FRONTEND_UI_JIRA_SPEC.md`](file:///d:/DATN-2026/docs/services/work_service_new/5_WORK_FRONTEND_UI_JIRA_SPEC.md).

---

## 📑 MỤC LỤC
1. [🏢 Phân hệ 1: Quản trị Đội nhóm, Dự án & Phân quyền RBAC](#1--phân-hệ-1-quản-trị-đội-nhóm-dự-án--phân-quyền-rbac)
2. [⚙️ Phân hệ 2: Quy trình Trạng thái & Máy trạng thái FSM (Dynamic Workflow Engine)](#2-️-phân-hệ-2-quy-trình-trạng-thái--máy-trạng-thái-fsm-dynamic-workflow-engine)
3. [🗂️ Phân hệ 3: Quản trị Work Item, Phân cấp 3 Tầng & Sinh mã nguyên tử](#3-️-phân-hệ-3-quản-trị-work-item-phân-cấp-3-tầng--sinh-mã-nguyên-tử)
4. [🎨 Phân hệ 4: Giao diện Người dùng Frontend (React 19)](#4--phân-hệ-4-giao-diện-người-dùng-frontend-react-19)
5. [📋 Bảng Checklist Kiểm thử Tổng hợp](#5--bảng-checklist-kiểm-thử-tổng-hợp)

---

## 1. 🏢 Phân hệ 1: Quản trị Đội nhóm, Dự án & Phân quyền RBAC

### 1.1. Tạo Nhóm làm việc mới (Team)
- **Endpoint:** `POST /api/v1/work/teams`
- **Headers:** `Authorization: Bearer <TOKEN>`, `Content-Type: application/json`
- **Request Body:**
  ```json
  {
    "name": "Backend Core Engineering Team",
    "description": "Nhóm phụ trách các dịch vụ lõi và nền tảng hạ tầng"
  }
  ```
- **Kết quả mong đợi (201 Created):**
  - Trả về thông tin Team kèm `id` dạng UUID và `ownerId` được gán tự động từ JWT Token.

---

### 1.2. Mời thành viên vào Nhóm (Add Team Member)
- **Endpoint:** `POST /api/v1/work/teams/{teamId}/members`
- **Request Body:**
  ```json
  {
    "userId": "11111111-1111-1111-1111-111111111111",
    "role": "MEMBER"
  }
  ```
- **Kết quả mong đợi (200 OK):** Thành viên được thêm vào nhóm với vai trò `LEADER`, `MEMBER` hoặc `VIEWER`.

---

### 1.3. Tạo Dự án mới & Kiểm tra Cơ chế Auto-Seeding Chuẩn Jira
- **Endpoint:** `POST /api/v1/work/projects`
- **Request Body:**
  ```json
  {
    "name": "Hệ thống Quản lý Bán hàng",
    "key": "SALES",
    "description": "Dự án phát triển nền tảng E-commerce",
    "projectType": "SCRUM",
    "teamId": "TEAM_UUID_VỪA_TẠO"
  }
  ```
- **Kết quả mong đợi (201 Created):**
  - Tạo thành công dự án với mã tiền tố `SALES`.
  - 👉 **Cơ chế Auto-seed tự động tạo sẵn trong Database:**
    1. **5 Loại công việc:** `Epic` (Level 1), `Story` (Level 0), `Task` (Level 0), `Bug` (Level 0), `Sub-task` (Level -1).
    2. **4 Trạng thái chuẩn:** `To Do` (`TODO`), `In Progress` (`IN_PROGRESS`), `In Review` (`IN_PROGRESS`), `Done` (`DONE`).
    3. **1 Workflow mặc định:** Kèm các bước chuyển `Start Progress`, `Submit Review`, `Resolve`, `Reopen`.
    4. **2 Bảng làm việc:** 1 Scrum Board và 1 Kanban Board.

---

### 1.4. Lấy danh sách Dự án & Xem chi tiết
- **Danh sách dự án:** `GET /api/v1/work/projects?page=0&size=10`
- **Chi tiết dự án:** `GET /api/v1/work/projects/{projectId}`
- **Xem quyền cá nhân:** `GET /api/v1/work/projects/{projectId}/permissions/my`
  - Trả về mảng các quyền hạn: `["CREATE_ISSUE", "EDIT_ISSUE", "TRANSITION_ISSUE", "MANAGE_SPRINT"...]`

---

## 2. ⚙️ Phân hệ 2: Quy trình Trạng thái & Máy trạng thái FSM (Dynamic Workflow Engine)

### 2.1. Xem Cấu hình Workflow của Dự án
- **Endpoint:** `GET /api/v1/work/projects/{projectId}/workflow`
- **Kết quả mong đợi (200 OK):** Trả về toàn bộ danh sách trạng thái và ma trận các bước chuyển (`transitions`).

---

### 2.2. Tạo thêm Trạng thái tùy biến mới (Custom Status)
- **Endpoint:** `POST /api/v1/work/statuses`
- **Request Body:**
  ```json
  {
    "name": "Ready for QA",
    "category": "IN_PROGRESS",
    "colorHex": "#8b5cf6",
    "description": "Mã nguồn đã merge, sẵn sàng cho Tester kiểm thử"
  }
  ```

---

### 2.3. Tạo Bước chuyển trạng thái mới (Workflow Transition)
- **Endpoint:** `POST /api/v1/work/workflows/{workflowId}/transitions`
- **Request Body:**
  ```json
  {
    "name": "Send to QA",
    "fromStatusId": "IN_PROGRESS_STATUS_UUID",
    "toStatusId": "READY_FOR_QA_STATUS_UUID"
  }
  ```

---

### 2.4. Kiểm tra Bước chuyển Hợp lệ của một Issue (FSM Query)
- **Endpoint:** `GET /api/v1/work/work-items/{workItemId}/transitions`
- **Kết quả mong đợi (200 OK):**
  - Chỉ trả về danh sách các transition **hợp lệ từ trạng thái hiện tại** của Issue (không cho phép nhảy cóc quy trình bừa bãi).

---

### 2.5. Thực thi Chuyển trạng thái qua FSM Engine
- **Endpoint:** `POST /api/v1/work/work-items/{workItemId}/transitions/{transitionId}`
- **Request Body:**
  ```json
  {
    "comment": "Đã hoàn thành code và viết xong unit test",
    "timeSpentSeconds": 3600
  }
  ```
- **Kết quả mong đợi (200 OK):** Issue đổi sang trạng thái mới, tự động ghi nhận ngày hoàn thành nếu chuyển sang nhóm `DONE`.

---

## 3. 🗂️ Phân hệ 3: Quản trị Work Item, Phân cấp 3 Tầng & Sinh mã nguyên tử

### 3.1. Tạo Work Item mới (Epic, Story, Task, Bug)
- **Endpoint:** `POST /api/v1/work/projects/{projectId}/items`
- **Request Body (Tạo Epic):**
  ```json
  {
    "typeId": "EPIC_TYPE_UUID",
    "summary": "Xây dựng Cổng Thanh toán VNPay và MoMo",
    "description": "Tích hợp SDK thanh toán trực tuyến cho khách hàng",
    "priority": "HIGH",
    "parentId": null,
    "storyPoints": 13.0,
    "customFields": {
      "partner": "VNPay",
      "riskLevel": "MEDIUM"
    }
  }
  ```
- **Kết quả mong đợi (201 Created):**
  - **Mã tự sinh:** `SALES-1`
  - **LexoRank:** Sinh chuỗi ban đầu `0|i00000:`
  - **Trạng thái:** Tự gán `To Do`

---

### 3.2. Tạo User Story thuộc Epic & Tạo Sub-task thuộc Story
- **Tạo Story (Level 0):**
  ```json
  {
    "typeId": "STORY_TYPE_UUID",
    "summary": "Khách hàng quét mã QR VNPay để thanh toán",
    "priority": "HIGH",
    "parentId": "EPIC_ID_SALES_1",
    "storyPoints": 5.0
  }
  ```
  ➔ Sinh mã `SALES-2`, có `parentCode: "SALES-1"`.

- **Tạo Sub-task (Level -1):**
  ```json
  {
    "typeId": "SUBTASK_TYPE_UUID",
    "summary": "Viết API Callback nhận IPN từ VNPay",
    "priority": "MEDIUM",
    "parentId": "STORY_ID_SALES_2",
    "originalEstimateSeconds": 14400
  }
  ```
  ➔ Sinh mã `SALES-3`, gắn trực tiếp vào `SALES-2`.

---

### 3.3. 🛡️ Các Test Case Kiểm soát Phân cấp Nghiêm ngặt (Strict 3-Level Hierarchy)

| STT | Kịch bản Thử nghiệm | Thao tác thực hiện | Kết quả mong đợi |
| :---: | :--- | :--- | :--- |
| **TC1** | **Tạo Epic có Parent** | Truyền `typeId = Epic` kèm `parentId = ID khác` | ❌ **Trả về 400 Bad Request:** *"Epic (Hierarchy Level 1) cannot have a parent item."* |
| **TC2** | **Tạo Subtask không Parent**| Truyền `typeId = Subtask` kèm `parentId = null` | ❌ **Trả về 400 Bad Request:** *"Sub-task (-1) must have a parent (Story/Task/Bug)."* |
| **TC3** | **Subtask lồng Subtask** | Truyền `parentId = ID của một Subtask khác` | ❌ **Trả về 400 Bad Request:** *"Multi-level subtask nesting is not allowed."* |
| **TC4** | **Parent khác Dự án** | Truyền `parentId` thuộc về Project A nhưng tạo trong Project B | ❌ **Trả về 400 Bad Request:** *"Parent item belongs to a different project."* |
| **TC5** | **Phân cấp Chuẩn Agile** | Epic (null) $\rightarrow$ Story (Epic ID) $\rightarrow$ Subtask (Story ID) | ✅ **Thành công 201 Created.** |

---

### 3.4. Lọc, Tìm kiếm & Phân trang Đa Tiêu chí
- **Tìm kiếm theo từ khóa:** `GET /api/v1/work/projects/{projectId}/items?keyword=VNPay`
- **Lọc theo độ ưu tiên:** `GET /api/v1/work/projects/{projectId}/items?priorities=HIGH,HIGHEST`
- **Lọc công việc ở Backlog:** `GET /api/v1/work/projects/{projectId}/items?isBacklog=true`
- **Lấy danh sách Sub-tasks:** `GET /api/v1/work/items/{storyId}/subtasks`

---

### 3.5. Cập nhật Thông tin & Vị trí Sắp xếp
- **Cập nhật nội dung:** `PUT /api/v1/work/items/{id}`
  ```json
  {
    "summary": "Khách hàng quét mã QR VNPay (Đã cập nhật SDK v2)",
    "storyPoints": 8.0,
    "priority": "HIGHEST"
  }
  ```
- **Cập nhật LexoRank $O(1)$:** `PATCH /api/v1/work/items/{id}/rank`
  ```json
  {
    "rank": "0|i0000b:"
  }
  ```
- **Xóa mềm:** `DELETE /api/v1/work/items/{id}`

---

## 4. 🎨 Phân hệ 4: Giao diện Người dùng Frontend (React 19)

Mở trình duyệt truy cập: **[http://localhost:5173](http://localhost:5173)**

### 4.1. Trang Danh sách Dự án (`/projects`)
- [ ] Xem toàn bộ danh sách các dự án hiện có dưới dạng thẻ Card.
- [ ] Bấm nút **Create Project** ➔ Mở modal tạo dự án mới:
  - Nhập Tên dự án: `Hệ thống ERP`
  - Chọn Loại: `SCRUM` hoặc `KANBAN`
  - Kiểm tra Project Key tự động sinh: `ERP`

### 4.2. Trang Chi tiết & Cài đặt Dự án (`/projects/:id`)
- [ ] **Tab Members & Roles:**
  - Xem danh sách thành viên trong dự án.
  - Thêm thành viên mới và đổi vai trò (`Project Admin`, `Member`, `Viewer`).
- [ ] **Tab Workflow & Statuses:**
  - Xem cấu hình sơ đồ các trạng thái công việc của dự án.

### 4.3. Modal Chi tiết Issue Chuẩn Jira 2 Cột (`WorkItemDetailModal.jsx`)
- [ ] **Cột Trái (Nội dung chính):**
  - Click vào tiêu đề để sửa nhanh inline ➔ Nhấn `Enter` hoặc blur ra ngoài để tự động lưu.
  - Sửa mô tả chi tiết Description (hỗ trợ hiển thị định dạng Markdown).
  - Kiểm tra danh sách **Sub-tasks**:
    - Xem thanh **Progress Bar** (ví dụ: `1 of 3 completed - 33%`).
    - Bấm checkbox để đổi trạng thái Sub-task.
    - Nhập tiêu đề vào ô `+ Thêm Subtask` inline và bấm `Tạo`.
  - Chuyển tab Activity: Viết bình luận vào ô **Comments** và bấm **Gửi bình luận**.
- [ ] **Cột Phải (Thuộc tính & Metadata):**
  - Bấm vào nút **Status** ➔ Hiển thị dropdown các bước chuyển FSM hợp lệ.
  - Bấm vào **Priority** ➔ Đổi độ ưu tiên kèm icon màu sắc trực quan (Highest, High, Medium, Low, Lowest).
  - Sửa ô **Story Points** ➔ Tự động lưu giá trị mới.
  - Xem thông tin người gán (Assignee), người tạo (Reporter), Sprint và ngày tháng.

---

## 5. 📋 Bảng Checklist Kiểm thử Tổng hợp

| STT | Luồng Nghiệp vụ | Postman / API | Giao diện Frontend | Trạng thái |
| :---: | :--- | :---: | :---: | :---: |
| **1** | Tạo Team & Mời thành viên | `POST /teams`, `POST /teams/{id}/members` | Tab Team Settings | [ ] |
| **2** | Tạo Dự án Scrum/Kanban + Auto-seed | `POST /projects` | Modal Create Project | [ ] |
| **3** | Phân quyền Project RBAC (Admin/Member) | `POST /projects/{id}/members` | Tab Members & Roles | [ ] |
| **4** | Cấu hình Trạng thái & Workflow FSM | `GET /projects/{id}/workflow` | Tab Workflow | [ ] |
| **5** | Chuyển trạng thái FSM hợp lệ | `POST /work-items/{id}/transitions/{tId}` | Dropdown Status trên Modal | [ ] |
| **6** | Tạo Epic / Story / Task / Bug | `POST /projects/{id}/items` | Modal Create Issue | [ ] |
| **7** | Sinh mã tuần tự (`DATN-1`, `DATN-2`) | `POST /projects/{id}/items` | Header Breadcrumbs | [ ] |
| **8** | Chặn vi phạm phân cấp 3 tầng (TC1-TC4) | `POST /projects/{id}/items` | Toast Báo lỗi 400 | [ ] |
| **9** | Thêm Subtask & Xem Progress bar | `POST /items/{parentId}/subtasks` | Subtask Progress Widget | [ ] |
| **10**| Click-to-edit inline tiêu đề & mô tả | `PUT /items/{id}` | Cột trái Modal Chi tiết | [ ] |
| **11**| Đổi Priority & Story Points | `PUT /items/{id}` | Cột phải Modal Chi tiết | [ ] |
| **12**| Bình luận tương tác Issue | `POST /items/{id}/comments` | Tab Comments trên Modal | [ ] |
