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

Các công nghệ dưới đây được lựa chọn dựa trên yêu cầu của hệ thống: nhiều người dùng thao tác cùng lúc, dữ liệu có quan hệ chặt chẽ, quyền hạn phải được kiểm soát nghiêm ngặt, thông báo phải đến kịp thời và việc triển khai phải tự động. Danh sách được lấy trực tiếp từ mã nguồn của dự án.

### 1.2.1. Kiến trúc Microservice và cổng giao tiếp API

Kiến trúc Microservice là một phong cách kiến trúc phần mềm [1], trong đó một ứng dụng lớn được chia thành tập hợp các dịch vụ nhỏ, độc lập, mỗi dịch vụ chạy trong tiến trình riêng và giao tiếp với nhau qua giao diện lập trình hoặc hàng đợi thông điệp. Đối lập với kiến trúc nguyên khối, nơi toàn bộ chức năng nằm trong một chương trình duy nhất, cách chia này cho phép mỗi phần được phát triển, kiểm thử và triển khai riêng mà không ảnh hưởng đến phần còn lại.

Hệ thống được tách thành năm thành phần chạy riêng: cổng giao tiếp (api-gateway), dịch vụ định danh (identity-service), dịch vụ công việc (work-service), dịch vụ không gian làm việc (workspace-service) và dịch vụ thông báo (notification-service), cùng một module dùng chung (common) chứa các định nghĩa mà các dịch vụ cùng sử dụng. Các đặc điểm được tận dụng gồm:

Độc lập: mỗi dịch vụ phụ trách một mảng nghiệp vụ riêng và có thể được sửa, khởi động lại mà không dừng cả hệ thống. Chẳng hạn khi cần chỉnh phần thông báo, nhóm chỉ cần triển khai lại dịch vụ thông báo.

Phân tán: các dịch vụ có thể đặt trên những máy chủ khác nhau, nhờ đó phần nào chịu tải lớn (như dịch vụ công việc) có thể được mở rộng riêng.

Giao tiếp đồng bộ và bất đồng bộ: khi cần kết quả ngay, dịch vụ gọi nhau trực tiếp qua giao diện REST (dùng OpenFeign); khi chỉ cần báo tin như gửi thông báo, dịch vụ phát sự kiện lên hàng đợi để bên nhận xử lý sau. Cách này giảm sự phụ thuộc giữa các dịch vụ và giúp hệ thống chịu lỗi tốt hơn.

Cổng giao tiếp API: toàn bộ yêu cầu từ trình duyệt đi qua một điểm vào duy nhất, được xây dựng bằng Spring Cloud Gateway [2]. Cổng này định tuyến yêu cầu đến đúng dịch vụ, đọc mã xác thực từ tiêu đề hoặc cookie rồi chuyển xuống dịch vụ phía sau, đồng thời giới hạn nguồn truy cập (CORS). Nhờ đó các dịch vụ bên trong không phải lộ trực tiếp ra bên ngoài và không phải lặp lại cùng một đoạn xử lý chung.

### 1.2.2. Spring Boot

Spring Boot [3] là khung phát triển ứng dụng Java giúp dựng nhanh dịch vụ chạy độc lập, với cấu hình mặc định hợp lý và máy chủ web được nhúng sẵn. Đây là nền tảng cho toàn bộ phần phía máy chủ của hệ thống, sử dụng ngôn ngữ Java 21 cùng phiên bản Spring Boot 3.5 và Spring Cloud 2025.0, các module được quản lý bằng công cụ Maven.

Java được chọn vì là ngôn ngữ ổn định, có hệ sinh thái phong phú và kiểm soát kiểu dữ liệu chặt chẽ, phù hợp với phần nghiệp vụ nhiều quy tắc như phân quyền hay quy trình duyệt công việc. Trong dự án, một số thành phần của Spring được dùng cụ thể như sau:

Spring Web: xây dựng các giao diện REST mà giao diện người dùng gọi tới.

Spring Data JPA và Hibernate: ánh xạ các bảng trong cơ sở dữ liệu thành các đối tượng Java, giúp truy vấn và lưu dữ liệu mà không phải viết thủ công từng câu lệnh. Với các bộ lọc công việc có điều kiện thay đổi, hệ thống dùng thêm cơ chế Specification để ghép điều kiện linh hoạt.

Spring Security: bảo vệ các giao diện, phối hợp với máy chủ định danh để kiểm tra mã xác thực của từng yêu cầu.

Spring AOP: cho phép đặt chú thích lên phương thức để tự động kiểm tra quyền trước khi chạy, tránh phải viết lặp đoạn kiểm tra ở từng nơi.

Spring Validation và Actuator: kiểm tra dữ liệu đầu vào và cung cấp điểm kiểm tra tình trạng của dịch vụ khi triển khai.

### 1.2.3. React và Vite

React [4] là thư viện JavaScript do Meta phát triển để xây dựng giao diện người dùng theo hướng thành phần. Mỗi phần của giao diện như thẻ công việc, cột trên bảng hay khung bình luận là một thành phần độc lập, có thể tái sử dụng và tự cập nhật khi dữ liệu thay đổi. Điều này đặc biệt phù hợp với giao diện quản lý công việc, nơi trạng thái thay đổi liên tục. Hệ thống dùng React 19 và công cụ đóng gói Vite 8 [5], vốn khởi động nhanh và cập nhật tức thì khi sửa mã trong lúc phát triển.

Giao diện được viết bằng JavaScript thuần và tự thiết kế phần trình bày, không dùng thư viện giao diện dựng sẵn, nhằm giữ phong cách đơn giản, nhẹ và thống nhất. Một số thư viện nhỏ được dùng cho các nhu cầu cụ thể: React Router để chuyển trang, Axios để gọi giao diện máy chủ và tự làm mới mã xác thực khi hết hạn, dnd-kit để kéo thả công việc giữa các cột và trong danh sách tồn đọng, Recharts để vẽ biểu đồ báo cáo tiến độ, và stompjs để nhận thông báo theo thời gian thực.

### 1.2.4. Keycloak, OAuth2, OpenID Connect và JWT

**Giới thiệu về Keycloak**

Keycloak [6] là phần mềm mã nguồn mở chuyên quản lý định danh và quyền truy cập, do Red Hat khởi xướng và hiện thuộc quỹ Cloud Native Computing Foundation. Phần mềm này đóng vai trò một máy chủ xác thực tập trung: người dùng đăng nhập một lần tại Keycloak, các ứng dụng phía sau tin vào kết quả đó thay vì tự kiểm tra mật khẩu. Thay vì tự viết phần đăng nhập, lưu mật khẩu và cấp mã xác thực, vốn là những việc dễ sai sót và rủi ro về bảo mật, hệ thống giao toàn bộ việc này cho Keycloak 24, một giải pháp đã được kiểm chứng rộng rãi trong thực tế.

Trong Keycloak, dữ liệu được tổ chức theo từng miền quản lý riêng gọi là realm. Hệ thống dùng một realm tên `datn-realm`, bên trong có người dùng, các vai trò và ứng dụng khách `datn-backend` đại diện cho phần máy chủ của hệ thống. Ba vai trò toàn cục được định nghĩa là ADMIN (quản trị toàn hệ thống), USER (người dùng thông thường) và USER_CREATE (người dùng được phép tạo mới ở một số chức năng).

**Tính năng chính của Keycloak được sử dụng**

Quản lý người dùng và mật khẩu: Keycloak lưu tài khoản, băm mật khẩu bằng thuật toán an toàn và cung cấp sẵn cơ chế chính sách mật khẩu, khóa tài khoản khi đăng nhập sai nhiều lần. Dịch vụ định danh của hệ thống tạo và cập nhật tài khoản trong Keycloak thông qua thư viện Keycloak Admin Client, do đó không phải tự lưu mật khẩu trong cơ sở dữ liệu của mình.

