# PHẦN MỞ ĐẦU

> Phần này gồm: trang bìa, lời cảm ơn, danh mục từ viết tắt, danh mục bảng biểu, danh mục hình ảnh. Mục lục nằm ở `00_MUC_LUC.md`. Số trang của phần này đánh số La Mã (i, ii, iii, …), từ Chương 1 đánh số Ả Rập.

---

## TRANG BÌA

HỌC VIỆN CÔNG NGHỆ BƯU CHÍNH VIỄN THÔNG
KHOA CÔNG NGHỆ THÔNG TIN 1

---

**ĐỒ ÁN TỐT NGHIỆP**

**ĐỀ TÀI: XÂY DỰNG HỆ THỐNG QUẢN LÝ DỰ ÁN VÀ CÔNG VIỆC DEVCENTRAL**

Giảng viên hướng dẫn: (điền họ tên, học hàm, học vị)

| Thành viên | Mã sinh viên | Lớp sinh viên |
| :--- | :--- | :--- |
| Trần Xuân Sơn | (điền) | (điền) |
| Nguyễn Viết Tuấn Anh | (điền) | (điền) |
| Nguyễn Hữu Tiến | (điền) | (điền) |

Hà Nội, 2026

> Ghi chú: tên trường, khoa theo mẫu của báo cáo tham khảo; danh sách thành viên lấy từ lịch sử git và bản phân công cũ, cần kiểm tra lại cho đúng.

---

## LỜI CẢM ƠN

Lời đầu tiên, nhóm chúng em xin bày tỏ lòng biết ơn sâu sắc đến giảng viên hướng dẫn đã tận tình giúp đỡ, chỉ bảo và định hướng cho nhóm trong suốt quá trình tìm hiểu, nghiên cứu và hoàn thành đồ án tốt nghiệp.

Nhóm em xin chân thành cảm ơn các thầy cô Khoa Công nghệ Thông tin 1 cũng như toàn thể thầy cô trong Học viện Công nghệ Bưu chính Viễn thông đã giảng dạy và trang bị cho chúng em những kiến thức cơ bản, cần thiết để nhóm có thể hoàn thành đồ án này.

Cuối cùng, nhóm em xin gửi lời cảm ơn đến gia đình, người thân và bạn bè, những người đã luôn ủng hộ, động viên và tạo điều kiện cho chúng em trong suốt quá trình thực hiện đồ án tốt nghiệp.

Thông qua quá trình làm đồ án, nhóm chúng em đã có cơ hội tiếp cận thực tế, tích lũy thêm nhiều kiến thức chuyên môn bổ ích, đồng thời củng cố những kiến thức đã học trên lớp, đặc biệt là về kiến trúc Microservice, bảo mật và xây dựng hệ thống phân tán.

Vì thời gian có hạn và kiến thức còn hạn chế, báo cáo không tránh khỏi những thiếu sót. Nhóm em rất mong nhận được sự góp ý của quý thầy cô và các bạn để đồ án được hoàn thiện hơn.

Chúng em xin chân thành cảm ơn!

---

## DANH MỤC CÁC TỪ VIẾT TẮT VÀ THUẬT NGỮ

