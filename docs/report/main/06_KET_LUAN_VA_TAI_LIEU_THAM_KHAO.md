# KẾT LUẬN

## 1. Kết quả đạt được

Đồ án đã xây dựng được một hệ thống quản lý dự án và công việc theo mô hình Agile, học hỏi cách tổ chức của Jira nhưng đơn giản hơn và dùng được cho nhiều ngành nghề. Hệ thống gồm năm thành phần chạy riêng theo kiến trúc Microservice (cổng giao tiếp API, dịch vụ định danh, dịch vụ công việc, dịch vụ không gian làm việc, dịch vụ thông báo) cùng giao diện React, đóng gói bằng Docker và đã được triển khai tự động lên máy chủ AWS EC2 có HTTPS.

Về chức năng, hệ thống đáp ứng các mục tiêu đề ra ở Chương 1:

- **Xác thực và phân quyền.** Đăng nhập tập trung bằng Keycloak (OAuth2, OpenID Connect, JWT), đăng ký có xác thực email, đăng nhập Google, quên và đặt lại mật khẩu. Phân quyền theo từng dự án bằng ma trận vai trò–quyền lưu thành dữ liệu, cưỡng chế bằng aspect theo nguyên tắc mặc định từ chối, với một định danh người dùng duy nhất trên toàn hệ thống (Keycloak `sub`).
- **Quản lý công việc.** Dự án, công việc phân cấp ba tầng, workflow cấu hình được với điều kiện, bộ kiểm tra và hàm sau chuyển; liên kết công việc có phát hiện chu trình phụ thuộc bằng DFS; sắp xếp kéo thả bằng LexoRank với một bản ghi mỗi lần kéo; nhật ký thay đổi và khóa lạc quan.
- **Agile.** Sprint với vòng đời một chiều, Backlog, Board Scrum/Kanban có giới hạn WIP và swimlane; báo cáo Burndown, Velocity, CFD đúng cả với Sprint đang chạy (phát lại nhật ký) và đã đóng (snapshot bất biến).
- **Chất lượng công việc và công bằng.** Quy trình "Tạm hoàn thành" chỉ cho quản trị viên dự án xác nhận công việc xong; sổ ghi đóng góp chỉ thêm, tính điểm có phạt trễ hạn, có snapshot theo Sprint để đánh giá đóng góp của từng thành viên.
- **Thông báo.** Hệ thống thông báo hướng sự kiện với 53 loại: ghi outbox cùng giao dịch, đẩy qua Kafka, lưu bằng chống trùng, đẩy xuống trình duyệt bằng WebSocket/STOMP và gửi email, có cài đặt theo nhóm và kênh, chạy được nhiều bản sao.
- **Hỗ trợ tạo dự án.** Dự án mẫu, nhân bản sâu có ánh xạ ID và dời ngày, kiểm tra toàn vẹn của mẫu; tích hợp GitHub (OAuth, webhook, đồng bộ Issue, liên kết pull request).
- **Vận hành.** Quy trình CI/CD bằng GitHub Actions: kiểm thử, dựng sáu ảnh Docker, đẩy lên Docker Hub, triển khai qua SSH với khóa bị hạn chế, kiểm tra sau triển khai và có thể quay về bản trước.

Về chất lượng, có 540 bài kiểm thử đơn vị (work-service 344, workspace-service 48, notification-service 148) đạt trong CI; hệ thống thông báo còn được kiểm thử tích hợp với Kafka và PostgreSQL thật, kiểm thử giao diện đầu cuối và kiểm thử hai bản sao dùng chung Redis. Về tổng quát hóa, khảo sát mô phỏng 20 ngành nghề cho thấy mô hình phù hợp tốt với phần mềm, game, marketing, F&B, thương mại điện tử và sự kiện.

## 2. Hạn chế

