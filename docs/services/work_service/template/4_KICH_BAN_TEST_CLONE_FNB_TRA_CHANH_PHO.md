# KỊCH BẢN KIỂM THỬ TOÀN DIỆN: CLONE DỰ ÁN MẪU F&B & TRIỂN KHAI QUÁN TRÀ CHANH PHỐ (END-TO-END TEST SCENARIO)

> **Dịch vụ kiểm thử:** `work-service` & `frontend` (DATN-2026)  
> **Dự án mẫu nguồn (Base Template):** `Xây dựng Cửa hàng F&B (Nhà hàng - Quán Café)` (`TPLFNB` - `BUSINESS`)  
> **Dự án mục tiêu:** `Trà Chanh Phố 1983 - Không Gian Vỉa Hè Chill & Check-in` (Key: `TCP`)  
> **Môi trường thử nghiệm:** Local Development (`http://localhost:5173` | Spring Boot 3.x Java 21 | PostgreSQL 16 | Keycloak OAuth2)  
> **Phiên bản:** `v1.0 - Chuẩn hóa quy trình F&B Thực chiến & Clone Engine`  
> **Trạng thái:** `Sẵn sàng thực thi (Ready for Execution)`  

---

## 📑 MỤC LỤC CHI TIẾT

1. [Tổng quan Kịch bản & Thiết lập Môi trường](#1-tổng-quan-kịch-bản--thiết-lập-môi-trường)
2. [Giai đoạn 1: Khám phá Thư viện Mẫu & Xem trước Bản thiết kế (Gallery & Preview)](#giai-đoạn-1-khám-phá-thư-viện-mẫu--xem-trước-bản-thiết-kế-gallery--preview)
3. [Giai đoạn 2: Tạo Dự án từ Mẫu F&B (Clone Engine & Date Offset)](#giai-đoạn-2-tạo-dự-án-từ-mẫu-fb-clone-engine--date-offset)
4. [Giai đoạn 3: Tùy biến Cấu hình Dự án (Customizing Project Settings)](#giai-đoạn-3-tùy-biến-cấu-hình-dự-án-customizing-project-settings)
   - 3.1. Tùy biến & Bổ sung Loại Công Việc (Work Item Types Customization)
   - 3.2. Tùy biến Quy trình (Workflow) & Thêm Trạng thái đặc thù F&B
   - 3.3. Cấu hình lại Bảng (Board Columns) & Thiết lập WIP Limit
5. [Giai đoạn 4: Quản lý & Tinh chỉnh Công việc (Work Items Execution & Enrichment)](#giai-đoạn-4-quản-lý--tinh-chỉnh-công-việc-work-items-execution--enrichment)
   - 4.1. Tinh chỉnh các Task có sẵn từ Template F&B sang Quán Trà Chanh
   - 4.2. Thêm mới Epic, Task Cấp 0 và Subtask Cấp -1 đa dạng
   - 4.3. Bổ sung Task dạng Sự cố/Rủi ro & Thiết lập Liên kết phụ thuộc (Issue Links)
6. [Giai đoạn 5: Vận hành Sprint, Active Board & Thực thi Phân công (Execution Flow)](#giai-đoạn-5-vận-hành-sprint-active-board--thực-thi-phân-công-execution-flow)
   - 5.1. Kích hoạt Sprint 1 (`Start Sprint`)
   - 5.2. Phân công công việc (Assignee) & Kéo thả thẻ trên Active Board
   - 5.3. Thử nghiệm Trạng thái `Thử vị / KCS` & Kiểm tra WIP Limit
   - 5.4. Kiểm thử Core Validator (`ALL_SUBTASKS_DONE`)
   - 5.5. Hoàn thành Sprint 1 & Chuyển giao Backlog (`Complete Sprint`)
7. [Giai đoạn 6: Đóng gói Ngược thành Mẫu Chuỗi Nhượng quyền (Save As Template)](#giai-đoạn-6-đóng-gói-ngược-thành-mẫu-chuỗi-nhượng-quyền-save-as-template)
8. [Ma trận Kiểm thử & Bảng Nghiệm thu Toàn diện (Acceptance Matrix)](#ma-trận-kiểm-thử--bảng-nghiệm-thu-toàn-diện-acceptance-matrix)

---

## 1. TỔNG QUAN KỊCH BẢN & THIẾT LẬP MÔI TRƯỜNG

### 1.1. Bối cảnh Nghiệp vụ (Business Context)
Một chủ đầu tư muốn mở mới điểm kinh doanh **"Trà Chanh Phố 1983"** — mô hình kết hợp giữa trà chanh giã tay đặc sản, đồ uống hoa quả tươi vỉa hè chill, phòng lạnh check-in phố cổ và đồ ăn vặt Hà Nội (hướng dương dừa, nem chua rán, khô bò). Thay vì tốn hàng tuần để lên kế hoạch từ đầu, chủ quán tận dụng **Dự án mẫu F&B (Nhà hàng - Quán Café)** sẵn có trong hệ thống, thực hiện:
1. **Clone** toàn bộ hệ sinh thái dự án (3 Sprint, 5 Epic, 26 Task, Quy trình 4 bước).
2. **Dời ngày tự động** theo ngày khởi động thực tế của quán.
3. **Tùy biến (Customization)** sâu cấu hình: bổ sung Loại công việc mới cho quán trà chanh, thiết lập quy trình kiểm soát chất lượng KCS đồ uống và cấu hình giới hạn WIP trên bảng.
4. **Triển khai các Task**: Sửa đổi nội dung task mẫu cho khớp quán trà chanh, thêm các task hot trend (TikTok check-in, mua sắm máy dập nắp cốc, khay topping âm bàn, bãi đỗ xe vỉa hè).
5. **Vận hành Sprint & Board**: Thử nghiệm kéo thả, phân công, tính thời gian làm việc và kiểm tra các ràng buộc logic.
6. **Lưu thành mẫu chuỗi (Franchise Template)** để nhân bản ra các cơ sở tiếp theo.

### 1.2. Bảng tham số Thiết lập Test

| Tham số | Giá trị Thiết lập | Ghi chú |
|---|---|---|
| **Dự án Mẫu nguồn** | `TPLFNB` (`Xây dựng Cửa hàng F&B (Nhà hàng - Quán Café)`) | Category: `BUSINESS` |
| **Tên dự án mới** | `Trà Chanh Phố 1983 - Không Gian Vỉa Hè Chill & Check-in` | Tên đầy đủ trên Header |
| **Mã dự án mới (Key)** | `TCP` | Tiền tố mã công việc (`TCP-1` .. `TCP-n`) |
| **Loại dự án** | `Software Scrum` | Quản lý theo Backlog, Sprint & Kanban Board |
| **Workspace** | `Default Workspace` (hoặc workspace hiện hành của bạn) | UUID: `a0000000-0000-0000-0000-000000000001` |
| **Tài khoản thực hiện** | `Lead / Project Admin` | Có quyền tạo dự án và quản trị dự án |
| **Ngày bắt đầu dự án mới** | `01/11/2026` | Ngày mốc dời timeline |
| **Hạn chót dự án mới** | `30/01/2027` (Tự động tính: +90 ngày) | Gợi ý tự động từ Timeline mẫu |

```
                       SƠ ĐỒ TIẾN TRÌNH KIỂM THỬ TỔNG THỂ
┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
│ Giai đoạn 1     │       │ Giai đoạn 2     │       │ Giai đoạn 3     │
│ Khám phá Mẫu    │ ────> │ Clone Dự án     │ ────> │ Tùy biến Cấu    │
│ F&B & Preview   │       │ TCP từ TPLFNB   │       │ hình & Workflow │
└─────────────────┘       └─────────────────┘       └─────────────────┘
                                                              │
                                                              ▼
┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
│ Giai đoạn 6     │       │ Giai đoạn 5     │       │ Giai đoạn 4     │
│ Save As Template│ <──── │ Vận hành Sprint │ <──── │ Triển khai Task │
│ Nhân chuỗi mới  │       │ & Kéo thả Board │       │ & Thêm Task mới │
└─────────────────┘       └─────────────────┘       └─────────────────┘
```

---

## GIAI ĐOẠN 1: KHÁM PHÁ THƯ VIỆN MẪU & XEM TRƯỚC BẢN THIẾT KẾ (GALLERY & PREVIEW)

Mục tiêu: Đảm bảo Thư viện mẫu hiển thị đầy đủ, phân loại chính xác và modal xem trước (Preview) render trực quan toàn bộ hệ sinh thái của mẫu F&B mà không có lỗi toàn vẹn nào.

### Test Case 1.1: Tìm kiếm & Lọc Thư viện Mẫu (Template Gallery)
- [ ] **Bước 1.1.1:** Điều hướng đến trang danh sách dự án (`/projects`). Nhấn nút **"+ Tạo dự án"**.
- [ ] **Bước 1.1.2:** Trong modal xuất hiện, chuyển sang tab **"Chọn mẫu dự án"** (Template Gallery).
- [ ] **Bước 1.1.3:** Thử nghiệm bộ lọc:
  - Chọn bộ lọc Danh mục: Nhấp chọn badge **"Kinh doanh (BUSINESS)"**.
  - Nhập từ khóa tìm kiếm vào ô input: `F&B` hoặc `Quán Café`.
- [ ] **Kết quả mong đợi:**
  - Danh sách lập tức lọc ra thẻ mẫu: **"Xây dựng Cửa hàng F&B (Nhà hàng - Quán Café)"** (Key: `TPLFNB`).
  - Trên thẻ hiển thị tóm tắt trực quan:
    + Huy hiệu phân loại: `Kinh doanh` (màu sắc phân loại rõ nét).
    + Thống kê số lượng: `3 Sprint`, `5 Epic`, `26 Công việc`, `Thời lượng: 90 ngày`.
    + Dòng luồng trạng thái thu gọn: `Cần làm → Đang thực hiện → Nghiệm thu / Đánh giá → Hoàn thành`.
    + Số lượt đã sử dụng mẫu.

### Test Case 1.2: Xem trước Toàn bộ Bản thiết kế (Template Preview Modal)
- [ ] **Bước 1.2.1:** Nhấp trực tiếp vào thẻ mẫu **"Xây dựng Cửa hàng F&B"**. Modal **"Xem trước bản thiết kế dự án mẫu"** mở ra.
- [ ] **Bước 1.2.2:** Kiểm tra từng tab chi tiết:
  - **Tab "Tổng quan" (Overview):**
    + Thống kê tổng số 4 trạng thái, 3 loại công việc (`Epic`, `Task`, `Subtask`).
    + Biểu đồ mini Timeline Gantt hiển thị 3 mốc Giai đoạn từ Ngày 1 đến Ngày 60+.
  - **Tab "Trạng thái & Quy trình" (Workflow):**
    + Hiển thị 4 trạng thái được nhóm theo màu sắc: `Cần làm` (Xám), `Đang thực hiện` (Xanh biển), `Nghiệm thu / Đánh giá` (Vàng cam), `Hoàn thành` (Xanh lá).
    + Sơ đồ các bước chuyển (Transitions): `Bắt đầu triển khai`, `Gửi nghiệm thu`, `Nghiệm thu đạt chuẩn`, `Yêu cầu chỉnh sửa`, `Hoàn tất trực tiếp`, `Mở lại công việc`.
  - **Tab "Board":**
    + Xem trước bảng Scrum với 4 cột: `CHUẨN BỊ`, `ĐANG TRIỂN KHAI`, `NGHIỆM THU`, `HOÀN THÀNH`.
  - **Tab "Sprint & Công việc" (Plan):**
    + Xem bảng 3 Sprint: `Giai đoạn 1` (20 ngày), `Giai đoạn 2` (22 ngày), `Giai đoạn 3` (18 ngày).
    + Cây phân cấp công việc mở/thu hiển thị đầy đủ 5 Epic và 21 Task/Subtask con.
  - **Tab "Phân quyền" (Roles):**
    + 3 vai trò: `ADMINISTRATOR`, `MEMBER`, `VIEWER` cùng bảng ma trận phân quyền chi tiết.
- [ ] **Bước 1.2.3:** Kiểm tra băng thông báo Toàn vẹn (Integrity Alert):
  - Băng hiển thị màu xanh lá: **"Bản thiết kế hợp lệ, sẵn sàng để khởi tạo dự án"** (0 Errors).
  - Nút **"Dùng mẫu này"** ở góc dưới hiển thị trạng thái khả dụng (Enabled), không bị disable.

---

## GIAI ĐOẠN 2: TẠO DỰ ÁN TỪ MẪU F&B (CLONE ENGINE & DATE OFFSET)

Mục tiêu: Thực hiện sao chép sâu (Deep Clone) mẫu `TPLFNB` sang dự án `TCP`, kiểm tra cơ chế dời mốc thời gian tự động (Date Offset) và xác nhận tính toàn vẹn độc lập của dữ liệu.

### Test Case 2.1: Nhập thông tin Khởi tạo Dự án từ Mẫu
- [ ] **Bước 2.1.1:** Tại modal Preview, bấm nút **"Dùng mẫu này"**. Hệ thống chuyển sang form cấu hình tạo dự án với mẫu đã chọn.
- [ ] **Bước 2.1.2:** Nhập các thông tin chi tiết:
  - **Tên dự án:** `Trà Chanh Phố 1983 - Không Gian Vỉa Hè Chill & Check-in`
  - **Mã dự án (Key):** `TCP`
  - **Nhóm triển khai (Team):** Chọn nhóm vận hành (hoặc để trống tùy phân quyền).
  - **Ngày bắt đầu (Start Date):** Chọn ngày `01/11/2026`.
  - **Hạn chót dự án (End Date):** Kiểm tra ô input — hệ thống tự động gợi ý `30/01/2027` (đúng 90 ngày sau ngày bắt đầu).
  - **Mô tả:** `Dự án setup điểm kinh doanh Trà Chanh Phố: cải tạo mặt tiền ngã tư vỉa hè, lắp đặt quầy bar dập nắp cốc, menu trà chanh giã tay & đồ ăn vặt, hoàn thiện ATTP và tổ chức tuần lễ khai trương.`
- [ ] **Bước 2.1.3:** Kiểm tra 3 tùy chọn sao chép (Clone Options):
  - [x] **Sao chép danh sách Sprint** (Mặc định: BẬT).
  - [x] **Sao chép công việc mẫu (Backlog & Sprint)** (Mặc định: BẬT).
  - [ ] **Sao chép phiên bản (Versions)** (Mặc định: TẮT).
- [ ] **Bước 2.1.4:** Bấm nút **"Tạo dự án từ mẫu"**.

### Test Case 2.2: Xác minh Cơ chế Dời mốc Thời gian (Date Offset Verification)
- [ ] **Kết quả mong đợi từ API & Database:**
  - API trả về mã `201 CREATED` với thông điệp: *"Dự án đã được tạo từ mẫu thành công"*.
  - Payload trả về `stats` chứa:
    + `statuses`: 4
    + `workItemTypes`: 3
    + `workflows`: 1
    + `transitions`: 7
    + `boards`: 1
    + `boardColumns`: 4
    + `sprints`: 3
    + `workItems`: 26
  - Toast thông báo thành công và màn hình tự động chuyển hướng vào dự án `/projects/TCP/boards` hoặc `/projects/TCP/backlog`.
- [ ] **Kiểm tra độ dời lịch của Sprint và Công việc:**
  - **Sprint 1 (Giai đoạn 1):** Bắt đầu `01/11/2026` → Kết thúc `21/11/2026` (20 ngày).
  - **Sprint 2 (Giai đoạn 2):** Bắt đầu `21/11/2026` → Kết thúc `13/12/2026` (22 ngày).
  - **Sprint 3 (Giai đoạn 3):** Bắt đầu `13/12/2026` → Kết thúc `31/12/2026` (18 ngày).
  - Tất cả các Sprint được sinh ra đều ở trạng thái **`FUTURE`** (Dự kiến), không tự ý kích hoạt.
  - Toàn bộ thời hạn hoàn thành (Due date) của 26 task đều được cộng số ngày offset tương ứng, không có task nào bị quá hạn so với ngày bắt đầu mới.

### Test Case 2.3: Xác minh Đặt lại Tiến độ & Tính Độc lập (Reset Progress & Isolation)
- [ ] **Bước 2.3.1:** Vào trang Backlog (`/projects/TCP/backlog`).
- [ ] **Bước 2.3.2:** Mở chi tiết công việc bất kỳ (ví dụ `TCP-2`):
  - **Mã công việc:** Đã đổi thành tiền tố mới `TCP-2` (thay vì `TPLFNB-2`).
  - **Trạng thái:** Tự động đặt về trạng thái khởi tạo `Cần làm` (TODO).
  - **Người được giao (Assignee):** Bị xóa trắng (Chưa phân công).
  - **Tiến độ thời gian:** `Thời gian đã dành (Time Spent) = 0`, `Thời gian còn lại (Remaining) = Ước lượng ban đầu (Original Estimate)`.
  - **Lịch sử thay đổi (Changelog):** Có dòng ghi vết hệ thống:
    > *"Đã tạo công việc TCP-2 từ TPLFNB-2 của mẫu Xây dựng Cửa hàng F&B (Nhà hàng - Quán Café)"*
- [ ] **Bước 2.3.3:** Kiểm tra tính độc lập: Mọi chỉnh sửa trên dự án `TCP` hoàn toàn không tác động đến mẫu `TPLFNB`.

---

## GIAI ĐOẠN 3: TÙY BIẾN CẤU HÌNH DỰ ÁN (CUSTOMIZING PROJECT SETTINGS)

Mục tiêu: Tùy biến bộ khung quản trị dự án "Trà Chanh Phố 1983" bằng cách bổ sung Loại công việc mới và tùy biến Quy trình nghiệp vụ F&B chuyên sâu.

> [!IMPORTANT]
> **Quy tắc an toàn dữ liệu:** Giữ nguyên vẹn 3 Loại công việc có sẵn từ mẫu (`Epic`, `Task`, `Subtask`) và các trạng thái gốc để bảo toàn liên kết cho 26 task mẫu đã clone. Trong giai đoạn này, ta **chỉ thêm mới (Add Only)** loại công việc và trạng thái/bước chuyển quy trình, tuyệt đối **không xóa** các phần tử kế thừa từ template!

```
                      BẢN ĐỒ TÙY BIẾN CẤU HÌNH DỰ ÁN TCP
┌────────────────────────────────────────────────────────────────────────┐
│                   DỰ ÁN: TRÀ CHANH PHỐ 1983 (TCP)                      │
├────────────────────────────────────────────────────────────────────────┤
│ 1. LOẠI CÔNG VIỆC (WORK ITEM TYPES)                                    │
│    • Giữ nguyên 3 loại mẫu: [Epic] (Level 1), [Task] (Level 0),        │
│      [Subtask] (Level -1) để bảo vệ 26 task có sẵn                     │
│    • THÊM MỚI: [Sự cố & Rủi ro] (Code: INCIDENT | Level 0 | #EF4444)   │
│    • Gán loại mới vào Workflow chung của dự án                         │
├────────────────────────────────────────────────────────────────────────┤
│ 2. QUY TRÌNH (WORKFLOW) & TRẠNG THÁI                                   │
│    • Cần làm ➔ Đang thực hiện ➔ [Thử vị & KCS Đồ uống] ➔               │
│      Nghiệm thu / Đánh giá ➔ Hoàn thành                                │
│    • Thiết lập 3 bước chuyển: [Gửi nếm thử vị], [Hương vị đạt chuẩn],  │
│      [Điều chỉnh lại công thức]                                        │
├────────────────────────────────────────────────────────────────────────┤
│ 3. CẤU HÌNH BẢNG (BOARD COLUMNS) & WIP LIMIT                           │
│    • Thêm cột mới: [THỬ VỊ / KCS] (Nằm giữa Đang làm & Nghiệm thu)     │
│    • Ánh xạ trạng thái [Thử vị & KCS Đồ uống] vào cột                  │
│    • Thiết lập WIP Limit Max = 3                                       │
└────────────────────────────────────────────────────────────────────────┘
```

### 3.1. Tùy biến & Bổ sung Loại Công Việc (Work Item Types Customization)

#### Test Case 3.1: Kiểm tra bảo toàn các Loại công việc có sẵn từ mẫu
- [ ] **Thao tác:** Vào mục **"Cấu hình Dự án"** -> Chuyển sang sub-tab **"Phân bổ Loại Công việc"** (`/workflow`).
- [ ] **Kiểm tra danh sách Loại công việc:**
  - `Epic`: Cấp 1 (Hạng mục lớn) - Icon `Zap` (#9333EA)
  - `Task`: Cấp 0 (Nhiệm vụ tiêu chuẩn) - Icon `CheckSquare` (#3B82F6)
  - `Subtask`: Cấp -1 (Nhiệm vụ con) - Icon `GitCommit` (#06B6D4)
- [ ] **Xác nhận quy tắc:** Giữ nguyên 3 loại này, **không xóa hoặc đổi tên** để đảm bảo 26 task mẫu không bị mất ánh xạ loại.

#### Test Case 3.2: Thêm mới Loại công việc "Sự cố & Rủi ro" (Level 0)
- [ ] **Thao tác:** Tại sub-tab **"Phân bổ Loại Công việc"**, bấm nút **"+ Thêm Loại Công Việc"**.
- [ ] **Dữ liệu nhập:**
  - **Tên loại công việc:** `Sự cố & Rủi ro`
  - **Mã loại (Code):** `INCIDENT`
  - **Cấp phân cấp (Hierarchy Level):** Chọn `Cấp 0 (Tiêu chuẩn)`
  - **Biểu tượng (Icon):** `AlertTriangle`
  - **Màu sắc:** Màu Đỏ Cảnh Báo `#EF4444`
  - **Quy trình áp dụng:** Gán vào `Quy trình Mở mới Cửa hàng F&B`
  - **Mô tả:** *Ghi nhận các sự cố đột xuất về điện nước, hư hỏng máy pha chế hoặc vấn đề an ninh trật tự vỉa hè trong quá trình setup quán.*
- [ ] **Kết quả mong đợi:**
  - Hệ thống lưu thành công loại công việc mới.
  - Danh sách hiển thị đủ 4 loại (`Epic`, `Task`, `Subtask`, `Sự cố & Rủi ro`).
  - Loại mới được gắn đúng vào Workflow của dự án mà không ảnh hưởng bất kỳ loại nào trước đó.

---

### 3.2. Tùy biến Quy trình (Workflow) & Thêm Trạng thái đặc thù F&B

Mô hình trà chanh đòi hỏi bước **Thử vị / Kiểm tra chất lượng (KCS)** trước khi đem ra nghiệm thu chính thức:

#### Test Case 3.3: Thêm Trạng thái mới "Thử vị & KCS Đồ uống"
- [ ] **Thao tác:** Chuyển sang sub-tab **"Sơ đồ Quy trình & Trạng thái"**.
- [ ] **Bấm nút "+ Thêm trạng thái":**
  - Tên trạng thái: `Thử vị & KCS Đồ uống`
  - Nhóm trạng thái (Category): `IN_PROGRESS` (Đang xử lý)
  - Màu sắc: Màu Cam San Hô `#FB923C`
  - Mô tả: *Đội ngũ pha chế blind-test độ đậm trà, độ chua ngọt của chanh tươi và thẩm định hương vị trước khi đưa vào thực đơn.*
- [ ] **Kết quả mong đợi:** Trạng thái mới được tạo thành công với danh mục `IN_PROGRESS` và xuất hiện trên sơ đồ.

#### Test Case 3.4: Thiết lập Bước chuyển Quy trình (Workflow Transitions)
- [ ] **Thao tác:** Nối các bước chuyển cho trạng thái mới trên sơ đồ:
  - Transition 1: `Đang thực hiện` ➔ `Thử vị & KCS Đồ uống` (Tên bước: *Gửi nếm thử vị*)
  - Transition 2: `Thử vị & KCS Đồ uống` ➔ `Nghiệm thu / Đánh giá` (Tên bước: *Hương vị đạt chuẩn*)
  - Transition 3: `Thử vị & KCS Đồ uống` ➔ `Đang thực hiện` (Tên bước: *Điều chỉnh lại công thức*)
- [ ] **Bấm lưu Workflow:** Nhấn **"Lưu thay đổi quy trình"**.
- [ ] **Kết quả mong đợi:** Hệ thống lưu thành công, đồ thị FSM hợp lệ, không báo lỗi cô lập trạng thái.

---

### 3.3. Cấu hình lại Bảng (Board Columns) & Thiết lập WIP Limit

#### Test Case 3.5: Bổ sung Cột "THỬ VỊ / KCS" và Ánh xạ Trạng thái
- [ ] **Thao tác:** Vào trang **"Bảng làm việc (Board)"** -> Bấm nút **"Cấu hình bảng (Board Settings)"** -> Chọn tab **"Cột (Columns)"**.
- [ ] **Thao tác thêm cột:**
  - Bấm **"+ Thêm cột mới"**.
  - Đặt tên cột: `THỬ VỊ / KCS`.
  - Vị trí: Kéo cột đặt vào vị trí thứ 3 (nằm giữa cột `ĐANG TRIỂN KHAI` và cột `NGHIỆM THU`).
  - Ánh xạ trạng thái: Kéo trạng thái `Thử vị & KCS Đồ uống` thả vào cột `THỬ VỊ / KCS`.
- [ ] **Cấu hình WIP Limit (Giới hạn công việc đang xử lý):**
  - Cột `THỬ VỊ / KCS`: Đặt `WIP Max = 3` (Quy định: Tại một thời điểm, quầy bar không nếm thử quá 3 món để tránh rối loạn vị giác của barista).
- [ ] **Bấm "Lưu cấu hình":** Quay lại màn hình Active Board.
- [ ] **Kết quả mong đợi:** Bảng Scrum hiển thị đủ 5 cột trực quan:
  `[1. CHUẨN BỊ] ➔ [2. ĐANG TRIỂN KHAI] ➔ [3. THỬ VỊ / KCS (Max 3)] ➔ [4. NGHIỆM THU] ➔ [5. HOÀN THÀNH]`.

---

## GIAI ĐOẠN 4: QUẢN LÝ & TINH CHỈNH CÔNG VIỆC (WORK ITEMS EXECUTION & ENRICHMENT)

Mục tiêu: Tận dụng 26 Task có sẵn từ mẫu, biên tập lại sát với thực tế mở quán trà chanh, đồng thời bổ sung các công việc mới cực kỳ đa dạng để thử nghiệm toàn diện hệ sinh thái Task.

```
            CÂY PHÂN CẤP CÔNG VIỆC DỰ ÁN TRÀ CHANH PHỐ 1983 (TCP)
            
   [EPIC 1] TCP-1: Khảo sát vị trí, Thuê mặt bằng vỉa hè & Pháp lý phố
   ├── [TASK] TCP-2: Khảo sát góc phố đắc địa, ngã tư & vỉa hè kê bàn ghế cóc (ĐÃ SỬA)
   │   ├── [SUBTASK] TCP-3: Đo đếm lượt giới trẻ & học sinh sinh viên qua lại (3 ngày)
   │   └── [SUBTASK] TCP-4: Khảo sát chỗ đỗ xe máy & điểm tựa an ninh trật tự
   ├── [TASK] TCP-5: Thương lượng hợp đồng thuê và đặt cọc mặt bằng
   └── [TASK] TCP-8: Nộp hồ sơ ĐKKD hộ cá thể & Giấy chứng nhận ATTP
   
   [EPIC 2] TCP-9: Thiết kế kiến trúc, Thi công Retro Phố Cổ & Quầy Bar Mở
   ├── [TASK] TCP-10: Thiết kế bản vẽ phối cảnh 3D phong cách Trà chanh Retro
   ├── [TASK] TCP-11: Cải tạo hệ thống cấp thoát nước công suất cao cho quầy bar
   └── [TASK] TCP-12: Sản xuất quầy pha chế Inox & bàn ghế cóc xếp dù (ĐÃ SỬA)
       ├── [SUBTASK-MỚI] TCP-29: Lắp đặt vòi xịt rửa ly tự động âm mặt đá quầy bar
       └── [SUBTASK-MỚI] TCP-30: Bố trí khay topping 6 ngăn giữ nhiệt có nắp đậy
       
   [EPIC 3] TCP-14: Xây dựng Menu Trà Chanh, Topping & Nhập Máy móc Chuyên dụng
   ├── [TASK] TCP-15: Chốt Menu Trà Chanh Giã Tay, Trà Mãng Cầu & Đồ Ăn Vặt (ĐÃ SỬA)
   ├── [TASK] TCP-16: Mua sắm máy dập nắp cốc tự động, bình ủ trà 12L & máy bào đá (ĐÃ SỬA)
   └── [TASK] TCP-17: Ký hợp đồng nhập chanh thơm Quảng Đông & trà lài đặc sản
   
   [EPIC 6 - MỚI] TCP-27: Xây dựng Kênh TikTok / Fanpage & Chiến dịch Check-in Phố
   ├── [TASK-MỚI] TCP-28: Minigame "Check-in Trà Chanh Phố - Tặng Bình Thủy Tinh"
   └── [TASK-RỦI RO] TCP-31: Xử lý sự cố sụt áp điện khi chạy cùng lúc 3 bình ủ trà
```

### 4.1. Tinh chỉnh các Task có sẵn từ Template F&B sang Quán Trà Chanh

#### Test Case 4.1: Chỉnh sửa Task Khảo sát mặt bằng (`TCP-2`)
- [ ] **Thao tác:** Mở Task `TCP-2` trong Backlog.
- [ ] **Dữ liệu cập nhật:**
  - **Tóm tắt (Summary):** `Khảo sát ngã tư góc phố đắc địa, mặt tiền thoáng & vỉa hè rộng kê bàn ghế cóc`
  - **Mô tả:** *Khảo sát bán kính 500m quanh các trường cấp 3, đại học và khu văn phòng trẻ. Kiểm tra độ rộng vỉa hè tối thiểu 4m cho phép kê 15 bộ bàn ghế cóc không cản trở lối đi bộ.*
- [ ] **Kết quả mong đợi:** Task được lưu thành công với nội dung thực tế khảo sát quán trà chanh.

#### Test Case 4.2: Chỉnh sửa Task Chốt Menu đồ uống (`TCP-15`)
- [ ] **Thao tác:** Mở Task `TCP-15`.
- [ ] **Dữ liệu cập nhật:**
  - **Tóm tắt:** `Chốt Menu Trà Chanh Giã Tay, Trà Mãng Cầu Đắk Lắk, Hướng Dương Vị Dừa & Khô Gà Lá Chanh`
  - **Mô tả:** *Chuẩn hóa công thức 5 món chủ lực (Signature): Trà chanh giã tay hoa nước hoa, Trà đào cam sả, Trà mãng cầu xiêm, Hướng dương dừa rang bùi béo và Nem chua rán phố cổ. Tính toán Food Cost mục tiêu dưới 25%.*
  - **Story Points:** `5.0`.
- [ ] **Kết quả mong đợi:** Dữ liệu task phản ánh chính xác nghiệp vụ món ăn quán trà chanh.

#### Test Case 4.3: Chỉnh sửa Task Mua sắm máy móc (`TCP-16`)
- [ ] **Thao tác:** Mở Task `TCP-16`.
- [ ] **Dữ liệu cập nhật:**
  - **Tóm tắt:** `Mua máy dập nắp cốc tự động, 3 bình ủ trà Inox 12L, máy ép chanh công suất cao & tủ đá 500L`
  - **Mô tả:** *Chọn mua máy dập nắp Fest tự động nhận diện cốc, 3 bình ủ trà giữ nhiệt ruột tráng bạc, máy làm đá viên mini 80kg/ngày và máy bào đá tuyết.*
  - **Ước lượng ban đầu:** `16 giờ` (57600s).
- [ ] **Kết quả mong đợi:** Cập nhật thành công.

---

### 4.2. Thêm mới Epic, Task Cấp 0 và Subtask Cấp -1 đa dạng

#### Test Case 4.4: Tạo mới Epic Cấp 1 (Marketing Phố & TikTok)
- [ ] **Thao tác:** Bấm nút **"+ Tạo công việc"** (Create Issue) trên thanh Header.
- [ ] **Dữ liệu nhập:**
  - Loại công việc: `Epic` (Level 1)
  - Tóm tắt: `Xây dựng Kênh TikTok / Fanpage & Chiến dịch Check-in Trà Chanh Phố`
  - Mô tả: *Chiến dịch phủ sóng hình ảnh quán trên TikTok với các clip giã chanh điệu nghệ, setup góc sống ảo đèn neon và chương trình tặng quà check-in.*
  - Độ ưu tiên: `HIGH`
- [ ] **Kết quả mong đợi:** Epic mới được tạo với mã **`TCP-27`** (kế tiếp sau mã 26 của template).

#### Test Case 4.5: Tạo mới Task Cấp 0 thuộc Epic mới
- [ ] **Thao tác:** Tạo công việc mới:
  - Loại công việc: `Task` (Level 0)
  - Thuộc Epic (Parent): Chọn `TCP-27 (Xây dựng Kênh TikTok...)`
  - Tóm tắt: `Tổ chức Minigame: "Check-in Trà Chanh Phố - Tặng Ngay Bình Thủy Tinh Giới Hạn"`
  - Mô tả: *Khách hàng chụp ảnh cùng ly trà chanh giã tay kèm hashtag #TraChanhPho1983 được tặng 01 bình thủy tinh quấn dây cói in logo quán (Chuẩn bị 200 bình).*
  - Sprint: Gán vào `Giai đoạn 3: Tuyển dụng, Đào tạo & Khai trương`
  - Ước lượng ban đầu: `24 giờ` (86400s)
- [ ] **Kết quả mong đợi:** Task được tạo thành công với mã **`TCP-28`**, liên kết cha-con đúng chuẩn với Epic `TCP-27`.

#### Test Case 4.6: Tạo mới các Subtask Cấp -1 cho Task Quầy Bar (`TCP-12`)
- [ ] **Thao tác:** Mở Task `TCP-12` (*Sản xuất và lắp đặt quầy bar chuyên dụng...*).
- [ ] **Bấm nút "+ Thêm công việc con" (Add Subtask):**
  - **Subtask 1:**
    + Tóm tắt: `Lắp đặt vòi xịt rửa ly tự động âm mặt đá quầy bar (Pitcher Rinser)`
    + Ước lượng: `4 giờ`
    + Độ ưu tiên: `MEDIUM`
    + Mã sinh ra: **`TCP-29`** (Level -1).
  - **Subtask 2:**
    + Tóm tắt: `Bố trí khay topping Inox 6 ngăn có khay giữ nhiệt lạnh và nắp đậy mica`
    + Ước lượng: `3 giờ`
    + Độ ưu tiên: `HIGH`
    + Mã sinh ra: **`TCP-30`** (Level -1).
- [ ] **Kết quả mong đợi:** Task `TCP-12` hiển thị danh sách 2 công việc con `TCP-29` và `TCP-30` ở trạng thái `Cần làm`.

---

### 4.3. Bổ sung Task dạng Sự cố/Rủi ro & Thiết lập Liên kết phụ thuộc (Issue Links)

Trong thực tế thi công quán luôn phát sinh rủi ro điện nước. Ta thêm một task đột xuất để test hệ thống liên kết:

#### Test Case 4.7: Tạo Task Sự cố bằng Loại công việc tùy biến ("Sự cố & Rủi ro")
- [ ] **Thao tác:** Bấm nút **"+ Tạo công việc"** (Create Issue) trên thanh Header:
  - Loại công việc: Chọn loại vừa tạo mới ở Giai đoạn 3: **`Sự cố & Rủi ro`** (Level 0)
  - Tóm tắt: `Sự cố: Đường điện quá tải khi bật đồng thời 3 bình ủ trà & máy dập cốc - Cần thay Aptomat 40A`
  - Độ ưu tiên: **`HIGHEST`** (Khẩn cấp)
  - Mô tả: *Thử tải quầy bar bị nhảy aptomat tổng. Cần gọi thợ điện kéo riêng 1 dây nguồn 4.0mm từ công tơ vào quầy pha chế.*
  - Mã sinh ra: **`TCP-31`**.
- [ ] **Kết quả mong đợi:**
  - Công việc được tạo thành công với loại `Sự cố & Rủi ro`.
  - Icon cảnh báo `AlertTriangle` màu đỏ hiển thị chính xác trên Backlog và Board.

#### Test Case 4.8: Thiết lập Ràng buộc Phụ thuộc (Issue Dependency Links)
- [ ] **Thao tác:** Mở Task `TCP-31` -> Bấm **"Thêm liên kết" (Link Issue)**:
  - Chọn mối quan hệ: **`blocks (Chặn)`**
  - Chọn công việc bị chặn: `TCP-22` (*Kiểm tra tay nghề thực tế Dry-run và pha chế thử*).
- [ ] **Thao tác tiếp theo:** Mở Task `TCP-7` (*Hợp đồng thuê mặt bằng*):
  - Chọn mối quan hệ: **`blocks (Chặn)`**
  - Chọn công việc bị chặn: `TCP-13` (*Thi công lắp đặt biển hiệu quảng cáo mặt tiền*).
- [ ] **Kết quả mong đợi:**
  - Trên giao diện chi tiết của `TCP-22` hiển thị rõ: *"is blocked by TCP-31"* kèm icon cảnh báo.
  - Sơ đồ phụ thuộc ghi nhận chính xác 2 mối liên kết.

---

## GIAI ĐOẠN 5: VẬN HÀNH SPRINT, ACTIVE BOARD & THỰC THI PHÂN CÔNG (EXECUTION FLOW)

Mục tiêu: Đưa dự án vào hoạt động thực tế — Bắt đầu Sprint, kéo thả Kanban Board theo quy trình mới, ghi log thời gian và kiểm tra các Validator nghiệp vụ.

```
                      LUỒNG KÉO THẢ THẺ TRÊN ACTIVE BOARD
┌──────────────┐      ┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│  CHUẨN BỊ    │ ───> │ĐANG TRIỂN KHAI│ ───> │ THỬ VỊ / KCS │ ───> │  HOÀN THÀNH  │
│  (TODO)      │      │(IN_PROGRESS) │      │(WIP Max = 3) │      │   (DONE)     │
└──────────────┘      └──────────────┘      └──────────────┘      └──────────────┘
       │                     │                     │                     ▲
       │                     └─────────────────────┼─────────────────────┤
       │                       (Các task bình thường: không cần thử vị)  │
       ▼                                                                 │
 [VALIDATOR CHẶN]                                                        │
 Không cho Task cha Done nếu Subtask chưa xong! ─────────────────────────┘
```

### 5.1. Kích hoạt Sprint 1 (`Start Sprint`)
- [ ] **Bước 5.1.1:** Vào trang **"Backlog"** (`/projects/TCP/backlog`).
- [ ] **Bước 5.1.2:** Tìm khối **"Sprint 1: Giai đoạn 1: Pháp lý, Mặt bằng & Thiết kế Concept"** (đang ở trạng thái `FUTURE`).
- [ ] **Bước 5.1.3:** Bấm nút **"Bắt đầu Sprint" (Start Sprint)**:
  - Hộp thoại Start Sprint xuất hiện.
  - Tên Sprint: Giữ nguyên hoặc tinh chỉnh.
  - Thời lượng: `3 tuần` (Khớp với 20 ngày đã tính từ offset).
  - Mục tiêu Sprint: *Chốt vị trí đắc địa vỉa hè ngã tư, ký hợp đồng thuê và nộp hồ sơ ATTP.*
  - Bấm **"Bắt đầu"**.
- [ ] **Kết quả mong đợi:**
  - Sprint 1 chuyển trạng thái sang **`ACTIVE`**.
  - Hệ thống tự động chuyển người dùng sang tab **"Bảng Active Board"**.

---

### 5.2. Phân công công việc & Kéo thả thẻ trên Active Board
- [ ] **Bước 5.2.1:** Trên Active Board, tại cột **"CHUẨN BỊ"**, tìm thẻ `TCP-2` (*Khảo sát ngã tư góc phố...*).
- [ ] **Bước 5.2.2:** Bấm vào thẻ để gán **Người được giao (Assignee)** cho Quản lý dự án.
- [ ] **Bước 5.2.3:** Dùng chuột kéo thẻ `TCP-2` từ cột **"CHUẨN BỊ"** thả sang cột **"ĐANG TRIỂN KHAI"**.
  - Trạng thái công việc chuyển sang: `Đang thực hiện`.
- [ ] **Bước 5.2.4:** Mở modal chi tiết của `TCP-2`, thực hiện **Ghi nhận thời gian làm việc (Log Work)**:
  - Số giờ đã làm: `1d` (8 giờ).
  - Ghi chú: *Đã đếm được 450 lượt bạn trẻ đi lại trong khung giờ 19h - 22h tối thứ 6.*
  - Bấm lưu log work.
- [ ] **Bước 5.2.5:** Kéo thẻ `TCP-2` sang cột **"NGHIỆM THU"**, sau đó kéo sang cột **"HOÀN THÀNH"**.
- [ ] **Kết quả mong đợi:** Thẻ `TCP-2` hiển thị gạch ngang tóm tắt, trạng thái chuyển thành `Hoàn thành` (DONE), ngày giải quyết (`resolved_at`) được ghi nhận.

---

### 5.3. Thử nghiệm Trạng thái `Thử vị / KCS` & Kiểm tra WIP Limit

#### Test Case 5.1: Chuyển thẻ vào cột THỬ VỊ / KCS
- [ ] **Thao tác:** Kéo task `TCP-15` (*Chốt Menu Trà Chanh...*) từ cột **"ĐANG TRIỂN KHAI"** thả vào cột **"THỬ VỊ / KCS"**.
- [ ] **Kết quả mong đợi:**
  - Bước chuyển quy trình thực thi thành công.
  - Cột `THỬ VỊ / KCS` hiển thị 1 thẻ, badge đếm số lượng hiển thị: `1/3`.

#### ⚠️ Test Case 5.2: Kiểm tra Cảnh báo Vượt giới hạn WIP Limit (Max 3)
- [ ] **Thao tác:** Lần lượt kéo thêm 3 task khác vào cột `THỬ VỊ / KCS` để số lượng thẻ trong cột đạt con số **4 thẻ**.
- [ ] **Kết quả mong đợi:**
  - Header của cột `THỬ VỊ / KCS` đổi sang màu đỏ cảnh báo (Warning Red) với thông báo: `4/3 - Vượt quá giới hạn WIP Limit!`
  - Hệ thống cảnh báo trực quan cho người quản lý rằng quầy bar đang bị quá tải trong khâu thẩm định món.

---

### 5.4. Kiểm thử Core Validator (`ALL_SUBTASKS_DONE`)

Một trong những quy tắc nghiệp vụ cốt lõi của hệ sinh thái Work Service là: **Không thể đóng Task cha nếu các Subtask con vẫn đang dang dở.**

- [ ] **Bước 5.4.1:** Tìm Task cha `TCP-12` (*Sản xuất quầy bar và bàn ghế...*). Hiện 2 Subtask con của nó (`TCP-29`, `TCP-30`) vẫn đang nằm ở trạng thái `Cần làm`.
- [ ] **Bước 5.4.2:** Thử dùng chuột kéo trực tiếp thẻ `TCP-12` từ cột `ĐANG TRIỂN KHAI` thả sang cột `HOÀN THÀNH`.
- [ ] **Kết quả mong đợi (Validator Guard Triggered):**
  - Hệ thống **chặn thao tác kéo thả** và trả thẻ về vị trí cũ (hoặc hiển thị modal cảnh báo lỗi).
  - Thông báo lỗi hiển thị rõ ràng:
    > *"Không thể hoàn thành công việc TCP-12. Tất cả các công việc con (Subtasks) phải đạt trạng thái HOÀN THÀNH trước khi đóng công việc cha."*
- [ ] **Bước 5.4.3 (Thực hiện tuần tự đúng chuẩn):**
  - Kéo `TCP-29` (Lắp vòi xịt rửa) sang `HOÀN THÀNH`.
  - Kéo `TCP-30` (Bố trí khay topping) sang `HOÀN THÀNH`.
  - Sau đó kéo lại Task cha `TCP-12` sang `HOÀN THÀNH`.
- [ ] **Kết quả mong đợi:** Lần này hệ thống cho phép hoàn thành `TCP-12` thành công!

---

### 5.5. Hoàn thành Sprint 1 & Chuyển giao Backlog (`Complete Sprint`)
- [ ] **Bước 5.5.1:** Giả lập toàn bộ các công việc trong Sprint 1 đã hoàn tất (hoặc còn sót 1 task).
- [ ] **Bước 5.5.2:** Tại Active Board, bấm nút **"Hoàn thành Sprint" (Complete Sprint)**.
- [ ] **Bước 5.5.3:** Modal tổng kết hiển thị:
  - Số công việc đã hoàn thành: X việc.
  - Số công việc chưa hoàn thành: Y việc.
  - Lựa chọn chuyển giao công việc dở dang: Chọn chuyển vào **"Sprint 2: Giai đoạn 2: Thi công nội thất, Máy móc & Menu"**.
  - Bấm **"Xác nhận hoàn thành"**.
- [ ] **Kết quả mong đợi:**
  - Sprint 1 chuyển sang trạng thái **`CLOSED`**.
  - Các task dở dang tự động di dời sang Sprint 2 trong Backlog.

---

## GIAI ĐOẠN 6: ĐÓNG GÓI NGƯỢC THÀNH MẪU CHUỖI NHƯỢNG QUYỀN (SAVE AS TEMPLATE)

Mục tiêu: Sau khi đã hoàn thiện toàn bộ công thức quán Trà Chanh Phố với Loại công việc mới (Sự cố & Rủi ro), Quy trình KCS đồ uống mở rộng, Bảng 5 cột và các Task thực tế, người dùng sử dụng tính năng **Lưu dự án thành mẫu** (`SAVE_AS_TEMPLATE`) để đóng gói thành một Blueprint mới dùng cho việc mở chuỗi nhượng quyền.

```
                             LUỒNG LƯU THÀNH MẪU
┌────────────────────────┐                          ┌────────────────────────┐
│ Dự án đang chạy:       │  ⋯ ➔ Save as Template   │ Mẫu Hệ thống mới:      │
│ TRÀ CHANH PHỐ 1983     │ ───────────────────────> │ MẪU CHUỖI TRÀ CHANH    │
│ (TCP - is_template=F)  │                          │ (TPLTCP - is_template=T│
└────────────────────────┘                          └────────────────────────┘
```

### Test Case 6.1: Gọi tính năng "Lưu thành Dự án mẫu"
- [ ] **Bước 6.1.1:** Trong trang tổng quan dự án `TCP`, mở menu tùy chọn dự án (**biểu tượng ⋯ ở góc phải trên**).
- [ ] **Bước 6.1.2:** Nhấp chọn mục: **"Lưu thành dự án mẫu" (Save as Template)**.
- [ ] **Bước 6.1.3:** Modal **"Lưu dự án thành mẫu mới"** xuất hiện.

### Test Case 6.2: Cấu hình Blueprint cho Mẫu Nhượng quyền
- [ ] **Bước 6.2.1:** Nhập thông tin cho mẫu mới:
  - **Tên mẫu:** `Mẫu Quy trình Mở Chuỗi Nhượng Quyền Trà Chanh Phố & Đồ Ăn Vặt`
  - **Mã dự án (Key):** `TPLTCP`
  - **Phân loại (Category):** Chọn `BUSINESS` (Kinh doanh)
  - **Mô tả:** *Bộ blueprint chuẩn hóa toàn diện từ mô hình Trà Chanh Phố 1983: bao gồm đầy đủ loại công việc Sự cố, quy trình KCS đồ uống, hướng dẫn setup vỉa hè chill, âm thanh acoustic và chiến dịch check-in TikTok.*
- [ ] **Bước 6.2.2:** Lựa chọn dữ liệu đóng gói:
  - [x] Bao gồm danh sách Sprint (3 Sprint chuẩn).
  - [x] Bao gồm toàn bộ công việc mẫu (Bao gồm cả các task mới tạo: TikTok, quầy bar, bãi xe, sự cố điện).
- [ ] **Bước 6.2.3:** Bấm nút **"Tạo dự án mẫu"**.

### Test Case 6.3: Xác minh Mẫu mới trong Thư viện
- [ ] **Kết quả mong đợi:**
  - API `POST /api/v1/work/projects/{projectId}/save-as-template` trả về `201 CREATED`.
  - Record mới trong `projects` có `is_template = TRUE`, `key = 'TPLTCP'`.
  - Record trong `project_clones` ghi nhận:
    + `source_project_id`: ID của dự án `TCP`.
    + `cloned_project_id`: ID của mẫu `TPLTCP`.
    + `clone_type`: `SAVE_AS_TEMPLATE`.
    + `date_offset_days`: `0`.
- [ ] **Kiểm tra UI Thư viện mẫu:**
  - Mở lại trang danh sách dự án -> Bấm **"+ Tạo dự án"** -> Tab **"Chọn mẫu dự án"**.
  - Thư viện mẫu lập tức hiển thị thẻ mẫu mới: **"Mẫu Quy trình Mở Chuỗi Nhượng Quyền Trà Chanh Phố"** với đầy đủ 4 loại công việc, quy trình KCS đồ uống và bảng 5 cột vừa tùy biến!

---

## MA TRẬN KIỂM THỬ & BẢNG NGHIỆM THU TOÀN DIỆN (ACCEPTANCE MATRIX)

Bảng tổng hợp đối chiếu kết quả kiểm thử toàn diện kịch bản Trà Chanh Phố:

| STT | Hạng mục Kiểm thử | Tiêu chí Nghiệm thu (Acceptance Criteria) | Kết quả Mong đợi | Trạng thái |
|:---:|---|---|---|:---:|
| **1** | **Template Gallery** | Lọc theo danh mục `BUSINESS` tìm thấy mẫu `TPLFNB` | Thẻ mẫu hiển thị đầy đủ stats, timeline 90 ngày | [ ] PASS |
| **2** | **Template Preview** | Xem chi tiết 5 tab và kiểm tra báo cáo toàn vẹn | Băng xanh lá, 0 Errors, nút "Dùng mẫu này" mở | [ ] PASS |
| **3** | **Clone Engine** | Sao chép sâu blueprint từ `TPLFNB` sang `TCP` | Sinh ra 4 statuses, 1 workflow, 3 sprints, 26 items | [ ] PASS |
| **4** | **Date Offset** | Dời lịch theo ngày bắt đầu mới `01/11/2026` | Sprint 1 bắt đầu đúng 01/11, due date dời chuẩn | [ ] PASS |
| **5** | **Progress Reset** | Tất cả task đưa về `Cần làm`, xóa assignee | Assignee rỗng, changelog ghi vết từ `TPLFNB` | [ ] PASS |
| **6** | **Work Item Types** | Thêm loại `Sự cố & Rủi ro` (Level 0), bảo toàn 3 loại mẫu | Danh sách 4 loại, không làm gãy 26 task có sẵn | [ ] PASS |
| **7** | **Workflow & Status** | Thêm trạng thái `Thử vị & KCS Đồ uống` và 3 transition | Workflow hợp lệ, không có trạng thái bị cô lập | [ ] PASS |
| **8** | **Board Columns** | Thêm cột `THỬ VỊ / KCS` với `WIP Limit = 3` | Hiển thị 5 cột, cảnh báo đỏ khi quá 3 task | [ ] PASS |
| **9** | **Task Modification** | Tinh chỉnh task `TCP-2`, `TCP-15`, `TCP-16` sát trà chanh | Nội dung thực tế, gán story points và costing | [ ] PASS |
| **10**| **Hierarchy Creation**| Tạo Epic mới (`TCP-27`), Task (`TCP-28`), Subtask (`TCP-29, 30`) | Đúng cây phân cấp 3 tầng: Level 1 ➔ 0 ➔ -1 | [ ] PASS |
| **11**| **Custom Type Usage** | Tạo task `TCP-31` bằng loại `Sự cố & Rủi ro` mới | Task lưu đúng loại mới, icon màu đỏ cảnh báo | [ ] PASS |
| **12**| **Issue Dependency** | Thiết lập liên kết `blocks` giữa `TCP-31` và `TCP-22` | Giao diện hiển thị cảnh báo phụ thuộc công việc | [ ] PASS |
| **13**| **Start Sprint** | Bắt đầu Sprint 1 với thời lượng 20 ngày | Chuyển sang `ACTIVE`, chuyển sang Active Board | [ ] PASS |
| **14**| **Board Drag & Drop** | Kéo thả thẻ mượt mà qua các cột, log work | Cập nhật trạng thái thời gian thực, lưu changelog | [ ] PASS |
| **15**| **Core Validator** | Chặn hoàn thành Task cha nếu Subtask chưa xong | Ném lỗi nghiệp vụ, bảo vệ tính toàn vẹn dữ liệu | [ ] PASS |
| **16**| **Complete Sprint** | Đóng Sprint 1 và chuyển task dở dang sang Sprint 2 | Sprint 1 `CLOSED`, backlog cập nhật chính xác | [ ] PASS |
| **17**| **Save As Template** | Lưu dự án `TCP` thành mẫu chuỗi `TPLTCP` | Mẫu mới xuất hiện trong thư viện, sẵn sàng nhân bản | [ ] PASS |

---

## 📌 HƯỚNG DẪN THỰC THI NHANH QUA CURL / API (DEV QUICK TEST)

Dành cho Tester / Developer muốn kiểm thử nhanh qua Postman hoặc terminal:

```bash
# 1. Xem trước bản thiết kế của Mẫu F&B (TPLFNB)
curl -X GET "http://localhost:8080/api/v1/work/templates/b1000000-0000-0000-0000-000000000001/preview" \
  -H "Authorization: Bearer $TOKEN"

# 2. Tạo dự án Trà Chanh Phố từ Mẫu F&B với Date Offset
curl -X POST "http://localhost:8080/api/v1/work/templates/b1000000-0000-0000-0000-000000000001/projects" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "workspaceId": "a0000000-0000-0000-0000-000000000001",
    "name": "Trà Chanh Phố 1983 - Không Gian Vỉa Hè Chill & Check-in",
    "key": "TCP",
    "description": "Setup điểm kinh doanh Trà Chanh Phố vỉa hè kết hợp check-in",
    "startDate": "2026-11-01",
    "includeSprints": true,
    "includeWorkItems": true,
    "includeVersions": false
  }'

# 3. Lưu dự án Trà Chanh Phố thành Mẫu Nhượng quyền mới
curl -X POST "http://localhost:8080/api/v1/work/projects/{PROJECT_TCP_ID}/save-as-template" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Mẫu Quy trình Mở Chuỗi Nhượng Quyền Trà Chanh Phố",
    "key": "TPLTCP",
    "templateCategory": "BUSINESS",
    "description": "Blueprint chuẩn hóa nhân chuỗi trà chanh vỉa hè kết hợp check-in",
    "includeSprints": true,
    "includeWorkItems": true,
    "includeVersions": true
  }'
```

---
*Tài liệu được biên soạn đồng bộ với kiến trúc `work-service` và giao diện người dùng DATN-2026.*
