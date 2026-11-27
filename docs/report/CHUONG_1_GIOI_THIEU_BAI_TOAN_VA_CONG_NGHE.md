# CHƯƠNG 1. GIỚI THIỆU BÀI TOÁN VÀ CÔNG NGHỆ LIÊN QUAN

Chương này trình bày bối cảnh và bài toán mà đồ án hướng tới giải quyết, bao gồm mục tiêu, phạm vi và các nhóm người dùng của hệ thống quản lý công việc. Phần tiếp theo giới thiệu các công nghệ được sử dụng để xây dựng hệ thống, được liệt kê trực tiếp từ mã nguồn của dự án cùng lý do lựa chọn từng nhóm công nghệ.

## 1.1. Tổng quan về hệ thống quản lý công việc

### 1.1.1. Giới thiệu về bài toán

Dù làm trong lĩnh vực nào, từ phát triển phần mềm, thiết kế, tiếp thị, giáo dục cho đến sản xuất hay nghiên cứu, mọi nhóm làm việc đều phải đối mặt với cùng một nhu cầu: biết ai đang làm việc gì, đến đâu và khi nào phải xong. Tuy vậy, phần lớn các nhóm vừa và nhỏ vẫn theo dõi công việc bằng bảng tính, tin nhắn hoặc thư điện tử. Cách làm này khiến thông tin bị phân tán, không ai nắm chính xác người phụ trách, tình trạng và hạn chót của từng việc. Người quản lý khó đánh giá tiến độ thực tế của nhóm, công việc có thể bị coi là xong mà không ai xác nhận, thành viên dễ bỏ sót việc được giao hoặc lời nhắc tên trong bình luận, và đến cuối kỳ nhóm cũng không có căn cứ khách quan để ghi nhận đóng góp của từng người.

Các công cụ như Jira, Trello hay ClickUp đã giải quyết bài toán này ở nhiều mức độ khác nhau. Jira có workflow, phân quyền và báo cáo Agile đầy đủ nhưng cấu hình phức tạp và chi phí cao khi mở rộng. Trello đơn giản và trực quan nhờ bảng Kanban nhưng thiếu Sprint, workflow chặt chẽ và báo cáo chuyên sâu. ClickUp có nhiều tính năng linh hoạt nhưng khó làm chủ với nhóm nhỏ, đồng thời dữ liệu nằm hoàn toàn trên nền tảng bên ngoài.

Từ thực tế đó, đồ án xây dựng một hệ thống quản lý công việc đơn giản, dễ làm quen và dùng được cho nhiều ngành nghề khác nhau, thay vì chỉ dành riêng cho lập trình viên. Hệ thống học hỏi cách tổ chức công việc của Jira nhưng lược bỏ những cấu hình rườm rà: dữ liệu chỉ chia hai cấp là không gian làm việc và dự án, trong mỗi dự án là các đầu việc được sắp xếp trên bảng theo cột trạng thái, có thể gom thành từng chặng ngắn để theo dõi tiến độ. Quy trình xử lý, trạng thái và quyền hạn của từng người đều có thể điều chỉnh để phù hợp với đặc thù của mỗi nhóm. Hệ thống còn có một số điểm riêng: công việc chỉ được đóng sau khi quản trị viên dự án xác nhận, đóng góp của từng thành viên được ghi sổ để đánh giá công bằng, người dùng có thể lập dự án mới từ dự án mẫu có sẵn, và mọi thay đổi quan trọng đều được báo ngay cho người liên quan trong ứng dụng và qua thư điện tử.

Bên cạnh đó, đồ án hướng tới việc đưa trợ lý trí tuệ nhân tạo vào hỗ trợ người dùng trong công việc hằng ngày, chẳng hạn gợi ý chia nhỏ một đầu việc lớn, soạn mô tả công việc, tóm tắt tiến độ của một chặng làm việc hay cảnh báo những việc có nguy cơ trễ hạn. Nhờ vậy, người dùng không chuyên về quản lý vẫn có thể lập kế hoạch và theo dõi dự án hiệu quả. Phần trợ lý này hiện mới ở mức thiết kế, chưa được xây dựng trong phiên bản hiện tại của hệ thống.

### 1.1.2. Mục tiêu và phạm vi bài toán

