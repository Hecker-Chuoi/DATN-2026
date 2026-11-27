# PHỤ LỤC

## Phụ lục A: Tài liệu API (tóm tắt theo dịch vụ)

Mọi đường dẫn dưới đây là đường dẫn bên trong từng dịch vụ. Khi gọi từ trình duyệt qua API Gateway cần thêm tiền tố dịch vụ: `/identity`, `/work`, `/workspace`, `/notification`. Phản hồi thành công bọc trong `ApiResponse`. Mã lỗi: 400, 401, 403, 404, 409. Tài liệu chi tiết từng tham số: `docs/API_DOCUMENTATION.md`.

### A.1. identity-service

| Phương thức | Đường dẫn | Mô tả | Xác thực |
| :--- | :--- | :--- | :-: |
| POST | `/api/v1/auth/register` | Đăng ký, gửi thư xác thực | Không |
| GET | `/api/v1/auth/verify-email` | Xác thực email bằng mã | Không |
| POST | `/api/v1/auth/resend-verification` | Gửi lại thư xác thực | Không |
| POST | `/api/v1/auth/login` | Đăng nhập | Không |
| POST | `/api/v1/auth/refresh` | Làm mới mã truy cập | Không |
| POST | `/api/v1/auth/logout` | Đăng xuất | Có |
| POST | `/api/v1/auth/forgot-password` | Yêu cầu đặt lại mật khẩu | Không |
| POST | `/api/v1/auth/reset-password` | Đặt mật khẩu mới bằng mã | Không |
| GET | `/api/v1/auth/google/init` | Lấy địa chỉ đăng nhập Google | Không |
| POST | `/api/v1/auth/google/callback` | Hoàn tất đăng nhập Google | Không |
| GET | `/api/v1/users/me` | Hồ sơ của người đang đăng nhập | Có |
| GET | `/api/v1/users/search`, `/by-email` | Tìm người dùng | Có |
| GET, POST | `/api/v1/admin/users` | Danh sách, tạo người dùng | ADMIN |
| PUT | `/api/v1/admin/users/{id}/roles` | Đổi vai trò | ADMIN |
| PUT | `/api/v1/admin/users/{id}/reset-password` | Đặt lại mật khẩu | ADMIN |
| DELETE | `/api/v1/admin/users/{id}` | Xóa người dùng | ADMIN |
| GET, POST | `/internal/users/...` | Tra người dùng theo mã, email, tên đăng nhập; tra hàng loạt (`/batch`) | Nội bộ, không qua gateway |

### A.2. workspace-service

| Phương thức | Đường dẫn | Mô tả |
| :--- | :--- | :--- |
| POST | `/api/v1/workspaces` | Tạo Workspace |
| GET | `/api/v1/workspaces/my` | Workspace của tôi |
| GET, PUT, DELETE | `/api/v1/workspaces/{workspaceId}` | Xem, sửa, xóa mềm |
| POST | `/api/v1/workspaces/{workspaceId}/restore`, `/leave` | Khôi phục; rời Workspace |
| GET, POST | `/api/v1/workspaces/{workspaceId}/members` | Danh sách thành viên; mời thành viên (tạo lời mời) |
| GET | `/api/v1/workspaces/{workspaceId}/members/me`, `/invitations` | Vai trò của tôi; lời mời đang chờ |
| PUT, DELETE | `/api/v1/workspaces/{workspaceId}/members/{userId}[/role]` | Đổi vai trò; xóa thành viên |
| GET | `/api/v1/workspace-invitations/my` | Lời mời Workspace của tôi |
| POST | `/api/v1/workspace-invitations/{id}/accept`, `/decline` | Chấp nhận, từ chối |
| DELETE | `/api/v1/workspace-invitations/{id}` | Thu hồi lời mời |
| GET, POST | `/internal/workspaces/...` | Vai trò thành viên, kiểm tra tồn tại, Workspace mặc định (nội bộ) |

### A.3. work-service (tiền tố `/api/v1/work`)

**Dự án, thành viên, lời mời**

