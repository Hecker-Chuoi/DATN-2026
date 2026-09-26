# KỊCH BẢN KIỂM THỬ TOÀN DIỆN DỰ ÁN BLOG (END-TO-END TEST SCENARIO)
> **Dự án mẫu:** Nền tảng Website Blog Cá Nhân & Doanh Nghiệp (Blog Platform 2026)  
> **Dịch vụ kiểm thử:** `work-service` & `frontend` (DATN-2026)  
> **Thời gian tạo:** `12/09/2026 17:07:07 (GMT+7)`  
> **Phiên bản:** `v2.0` (Cập nhật Dynamic Project Filters, Phân nhóm cấp bậc Issue Types và Sửa lỗi Column Duplication)  
> **Trạng thái:** `Sẵn sàng thực thi (Ready for Testing)`  
> **Môi trường:** Local Development (`http://localhost:5173` | Spring Boot 3.x Java 21 | PostgreSQL 16)

---

## 📑 MỤC LỤC
1. [Thông tin Dự án & Thiết lập Dữ liệu Mẫu](#1-thông-tin-dự-án--thiết-lập-dữ-liệu-mẫu)
2. [Giai đoạn 1: Khởi tạo Dự án Mới](#giai-đoạn-1-khởi-tạo-dự-án-mới)
3. [Giai đoạn 2: Tùy biến Loại công việc & Phân cấp (Issue Types Scheme)](#giai-đoạn-2-tùy-biến-loại-công-việc--phân-cấp-issue-types-scheme)
4. [Giai đoạn 3: Tùy biến Trạng thái Dự án (Custom Statuses Scheme)](#giai-đoạn-3-tùy-biến-trạng-thái-dự-án-custom-statuses-scheme)
5. [Giai đoạn 4: Thiết lập Bước chuyển Quy trình (Workflow Transitions & Validators)](#giai-đoạn-4-thiết-lập-bước-chuyển-quy-trình-workflow-transitions--validators)
6. [Giai đoạn 5: Tạo Công việc Phân cấp 3 Tầng (3-Level Hierarchy)](#giai-đoạn-5-tạo-công-việc-phân-cấp-3-tầng-3-level-hierarchy)
7. [Giai đoạn 6: Kiểm thử Active Board & Bộ lọc Động theo Dự án (Dynamic Filters)](#giai-đoạn-6-kiểm-thử-active-board--bộ-lọc-động-theo-dự-án-dynamic-filters)
8. [Giai đoạn 7: Kiểm thử Bước chuyển & Kịch bản Ngoại lệ Core Validator (ALL_SUBTASKS_DONE)](#giai-đoạn-7-kiểm-thử-bước-chuyển--kịch-bản-ngoại-lệ-core-validator-all_subtasks_done)
9. [Giai đoạn 8: Kiểm thử Ràng buộc Toàn vẹn Dữ liệu trước khi Xóa (Integrity Guards)](#giai-đoạn-8-kiểm-thử-ràng-buộc-toàn-vẹn-dữ-liệu-trước-khi-xóa-integrity-guards)
10. [Giai đoạn 9: Kiểm thử Xóa Mềm Dự án (Soft Delete Project Guard)](#giai-đoạn-9-kiểm-thử-xóa-mềm-dự-án-soft-delete-project-guard)
11. [Bảng Tổng kết Nghiệm thu (Checklist)](#11-bảng-tổng-kết-nghiệm-thu-checklist)
12. [Lịch sử Cập nhật Tài liệu](#12-lịch-sử-cập-nhật-tài-liệu)

---

## 1. THÔNG TIN DỰ ÁN & THIẾT LẬP DỮ LIỆU MẪU

| Thuộc tính | Giá trị thiết lập | Mục đích kiểm thử |
|:---|:---|:---|
| **Tên dự án** | `Nền tảng Web Blog 2026` | Tên hiển thị toàn hệ thống |
| **Mã dự án (Key)** | `BLOG` | Tiền tố mã định danh công việc (`BLOG-1`, `BLOG-2`, ...) |
| **Mô hình dự án** | `Software Scrum` | Hỗ trợ quản lý Backlog, Sprint và Active Board |
| **Mô tả** | *Hệ thống Blog đa tác giả, hỗ trợ soạn thảo Markdown, quản lý bài viết và quy trình biên tập xuất bản.* | Thông tin tóm tắt |
| **Thời gian bắt đầu test** | `12/09/2026 17:07:07` | Ghi nhận mốc thời gian kiểm thử chuẩn xác |

---

## GIAI ĐOẠN 1: KHỞI TẠO DỰ ÁN MỚI

- [ ] **Bước 1.1:** Điều hướng tới màn hình danh sách dự án (`/projects`), bấm nút **"+ Tạo dự án"**.
- [ ] **Bước 1.2:** Điền thông tin dự án:
  - Tên dự án: `Nền tảng Web Blog 2026`
  - Mã dự án: `BLOG`
  - Kiểu dự án: `Scrum`
  - Mô tả: `Hệ thống Blog đa tác giả, hỗ trợ soạn thảo Markdown, quản lý bài viết và xuất bản.`
- [ ] **Bước 1.3:** Bấm nút **"Tạo dự án"**.
- [ ] **Kết quả mong đợi:**
  - Toast thông báo tạo dự án thành công.
  - Dự án tự động khởi tạo hệ sinh thái mặc định:
    + 3 loại công việc: `Epic` (Level 1), `Task` (Level 0), `Subtask` (Level -1).
    + 3 trạng thái mặc định: `To Do` (TODO), `In Progress` (IN_PROGRESS), `Done` (DONE).
    + Quy trình Workflow mặc định với các bước chuyển liên thông.
    + 1 bảng Active Board gắn liền với dự án.

---

## GIAI ĐOẠN 2: TÙY BIẾN LOẠI CÔNG VIỆC & PHÂN CẤP (ISSUE TYPES SCHEME)

Truy cập chi tiết dự án `BLOG` -> Chuyển sang tab **"Cấu hình Dự án (Config)"** -> Chọn subtab **"Loại công việc & Phân cấp (Issue Types Scheme)"**.

### Test Case 2.1: Thêm mới Loại công việc Cấp 0 (Standard Type)
- [ ] **Thao tác:** Bấm **"+ Tạo loại công việc"**.
- [ ] **Dữ liệu nhập:**
  - Cấp bậc: Chọn **Cấp bậc 0 (Công việc tiêu chuẩn / Standard)**.
  - Tên loại: `Bài viết (Post)`
  - Mã loại (Code): `POST`
  - Màu sắc: Chọn màu xanh lá tươi `#10B981`.
  - Biểu tượng (Icon): Chọn `Bookmark`.
  - Mô tả: *Bài viết nội dung blog chuẩn SEO.*
- [ ] **Bấm lưu:** Nhấn **"Tạo mới"**.
- [ ] **Kết quả mong đợi:** Loại `POST` xuất hiện ở Cấp bậc 0 trong danh sách cấu hình.

### Test Case 2.2: Thêm mới Loại công việc Cấp -1 (Subtask Type)
- [ ] **Thao tác:** Bấm **"+ Tạo loại công việc"**.
- [ ] **Dữ liệu nhập:**
  - Cấp bậc: Chọn **Cấp bậc -1 (Nhiệm vụ con / Subtask)**.
  - Tên loại: `Chi tiết kỹ thuật`
  - Mã loại: `TECH_SUB`
  - Màu sắc: Chọn màu tím `#9333EA`.
  - Biểu tượng: `GitCommit`.
  - Mô tả: *Các đầu việc kỹ thuật chuyên sâu cấu thành bài viết.*
- [ ] **Bấm lưu:** Nhấn **"Tạo mới"**.
- [ ] **Kết quả mong đợi:** Loại `TECH_SUB` xuất hiện ở Cấp bậc -1 trong danh sách.

### ⚠️ Test Ngoại lệ 2.3: Thử xóa loại công việc duy nhất của một cấp bậc (Quy tắc bảo toàn bất biến)
- [ ] **Thao tác:** Ở Cấp bậc 1 (Epic), hiện chỉ có duy nhất loại `Epic`. Bấm icon **Thùng rác (Xóa)** ở loại `Epic`.
- [ ] **Kết quả mong đợi:** Hệ thống **chặn xóa** và hiển thị thông báo lỗi rõ ràng:
  > *"Không thể xóa loại công việc này. Dự án luôn phải duy trì tối thiểu 1 loại công việc cho Level 1 (Epic)"*

---

## GIAI ĐOẠN 3: TÙY BIẾN TRẠNG THÁI DỰ ÁN (CUSTOM STATUSES SCHEME)

Chuyển sang subtab **"Sơ đồ Quy trình & Trạng thái (Workflow Diagram)"**.

### Test Case 3.1: Thêm trạng thái "Chờ duyệt bài" (In Review)
- [ ] **Thao tác:** Bấm nút **"+ Thêm trạng thái"**.
- [ ] **Dữ liệu nhập:**
  - Tên trạng thái: `Chờ duyệt bài`
  - Danh mục (Category): Chọn **Đang thực hiện (IN_PROGRESS)**.
  - Màu sắc: Chọn màu vàng cam `#F59E0B`.
  - Mô tả: *Bài viết đã hoàn thành bản thảo và đang chờ Biên tập viên kiểm duyệt.*
- [ ] **Bấm lưu:** Nhấn **"Thêm mới"**.
- [ ] **Kết quả mong đợi:** Trạng thái `Chờ duyệt bài` xuất hiện trong sơ đồ ở phân nhóm IN_PROGRESS.

### ⚠️ Test Ngoại lệ 3.2: Thử đổi danh mục của trạng thái duy nhất
- [ ] **Thao tác:** Tìm trạng thái `Done` (hiện là trạng thái duy nhất thuộc nhóm DONE). Bấm icon **Chỉnh sửa**.
- [ ] **Thử đổi:** Đổi danh mục từ `DONE` sang `TODO` -> Bấm Lưu.
- [ ] **Kết quả mong đợi:** Hệ thống **chặn cập nhật** và báo lỗi:
  > *"Không thể đổi danh mục của trạng thái này vì đây là trạng thái duy nhất thuộc danh mục DONE. Mỗi dự án bắt buộc phải duy trì tối thiểu 1 trạng thái cho từng danh mục (TODO, IN_PROGRESS, DONE)!"*

### ⚠️ Test Ngoại lệ 3.3: Thử tạo trùng tên trạng thái
- [ ] **Thao tác:** Bấm **"+ Thêm trạng thái"** -> Nhập tên `To Do` (đã có sẵn) -> Bấm Lưu.
- [ ] **Kết quả mong đợi:** Hệ thống báo lỗi: *"Trạng thái với tên 'To Do' đã tồn tại trong dự án này"*.

---

## GIAI ĐOẠN 4: THIẾT LẬP BƯỚC CHUYỂN QUY TRÌNH (WORKFLOW TRANSITIONS & VALIDATORS)

### Test Case 4.1: Thêm bước chuyển "Gửi duyệt bài" (In Progress -> Chờ duyệt bài)
- [ ] **Thao tác:** Bấm **"+ Thêm bước chuyển"**.
- [ ] **Dữ liệu nhập:**
  - Trạng thái bắt đầu: `In Progress`
  - Trạng thái đích: `Chờ duyệt bài`
  - Tên bước chuyển: `Gửi duyệt bài`
- [ ] **Bấm lưu:** Nhấn **"Lưu bước chuyển"**.
- [ ] **Kết quả mong đợi:** Bước chuyển `Gửi duyệt bài` xuất hiện trong danh sách các bước chuyển hợp lệ.

### Test Case 4.2: Thêm bước chuyển "Xuất bản bài viết" với Validator ALL_SUBTASKS_DONE (Chờ duyệt bài -> Done)
- [ ] **Thao tác:** Bấm **"+ Thêm bước chuyển"**.
- [ ] **Dữ liệu nhập:**
  - Trạng thái bắt đầu: `Chờ duyệt bài`
  - Trạng thái đích: `Done`
  - Tên bước chuyển: `Xuất bản bài viết`
  - **Tích chọn điều kiện ràng buộc:**
    ☑️ **"Bắt buộc hoàn thành tất cả Sub-tasks (All Subtasks Done)"** *(Chặn chuyển nếu còn nhiệm vụ con chưa xong)*.
- [ ] **Bấm lưu:** Nhấn **"Lưu bước chuyển"**.
- [ ] **Kết quả mong đợi:** Bước chuyển `Xuất bản bài viết` được lưu thành công kèm huy hiệu điều kiện `All Subtasks Done`.

### ⚠️ Test Ngoại lệ 4.3: Thử tạo trùng đường chuyển đã có
- [ ] **Thao tác:** Bấm **"+ Thêm bước chuyển"** -> Chọn Từ: `In Progress`, Đích: `Chờ duyệt bài`, Tên: `Gửi duyệt bài lần 2`.
- [ ] **Kết quả mong đợi:** Báo lỗi: *"Đã tồn tại bước chuyển giữa hai trạng thái này trong quy trình"*.

---

## GIAI ĐOẠN 5: TẠO CÔNG VIỆC PHÂN CẤP 3 TẦNG (3-LEVEL HIERARCHY)

Chuyển sang tab **"Active Board"** hoặc bấm nút **"Tạo công việc"** trên thanh tiêu đề.

### Test Case 5.1: Giao diện Modal tạo công việc hiển thị rõ cấp độ
- [ ] **Thao tác:** Bấm **"+ Tạo công việc"**.
- [ ] **Kiểm tra giao diện:**
  - Vùng chọn **Loại công việc (Issue Type)** hiển thị rõ ràng 3 phân nhóm:
    1. *Cấp 1 • Hạng mục lớn (Epic)*: Nút `Epic` có huy hiệu tím `Cấp 1`.
    2. *Cấp 0 • Tiêu chuẩn (Standard)*: Nút `Task`, `Bài viết (Post)` có huy hiệu xanh `Cấp 0`.
    3. *Cấp -1 • Nhiệm vụ con (Subtask)*: Nút `Subtask`, `Chi tiết kỹ thuật` có huy hiệu xanh da trời `Cấp -1`.
  - Có banner giải thích quy tắc phân cấp tương ứng với loại đang chọn.

### Test Case 5.2: Tạo Epic (Level 1)
- [ ] **Dữ liệu nhập:**
  - Loại công việc: Chọn **Epic** (Cấp 1).
  - Tiêu đề: `Hệ thống Soạn thảo & Xuất bản Blog`
  - Công việc cha: Trường này tự động **bị khóa (disabled)** vì Epic là cấp cao nhất.
  - Điểm câu chuyện (Story Points): `13`
- [ ] **Bấm tạo:** Nhấn **"Tạo mới"**.
- [ ] **Kết quả mong đợi:** Tạo thành công công việc `BLOG-1` (Epic).

### Test Case 5.3: Tạo Bài viết (Level 0 - Gắn vào Epic)
- [ ] **Dữ liệu nhập:**
  - Loại công việc: Chọn **Bài viết (Post)** (Cấp 0).
  - Tiêu đề: `Xây dựng Trình soạn thảo Markdown cho Tác giả`
  - Công việc cha (Parent Issue): Chọn `[BLOG-1] Hệ thống Soạn thảo & Xuất bản Blog`.
  - Hạn chót (Due Date): Chọn ngày cuối tháng.
  - Story Points: `5`
- [ ] **Bấm tạo:** Nhấn **"Tạo mới"**.
- [ ] **Kết quả mong đợi:** Tạo thành công `BLOG-2` có liên kết cha là `BLOG-1`.

### Test Case 5.4: Tạo Subtasks (Level -1 - Gắn vào Task Level 0)
- [ ] **Thao tác:** Bấm vào thẻ `BLOG-2` trên Board để mở modal chi tiết.
- [ ] **Tạo Subtask 1:**
  - Bấm **"+ Thêm nhiệm vụ con"**.
  - Tiêu đề: `Tích hợp thư viện Markdown Parser & Preview`
  - Bấm **"Tạo"** -> Tạo thành công `BLOG-3`.
- [ ] **Tạo Subtask 2:**
  - Bấm **"+ Thêm nhiệm vụ con"**.
  - Tiêu đề: `API Upload ảnh minh họa bài viết lên S3/Cloudinary`
  - Bấm **"Tạo"** -> Tạo thành công `BLOG-4`.
- [ ] **Kết quả mong đợi:** `BLOG-2` sở hữu 2 nhiệm vụ con là `BLOG-3` và `BLOG-4`, trạng thái ban đầu đều là `To Do`.

### ⚠️ Test Ngoại lệ 5.5: Chặn liên kết cha - con vi phạm phân tầng
- [ ] **Thao tác:** Thử tạo công việc loại `Subtask` nhưng không chọn công việc cha (hoặc chọn cha là một Subtask khác).
- [ ] **Kết quả mong đợi:** Hệ thống báo lỗi hợp lệ hóa: *"Công việc con (Sub-task) bắt buộc phải gắn với một công việc tiêu chuẩn (Task / Story / Post) làm cha!"*.

---

## GIAI ĐOẠN 6: KIỂM THỬ ACTIVE BOARD & BỘ LỌC ĐỘNG THEO DỰ ÁN (DYNAMIC FILTERS)

Quan sát màn hình **Active Board** của dự án `BLOG`:

### Test Case 6.1: Bộ lọc Loại công việc động theo Dự án (Type Filter Pills)
- [ ] **Kiểm tra thanh bộ lọc:**
  - Thanh bộ lọc **không hiển thị danh sách cố định chung chung**, mà tự động tải danh sách của dự án `BLOG`:
    `Tất cả` | `Epic` | `Task` | `Subtask` | `Bài viết (Post)` | `Chi tiết kỹ thuật`.
  - Mỗi loại công việc hiển thị đúng mã màu và biểu tượng tùy chỉnh đã cấu hình ở Giai đoạn 2.
- [ ] **Thao tác lọc:** Bấm vào viên thuốc `Bài viết (Post)` -> Chỉ có thẻ `BLOG-2` hiển thị trên bảng.
- [ ] **Bấm lại hoặc bấm "Xóa lọc":** Toàn bộ thẻ hiển thị trở lại.

### Test Case 6.2: Bộ lọc Trạng thái động theo Dự án (Status Dropdown Filter)
- [ ] **Kiểm tra Dropdown Trạng thái:**
  - Mở dropdown **"Trạng thái"** trên toolbar.
  - Danh sách hiển thị đúng các trạng thái của dự án `BLOG`:
    + `To Do`
    + `Chờ duyệt bài`
    + `In Progress`
    + `Done`
  - Có số lượng công việc (card count) hiển thị kèm theo từng trạng thái.
- [ ] **Thao tác lọc:** Chọn `Chờ duyệt bài` -> Bảng chỉ giữ lại các cột/công việc thỏa mãn.

### Test Case 6.3: Kiểm tra không bị nhân bản thẻ giữa các cột cùng danh mục (No Duplicate Cards)
- [ ] **Kiểm tra các cột trạng thái:**
  - Cột `CHỜ DUYỆT BÀI` và cột `IN PROGRESS` cùng thuộc danh mục `IN_PROGRESS`.
  - **Kết quả mong đợi:** Thẻ công việc chỉ xuất hiện duy nhất ở đúng cột trạng thái thực tế của nó (`statusId`), **hoàn toàn không bị nhân đôi** sang cột khác cùng category.

---

## GIAI ĐOẠN 7: KIỂM THỬ BƯỚC CHUYỂN & KỊCH BẢN NGOẠI LỆ CORE VALIDATOR (ALL_SUBTASKS_DONE)

Đây là kịch bản cốt lõi chứng minh tính năng kiểm soát bước chuyển của hệ thống!

### Kịch bản 7.1: Di chuyển Task cha sang "Chờ duyệt bài"
- [ ] **Thao tác:** Mở modal chi tiết `BLOG-2`.
- [ ] Tại dropdown trạng thái ở thanh bên phải, chọn bước chuyển sang `In Progress`.
- [ ] Tiếp tục chọn bước chuyển **"Gửi duyệt bài"** (đã cấu hình ở Test Case 4.1).
- [ ] **Kết quả mong đợi:** `BLOG-2` chuyển trạng thái thành công sang `Chờ duyệt bài`.

### ❌ Kịch bản 7.2: TEST NGOẠI LỆ "ALL_SUBTASKS_DONE" (BẮT BUỘC CHẶN)
- [ ] **Hiện trạng công việc:**
  - Công việc cha `BLOG-2` đang ở trạng thái `Chờ duyệt bài`.
  - Hai subtasks `BLOG-3` và `BLOG-4` vẫn đang ở trạng thái `To Do` (chưa hoàn thành).
- [ ] **Thao tác:**
  - Tại modal chi tiết của `BLOG-2`, bấm vào bước chuyển **"Xuất bản bài viết"** (hoặc kéo thả thẻ `BLOG-2` trên Active Board vào cột `Done`).
- [ ] **Kết quả mong đợi (BẮT BUỘC BỊ CHẶN):**
  - Hệ thống **từ chối thực hiện chuyển trạng thái**.
  - Hiển thị thông báo Toast cảnh báo lỗi rõ ràng:
    > *"Không thể chuyển trạng thái vì vẫn còn 2 công việc con (Sub-tasks) chưa hoàn thành!"*
  - `BLOG-2` giữ nguyên trạng thái `Chờ duyệt bài`, không bị nhảy sang `Done`.

### Kịch bản 7.3: Hỗ trợ Giải quyết Đồng loạt Subtasks (Resolve Subtasks Modal)
- [ ] **Thao tác:**
  - Khi người dùng cố gắng chuyển một task sang Done mà còn subtask chưa xong, hệ thống hiển thị modal **"Giải quyết nhiệm vụ con" (Resolve Subtasks Modal)**.
  - Modal liệt kê danh sách các subtask chưa hoàn thành (`BLOG-3`, `BLOG-4`).
  - Cung cấp tùy chọn: *"Đóng đồng loạt tất cả nhiệm vụ con và hoàn thành công việc cha"*.
- [ ] **Nếu chọn Đóng đồng loạt:**
  - Hệ thống tự động chuyển `BLOG-3`, `BLOG-4` sang `Done`, sau đó chuyển tiếp `BLOG-2` sang `Done`.
  - Cả 3 công việc đều cập nhật sang `Done` đồng bộ.

### Kịch bản 7.4: Hoàn thành lần lượt từng Subtask thủ công
- [ ] **Thao tác thủ công:**
  1. Mở subtask `BLOG-3` -> Chuyển trạng thái sang `Done`.
  2. Mở lại `BLOG-2` -> Bấm thử bước chuyển **"Xuất bản bài viết"** -> Hệ thống tiếp tục chặn và báo còn 1 công việc con (`BLOG-4`) chưa hoàn thành.
  3. Mở tiếp `BLOG-4` -> Chuyển trạng thái sang `Done`.
  4. Trở lại `BLOG-2` -> Bấm **"Xuất bản bài viết"**.
- [ ] **Kết quả mong đợi:**
  - Chuyển trạng thái thành công! `BLOG-2` chuyển sang `Done` mượt mà.
  - Thanh tiến độ hiển thị `100% hoàn thành (2/2 subtasks Done)`.

---

## GIAI ĐOẠN 8: KIỂM THỬ RÀNG BUỘC TOÀN VẸN DỮ LIỆU TRƯỚC KHI XÓA (INTEGRITY GUARDS)

Vào lại tab **"Cấu hình Dự án (Config)"**:

### ⚠️ Test Ngoại lệ 8.1: Thử xóa Loại công việc đang có dữ liệu
- [ ] **Thao tác:** Tại subtab **"Loại công việc & Phân cấp"**, tìm loại `Bài viết (Post)` (loại này đang được gán cho `BLOG-2`).
- [ ] Bấm nút **Thùng rác** để yêu cầu xóa.
- [ ] **Kết quả mong đợi:** Hệ thống từ chối xóa và hiển thị cảnh báo:
  > *"Không thể xóa loại công việc 'Bài viết (Post)' vì đang có công việc đang sử dụng. Vui lòng chuyển đổi các công việc sang loại khác trước khi xóa."*

### ⚠️ Test Ngoại lệ 8.2: Thử xóa Trạng thái đang có công việc áp dụng
- [ ] **Thao tác:** Tại subtab **"Sơ đồ Quy trình & Trạng thái"**, tìm trạng thái `Done` (đang chứa các công việc `BLOG-2`, `BLOG-3`, `BLOG-4`).
- [ ] Bấm icon **Xóa** ở trạng thái `Done`.
- [ ] **Kết quả mong đợi:** Hệ thống từ chối xóa và hiển thị cảnh báo:
  > *"Không thể xóa trạng thái 'Done' vì đang có công việc đang áp dụng trạng thái này!"*

---

## GIAI ĐOẠN 9: KIỂM THỬ XÓA MỀM DỰ ÁN (SOFT DELETE PROJECT GUARD)

### Test Case 9.1: Modal xác nhận nhập chính xác Mã dự án (Key Guard)
- [ ] **Thao tác:**
  - Tại trang chi tiết dự án `BLOG`, bấm vào nút menu ba chấm **"..."** trên góc trên bên phải.
  - Chọn **"Xóa dự án (Thùng rác)"**.
- [ ] **Giao diện Modal:**
  - Hiển thị cảnh báo rủi ro cao màu đỏ.
  - Yêu cầu người dùng: *"Để xác nhận, vui lòng nhập mã dự án BLOG"*.
  - Nút **"Xác nhận xóa dự án"** mặc định bị vô hiệu hóa (disabled).
- [ ] **Kiểm tra nhập sai:**
  - Nhập `BL` hoặc `TEST` -> Nút xóa vẫn bị disabled, có thông báo đỏ *"Mã xác nhận chưa chính xác"*.
- [ ] **Kiểm tra nhập đúng:**
  - Nhập chính xác `BLOG` -> Nút **"Xác nhận xóa dự án"** sáng đỏ cho phép bấm.
- [ ] **Bấm xóa:**
  - Hiển thị spinner và trạng thái *"Đang xóa dự án..."*.
  - Toast thông báo: *"Dự án 'Nền tảng Web Blog 2026' đã được chuyển vào thùng rác!"*.
  - Tự động điều hướng người dùng về trang `/projects`.
  - Dự án `BLOG` không còn xuất hiện trong danh sách dự án hoạt động chính.

---

## 11. BẢNG TỔNG KẾT NGHIỆM THU (CHECKLIST)

| STT | Kịch bản kiểm thử | Hành vi mong đợi chuẩn | Kết quả (Pass / Fail) | Ghi chú |
|:---:|:---|:---|:---:|:---|
| **1** | Tạo dự án `BLOG` | Sinh đủ 3 cấp bậc loại công việc & 3 trạng thái chuẩn | `[ ]` | Khởi tạo sinh thái mặc định |
| **2** | Thêm loại công việc `POST` (Cấp 0) & `TECH_SUB` (Cấp -1) | Lưu thành công, hiển thị đúng màu và icon | `[ ]` | Tùy biến loại công việc |
| **3** | Chặn xóa loại công việc duy nhất của 1 cấp bậc | Báo lỗi quy tắc bất biến tối thiểu 1 loại/level | `[ ]` | Exception Guard |
| **4** | Thêm trạng thái `Chờ duyệt bài` (Category: IN_PROGRESS) | Thêm thành công, hiển thị trên sơ đồ workflow | `[ ]` | Custom Status |
| **5** | Chặn đổi danh mục trạng thái duy nhất (`Done`) | Báo lỗi quy tắc bất biến danh mục trạng thái | `[ ]` | Exception Guard |
| **6** | Thêm bước chuyển có điều kiện `ALL_SUBTASKS_DONE` | Lưu thành công metadata validator JSON | `[ ]` | Workflow Rule |
| **7** | Chặn tạo trùng bước chuyển giữa 2 trạng thái | Báo lỗi trùng lặp đường chuyển | `[ ]` | Validation Guard |
| **8** | Tạo Epic `BLOG-1` | Trường công việc cha bị vô hiệu hóa | `[ ]` | Phân tầng Cấp 1 |
| **9** | Tạo Bài viết `BLOG-2` gắn cha `BLOG-1` | Thiết lập quan hệ cha - con thành công | `[ ]` | Phân tầng Cấp 0 |
| **10** | Tạo 2 Subtasks `BLOG-3`, `BLOG-4` | Gắn vào `BLOG-2` thành công | `[ ]` | Phân tầng Cấp -1 |
| **11** | Bộ lọc Loại công việc động trên Active Board | Hiển thị đúng các loại của dự án Blog | `[ ]` | Dynamic Type Filter |
| **12** | Bộ lọc Trạng thái động trên Active Board | Hiển thị đúng các status của dự án kèm đếm thẻ | `[ ]` | Dynamic Status Filter |
| **13** | Không nhân bản thẻ giữa `CHỜ DUYỆT BÀI` và `IN PROGRESS` | Mỗi thẻ chỉ nằm ở 1 cột duy nhất | `[ ]` | Column Matching Fix |
| **14** | **Chuyển Task sang Done khi còn Subtask chưa xong** | **BẮT BUỘC CHẶN VÀ BÁO LỖI RÕ RÀNG** | `[ ]` | **Core Validator Test** |
| **15** | Modal Giải quyết đồng loạt Subtask khi Done | Đóng hàng loạt subtask và hoàn thành cha | `[ ]` | Bulk Transition UX |
| **16** | Chuyển Task sang Done khi tất cả Subtask đã xong | Chuyển trạng thái thành công | `[ ]` | Valid Transition |
| **17** | Chặn xóa Loại công việc hoặc Trạng thái đang có dữ liệu | Báo lỗi toàn vẹn và số lượng task đang gắn | `[ ]` | Data Integrity Guard |
| **18** | Xóa mềm dự án có xác nhận mã Key | Bắt buộc nhập đúng `BLOG` mới xóa | `[ ]` | Soft Delete Guard |

---

## 12. LỊCH SỬ CẬP NHẬT TÀI LIỆU

| Phiên bản | Thời gian ghi nhận | Tác giả / Người thực hiện | Nội dung thay đổi |
|:---:|:---|:---|:---|
| **v1.0** | `12/09/2026 16:27:13` | DATN-2026 Team | Khởi tạo kịch bản kiểm thử dự án Blog (Scrum, Custom Type, Status, Validator). |
| **v2.0** | `12/09/2026 17:07:07` | DATN-2026 Team | Bổ sung kiểm thử hiển thị phân cấp Issue Types trên Modal Tạo, Bộ lọc động theo dự án trên Active Board, khắc phục lỗi Duplicate Cards giữa các cột cùng danh mục trạng thái, bổ sung checklist chi tiết. |