Đăng nhập một lần và đăng nhập bằng bên thứ ba: Keycloak hỗ trợ sẵn việc liên kết với nhà cung cấp danh tính bên ngoài. Hệ thống tận dụng tính năng này để cho phép người dùng đăng nhập bằng tài khoản Google, không cần tạo thêm mật khẩu mới.

Cấp và làm mới mã xác thực: sau khi đăng nhập, Keycloak cấp mã truy cập có thời hạn ngắn và mã làm mới có thời hạn dài hơn. Khi mã truy cập hết hạn, giao diện người dùng dùng mã làm mới để xin mã mới mà người dùng không phải đăng nhập lại.

Quản lý vai trò: vai trò được gắn vào người dùng và được ghi thẳng vào mã xác thực, để mỗi dịch vụ biết ngay người gọi có phải quản trị hệ thống hay không mà không cần tra cứu thêm.

Trang quản trị: Keycloak có sẵn giao diện web để quản trị viên xem, khóa hoặc sửa người dùng, đổi cấu hình và xuất toàn bộ cấu hình realm ra tệp để cài lại trên máy khác. Hệ thống tận dụng điều này: cấu hình realm được lưu thành tệp trong kho mã và nạp lại khi dựng môi trường mới.

**Các chuẩn xác thực**

Việc xác thực dựa trên các chuẩn mở. OAuth2 [7] là chuẩn ủy quyền, cho phép một ứng dụng truy cập tài nguyên thay mặt người dùng mà không cần biết mật khẩu của họ. OpenID Connect [8] là lớp mở rộng đặt trên OAuth2 để xác định danh tính người dùng, cho biết người đang đăng nhập là ai. Hai chuẩn này được nhiều hệ thống lớn như Google, Microsoft sử dụng, nên việc tích hợp thêm nhà cung cấp mới về sau rất thuận lợi.

Kết quả của quá trình đăng nhập là mã xác thực dạng JWT (JSON Web Token) [9]. Đây là một chuỗi gồm ba phần: phần đầu mô tả thuật toán ký, phần nội dung chứa các thông tin như mã người dùng, vai trò, nơi cấp và thời điểm hết hạn, và phần chữ ký số do Keycloak tạo ra. Vì có chữ ký nên nội dung mã không thể bị sửa đổi mà không bị phát hiện. Mỗi dịch vụ chỉ cần kiểm tra chữ ký và thời hạn là biết yêu cầu có hợp lệ hay không, không cần hỏi lại máy chủ định danh ở mỗi lần gọi, nhờ đó giảm tải và tăng tốc độ xử lý.

**Cách hệ thống sử dụng**

Khi người dùng đăng nhập, mã xác thực được trả về và lưu ở trình duyệt, đồng thời có thể được đặt trong cookie chỉ gửi qua giao thức web (httpOnly) để mã độc trên trang không đọc được. Cổng giao tiếp API đọc mã từ tiêu đề Authorization hoặc từ cookie, sau đó chuyển tiếp xuống các dịch vụ phía sau. Cả bốn dịch vụ nghiệp vụ đều đóng vai trò máy chủ tài nguyên theo chuẩn OAuth2 của Spring Security và cùng tin cậy một nơi cấp mã duy nhất.

Mã người dùng trong mã xác thực (trường sub) được chọn làm định danh duy nhất của người dùng ở mọi bảng dữ liệu của toàn hệ thống. Nhờ vậy các dịch vụ thống nhất cách gọi tên một người, và dịch vụ nào cũng biết người đang gọi là ai chỉ bằng cách đọc mã xác thực, không cần gọi sang dịch vụ định danh.

