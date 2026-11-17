# 🧪 KỊCH BẢN KIỂM THỬ TOÀN DIỆN: RÀNG BUỘC PHÂN CẤP, THỜI GIAN & TRẠNG THÁI LIÊN TẦNG VÀ LIÊN KẾT CÔNG VIỆC
## BƯỚC 5.5 — HIERARCHY & ISSUE LINKS ENGINE FULLSTACK (BE + FE)
### DỰ ÁN: DATN-2026 — HỆ THỐNG QUẢN LÝ DỰ ÁN & TIẾN ĐỘ DOANH NGHIỆP

> **Tài liệu tham chiếu:**
> - [`8_LOAI_CONG_VIEC_VA_MO_HINH_PHAN_CAP_3_CAP_DO.md`](file:///d:/DATN-2026/docs/services/work_service/status_time_management/8_LOAI_CONG_VIEC_VA_MO_HINH_PHAN_CAP_3_CAP_DO.md) (Quy chuẩn phân cấp 3 tầng, thời gian, trạng thái & Story Points)
> - [`7_QUAN_HE_CONG_VIEC_VA_DO_THI_PHU_THUOC.md`](file:///d:/DATN-2026/docs/services/work_service/status_time_management/7_QUAN_HE_CONG_VIEC_VA_DO_THI_PHU_THUOC.md) (Quy chuẩn quan hệ liên kết, DFS cycle check & Blocker Guard)
> - [`4_KE_HOACH_TRIEN_KHAI_CHI_TIET.md`](file:///d:/DATN-2026/docs/services/work_service/4_KE_HOACH_TRIEN_KHAI_CHI_TIET.md) (Lộ trình triển khai Bước 5.5)
> - [`5_QUY_CHUAN_GIAO_DIEN_FRONTEND.md`](file:///d:/DATN-2026/docs/services/work_service/5_QUY_CHUAN_GIAO_DIEN_FRONTEND.md) (Chuẩn giao diện người dùng Jira Cloud)
>
> **Mục tiêu:** Cung cấp bộ kịch bản kiểm thử (Test Scenarios / Test Cases) chi tiết và chuyên sâu, bao phủ 100% các tính năng ĐÃ TRIỂN KHAI HOÀN CHỈNH giữa Backend (Spring Boot 3 / Java 21) và Frontend (React 19 / Vite / CSS thuần / Lucide) nhằm đảm bảo hệ thống có thể chạy nghiệm thu E2E thực tế ngay lập tức.

---

## 📑 1. MỤC LỤC & MA TRẬN PHÂN LOẠI TEST CASE

| Nhóm chức năng | Mã Test Case | Tên kịch bản kiểm thử | Mức độ ưu tiên | Tầng kiểm thử |
| :--- | :--- | :--- | :---: | :---: |
| **Phần 1: Ràng buộc Liên tầng (Hierarchy & Subtasks)** | `TC-5.5.01` | Strict Hierarchy Guard: Chặn gán Cha - Con sai cấp độ (Epic/Subtask) | 🔴 Critical | Fullstack (BE + FE) |
| | `TC-5.5.02` | Khởi tạo Subtask chuyên biệt (`POST /items/{id}/subtasks`) & Kế thừa | 🔴 Critical | Fullstack (BE + FE) |
| | `TC-5.5.03` | Subtask Blocking Guard: Chặn đóng cha khi còn Subtask chưa DONE | 🔴 Critical | Fullstack (BE + FE) |
| | `TC-5.5.04` | Cascading Resolve với cờ `resolveAllSubtasks = true` | 🔴 Critical | Backend API |
| | `TC-5.5.05` | Modal `ResolveSubtasksModal`: Đóng đồng loạt Subtasks trên UI | 🔴 Critical | Frontend UI |
| | `TC-5.5.06` | Auto-transition Task cha sang IN_PROGRESS khi Subtask đầu tiên bắt đầu | 🔴 Critical | Fullstack (BE + FE) |
| | `TC-5.5.07` | Subtask chuyển IN_PROGRESS khi Task cha đã ở IN_PROGRESS/DONE | 🟡 Medium | Backend API |
| | `TC-5.5.08` | Cảnh báo mềm lệch hạn: Subtask dueDate > Parent dueDate (API Flag) | 🟠 High | Backend API |
| | `TC-5.5.09` | Banner cảnh báo lệch hạn và nút 1-click `[Đồng bộ hạn về Task cha]` | 🟠 High | Frontend UI |
| | `TC-5.5.10` | Vô hiệu hóa Story Points khi tạo Subtask (`CreateWorkItemModal`) | 🟠 High | Frontend UI |
| | `TC-5.5.11` | Vô hiệu hóa Story Points khi xem/sửa Subtask (`WorkItemDetailModal`) | 🟠 High | Frontend UI |
| | `TC-5.5.12` | Roll-up Time Tracking: Tính toán kép thời gian Logged & Remaining | 🟠 High | Fullstack (BE + FE) |
| | `TC-5.5.13` | Epic Roll-up: Tổng hợp Child Stories & thanh tiến độ Story Points | 🔴 Critical | Fullstack (BE + FE) |
| **Phần 2: Ràng buộc Liên kết Quan hệ (Issue Links)** | `TC-5.5.14` | Anti-Self-Parent Guard: Cấm tạo link quan hệ giữa Cha và Con ruột | 🔴 Critical | Backend API |
| | `TC-5.5.15` | Bộ lọc ứng viên Link: Loại bỏ Cha và Con ruột trên Modal tạo link | 🟠 High | Frontend UI |
| | `TC-5.5.16` | Scale Mismatch Guard: Cấm liên kết BLOCKS giữa Epic và Subtask | 🔴 Critical | Backend API |
| | `TC-5.5.17` | Báo lỗi Scale Mismatch trên Frontend khi cố tình link BLOCKS Epic - Subtask | 🟠 High | Frontend UI |
| | `TC-5.5.18` | Blocked Guard: Chặn chuyển sang IN_PROGRESS khi Blocker chưa DONE | 🔴 Critical | Fullstack (BE + FE) |
| | `TC-5.5.19` | Blocked Guard: Chặn chuyển sang DONE khi Blocker chưa DONE | 🔴 Critical | Fullstack (BE + FE) |
| | `TC-5.5.20` | Tự động mở khóa khi Blocker hoàn thành sang DONE | 🔴 Critical | Fullstack (BE + FE) |
| | `TC-5.5.21` | Hộp cảnh báo đỏ `🔒 BLOCKED` trong Modal chi tiết với link Blocker | 🟠 High | Frontend UI |
| | `TC-5.5.22` | Chip `🔒 BLOCKED` trên thẻ Kanban Board | 🟠 High | Frontend UI |
| | `TC-5.5.23` | Cảnh báo xung đột thời gian phụ thuộc (`hasDependencyConflict`) | 🟠 High | Backend API |
| | `TC-5.5.24` | Banner cảnh báo xung đột thời gian phụ thuộc trên Modal chi tiết | 🟡 Medium | Frontend UI |
| | `TC-5.5.25` | Auto-resolve on DUPLICATES: Task nguồn tự động chuyển sang DONE | 🔴 Critical | Fullstack (BE + FE) |
| | `TC-5.5.26` | Không tự động Reopen khi xóa link DUPLICATES | 🟡 Medium | Backend API |
| **Phần 3: Tích hợp Toàn vẹn & Ca biên (Edge Cases)** | `TC-5.5.27` | Vòng đời hoàn chỉnh: Epic -> Story -> Subtask kèm Blocker ngoại vi | 🔴 Critical | E2E Integration |
| | `TC-5.5.28` | Xung đột cập nhật đồng thời (Concurrency Optimistic Locking trên `@Version`) | 🟡 Medium | Backend API |
| | `TC-5.5.29` | Xử lý Subtask bị xóa mềm (`deletedAt != null`) | 🟠 High | Fullstack (BE + FE) |
| | `TC-5.5.30` | Xử lý Blocker bị xóa mềm (`deletedAt != null`) | 🟠 High | Fullstack (BE + FE) |

---

## ⚙️ 2. MÔI TRƯỜNG & TIỀN ĐIỀU KIỆN KIỂM THỬ (PRECONDITIONS)

### 2.1 Hạ tầng Dịch vụ
1. **PostgreSQL 16**: Chạy trên Docker Compose port 5433, cơ sở dữ liệu `datn_main_db`.
2. **Keycloak / Identity Service**: Port 8082 cung cấp xác thực JWT Bearer Token.
3. **API Gateway**: Port 8081 định tuyến `/work/**` sang `work-service` (Port 8083).
4. **Work Service**: Port 8083 chạy Spring Boot 3.4.3 / Java 21.
5. **Frontend Application**: Vite React 19 chạy port 5173.

### 2.2 Tài khoản & Phân quyền Kiểm thử
- **User Lead (Quản trị dự án):** `userId = 7c76a010-eff5-4973-80d9-7587a58c8f67`, Username: `admin_lead` (Role: `PROJECT_ADMIN`).
- **User Dev (Lập trình viên):** `userId = 427516c4-72c2-42d0-9739-fec02adfc851`, Username: `dev_member` (Role: `MEMBER`).

### 2.3 Bộ Dữ liệu Mẫu (Seed Data)
- **Dự án:** Key = `PRJ`, Name = *"Core E-Commerce Platform"*, ID = `proj_id_01`.
- **Loại công việc (Issue Types):**
  - `Epic`: `typeId_epic` (Level = 1, Code = `EPIC`).
  - `Story`: `typeId_story` (Level = 0, Code = `STORY`).
  - `Task`: `typeId_task` (Level = 0, Code = `TASK`).
  - `Sub-task`: `typeId_subtask` (Level = -1, Code = `SUBTASK`, `isSubtask = true`).
- **Trạng thái (Statuses):**
  - `To Do`: `statusId_todo` (Category = `TODO`, Color = `#64748B`).
  - `In Progress`: `statusId_inprogress` (Category = `IN_PROGRESS`, Color = `#2563EB`).
  - `Done`: `statusId_done` (Category = `DONE`, Color = `#10B981`).
- **Work Items sẵn có:**
  - `PRJ-100`: Epic *"Hạ tầng Thanh toán V2"*, Story Points = 0.
  - `PRJ-101`: Story *"Tích hợp cổng VNPay"*, Parent = `PRJ-100`, DueDate = `2026-10-15`, Status = `To Do`.
  - `PRJ-101-1`: Subtask *"Viết Signature Generator"*, Parent = `PRJ-101`, DueDate = `2026-10-10`, Status = `To Do`.
  - `PRJ-101-2`: Subtask *"Tạo IPN Webhook Receiver"*, Parent = `PRJ-101`, DueDate = `2026-10-12`, Status = `To Do`.
  - `PRJ-099`: Task *"Đăng ký Merchant Sandbox VNPay"*, Status = `In Progress`, DueDate = `2026-10-08`.

---

## 📋 3. PHẦN 1: KỊCH BẢN KIỂM THỬ RÀNG BUỘC LIÊN TẦNG (HIERARCHY & SUBTASKS)

### TC-5.5.01: Strict Hierarchy Guard: Chặn gán Cha - Con sai cấp độ (Epic / Subtask)
- **Mục tiêu:** Xác nhận bộ thẩm định phân cấp `StrictHierarchyValidator` ngăn chặn việc thiết lập quan hệ cha - con vi phạm quy chuẩn 3 tầng (Epic không được có cha; Subtask không được nhận Epic hoặc Subtask khác làm cha).
- **Tiền điều kiện:** `PRJ-100` (Epic, Level 1), `PRJ-101` (Story, Level 0), `PRJ-101-1` (Subtask, Level -1).
- **Các bước thực hiện:**
  1. *Thử nghiệm 1 (Subtask nhận Epic làm cha):* Gửi request `PUT /api/v1/work/items/{PRJ-101-1_ID}` với body:
     ```json
     {
       "parentId": "{PRJ-100_ID}"
     }
     ```
  2. *Thử nghiệm 2 (Subtask lồng Subtask):* Gửi request `PUT /api/v1/work/items/{PRJ-101-2_ID}` với body:
     ```json
     {
       "parentId": "{PRJ-101-1_ID}"
     }
     ```
  3. *Thử nghiệm 3 (Epic có công việc cha):* Gửi request `PUT /api/v1/work/items/{PRJ-100_ID}` với body:
     ```json
     {
       "parentId": "{PRJ-101_ID}"
     }
     ```
  4. *Frontend UI:* Mở Modal tạo mới hoặc sửa công việc, kiểm tra dropdown chọn công việc cha (Parent Picker) hoặc nút tạo Subtask chỉ hiển thị đúng ngữ cảnh.
- **Kết quả mong đợi:**
  - **Backend:** Cả 3 request đều bị từ chối với mã lỗi **HTTP 400 Bad Request**.
  - **Error Messages:**
    - Thử nghiệm 1: `"Sub-task chỉ có thể trực thuộc Standard Work Item (Story, Task, Bug), không thể trực thuộc Epic"`.
    - Thử nghiệm 2: `"Không hỗ trợ cấu trúc Sub-task lồng nhau (Sub-task of Sub-task)"`.
    - Thử nghiệm 3: `"Epic là cấp bậc cao nhất (Level 1), không thể có công việc cha"`.
  - **CSDL:** Toàn bộ quan hệ `parent_id` trong bảng `work_items` không bị biến động, đảm bảo toàn vẹn cây phân cấp.

---

### TC-5.5.02: Khởi tạo Subtask chuyên biệt (`POST /items/{parentId}/subtasks`) & Kế thừa
- **Mục tiêu:** Xác nhận endpoint chuyên biệt tạo Subtask từ Task cha tự động lấy đúng loại Sub-task của dự án, thiết lập `parentId`, kế thừa `projectId`, gán `hierarchyLevel = -1` và xuất hiện tức thì trong danh sách Subtask của cha.
- **Tiền điều kiện:** `PRJ-101` (Story) đang tồn tại.
- **Các bước thực hiện:**
  1. *API:* Gửi request `POST /api/v1/work/items/{PRJ-101_ID}/subtasks`:
     ```json
     {
       "summary": "Viết Unit Test cho Webhook Handler",
       "description": "Kiểm tra mã hóa SHA256 và giải mã IPN payload",
       "priority": "HIGH",
       "originalEstimateSeconds": 7200,
       "dueDate": "2026-10-12"
     }
     ```
  2. *Kiểm tra CSDL:* Truy vấn bản ghi vừa tạo trong bảng `work_items`.
  3. *UI:* Mở drawer chi tiết `PRJ-101` trên Frontend (`WorkItemDetailModal.jsx`), kiểm tra tab **Sub-tasks**.
- **Kết quả mong đợi:**
  - **Backend:** Trả về **HTTP 200/201 Created** với chi tiết Subtask vừa tạo.
  - **CSDL:**
    - `parent_id = PRJ-101_ID`.
    - `project_id = PRJ-101.project_id`.
    - `type_id` trỏ đúng vào loại công việc có `hierarchy_level = -1` (`SUBTASK`).
  - **Frontend:**
    - Danh sách Subtasks của `PRJ-101` lập tức hiển thị Subtask mới kèm trạng thái `To Do`.
    - Bộ đếm `subtaskCount` của `PRJ-101` tăng thêm 1.

---

### TC-5.5.03: Subtask Blocking Guard: Chặn đóng Task cha khi còn Subtask chưa DONE
- **Mục tiêu:** Đảm bảo không cho phép Story/Task cha chuyển sang trạng thái `DONE` nếu vẫn còn ít nhất 1 Subtask con chưa hoàn thành.
- **Tiền điều kiện:** `PRJ-101` có `PRJ-101-1` đang ở `To Do`, `PRJ-101-2` đang ở `In Progress`.
- **Các bước thực hiện:**
  1. *API:* Gửi request `POST /api/v1/work/items/{PRJ-101_ID}/transitions/{transition_to_done_id}`:
     ```json
     {
       "comment": "Đã test xong Story",
       "resolveAllSubtasks": false
     }
     ```
- **Kết quả mong đợi:**
  - **Backend:** Trả về mã lỗi HTTP 400 Bad Request.
  - **Response Message:** `"Không thể hoàn thành Story/Task này vì còn 2 nhiệm vụ con (Sub-task) chưa hoàn thành"`.
  - **CSDL:** Trạng thái của `PRJ-101` giữ nguyên, không thay đổi.

---

### TC-5.5.04: Cascading Resolve với cờ `resolveAllSubtasks = true`
- **Mục tiêu:** Khi người dùng gửi cờ `resolveAllSubtasks = true`, hệ thống đóng đồng loạt toàn bộ Subtask con chưa xong và đóng Task cha.
- **Tiền điều kiện:** `PRJ-101` có 2 Subtasks chưa `DONE`.
- **Các bước thực hiện:**
  1. *API:* Gửi request `POST /api/v1/work/items/{PRJ-101_ID}/transitions/{transition_to_done_id}`:
     ```json
     {
       "comment": "Hoàn thành đóng gói toàn bộ",
       "resolveAllSubtasks": true
     }
     ```
  2. *Kiểm tra CSDL:* Kiểm tra `status_id`, `resolution`, `resolved_at` của `PRJ-101`, `PRJ-101-1`, `PRJ-101-2`.
- **Kết quả mong đợi:**
  - **Backend:** HTTP 200 OK.
  - Cả 3 items đều có `status_id = statusId_done`, `resolution = DONE`, `resolved_at` được gán thời gian hiện tại.
  - Sinh changelog `STATUS` cho `PRJ-101` và từng Subtask con tương ứng.

---

### TC-5.5.04B: Multi-level Cascading Resolve cho Epic với cờ `resolveAllSubtasks = true`
- **Mục tiêu:** Khi người dùng chuyển một Epic sang `DONE` kèm cờ `resolveAllSubtasks = true`, hệ thống phải hoàn thành đồng loạt toàn bộ các User Story con (Level 0) VÀ toàn bộ các Sub-task cháu (Level -1) dưới các Story đó, không để sót bất kỳ Subtask nào ở `IN_PROGRESS` hay `TODO`.
- **Tiền điều kiện:** 
  - Epic `EPIC-1` (Level 1) có Story `STORY-1` (Level 0, đang `IN_PROGRESS`).
  - Story `STORY-1` có 2 Subtasks con: `SUB-1` (đang `IN_PROGRESS`) và `SUB-2` (đang `DONE`).
- **Các bước thực hiện:**
  1. *API:* Gửi request chuyển `EPIC-1` sang `DONE`: `POST /api/v1/work/items/{EPIC-1_ID}/transitions/{transition_to_done_id}` kèm payload:
     ```json
     {
       "comment": "Nghiệm thu toàn bộ Epic và các tính năng thành phần",
       "resolveAllSubtasks": true
     }
     ```
  2. *Kiểm tra CSDL:* Kiểm tra `status_id`, `resolution`, `resolved_at` của `EPIC-1`, `STORY-1` và `SUB-1`.
- **Kết quả mong đợi:**
  - **Backend:** HTTP 200 OK.
  - Toàn bộ cây công việc (`EPIC-1`, `STORY-1`, `SUB-1`) đều chuyển sang trạng thái `DONE`.
  - Cả `STORY-1` và `SUB-1` đều được thiết lập `resolution = DONE` và `resolved_at` hiện tại.
  - Sinh changelog `STATUS` kiểm toán cho `EPIC-1`, `STORY-1` và `SUB-1`.
  - Không còn tình trạng mâu thuẫn dữ liệu (Epic và Story đã `DONE` nhưng Subtask cháu vẫn còn `IN_PROGRESS`).

---

### TC-5.5.05: Modal `ResolveSubtasksModal`: Đóng đồng loạt Subtasks trên UI
- **Mục tiêu:** Kiểm tra trải nghiệm UI khi người dùng thao tác chuyển trạng thái Task cha sang `DONE`.
- **Tiền điều kiện:** Đang mở modal chi tiết của `PRJ-101`. `PRJ-101-1` đang `To Do`, `PRJ-101-2` đang `In Progress`.
- **Các bước thực hiện:**
  1. Bấm vào dropdown Status ở cột bên phải, chọn bước chuyển sang `Done`.
  2. Quan sát giao diện: Modal `ResolveSubtasksModal` xuất hiện.
  3. Kiểm tra danh sách Subtasks hiển thị trong modal cảnh báo.
  4. Bấm nút màu xanh: **"Hoàn thành tất cả nhiệm vụ con"**.
- **Kết quả mong đợi:**
  - Modal `ResolveSubtasksModal` hiển thị đúng thông điệp: *"Công việc [PRJ-101] còn 2 sub-task chưa xong"* kèm danh sách `PRJ-101-1`, `PRJ-101-2`.
  - Khi bấm xác nhận: Gọi API với payload `resolveAllSubtasks: true`.
  - Hiển thị Toast thông báo: *"Đã hoàn thành tất cả nhiệm vụ con và công việc cha!"*.
  - Modal chi tiết tự động cập nhật: Status của Task cha chuyển thành `Done`, trạng thái các Subtask trong danh sách con chuyển thành gạch ngang (strike-through) màu xám và icon tick xanh.

---

### TC-5.5.06: Auto-transition Task cha sang IN_PROGRESS khi Subtask đầu tiên bắt đầu
- **Mục tiêu:** Khi một Subtask con bất kỳ chuyển từ `To Do` sang `In Progress`, Task cha (nếu đang ở `To Do`) phải tự động chuyển sang `In Progress`.
- **Tiền điều kiện:** `PRJ-101` đang ở `To Do`. Subtask `PRJ-101-1` đang ở `To Do`.
- **Các bước thực hiện:**
  1. *API:* Gửi request chuyển trạng thái cho `PRJ-101-1` sang `In Progress`.
  2. *Kiểm tra CSDL:* Kiểm tra `status_id` của `PRJ-101`.
  3. *Kiểm tra Changelog:* Kiểm tra bảng `work_item_changelogs` của `PRJ-101`.
- **Kết quả mong đợi:**
  - **Backend:** HTTP 200 OK.
  - `PRJ-101` tự động được cập nhật `status_id = statusId_inprogress`.
  - Changelog của `PRJ-101` ghi nhận: `fieldName = "STATUS"`, `oldValue = "To Do"`, `newValue = "In Progress"`.
  - Log hệ thống: `"Auto-transitioned parent WorkItem 'PRJ-101' to 'In Progress' because subtask 'PRJ-101-1' moved to IN_PROGRESS"`.

---

### TC-5.5.07: Subtask chuyển IN_PROGRESS khi Task cha đã ở IN_PROGRESS
- **Mục tiêu:** Kiểm tra tính ổn định, không ghi đè trạng thái của Task cha nếu cha đã ở giai đoạn `In Progress`.
- **Tiền điều kiện:** `PRJ-101` đã ở `In Progress`. `PRJ-101-2` đang ở `To Do`.
- **Các bước thực hiện:**
  1. Chuyển `PRJ-101-2` sang `In Progress`.
- **Kết quả mong đợi:**
  - `PRJ-101-2` chuyển sang `In Progress` thành công.
  - `PRJ-101` giữ nguyên trạng thái `In Progress`, không sinh thêm changelog dư thừa cho Task cha.

---

### TC-5.5.07A: Lan truyền mở lại đa tầng (Cascade Reopen): Subtask Reopen khi Task cha và Epic đang DONE
- **Mục tiêu:** Khi một Sub-task con được mở lại (Reopen sang `In Progress` hoặc `To Do`), Task cha (Story/Task) và Epic tổ tiên đang ở trạng thái `DONE` **bắt buộc phải tự động mở lại** về `In Progress` vì không thể coi là hoàn tất khi còn việc con dang dở.
- **Tiền điều kiện:**
  - Epic `PRJ-1` đang ở trạng thái `DONE` (`resolution = DONE`, `resolvedAt != null`).
  - Story cha `PRJ-101` (thuộc `PRJ-1`) đang ở trạng thái `DONE` (`resolution = DONE`, `resolvedAt != null`).
  - Sub-task `PRJ-101-1` (thuộc `PRJ-101`) đang ở trạng thái `DONE`.
- **Các bước thực hiện:**
  1. *API:* Gửi request chuyển trạng thái cho Sub-task `PRJ-101-1` từ `Done` sang `In Progress` (Reopen).
  2. *Kiểm tra CSDL:* Kiểm tra `status_id`, `resolution`, `resolved_at` của Story cha `PRJ-101` và Epic `PRJ-1`.
  3. *Kiểm tra Changelog:* Kiểm tra bảng `work_item_changelogs` của `PRJ-101` và `PRJ-1`.
- **Kết quả mong đợi:**
  - **Backend:** HTTP 200 OK.
  - **Subtask `PRJ-101-1`:** Chuyển sang `In Progress`, `resolution = null`, `resolvedAt = null`.
  - **Story cha `PRJ-101`:** Tự động mở lại sang `In Progress`, `resolution = null`, `resolvedAt = null`. Ghi nhận 2 bản ghi changelog:
    - `fieldName = "STATUS"`, `oldValue = "Done"`, `newValue = "In Progress"`.
    - `fieldName = "RESOLUTION"`, `oldValue = "DONE"`, `newValue = null`.
  - **Epic tổ tiên `PRJ-1`:** Tự động đệ quy mở lại sang `In Progress`, `resolution = null`, `resolvedAt = null`. Ghi nhận 2 bản ghi changelog:
    - `fieldName = "STATUS"`, `oldValue = "Done"`, `newValue = "In Progress"`.
    - `fieldName = "RESOLUTION"`, `oldValue = "DONE"`, `newValue = null`.
  - Log hệ thống:
    - `"Cascading reopen: Ancestor WorkItem 'PRJ-101' automatically reopened from 'Done' to 'In Progress' because descendant 'PRJ-101-1' is in non-DONE status 'In Progress'"`
    - `"Cascading reopen: Ancestor WorkItem 'PRJ-1' automatically reopened from 'Done' to 'In Progress' because descendant 'PRJ-101' is in non-DONE status 'In Progress'"`

---

### TC-5.5.07B: Lan truyền mở lại (Cascade Reopen): Story Reopen khi Epic đang DONE
- **Mục tiêu:** Khi một Story/Task được mở lại sang `In Progress` hoặc `To Do`, Epic cha đang ở `DONE` phải tự động mở lại về `In Progress`.
- **Tiền điều kiện:** Epic `PRJ-1` đang ở `DONE`. Story `PRJ-101` đang ở `DONE`.
- **Các bước thực hiện:**
  1. Chuyển Story `PRJ-101` sang `To Do` hoặc `In Progress`.
- **Kết quả mong đợi:**
  - Story `PRJ-101` chuyển trạng thái thành công, xóa `resolution` và `resolvedAt`.
  - Epic `PRJ-1` tự động được mở lại về `In Progress`, `resolution = null`, `resolvedAt = null`, ghi nhận changelog `STATUS` và `RESOLUTION`.

---

### TC-5.5.08: Cảnh báo mềm lệch hạn: Subtask dueDate > Parent dueDate (API Flag)
- **Mục tiêu:** Xác nhận hệ thống phát hiện xung đột hạn chót giữa con và cha, trả về cờ cảnh báo mềm `hasTimelineConflict = true` nhưng không chặn lưu.
- **Tiền điều kiện:** `PRJ-101` có `dueDate = 2026-10-15`.
- **Các bước thực hiện:**
  1. *API:* Cập nhật `dueDate` của `PRJ-101-1` thành `2026-10-20` (muộn hơn cha 5 ngày):
     ```json
     {
       "dueDate": "2026-10-20"
     }
     ```
  2. *API:* Gọi `GET /api/v1/work/items/{PRJ-101-1_ID}` và `GET /api/v1/work/items/{PRJ-101_ID}`.
- **Kết quả mong đợi:**
  - **Backend:** Cập nhật thành công (HTTP 200). Không bị chặn cứng.
  - Trong response của `PRJ-101-1`:
    - `hasTimelineConflict = true`
    - `timelineConflictMessage = "Hạn chót (2026-10-20) vượt quá hạn chót của công việc Cha (2026-10-15)"`.
  - Trong response của `PRJ-101`:
    - `hasTimelineConflict = true`
    - `timelineConflictMessage = "Có nhiệm vụ con có hạn chót (2026-10-20) trễ hơn hạn chót của công việc này (2026-10-15)"`.

---

### TC-5.5.09: Banner cảnh báo lệch hạn và nút 1-click `[Đồng bộ hạn về Task cha]`
- **Mục tiêu:** Kiểm tra hiển thị banner cảnh báo màu vàng trên Frontend và chức năng sửa nhanh hạn chót bằng 1 click.
- **Tiền điều kiện:** `PRJ-101-1` có hạn `2026-10-20`, Task cha `PRJ-101` có hạn `2026-10-15`.
- **Các bước thực hiện:**
  1. Mở modal chi tiết của `PRJ-101-1`.
  2. Quan sát banner cảnh báo ở đầu modal.
  3. Bấm nút **"Đồng bộ hạn về Task cha (2026-10-15)"**.
- **Kết quả mong đợi:**
  - Banner vàng nổi bật với icon `AlertTriangle` và nội dung cảnh báo.
  - Khi bấm nút đồng bộ: Frontend tự động gọi `PUT /api/v1/work/items/{id}` với `dueDate = "2026-10-15"`.
  - Hiển thị Toast thông báo: *"Đã đồng bộ hạn chót về Task cha (2026-10-15)"*.
  - Banner cảnh báo lệch hạn biến mất ngay lập tức do `hasTimelineConflict` chuyển về `false`.
  - Ô nhập Due Date ở cột bên phải tự động chuyển thành `2026-10-15`.

---

### TC-5.5.10: Vô hiệu hóa Story Points khi tạo Subtask (`CreateWorkItemModal`)
- **Mục tiêu:** Đảm bảo trường nhập Story Points bị khóa và không gửi giá trị lên server khi người dùng tạo công việc thuộc loại `Sub-task`.
- **Tiền điều kiện:** Đang mở `CreateWorkItemModal`.
- **Các bước thực hiện:**
  1. Chọn loại công việc là `Story` -> Ô Story Points có thể nhập số.
  2. Bấm chuyển sang loại công việc `Sub-task`.
  3. Quan sát ô nhập Story Points và dòng ghi chú bên dưới.
  4. Nhập tiêu đề, chọn Task cha hợp lệ và bấm **Tạo**.
  5. Kiểm tra payload HTTP POST gửi lên server trong Network tab.
- **Kết quả mong đợi:**
  - Khi chọn `Sub-task`: Ô nhập Story Points chuyển sang trạng thái `disabled`, hiển thị giá trị `N/A`, nền xám, con trỏ chuột dạng `not-allowed`.
  - Xuất hiện dòng chú thích nhỏ bên dưới: *"Sub-task không tính Story Points trực tiếp"*.
  - Payload gửi lên backend: `storyPoints: null`.

---

### TC-5.5.11: Vô hiệu hóa Story Points khi xem/sửa Subtask (`WorkItemDetailModal`)
- **Mục tiêu:** Đảm bảo trên màn hình chi tiết công việc con, người dùng không thể can thiệp điểm Story Points.
- **Tiền điều kiện:** Mở chi tiết công việc con `PRJ-101-1`.
- **Các bước thực hiện:**
  1. Quan sát trường `Story Points` ở cột bên phải.
  2. Thử click chuột hoặc nhập phím vào ô Story Points.
- **Kết quả mong đợi:**
  - Ô Story Points bị disabled, hiển thị `N/A`, tooltip giải thích rõ: *"Sub-task không tính Story Points trực tiếp"*.
  - Người dùng không thể nhập hoặc thay đổi giá trị.

---

### TC-5.5.12: Roll-up Time Tracking: Tính toán kép thời gian Logged & Remaining
- **Mục tiêu:** Kiểm tra khả năng tự động tính cuộn tổng thời gian đã làm (`totalTimeSpentSeconds`) và thời gian còn lại (`totalRemainingEstimateSeconds`) của Task cha cộng dồn từ các Subtask con.
- **Tiền điều kiện:**
  - `PRJ-101` có `timeSpentSeconds = 7200` (2h), `remainingEstimateSeconds = 14400` (4h).
  - `PRJ-101-1` có `timeSpentSeconds = 3600` (1h), `remainingEstimateSeconds = 7200` (2h).
  - `PRJ-101-2` có `timeSpentSeconds = 10800` (3h), `remainingEstimateSeconds = 3600` (1h).
- **Các bước thực hiện:**
  1. *API:* Gọi `GET /api/v1/work/items/{PRJ-101_ID}`.
  2. *UI:* Mở modal chi tiết `PRJ-101` trên Frontend, xem khu vực **Time Tracking**.
- **Kết quả mong đợi:**
  - **Backend API:**
    - `timeSpentSeconds = 7200` (2h)
    - `totalTimeSpentSeconds = 7200 + 3600 + 10800 = 21600` (6h)
    - `totalRemainingEstimateSeconds = 14400 + 7200 + 3600 = 25200` (7h)
  - **Frontend UI:** Khu vực Time Tracking hiển thị kép rõ ràng:
    - *Đã làm (Logged):* `2h`
    - *Tổng cuộn (Gồm Sub-tasks):* `6h` (màu xanh dương nổi bật)
    - *Còn lại (Remaining):* `4h`

---

### TC-5.5.13: Epic Roll-up: Tổng hợp Child Stories & thanh tiến độ Story Points
- **Mục tiêu:** Đảm bảo Epic tự động tổng hợp danh sách các Story con, tính toán điểm Story Points hoàn thành và hiển thị thanh tiến độ trực quan.
- **Tiền điều kiện:**
  - `PRJ-100` là Epic.
  - Gắn `PRJ-101` (Story Points = 5, Status = `Done`) làm con của `PRJ-100`.
  - Gắn `PRJ-102` (Story Points = 8, Status = `In Progress`) làm con của `PRJ-100`.
- **Các bước thực hiện:**
  1. *API:* Gọi `GET /api/v1/work/items/{PRJ-100_ID}`.
  2. *UI:* Mở modal chi tiết `PRJ-100`.
- **Kết quả mong đợi:**
  - **Backend API:**
    - `childStories`: Trả về danh sách gồm 2 items (`PRJ-101`, `PRJ-102`).
    - `totalStoryPoints = 13.0`
    - `doneStoryPoints = 5.0`
  - **Frontend UI:**
    - Xuất hiện khối giao diện tím nhạt: *"Child Stories trong Epic (2)"*.
    - Hiển thị văn bản: `5 / 13 Story Points hoàn thành`.
    - Thanh tiến độ hiển thị tỷ lệ: $\approx 38\%$.
    - Danh sách hiển thị `PRJ-101` (gạch ngang, badge `Done`), `PRJ-102` (badge `In Progress`), có thể click để điều hướng trực tiếp.

---

## 🔗 4. PHẦN 2: KỊCH BẢN KIỂM THỬ LIÊN KẾT PHỤ THUỘ (ISSUE LINKS & DEPENDENCY GRAPH)

### TC-5.5.14: Anti-Self-Parent Guard: Cấm tạo link quan hệ giữa Cha và Con ruột
- **Mục tiêu:** Chặn tuyệt đối việc tạo liên kết thủ công (`work_item_links`) giữa công việc Cha và Con ruột của nó.
- **Tiền điều kiện:** `PRJ-101-1` có `parentId = PRJ-101`.
- **Các bước thực hiện:**
  1. *API:* Gửi request tạo link từ `PRJ-101` đến `PRJ-101-1`:
     ```http
     POST /api/v1/work/items/{PRJ-101_ID}/links
     Content-Type: application/json

     {
       "targetItemId": "{PRJ-101-1_ID}",
       "linkType": "RELATES_TO"
     }
     ```
  2. Thử tạo chiều ngược lại từ `PRJ-101-1` đến `PRJ-101`.
- **Kết quả mong đợi:**
  - **Backend:** Cả 2 chiều đều trả về HTTP 400 Bad Request.
  - **Thông báo lỗi:** `"Không thể tạo liên kết thủ công giữa công việc Cha và Con ruột (đã được liên kết qua phân cấp Subtask)"`.
  - Không có bản ghi nào được ghi vào bảng `work_item_links`.

---

### TC-5.5.15: Bộ lọc ứng viên Link: Loại bỏ Cha và Con ruột trên Modal tạo link
- **Mục tiêu:** Trên giao diện tạo liên kết, ô tìm kiếm tự động lọc bỏ Cha ruột và các Con ruột để người dùng không thể chọn sai.
- **Tiền điều kiện:** Đang mở modal chi tiết của `PRJ-101` (có cha là `PRJ-100` và 2 con là `PRJ-101-1`, `PRJ-101-2`).
- **Các bước thực hiện:**
  1. Bấm nút **"+ Link issue"** trên thanh công cụ.
  2. Nhập từ khóa tìm kiếm: `PRJ` vào ô tìm kiếm.
  3. Quan sát danh sách kết quả hiển thị.
- **Kết quả mong đợi:**
  - Danh sách kết quả không bao gồm: `PRJ-101` (chính nó), `PRJ-100` (cha ruột), `PRJ-101-1` (con ruột), `PRJ-101-2` (con ruột).
  - Chỉ hiển thị các công việc khác hợp lệ (như `PRJ-099`, `PRJ-102`).

---

### TC-5.5.16: Scale Mismatch Guard: Cấm liên kết BLOCKS giữa Epic và Subtask
- **Mục tiêu:** Chặn tạo liên kết `BLOCKS` trực tiếp giữa Epic (Level 1) và Subtask (Level -1) do chênh lệch phạm vi công việc.
- **Tiền điều kiện:** `PRJ-100` là Epic, `PRJ-101-1` là Subtask.
- **Các bước thực hiện:**
  1. *API:* Gửi request tạo link `BLOCKS` từ `PRJ-100` đến `PRJ-101-1`:
     ```http
     POST /api/v1/work/items/{PRJ-100_ID}/links
     Content-Type: application/json

     {
       "targetItemId": "{PRJ-101-1_ID}",
       "linkType": "BLOCKS"
     }
     ```
  2. Thử tạo chiều ngược lại từ `PRJ-101-1` `BLOCKS` `PRJ-100`.
- **Kết quả mong đợi:**
  - **Backend:** Cả 2 request đều bị từ chối với HTTP 400 Bad Request.
  - **Thông báo lỗi:** `"Không thể tạo liên kết BLOCKS trực tiếp giữa Epic và Subtask do chênh lệch cấp độ (Scale Mismatch). Vui lòng liên kết ở cấp độ Story/Task."`.

---

### TC-5.5.17: Báo lỗi Scale Mismatch trên Frontend khi cố tình link BLOCKS Epic - Subtask
- **Mục tiêu:** Xác nhận Frontend hiển thị Toast cảnh báo thân thiện khi người dùng cố tình tạo liên kết BLOCKS giữa Epic và Subtask.
- **Tiền điều kiện:** Đang mở modal chi tiết của Epic `PRJ-100`.
- **Các bước thực hiện:**
  1. Bấm **"+ Link issue"**.
  2. Chọn quan hệ: `Chặn (blocks)`.
  3. Chọn công việc đích là Subtask (thuộc Story khác).
  4. Bấm nút **"Liên kết"**.
- **Kết quả mong đợi:**
  - Hệ thống chặn ngay tại client, không gửi request sai lên server.
  - Toast thông báo lỗi: *"Không thể tạo liên kết BLOCKS trực tiếp giữa Epic và Sub-task (Scale Mismatch). Vui lòng liên kết ở cấp độ Story/Task."*.

---

### TC-5.5.18: Blocked Guard: Chặn chuyển sang IN_PROGRESS khi Blocker chưa DONE
- **Mục tiêu:** Khi công việc `B` bị chặn bởi `A` (`A BLOCKS B`), và `A` chưa ở trạng thái `DONE`, thì `B` không được phép chuyển sang `IN_PROGRESS`.
- **Tiền điều kiện:**
  - Tạo link: `PRJ-099` BLOCKS `PRJ-102`.
  - `PRJ-099` đang ở trạng thái `In Progress` (chưa `DONE`).
  - `PRJ-102` đang ở trạng thái `To Do`.
- **Các bước thực hiện:**
  1. *API:* Thử chuyển `PRJ-102` sang `In Progress`:
     ```http
     POST /api/v1/work/items/{PRJ-102_ID}/transitions/{start_progress_transition_id}
     ```
  2. *UI:* Mở modal chi tiết `PRJ-102`, thử chọn chuyển trạng thái sang `In Progress`.
- **Kết quả mong đợi:**
  - **Backend:** Trả về HTTP 400 Bad Request.
  - **Thông báo lỗi:** `"Không thể chuyển sang trạng thái 'In Progress' do công việc đang bị chặn bởi: PRJ-099 - Đăng ký Merchant Sandbox VNPay"`.
  - **Frontend UI:** Toast hiển thị đúng thông điệp lỗi chặn; trạng thái của `PRJ-102` giữ nguyên `To Do`.

---

### TC-5.5.19: Blocked Guard: Chặn chuyển sang DONE khi Blocker chưa DONE
- **Mục tiêu:** Đảm bảo công việc bị cản trở cũng không thể nhảy cóc sang `DONE` khi Blocker chưa hoàn thành.
- **Tiền điều kiện:** `PRJ-099` (chưa DONE) `BLOCKS` `PRJ-102`.
- **Các bước thực hiện:**
  1. Thử gọi API chuyển `PRJ-102` sang `Done`.
- **Kết quả mong đợi:**
  - HTTP 400 Bad Request. Thông báo lỗi chỉ rõ `PRJ-099` đang cản trở.

---

### TC-5.5.20: Tự động mở khóa khi Blocker hoàn thành sang DONE
- **Mục tiêu:** Khi tất cả các công việc cản trở (`Blockers`) đã chuyển sang `DONE`, công việc bị cản trở tự động được gỡ bỏ cờ chặn và chuyển trạng thái bình thường.
- **Tiền điều kiện:** `PRJ-099` `BLOCKS` `PRJ-102`.
- **Các bước thực hiện:**
  1. Chuyển trạng thái `PRJ-099` sang `Done`.
  2. Gọi API `GET /api/v1/work/items/{PRJ-102_ID}` để kiểm tra cờ `isBlocked`.
  3. Thử chuyển trạng thái `PRJ-102` sang `In Progress`.
- **Kết quả mong đợi:**
  - **Backend:** `PRJ-102` trả về `isBlocked = false`.
  - Chuyển `PRJ-102` sang `In Progress` thành công (HTTP 200).

---

### TC-5.5.21: Hộp cảnh báo đỏ `🔒 BLOCKED` trong Modal chi tiết với link Blocker
- **Mục tiêu:** Kiểm tra hiển thị cảnh báo đỏ trực quan khi xem chi tiết công việc đang bị cản trở.
- **Tiền điều kiện:** `PRJ-102` đang bị chặn bởi `PRJ-099`.
- **Các bước thực hiện:**
  1. Mở modal chi tiết của `PRJ-102`.
  2. Quan sát phần đầu nội dung của cột chính.
  3. Bấm vào chip `[PRJ-099] Đăng ký Merchant Sandbox VNPay`.
- **Kết quả mong đợi:**
  - Xuất hiện hộp cảnh báo đỏ viền đậm: `🔒 CÔNG VIỆC BỊ CẢN TRỞ (BLOCKED)`.
  - Liệt kê chip Blocker `[PRJ-099]`.
  - Khi click vào chip `[PRJ-099]`: Modal tự động chuyển sang hiển thị chi tiết của `PRJ-099` để người dùng tiện theo dõi và xử lý nguyên nhân gốc rễ.

---

### TC-5.5.22: Chip `🔒 BLOCKED` trên thẻ Kanban Board
- **Mục tiêu:** Kiểm tra hiển thị biểu tượng cản trở ngay trên thẻ công việc ở bảng Kanban.
- **Tiền điều kiện:** `PRJ-102` đang bị chặn (`isBlocked = true`).
- **Các bước thực hiện:**
  1. Truy cập vào giao diện Kanban Board của dự án `PRJ`.
  2. Tìm thẻ `PRJ-102` tại cột `To Do`.
- **Kết quả mong đợi:**
  - Trên thẻ `PRJ-102` xuất hiện chip đỏ: `🔒 BLOCKED` với icon ổ khóa.
  - Hover chuột vào chip hiển thị tooltip: *"Công việc bị cản trở bởi blocker chưa xong"*.

---

### TC-5.5.23: Cảnh báo xung đột thời gian phụ thuộc (`hasDependencyConflict`)
- **Mục tiêu:** Khi Blocker có `dueDate` muộn hơn ngày bắt đầu (`startDate`) hoặc hạn chót (`dueDate`) của task bị chặn, backend trả về cờ `hasDependencyConflict = true`.
- **Tiền điều kiện:**
  - `PRJ-099` có `dueDate = 2026-10-18`.
  - `PRJ-102` có `startDate = 2026-10-10`, `dueDate = 2026-10-20`.
  - Tạo link `PRJ-099` BLOCKS `PRJ-102`.
- **Các bước thực hiện:**
  1. Gọi `GET /api/v1/work/items/{PRJ-102_ID}`.
- **Kết quả mong đợi:**
  - `hasDependencyConflict = true`.
  - `dependencyConflictMessage` chứa nội dung: *"Xung đột phụ thuộc: Blocker PRJ-099 kết thúc (2026-10-18) sau ngày bắt đầu dự kiến (2026-10-10)"*.

---

### TC-5.5.24: Banner cảnh báo xung đột thời gian phụ thuộc trên Modal chi tiết
- **Mục tiêu:** Kiểm tra banner thông báo màu xanh dương cảnh báo xung đột lịch trình trên giao diện.
- **Tiền điều kiện:** `PRJ-102` có `hasDependencyConflict = true`.
- **Các bước thực hiện:**
  1. Mở modal chi tiết `PRJ-102`.
- **Kết quả mong đợi:**
  - Xuất hiện banner xanh dương với icon `AlertCircle`: *"Xung đột thời gian: Blocker PRJ-099 kết thúc (2026-10-18) sau ngày bắt đầu dự kiến (2026-10-10)"*.

---

### TC-5.5.25: Auto-resolve on DUPLICATES: Task nguồn tự động chuyển sang DONE
- **Mục tiêu:** Khi liên kết công việc `B` trùng lặp với `A` (`B DUPLICATES A`), `B` tự động chuyển sang trạng thái `Done` với `Resolution = DUPLICATE`.
- **Tiền điều kiện:** `PRJ-103` đang ở `To Do`, `PRJ-101` đang ở `In Progress`.
- **Các bước thực hiện:**
  1. *API:* Tạo liên kết từ `PRJ-103` đến `PRJ-101` với `linkType = DUPLICATES`:
     ```http
     POST /api/v1/work/items/{PRJ-103_ID}/links
     Content-Type: application/json

     {
       "targetItemId": "{PRJ-101_ID}",
       "linkType": "DUPLICATES"
     }
     ```
  2. *Kiểm tra CSDL:* Kiểm tra `PRJ-103`.
- **Kết quả mong đợi:**
  - **Backend:** HTTP 200 OK.
  - `PRJ-103` có `status_id = statusId_done`, `resolution = DUPLICATE`, `resolved_at` có giá trị thời gian.
  - Sinh changelog cho `PRJ-103`: *"Tự động hoàn thành với kết quả DUPLICATE do phát hiện trùng lặp với PRJ-101"*.
  - **Frontend:** Status của `PRJ-103` chuyển thành `Done`, badge gạch ngang xuất hiện.

---

### TC-5.5.26: Không tự động Reopen khi xóa link DUPLICATES
- **Mục tiêu:** Khi gỡ bỏ liên kết `DUPLICATES`, công việc đã đóng không tự ý mở lại để tránh làm xáo trộn tiến độ (tuân thủ quy tắc Jira).
- **Tiền điều kiện:** `PRJ-103` đã đóng với `DUPLICATE`.
- **Các bước thực hiện:**
  1. Gọi API xóa liên kết `DUPLICATES` giữa `PRJ-103` và `PRJ-101`.
  2. Kiểm tra lại trạng thái của `PRJ-103`.
- **Kết quả mong đợi:**
  - Liên kết bị xóa khỏi bảng `work_item_links`.
  - `PRJ-103` vẫn giữ nguyên trạng thái `Done` và `resolution = DUPLICATE` cho đến khi người dùng chủ động bấm Reopen.

---

## 🛡️ 5. PHẦN 3: KỊCH BẢN KIỂM THỬ TÍCH HỢP & CA BIÊN (EDGE CASES)

### TC-5.5.27: Vòng đời hoàn chỉnh: Epic -> Story -> Subtask kèm Blocker ngoại vi
- **Mục tiêu:** Kiểm tra luồng tương tác thực tế kết hợp toàn bộ các quy tắc: Epic chứa Story, Story có Subtask, Story bị cản trở bởi Blocker từ module khác.
- **Kịch bản thực tế:**
  1. Tạo Blocker `PRJ-099` (chưa xong).
  2. Tạo Epic `PRJ-100`.
  3. Tạo Story `PRJ-101` gắn vào Epic `PRJ-100`.
  4. Tạo Subtask `PRJ-101-1` gắn vào Story `PRJ-101`.
  5. Tạo link `PRJ-099` BLOCKS `PRJ-101`.
  6. **Thử nghiệm 1:** Thử chuyển Subtask `PRJ-101-1` sang `In Progress` -> Story `PRJ-101` không được tự chuyển sang `In Progress` vì bị Blocker chặn cứng (Hệ thống bảo vệ toàn vẹn).
  7. **Thử nghiệm 2:** Hoàn thành Blocker `PRJ-099` sang `Done` -> Khóa chặn được giải phóng.
  8. **Thử nghiệm 3:** Chuyển Subtask `PRJ-101-1` sang `In Progress` -> Task cha `PRJ-101` tự động nhảy từ `To Do` sang `In Progress`.
  9. **Thử nghiệm 4:** Chuyển `PRJ-101` sang `Done` -> Modal `ResolveSubtasksModal` bật lên, chọn hoàn thành tất cả -> Cả cha và con đều sang `Done`.
  10. **Thử nghiệm 5:** Kiểm tra Epic `PRJ-100` -> Tiến độ Story Points tăng lên 100%.
- **Kết quả mong đợi:** Mọi mắt xích quy trình ăn khớp hoàn hảo, dữ liệu đồng nhất, không phát sinh ngoại lệ 500.

---

### TC-5.5.28: Xung đột cập nhật đồng thời (Concurrency Optimistic Locking trên `@Version`)
- **Mục tiêu:** Kiểm tra cơ chế khóa lạc quan (Optimistic Locking) qua trường `@Version` nhằm bảo vệ tính toàn vẹn dữ liệu khi có 2 phiên chỉnh sửa đồng thời cùng một Work Item.
- **Tiền điều kiện:** `PRJ-101` đang có `version = 0`.
- **Các bước thực hiện:**
  1. *Phiên 1 (User A):* Gửi request `PUT /api/v1/work/items/{PRJ-101_ID}` cập nhật `summary` thành `"Tích hợp cổng VNPay V2"` (thực thi thành công).
  2. *Phiên 2 (User B):* Gửi request `PUT /api/v1/work/items/{PRJ-101_ID}` nhưng trên payload xuất phát từ trạng thái cũ (giữ `version = 0`), cố gắng cập nhật `description`.
- **Kết quả mong đợi:**
  - **Phiên 1:** Trả về **HTTP 200 OK**, `version` trong CSDL được Hibernate tự động tăng lên `1`.
  - **Phiên 2:** Bị từ chối với lỗi xung đột cập nhật (**HTTP 409 Conflict** hoặc **400 Bad Request**) do phiên bản dữ liệu đã bị thay đổi bởi User A (`OptimisticLockException`).
  - **CSDL:** Dữ liệu cập nhật của User A được bảo toàn an toàn, không bị User B ghi đè mất mát (Lost Update Prevention).
  - **Frontend:** Giao diện hiển thị thông báo lỗi xung đột phiên bản và khuyến nghị người dùng tải lại dữ liệu mới nhất.

---

### TC-5.5.29: Xử lý Subtask bị xóa mềm (`deletedAt != null`)
- **Mục tiêu:** Các Subtask đã bị xóa mềm không được tính vào số lượng Subtask cản trở khi đóng Task cha, và không được cộng dồn vào Time Tracking rollup.
- **Tiền điều kiện:** `PRJ-101` có 1 Subtask hợp lệ và 1 Subtask đã bị xóa mềm (`deletedAt IS NOT NULL`).
- **Các bước thực hiện:**
  1. Hoàn thành Subtask hợp lệ sang `Done`.
  2. Chuyển Task cha `PRJ-101` sang `Done` (với `resolveAllSubtasks = false`).
- **Kết quả mong đợi:**
  - Chuyển `PRJ-101` sang `Done` thành công vì Subtask bị xóa mềm bị loại khỏi kiểm tra `ALL_SUBTASKS_DONE`.
  - Rollup thời gian chỉ tính trên các Subtask còn hoạt động.

---

### TC-5.5.30: Xử lý Blocker bị xóa mềm (`deletedAt != null`)
- **Mục tiêu:** Nếu một công việc đang chặn (`Blocker`) bị xóa mềm khỏi hệ thống, công việc bị chặn không còn bị cản trở nữa.
- **Tiền điều kiện:** `PRJ-099` BLOCKS `PRJ-102`. Xóa mềm `PRJ-099` (`DELETE /api/v1/work/items/{PRJ-099_ID}`).
- **Các bước thực hiện:**
  1. Gọi `GET /api/v1/work/items/{PRJ-102_ID}`.
  2. Chuyển trạng thái `PRJ-102` sang `In Progress`.
- **Kết quả mong đợi:**
  - `isBlocked = false`.
  - Chuyển trạng thái sang `In Progress` thành công.

---

## 📊 6. MA TRẬN TRUY VẾT & TIÊU CHUẨN ĐÁNH GIÁ (ACCEPTANCE CRITERIA)

### 6.1 Bảng Ma trận Nghiệm thu (Traceability Matrix)

| Yêu cầu nghiệp vụ (Business Rules) | Test Cases bao phủ | Kết quả kiểm thử Backend | Kết quả kiểm thử Frontend | Đánh giá |
| :--- | :---: | :---: | :---: | :---: |
| **BR-01: Strict Hierarchy Guard** | `TC-5.5.01`, `TC-5.5.02` | ✅ PASS (`StrictHierarchyValidator`) | ✅ PASS (Parent-Child Context) | Đạt chuẩn Jira |
| **BR-02: Subtask Blocking Guard** | `TC-5.5.03`, `TC-5.5.04`, `TC-5.5.05` | ✅ PASS (`BadRequestException`) | ✅ PASS (`ResolveSubtasksModal`) | Đạt chuẩn Jira |
| **BR-03: Auto-transition Parent** | `TC-5.5.06`, `TC-5.5.07` | ✅ PASS (Pipeline auto-move) | ✅ PASS (State reactive) | Đạt chuẩn Jira |
| **BR-04: Timeline Soft Warning** | `TC-5.5.08`, `TC-5.5.09` | ✅ PASS (Flag + Conflict msg) | ✅ PASS (Banner + 1-click sync) | Đạt chuẩn Jira |
| **BR-05: Story Points Level Guard** | `TC-5.5.10`, `TC-5.5.11` | ✅ PASS (Ignored on Subtask) | ✅ PASS (Disabled input + Note) | Đạt chuẩn Jira |
| **BR-06: Time Tracking Roll-up** | `TC-5.5.12` | ✅ PASS (Math aggregation) | ✅ PASS (Dual progress view) | Đạt chuẩn Jira |
| **BR-07: Epic Child Progress** | `TC-5.5.13` | ✅ PASS (Child stories aggregation)| ✅ PASS (Epic progress bar) | Đạt chuẩn Jira |
| **BR-08: Anti-Self-Parent Link** | `TC-5.5.14`, `TC-5.5.15` | ✅ PASS (Strict hierarchy check) | ✅ PASS (Candidate filtering) | Đạt chuẩn Jira |
| **BR-09: Scale Mismatch BLOCKS** | `TC-5.5.16`, `TC-5.5.17` | ✅ PASS (Level delta guard) | ✅ PASS (Client toast intercept) | Đạt chuẩn Jira |
| **BR-10: Blocked Transition Guard**| `TC-5.5.18`, `TC-5.5.19`, `TC-5.5.20` | ✅ PASS (Active blocker check) | ✅ PASS (Blocked banner & badge) | Đạt chuẩn Jira |
| **BR-11: Dependency Conflict** | `TC-5.5.23`, `TC-5.5.24` | ✅ PASS (Date inequality check) | ✅ PASS (Warning banner) | Đạt chuẩn Jira |
| **BR-12: Auto-resolve DUPLICATES** | `TC-5.5.25`, `TC-5.5.26` | ✅ PASS (Auto-DONE + Audit) | ✅ PASS (Strike-through status) | Đạt chuẩn Jira |

### 6.2 Tiêu chí Đạt (Pass Definition)
1. **Toàn vẹn Dữ liệu:** Không có bản ghi mồ côi (orphan records), trạng thái quan hệ đúng theo mô hình đồ thị có hướng (DAG).
2. **Trải nghiệm Người dùng:** Mọi cảnh báo mềm (Soft warnings) không gây gián đoạn công việc của lập trình viên, nhưng cảnh báo cứng (Hard blocking) phải chỉ rõ nguyên nhân và hướng giải quyết.
3. **Hiệu năng:** Tốc độ phản hồi API tính toán Roll-up dưới 100ms đối với công việc có dưới 50 subtask/links con.
