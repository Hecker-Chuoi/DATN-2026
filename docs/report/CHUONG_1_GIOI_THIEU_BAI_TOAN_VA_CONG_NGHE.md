# CHƯƠNG 1. GIỚI THIỆU BÀI TOÁN VÀ CÔNG NGHỆ LIÊN QUAN

## 1.1. Tổng quan về hệ thống quản lý công việc

### 1.1.1. Giới thiệu bài toán

Trong các nhóm phát triển phần mềm và các đội dự án vừa và nhỏ, công việc thường được theo dõi bằng bảng tính, tin nhắn hoặc email. Cách làm này dẫn đến một số vấn đề quen thuộc:

- Không có nguồn dữ liệu chung: không ai biết chính xác ai đang làm việc gì, ở trạng thái nào, hạn chót khi nào.
- Khó đo tiến độ: không có biểu đồ burndown, velocity hay thống kê theo sprint để đánh giá nhóm đang nhanh hay chậm.
- Không có quy trình: việc có thể bị đóng mà không ai xác nhận; người ngoài dự án vẫn xem hoặc sửa được dữ liệu.
- Bỏ sót thông tin: được giao việc, được nhắc tên trong bình luận hay sắp đến hạn nhưng không được báo kịp thời.
- Khó ghi nhận đóng góp: cuối kỳ không có số liệu khách quan về công sức từng thành viên.

Đề tài xây dựng **hệ thống quản lý công việc theo mô hình Agile, lấy cảm hứng từ Jira**. Hệ thống tổ chức dữ liệu theo cấu trúc phẳng hai cấp **Workspace → Project → Work Item**, hỗ trợ Backlog, Sprint, Board Kanban, Workflow tùy biến, phân quyền theo dự án, thông báo thời gian thực và báo cáo tiến độ.

### 1.1.2. Khảo sát các hệ thống tương tự

| Hệ thống | Điểm mạnh | Hạn chế đối với nhóm nhỏ / đồ án |
|---|---|---|
| **Jira** | Workflow, phân quyền, báo cáo Agile đầy đủ | Cấu hình phức tạp, giao diện nặng, chi phí cao khi mở rộng |
| **Trello** | Đơn giản, trực quan (Kanban) | Thiếu Sprint, Workflow chặt chẽ và báo cáo Agile sâu |
| **ClickUp** | Nhiều tính năng, linh hoạt | Quá nhiều tùy chọn, khó làm chủ; dữ liệu nằm trên nền tảng bên ngoài |

**Điểm khác biệt của đề tài:**

- Giữ lõi Agile của Jira (Backlog, Sprint, Board, Workflow, phân quyền theo dự án) nhưng giao diện tối giản.
- Quy trình **"tạm hoàn thành"**: chỉ quản trị viên dự án mới được đóng công việc, người khác tạo yêu cầu chờ duyệt.
- **Quản lý đóng góp**: ghi sổ đóng góp và chụp snapshot theo sprint để có số liệu đánh giá từng thành viên.
- **Dự án mẫu**: tạo dự án từ mẫu, lưu dự án thành mẫu và nhân bản dự án.
- Thông báo trong ứng dụng và email theo mô hình hướng sự kiện, không mất sự kiện.
- Tích hợp **GitHub** để liên kết công việc với repository.

### 1.1.3. Mục tiêu và phạm vi

**Mục tiêu**

1. Xây dựng nền tảng quản lý công việc nhóm theo Agile, dùng được thực tế.
2. Bảo mật theo nguyên tắc mặc định từ chối: người dùng chỉ thấy và làm những gì được cấp quyền.
3. Cung cấp thông báo kịp thời và báo cáo tiến độ, đóng góp có số liệu.
4. Thiết kế theo kiến trúc Microservice, đóng gói bằng Docker và tự động triển khai (CI/CD).

**Phạm vi đã thực hiện**

- Xác thực: đăng ký có xác thực email, đăng nhập thường và Google, quên / đặt lại mật khẩu.
- Workspace, thành viên và lời mời (workspace và dự án).
- Dự án, công việc (Work Item), Workflow, Backlog (LexoRank), Sprint, Board.
- Phân quyền theo dự án; xác nhận hoàn thành công việc.
- Dự án mẫu và nhân bản dự án.
- Báo cáo tiến độ (burndown, velocity, CFD) và quản lý đóng góp.
- Thông báo trong ứng dụng (WebSocket) và email; tích hợp GitHub; trang quản trị.

**Phạm vi định hướng (chưa có mã nguồn, mới có thiết kế trong tài liệu)**

- Community Service (sáng kiến, bài viết cộng đồng).
- Trợ lý AI hỗ trợ quản lý công việc.

### 1.1.4. Đối tượng người dùng

