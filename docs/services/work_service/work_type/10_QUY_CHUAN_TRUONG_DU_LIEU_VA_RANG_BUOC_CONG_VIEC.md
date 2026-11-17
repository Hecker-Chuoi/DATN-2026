# 📑 ĐẶC TẢ TRƯỜNG DỮ LIỆU & QUY TẮC RÀNG BUỘC THEO TỪNG LOẠI CÔNG VIỆC
## HỆ THỐNG QUẢN LÝ DỰ ÁN & TIẾN ĐỘ DOANH NGHIỆP — DATN-2026
### PHÂN HỆ: WORK-SERVICE (JIRA-GRADE FULLSTACK BE + FE)

> **Tài liệu tham chiếu:**
> - [`V1__init_work_jira_schema.sql`](file:///d:/DATN-2026/backend/work-service/src/main/resources/db/migration/V1__init_work_jira_schema.sql) (Đặc tả 25 bảng CSDL & ràng buộc NOT NULL)
> - [`8_LOAI_CONG_VIEC_VA_MO_HINH_PHAN_CAP_3_CAP_DO.md`](file:///d:/DATN-2026/docs/services/work_service/status_time_management/8_LOAI_CONG_VIEC_VA_MO_HINH_PHAN_CAP_3_CAP_DO.md) (Mô hình phân cấp 3 tầng nghiêm ngặt)
> - [`CreateWorkItemRequest.java`](file:///d:/DATN-2026/backend/work-service/src/main/java/com/datn/workservice/dto/request/CreateWorkItemRequest.java) (Spring Boot DTO Validation Annotations)
> - [`StrictHierarchyValidator.java`](file:///d:/DATN-2026/backend/work-service/src/main/java/com/datn/workservice/service/StrictHierarchyValidator.java) (Bộ thẩm định phân cấp nghiệp vụ Backend)
> - [`CreateWorkItemModal.jsx`](file:///d:/DATN-2026/frontend/src/components/items/CreateWorkItemModal.jsx) (Giao diện Modal tạo công việc React 19)
> - [`WorkItemDetailModal.jsx`](file:///d:/DATN-2026/frontend/src/components/items/WorkItemDetailModal.jsx) (Giao diện xem/sửa chi tiết công việc)

---

## 📑 MỤC LỤC

1. [Tổng quan & Triết lý Thiết kế (Design Philosophy & Jira Alignment)](#1-tổng-quan--triết-lý-thiết-kế)
2. [Ma trận Tổng hợp Ràng buộc Trường Dữ liệu theo từng Task Type](#2-ma-trận-tổng-hợp-ràng-buộc-trường-dữ-liệu-theo-từng-task-type)
3. [Đặc tả Chi tiết Từng Trường Dữ liệu (Field-by-Field Specifications)](#3-đặc-tả-chi-tiết-từng-trường-dữ-liệu)
   - [3.1. Loại công việc (`typeId` / Issue Type)](#31-loại-công-việc-typeid--issue-type)
   - [3.2. Tiêu đề công việc (`summary` / Summary)](#32-tiêu-đề-công-việc-summary--summary)
   - [3.3. Công việc cha (`parentId` / Parent Issue)](#33-công-việc-cha-parentid--parent-issue)
   - [3.4. Điểm Story Points (`storyPoints`)](#34-điểm-story-points-storypoints)
   - [3.5. Mức độ ưu tiên (`priority` / Priority)](#35-mức-độ-ưu-tiên-priority--priority)
   - [3.6. Hạn chót (`dueDate` / Due Date)](#36-hạn-chót-duedate--due-date)
   - [3.7. Người thực hiện (`assigneeId` / Assignee)](#37-người-thực-hiện-assigneeid--assignee)
   - [3.8. Mô tả chi tiết (`description` / Description Markdown)](#38-mô-tả-chi-tiết-description--description-markdown)
   - [3.9. Các trường do Hệ thống Tự động Sinh & Điền (System-Handled Fields)](#39-các-trường-do-hệ-thống-tự-động-sinh--điền)
4. [Cơ chế Thẩm định Đa tầng (Multi-tier Validation Architecture)](#4-cơ-chế-thẩm-định-đa-tầng)
   - [4.1. Tầng Cơ sở dữ liệu (Database Schema Constraints)](#41-tầng-cơ-sở-dữ-liệu-database-schema-constraints)
   - [4.2. Tầng Backend DTO (Spring Jakarta Validation)](#42-tầng-backend-dto-spring-jakarta-validation)
   - [4.3. Tầng Nghiệp vụ Phân cấp (Strict Hierarchy Validator)](#43-tầng-nghiệp-vụ-phân-cấp-strict-hierarchy-validator)
   - [4.4. Tầng Giao diện Người dùng (Frontend UX / React Validation)](#44-tầng-giao-diện-người-dùng-frontend-ux--react-validation)
5. [Ví dụ Thực tế: Payload Mẫu khi Gọi API theo Từng Type](#5-ví-dụ-thực-tế-payload-mẫu-khi-gọi-api-theo-từng-type)
6. [Xử lý Ngoại lệ & Bảng Mã lỗi Thường gặp](#6-xử-lý-ngoại-lệ--bảng-mã-lỗi-thường-gặp)
7. [Tổng kết Dành cho Tester và Developer](#7-tổng-kết-dành-cho-tester-và-developer)

---

## 🎯 1. TỔNG QUAN & TRIẾT LÝ THIẾT KẾ

Trong các công cụ quản lý dự án Agile chuyên nghiệp như Atlassian Jira Cloud, việc tạo công việc mới cần cân bằng hoàn hảo giữa hai yếu tố:
1. **Tính toàn vẹn dữ liệu (Data Integrity):** Không để lọt các bản ghi thiếu thông tin định danh cốt lõi, không vi phạm cấu trúc cây phân cấp (DAG) hoặc gây xung đột khóa ngoại.
2. **Trải nghiệm tạo nhanh (Frictionless Quick Creation):** Người dùng chỉ cần nhập tối thiểu **2 thông tin** (Loại công việc + Tiêu đề) là có thể tạo ngay một thẻ công việc trên bảng Kanban/Backlog. Không ép buộc nhập hàng loạt trường thông tin phụ khi chưa cần thiết.

### Nguyên tắc phân loại trường thông tin:
* 🔴 **BẮT BUỘC (MANDATORY):** Có dấu `*` đỏ trên UI. Thiếu trường này sẽ bị chặn ngay tại Client và ném lỗi `HTTP 400 Bad Request` ở Backend.
* ⚪ **TÙY CHỌN (OPTIONAL):** Không có dấu `*`. Người dùng có thể bỏ trống hoàn toàn mà hệ thống vẫn tạo việc thành công 100%.
* ⚙️ **TỰ ĐỘNG (SYSTEM AUTO-ASSIGNED):** Không cần hiển thị ô nhập trên modal tạo mới; Backend tự động sinh mã nguyên tử, tự gán trạng thái bắt đầu của quy trình và tự gán người tạo.
* ⛔ **VÔ HIỆU HÓA / CẤM (DISABLED / FORBIDDEN):** Tùy theo cấp bậc phân cấp, một số trường sẽ bị khóa (ví dụ Story Points trên Subtask và Epic) hoặc cấm chọn (Công việc cha trên Epic) để bảo toàn kiến trúc.

---

## 📊 2. MA TRẬN TỔNG HỢP RÀNG BUỘC THEO 3 CẤP ĐỘ PHÂN CẤP CỐT LÕI

Hệ thống quản lý công việc của DATN-2026 áp dụng cơ chế validation dựa trên **3 Cấp độ Phân cấp (Strict 3-Level Hierarchy: Level 1 Epic, Level 0 Standard, Level -1 Subtask)** do `StrictHierarchyValidator.java` và `hierarchyLevel` điều phối.

Bảng dưới đây tổng hợp quy chuẩn áp dụng chi tiết theo 3 cấp độ phân cấp, minh họa qua các loại công việc đại diện (Epic, Story, Task, Bug, Sub-task):

| Tên trường dữ liệu | Khóa DTO API | 🟣 Cấp 1 (Epic) | 🟢 Cấp 0 (Story) | 🔵 Cấp 0 (Task) | 🔴 Cấp 0 (Bug) | ⚪ Cấp -1 (Sub-task) | Quy chuẩn hiển thị & Validate trên UI |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| **Loại công việc** | `typeId` | 🔴 **Bắt buộc** | 🔴 **Bắt buộc** | 🔴 **Bắt buộc** | 🔴 **Bắt buộc** | 🔴 **Bắt buộc** | **Có dấu `*` đỏ**. Bắt buộc chọn 1 trong các nút bấm pill. |
| **Tiêu đề công việc** | `summary` | 🔴 **Bắt buộc** | 🔴 **Bắt buộc** | 🔴 **Bắt buộc** | 🔴 **Bắt buộc** | 🔴 **Bắt buộc** | **Có dấu `*` đỏ**. Validate không để trống, độ dài từ 1 đến 500 ký tự. |
| **Công việc cha** | `parentId` | ⛔ **CẤM** | ⚪ *Tùy chọn* | ⚪ *Tùy chọn* | ⚪ *Tùy chọn* | 🔴 **BẮT BUỘC** | **Chỉ hiện dấu `*` đỏ khi chọn Subtask** (chặn submit nếu chưa chọn cha). Epic ẩn dropdown và hiện ghi chú xám. Story/Task/Bug không có `*`. |
| **Story Points** | `storyPoints` | ⛔ **Khóa (N/A)** | ⚪ *Tùy chọn* | ⚪ *Tùy chọn* | ⚪ *Tùy chọn* | ⛔ **Khóa (N/A)** | **Không có `*`**. Đã **khóa `N/A`** cho cả Subtask (dùng giờ) và Epic (Roll-up từ Story con). Mọi loại thuộc Cấp 0 đều được mở. |
| **Mức độ ưu tiên** | `priority` | ⚪ *Có mặc định* | ⚪ *Có mặc định* | ⚪ *Có mặc định* | ⚪ *Có mặc định* | ⚪ *Có mặc định* | **Không có `*`**. Mặc định luôn là *"Trung bình (Medium)"*. Không cần bấm chọn nếu không muốn đổi. |
| **Hạn chót** | `dueDate` | ⚪ *Tùy chọn* | ⚪ *Tùy chọn* | ⚪ *Tùy chọn* | ⚪ *Tùy chọn* | ⚪ *Tùy chọn* | **Không có `*`**. Bỏ trống thoải mái. |
| **Người thực hiện** | `assigneeId` | ⚪ *Tùy chọn* | ⚪ *Tùy chọn* | ⚪ *Tùy chọn* | ⚪ *Tùy chọn* | ⚪ *Tùy chọn* | **Không có `*`**. Mặc định là *"Chưa gán (Unassigned)"*. |
| **Mô tả chi tiết** | `description` | ⚪ *Tùy chọn* | ⚪ *Tùy chọn* | ⚪ *Tùy chọn* | ⚪ *Tùy chọn* | ⚪ *Tùy chọn* | **Không có `*`**. Hỗ trợ định dạng Markdown. Bỏ trống thoải mái. |
| **Mã công việc** | `code` | ⚙️ *Tự sinh* | ⚙️ *Tự sinh* | ⚙️ *Tự sinh* | ⚙️ *Tự sinh* | ⚙️ *Tự sinh* | Hệ thống tự sinh nguyên tử: `PRJ-1`, `PRJ-2`... |
| **Trạng thái ban đầu** | `statusId` | ⚙️ *Tự sinh* | ⚙️ *Tự sinh* | ⚙️ *Tự sinh* | ⚙️ *Tự sinh* | ⚙️ *Tự sinh* | Hệ thống tự gán trạng thái khởi đầu của dự án (thường là `To Do`). |
| **Người báo cáo** | `reporterId` | ⚙️ *Tự sinh* | ⚙️ *Tự sinh* | ⚙️ *Tự sinh* | ⚙️ *Tự sinh* | ⚙️ *Tự sinh* | Hệ thống tự trích xuất từ JWT Token của người dùng đang đăng nhập. |
| **Thứ tự sắp xếp** | `rank` | ⚙️ *Tự sinh* | ⚙️ *Tự sinh* | ⚙️ *Tự sinh* | ⚙️ *Tự sinh* | ⚙️ *Tự sinh* | Hệ thống tự tính toán thuật toán LexoRank đặt ở cuối danh sách. |

> [!NOTE]
> **Nguyên tắc mở rộng theo cấp độ:** Dự án hoàn toàn có thể tự tạo thêm bất kỳ loại công việc mới nào (như Spike, Feature, Improvement, Bài viết SEO...). Khi được cấu hình ở **Cấp độ 0 (`hierarchyLevel = 0`)**, công việc đó tự động thừa hưởng 100% quy tắc: được chọn parent là Epic hoặc đứng độc lập, được nhập Story Points, được gán vào Sprint và hiển thị trên Board.

---

## 🔍 3. ĐẶC TẢ CHI TIẾT TỪNG TRƯỜNG DỮ LIỆU

### 3.1. Loại công việc (`typeId` / Issue Type)
* **Mục đích:** Xác định chủng loại công việc và định vị cấp bậc phân cấp (`hierarchyLevel`).
* **Cấp bậc quy chuẩn:**
  * `1` (Epic): Sáng kiến lớn, module vĩ mô.
  * `0` (Standard): User Story, Task kỹ thuật, Bug lỗi.
  * `-1` (Sub-task): Nhiệm vụ con thực thi vi mô.
* **Quy tắc:**
  * **BẮT BUỘC cho 100% loại công việc.**
  * Tầng DTO Backend: `@NotNull(message = "Loại công việc (typeId) là bắt buộc")`.
  * Giao diện UI: Đánh dấu sao đỏ `*`. Người dùng bấm chọn trực tiếp vào nút biểu tượng loại việc.

### 3.2. Tiêu đề công việc (`summary` / Summary)
* **Mục đích:** Tên tóm tắt ngắn gọn mô tả mục tiêu công việc hiển thị trên thẻ Board, Backlog và tiêu đề modal.
* **Quy tắc:**
  * **BẮT BUỘC cho 100% loại công việc.**
  * Tầng Database: Cột `summary VARCHAR(500) NOT NULL`.
  * Tầng DTO Backend: `@NotBlank(message = "Tiêu đề công việc (summary) không được để trống")` và `@Size(max = 500, message = "Tiêu đề công việc tối đa 500 ký tự")`.
  * Giao diện UI: Đánh dấu sao đỏ `*`. Tự động cắt khoảng trắng thừa (`trim()`), validate chặn chuỗi rỗng và chặn vượt quá 500 ký tự.

### 3.3. Công việc cha (`parentId` / Parent Issue)
* **Mục đích:** Thiết lập mối quan hệ phân cấp hình cây giữa các tầng công việc.
* **Quy tắc động theo từng Task Type:**
  1. **Đối với Sub-task (`hierarchyLevel = -1`):**
     * **BẮT BUỘC (MANDATORY)**: Subtask không thể tồn tại độc lập mà không gắn vào một Story/Task/Bug cha.
     * UI: Hiển thị nhãn kèm **sao đỏ `*`** (`Công việc cha (Parent Issue) *`). Dropdown lọc duy nhất các công việc thuộc Level 0 (Story, Task, Bug).
     * Backend: Thẩm định qua `StrictHierarchyValidator.validateHierarchyLevels`: nếu `parentLevel == null` hoặc `parentLevel != 0` $\rightarrow$ ném `InvalidHierarchyException` (HTTP 400).
  2. **Đối với Standard Items (Story / Task / Bug - `hierarchyLevel = 0`):**
     * **TÙY CHỌN (OPTIONAL)**: Có thể thuộc về một Epic (`parentId = epicId`) hoặc đứng độc lập trong Backlog dự án (`parentId = null`).
     * UI: Không có dấu `*`. Dropdown chỉ hiển thị các Epic trong dự án kèm lựa chọn mặc định: *"-- Không có (Độc lập / Thuộc Backlog) --"*.
  3. **Đối với Epic (`hierarchyLevel = 1`):**
     * **CẤM (FORBIDDEN)**: Epic là tầng cao nhất, không được phép có bất kỳ công việc cha nào.
     * UI: Ẩn hoàn toàn dropdown chọn cha, hiển thị banner thông tin màu xám: *"ℹ️ Epic là cấp cao nhất (Level 1) và không thể trực thuộc công việc khác."* Payload tự động gán `parentId = null`.
     * Backend: Nếu request gửi kèm `parentId != null` cho Epic $\rightarrow$ ném lỗi `HTTP 400 Bad Request`.

### 3.4. Điểm Story Points (`storyPoints`)
* **Mục đích:** Đo lường độ phức tạp, khối lượng và rủi ro của yêu cầu nghiệp vụ theo dãy số Fibonacci (1, 2, 3, 5, 8, 13...).
* **Quy tắc theo từng Task Type:**
  1. **User Story (Level 0):** **TÙY CHỌN**. Ô nhập số cho phép điền điểm ước lượng hoặc bỏ trống nếu đang lập kế hoạch sơ bộ.
  2. **Technical Task & Bug (Level 0):** **TÙY CHỌN**. Thường ưu tiên đo bằng giờ (Time Tracking) nhưng hệ thống vẫn cho phép nhập điểm tùy theo văn hóa của đội ngũ Scrum.
  3. **Sub-task (Level -1):** **KHÓA (N/A)**. Subtask ước lượng thuần túy bằng thời gian thực thi (giờ/phút). Trên UI, ô nhập bị disable hiển thị chữ `N/A` và chú thích *"Sub-task không tính Story Points trực tiếp"*. Backend tự động bỏ qua (ép về `null`).
  4. **Epic (Level 1):** **KHÓA (N/A)**. Điểm Story Points của Epic được tính tự động bằng cơ chế cộng dồn (Roll-up) từ toàn bộ các Story con bên trong. Trên UI, ô nhập bị disable hiển thị `N/A` kèm chú thích *"Epic tự động tính điểm từ các Story con (Roll-up)"*.

### 3.5. Mức độ ưu tiên (`priority` / Priority)
* **Mục đích:** Đánh dấu độ khẩn cấp và tầm quan trọng của công việc (Highest, High, Medium, Low, Lowest).
* **Quy tắc:**
  * **TÙY CHỌN (Có giá trị mặc định sẵn)**.
  * Tầng Database: `priority VARCHAR(50) NOT NULL DEFAULT 'MEDIUM'`.
  * Giao diện UI: Không có dấu `*`. Hệ thống luôn kích hoạt sẵn nút chọn **"Trung bình (Medium)"**. Người dùng không cần thao tác bấm chọn nếu muốn giữ nguyên mức trung bình.

### 3.6. Hạn chót (`dueDate` / Due Date)
* **Mục đích:** Xác định ngày kết thúc dự kiến của công việc.
* **Quy tắc:**
  * **TÙY CHỌN cho 100% loại công việc.**
  * Bỏ trống thoải mái nếu công việc chạy linh hoạt theo chu kỳ lặp hoặc chưa xác định Deadline.
  * Tích hợp cơ chế kiểm tra lệch hạn: Nếu Subtask có `dueDate` trễ hơn `dueDate` của Task cha, hệ thống kích hoạt cờ cảnh báo mềm `hasTimelineConflict = true` mà không chặn lưu dữ liệu.

### 3.7. Người thực hiện (`assigneeId` / Assignee)
* **Mục đích:** Gán kỹ sư chịu trách nhiệm thực thi công việc.
* **Quy tắc:**
  * **TÙY CHỌN cho 100% loại công việc.**
  * Tầng Database: `assignee_id UUID NULL` (cho phép null).
  * Giao diện UI: Không có dấu `*`. Mặc định hiển thị mục *"Chưa gán (Unassigned)"*. Công việc có thể được tạo trước vào Backlog và phân công sau.

### 3.8. Mô tả chi tiết (`description` / Description Markdown)
* **Mục đích:** Chứa nội dung đặc tả chi tiết, tiêu chí chấp nhận (Acceptance Criteria), các bước tái hiện lỗi (Steps to Reproduce) hoặc checklist kỹ thuật.
* **Quy tắc:**
  * **TÙY CHỌN cho 100% loại công việc.**
  * Hỗ trợ định dạng văn bản giàu qua cú pháp Markdown. Bỏ trống thoải mái nếu tiêu đề đã đủ rõ ràng.

### 3.9. Các trường do Hệ thống Tự động Sinh & Điền
Người dùng và lập trình viên Frontend **không cần** quan tâm hay gửi các trường sau trong form tạo mới, Backend sẽ hoàn toàn tự động chịu trách nhiệm:

| Trường hệ thống | Kiểu dữ liệu | Cách thức Backend tự động xử lý |
| :--- | :--- | :--- |
| `projectId` | UUID | Lấy trực tiếp từ đường dẫn URL ngữ cảnh dự án (`/projects/{projectId}/items`). |
| `statusId` | UUID | Gọi `resolveInitialStatus(projectId)` để lấy trạng thái bắt đầu của dự án (thường là `To Do`). |
| `code` | VARCHAR(50) | Bộ sinh mã nguyên tử `atomicKeySequenceGenerator.generateNextCode(projectKey)` cấp số tăng dần không trùng lặp (ví dụ `PRJ-101`). |
| `reporterId` | UUID | Tự động lấy ID của người dùng đăng nhập trích xuất từ JWT Bearer Token (`userId`). |
| `rank` | VARCHAR(255) | Tính toán thuật toán `lexoRankService` để xếp thẻ việc ở cuối hàng đợi của danh sách. |
| `version` | INT | Khởi tạo bằng `0` phục vụ cơ chế khóa lạc quan (Optimistic Locking). |
| `timeSpentSeconds` | BIGINT | Khởi tạo bằng `0L`. |

---

## 🛡️ 4. CƠ CHẾ THẨM ĐỊNH ĐA TẦNG (MULTI-TIER VALIDATION)

Để đảm bảo hệ thống không bao giờ nhận dữ liệu rác hoặc dữ liệu sai lệch, các quy tắc trên được thực thi đồng thời qua 4 chốt chặn kiểm soát:

```
[ Người dùng thao tác ]
        │
        ▼
┌────────────────────────────────────────────────────────────────────────┐
│ 1. TẦNG CLIENT (React 19 / CreateWorkItemModal.jsx)                     │
│ ├── Kiểm tra Tiêu đề: Không rỗng, cắt khoảng trắng, max 500 ký tự       │
│ ├── Ép buộc chọn Công việc cha nếu là Subtask                          │
│ ├── Khóa ô Story Points nếu là Subtask hoặc Epic                       │
│ └── Lọc Dropdown Cha hợp lệ theo cấp bậc (Epic vs Standard)            │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │ (HTTP POST /projects/{id}/items)
                                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│ 2. TẦNG REQUEST DTO (Spring Boot / CreateWorkItemRequest.java)         │
│ ├── @NotNull(typeId)                                                   │
│ ├── @NotBlank @Size(max = 500) (summary)                               │
│ ├── @DecimalMin("0.0") @DecimalMax("999.99") (storyPoints)             │
│ └── @PositiveOrZero (estimateSeconds)                                  │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │ (@Valid passed)
                                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│ 3. TẦNG NGHIỆP VỤ PHÂN CẤP (StrictHierarchyValidator.java)            │
│ ├── Subtask (-1) bắt buộc có cha Level 0 (Story/Task/Bug)              │
│ ├── Cấm Subtask lồng Subtask (Subtask of Subtask)                     │
│ ├── Cấm Subtask gắn trực tiếp vào Epic                                 │
│ └── Cấm Epic (Level 1) có công việc cha                                │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │ (Validation thành công)
                                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│ 4. TẦNG CƠ SỞ DỮ LIỆU (PostgreSQL 16 / work_items table)               │
│ ├── Constraints NOT NULL: summary, type_id, status_id, code, priority   │
│ ├── Foreign Keys: projects(id), work_item_types(id), statuses(id)     │
│ └── Unique Constraint: uq_project_work_item_code (project_id, code)    │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 💻 5. VÍ DỤ THỰC TẾ: PAYLOAD MẪU KHI GỌI API THEO TỪNG TYPE

### 5.1. Tạo Epic (Tối thiểu — Chỉ cần Tiêu đề & Loại việc)
* **Endpoint:** `POST /api/v1/work/projects/{projectId}/items`
* **Request Payload:**
```json
{
  "typeId": "b188c03e-4b71-4770-80d1-0530b15c9940",
  "summary": "Hạ tầng Thanh toán V2"
}
```
* **Kết quả:** Hệ thống tạo thành công Epic `PRJ-100`, `parentId = null`, `storyPoints = null`, `status = To Do`.

---

### 5.2. Tạo User Story thuộc Epic (Kèm Hạn chót & Điểm Story Points)
* **Endpoint:** `POST /api/v1/work/projects/{projectId}/items`
* **Request Payload:**
```json
{
  "typeId": "c299d14f-5c82-4881-91e2-1641c26d0051",
  "parentId": "a0011223-3445-5667-7889-9aabbccddeef",
  "summary": "Tích hợp cổng VNPay",
  "priority": "HIGH",
  "dueDate": "2026-10-15",
  "storyPoints": 5.0,
  "description": "Là khách hàng, tôi muốn quét mã QR VNPay để thanh toán đơn hàng."
}
```
* **Kết quả:** Hệ thống tạo thành công Story `PRJ-101` gắn vào Epic `PRJ-100`.

---

### 5.3. Tạo Technical Task độc lập (Không có công việc cha)
* **Endpoint:** `POST /api/v1/work/projects/{projectId}/items`
* **Request Payload:**
```json
{
  "typeId": "d3aa025a-6d93-4992-a2f3-2752d37e1162",
  "summary": "Đăng ký Merchant Sandbox VNPay",
  "priority": "MEDIUM",
  "dueDate": "2026-10-08"
}
```
* **Kết quả:** Hệ thống tạo thành công Task `PRJ-099` đứng độc lập ngoài Backlog.

---

### 5.4. Tạo Sub-task (Bắt buộc phải có `parentId` là Story/Task)
* **Endpoint:** `POST /api/v1/work/projects/{projectId}/items`
* **Request Payload:**
```json
{
  "typeId": "e4bb136b-7ea4-4aa3-b304-3863e48f2273",
  "parentId": "d8877665-5443-3221-1009-876543210fed",
  "summary": "Viết Signature Generator",
  "priority": "MEDIUM",
  "dueDate": "2026-10-10"
}
```
* **Lưu ý:** Trường `storyPoints` không gửi (hoặc gửi `null`). Nếu gửi kèm số, Backend sẽ tự động bỏ qua để đảm bảo toàn vẹn quy tắc Subtask.

---

## ⚠️ 6. XỬ LÝ NGOẠI LỆ & BẢNG MÃ LỖI THƯỜNG GẶP

Khi dữ liệu đầu vào vi phạm các ràng buộc, hệ thống trả về phản hồi chuẩn hóa `ApiResponse<T>` với mã lỗi tương ứng:

| Tình huống vi phạm | Mã HTTP | Thông báo lỗi phản hồi (Error Message) | Cách khắc phục |
| :--- | :---: | :--- | :--- |
| Để trống tiêu đề công việc | `400 Bad Request` | `"Tiêu đề công việc (summary) không được để trống"` | Nhập ít nhất 1 ký tự khác khoảng trắng cho tiêu đề. |
| Tiêu đề vượt quá 500 ký tự | `400 Bad Request` | `"Tiêu đề công việc tối đa 500 ký tự"` | Rút ngắn tiêu đề, đưa nội dung dài vào ô Mô tả chi tiết. |
| Tạo Subtask nhưng bỏ trống `parentId` | `400 Bad Request` | `"Sub-task (-1) must have a parent (Story/Task/Bug). It cannot stand alone without a parent."` | Chọn một Story, Task hoặc Bug làm công việc cha. |
| Tạo Subtask nhưng chọn cha là Epic | `400 Bad Request` | `"Sub-task (-1) cannot be directly attached to an Epic (1). It must belong to a Story, Task, or Bug (0)."` | Gắn Subtask vào Story trung gian nằm trong Epic đó. |
| Tạo Subtask nhưng chọn cha là một Subtask khác | `400 Bad Request` | `"Sub-task (-1) cannot have another Sub-task as parent (Multi-level subtask nesting is not allowed)."` | Không lồng Subtask đa tầng; gắn trực tiếp vào Task cha Level 0. |
| Tạo Epic nhưng lại chọn công việc cha | `400 Bad Request` | `"Epic (Hierarchy Level 1) cannot have a parent item."` | Để trống mục công việc cha đối với Epic. |
| Điểm Story Points là số âm | `400 Bad Request` | `"Điểm Story Points không thể âm"` | Nhập số thực không âm (ví dụ: `0`, `1`, `2`, `3`, `5`, `8`). |
| Điểm Story Points vượt quá 999.99 | `400 Bad Request` | `"Điểm Story Points tối đa 999.99"` | Chia nhỏ công việc thành nhiều Story có số điểm hợp lý. |

---

## 🎯 7. TỔNG KẾT DÀNH CHO TESTER VÀ DEVELOPER

1. **Khi test thủ công trên giao diện Web:**
   * Bạn chỉ cần quan tâm **2 trường**: Chọn **Loại công việc** và gõ **Tiêu đề**.
   * Nếu chọn loại là **Subtask**, bắt buộc phải chọn thêm **Công việc cha**.
   * **Tất cả các trường còn lại** (Mô tả, Người thực hiện, Story Points, Hạn chót, Mức độ ưu tiên) bạn có thể **bỏ qua hoàn toàn** mà hệ thống vẫn tạo thẻ thành công và hiển thị ngay trên bảng.
2. **Khi viết Unit Test / Automation Script / MockMvc:**
   * Payload gửi lên endpoint `POST /api/v1/work/projects/{projectId}/items` chỉ cần tối thiểu:
     ```json
     {
       "typeId": "{uuid}",
       "summary": "{text}"
     }
     ```
   * (Kèm thêm `"parentId": "{uuid}"` nếu `typeId` là Subtask).