- Mô hình công việc còn các giới hạn cố ý hoặc chưa kịp mở rộng: ba tầng phân cấp, ba nhóm trạng thái, một người thực hiện duy nhất, đơn vị đo chỉ là điểm câu chuyện và thời gian, chưa có tự động lập lịch theo đường găng và chưa có giao diện quản lý trường tùy chỉnh. Vì vậy các ngành xây dựng, sản xuất, kiểm toán, luật chưa phù hợp.
- Chỉ thông báo là thời gian thực; Board và danh sách công việc chưa tự cập nhật khi người khác thay đổi.
- Chưa đo độ phủ mã, chưa có kiểm thử tự động cho frontend và kiểm thử đầu cuối trong CI.
- Các dịch vụ dùng chung một cơ sở dữ liệu; triển khai trên một máy chủ nên chưa chịu lỗi ở mức máy chủ.
- Trợ lý AI và dịch vụ cộng đồng mới ở mức thiết kế, chưa có mã nguồn.

## 3. Hướng phát triển

1. **Trợ lý AI** hỗ trợ người dùng: gợi ý chia nhỏ công việc lớn, soạn mô tả, tóm tắt tiến độ Sprint, cảnh báo công việc có nguy cơ trễ hạn dựa trên nhật ký và sổ ghi công đã có sẵn.
2. **Thời gian thực cho Board** theo kế hoạch đã nghiên cứu (phát sự kiện thay đổi, hiển thị người đang xem, giải quyết xung đột theo trường), tận dụng hạ tầng WebSocket của dịch vụ thông báo.
3. **Mở rộng mô hình công việc:** thêm nhóm trạng thái ngoại lệ (hủy, tạm dừng), nhiều người thực hiện, đơn vị đo tùy chọn, giao diện quản lý trường tùy chỉnh, tự động dời lịch theo phụ thuộc.
4. **Dịch vụ cộng đồng** (sáng kiến, bài viết chia sẻ) và tích hợp thêm các công cụ bên ngoài.
5. **Nâng cao chất lượng và vận hành:** đo độ phủ bằng JaCoCo, kiểm thử giao diện tự động, kiểm thử đầu cuối trong CI, theo dõi bằng chỉ số và nhật ký tập trung, tách cơ sở dữ liệu theo dịch vụ khi quy mô tăng.

---

# TÀI LIỆU THAM KHẢO

Các mục [1] đến [19] đã được dẫn ở Chương 1; các mục [20] đến [26] bổ sung cho Chương 2 đến Chương 4.

[1] Martin Fowler, "Microservices", martinfowler.com/articles/microservices.html

[2] Spring Cloud Gateway, docs.spring.io/spring-cloud-gateway

[3] Spring Boot Reference Documentation, docs.spring.io/spring-boot

[4] React, react.dev

[5] Vite, vite.dev

[6] Keycloak Documentation, keycloak.org/documentation

[7] RFC 6749, The OAuth 2.0 Authorization Framework

[8] OpenID Connect Core 1.0, openid.net/specs/openid-connect-core-1_0.html

[9] RFC 7519, JSON Web Token (JWT)

[10] PostgreSQL Documentation, postgresql.org/docs

[11] Flyway Documentation, documentation.red-gate.com/flyway

[12] Redis Documentation, redis.io/docs

[13] MinIO Documentation, min.io/docs

[14] Apache Kafka Documentation, kafka.apache.org/documentation

[15] Chris Richardson, "Transactional outbox", microservices.io/patterns/data/transactional-outbox.html

[16] RFC 6455, The WebSocket Protocol

[17] STOMP Protocol Specification, stomp.github.io

[18] Docker Documentation, docs.docker.com

[19] GitHub Actions Documentation, docs.github.com/actions

[20] Ken Schwaber, Jeff Sutherland, "The Scrum Guide", scrumguides.org

[21] David J. Anderson, "Kanban: Successful Evolutionary Change for Your Technology Business", Blue Hole Press, 2010

[22] Thomas H. Cormen, Charles E. Leiserson, Ronald L. Rivest, Clifford Stein, "Introduction to Algorithms", MIT Press (mục tìm kiếm theo chiều sâu, DFS)

[23] Chris Richardson, "Microservices Patterns", Manning, 2018

[24] OWASP Foundation, "OWASP Top 10: A01 Broken Access Control", owasp.org/Top10

[25] Spring Security Reference, docs.spring.io/spring-security

[26] Atlassian, tài liệu về xếp hạng công việc (ranking) và LexoRank của Jira Software, support.atlassian.com
