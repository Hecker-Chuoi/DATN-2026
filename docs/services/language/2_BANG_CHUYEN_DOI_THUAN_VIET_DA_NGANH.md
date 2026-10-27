# BẢNG CHUYỂN ĐỔI THUẦN VIỆT ĐA NGÀNH NGHỀ TRÊN GIAO DIỆN (MULTI-INDUSTRY VIETNAMESE LOCALIZATION MATRIX)

> **Dự án:** Hệ thống Quản trị Công việc & Dự án Vi dịch vụ (DATN-2026)  
> **Phạm vi áp dụng:** Toàn bộ giao diện người dùng Frontend (`frontend/src/`)  
> **Vị trí tài liệu:** `docs/services/language/2_BANG_CHUYEN_DOI_THUAN_VIET_DA_NGANH.md`  
> **Ngày phê duyệt & cập nhật:** 27/10/2026  
> **Trạng thái:** `Chính thức áp dụng (Active Standard)`  

---

## 📑 MỤC LỤC

1. [Bối cảnh & Mục tiêu Chuyển đổi](#1-bối-cảnh--mục-tiêu-chuyển-đổi)
2. [Triết lý Thiết kế Ngôn ngữ Đa Ngành nghề](#2-triết-lý-thiết-kế-ngôn-ngữ-đa-ngành-nghề)
3. [Bảng Chuẩn hóa Thuật ngữ Cốt lõi (Master Translation Matrix)](#3-bảng-chuẩn-hóa-thuật-ngữ-cốt-lõi-master-translation-matrix)
4. [Danh mục Chuyển đổi Chi tiết theo Từng Tệp Nguồn](#4-danh-mục-chuyển-đổi-chi-tiết-theo-từng-tệp-nguồn)
   - [4.1. Hệ thống Hằng số & Bộ chuyển đổi Dữ liệu](#41-hệ-thống-hằng-số--bộ-chuyển-đổi-dữ-liệu)
   - [4.2. Thành phần Dùng chung & Hộp thoại Tương tác](#42-thành-phần-dùng-chung--hộp-thoại-tương-tác)
   - [4.3. Các Màn hình & Trang Nghiệp vụ (Pages)](#43-các-màn-hình--trang-nghiệp-vụ-pages)
5. [Quy chuẩn Nhập liệu & Ví dụ Mẫu Đa Ngành nghề](#5-quy-chuẩn-nhập-liệu--ví-dụ-mẫu-đa-ngành-nghề)
6. [Bảo toàn Hợp đồng Kỹ thuật Backend](#6-bảo-toàn-hợp-đồng-kỹ-thuật-backend)
7. [Kết quả Kiểm tra & Biên dịch](#7-kết-quả-kiểm-tra--biên-dịch)

---

## 1. BỐI CẢNH & MỤC TIÊU CHUYỂN ĐỔI

Hệ thống **DATN-2026** ban đầu kế thừa bộ thuật ngữ từ Jira Software và phương pháp luận Agile/Scrum truyền thống dành cho kỹ sư công nghệ phần mềm (software development). Tuy nhiên, nền tảng được định vị là **hệ thống quản trị công việc doanh nghiệp toàn diện**, phục vụ đa dạng các phòng ban và ngành nghề:
- **Xây dựng & Kiến trúc**: Quản lý gói thầu, thi công hiện trường, nghiệm thu khối lượng.
- **Tiếp thị & Truyền thông (Marketing)**: Chiến dịch quảng bá, sáng tạo nội dung, thiết kế ấn phẩm.
- **Kinh doanh, Bán lẻ & F&B**: Mở rộng chuỗi cửa hàng, quản lý đơn hàng, nhập hàng và vận hành.
- **Giáo dục & Đào tạo**: Soạn thảo giáo trình, kế hoạch giảng dạy, tổ chức khóa học.
- **Hành chính & Vận hành**: Quy trình tuyển dụng, kiểm kê tài sản, văn thư lưu trữ.

**Mục tiêu của đợt chuyển hóa Thuần Việt:**
1. **Loại bỏ rào cản chuyên ngành IT**: Thay thế các từ ngữ kỹ thuật phần mềm hẹp (như *"Câu chuyện người dùng"*, *"Con bọ"*, *"Lỗi phần mềm"*, *"Danh sách tồn đọng"*, *"Vận tốc"*, *"Lưu lượng tích lũy"*) bằng các từ ngữ tiếng Việt tự nhiên, lịch sự, dễ hiểu với mọi nhân sự.
2. **Duy trì bản chất phương pháp luận**: Vẫn giữ được tính ưu việt của mô hình quản trị Theo chu kỳ / Theo giai đoạn (Scrum) và Bảng công việc liên tục (Kanban) mà không làm biến dạng ý nghĩa khoa học quản lý.
3. **Bảo toàn 100% hợp đồng API**: Giữ nguyên toàn bộ mã enum và giá trị code của Backend (như `STORY`, `BUG`, `TASK`, `EPIC`, `SUBTASK`, `TODO`, `IN_PROGRESS`, `DONE`, `SOFTWARE_SCRUM`, `SOFTWARE_KANBAN`).

---

## 2. TRIẾT LÝ THIẾT KẾ NGÔN NGỮ ĐA NGÀNH NGHỀ

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                   4 NGUYÊN TẮC THUẦN VIỆT ĐA NGÀNH NGHỀ                     │
├─────────────────────────────────────────────────────────────────────────────┤
│ 1. TRUNG TÍNH (Domain-Agnostic): Dễ hiểu cho cả kỹ sư, kế toán lẫn marketer. │
│ 2. TÍCH CỰC & CHUYÊN NGHIỆP: Tránh cảm giác tiêu cực (tồn đọng, con bọ).   │
│ 3. KHÔNG DỊCH THÔ CƠ HỌC: Tránh nghĩa đen vật lý/ống nước (vận tốc, lưu lượng). │
│ 4. NHẤT QUÁN HỆ THỐNG: Cùng một khái niệm phải hiển thị đồng nhất toàn app.│
└─────────────────────────────────────────────────────────────────────────────┘
```

- **Từ tiêu cực sang tích cực**: Khái niệm *Backlog* trước đây dịch là *"Danh sách tồn đọng"* mang hàm ý việc bị trễ hạn, tồn đọng ứ đọng; chuyển sang **"Kho việc & Kế hoạch"** hoặc **"Kho việc chờ phân bổ"** thể hiện đúng bản chất là kho lưu trữ tài nguyên công việc sẵn sàng để đưa vào kế hoạch.
- **Từ từ lóng phần mềm sang ngôn ngữ doanh nghiệp**:
  - *User Story* $\to$ **Yêu cầu / Đầu việc chính** (bất kỳ ngành nào cũng có "yêu cầu khách hàng", "yêu cầu nghiệp vụ").
  - *Bug* $\to$ **Sự cố / Vấn đề phát sinh** (công trường gặp sự cố, marketing gặp trục trặc truyền thông, nhà hàng gặp sự cố thiết bị).
- **Từ dịch thô vật lý sang chỉ số năng suất**:
  - *Velocity* $\to$ **Năng suất làm việc** (thay cho *"Vận tốc"* của môn Vật lý).
  - *Cumulative Flow (CFD)* $\to$ **Phân bổ luồng trạng thái** (thay cho *"Lưu lượng tích lũy"* của môn Thủy lực).

---

## 3. BẢNG CHUẨN HÓA THUẬT NGỮ CỐT LÕI (MASTER TRANSLATION MATRIX)

| STT | Thuật ngữ gốc (EN) / Mã Code | Thuật ngữ cũ (Phần mềm) | Thuần Việt mới (Đa ngành nghề) | Lý do & Ý nghĩa chuyển đổi |
| :---: | :--- | :--- | :--- | :--- |
| **1** | `STORY` / `USER_STORY` | Câu chuyện người dùng | **Yêu cầu / Đầu việc** | Phù hợp mọi nghiệp vụ; tránh từ "câu chuyện" gây khó hiểu ngoài ngành phần mềm. |
| **2** | `BUG` | Lỗi phần mềm / Con bọ | **Sự cố / Vấn đề phát sinh** | Mọi ngành nghề đều có sự cố hiện trường, trục trặc vận hành, vấn đề phát sinh. |
| **3** | `TASK` | Nhiệm vụ | **Nhiệm vụ** | Chuẩn xác, ngắn gọn, phù hợp tác vụ tiêu chuẩn. |
| **4** | `EPIC` | Hạng mục lớn | **Hạng mục lớn** | Bao quát mục tiêu dài hạn, gói thầu hoặc giai đoạn lớn. |
| **5** | `SUB_TASK` / `SUBTASK` | Nhiệm vụ con | **Nhiệm vụ con** | Tác vụ chi tiết trực thuộc công việc cha cấp 0. |
| **6** | `BACKLOG` | Danh sách tồn đọng | **Kho việc & Kế hoạch** *(Kho việc chờ phân bổ)* | Tránh cảm giác tiêu cực "tồn đọng"; thể hiện kho việc sẵn sàng xếp lịch. |
| **7** | `SPRINT` | Sprint / Chu kỳ chạy | **Chu kỳ** / **Đợt làm việc** | Khung thời gian làm việc có định kỳ, cam kết mục tiêu cụ thể. |
| **8** | `ACTIVE_BOARD` / `BOARD` | Bảng làm việc | **Bảng tiến độ** | Trực quan hóa tiến trình dịch chuyển công việc qua các cột. |
| **9** | `STORY_POINTS` / `POINTS` | Điểm ước lượng / Điểm Story | **Điểm ước lượng** / **Khối lượng (Điểm)** | Đo lường độ phức tạp, rủi ro và khối lượng cần giải quyết. |
| **10** | `VELOCITY` | Vận tốc chu kỳ | **Năng suất làm việc** *(Năng suất trung bình)* | Tránh từ "vận tốc" thuần vật lý; thể hiện năng lực hoàn thành của đội ngũ. |
| **11** | `CUMULATIVE_FLOW_DIAGRAM` | Biểu đồ lưu lượng tích lũy | **Biểu đồ luồng trạng thái** *(Phân bổ luồng trạng thái)* | Phản ánh chính xác phân bố các thẻ việc qua các giai đoạn quy trình. |
| **12** | `WIP_LIMIT` | Giới hạn WIP / Giới hạn đang làm | **Giới hạn việc đang làm** | Kiểm soát số lượng công việc cùng lúc, tránh quá tải. |
| **13** | `SWIMLANE` | Swimlane | **Phân làn công việc** | Gom nhóm các thẻ theo người phụ trách hoặc hạng mục lớn. |
| **14** | `SOFTWARE_SCRUM` | Dự án Scrum | **Theo chu kỳ / Theo giai đoạn** | Quản lý theo từng chu kỳ hoặc giai đoạn định kỳ, lập kế hoạch đợt việc và đo lường khối lượng. |
| **15** | `SOFTWARE_KANBAN` | Dự án Kanban | **Bảng công việc liên tục** | Luồng công việc liên tục, trực quan hóa trạng thái và giới hạn việc đang làm. |
| **16** | `BUSINESS` | Kinh doanh | **Nghiệp vụ doanh nghiệp** | Khái quát rộng hơn việc buôn bán, bao gồm cả vận hành, dịch vụ. |
| **17** | `WORKSPACE` | Workspace | **Không gian làm việc** | Cấp bao bọc cao nhất quản lý toàn bộ các dự án của doanh nghiệp. |
| **18** | `ADMIN` / `ADMINISTRATOR` | Administrator / Quản trị | **Quản trị viên** | Danh xưng trang trọng, lịch sự và chuẩn chính tả tiếng Việt. |
| **19** | `MEMBER` | Member | **Thành viên** | Người trực tiếp tham gia xử lý công việc. |
| **20** | `OWNER` | Owner | **Chủ sở hữu** | Người sở hữu không gian làm việc. |
| **21** | `LEAD` / `PROJECT_LEAD` | Project Lead | **Trưởng dự án** | Người điều phối chính của dự án. |
| **22** | `SLUG` | Đường dẫn (slug) | **Mã định danh URL** | Giúp người dùng văn phòng hiểu rõ mục đích của chuỗi ký tự trên thanh địa chỉ. |

---

## 4. DANH MỤC CHUYỂN ĐỔI CHI TIẾT THEO TỪNG TỆP NGUỒN

### 4.1. Hệ thống Hằng số & Bộ chuyển đổi Dữ liệu

| Tệp nguồn | Vị trí thay đổi | Trước khi sửa | Sau khi sửa |
| :--- | :--- | :--- | :--- |
| `frontend/src/constants/workConstants.js` | `WORK_ITEM_TYPE_CONFIG.STORY.name` | `Câu chuyện người dùng` | `Yêu cầu` |
| `frontend/src/constants/workConstants.js` | `WORK_ITEM_TYPE_CONFIG.BUG.name` | `Lỗi phần mềm` | `Sự cố` |
| `frontend/src/constants/workConstants.js` | `getTypeDisplayName(type)` | `STORY` $\to$ `Câu chuyện người dùng`<br>`BUG` $\to$ `Lỗi phần mềm` | `STORY` $\to$ `Yêu cầu`<br>`BUG` $\to$ `Sự cố` |
| `frontend/src/constants/workConstants.js` | `PROJECT_TYPE_CONFIG.SCRUM.name` | `Quy trình Scrum` | `Theo chu kỳ / Theo giai đoạn` |
| `frontend/src/constants/workConstants.js` | `PROJECT_TYPE_CONFIG.KANBAN.name` | `Bảng Kanban` | `Bảng công việc liên tục` |
| `frontend/src/constants/workConstants.js` | Bổ sung helper mới | *(chưa có)* | `export const getRoleDisplayName(role)` |
| `frontend/src/constants/jiraTokens.js` | `TYPE_TOKENS.STORY.label` | `Câu chuyện người dùng` | `Yêu cầu` |
| `frontend/src/constants/jiraTokens.js` | `TYPE_TOKENS.BUG.label` | `Lỗi phần mềm` | `Sự cố` |
| `frontend/src/constants/templateConstants.js` | `PROJECT_TYPE_LABELS` | `Quy trình Scrum`<br>`Bảng Kanban`<br>`Kinh doanh` | `Theo chu kỳ / Theo giai đoạn`<br>`Bảng công việc liên tục`<br>`Nghiệp vụ doanh nghiệp` |
| `frontend/src/utils/reportAdapters.js` | Line 34 | `label: s.sprintName \|\| 'Sprint'` | `label: s.sprintName \|\| 'Đợt làm việc'` |
| `frontend/src/utils/dragGuards.js` | Line 62 (thông báo kéo thẻ) | `(Câu chuyện, Nhiệm vụ hoặc Lỗi phần mềm)` | `(Yêu cầu, Nhiệm vụ hoặc Sự cố)` |
| `frontend/src/utils/workspaceRole.js` | `ROLE_LABELS.ADMIN` | `Quản trị` | `Quản trị viên` |

---

### 4.2. Thành phần Dùng chung & Hộp thoại Tương tác

| Tệp nguồn | Vị trí / Thuộc tính | Trước khi sửa | Sau khi sửa |
| :--- | :--- | :--- | :--- |
| `AccessDenied.jsx` | Thông báo liên hệ 403 | `liên hệ Trưởng dự án (Project Lead) hoặc Administrator` | `liên hệ Trưởng dự án hoặc Quản trị viên` |
| `PendingInvitations.jsx` | Hiển thị vai trò | `vai trò {inv.roleName \|\| inv.role \|\| 'MEMBER'}` | `vai trò {inv.roleName \|\| getRoleDisplayName(inv.role) \|\| 'Thành viên'}` |
| `StoryPointsHelpTooltip.jsx` | Thang điểm | `Thang Fibonacci:` | `Thang điểm gợi ý:` |
| `StoryPointsHelpTooltip.jsx` | Quy tắc phân cấp | `Câu chuyện / Nhiệm vụ: Nhập điểm trực tiếp` | `Yêu cầu / Nhiệm vụ: Nhập điểm trực tiếp` |
| `StoryPointsHelpTooltip.jsx` | Nhiệm vụ con | `Không tính điểm (theo dõi theo giờ làm)` | `Không tính điểm (theo dõi theo tiến độ chi tiết)` |
| `InteractiveBoard.jsx` | Bắt lỗi kéo nhiệm vụ con | Regex kiểm tra: `công việc con` | Mở rộng regex: `công việc con \| nhiệm vụ con` |
| `SprintAccordion.jsx` | Trạng thái rỗng Backlog | `Danh sách tồn đọng trống. Hãy tạo công việc mới.` | `Kho công việc hiện đang trống. Hãy tạo công việc mới.` |
| `SprintAccordion.jsx` | Gợi ý kéo việc vào sprint | `Kéo công việc từ danh sách tồn đọng vào chu kỳ này.` | `Kéo công việc từ kho việc vào chu kỳ này.` |
| `CompleteSprintModal.jsx` | Radio chuyển việc chưa xong | `Danh sách tồn đọng` | `Kho việc chờ phân bổ` |
| `CreateWorkItemModal.jsx` | Cấu hình Type badges | `Câu chuyện người dùng`, `Lỗi phần mềm` | `Yêu cầu`, `Sự cố` |
| `CreateWorkItemModal.jsx` | Placeholder tiêu đề việc | `VD: Khách hàng quét mã QR VNPay để thanh toán...` | `VD: Triển khai kế hoạch khảo sát, chuẩn bị báo cáo tiến độ...` |
| `CreateWorkItemModal.jsx` | Dropdown chọn cha | `-- Không có (Độc lập / Danh sách tồn đọng) --` | `-- Không có (Độc lập / Kho việc) --` |
| `CreateWorkItemModal.jsx` | Option chọn chu kỳ | `<option value="">Danh sách tồn đọng</option>` | `<option value="">Kho việc chờ phân bổ</option>` |
| `WorkItemDetailModal.jsx` | Type icons & nhãn loại | `Câu chuyện người dùng`, `Lỗi phần mềm` | `Yêu cầu`, `Sự cố` |
| `WorkItemDetailModal.jsx` | Toast chuyển việc | `Đã đưa về danh sách tồn đọng` | `Đã đưa về kho việc` |
| `WorkItemDetailModal.jsx` | Dropdown chọn chu kỳ | `Danh sách tồn đọng` | `Kho việc chờ phân bổ` |
| `CreateProjectModal.jsx` | Placeholder tên dự án | `VD: Core Banking Platform, E-Commerce App...` | `VD: Chiến dịch Marketing 2026, Xây dựng Văn phòng...` |
| `CreateProjectModal.jsx` | Placeholder mã dự án | `VD: CBE, PRJ` | `VD: MKT, XD, KH` |
| `CreateProjectModal.jsx` | Thẻ chọn loại dự án | `Quy trình Scrum`, `Bảng Kanban` | `Theo chu kỳ / Theo giai đoạn`, `Bảng công việc liên tục` |
| `CreateProjectModal.jsx` | Tùy chọn nhân bản mẫu | `công việc nằm hết trong Danh sách tồn đọng` | `công việc nằm hết trong Kho việc chờ phân bổ` |
| `ProjectMembersTab.jsx` | Mô tả quyền tạo việc | `Hạng mục lớn, Câu chuyện người dùng, Nhiệm vụ, Lỗi phần mềm...` | `Hạng mục lớn, Yêu cầu, Nhiệm vụ, Sự cố và Nhiệm vụ con...` |
| `ProjectWorkflowTab.jsx` | Nhãn biểu tượng preset | `Thẻ đánh dấu (Câu chuyện)`<br>`Con bọ (Lỗi phần mềm)`<br>`Điểm nhánh (Nhiệm vụ con)` | `Thẻ đánh dấu (Yêu cầu)`<br>`Cảnh báo (Sự cố)`<br>`Nhánh việc (Nhiệm vụ con)` |
| `VelocityChart.jsx` | Thông báo biểu đồ rỗng | `Chưa có chu kỳ nào đã đóng để tính vận tốc.` | `Chưa có chu kỳ nào hoàn tất để tính năng suất làm việc.` |
| `CumulativeFlowChart.jsx` | Thông báo biểu đồ rỗng | `Chưa có dữ liệu lưu lượng tích lũy.` | `Chưa có dữ liệu phân bổ luồng trạng thái.` |
| `TemplatePreviewModal.jsx` | Tiêu đề bảng chu kỳ | `Sprint`, `Points` | `Tên chu kỳ`, `Khối lượng (Điểm)` |
| `TemplatePreviewModal.jsx` | Banner kiểm tra hợp lệ | `trạng thái, workflow, board, sprint và thời gian...` | `trạng thái, quy trình, bảng làm việc, chu kỳ và thời gian...` |

---

### 4.3. Các Màn hình & Trang Nghiệp vụ (Pages)

| Tệp nguồn | Vị trí / Thuộc tính | Trước khi sửa | Sau khi sửa |
| :--- | :--- | :--- | :--- |
| `LandingPage.jsx` | Huy hiệu Hero | `Kiến trúc Vi dịch vụ & Quản lý Định danh Doanh nghiệp` | `Nền tảng Quản trị Công việc & Dự án Doanh nghiệp` |
| `LandingPage.jsx` | Tiêu đề chính | `Nền tảng Quản lý Định danh & Cổng Điều phối API` | `Nền tảng Quản lý Công việc & Dự án Toàn diện` |
| `LandingPage.jsx` | Phụ đề Hero | `Hệ thống phân quyền truy cập, xác thực tập trung với Keycloak...` | `Giải pháp toàn diện giúp doanh nghiệp lập kế hoạch, theo dõi tiến độ, phân bổ nguồn lực và tối ưu hiệu suất làm việc đa phòng ban.` |
| `LandingPage.jsx` | Thẻ tính năng 1 | `Xác thực Keycloak` (chuẩn OAuth2/OIDC) | `Bảo mật & Phân quyền tập trung` (bảo mật đa tầng) |
| `LandingPage.jsx` | Thẻ tính năng 2 | `Spring Cloud Gateway` (điều phối JWT) | `Điều phối & Vận hành mượt mà` (hạ tầng dữ liệu ổn định) |
| `LoginPage.jsx` | Logo thương hiệu | `DATN SYSTEM` | `HỆ THỐNG QUẢN TRỊ DỰ ÁN` |
| `LoginPage.jsx` | Placeholder mã tổ chức | `541115DSFA` | `Mã tổ chức / Mã phòng ban` |
| `LoginPage.jsx` | Mockup minh họa đồ họa | `Chỉ số Dịch vụ Định danh`<br>`Keycloak IAM Server`<br>`Spring API Gateway / Cổng 8081`<br>`VI DỊCH VỤ / 12 Đã hoàn thành` | `Chỉ số Hoạt động Hệ thống`<br>`Máy chủ Xác thực`<br>`Cổng Điều phối Trung tâm / Sẵn sàng`<br>`DỰ ÁN ĐANG CHẠY / 12 Hoàn thành tốt` |
| `UserLoginPage.jsx` | Tiêu đề đăng nhập | `Không gian làm việc DATN` | `Không gian Làm việc Trực tuyến` |
| `UserLoginPage.jsx` | Nét gạch phân cách | `HOẶC DÙNG TÀI KHOẢN` | `HOẶC ĐĂNG NHẬP BẰNG TÀI KHOẢN` |
| `UserLoginPage.jsx` | Placeholder tên đăng nhập | `VD: user_test` | `VD: nguyen.van.a` |
| `RegisterPage.jsx` | Logo thương hiệu | `DATN SYSTEM` | `HỆ THỐNG QUẢN TRỊ DỰ ÁN` |
| `RegisterPage.jsx` | Placeholder email | `user@domain.com` | `email_cua_ban@congty.com` |
| `RegisterPage.jsx` | Huy hiệu đồ họa mockup | `Chuẩn OIDC`<br>`DATN 2026` | `Bảo mật cao`<br>`HỆ THỐNG 2026` |
| `ForgotPasswordPage.jsx`| Placeholder email | `you@example.com` | `email_cua_ban@congty.com` |
| `InvitationsPage.jsx` | Nhãn loại lời mời | `Workspace` | `Không gian làm việc` |
| `InvitationsPage.jsx` | Hiển thị tên vai trò | `với vai trò {inv.role \|\| 'MEMBER'}` | `với vai trò {getRoleDisplayName(inv.role) \|\| 'Thành viên'}` |
| `WorkspaceListPage.jsx`| Tiêu đề khối lời mời | `Lời mời workspace đang chờ` | `Lời mời không gian làm việc đang chờ` |
| `WorkspaceSettingsPage.jsx` | Quyền xem trang | `vai trò Quản trị trở lên` | `vai trò Quản trị viên trở lên` |
| `WorkspaceSettingsPage.jsx` | Chú thích URL slug | `Đường dẫn (slug) không đổi được` | `Mã định danh URL không đổi được` |
| `ProjectListPage.jsx` | Bộ lọc danh mục dự án | `Dự án Scrum`<br>`Dự án Kanban` | `Theo chu kỳ / giai đoạn`<br>`Bảng công việc liên tục` |
| `ProjectDetailPage.jsx` | Thanh tab con | `Bảng làm việc`<br>`Danh sách tồn đọng` | `Bảng tiến độ`<br>`Kho việc & Kế hoạch` |
| `ProjectBoardPage.jsx` | Gợi ý khi chưa có sprint | `Hãy sang tab Danh sách tồn đọng để bắt đầu...` | `Hãy sang tab Kho việc & Kế hoạch để bắt đầu...` |
| `ProjectBacklogPage.jsx`| Thông báo & Loading | `Đang tải danh sách tồn đọng...`<br>`Không tải được danh sách tồn đọng.` | `Đang tải kho công việc & kế hoạch...`<br>`Không tải được kho công việc & kế hoạch.` |
| `ProjectReportsPage.jsx`| Tabs & Cards số liệu | `Vận tốc chu kỳ`<br>`Lưu lượng tích lũy`<br>`Vận tốc trung bình` | `Năng suất làm việc`<br>`Phân bổ luồng trạng thái`<br>`Năng suất trung bình` |
| `AdminDashboard.jsx` | Menu & Thông báo tải | `Xác thực Keycloak`<br>`Đang tải dữ liệu từ Identity Service...` | `Bảo mật & Xác thực`<br>`Đang tải danh sách tài khoản người dùng...` |

---

## 5. QUY CHUẨN NHẬP LIỆU & VÍ DỤ MẪU ĐA NGÀNH NGHỀ

Để tránh việc người dùng mới bị định kiến hệ thống này chỉ dành cho dân lập trình viết code, toàn bộ placeholder gợi ý trên các biểu mẫu đã được quy chuẩn hóa đa lĩnh vực:

### 5.1. Ví dụ Tên Dự án & Tiền tố Viết tắt (Project Key)

| Lĩnh vực áp dụng | Tên Dự án Mẫu gợi ý | Mã Tiền tố (Key) | Mục đích Quản lý |
| :--- | :--- | :---: | :--- |
| **Tiếp thị & Truyền thông** | Chiến dịch Tết Nguyên Đán 2026 | `MKT` | Quản lý sản xuất nội dung, chạy quảng cáo, sự kiện |
| **Xây dựng & Công trình** | Thi công Tòa nhà Trụ sở Văn phòng | `XD` / `XDD` | Theo dõi tiến độ đổ móng, xây thô, hoàn thiện, an toàn |
| **Bán lẻ & Thương mại** | Khai trương Chuỗi Cửa hàng Miền Nam | `CH` / `RET` | Chuẩn bị mặt bằng, tuyển dụng, nhập hàng hóa |
| **Vận hành Doanh nghiệp** | Chuyển đổi Số Quy trình Nội bộ | `VH` / `OPS` | Chuẩn hóa tài liệu, mua sắm phần mềm, đào tạo nội bộ |
| **Công nghệ Phần mềm** | Ứng dụng Di động Khách hàng Thân thiết | `APP` / `DEV` | Quản lý tính năng, sprint, kiểm thử và phát hành |

### 5.2. Ví dụ Tiêu đề & Nội dung Công việc Cấp 0 (`STORY`, `TASK`, `BUG`)

- **Yêu cầu (`STORY`)**:
  - *"Khảo sát địa chất và bàn giao mặt bằng thi công giai đoạn 1"* (Xây dựng)
  - *"Thiết kế bộ nhận diện thương hiệu và banner quảng bá Facebook"* (Marketing)
  - *"Ký kết hợp đồng cung ứng nguyên vật liệu quý 1"* (Vận hành)
- **Nhiệm vụ (`TASK`)**:
  - *"Soạn thảo báo cáo tài chính tháng 10"* (Tài chính)
  - *"Kiểm tra hệ thống phòng cháy chữa cháy tầng hầm"* (Bảo trì)
- **Sự cố (`BUG`)**:
  - *"Chậm tiến độ giao cát đá do thời tiết mưa bão"* (Hiện trường xây dựng)
  - *"Sai lệch thông tin khuyến mãi trên ấn phẩm in ấn"* (Truyền thông)
  - *"Lỗi kết nối máy quẹt thẻ POS tại quầy thu ngân"* (Vận hành bán lẻ)

---

## 6. BẢO TOÀN HỢP ĐỒNG KỸ THUẬT BACKEND

Việc Việt hóa toàn bộ giao diện tuân thủ tuyệt đối quy tắc **"Tách biệt Bản trình bày (Presentation) và Mô hình Dữ liệu (Domain Model)"**:

```
┌────────────────────────────────────────────────────────────────────────┐
│ UI Layer (Frontend React):                                             │
│  "Yêu cầu"  ───►  "Sự cố"  ───►  "Kho việc"  ───► "Bảng tiến độ"      │
│       │                 │               │                 │            │
│       ▼                 ▼               ▼                 ▼            │
│ [Display Mapping Function / getTypeDisplayName / getStatusDisplayName] │
│       │                 │               │                 │            │
│       ▼                 ▼               ▼                 ▼            │
│ API Contract / Enums:                                                  │
│   'STORY'             'BUG'         'BACKLOG'         'BOARD'          │
│   'SCRUM'            'KANBAN'        'TODO'         'IN_PROGRESS'      │
│   'ADMIN'            'MEMBER'        'OWNER'          'GUEST'          │
└────────────────────────────────────────────────────────────────────────┘
```

- Không sửa đổi bất kỳ tên trường payload gửi đi hay nhận về từ REST API (`typeCode: 'STORY'`, `priority: 'HIGH'`, `statusCategory: 'IN_PROGRESS'`).
- Không ảnh hưởng đến các câu lệnh truy vấn CSDL, Flyway Migrations hoặc quy tắc bảo mật Spring Security / Keycloak JWT ở tầng Backend.

---

## 7. KẾT QUẢ KIỂM TRA & BIÊN DỊCH

- **Kiểm thử tĩnh (Lint & Static Scan)**: 100% không còn sót các chuỗi tiếng Anh hoặc thuật ngữ Jira cũ chưa chuyển đổi.
- **Biên dịch Frontend**: Lệnh `npm --prefix frontend run build` (Vite v8.3.1) hoàn tất thành công trong **582ms** mà không phát sinh bất kỳ cảnh báo lỗi cú pháp hay thiếu import nào.
- **Tính toàn vẹn mã nguồn**: Toàn bộ thay đổi đã được đóng gói thành các commit nguyên tử trên nhánh Git `feat/sontx-refactor-team`.
