# MỤC LỤC BÁO CÁO ĐỒ ÁN TỐT NGHIỆP

**Đề tài:** Xây dựng hệ thống quản lý dự án và công việc DevCentral (DATN-2026)

> Cấu trúc bám theo báo cáo tham khảo (`docs/report/tham_khao`): phần đầu → 4 chương → kết luận → tài liệu tham khảo → phụ lục. Nội dung mỗi mục lấy từ mã nguồn và tài liệu trong `docs/` của dự án, mã nguồn là căn cứ cuối cùng khi có chỗ lệch.
>
> Bảng đối chiếu cấu trúc với báo cáo tham khảo:
>
> | Báo cáo tham khảo | Báo cáo này |
> | :--- | :--- |
> | Chương 1: Bài toán và công nghệ liên quan | Chương 1: Giới thiệu bài toán và công nghệ liên quan |
> | Chương 2: Giải pháp đánh giá bài tập UML (phần thuật toán lõi) | Chương 2: Xây dựng các giải pháp kỹ thuật cốt lõi (phân quyền, quy trình, chu kỳ và báo cáo, hoàn thành, thông báo, dự án mẫu) |
> | Chương 3: Đặc tả, phân tích, thiết kế | Chương 3: Đặc tả, phân tích, thiết kế hệ thống |
> | Chương 4: Cài đặt, kiểm thử, đánh giá | Chương 4: Cài đặt, kiểm thử và đánh giá hệ thống |

## Danh sách tài liệu (mỗi phần một file, đặt trong `docs/report/main/`)

| STT | File | Nội dung |
| :-: | :--- | :--- |
| 0 | `00_MUC_LUC.md` | Mục lục này |
| 1 | `01_PHAN_MO_DAU.md` | Trang bìa, lời cảm ơn, danh mục từ viết tắt, danh mục bảng biểu, danh mục hình ảnh |
| 2 | `02_CHUONG_1_GIOI_THIEU_BAI_TOAN_VA_CONG_NGHE.md` | Chương 1 |
| 3 | `03_CHUONG_2_GIAI_PHAP_KY_THUAT_CO_BAN.md` | Chương 2 |
| 4 | `04_CHUONG_3_DAC_TA_PHAN_TICH_THIET_KE.md` | Chương 3 |
| 5 | `05_CHUONG_4_CAI_DAT_KIEM_THU_DANH_GIA.md` | Chương 4 |
| 6 | `06_KET_LUAN_VA_TAI_LIEU_THAM_KHAO.md` | Kết luận, hướng phát triển, tài liệu tham khảo |
| 7 | `07_PHU_LUC.md` | Phụ lục A, B, C |

---

## PHẦN MỞ ĐẦU (đánh số trang La Mã)

- Trang bìa, trang phụ bìa (tên đề tài, giảng viên hướng dẫn, thông tin sinh viên)
- Lời cảm ơn
- Mục lục
- Danh mục các từ viết tắt và thuật ngữ
- Danh mục bảng biểu
- Danh mục hình ảnh

---

## CHƯƠNG 1. GIỚI THIỆU BÀI TOÁN VÀ CÔNG NGHỆ LIÊN QUAN

### 1.1. Tổng quan về hệ thống quản lý công việc
- 1.1.1. Giới thiệu về bài toán
- 1.1.2. Mục tiêu và phạm vi bài toán
- 1.1.3. Đối tượng người dùng

### 1.2. Tìm hiểu về công nghệ sử dụng
- 1.2.1. Kiến trúc Microservice và cổng giao tiếp API
- 1.2.2. Spring Boot
- 1.2.3. React và Vite
- 1.2.4. Keycloak, OAuth2, OpenID Connect và JWT
- 1.2.5. PostgreSQL và Flyway
- 1.2.6. Redis
- 1.2.7. MinIO
- 1.2.8. Apache Kafka và mẫu thiết kế Outbox
- 1.2.9. WebSocket, STOMP và thư điện tử
- 1.2.10. Docker và quy trình triển khai tự động

---

## CHƯƠNG 2. XÂY DỰNG CÁC GIẢI PHÁP KỸ THUẬT CỐT LÕI

### 2.1. Các bài toán kỹ thuật chính
- 2.1.1. Xác thực và phân quyền
- 2.1.2. Quản trị dự án và quản lý công việc (trọng tâm)
- 2.1.3. Quản lý đóng góp và quản lý tiến độ
- 2.1.4. Mẫu dự án
- 2.1.5. Thông báo
- 2.1.6. Tích hợp GitHub (cho dự án phần mềm)

### 2.2. Giải pháp xác thực và phân quyền
- 2.2.1. Xác thực tập trung
- 2.2.2. Phân quyền trong không gian làm việc
- 2.2.3. Phân quyền trong dự án
- 2.2.4. Lời mời tham gia