Mục tiêu của đề tài là xây dựng một nền tảng quản lý công việc nhóm theo Agile có thể sử dụng trong thực tế. Hệ thống phải bảo mật theo nguyên tắc mặc định từ chối, nghĩa là người dùng chỉ nhìn thấy và thao tác được trên những gì đã được cấp quyền. Hệ thống đồng thời phải thông báo kịp thời cho người liên quan, cung cấp báo cáo tiến độ và đóng góp có số liệu cụ thể, và được thiết kế theo kiến trúc Microservice, đóng gói bằng Docker để có thể tự động triển khai lên máy chủ.

Về phạm vi, phần đã hoàn thiện gồm các chức năng sau:

- Xác thực người dùng: đăng ký có xác thực email, đăng nhập thường và đăng nhập Google, quên và đặt lại mật khẩu.
- Quản lý tổ chức: Workspace, thành viên và lời mời tham gia Workspace hoặc dự án.
- Quản lý công việc: dự án, công việc, workflow, Backlog, Sprint, Board, phân quyền theo dự án và xác nhận hoàn thành công việc.
- Hỗ trợ dự án: dự án mẫu, nhân bản dự án và tích hợp GitHub.
- Theo dõi và thông báo: báo cáo tiến độ (burndown, velocity, CFD), quản lý đóng góp, thông báo trong ứng dụng qua WebSocket, email và trang quản trị hệ thống.

Hai hướng phát triển là dịch vụ cộng đồng (sáng kiến, bài viết chia sẻ) và trợ lý AI hỗ trợ quản lý công việc mới dừng ở mức thiết kế trong tài liệu, chưa có mã nguồn và nằm ngoài phạm vi đã triển khai của đồ án.

### 1.1.3. Đối tượng người dùng

Hệ thống phục vụ nhiều nhóm người dùng với mức quyền khác nhau. Quản trị hệ thống mang vai trò toàn cục `ADMIN`, chịu trách nhiệm quản lý người dùng và có quyền vượt qua các kiểm tra phân quyền ở cấp dự án. Chủ và quản trị viên Workspace quản lý thành viên, gửi lời mời và tạo dự án trong không gian làm việc của mình. Trong từng dự án, quản trị viên dự án, gồm người tạo, trưởng dự án và người giữ vai trò ADMINISTRATOR, cấu hình workflow, phân quyền cho thành viên và duyệt các yêu cầu hoàn thành công việc. Thành viên dự án là người trực tiếp thực hiện công việc, họ tạo và cập nhật công việc, bình luận và tham gia sprint. Cuối cùng, người xem chỉ có quyền theo dõi nội dung dự án mà không được chỉnh sửa.

## 1.2. Tìm hiểu về công nghệ sử dụng

### 1.2.1. Kiến trúc Microservice và API Gateway

Hệ thống được chia thành các dịch vụ độc lập, mỗi dịch vụ phụ trách một mảng nghiệp vụ và được triển khai riêng. Dịch vụ định danh (`identity-service`) quản lý người dùng, đăng ký và xác thực email. Dịch vụ công việc (`work-service`) là phần lớn nhất, chứa dự án, công việc, workflow, sprint, board, báo cáo và đóng góp. Dịch vụ không gian làm việc (`workspace-service`) quản lý Workspace, thành viên và lời mời, còn dịch vụ thông báo (`notification-service`) phụ trách thông báo trong ứng dụng và email. Phần mã dùng chung như hợp đồng sự kiện được tách thành module `common`.

Toàn bộ yêu cầu từ trình duyệt đi qua một cổng duy nhất là API Gateway, xây dựng bằng Spring Cloud Gateway. Gateway nhận JWT từ header hoặc cookie, định tuyến đến dịch vụ phù hợp và chuyển token xuống dịch vụ phía sau. Khi một dịch vụ cần dữ liệu của dịch vụ khác, chẳng hạn work-service tra thông tin người dùng từ identity-service, hai bên giao tiếp đồng bộ qua OpenFeign.

### 1.2.2. Frontend

Giao diện được xây dựng bằng React 19 và công cụ build Vite 8, viết bằng JavaScript thuần. Việc điều hướng giữa các trang do React Router 7 đảm nhiệm, còn Axios được dùng để gọi API và tự động làm mới token khi nhận phản hồi 401. Chức năng kéo thả công việc trên Board và Backlog dùng thư viện dnd-kit, các biểu đồ báo cáo tiến độ dùng Recharts. Thông báo thời gian thực được nhận qua `@stomp/stompjs`, bên cạnh đó là react-hot-toast để hiển thị cảnh báo ngắn và lucide-react cho bộ biểu tượng.