| Phương thức | Đường dẫn | Quyền | Mô tả |
| :--- | :--- | :--- | :--- |
| POST, GET | `/projects` | Thành viên Workspace | Tạo, liệt kê dự án |
| GET | `/projects/{projectId}`, `/projects/key/{key}` | `PROJECT_VIEW` | Xem dự án |
| PUT, DELETE | `/projects/{projectId}` | `PROJECT_ADMIN` | Sửa, xóa mềm |
| POST | `/projects/{projectId}/restore` | `PROJECT_ADMIN` | Khôi phục |
| GET | `/projects/{projectId}/permissions/my` | Thành viên | Quyền của tôi |
| GET, POST | `/projects/{projectId}/members` | Xem; `PROJECT_ADMIN` | Danh sách; mời (tạo lời mời) |
| PUT, DELETE | `/projects/{projectId}/members/{userId}[/role]` | `PROJECT_ADMIN` | Đổi vai trò; xóa |
| GET | `/projects/{projectId}/roles` | Thành viên | Vai trò của dự án |
| GET | `/invitations/my`, `/invitations` | Người dùng | Lời mời của tôi |
| POST | `/invitations/{id}/accept`, `/decline` | Người được mời | Trả lời lời mời |

**Công việc**

| Phương thức | Đường dẫn | Quyền | Mô tả |
| :--- | :--- | :--- | :--- |
| POST, GET | `/projects/{projectId}/items` | `ISSUE_CREATE`; `PROJECT_VIEW` | Tạo; lọc danh sách |
| GET | `/items/{id}`, `/items/code/{code}` | `PROJECT_VIEW` | Xem theo mã nội bộ hoặc mã `MOC-12` |
| PUT, DELETE | `/items/{id}` | `ISSUE_EDIT`; `ISSUE_DELETE` | Sửa; xóa mềm |
| PUT | `/items/{id}/assignee` | `ISSUE_ASSIGN` | Giao hoặc hủy giao |
| PATCH | `/items/{id}/rank` | `ISSUE_EDIT` | Đổi thứ tự (LexoRank) |
| GET, POST | `/items/{id}/subtasks` | | Sub-task |
| GET, POST, DELETE | `/items/{itemId}/links[/{linkId}]` | | Liên kết công việc |
| GET, POST, PUT, DELETE | `/items/{itemId}/comments[/{commentId}]` | `COMMENT_*` | Bình luận |
| GET, POST, DELETE | `/items/{itemId}/attachments[/{attachmentId}]` | | Tệp đính kèm; tải về `/attachments/{id}/download` |
| GET | `/items/{itemId}/changelogs` | `PROJECT_VIEW` | Nhật ký thay đổi |
| GET, POST, PUT, DELETE | `/projects/{projectId}/types[/{typeId}]` | `PROJECT_ADMIN` | Loại công việc |

**Workflow và chuyển trạng thái**

| Phương thức | Đường dẫn | Mô tả |
| :--- | :--- | :--- |
| GET | `/work-items/{id}/transitions` | Các bước chuyển khả dụng (đã lọc Condition) |
| POST | `/work-items/{id}/transitions/{transitionId}` | Thực hiện bước chuyển |
| GET | `/projects/{projectId}/workflow`, `/workflows`, `/workflow/scheme`, `/statuses` | Xem quy trình, sơ đồ, trạng thái |
| PUT | `/projects/{projectId}/workflow/scheme` | Đổi sơ đồ workflow |
| POST, PUT, DELETE | `/statuses`, `/projects/{projectId}/statuses`, `/statuses/{statusId}` | Quản lý trạng thái |
| POST, PUT | `/workflows`, `/workflows/{workflowId}` | Quản lý workflow |
| POST, PUT, DELETE | `/workflows/{workflowId}/transitions[/{transitionId}]` | Quản lý bước chuyển |

**Sprint, Backlog, Board, báo cáo**

| Phương thức | Đường dẫn | Quyền | Mô tả |
| :--- | :--- | :--- | :--- |
| GET, POST | `/projects/{projectId}/sprints` | `PROJECT_VIEW`; `SPRINT_MANAGE` | Liệt kê, tạo Sprint |
| GET, PUT, DELETE | `/sprints/{sprintId}` | `SPRINT_MANAGE` | Xem, sửa, xóa |
| POST | `/sprints/{sprintId}/start`, `/close` (hoặc `/complete`) | `SPRINT_MANAGE` | Bắt đầu; hoàn tất |
| POST, DELETE | `/sprints/{sprintId}/items`, `/items/remove` | `SPRINT_MANAGE` | Thêm, bớt công việc |
| GET | `/projects/{projectId}/backlog` | `PROJECT_VIEW` | Toàn bộ màn hình Backlog trong một lần gọi |
| GET, POST | `/projects/{projectId}/boards` | | Danh sách, tạo Board |
| GET, PUT, DELETE | `/boards/{boardId}` | `PROJECT_ADMIN` (ghi) | Board |
| GET | `/boards/{boardId}/data` | `PROJECT_VIEW` | Dữ liệu Board (cột, thẻ, swimlane) |
| PUT | `/boards/{boardId}/items/{itemId}/move` | `ISSUE_TRANSITION` | Kéo thẻ |
| GET, PUT | `/boards/{boardId}/columns`, `/swimlanes` | `PROJECT_ADMIN` (ghi) | Cấu hình cột, swimlane |
| GET | `/sprints/{sprintId}/burndown` | `PROJECT_VIEW` | Burndown |
| GET | `/projects/{projectId}/velocity`, `/cfd` | `PROJECT_VIEW` | Velocity, CFD |