### 2.3. Giải pháp quản trị dự án và quản lý công việc (trọng tâm)
- 2.3.1. Mô hình dự án và bộ sinh mã công việc
- 2.3.2. Loại công việc và phân cấp ba tầng
- 2.3.3. Trạng thái công việc
- 2.3.4. Quy trình, bước chuyển và máy trạng thái
- 2.3.5. Liên kết giữa các công việc
- 2.3.6. Tùy biến theo ngành: trạng thái, loại công việc và trường tùy chỉnh
- 2.3.7. Thuộc tính của công việc, tìm kiếm và sắp xếp
- 2.3.8. Giai đoạn, kho việc và bảng tiến độ

### 2.4. Giải pháp quản lý đóng góp và quản lý tiến độ

### 2.5. Giải pháp mẫu dự án

### 2.6. Giải pháp thông báo

### 2.7. Giải pháp tích hợp GitHub

### 2.8. Tổng kết chương

---

## CHƯƠNG 3. ĐẶC TẢ, PHÂN TÍCH, THIẾT KẾ HỆ THỐNG

### 3.1. Đặc tả hệ thống
- 3.1.1. Yêu cầu chức năng
- 3.1.2. Yêu cầu phi chức năng
- 3.1.3. Xác định và mô tả các Actor
- 3.1.4. Xác định và mô tả các Use Case
- 3.1.5. Một số Use Case tính năng chính

### 3.2. Phân tích hệ thống
- 3.2.1. Phân tích luồng nghiệp vụ chính

### 3.3. Thiết kế hệ thống
- 3.3.1. Thiết kế kiến trúc hệ thống
- 3.3.2. Sơ đồ lớp pha thiết kế
- 3.3.3. Thiết kế cơ sở dữ liệu toàn hệ thống
- 3.3.4. Thiết kế giao diện lập trình
- 3.3.5. Thiết kế giao diện người dùng

### 3.4. Thiết kế các module chuyên biệt
- 3.4.1. Module thông báo
- 3.4.2. Module báo cáo tiến độ và đóng góp
- 3.4.3. Module dự án mẫu
- 3.4.4. Module tích hợp GitHub

---

## CHƯƠNG 4. CÀI ĐẶT, KIỂM THỬ VÀ ĐÁNH GIÁ HỆ THỐNG

### 4.1. Cài đặt hệ thống
- 4.1.1. Môi trường phát triển và công cụ sử dụng
- 4.1.2. Cài đặt các thành phần hạ tầng bằng Docker Compose
- 4.1.3. Cài đặt và chạy ứng dụng Backend và Frontend
- 4.1.4. Triển khai production và quy trình CI/CD (GitHub Actions, Docker Hub, AWS EC2)

### 4.2. Kiểm thử hệ thống
- 4.2.1. Môi trường kiểm thử
- 4.2.2. Các chức năng được kiểm thử
- 4.2.3. Một số test case hệ thống chi tiết (xác thực/phân quyền; quản lý công việc; Sprint, Board; hoàn thành và thông báo)
- 4.2.4. Kịch bản kiểm thử đa ngành (quán cà phê, blog, phần mềm)

### 4.3. Kiểm thử đơn vị
- 4.3.1. Các đơn vị và hàm được chọn kiểm thử
- 4.3.2. Phương pháp kiểm thử đơn vị
- 4.3.3. Một số test case chính
- 4.3.4. Ví dụ kịch bản kiểm thử mẫu
- 4.3.5. Kết quả kiểm thử đơn vị
- 4.3.6. Độ phủ kiểm thử

### 4.4. Demo và minh họa
- 4.4.1. Giao diện đăng nhập, đăng ký, Workspace
- 4.4.2. Giao diện dự án: Backlog, Board, chi tiết công việc, Workflow
- 4.4.3. Giao diện báo cáo, đóng góp, thành viên và thông báo
- 4.4.4. Giao diện quản trị hệ thống

### 4.5. Đánh giá hệ thống
- 4.5.1. Đánh giá giao diện
- 4.5.2. Đánh giá tính năng
- 4.5.3. So sánh với Jira, Trello, ClickUp
- 4.5.4. Hạn chế

---

## KẾT LUẬN
- Kết quả đạt được
- Hạn chế
- Hướng phát triển (trợ lý AI, dịch vụ cộng đồng, thời gian thực trên Board)

## TÀI LIỆU THAM KHẢO

## PHỤ LỤC
- Phụ lục A: Tài liệu API (tóm tắt theo dịch vụ)
- Phụ lục B: Danh mục 53 loại thông báo và ma trận quyền mặc định của dự án
- Phụ lục C: Phân công công việc của các thành viên

---

## Quy ước trình bày chung
- Đánh số mục tối đa 3 cấp (x.y.z); bảng đặt tên "Bảng x.y", hình đặt tên "Hình x.y" theo từng chương, có chú thích bên dưới/bên trên.
- Chỗ cần ảnh chụp màn hình hoặc sơ đồ UML được đánh dấu bằng dòng `> [HÌNH x.y: ...]` để bạn chèn khi dựng bản Word.
- Trích dẫn tài liệu theo dạng [n], danh mục ở `06_KET_LUAN_VA_TAI_LIEU_THAM_KHAO.md`, đánh số liên tục từ Chương 1.
- Thuật ngữ tiếng Việt thống nhất theo `docs/services/language/`.