Bên cạnh vai trò toàn cục do Keycloak quản lý, phần phân quyền theo từng dự án được hệ thống tự xây dựng, vì mỗi dự án cần bộ vai trò và quyền riêng mà Keycloak không thể biết trước. Mỗi dự án có các vai trò như quản trị viên, thành viên và người xem, mỗi vai trò gắn với tập quyền cụ thể, và mọi thao tác đều bị từ chối nếu người dùng chưa được cấp quyền. Riêng người có vai trò ADMIN toàn cục được vượt qua các kiểm tra này.

**Lợi ích khi sử dụng**

Về bảo mật, mật khẩu và việc cấp mã được giao cho một phần mềm chuyên biệt, được cộng đồng kiểm tra và vá lỗi thường xuyên, thay vì mã tự viết của nhóm. Về phát triển, nhóm không mất công xây dựng lại đăng nhập, quên mật khẩu, khóa tài khoản hay đăng nhập Google, và có thể tập trung vào nghiệp vụ quản lý công việc. Về vận hành, nhiều dịch vụ dùng chung một nguồn xác thực nên người dùng chỉ đăng nhập một lần, và khi cần thu hồi quyền chỉ phải xử lý tại một nơi. Về mở rộng, việc thêm dịch vụ mới chỉ cần cấu hình tin cậy cùng nơi cấp mã mà không phải viết lại phần xác thực.

### 1.2.5. PostgreSQL và Flyway

PostgreSQL [10] là hệ quản trị cơ sở dữ liệu quan hệ mã nguồn mở, nổi tiếng về độ tin cậy, hỗ trợ giao dịch đầy đủ và nhiều kiểu dữ liệu nâng cao. Dữ liệu của hệ thống như dự án, công việc, trạng thái, người được giao hay lịch sử thay đổi có quan hệ chặt chẽ với nhau, nên mô hình quan hệ và các ràng buộc khóa ngoại là lựa chọn tự nhiên. Hệ thống dùng PostgreSQL 15, các dịch vụ dùng chung một cơ sở dữ liệu nhưng mỗi dịch vụ chỉ quản lý các bảng của mình.

Flyway [11] là công cụ quản lý phiên bản cấu trúc cơ sở dữ liệu. Mỗi thay đổi về bảng, cột được viết thành một tệp lệnh đánh số thứ tự và được áp dụng tự động theo đúng thứ tự khi dịch vụ khởi động. Nhờ vậy cấu trúc dữ liệu ở máy phát triển và máy chủ thật luôn khớp nhau, và mọi thay đổi đều có lịch sử để truy vết. Flyway được dùng ở dịch vụ công việc, dịch vụ không gian làm việc và dịch vụ thông báo.

### 1.2.6. Redis

Redis [12] là kho dữ liệu lưu trong bộ nhớ, truy xuất rất nhanh, thường dùng làm bộ nhớ đệm và kênh truyền tin. Hệ thống dùng Redis 7 cho hai mục đích. Thứ nhất là lưu tạm các dữ liệu đọc nhiều để giảm tải cho cơ sở dữ liệu. Thứ hai là hỗ trợ chạy nhiều bản sao của cùng một dịch vụ: khi đó thông báo cần được đẩy tới người dùng đang kết nối ở bản sao khác thông qua cơ chế phát và nhận tin của Redis, và các công việc chạy định kỳ như nhắc hạn được khóa lại để chỉ một bản sao thực hiện, tránh gửi trùng.

### 1.2.7. MinIO

MinIO [13] là hệ thống lưu trữ đối tượng mã nguồn mở, tương thích với giao diện S3 của Amazon. Các tệp đính kèm của công việc như hình ảnh, tài liệu có dung lượng lớn và không phù hợp để lưu trong cơ sở dữ liệu, vì làm cơ sở dữ liệu phình to và chậm. Hệ thống lưu các tệp này trong MinIO, còn cơ sở dữ liệu chỉ giữ thông tin mô tả và đường dẫn tới tệp. Vì tương thích S3, về sau có thể chuyển sang dịch vụ lưu trữ đám mây mà ít phải sửa mã.