**Hoàn thành và đóng góp**

| Phương thức | Đường dẫn | Quyền | Mô tả |
| :--- | :--- | :--- | :--- |
| GET | `/projects/{projectId}/completion-requests` | `PROJECT_VIEW` | Yêu cầu đang chờ của dự án |
| GET | `/items/{id}/completion-requests` | `PROJECT_VIEW` | Lịch sử yêu cầu của công việc |
| POST | `/items/{id}/completion/approve`, `/reject` | `PROJECT_ADMIN` | Xác nhận, từ chối (`reject` kèm `{reason}`) |
| DELETE | `/items/{id}/completion` | Người gửi hoặc quản trị viên | Rút lại yêu cầu |
| GET | `/projects/{projectId}/reports/contributions` | `PROJECT_VIEW` | Tổng quan đóng góp |
| GET | `.../contributions/ledger`, `/members/{userId}`, `/export` | `PROJECT_VIEW` | Sổ ghi công, chi tiết, xuất tệp |
| POST | `.../contributions/adjust` | `PROJECT_ADMIN` | Điều chỉnh thủ công |

**Dự án mẫu và GitHub**

| Phương thức | Đường dẫn | Mô tả |
| :--- | :--- | :--- |
| GET | `/templates`, `/templates/{templateId}/preview` | Thư viện mẫu, xem trước |
| POST | `/templates/{templateId}/projects` | Tạo dự án từ mẫu |
| POST | `/projects/{projectId}/save-as-template`, `/clone` | Lưu thành mẫu; nhân bản |
| GET | `/projects/{projectId}/template-check` | Kiểm tra toàn vẹn |
| GET | `/projects/{projectId}/integrations/github/auth-url`, `/status`, `/repos`, `/repos/{owner}/{repo}/branches` | Trạng thái và danh sách kho |
| POST, PUT, DELETE | `.../integrations/github/oauth-callback`, `/connect`, `/config`, `/disconnect`, `/sync-issues`, `/user-account` | Kết nối và đồng bộ |
| POST | `/integrations/github/webhook` | Webhook GitHub (xác thực chữ ký, không cần JWT) |
| GET, POST | `/items/{itemId}/github/development`, `/github/issue` | Commit, PR, Issue gắn với công việc |
| POST | `/admin/notification-jobs/{job}/run` | Chạy tay job thông báo (ADMIN hệ thống) |

### A.4. notification-service

| Phương thức | Đường dẫn | Mô tả |
| :--- | :--- | :--- |
| GET | `/api/v1/notifications` | Danh sách phân trang, có lọc |
| GET | `/api/v1/notifications/unread-count` | Số chưa đọc |
| PATCH | `/api/v1/notifications/{id}` | Đánh dấu đã đọc hoặc chưa đọc |
| POST | `/api/v1/notifications/read-all` | Đánh dấu tất cả đã đọc |
| DELETE, POST | `/api/v1/notifications/{id}`, `/{id}/restore` | Xóa mềm; hoàn tác |
| DELETE | `/api/v1/notifications` | Xóa nhiều (ví dụ các thông báo đã đọc) |
| GET, PUT | `/api/v1/notifications/preferences` | Xem, cập nhật cài đặt |
| PUT, DELETE | `/api/v1/notifications/preferences/projects/{projectId}/mute` | Tắt, bật tiếng dự án |
| POST | `/internal/notifications/email`, `/email/template` | Gửi email nội bộ (không qua gateway) |
| WebSocket | `/ws` | Kênh STOMP; đăng ký `/user/queue/notifications` |

---

## Phụ lục B: Danh mục 53 loại thông báo và ma trận quyền mặc định

### B.1. Danh mục loại thông báo

Mã loại theo `docs/services/notification/1_DANH_MUC_LOAI_THONG_BAO.md` và `NotificationType`. Có 53 loại chia thành chín nhóm.

