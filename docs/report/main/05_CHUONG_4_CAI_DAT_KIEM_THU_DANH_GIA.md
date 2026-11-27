# CHƯƠNG 4. CÀI ĐẶT, KIỂM THỬ VÀ ĐÁNH GIÁ HỆ THỐNG

Chương này trình bày cách cài đặt hệ thống ở môi trường phát triển và môi trường chạy thật, các mức kiểm thử đã thực hiện (kiểm thử hệ thống và kiểm thử đơn vị), phần minh họa giao diện và đánh giá tổng quan về giao diện, tính năng cùng các hạn chế còn lại.

> Lưu ý khi dựng bản Word: các dòng `> [HÌNH ...]` là vị trí cần chèn ảnh chụp màn hình. Cột "Kết quả" trong các bảng test case hệ thống ghi kết quả mong đợi theo quy tắc nghiệp vụ; những kịch bản đã được chạy và ghi nhận trong tài liệu của dự án được nêu rõ ở phần ghi chú.

## 4.1. Cài đặt hệ thống

### 4.1.1. Môi trường phát triển và công cụ sử dụng

**Bảng 4.1: Môi trường và công cụ**

| Hạng mục | Phiên bản / công cụ | Ghi chú |
| :--- | :--- | :--- |
| Ngôn ngữ backend | Java 21 | Cần JDK 21 vì cấu hình `java.version = 21` |
| Khung backend | Spring Boot 3.5.4, Spring Cloud 2025.0.0 | Maven nhiều module: `common`, `api-gateway`, `identity-service`, `work-service`, `workspace-service`, `notification-service` |
| Frontend | React 19, Vite 8, JavaScript thuần | Node `^20.19` hoặc `>=22.12` |
| Cơ sở dữ liệu | PostgreSQL 15 | Một cơ sở dữ liệu chung `datn_main_db` |
| Xác thực | Keycloak 24.0.1 | Realm `datn-realm` |
| Bộ nhớ đệm | Redis 7 | |
| Hàng đợi sự kiện | Apache Kafka 3.8.0 (KRaft) | |
| Lưu tệp | MinIO | Bucket `work-attachments` |
| Thư điện tử (phát triển) | Mailpit | SMTP 1025, giao diện web 8025 |
| Đóng gói | Docker, Docker Compose | |
| IDE | IntelliJ IDEA (backend), VS Code (frontend) | |
| Kiểm soát phiên bản | Git, GitHub | Quy trình CI/CD bằng GitHub Actions |

Cấu trúc kho mã gồm `backend/` (Maven nhiều module), `frontend/`, `infrastructure/` (các tệp Docker Compose, cấu hình Keycloak, tệp hướng dẫn, thư mục `prod/`) và `docs/` (tài liệu).

### 4.1.2. Cài đặt các thành phần hạ tầng bằng Docker Compose

Các thành phần hạ tầng (PostgreSQL, Keycloak, Redis, Kafka, MinIO, Mailpit, pgAdmin) được khai báo trong thư mục `infrastructure/`. Khởi động ở môi trường phát triển:

```bash
cd infrastructure
docker compose -f docker-compose.infra.yml up -d
```

Sau khi các container chạy, cần nạp cấu hình Keycloak. Tệp xuất realm (`infrastructure/keycloak/realm-export_08082026.json`) được nhập vào Keycloak qua giao diện quản trị hoặc API (`POST /admin/realms` với mã quản trị của realm master). Bước này tạo sẵn realm `datn-realm`, client `datn-backend` và các vai trò `ADMIN`, `USER`, `USER_CREATE`. Đăng nhập Google cần thêm cấu hình nhà cung cấp danh tính và địa chỉ chuyển hướng trong Google Console. Khóa bí mật của client `datn-backend` phải khớp nhau ở ba nơi: cấu hình identity-service, tệp compose và tệp xuất realm.

Thư điện tử ở môi trường phát triển được chuyển tới Mailpit nên có thể xem nội dung thư (xác thực, đặt lại mật khẩu, thông báo) tại cổng 8025 mà không gửi ra ngoài. Khi cần gửi thư thật, cấu hình SMTP bằng các biến môi trường `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` của notification-service.

Kafka có hai địa chỉ quảng bá: `localhost:9092` cho dịch vụ chạy trực tiếp trên máy và `kafka:29092` cho dịch vụ chạy trong container thuộc cùng mạng Docker. Dữ liệu Kafka nằm trên volume riêng nên tạo lại container không làm mất sự kiện.

### 4.1.3. Cài đặt và chạy ứng dụng Backend và Frontend

**Backend.** Mỗi dịch vụ là một ứng dụng Spring Boot. Có thể chạy từng dịch vụ trong IDE hoặc dùng tập lệnh `start-dev.ps1` để mở lần lượt cả năm dịch vụ (cổng 8081 gateway, 8082 identity, 8083 work, 8087 workspace, 9994 notification). Tập lệnh cài đặt module `common` trước vì các dịch vụ phụ thuộc vào hợp đồng sự kiện trong đó; thiếu bước này sẽ gặp lỗi `NoClassDefFoundError`. Dịch vụ work-service, workspace-service và notification-service tự áp dụng các migration Flyway khi khởi động; identity-service tự tạo bảng bằng JPA.

```bash
cd backend
mvn -pl work-service -am spring-boot:run      # ví dụ chạy work-service
```

Mỗi dịch vụ có Dockerfile riêng. Các Dockerfile dùng ngữ cảnh build là thư mục `backend/` và sao chép đủ các tệp `pom.xml` của mọi module (vì pom cha liệt kê toàn bộ module) rồi build nhiều giai đoạn. Thêm một module mới đòi hỏi cập nhật cả các Dockerfile còn lại.