| STT | Từ viết tắt | Thuật ngữ tiếng Anh | Ý nghĩa |
| :-: | :--- | :--- | :--- |
| 1 | AOP | Aspect-Oriented Programming | Lập trình hướng khía cạnh |
| 2 | API | Application Programming Interface | Giao diện lập trình ứng dụng |
| 3 | CFD | Cumulative Flow Diagram | Biểu đồ dòng tích lũy |
| 4 | CI/CD | Continuous Integration / Continuous Delivery | Tích hợp và triển khai liên tục |
| 5 | CNTT | – | Công nghệ thông tin |
| 6 | CORS | Cross-Origin Resource Sharing | Chia sẻ tài nguyên giữa các nguồn |
| 7 | CSDL | – | Cơ sở dữ liệu |
| 8 | DFS | Depth-First Search | Tìm kiếm theo chiều sâu |
| 9 | DLT | Dead Letter Topic | Chủ đề chứa sự kiện xử lý lỗi |
| 10 | DONE | – | Nhóm trạng thái "đã hoàn thành" |
| 11 | EC2 | Elastic Compute Cloud | Máy chủ ảo của Amazon Web Services |
| 12 | FK | Foreign Key | Khóa ngoại |
| 13 | FSM | Finite State Machine | Máy trạng thái hữu hạn |
| 14 | HTTP(S) | HyperText Transfer Protocol (Secure) | Giao thức truyền siêu văn bản (an toàn) |
| 15 | IDE | Integrated Development Environment | Môi trường phát triển tích hợp |
| 16 | JPA | Java Persistence API | Giao diện lưu trữ bền vững của Java |
| 17 | JSON / JSONB | JavaScript Object Notation / Binary | Định dạng dữ liệu văn bản nhẹ / dạng nhị phân của PostgreSQL |
| 18 | JWT | JSON Web Token | Mã thông báo xác thực dạng JSON |
| 19 | LexoRank | Lexicographical Rank | Xếp hạng theo thứ tự từ điển |
| 20 | OAuth2 | Open Authorization 2.0 | Chuẩn ủy quyền truy cập |
| 21 | OIDC | OpenID Connect | Lớp xác định danh tính trên OAuth2 |
| 22 | ORM | Object-Relational Mapping | Ánh xạ đối tượng – quan hệ |
| 23 | PK | Primary Key | Khóa chính |
| 24 | RBAC | Role-Based Access Control | Kiểm soát truy cập dựa trên vai trò |
| 25 | REST | Representational State Transfer | Kiểu kiến trúc cho dịch vụ web |
| 26 | S3 | Simple Storage Service | Giao diện lưu trữ đối tượng của Amazon |
| 27 | SMTP | Simple Mail Transfer Protocol | Giao thức gửi thư điện tử |
| 28 | SP | Story Points | Điểm câu chuyện (đơn vị ước lượng công việc) |
| 29 | SPA | Single Page Application | Ứng dụng một trang |
| 30 | SSH | Secure Shell | Giao thức kết nối an toàn |
| 31 | STOMP | Simple Text Oriented Messaging Protocol | Giao thức nhắn tin văn bản đơn giản |
| 32 | `sub` | Subject | Trường định danh người dùng trong JWT |
| 33 | UI / UX | User Interface / User Experience | Giao diện / Trải nghiệm người dùng |
| 34 | UML | Unified Modeling Language | Ngôn ngữ mô hình hóa thống nhất |
| 35 | UUID | Universally Unique Identifier | Mã định danh duy nhất toàn cục |
| 36 | WIP | Work In Progress | Công việc đang thực hiện (giới hạn trên Board) |

---

## DANH MỤC BẢNG BIỂU

| Bảng | Tên bảng | Trang |
| :--- | :--- | :-: |
| Bảng 2.1 | Đầu vào và đầu ra của bài toán xác thực, phân quyền | |
| Bảng 2.2 | Đầu vào và đầu ra của bài toán quản trị dự án | |
| Bảng 2.3 | Đầu vào và đầu ra của bài toán đóng góp, tiến độ | |
| Bảng 2.4 | Đầu vào và đầu ra của bài toán mẫu dự án | |
| Bảng 2.5 | Đầu vào và đầu ra của bài toán thông báo | |
| Bảng 2.6 | Đầu vào và đầu ra của bài toán tích hợp GitHub | |
| Bảng 2.7 | Bậc tối thiểu cho từng thao tác trong không gian làm việc | |
| Bảng 2.8 | Các khóa quyền của dự án | |
| Bảng 2.9 | Các loại dự án | |
| Bảng 2.10 | Ma trận hợp lệ giữa công việc cha và công việc con | |
| Bảng 2.11 | Các loại liên kết giữa công việc | |
| Bảng 2.12 | Những gì người dùng được tùy biến | |
| Bảng 2.13 | Thuộc tính chính của công việc | |
| Bảng 2.14 | Tùy chỉnh được trước khi tạo dự án từ mẫu | |
| Bảng 3.1 | Bảng yêu cầu chức năng | |
| Bảng 3.2 | Bảng yêu cầu phi chức năng | |
| Bảng 3.3 | Danh sách Actor | |
| Bảng 3.4 | Danh sách Use Case | |
| Bảng 3.5 | Công nghệ và trách nhiệm của từng dịch vụ | |
| Bảng 3.6 | Định tuyến tại cổng giao tiếp API | |
| Bảng 3.7 | Các nhóm dữ liệu theo dịch vụ | |
| Bảng 3.8 | Nhóm phân quyền và thành viên | |
| Bảng 3.9 | Nhóm dự án, loại công việc, trạng thái và quy trình | |
| Bảng 3.10 | Nhóm công việc và tùy biến | |
| Bảng 3.11 | Nhóm giai đoạn, bảng tiến độ, đóng góp và tiến độ | |
| Bảng 3.12 | Nhóm mẫu dự án, thông báo và GitHub | |
| Bảng 3.13 | Nhóm giao diện lập trình theo dịch vụ | |
| Bảng 3.14 | Chức năng giao diện lập trình của dịch vụ thông báo | |
| Bảng 4.1 | Môi trường và công cụ | |
| Bảng 4.2 | Các công việc của quy trình CI/CD | |
| Bảng 4.3 | Thống kê nhóm chức năng và số test case hệ thống | |
| Bảng 4.4 | Kịch bản kiểm thử xác thực và phân quyền | |
| Bảng 4.5 | Kịch bản kiểm thử công việc, workflow, phân cấp | |
| Bảng 4.6 | Kịch bản kiểm thử Sprint, Board và báo cáo | |
| Bảng 4.7 | Kịch bản kiểm thử hoàn thành, đóng góp và thông báo | |
| Bảng 4.8 | Kịch bản kiểm thử dự án mẫu | |
| Bảng 4.9 | Tóm tắt kết quả khảo sát đa ngành | |
| Bảng 4.10 | Các đơn vị được chọn kiểm thử | |
| Bảng 4.11 | Một số bài kiểm thử đơn vị tiêu biểu | |
| Bảng 4.12 | Tổng hợp kết quả kiểm thử đơn vị | |
| Bảng 4.13 | Đối chiếu mục tiêu và kết quả | |
| Bảng 4.14 | So sánh với các công cụ quản lý công việc phổ biến | |