| Đối tượng | Vai trò | Quyền chính |
|---|---|---|
| **Quản trị hệ thống** (realm role `ADMIN`) | Quản trị toàn hệ thống | Quản lý người dùng, vượt qua mọi kiểm tra quyền dự án |
| **Chủ / quản trị Workspace** (OWNER, ADMIN) | Quản lý không gian làm việc | Quản lý thành viên, mời người, tạo dự án |
| **Quản trị dự án** (Project Lead, ADMINISTRATOR) | Điều hành dự án | Cấu hình dự án, workflow, phân quyền, duyệt hoàn thành |
| **Thành viên dự án** (MEMBER) | Thực hiện công việc | Tạo, cập nhật công việc, bình luận, tham gia sprint |
| **Người xem** (VIEWER) | Theo dõi | Chỉ xem dự án |

## 1.2. Tìm hiểu về công nghệ sử dụng

Công nghệ dưới đây lấy trực tiếp từ mã nguồn (`pom.xml`, `package.json`, file docker-compose và workflow CI/CD).

### 1.2.1. Kiến trúc Microservice và API Gateway

Hệ thống chia thành các dịch vụ độc lập, mỗi dịch vụ sở hữu một nghiệp vụ và triển khai riêng:

| Dịch vụ | Nhiệm vụ |
|---|---|
| `api-gateway` | Điểm vào duy nhất, định tuyến, CORS, chuyển tiếp token |
| `identity-service` | Người dùng, đăng ký, đăng nhập, xác thực email |
| `work-service` | Dự án, công việc, workflow, sprint, board, báo cáo, đóng góp |
| `workspace-service` | Workspace, thành viên, lời mời |
| `notification-service` | Thông báo trong ứng dụng và email |
| `common` (thư viện dùng chung) | Hợp đồng sự kiện, tiện ích |

**Spring Cloud Gateway** làm API Gateway: nhận JWT từ header hoặc cookie rồi chuyển tiếp cho dịch vụ phía sau. Các dịch vụ gọi nhau đồng bộ bằng **OpenFeign**.

### 1.2.2. Frontend

- **React 19** và **Vite 8**, viết bằng JavaScript/JSX thuần.
- **React Router 7** (điều hướng), **Axios** (gọi API, tự làm mới token khi 401).
- **@dnd-kit** (kéo thả trên Board và Backlog), **Recharts** (biểu đồ báo cáo).
- **@stomp/stompjs** (nhận thông báo thời gian thực), **react-hot-toast**, **lucide-react** (icon).

### 1.2.3. Backend

- **Java 21**, **Spring Boot 3.5**, **Spring Cloud 2025.0**; quản lý dự án bằng **Maven** nhiều module.
- **Spring Web** (REST), **Spring Data JPA / Hibernate** (truy cập dữ liệu), **Spring Validation**, **Spring AOP** (kiểm tra quyền bằng annotation), **Spring Boot Actuator** (kiểm tra sức khỏe).
- **Spring Security** kết hợp **OAuth2 Resource Server** để xác thực JWT.
- **JUnit 5, Mockito, AssertJ** cho kiểm thử đơn vị.

### 1.2.4. Xác thực và ủy quyền

- **Keycloak 24** làm máy chủ định danh (realm `datn-realm`); giao thức **OAuth2 / OpenID Connect**, token dạng **JWT**; đăng nhập Google qua Keycloak.
- Mọi dịch vụ là OAuth2 Resource Server cùng kiểm tra một issuer; mã người dùng thống nhất là `sub` của Keycloak.
- Ủy quyền theo dự án: `PermissionKey` + `ProjectRole` + `PermissionScheme` + `RoleAssignment`, kiểm tra bằng annotation và AOP, mặc định từ chối.

### 1.2.5. Lưu trữ dữ liệu

- **PostgreSQL 15**: cơ sở dữ liệu quan hệ chính (dùng chung một database cho các dịch vụ).
- **Flyway**: quản lý phiên bản lược đồ (work, workspace, notification).
- **Redis 7**: bộ nhớ đệm; Pub/Sub và khóa job khi chạy nhiều instance.
- **MinIO** (tương thích S3): lưu tệp đính kèm của công việc.
- **Keycloak admin client**: đồng bộ tài khoản giữa Identity Service và Keycloak.

### 1.2.6. Bất đồng bộ và thời gian thực

- **Apache Kafka 3.8** (Spring Kafka): truyền sự kiện thông báo, kèm Dead Letter Topic khi xử lý lỗi lặp lại.
- **Transactional Outbox**: sự kiện được ghi cùng transaction với nghiệp vụ rồi mới đẩy lên Kafka, đảm bảo không mất sự kiện.
- **WebSocket / STOMP**: đẩy thông báo đến trình duyệt theo thời gian thực.
- **SMTP** (Spring Mail): gửi email xác thực, đặt lại mật khẩu, thông báo; môi trường phát triển dùng **Mailpit**.
- **GitHub REST API**: liên kết công việc với repository.

### 1.2.7. Container hóa và CI/CD

- **Docker** và **Docker Compose**: đóng gói 6 image (gateway, identity, work, workspace, notification, frontend) cùng hạ tầng; frontend được phục vụ qua **Nginx**.
- **GitHub Actions**: khi push vào `main` thì chạy test, build và đẩy image lên **Docker Hub**, sau đó SSH vào máy chủ (AWS EC2) chạy `docker compose` để triển khai và kiểm tra lại qua HTTPS.