**Frontend.**

```bash
cd frontend
npm install
npm run dev        # Vite, cổng 5173, proxy /identity, /work, /workspace, /notification tới gateway
npm run build
npm run lint
```

Máy chủ phát triển Vite chuyển tiếp các đường dẫn `/identity`, `/work`, `/workspace` và `/notification` (kể cả WebSocket) tới API Gateway. Cấu hình CORS của gateway chỉ cho phép nguồn `localhost:5173` và `localhost:3000`, nên cần mở frontend bằng `localhost` chứ không dùng `127.0.0.1`.

### 4.1.4. Triển khai production và quy trình CI/CD

Môi trường chạy thật gồm một máy chủ AWS EC2 (`t3.large`, 2 vCPU, 8 GB RAM) chạy 12 container bằng một tệp Docker Compose (`docker-compose.prod.yml`): sáu thành phần hạ tầng (PostgreSQL, Keycloak, Kafka, Redis, MinIO, Mailpit) và sáu ứng dụng (API Gateway, identity, work, workspace, notification, frontend). Nginx trên máy chủ nhận HTTPS (chứng chỉ Let's Encrypt) và chuyển vào frontend (cổng 3000) và Keycloak (cổng 8080) theo hai tên miền con. Mọi container có kiểm tra tình trạng, thứ tự khởi động theo `depends_on` và giới hạn bộ nhớ.

Quy trình CI/CD (`.github/workflows/ci-cd.yml`) có ba công việc:

**Bảng 4.2: Các công việc của quy trình CI/CD**

| Công việc | Khi nào chạy | Nội dung |
| :--- | :--- | :--- |
| Test backend | Mọi pull request và push `main` | JDK 21, `mvn verify` bỏ các `*ApplicationTests` (cần cơ sở dữ liệu thật); tải lên báo cáo kiểm thử |
| Lint và build frontend | Mọi pull request và push `main` | `npm ci`, `npm run lint`, `npm run build` |
| Build, push và deploy | Chỉ push `main` (hoặc chạy tay có chọn deploy) | Gắn thẻ ảnh bằng 7 ký tự đầu của git SHA; đăng nhập Docker Hub; build và đẩy 6 ảnh; gửi gói cấu hình qua SSH; `deploy.sh` kéo ảnh và `docker compose up -d --wait`; kiểm tra công khai qua HTTPS |

Một số quyết định thiết kế đáng chú ý:

- **Khóa SSH bị hạn chế.** Khóa riêng của GitHub Actions trên máy chủ được đặt `command="/home/deployer/deploy.sh"` nên chỉ chạy được tập lệnh triển khai, ba lệnh `deploy <tag>`, `rollback` và `check`; thẻ ảnh phải là chuỗi SHA hợp lệ.
- **Cấu hình đi cùng mã.** Mỗi lần triển khai gửi gói gồm tệp compose, cấu hình và realm theo đúng commit, nên tệp cấu hình và ảnh luôn đồng bộ. Mật khẩu nằm ở `secrets.env` trên máy chủ, không đi theo gói và không nằm trong kho mã.
- **Kiểm tra sau triển khai.** `deploy.sh` thực hiện smoke test các điểm kiểm tra sức khỏe của identity, work, workspace và trang chủ; nếu lỗi thì có thể quay về bản chạy tốt ngay trước bằng `rollback`. Giữ năm bản phát hành gần nhất.
- **Pull request không động tới bí mật.** PR chỉ chạy kiểm thử và build, không dùng secret, không deploy. Lượt chạy trên `main` không bị hủy giữa chừng.
- Công việc sao lưu PostgreSQL định kỳ do `backup-postgres.sh`.

Hướng dẫn từng bước nằm ở `docs/cicd/cac_buoc/`. Bản triển khai thật đã chạy được chuỗi đăng ký, xác thực email, đăng nhập và tạo Workspace qua HTTPS.

> [HÌNH 4.1: Sơ đồ quy trình CI/CD và kiến trúc máy chủ triển khai]

## 4.2. Kiểm thử hệ thống

### 4.2.1. Môi trường kiểm thử

Kiểm thử hệ thống thực hiện trên môi trường đủ các thành phần: PostgreSQL, Keycloak, Kafka, Redis, MinIO, Mailpit, đủ năm dịch vụ Spring và frontend, mở bằng ba tài khoản thuộc ba vai trò (quản trị viên dự án, thành viên, người xem) ở ba cửa sổ trình duyệt độc lập. Dữ liệu kiểm thử lấy từ kịch bản "Mộc Coffee", một dự án quán cà phê sinh ra từ mẫu F&B.

### 4.2.2. Các chức năng được kiểm thử

**Bảng 4.3: Thống kê nhóm chức năng và số test case hệ thống**

| Nhóm chức năng | Bảng test case | Số test case trình bày |
| :--- | :--- | :-: |
| Xác thực và phân quyền | Bảng 4.4 | 8 |
| Công việc, workflow, phân cấp | Bảng 4.5 | 8 |
| Sprint, Board và báo cáo | Bảng 4.6 | 8 |
| Hoàn thành, đóng góp và thông báo | Bảng 4.7 | 8 |
| Dự án mẫu | Bảng 4.8 | 4 |
| **Tổng** | | **36** |

### 4.2.3. Một số test case hệ thống chi tiết

**Bảng 4.4: Kịch bản kiểm thử xác thực và phân quyền**

| Mã | Tình huống | Các bước | Kết quả mong đợi |
| :-: | :--- | :--- | :--- |
| TC-A01 | Đăng ký và xác thực email | Đăng ký tài khoản; mở thư trong Mailpit; bấm liên kết | Tài khoản ở trạng thái `INACTIVE` cho đến khi xác thực; sau đó đăng nhập được |
| TC-A02 | Đăng nhập khi chưa xác thực | Đăng nhập bằng tài khoản `INACTIVE` | Bị từ chối với mã 403 và thông báo cần xác thực email |
| TC-A03 | Đăng nhập sai mật khẩu | Nhập sai mật khẩu | Báo lỗi, không cấp mã |
| TC-A04 | Quên và đặt lại mật khẩu | Gửi yêu cầu; mở liên kết trong thư; đặt mật khẩu mới | Đăng nhập được bằng mật khẩu mới; liên kết hết hạn sau 30 phút |
| TC-A05 | Tự làm mới mã | Cho mã truy cập hết hạn, gọi API | Giao diện tự dùng mã làm mới và lặp lại yêu cầu; người dùng không phải đăng nhập lại |
| TC-A06 | Người ngoài dự án truy cập | Người không có `role_assignments` mở dự án | 403; không lộ dữ liệu (mặc định từ chối) |
| TC-A07 | Người xem thao tác ghi | Người xem tạo công việc | 403 do thiếu `ISSUE_CREATE` |
| TC-A08 | Quản trị hệ thống | Tài khoản `ADMIN` truy cập dự án không phải thành viên | Được phép (bỏ qua kiểm tra quyền dự án) |

**Bảng 4.5: Kịch bản kiểm thử công việc, workflow, phân cấp**

| Mã | Tình huống | Các bước | Kết quả mong đợi |
| :-: | :--- | :--- | :--- |
| TC-W01 | Tạo công việc phân cấp | Tạo Epic, Task thuộc Epic, Sub-task thuộc Task | Tạo thành công; mã công việc tăng liên tục (`MOC-1`, `MOC-2`, …) |
| TC-W02 | Vi phạm phân cấp | Gắn Sub-task trực tiếp vào Epic | Bị từ chối kèm lý do (`InvalidHierarchyException`) |
| TC-W03 | Chuyển trạng thái hợp lệ | Chọn bước chuyển từ "Cần làm" sang "Đang làm" | Trạng thái đổi; nhật ký thay đổi có một dòng mới |
| TC-W04 | Không có bước chuyển | Kéo thẻ tới cột không có bước chuyển hợp lệ | Lỗi 400 giải thích quy trình không có đường đi |
| TC-W05 | Chặn bởi việc tiền đề | Chuyển công việc đang bị `BLOCKS` bởi việc chưa xong | Bị từ chối |
| TC-W06 | Chu trình phụ thuộc | A chặn B, B chặn C, rồi thêm C chặn A | Bị từ chối kèm chu trình `A -> B -> C -> A` |
| TC-W07 | Lan truyền ngược | Mở lại Sub-task khi Task cha đã DONE | Task cha tự mở lại sang "Đang làm" |
| TC-W08 | Hoàn thành dây chuyền | Chuyển Task sang DONE khi còn Sub-task, chọn hoàn thành luôn việc con | Các Sub-task chuyển DONE theo (khi quản trị viên thực hiện) |

**Bảng 4.6: Kịch bản kiểm thử Sprint, Board và báo cáo**

| Mã | Tình huống | Các bước | Kết quả mong đợi |
| :-: | :--- | :--- | :--- |
| TC-S01 | Một Sprint đang chạy | Bắt đầu Sprint khi dự án đã có Sprint `ACTIVE` | 409 kèm tên Sprint đang chạy |
| TC-S02 | Bắt đầu thiếu ngày | Bắt đầu Sprint không có ngày kết thúc | Bị từ chối |
| TC-S03 | Hoàn tất Sprint | Hoàn tất, chọn chuyển việc chưa xong về Backlog | Việc xong giữ nguyên Sprint; việc chưa xong về Backlog; Sub-task đi theo cha |
| TC-S04 | Đích không hợp lệ | Chọn Sprint đã đóng làm đích | Bị từ chối |
| TC-S05 | Giới hạn WIP | Kéo thẻ vào cột đã đạt WIP tối đa | Bị chặn; kéo thẻ ra khỏi cột vẫn được |
| TC-S06 | Burndown giữa Sprint | Mở báo cáo của Sprint đang chạy | Hiển thị đường lý tưởng, đường thực tế, vùng thay đổi phạm vi (dựng từ nhật ký) |
| TC-S07 | Báo cáo Sprint đã đóng | Đổi tên trạng thái rồi mở báo cáo Sprint đã đóng | Số liệu không đổi (đọc từ snapshot) |
| TC-S08 | Kéo thả giữ thứ tự | Kéo một thẻ lên giữa danh sách | Chỉ thẻ đó được cập nhật vị trí; các thẻ khác không đổi |

**Bảng 4.7: Kịch bản kiểm thử hoàn thành, đóng góp và thông báo**

| Mã | Tình huống | Các bước | Kết quả mong đợi |
| :-: | :--- | :--- | :--- |
| TC-N01 | Tạm hoàn thành | Thành viên chuyển công việc sang DONE | Trạng thái giữ nguyên; xuất hiện nhãn "Chờ duyệt HT"; quản trị viên nhận thông báo |
| TC-N02 | Xác nhận hoàn thành | Quản trị viên xác nhận | Công việc sang DONE; điểm ghi cho người thực hiện; người gửi nhận thông báo |
| TC-N03 | Từ chối hoàn thành | Quản trị viên từ chối kèm lý do | Trạng thái giữ nguyên; lý do hiện thành bình luận; thông báo "Cần xử lý" chuyển sang "Đã xử lý" |
| TC-N04 | Phạt trễ hạn | Hoàn thành việc 5 điểm trễ 2 ngày | Ghi 4,0 điểm, ghi chú "Trễ 2 ngày (-20%)" |
| TC-N05 | Mở lại việc đã tính điểm | Mở lại công việc đã hoàn thành | Thêm bản ghi `REOPENED` điểm âm bằng số đã cộng |
| TC-N06 | Thông báo thời gian thực | Giao việc cho một người đang mở ứng dụng | Số trên chuông tăng ngay không tải lại trang; bấm vào mở đúng công việc |
| TC-N07 | Nhắc tên | Gõ `@` trong bình luận, chọn người | Lưu dạng `@[Tên](sub)`; người được nhắc nhận thông báo |
| TC-N08 | Không mất sự kiện | Tắt notification-service, giao việc, bật lại | Thông báo vẫn đến sau khi dịch vụ chạy lại; không trùng |

**Bảng 4.8: Kịch bản kiểm thử dự án mẫu**

| Mã | Tình huống | Các bước | Kết quả mong đợi |
| :-: | :--- | :--- | :--- |
| TC-T01 | Tạo từ mẫu | Chọn mẫu F&B, tạo dự án `MOC` | Dự án có đủ trạng thái, quy trình, Sprint, công việc; ngày dời theo ngày bắt đầu mới; Sprint ở `FUTURE` |
| TC-T02 | Tùy chỉnh nhanh | Bỏ một công việc cha, đổi tên một công việc | Công việc con bị bỏ theo; tên mới xuất hiện đúng |
| TC-T03 | Không sao chép thành viên | So sánh thành viên dự án mới và dự án mẫu | Dự án mới chỉ có người tạo |
| TC-T04 | Kiểm tra toàn vẹn | Đánh dấu dự án thiếu nhóm trạng thái DONE làm mẫu | Báo lỗi mức ERROR, không cho dùng làm mẫu |

**Ghi chú về kết quả.** Kịch bản "Mộc Coffee" (tạo dự án từ mẫu F&B, 23 công việc sau khi bỏ ba công việc) và kịch bản dự án Blog (tạo loại công việc, trạng thái, bước chuyển, phân cấp, bộ lọc, ràng buộc `ALL_SUBTASKS_DONE`) đã được viết kèm các bước thao tác và kết quả mong đợi trong `docs/services/work_service/DEMO/` và `docs/services/work_service/test/`. Hệ thống thông báo có một đợt kiểm thử riêng: 33 kiểm tra tích hợp với Kafka và PostgreSQL thật (bảo mật REST/WebSocket, chống trùng, gộp, đóng "Cần xử lý", đẩy WebSocket, DLT), 52 kiểm tra giao diện đầu cuối bằng trình duyệt tự động qua gateway với ba người dùng, 24 kiểm tra cài đặt bật/tắt, và kiểm tra hai bản sao notification-service dùng chung Redis (trình duyệt nối vào một bản sao vẫn nhận đủ 12 trên 12 thông báo, kể cả phần bản sao kia xử lý; đối chứng tắt Redis chỉ nhận phần của bản sao mình). Tất cả được ghi nhận là đạt trong tài liệu `docs/services/notification/3_TRIEN_KHAI_VA_KIEM_THU.md`.

### 4.2.4. Kịch bản kiểm thử đa ngành

Để kiểm tra mô hình công việc có đủ tổng quát hay chỉ dành cho lập trình viên, nhóm đóng vai 20 người dùng từ 20 ngành nghề khác nhau (phần mềm, xây dựng, F&B, nông nghiệp, sản xuất, marketing, y tế, giáo dục, sự kiện, luật, thương mại điện tử, điện ảnh, nội thất, phi chính phủ, nghiên cứu, game, thời trang, kiểm toán, bất động sản, tuyển dụng), mỗi ngành dựng một dự án đại diện và đối chiếu từng thao tác với mã nguồn của work-service (`docs/scenario_test/WORK_SERVICE_MULTI_INDUSTRY_SCENARIO_TEST.md`). Mỗi ngành được chấm trên thang 10 theo tám tiêu chí: phân rã công việc, quy trình, đơn vị đo lường, phụ thuộc, nguồn lực, bảng công việc, mẫu dự án và thuật ngữ.

**Bảng 4.9: Tóm tắt kết quả khảo sát đa ngành**

| Mức phù hợp | Số ngành | Ngành tiêu biểu (điểm trên 10) |
| :--- | :-: | :--- |
| Rất tốt đến hoàn hảo (≥ 8) | 6 | Phần mềm 9,5; Game 9,2; Marketing 8,8; F&B 8,5; Thương mại điện tử 8,2; Sự kiện 8,0 |
| Khá tốt đến trung bình (5 đến dưới 8) | 11 | Thời trang 7,8; Nội thất 7,5; Tuyển dụng 7,5; Giáo dục 6,5; Bất động sản 6,5; Nông nghiệp 6,0; Điện ảnh 6,0; NGO 6,0; Y tế 5,5; R&D 5,5; Kiểm toán 5,0 |
| Thấp (dưới 5) | 3 | Luật 4,5; Sản xuất 4,0; Xây dựng 3,5 |

Khảo sát chỉ ra các điểm nghẽn: giới hạn cứng ba tầng phân cấp không đủ cho xây dựng, kiểm toán; chỉ có ba nhóm trạng thái (`TODO`, `IN_PROGRESS`, `DONE`) nên thiếu trạng thái hủy hoặc tạm dừng; một người thực hiện duy nhất trên một công việc; đơn vị đo chỉ có điểm câu chuyện và thời gian; không có tự động lập lịch theo đường găng; chưa có API quản lý trường tùy chỉnh. Cần lưu ý đây là đánh giá nội bộ do nhóm thực hiện bằng cách đối chiếu mã nguồn và mô phỏng nghiệp vụ, không phải khảo sát người dùng thật, và một số nhận định (như kéo thẻ phải có bước chuyển) phản ánh thiết kế có chủ đích để bảo vệ tính đúng của báo cáo.

## 4.3. Kiểm thử đơn vị

### 4.3.1. Các đơn vị và hàm được chọn kiểm thử

Các đơn vị được chọn theo hai tiêu chí: chứa quy tắc nghiệp vụ hoặc thuật toán dễ sai, và có thể kiểm thử cô lập không cần cơ sở dữ liệu hay Keycloak.

**Bảng 4.10: Các đơn vị được chọn kiểm thử**

| Dịch vụ | Đơn vị (lớp) | Lý do chọn |
| :--- | :--- | :--- |
| work-service | `ProjectMemberServiceImpl`, `ProjectPermissionGuard`, `ProjectAccessService`, `WorkspaceAccessGuard` | Quyết định và cưỡng chế quyền, rủi ro bảo mật cao nhất |
| work-service | `WorkflowEngineServiceImpl`, `WorkflowServiceImpl`, `WorkItemCompletionGate`, `WorkItemCompletionServiceImpl` | Máy trạng thái và cổng xác nhận hoàn thành |
| work-service | `StrictHierarchyValidator`, `DependencyCycleDetector`, `LexoRankService` | Thuật toán lõi |
| work-service | `SprintServiceImpl`, `BoardServiceImpl`, `BacklogServiceImpl`, `WIPLimitGuard`, `SwimlaneGrouper` | Vòng đời Sprint, Board |
| work-service | `ProjectCloneEngine`, `TemplateIntegrityValidator` | Nhân bản dự án |
| work-service | `OutboxRelay`, `WorkItemNotifier`, `ProjectNotifier`, `NotificationEventPublisher` | Phát sự kiện và outbox |
| work-service | `GitHubIntegrationServiceImpl`, `GitHubTokenCipher`, `GitHubOAuthStateService`, `GitHubWebhookSignatureValidator` | Tích hợp bên ngoài và bảo mật |
| workspace-service | `WorkspaceServiceImpl`, `WorkspaceMemberServiceImpl`, `WorkspaceLastOwnerGuard`, `CurrentUserResolver` | Quyền và bảo vệ chủ cuối cùng |
| notification-service | `NotificationCatalog`, `NotificationIngestService`, `NotificationPreferenceService`, `NotificationPushService`, `StompAuthChannelInterceptor`, `ClusterJobLock`, `RedisPushRelay` | Dựng nội dung, chống trùng, cài đặt, đẩy tin, xác thực WebSocket, chạy nhiều bản sao |

### 4.3.2. Phương pháp kiểm thử đơn vị

Kiểm thử đơn vị dùng JUnit 5, Mockito để giả lập phụ thuộc (kho dữ liệu, dịch vụ khác), AssertJ cho các phép kiểm tra và MockMvc dạng độc lập (standalone) cho lớp controller. Các bài kiểm thử không cần cơ sở dữ liệu, Keycloak hay Kafka, nên chạy nhanh và chạy được trong CI. Mỗi bài theo mẫu sắp xếp, thực hiện, kiểm tra (Arrange, Act, Assert) và có `@DisplayName` mô tả tình huống. Các nhóm tình huống thường được phủ: nhánh thành công, nhánh ngoại lệ (dữ liệu sai, thiếu quyền, tài nguyên không tồn tại), điều kiện biên và thứ tự thao tác. Các bài kiểm thử `*ApplicationTests` (nạp toàn bộ ngữ cảnh Spring) cần cơ sở dữ liệu thật nên bị loại khỏi lượt chạy của CI.

### 4.3.3. Một số test case chính

**Bảng 4.11: Một số bài kiểm thử đơn vị tiêu biểu**

| Lớp kiểm thử | Phương thức / tình huống | Kiểm tra |
| :--- | :--- | :--- |
| `DependencyCycleDetectorTest` | Tự chặn; chu trình hai nút; chu trình nhiều nút; đồ thị hình thoi hợp lệ; liên kết thông tin | Chu trình bị phát hiện; đồ thị không chu trình hợp lệ không bị chặn nhầm; liên kết chỉ mang thông tin bị bỏ qua |
| `LexoRankServiceTest` | 500 lần chèn giữa, 500 lần chèn đầu, 500 lần chèn cuối | Thứ tự từ điển luôn nghiêm ngặt sau nhiều lần chèn liên tiếp |
| `ProjectMemberServiceTest` | `getMyPermissions` cho trưởng dự án, người ngoài, quản trị hệ thống, thành viên | Trưởng dự án và quản trị hệ thống có đủ quyền; người ngoài không có quyền nào; thành viên nhận đúng quyền theo vai trò |
| `ProjectMemberServiceTest` | Thêm thành viên với vai trò của dự án khác; chuẩn hóa sang Keycloak id; xóa trưởng dự án; xóa quản trị viên cuối cùng | Bị từ chối đúng tình huống; mã lưu là `sub` |
| `WorkflowEngineServiceTest` | Ẩn bước chuyển khi `ASSIGNEE_ONLY` không thỏa; chặn bởi `BLOCKS`; chặn bởi `ALL_SUBTASKS_DONE`; hoàn thành dây chuyền; Epic tự giải quyết Story và Sub-task; lan truyền mở lại lên Story và Epic; tự bắt đầu việc cha; chặn mở lại Sprint đã đóng | Các nhánh của chuỗi thực hiện bước chuyển |
| `WorkflowEngineServiceTest` | "Tạm hoàn thành": thành viên chỉ tạo yêu cầu; hoãn hoàn thành việc con; Admin hoàn thành và ghi công theo ngày gửi; xác nhận bỏ qua Condition | Quy tắc BR-W03 đến BR-W08 |
| `SprintServiceTest` | Chặn Sprint không ở `FUTURE`; 409 khi đã có Sprint chạy; bắt buộc ngày kết thúc; chặn Sprint rỗng; thứ tự kế hoạch (BR-S15); đóng Sprint: chỉ chuyển việc chưa xong, chụp snapshot trước khi di chuyển, từ chối đích đã đóng, cascade xuống Sub-task | Vòng đời Sprint và thứ tự thao tác khi đóng |
| `BoardServiceTest`, `WIPLimitGuardTest` | Kéo thẻ vào cột DONE; vượt WIP tối đa; kéo ra cột đầy | Quy tắc kéo thả và WIP |
| `StrictHierarchyValidatorTest` | Các cặp cha–con hợp lệ và không hợp lệ | Ma trận phân cấp ba tầng |
| `ProjectPermissionGuardTest`, `ProjectPermissionGuardAspectTest` | Xác định mã dự án theo từng nguồn; không xác định được thì từ chối; quản trị hệ thống bỏ qua | Tính từ chối mặc định của aspect |
| `ProjectCloneEngineTest`, `TemplateIntegrityValidatorTest` | Ánh xạ ID, dời ngày, bỏ qua công việc cha thì bỏ con; các lỗi toàn vẹn | Nhân bản đúng và kiểm tra mẫu |
| `OutboxRelayTest` | Gửi thành công, lỗi môi giới (không tăng số lần thử), lỗi dữ liệu (đến `FAILED`) | Phân biệt hai loại lỗi của relay |
| `NotificationIngestServiceTest` | Bỏ người thực hiện, chống trùng bằng `dedup_key`, gộp, đóng "Cần xử lý" | Xử lý sự kiện tại dịch vụ thông báo |
| `NotificationCatalogTest` | Dựng nội dung các loại | Nội dung tiếng Việt đúng định dạng |
| `StompAuthChannelInterceptorTest` | Kết nối có và không có mã hợp lệ | Xác thực ở frame `CONNECT` |
| `WorkspaceLastOwnerGuardTest` | Rời, đổi vai trò, xóa người khi chỉ còn một chủ | Workspace không mất chủ cuối cùng |
| `GitHubTokenCipherTest`, `GitHubWebhookSignatureValidatorTest`, `GitHubOAuthStateServiceTest` | Mã hóa và giải mã; chữ ký đúng và sai; `state` sai người dùng hoặc hết hạn | Bảo mật tích hợp GitHub |

### 4.3.4. Ví dụ kịch bản kiểm thử mẫu

**Ví dụ 1: Hoàn tất Sprint chụp snapshot trước khi di chuyển công việc** (`SprintServiceTest.completeSprint_snapshotsBeforeMoving`)

- *Mục đích:* bảo đảm thứ tự thao tác: số liệu phải được chụp khi các công việc chưa bị di chuyển.
- *Chuẩn bị:* Sprint `ACTIVE`, hai công việc (một DONE, một chưa xong), các kho dữ liệu được giả lập; engine chụp snapshot và hàm di chuyển được theo dõi bằng Mockito.
- *Thực hiện:* gọi `completeSprint(sprintId, đích = Backlog, userId)`.
- *Kiểm tra:* dùng `InOrder` để xác minh lời gọi `generateFinalSnapshot` xảy ra **trước** lệnh lưu việc đã chuyển Sprint; Sprint chuyển sang `CLOSED`.

**Ví dụ 2: Phát hiện chu trình nhiều nút** (`DependencyCycleDetectorTest.testMultiNodeCycle`)

- *Chuẩn bị:* các liên kết `A→B`, `B→C`, `C→D` đã có trong đồ thị.
- *Thực hiện:* gọi `validateNoCycle(D, A, BLOCKS)`.
- *Kiểm tra:* ném `CyclicDependencyException`, đường đi chu trình chứa cả bốn nút.

**Ví dụ 3: Quyền của người ngoài dự án** (`ProjectMemberServiceTest.testGetMyPermissions_AsOutsider_EmptyPermissions`)

- *Chuẩn bị:* dự án tồn tại, người dùng không phải trưởng dự án, người tạo hay chủ Workspace, và không có `role_assignments`.
- *Thực hiện:* gọi `getMyPermissions`.
- *Kiểm tra:* trả về tập quyền rỗng, thể hiện nguyên tắc từ chối mặc định.

### 4.3.5. Kết quả kiểm thử đơn vị

Lượt chạy CI trên JDK 21 (ghi trong `.github/workflows/ci-cd.yml`) cho kết quả toàn bộ bài kiểm thử đơn vị đều đạt:

**Bảng 4.12: Tổng hợp kết quả kiểm thử đơn vị**

| Dịch vụ | Số tệp kiểm thử | Số bài kiểm thử | Kết quả |
| :--- | :-: | :-: | :--- |
| work-service | 47 | 344 | Đạt |
| workspace-service | 5 | 48 | Đạt |
| notification-service | 15 | 148 | Đạt |
| **Tổng** | **67** | **540** | **Đạt** |

(Số tệp gồm cả vài tệp hỗ trợ như dữ liệu mẫu, không chứa bài kiểm thử.)

Số bài của notification-service lớn hơn số phương thức `@Test` do có các bài tham số hóa chạy nhiều bộ dữ liệu. identity-service chỉ có một bài kiểm thử nạp ngữ cảnh (`IdentityServiceApplicationTests`) cần cơ sở dữ liệu thật nên không nằm trong lượt chạy của CI; phần nghiệp vụ của dịch vụ này được kiểm thử ở mức hệ thống. Ngoài kiểm thử đơn vị, dự án còn dùng kiểm thử tích hợp thủ công cho thông báo như mô tả ở mục 4.2.3.

### 4.3.6. Độ phủ kiểm thử

Dự án hiện chưa cấu hình công cụ đo độ phủ mã (như JaCoCo) nên chưa có phần trăm dòng mã được phủ. Thay vào đó, độ phủ được đánh giá theo thành phần: các lớp chứa quy tắc nghiệp vụ và thuật toán lõi liệt kê ở Bảng 4.10 đều có bài kiểm thử riêng; các lớp controller được phủ bằng MockMvc độc lập ở phạm vi chọn lọc (thành viên dự án, workflow, chuyển trạng thái, thông báo). Khoảng trống còn lại gồm lớp truy cập dữ liệu và các câu truy vấn JPA phức tạp (chỉ có bài kiểm thử đơn vị mức kho dữ liệu ở phạm vi nhỏ), tầng frontend (chưa có kiểm thử tự động) và dịch vụ định danh. Thêm JaCoCo vào Maven để đưa chỉ số độ phủ vào CI là việc nên làm tiếp (xem mục 4.5.4).

## 4.4. Demo và minh họa

Các hình dưới đây chụp từ ứng dụng đang chạy; mỗi mục nêu nội dung màn hình cần chụp để khớp với phần phân tích ở Chương 3.

### 4.4.1. Giao diện đăng nhập, đăng ký, Workspace

> [HÌNH 4.2: Trang giới thiệu (`LandingPage`)]
> [HÌNH 4.3: Đăng ký và đăng nhập (kèm nút đăng nhập Google)]
> [HÌNH 4.4: Thư xác thực email trong Mailpit]
> [HÌNH 4.5: Danh sách Workspace và lời mời Workspace]
> [HÌNH 4.6: Thành viên Workspace và cài đặt Workspace]

### 4.4.2. Giao diện dự án: Backlog, Board, chi tiết công việc, Workflow

> [HÌNH 4.7: Danh sách dự án và thư viện mẫu, xem trước mẫu (bốn tab)]
> [HÌNH 4.8: Backlog với Sprint đang chạy, Sprint dự kiến, khu giai đoạn đã hoàn tất]
> [HÌNH 4.9: Hộp thoại hoàn tất Sprint (chọn đích cho việc chưa xong)]
> [HÌNH 4.10: Board Scrum/Kanban với WIP và swimlane]
> [HÌNH 4.11: Chi tiết công việc: menu trạng thái, liên kết, bình luận, tệp đính kèm, lịch sử]
> [HÌNH 4.12: Khung "Tạm hoàn thành – chờ Admin xác nhận" và nút "Chờ xác nhận HT (n)"]
> [HÌNH 4.13: Trang cấu hình quy trình (trạng thái, bước chuyển, điều kiện)]

### 4.4.3. Giao diện báo cáo, đóng góp, thành viên và thông báo

> [HÌNH 4.14: Báo cáo Burndown]
> [HÌNH 4.15: Báo cáo Velocity và CFD]
> [HÌNH 4.16: Báo cáo đóng góp theo thành viên và sổ ghi công]
> [HÌNH 4.17: Thành viên dự án và ma trận quyền theo vai trò]
> [HÌNH 4.18: Chuông thông báo và bảng thông báo (tab Cần xử lý)]
> [HÌNH 4.19: Trang thông báo và cài đặt thông báo, tắt tiếng dự án]
> [HÌNH 4.20: Trang Lời mời (gộp lời mời dự án và Workspace)]
> [HÌNH 4.21: Tích hợp GitHub: kết nối kho, liên kết pull request và Issue vào công việc]

### 4.4.4. Giao diện quản trị hệ thống

> [HÌNH 4.22: Trang quản trị: danh sách người dùng, gán vai trò, đặt lại mật khẩu]

## 4.5. Đánh giá hệ thống

### 4.5.1. Đánh giá giao diện

Giao diện theo phong cách tối giản kiểu Linear với bộ quy tắc thống nhất trong `frontend/DESIGN.md`: ít chữ, mỗi dòng một ý, một màu nhấn duy nhất, không đổ bóng và gradient, thao tác nguy hiểm xác nhận ngay trong dòng. Ưu điểm là sự nhất quán giữa các màn hình và giao diện gọn, ít gây rối với người dùng không chuyên về quản lý dự án. Thông tin quyền được phản ánh lên giao diện: nút không được phép bị ẩn hoặc làm mờ kèm lý do, và cột Board không thể tới được bị vô hiệu hóa trước khi kéo. Thao tác kéo thả và công tắc dùng cập nhật lạc quan nên phản hồi ngay, rồi hoàn lại nếu máy chủ từ chối. Thuật ngữ được Việt hóa theo bộ quy ước chung (ví dụ Sprint hiển thị là "Giai đoạn" hoặc "Chu kỳ" tùy ngữ cảnh) để người ngoài ngành phần mềm dễ làm quen.

Điểm cần cải thiện: giao diện chưa có kiểm thử tự động; kênh thời gian thực cho riêng Board (nhiều người cùng thấy thẻ di chuyển ngay) mới ở mức thiết kế, hiện chỉ thông báo là thời gian thực.

### 4.5.2. Đánh giá tính năng

**Bảng 4.13: Đối chiếu mục tiêu và kết quả**

| Mục tiêu | Mức đạt | Căn cứ |
| :--- | :-: | :--- |
| Xác thực tập trung, đăng nhập Google, xác thực email | Đạt | Keycloak, `AccountVerificationService`; chạy được trên bản triển khai thật |
| Phân quyền mặc định từ chối theo từng dự án | Đạt | `ProjectPermissionGuard`, ma trận quyền động; test đơn vị cho mọi nhánh |
| Quản lý công việc ba tầng, workflow cấu hình được, phụ thuộc không chu trình | Đạt | Mục 2.3; test đơn vị |
| Sprint, Backlog, Board, WIP, swimlane | Đạt | Mục 2.3.8 |
| Báo cáo Burndown, Velocity, CFD không đổi theo thời gian | Đạt | Snapshot và phát lại nhật ký |
| Xác nhận hoàn thành và đóng góp khách quan | Đạt | Cổng xác nhận, sổ ghi công chỉ thêm, phạt trễ hạn |
| Thông báo thời gian thực, không mất, không sai | Đạt | Outbox, Kafka, WebSocket; kiểm thử tích hợp và hai bản sao |
| Dự án mẫu, nhân bản, kiểm tra toàn vẹn | Đạt | Mục 2.5 |
| Tích hợp GitHub | Đạt (cơ bản) | OAuth, webhook, đồng bộ Issue, liên kết pull request |
| Triển khai tự động, đóng gói Docker | Đạt | Quy trình CI/CD, bản chạy thật có HTTPS |
| Trợ lý AI hỗ trợ lập kế hoạch | Chưa làm | Chỉ có tài liệu đề xuất (`docs/services/ai_service/`) |
| Dịch vụ cộng đồng | Chưa làm | Chỉ có tài liệu thiết kế |

Về chất lượng kỹ thuật, ba điểm đáng chú ý là: (1) các quy tắc quan trọng được đưa vào đúng tầng cưỡng chế (aspect, máy trạng thái, ràng buộc cơ sở dữ liệu như chỉ mục duy nhất một Sprint đang chạy) thay vì chỉ dựa vào giao diện; (2) tính nhất quán dữ liệu–sự kiện nhờ outbox; (3) số liệu báo cáo ổn định nhờ snapshot và phát lại nhật ký.

### 4.5.3. So sánh với Jira, Trello, ClickUp

**Bảng 4.14: So sánh với các công cụ quản lý công việc phổ biến**

| Tiêu chí | Jira | Trello | ClickUp | Hệ thống của đồ án |
| :--- | :--- | :--- | :--- | :--- |
| Workflow và phân quyền chi tiết | Rất mạnh, cấu hình phức tạp | Hạn chế | Linh hoạt | Có workflow, ma trận quyền theo dự án, cấu hình đơn giản |
| Sprint, Backlog, báo cáo Agile | Đầy đủ | Thiếu (cần tiện ích) | Có | Có: Burndown, Velocity, CFD |
| Xác nhận hoàn thành bởi quản trị viên | Phải tự cấu hình bằng điều kiện bước chuyển | Không có | Có thể qua tự động hóa | Mặc định có, gắn với điểm đóng góp |
| Sổ ghi đóng góp, phạt trễ hạn | Không có sẵn | Không | Hạn chế | Có |
| Dự án mẫu kèm dời ngày | Có (mẫu dự án) | Mẫu bảng | Mẫu | Có, kèm kiểm tra toàn vẹn |
| Thông báo thời gian thực và email | Có | Có | Có | Có, 53 loại, gộp và tắt tiếng |
| Triển khai trên hạ tầng riêng | Phiên bản tự lưu trữ (tính phí) | Không | Không | Có, mã nguồn nằm trong tay người dùng |
| Trợ lý AI | Có (trả phí) | Có | Có | Mới ở mức thiết kế |
| Mức hoàn thiện, hệ sinh thái | Rất cao | Rất cao | Cao | Phiên bản đồ án, chưa có tiện ích mở rộng |

Hệ thống không nhằm thay thế các sản phẩm thương mại mà tập trung vào một số khía cạnh: đơn giản hơn Jira, đủ chặt chẽ về phân quyền và hoàn thành, có đo lường đóng góp, và có thể tự vận hành.

### 4.5.4. Hạn chế

- **Mô hình công việc.** Giới hạn cứng ba tầng phân cấp, chỉ ba nhóm trạng thái, một người thực hiện duy nhất và đơn vị đo chỉ là điểm hoặc thời gian khiến một số ngành (xây dựng, sản xuất, kiểm toán, luật) khó dùng; chưa có tự động lập lịch theo đường găng; chưa có giao diện quản lý trường tùy chỉnh dù bảng dữ liệu đã có.
- **Thời gian thực.** Thông báo là thời gian thực, nhưng Board và danh sách công việc chưa tự cập nhật khi người khác thay đổi (đã có kế hoạch riêng ở `docs/services/work_service/realtime/`).
- **Phát hiện chu trình.** `DependencyCycleDetector` dựng đồ thị từ toàn bộ liên kết cùng loại trong cơ sở dữ liệu; với dữ liệu lớn nên giới hạn theo dự án.
- **Dữ liệu dùng chung một cơ sở dữ liệu.** Các dịch vụ chia sẻ một PostgreSQL; tách hẳn mỗi dịch vụ một cơ sở dữ liệu sẽ giảm ràng buộc nhưng đổi lại phải xử lý nhất quán phân tán.
- **Định danh.** Việc dịch vụ định danh có thể ghi đè `keycloak_id` khi trùng email gây dữ liệu mồ côi; cần ràng buộc chặt hơn.
- **Kiểm thử.** Chưa đo độ phủ mã; chưa có kiểm thử tự động cho frontend và kiểm thử đầu cuối trong CI; kiểm thử tích hợp thông báo đang chạy thủ công.
- **Hạ tầng.** Triển khai trên một máy chủ, chưa có tự mở rộng hoặc chịu lỗi ở mức máy chủ; Mailpit đang là máy chủ thư của bản triển khai thử, cần đổi sang SMTP thật khi dùng chính thức.
- **Phạm vi.** Trợ lý AI và dịch vụ cộng đồng chưa được xây dựng.