### 1.2.8. Apache Kafka và mẫu thiết kế Outbox

Apache Kafka [14] là nền tảng truyền sự kiện phân tán, cho phép một bên ghi sự kiện vào các chủ đề và nhiều bên khác đọc lại theo nhu cầu, với khả năng lưu trữ bền vững và chịu tải cao. Hệ thống dùng Kafka 3.8 qua thư viện Spring Kafka để truyền sự kiện thông báo từ các dịch vụ nghiệp vụ sang dịch vụ thông báo. Bên phát chỉ cần ghi sự kiện, không phải chờ hay biết bên nhận là ai, nên hai phía hoàn toàn tách biệt.

Một vấn đề là nếu dịch vụ lưu dữ liệu xong nhưng gặp sự cố trước khi gửi sự kiện thì thông báo sẽ bị mất. Để tránh điều này, hệ thống áp dụng mẫu thiết kế Outbox [15]: sự kiện được ghi vào một bảng riêng trong cùng giao dịch với dữ liệu nghiệp vụ, sau đó một tiến trình chạy nền đọc bảng này và đẩy lên Kafka. Do cùng một giao dịch nên dữ liệu và sự kiện hoặc cùng thành công, hoặc cùng thất bại. Những sự kiện xử lý lỗi nhiều lần được chuyển sang một chủ đề riêng để xem xét sau, không làm nghẽn luồng chính.

### 1.2.9. WebSocket, STOMP và thư điện tử

WebSocket [16] là giao thức cho phép trình duyệt và máy chủ giữ một kết nối hai chiều liên tục, để máy chủ chủ động gửi dữ liệu mà không cần trình duyệt hỏi liên tục. STOMP [17] là giao thức nhắn tin đơn giản chạy trên WebSocket, cho phép đăng ký nhận tin theo từng kênh, ở đây là kênh riêng của từng người dùng. Nhờ đó khi được giao việc hoặc bị nhắc tên, người dùng thấy thông báo hiện ngay trên chuông mà không cần tải lại trang.

Với người không mở ứng dụng, thông báo còn được gửi qua thư điện tử theo giao thức SMTP bằng Spring Mail. Cách này cũng được dùng cho thư xác thực tài khoản và đặt lại mật khẩu. Trong môi trường phát triển, thư được chuyển tới Mailpit, một máy chủ thư giả lập, nên có thể xem nội dung thư mà không gửi ra ngoài.

### 1.2.10. Docker và quy trình triển khai tự động

Docker [18] là nền tảng đóng gói ứng dụng cùng toàn bộ môi trường chạy của nó vào một ảnh (image), từ đó chạy giống hệt nhau trên mọi máy, khắc phục tình trạng "chạy được trên máy tôi nhưng lỗi trên máy chủ". Mỗi dịch vụ và giao diện người dùng của hệ thống được đóng thành một ảnh riêng, tổng cộng sáu ảnh. Docker Compose được dùng để khai báo và khởi động đồng thời các ảnh này cùng các thành phần hạ tầng như cơ sở dữ liệu, Keycloak, Redis, Kafka và MinIO, còn giao diện người dùng được phục vụ qua máy chủ web Nginx.

GitHub Actions [19] là dịch vụ tự động hóa quy trình ngay trên kho mã GitHub. Mỗi khi có mã mới được đưa vào nhánh chính, quy trình tự động chạy kiểm thử, dựng các ảnh Docker và đưa lên Docker Hub, sau đó kết nối tới máy chủ AWS EC2 để kéo ảnh mới về và khởi động lại, cuối cùng kiểm tra dịch vụ còn hoạt động qua đường truyền bảo mật HTTPS. Nhờ vậy việc phát hành phiên bản mới diễn ra nhanh, nhất quán và giảm sai sót do thao tác tay.