**A. Công việc (`TASK`, 10 loại)**

| Mã | Sự kiện |
| :-: | :--- |
| A1 | Được giao công việc |
| A2 | Bị chuyển khỏi công việc, hoặc bị hủy giao |
| A3 | Công việc đổi trạng thái |
| A4 | Ưu tiên được nâng lên Cao hoặc Rất cao |
| A5 | Hạn chót thay đổi |
| A6 | Công việc bị xóa |
| A7 | Công việc cha tự mở lại vì còn công việc con chưa xong |
| A8 | Công việc con được hoàn thành theo công việc cha |
| A9 | Bị đóng do trùng lặp |
| A10 | Công việc đổi chu kỳ |

**B. Phụ thuộc (`DEPENDENCY`, 2 loại)**

| Mã | Sự kiện |
| :-: | :--- |
| B1 | Công việc bị cản trở (`BLOCKS`) |
| B2 | Hết bị cản trở |

**C. Xác nhận hoàn thành (`APPROVAL`, 5 loại)**

| Mã | Sự kiện |
| :-: | :--- |
| C1 | Có yêu cầu xác nhận hoàn thành, chờ quản lý (Cần xử lý, có email) |
| C2 | Được xác nhận hoàn thành |
| C3 | Không được xác nhận hoàn thành |
| C4 | Nhắc quản lý: yêu cầu chờ quá lâu (job, Cần xử lý) |
| C5 | Yêu cầu xác nhận bị hủy |

**D. Bình luận (`COMMENT`, 3 loại)**

| Mã | Sự kiện |
| :-: | :--- |
| D1 | Được nhắc tên trong bình luận (có email) |
| D2 | Có bình luận mới |
| D3 | Có người trả lời bình luận của mình |

**E. Chu kỳ, giai đoạn (`SPRINT`, 9 loại)**

| Mã | Sự kiện |
| :-: | :--- |
| E1 | Chu kỳ bắt đầu |
| E2 | Chu kỳ hoàn tất |
| E3 | Chu kỳ quá hạn (job, Cần xử lý) |
| E4 | Chu kỳ sắp kết thúc (job) |
| E5 | Được thêm việc vào chu kỳ đang chạy |
| E6 | Đến ngày bắt đầu chu kỳ tiếp theo (job, Cần xử lý) |
| E7 | Bị đưa ra khỏi chu kỳ đang chạy |
| E8 | Chu kỳ đổi ngày |
| E9 | Chu kỳ bị xóa |

**F. Nhắc hạn (`REMINDER`, 8 loại)**

| Mã | Sự kiện |
| :-: | :--- |
| F1 | Sắp đến hạn (có email) |
| F2 | Đến hạn hôm nay |
| F3 | Quá hạn (có email) |
| F4 | Tổng hợp hằng ngày cho quản lý (email tùy chọn) |
| F5 | Hôm nay là ngày bắt đầu |
| F6 | Lâu không cập nhật |
| F7 | Dự án sắp kết thúc |
| F8 | Dự án quá hạn |

**G. Thành viên và quyền (`MEMBER`, 11 loại)**

| Mã | Sự kiện |
| :-: | :--- |
| G1 | Được mời vào dự án (Cần xử lý, có email) |
| G2 | Bị xóa khỏi dự án |
| G3 | Thành viên rời đi còn việc dở |
| G4 | Lời mời vào dự án được trả lời |
| G5 | Được thêm thẳng vào dự án |
| G6 | Vai trò trong dự án thay đổi |
| G7 | Đổi trưởng dự án |
| G8 | Được mời vào Workspace (Cần xử lý, có email) |
| G9 | Bị xóa khỏi Workspace |
| G10 | Vai trò trong Workspace thay đổi |
| G11 | Lời mời vào Workspace được trả lời |

**H. Dự án (`PROJECT`, 3 loại)**

| Mã | Sự kiện |
| :-: | :--- |
| H1 | Dự án bị lưu trữ |
| H2 | Dự án được khôi phục |
| H3 | Dự án đổi lịch |

**I. Đóng góp (`CONTRIBUTION`, 2 loại)**

| Mã | Sự kiện |
| :-: | :--- |
| I1 | Điểm đóng góp được điều chỉnh |
| I2 | Chốt bảng đóng góp (đóng băng số liệu Sprint) |

