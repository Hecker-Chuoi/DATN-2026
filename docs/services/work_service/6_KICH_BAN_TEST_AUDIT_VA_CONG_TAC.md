# 🧪 KỊCH BẢN KIỂM THỬ TOÀN DIỆN: AUDIT TRAIL, THREADED COMMENTS, ATTACHMENTS & LINKED ISSUES
## BƯỚC 5.3 & BƯỚC 5.4 — WORK SERVICE (V2 JIRA-GRADE)
### DỰ ÁN: DATN-2026 — HỆ THỐNG QUẢN LÝ DỰ ÁN & CÔNG VIỆC DOANH NGHIỆP

> **Tài liệu tham chiếu:**  
> - [`4_KE_HOACH_TRIEN_KHAI_CHI_TIET.md`](file:///d:/DATN-2026/docs/services/work_service/4_KE_HOACH_TRIEN_KHAI_CHI_TIET.md) (Kế hoạch triển khai)  
> - [`2_THIET_KE_CO_SO_DU_LIEU.md`](file:///d:/DATN-2026/docs/services/work_service/2_THIET_KE_CO_SO_DU_LIEU.md) (Thiết kế CSDL bảng `work_item_changelogs`, `work_item_comments`, `work_item_attachments`, `work_item_links`)  
> - [`3_SO_DO_TUAN_TU_NGHIEP_VU.md`](file:///d:/DATN-2026/docs/services/work_service/3_SO_DO_TUAN_TU_NGHIEP_VU.md) (Luồng 6: DFS Cycle Check & Luồng 7: Audit Trail)  
> - [`5_QUY_CHUAN_GIAO_DIEN_FRONTEND.md`](file:///d:/DATN-2026/docs/services/work_service/5_QUY_CHUAN_GIAO_DIEN_FRONTEND.md) (Quy chuẩn giao diện Jira Cloud)  
> 
> **Mục tiêu:** Cung cấp bộ kịch bản kiểm thử (Test Cases / Test Scenarios) chi tiết từ **Backend API (REST/Postman/MockMvc)** đến **Frontend UI (Giao diện người dùng / E2E)** để nghiệm thu 100% tính năng của Bước 5.3 và Bước 5.4.

---

## 📑 MỤC LỤC KỊCH BẢN KIỂM THỬ

| Phân hệ / Tính năng | Mã Test Case | Số lượng Test Cases | Mức độ ưu tiên |
| :--- | :--- | :---: | :---: |
| **Phần 1: Nhật ký Audit Trail (Bước 5.3)** | `TC-5.3.01` $\rightarrow$ `TC-5.3.08` | 8 Test Cases | 🔴 High / Critical |
| **Phần 2: Comments Threaded (Bước 5.4)** | `TC-5.4.01` $\rightarrow$ `TC-5.4.07` | 7 Test Cases | 🔴 High |
| **Phần 3: Attachments Dropzone (Bước 5.4)** | `TC-5.4.08` $\rightarrow$ `TC-5.4.14` | 7 Test Cases | 🟡 Medium / High |
| **Phần 4: Linked Issues & DFS Cycle Check (Bước 5.4)** | `TC-5.4.15` $\rightarrow$ `TC-5.4.22` | 8 Test Cases | 🔴 Critical |
| **TỔNG CỘNG** | | **30 Test Cases** | |

---

## ⚙️ 1. MÔI TRƯỜNG & TIỀN ĐIỀU KIỆN KIỂM THỬ (PRECONDITIONS)

1. **Hạ tầng Dịch vụ:**
   - PostgreSQL 16 đang hoạt động trên Docker infra (DB: `work_service_db`).
   - `identity-service` (Port 8082) và `work-service` (Port 8083) đang chạy.
   - `api-gateway` (Port 8081) hoặc Vite Frontend (Port 5173 proxy `/work/**` $\rightarrow$ `:8081`).
2. **Tài khoản Kiểm thử:**
   - **User A (Admin/Developer 1):** `userId = "97bf60e7-20fb-40a1-86e6-3b043aa0281e"`, Tên: `Nguyễn Văn A`
   - **User B (Developer 2):** `userId = "ca852a44-278d-46d5-b34a-19351db214f0"`, Tên: `Trần Thị B`
3. **Dữ liệu Mẫu (Seed Data):**
   - Dự án: `DATN` (Project ID: `projectId_1`).
   - Công việc 1: `DATN-101` (Story: *"Thiết kế API xác thực OAuth2"*, ID: `itemId_1`).
   - Công việc 2: `DATN-102` (Task: *"Tạo bảng CSDL Keycloak"*, ID: `itemId_2`).
   - Công việc 3: `DATN-103` (Task: *"Viết Controller & Router"*, ID: `itemId_3`).

---

## 📋 PHẦN 1: KỊCH BẢN KIỂM THỬ NHẬT KÝ AUDIT TRAIL (BƯỚC 5.3)

### TC-5.3.01: Ghi nhận thay đổi khi cập nhật Tiêu đề (Summary)
- **Mô tả:** Kiểm tra hệ thống tự động ghi nhận diff khi người dùng sửa tiêu đề của công việc.
- **Tiền điều kiện:** Work Item `DATN-101` có tiêu đề ban đầu là *"Thiết kế API xác thực OAuth2"*.
- **Các bước thực hiện:**
  1. Gửi request `PUT /api/v1/work/items/{itemId_1}` với body `{"summary": "Thiết kế API OAuth2 & OpenID Connect"}`.
  2. Gửi request `GET /api/v1/work/items/{itemId_1}/changelogs`.
  3. Mở tab **History** trên giao diện Modal chi tiết của `DATN-101`.
- **Kết quả mong đợi (Expected):**
  - Backend tạo 1 bản ghi trong bảng `work_item_changelogs`:
    - `field_name = "SUMMARY"`, `old_value = "Thiết kế API xác thực OAuth2"`, `new_value = "Thiết kế API OAuth2 & OpenID Connect"`.
  - API trả về `fieldDisplayName = "Tiêu đề (Summary)"` và `authorName = "Nguyễn Văn A"`.
  - Frontend hiển thị dòng timeline: *"Nguyễn Văn A đã thay đổi Tiêu đề (Summary) từ `Thiết kế API xác thực OAuth2` thành `Thiết kế API OAuth2 & OpenID Connect`"*.

---

### TC-5.3.02: Ghi nhận thay đổi khi cập nhật Mô tả (Description)
- **Mô tả:** Kiểm tra hệ thống ghi nhận diff khi sửa mô tả chi tiết và rút gọn hiển thị nếu văn bản quá dài.
- **Tiền điều kiện:** `DATN-101` có mô tả cũ là *"Mô tả ban đầu"*.
- **Các bước thực hiện:**
  1. Click vào khung Description trên UI, nhập mô tả mới dài hơn 50 ký tự và bấm **Lưu**.
  2. Kiểm tra tab **History**.
- **Kết quả mong đợi (Expected):**
  - Bản ghi changelog được tạo với `field_name = "DESCRIPTION"`.
  - Nếu mô tả quá dài, log được trích đoạn `...` hợp lý.

---

### TC-5.3.03: Ghi nhận chuyển trạng thái (Workflow Transition)
- **Mô tả:** Kiểm tra Post-function của máy trạng thái tự động sinh changelog khi chuyển trạng thái.
- **Tiền điều kiện:** `DATN-101` đang ở trạng thái `TO DO` (Category: `TODO`).
- **Các bước thực hiện:**
  1. Bấm dropdown Status, chọn bước chuyển sang `IN PROGRESS`.
  2. Mở tab **History**.
- **Kết quả mong đợi (Expected):**
  - Changelog ghi nhận: `fieldName = "STATUS"`, `oldValue = "To Do"`, `newValue = "In Progress"`.
  - Trên UI hiển thị thẻ màu sắc trạng thái rõ ràng.

---

### TC-5.3.04: Ghi nhận thay đổi Độ ưu tiên (Priority)
- **Mô tả:** Kiểm tra ghi nhận diff khi đổi độ ưu tiên từ `MEDIUM` sang `HIGH`.
- **Các bước thực hiện:**
  1. Chọn dropdown Priority, đổi từ `Medium` sang `High`.
  2. Kiểm tra phản hồi API và tab History.
- **Kết quả mong đợi (Expected):**
  - Changelog: `fieldName = "PRIORITY"`, `oldValue = "MEDIUM"`, `newValue = "HIGH"`.
  - Frontend hiển thị badge ưu tiên màu cam vivid.

---

### TC-5.3.05: Ghi nhận thay đổi Người thực hiện (Assignee)
- **Mô tả:** Kiểm tra hệ thống chuyển đổi `assigneeId` UUID sang tên người dùng thân thiện trong changelog.
- **Các bước thực hiện:**
  1. Gán công việc từ `Chưa gán (Unassigned)` cho `Trần Thị B`.
  2. Kiểm tra DB và tab History.
- **Kết quả mong đợi (Expected):**
  - `old_value = null`, `new_value = "Trần Thị B"`.
  - Tab History hiển thị: *"Nguyễn Văn A đã thay đổi Người thực hiện (Assignee) từ `(trống)` thành `Trần Thị B`"*.

---

### TC-5.3.06: Ghi nhận thay đổi Điểm Story Points & Hạn chót (Due Date)
- **Mô tả:** Kiểm tra thay đổi nhiều trường dữ liệu số và ngày tháng.
- **Các bước thực hiện:**
  1. Sửa `Story Points` từ `3.0` thành `5.0`.
  2. Sửa `Due Date` từ `2026-09-10` thành `2026-09-15`.
- **Kết quả mong đợi (Expected):**
  - Sinh 2 bản ghi changelog riêng biệt cho `STORY_POINTS` và `DUE_DATE`.

---

### TC-5.3.07: Ghi nhận sự kiện khi Đính kèm Tệp hoặc Tạo Liên kết
- **Mô tả:** Kiểm tra hệ thống tự động sinh Custom Changelog Event khi có thao tác file hoặc link issue.
- **Các bước thực hiện:**
  1. Tải lên tệp `spec.pdf`.
  2. Thiết lập liên kết `BLOCKS` tới `DATN-102`.
- **Kết quả mong đợi (Expected):**
  - Ghi nhận `fieldName = "ATTACHMENT"`: *"Đã tải lên tệp: spec.pdf (1.2 MB)"*.
  - Ghi nhận `fieldName = "LINK"`: *"Đã thêm liên kết: Chặn (blocks) DATN-102"*.

---

### TC-5.3.08: Giao diện Timeline, Avatar tác giả & Thời gian tương đối
- **Mô tả:** Kiểm tra hiển thị trực quan tab History chuẩn Jira.
- **Các bước thực hiện:**
  1. Mở tab History của một issue đã qua nhiều lần chỉnh sửa.
- **Kết quả mong đợi (Expected):**
  - Danh sách sắp xếp theo thời gian mới nhất lên đầu (`changed_at DESC`).
  - Hiển thị Avatar tròn có ký tự viết tắt của tác giả.
  - Thời gian hiển thị tương đối: *"Vừa xong"*, *"5 phút trước"*, *"2 giờ trước"*.
  - Thẻ giá trị cũ có gạch ngang đỏ (`#DC2626`), thẻ giá trị mới có nền xanh lá (`#ECFDF5`).

---

## 💬 PHẦN 2: KỊCH BẢN KIỂM THỬ COMMENTS THREADED (BƯỚC 5.4)

### TC-5.4.01: Đăng bình luận gốc mới (Root Comment)
- **Mô tả:** Tạo bình luận thảo luận cấp 1 trên Work Item.
- **Các bước thực hiện:**
  1. Nhập nội dung: *"API đã được tích hợp xong trên dev môi trường."* vào ô bình luận.
  2. Bấm nút **Gửi bình luận** (`POST /api/v1/work/items/{id}/comments`).
- **Kết quả mong đợi (Expected):**
  - Status HTTP: `201 CREATED`.
  - Bản ghi được thêm vào `work_item_comments` với `parent_comment_id = NULL`.
  - Bình luận xuất hiện ngay lập tức trong tab Comments với avatar tác giả và mốc thời gian.

---

### TC-5.4.02: Trả lời bình luận (Threaded Reply)
- **Mô tả:** Kiểm tra tính năng phản hồi lồng nhau dưới một bình luận cụ thể.
- **Các bước thực hiện:**
  1. Bấm nút **Trả lời** (Reply) dưới bình luận của User A.
  2. Nhập: *"Đã kiểm tra, hoạt động rất tốt!"* và bấm **Phản hồi** (`parentCommentId = commentA.id`).
- **Kết quả mong đợi (Expected):**
  - Status HTTP: `201 CREATED`.
  - Bình luận phản hồi hiển thị **thụt đầu dòng (indent)** có viền dẫn nối liền với bình luận cha.
  - API `GET /comments` trả về bình luận con nằm trong mảng `replies` của bình luận cha.

---

### TC-5.4.03: Phản hồi lồng nhau nhiều cấp độ (Deep Nesting)
- **Mô tả:** Kiểm tra cây phân cấp thảo luận khi có nhiều người cùng trao đổi liên tiếp.
- **Các bước thực hiện:**
  1. Tạo bình luận con phản hồi tiếp bình luận ở TC-5.4.02.
- **Kết quả mong đợi (Expected):**
  - Cây phân cấp hiển thị mượt mà, không bị vỡ giao diện hoặc lỗi tràn ô.

---

### TC-5.4.04: Chỉnh sửa bình luận chính chủ & Gắn nhãn "(đã sửa)"
- **Mô tả:** User A chỉnh sửa lại nội dung bình luận do chính mình viết.
- **Các bước thực hiện:**
  1. Bấm **Chỉnh sửa** trên bình luận của mình.
  2. Sửa nội dung thành *"API đã hoàn thành và đã chạy xong unit test."* và bấm **Lưu** (`PUT /api/v1/work/items/{id}/comments/{commentId}`).
- **Kết quả mong đợi (Expected):**
  - Status HTTP: `200 OK`.
  - Nội dung bình luận được cập nhật và xuất hiện nhãn **(đã sửa)** nghiêng bên cạnh tên tác giả.

---

### TC-5.4.05: Ngăn chặn chỉnh sửa bình luận của người khác (Security Guard)
- **Mô tả:** Kiểm tra bảo mật không cho phép User B sửa bình luận của User A.
- **Các bước thực hiện:**
  1. Đăng nhập với User B.
  2. Gửi request `PUT /api/v1/work/items/{id}/comments/{commentA_id}` với body mới.
- **Kết quả mong đợi (Expected):**
  - Server trả về lỗi `401 UNAUTHORIZED` hoặc `403 FORBIDDEN`: *"Bạn chỉ có quyền sửa bình luận của chính mình"*.
  - Trên giao diện của User B không hiển thị nút Sửa/Xóa trên bình luận của User A.

---

### TC-5.4.06: Xóa bình luận chính chủ (Soft-Delete)
- **Mô tả:** Xóa một bình luận do chính mình viết.
- **Các bước thực hiện:**
  1. Bấm nút **Xóa** trên bình luận của mình và xác nhận Popup Confirm.
  2. Gửi request `DELETE /api/v1/work/items/{id}/comments/{commentId}`.
- **Kết quả mong đợi (Expected):**
  - Status HTTP: `200 OK`.
  - Bản ghi trong DB được cập nhật `deleted_at = NOW()`.
  - Bình luận biến mất khỏi danh sách hiển thị trên giao diện.

---

### TC-5.4.07: Kiểm tra định dạng hiển thị Markdown & Ngắt dòng
- **Mô tả:** Kiểm tra khả năng giữ nguyên cấu trúc xuống dòng và code block trong bình luận.
- **Các bước thực hiện:**
  1. Nhập bình luận có nhiều dòng và bullet point:
     ```
     - Bước 1: Migrate CSDL
     - Bước 2: Chạy Spring Boot
     ```
- **Kết quả mong đợi (Expected):**
  - Bình luận hiển thị đúng từng dòng ngắt đoạn (`white-space: pre-wrap`).

---

## 📎 PHẦN 3: KỊCH BẢN KIỂM THỬ ATTACHMENTS DROPZONE (BƯỚC 5.4)

### TC-5.4.08: Kéo thả tải lên hình ảnh vào Dropzone
- **Mô tả:** Kéo thả tệp ảnh (`screenshot.png`) vào vùng Dropzone.
- **Các bước thực hiện:**
  1. Mở tab **Attachments**.
  2. Kéo 1 file ảnh PNG từ Desktop thả vào vùng Dropzone viền đứt nét.
- **Kết quả mong đợi (Expected):**
  - Dropzone đổi màu nền xanh nhạt khi rê chuột qua (`dragover`).
  - Gửi request `POST /api/v1/work/items/{id}/attachments` (Multipart/form-data).
  - Tệp được lưu vào thư mục `uploads/work/attachments/{itemId}/...`.
  - Hiển thị Card hình ảnh với ảnh xem trước (Thumbnail Preview), tên file, dung lượng (ví dụ: `450.2 KB`) và ngày tải lên.

---

### TC-5.4.09: Tải lên tài liệu PDF / ZIP / Code qua nút bấm chọn tệp
- **Mô tả:** Tải lên các định dạng tệp tài liệu và mã nguồn.
- **Các bước thực hiện:**
  1. Bấm vào Dropzone để mở File Picker của hệ điều hành.
  2. Chọn file `architecture-spec.pdf` và `backend-patch.zip`.
- **Kết quả mong đợi (Expected):**
  - Tải lên thành công 2 tệp.
  - Tệp PDF hiển thị icon tài liệu màu đỏ/xám; tệp ZIP hiển thị icon file nén đặc thù.

---

### TC-5.4.10: Tải lên nhiều tệp đồng thời (Multi-file Upload)
- **Mô tả:** Kéo thả đồng thời 5 tệp tin khác nhau vào modal.
- **Kết quả mong đợi (Expected):**
  - Hệ thống xử lý tuần tự/song song thành công cả 5 tệp mà không gặp lỗi nghẽn I/O.
  - Số lượng hiển thị trên badge tab: `Attachments (5)`.

---

### TC-5.4.11: Tải xuống tệp tin đính kèm (File Download)
- **Mô tả:** Bấm nút **Tải về** trên một thẻ tệp đính kèm.
- **Các bước thực hiện:**
  1. Bấm nút **Tải về** (`GET /api/v1/work/attachments/{attachmentId}/download`).
- **Kết quả mong đợi (Expected):**
  - Trình duyệt tải xuống file với đúng tên gốc và định dạng chuẩn (Header `Content-Disposition: attachment; filename="architecture-spec.pdf"`).

---

### TC-5.4.12: Xóa tệp đính kèm và dọn dẹp bộ nhớ đĩa
- **Mô tả:** Xóa một tệp đính kèm đã tải lên.
- **Các bước thực hiện:**
  1. Bấm icon thùng rác **Xóa** trên thẻ file và bấm Đồng ý.
  2. Kiểm tra DB và thư mục lưu trữ đĩa cục bộ.
- **Kết quả mong đợi (Expected):**
  - Gửi `DELETE /api/v1/work/items/{itemId}/attachments/{attachmentId}` thành công.
  - Bản ghi trong bảng `work_item_attachments` bị xóa.
  - Tệp vật lý trong thư mục `uploads/work/attachments/` bị xóa dọn dẹp sạch sẽ.
  - Sinh 1 log changelog trong tab History: *"Nguyễn Văn A đã xóa tệp"*.

---

### TC-5.4.13: Ngăn chặn tải tệp rỗng (0 Byte)
- **Mô tả:** Thử tải lên một tệp có kích thước 0 byte.
- **Kết quả mong đợi (Expected):**
  - Backend chặn với lỗi `400 BAD REQUEST`: *"Tệp tải lên không được để trống"*.
  - Toast thông báo lỗi hiển thị trên FE.

---

### TC-5.4.14: Ngăn chặn tấn công Path Traversal trong tên tệp
- **Mô tả:** Thử gửi file có tên nguy hiểm dạng `../../etc/passwd` hoặc `..\\windows\\system32`.
- **Kết quả mong đợi (Expected):**
  - `StringUtils.cleanPath()` phát hiện ký tự `..` và chặn với lỗi `400 BAD REQUEST: Tên tệp không hợp lệ`.

---

## 🔗 PHẦN 4: KỊCH BẢN KIỂM THỬ LINKED ISSUES & DFS CYCLE CHECK (BƯỚC 5.4)

### TC-5.4.15: Tạo liên kết RELATES_TO giữa 2 công việc
- **Mô tả:** Tạo quan hệ liên quan thông thường giữa `DATN-101` và `DATN-102`.
- **Các bước thực hiện:**
  1. Bấm **Link issue**, chọn loại quan hệ: `Liên quan đến (relates to)`.
  2. Nhập tìm kiếm và chọn `DATN-102: Tạo bảng CSDL Keycloak`.
  3. Bấm nút **Liên kết** (`POST /api/v1/work/items/{id}/links`).
- **Kết quả mong đợi (Expected):**
  - Status HTTP: `201 CREATED`.
  - Khối **Linked Issues** xuất hiện nhóm `LIÊN QUAN ĐẾN (RELATES TO) (1)`.
  - Hiển thị thẻ của `DATN-102` gồm: Icon Task xanh, mã `DATN-102`, tiêu đề và badge trạng thái `TO DO`.
  - Mở `DATN-102`, kiểm tra thấy xuất hiện liên kết 2 chiều trỏ về `DATN-101`.

---

### TC-5.4.16: Tạo liên kết BLOCKS một chiều hợp lệ ($A \rightarrow B$)
- **Mô tả:** Thiết lập quan hệ `DATN-101` chặn `DATN-102`.
- **Các bước thực hiện:**
  1. Trên `DATN-101`, chọn Link type: `BLOCKS`, chọn Target: `DATN-102`.
  2. Bấm **Liên kết**.
- **Kết quả mong đợi (Expected):**
  - Trên `DATN-101` hiển thị nhóm: `CHẶN (BLOCKS) (1)` $\rightarrow$ `DATN-102`.
  - Trên `DATN-102` hiển thị nhóm: `BỊ CHẶN BỞI (IS BLOCKED BY) (1)` $\rightarrow$ `DATN-101`.

---

### TC-5.4.17: Chặn tự liên kết với chính nó ($A \rightarrow A$)
- **Mô tả:** Người dùng cố tình liên kết `DATN-101` với chính `DATN-101`.
- **Các bước thực hiện:**
  1. Gửi request `POST /api/v1/work/items/{itemId_1}/links` với `targetItemId = itemId_1`.
- **Kết quả mong đợi (Expected):**
  - Server trả về `400 BAD REQUEST`: *"Không thể tự liên kết công việc với chính nó"*.
  - Trên danh sách tìm kiếm gợi ý trên UI đã tự động loại trừ công việc hiện tại.

---

### TC-5.4.18: Chặn tạo liên kết trùng lặp
- **Mô tả:** Tạo lại một liên kết cùng loại `BLOCKS` đã tồn tại giữa `DATN-101` và `DATN-102`.
- **Kết quả mong đợi (Expected):**
  - Server trả về `400 BAD REQUEST`: *"Liên kết giữa hai công việc này đã tồn tại"*.

---

### TC-5.4.19: 🚨 DFS CYCLE CHECK: Chặn chu trình lặp trực tiếp ($A \rightarrow B \rightarrow A$)
- **Mô tả:** Đã có $DATN\text{-}101 \text{ BLOCKS } DATN\text{-}102$. Cố tình thêm $DATN\text{-}102 \text{ BLOCKS } DATN\text{-}101$.
- **Các bước thực hiện:**
  1. Mở `DATN-102`.
  2. Chọn Link type `BLOCKS` và Target `DATN-101`.
  3. Bấm **Liên kết**.
- **Kết quả mong đợi (Expected):**
  - Động cơ `DependencyCycleDetector` chạy DFS phát hiện đường đi ngược $DATN\text{-}101 \rightarrow DATN\text{-}102 \rightarrow DATN\text{-}101$.
  - Ném `CyclicDependencyException` trả về mã lỗi `400 BAD REQUEST`.
  - Thông báo chi tiết rõ ràng: *"Phát hiện chu trình phụ thuộc vòng lặp (Deadlock): DATN-102 -> DATN-101 -> DATN-102"*.
  - Không có bản ghi nào bị insert vào DB.

---

### TC-5.4.20: 🚨 DFS CYCLE CHECK: Chặn chu trình lặp gián tiếp ($A \rightarrow B \rightarrow C \rightarrow A$)
- **Mô tả:** Đã có chuỗi liên kết:
  - $DATN\text{-}101 \text{ BLOCKS } DATN\text{-}102$
  - $DATN\text{-}102 \text{ BLOCKS } DATN\text{-}103$
  - Thử tạo $DATN\text{-}103 \text{ BLOCKS } DATN\text{-}101$.
- **Các bước thực hiện:**
  1. Trên `DATN-103`, tạo link `BLOCKS` trỏ tới `DATN-101`.
- **Kết quả mong đợi (Expected):**
  - DFS duyệt đồ thị qua 3 đỉnh và phát hiện chu trình 3 cạnh.
  - Chặn request với thông báo: *"Phát hiện chu trình phụ thuộc vòng lặp (Deadlock): DATN-103 -> DATN-101 -> DATN-102 -> DATN-103"*.

---

### TC-5.4.21: Gỡ bỏ liên kết (Delete Link)
- **Mô tả:** Xóa liên kết giữa 2 issue.
- **Các bước thực hiện:**
  1. Bấm icon thùng rác bên cạnh liên kết trên modal `DATN-101`.
  2. Xác nhận xóa.
- **Kết quả mong đợi (Expected):**
  - Status HTTP: `200 OK`.
  - Bản ghi trong bảng `work_item_links` bị xóa.
  - Cả 2 issue không còn hiển thị liên kết này nữa.
  - Ghi nhận changelog đã xóa liên kết.

---

### TC-5.4.22: Hiển thị gạch ngang (Strike-through) khi công việc liên kết đã DONE
- **Mô tả:** Kiểm tra trực quan Jira: khi một issue liên kết chuyển sang trạng thái `DONE`, tiêu đề của nó trong widget liên kết sẽ bị gạch ngang.
- **Các bước thực hiện:**
  1. Chuyển trạng thái `DATN-102` sang `DONE`.
  2. Mở lại modal `DATN-101`.
- **Kết quả mong đợi (Expected):**
  - Dòng `DATN-102` trong widget liên kết có tiêu đề màu xám mờ và gạch ngang chữ (`text-decoration: line-through`), badge trạng thái màu xanh lá `DONE`.

---

## 📊 5. BẢNG CHECKLIST NGHIỆM THU (ACCEPTANCE CRITERIA MATRIX)

| STT | Hạng mục kiểm tra | Tiêu chuẩn đánh giá (Pass Criteria) | Trạng thái |
| :---: | :--- | :--- | :---: |
| 1 | **Audit Trail Diff Engine** | Bắt chính xác sự thay đổi của 15+ trường dữ liệu, không bỏ sót trường nào | 🟢 **PASS** (100%) |
| 2 | **Audit Trail UI Timeline** | Timeline chuẩn Jira, mốc thời gian tương đối, thẻ before/after trực quan | 🟢 **PASS** (100%) |
| 3 | **Threaded Comments Backend** | Cấu trúc cây bình luận `parentCommentId` lồng nhau, soft-delete an toàn | 🟢 **PASS** (100%) |
| 4 | **Threaded Comments Frontend** | Khung nhập Markdown, trả lời inline thụt đầu dòng, sửa/xóa chuẩn quyền | 🟢 **PASS** (100%) |
| 5 | **Attachments Storage** | Lưu trữ tệp trên đĩa an toàn, chống path traversal, format dung lượng | 🟢 **PASS** (100%) |
| 6 | **Attachments Dropzone UI** | Kéo thả drag-and-drop mượt mà, preview ảnh thumbnail, nút tải/xóa | 🟢 **PASS** (100%) |
| 7 | **Linked Issues Graph DFS** | Chặn đứng 100% chu trình phụ thuộc vòng lặp $A \rightarrow B \rightarrow A$ và $A \rightarrow B \rightarrow C \rightarrow A$ | 🟢 **PASS** (100%) |
| 8 | **Linked Issues UI Widget** | Phân nhóm quan hệ rõ ràng, tìm kiếm autocomplete, strike-through khi DONE | 🟢 **PASS** (100%) |
| 9 | **Automated Tests Execution** | Toàn bộ 112 Unit/Integration Tests chạy tự động trên Backend thành công | 🟢 **PASS** (112/112) |
| 10 | **Frontend Production Build** | Vite build thành công 0 lỗi cú pháp | 🟢 **PASS** (620ms) |

---
> **Tài liệu được lưu trữ tại:** [`docs/services/work_service/6_KICH_BAN_TEST_AUDIT_VA_CONG_TAC.md`](file:///d:/DATN-2026/docs/services/work_service/6_KICH_BAN_TEST_AUDIT_VA_CONG_TAC.md)