> Cột "Trang" điền khi dựng bản Word (dùng chức năng danh mục bảng tự động nếu gán kiểu chú thích cho từng bảng).

---

## DANH MỤC HÌNH ẢNH

| Hình | Tên hình | Trang |
| :--- | :--- | :-: |
| Hình 1.1 | Kiến trúc Microservice của hệ thống (nên thêm vào mục 1.2.1, có thể dùng lại Hình 3.10) | |
| Hình 3.1 | Biểu đồ Use Case tổng quan | |
| Hình 3.2 | Sơ đồ tuần tự đăng ký, xác thực thư điện tử | |
| Hình 3.3 | Sơ đồ tuần tự đăng nhập, quên và đặt lại mật khẩu | |
| Hình 3.4 | Sơ đồ tuần tự tạo dự án từ mẫu và cấu hình dự án | |
| Hình 3.5 | Sơ đồ tuần tự tạo, chỉnh sửa và giao công việc | |
| Hình 3.6 | Sơ đồ tuần tự liên kết công việc | |
| Hình 3.7 | Sơ đồ tuần tự chuyển trạng thái và xác nhận hoàn thành | |
| Hình 3.8 | Sơ đồ tuần tự bảng tiến độ và báo cáo tiến độ | |
| Hình 3.9 | Sơ đồ tuần tự nhận thông báo | |
| Hình 3.10 | Sơ đồ tuần tự tích hợp GitHub | |
| Hình 3.11 | Tổng quan kiến trúc hệ thống | |
| Hình 3.12 | Sơ đồ lớp: dự án, công việc, loại, trạng thái, quy trình | |
| Hình 3.13 | Sơ đồ lớp: giai đoạn, bảng tiến độ, hoàn thành, đóng góp | |
| Hình 3.14 | Sơ đồ lớp: không gian làm việc, phân quyền, lời mời | |
| Hình 3.15 | Sơ đồ lớp: mẫu dự án, thông báo, GitHub | |
| Hình 3.16 | Sơ đồ thực thể và quan hệ của dịch vụ công việc | |
| Hình 3.17 | Sơ đồ điều hướng màn hình | |
| Hình 4.1 | Quy trình CI/CD và kiến trúc máy chủ triển khai | |
| Hình 4.2 | Trang giới thiệu | |
| Hình 4.3 | Đăng ký và đăng nhập | |
| Hình 4.4 | Thư xác thực email trong Mailpit | |
| Hình 4.5 | Danh sách Workspace và lời mời Workspace | |
| Hình 4.6 | Thành viên Workspace và cài đặt Workspace | |
| Hình 4.7 | Danh sách dự án, thư viện mẫu và xem trước mẫu | |
| Hình 4.8 | Backlog | |
| Hình 4.9 | Hộp thoại hoàn tất Sprint | |
| Hình 4.10 | Board Scrum/Kanban | |
| Hình 4.11 | Chi tiết công việc | |
| Hình 4.12 | Khung "Tạm hoàn thành" và nút "Chờ xác nhận HT" | |
| Hình 4.13 | Trang cấu hình quy trình | |
| Hình 4.14 | Báo cáo Burndown | |
| Hình 4.15 | Báo cáo Velocity và CFD | |
| Hình 4.16 | Báo cáo đóng góp và sổ ghi công | |
| Hình 4.17 | Thành viên dự án và ma trận quyền | |
| Hình 4.18 | Chuông thông báo | |
| Hình 4.19 | Trang thông báo và cài đặt thông báo | |
| Hình 4.20 | Trang Lời mời | |
| Hình 4.21 | Tích hợp GitHub | |
| Hình 4.22 | Trang quản trị hệ thống | |