Mức độ: `INFO` (thông thường), `SUCCESS` (tích cực), `WARNING` (cảnh báo), `URGENT` (khẩn cấp). Có sáu loại mang cờ "Cần xử lý": C1, C4, E3, E6, G1, G8.

### B.2. Ma trận quyền mặc định của dự án

Ba vai trò mặc định được gieo khi tạo dự án (`ProjectServiceImpl`). Vai trò ADMINISTRATOR có toàn bộ khóa quyền; người tạo hoặc trưởng dự án được tự động gán vai trò này.

| Khóa quyền | ADMINISTRATOR | MEMBER | VIEWER |
| :--- | :-: | :-: | :-: |
| `PROJECT_ADMIN` | ✓ | | |
| `PROJECT_VIEW` | ✓ | ✓ | ✓ |
| `ISSUE_CREATE` | ✓ | ✓ | |
| `ISSUE_EDIT` | ✓ | ✓ | |
| `ISSUE_DELETE` | ✓ | | |
| `ISSUE_TRANSITION` | ✓ | ✓ | |
| `ISSUE_ASSIGN` | ✓ | ✓ | |
| `SPRINT_MANAGE` | ✓ | | |
| `RELEASE_MANAGE` | ✓ | | |
| `COMMENT_CREATE` | ✓ | ✓ | |
| `COMMENT_EDIT_OWN` | ✓ | ✓ | |
| `COMMENT_DELETE_OWN` | ✓ | | |
| `COMMENT_DELETE_ALL` | ✓ | | |
| `ATTACHMENT_DELETE_OWN` | ✓ | | |
| `ATTACHMENT_DELETE_ALL` | ✓ | | |

Ma trận này là giá trị khởi tạo; quản trị viên dự án có thể thay đổi quyền của từng vai trò và tạo thêm vai trò. Thành viên có quyền `ISSUE_TRANSITION` vẫn không đưa được công việc vào nhóm DONE: họ chỉ tạo được yêu cầu "Tạm hoàn thành" (mục 2.4.1).

### B.3. Phân quyền theo phạm vi

| Phạm vi | Vai trò | Quyền chính |
| :--- | :--- | :--- |
| Hệ thống | `ADMIN` | Quản lý người dùng; bỏ qua kiểm tra quyền dự án |
| Hệ thống | `USER`, `USER_CREATE` | Người dùng thông thường |
| Workspace | `OWNER` | Toàn quyền, kể cả xóa Workspace; cũng được quản trị mọi dự án trong Workspace |
| Workspace | `ADMIN` | Quản lý thành viên, mời người; được quản trị mọi dự án trong Workspace |
| Workspace | `MEMBER` | Xem Workspace, tạo dự án mới |
| Dự án | Theo `PermissionKey` | Xem Phụ lục B.2 |

---

## Phụ lục C: Phân công công việc của các thành viên

> Bảng dưới được suy ra từ bản phân công viết báo cáo trước đây trong kho mã (đã xóa khỏi bản làm việc) và lịch sử git. Hãy kiểm tra và chỉnh lại tên, mã sinh viên, lớp cho đúng trước khi nộp.

| Thành viên | Mã sinh viên | Phân hệ phụ trách | Nội dung báo cáo phụ trách |
| :--- | :--- | :--- | :--- |
| Trần Xuân Sơn | (điền) | Lõi nghiệp vụ công việc: dự án, công việc, workflow, xác nhận hoàn thành, Backlog/LexoRank, Sprint, Board, dự án mẫu và nhân bản | Giải pháp ở mục 2.3, 2.4.1, 2.5; thiết kế giao diện 3.3.5; khung kiểm thử đơn vị 4.3; triển khai CI/CD 4.1.4 |
| Nguyễn Viết Tuấn Anh | (điền) | Nền tảng, bảo mật và thông báo: identity, Keycloak, API Gateway, mô hình phân quyền, notification (Outbox, Kafka, WebSocket, email) | Giải pháp ở mục 2.2, 2.6, 2.7; kiến trúc 3.3.1; module thông báo 3.4.1; cài đặt 4.1; Phụ lục A |
| Nguyễn Hữu Tiến | (điền) | Tổ chức, tiến độ và đóng góp: Workspace, thành viên, lời mời, trang quản trị, hồ sơ, báo cáo Agile, báo cáo đóng góp | Giải pháp ở mục 2.2.2, 2.4.2, 2.4.3; đặc tả 3.1; cơ sở dữ liệu 3.3.3; kiểm thử hệ thống 4.2; demo 4.4; ghép và biên tập báo cáo |