### 1.2.3. Backend

Phía máy chủ được viết bằng Java 21 trên nền Spring Boot 3.5 và Spring Cloud 2025.0, quản lý bằng Maven theo cấu trúc nhiều module. Các dịch vụ cung cấp REST API qua Spring Web, truy cập dữ liệu bằng Spring Data JPA với Hibernate và kiểm tra dữ liệu đầu vào bằng Spring Validation. Spring AOP được dùng để kiểm tra quyền thông qua annotation, Spring Boot Actuator cung cấp điểm kiểm tra sức khỏe cho dịch vụ khi triển khai. Logic nghiệp vụ được kiểm thử đơn vị bằng JUnit 5, Mockito và AssertJ.

### 1.2.4. Xác thực và ủy quyền

Việc xác thực được giao cho Keycloak 24, một máy chủ định danh mã nguồn mở, với giao thức OAuth2 và OpenID Connect; token trao đổi giữa các thành phần là JWT. Đăng nhập bằng Google cũng đi qua Keycloak. Mỗi dịch vụ đóng vai trò OAuth2 Resource Server của Spring Security và cùng kiểm tra một issuer, còn mã định danh người dùng thống nhất toàn hệ thống là trường `sub` trong token.

Phần ủy quyền được tự xây dựng theo từng dự án. Quyền được định nghĩa bằng `PermissionKey`, gắn với các `ProjectRole` thông qua `PermissionScheme` và được gán cho người dùng bằng `RoleAssignment`. Mỗi lần gọi API, quyền được kiểm tra bằng annotation kết hợp AOP theo nguyên tắc mặc định từ chối.

### 1.2.5. Lưu trữ dữ liệu

Dữ liệu nghiệp vụ lưu trong PostgreSQL 15, các dịch vụ dùng chung một database nhưng mỗi dịch vụ quản lý bảng của riêng mình. Lược đồ của work-service, workspace-service và notification-service được quản lý phiên bản bằng Flyway để các thay đổi cấu trúc có thể theo dõi và áp dụng nhất quán. Redis 7 đóng vai trò bộ nhớ đệm, đồng thời hỗ trợ Pub/Sub và khóa công việc định kỳ khi hệ thống chạy nhiều instance. Tệp đính kèm của công việc được lưu trên MinIO, một kho đối tượng tương thích S3. Riêng với tài khoản người dùng, identity-service đồng bộ với Keycloak thông qua Keycloak Admin Client.

### 1.2.6. Xử lý bất đồng bộ và thời gian thực

Thông báo được xử lý theo mô hình hướng sự kiện. Khi nghiệp vụ phát sinh, sự kiện được ghi vào bảng outbox trong cùng transaction với dữ liệu nghiệp vụ, sau đó một tiến trình chuyển tiếp định kỳ đẩy sự kiện lên Apache Kafka 3.8 thông qua Spring Kafka. Cách làm này đảm bảo sự kiện không bị mất khi dịch vụ gặp sự cố, và những sự kiện xử lý lỗi nhiều lần sẽ được chuyển sang Dead Letter Topic. Notification-service nhận sự kiện từ Kafka, lưu vào cơ sở dữ liệu rồi đẩy đến trình duyệt qua WebSocket với giao thức STOMP, đồng thời gửi email qua SMTP bằng Spring Mail. Ở môi trường phát triển, email được hứng bởi Mailpit nên không gửi ra ngoài. Ngoài ra, hệ thống gọi GitHub REST API để liên kết công việc với repository.

### 1.2.7. Container hóa và CI/CD

Mỗi dịch vụ và frontend được đóng gói thành một image Docker, tổng cộng sáu image, cùng với các thành phần hạ tầng nói trên và được điều phối bằng Docker Compose; frontend được phục vụ qua Nginx. Quy trình tích hợp và triển khai liên tục chạy trên GitHub Actions. Khi có thay đổi được đẩy vào nhánh `main`, quy trình chạy kiểm thử, build và đẩy các image lên Docker Hub, sau đó kết nối SSH tới máy chủ AWS EC2 để chạy `docker compose` triển khai phiên bản mới, và cuối cùng kiểm tra lại dịch vụ qua HTTPS.
