# Hướng dẫn sau khi pull: xác thực email, notification-service, lời mời workspace

Áp dụng cho branch `feat/noti-verify-email` (và các branch merge từ nó). Đọc hết mục 1–4 trước khi chạy, mục 5–7 khi cần.

## 1. Có gì mới

| Thành phần | Thay đổi |
|---|---|
| `backend/notification-service` (:9994) | Service mới, chỉ gửi email qua SMTP, không đi qua gateway. Template HTML nằm ở `src/main/resources/templates/` (`forgot-password.html`, `invite-member.html`). |
| identity-service | Đăng ký tạo user Keycloak `enabled=false` + `users.status=INACTIVE`, gửi mail xác thực. Login khi chưa xác thực bị 403. Thêm quên mật khẩu qua email. Bảng mới `verification_tokens`. |
| work-service | Mời vào nhóm/dự án gửi thêm email. |
| workspace-service | Thêm thành viên workspace chuyển thành **lời mời** (bảng mới `workspace_invitations`, migration V4). |
| frontend | Trang `/verify-email`, `/forgot-password`, `/reset-password`; trang Lời mời gộp cả 3 loại workspace/nhóm/dự án. |
| infrastructure | Compose có thêm `notification-service` và `mailpit`. 4 Dockerfile COPY thêm `workspace-service/pom.xml`. |

## 2. Database: không cần chạy SQL tay

- **work-service** và **workspace-service** dùng Flyway, tự apply migration lúc boot. workspace-service có `V4__workspace_invitations.sql` mới, log boot sẽ in `Migrating schema "public" to version "4 - workspace invitations"`.
- **identity-service** dùng `ddl-auto: update`, tự tạo bảng `verification_tokens`.
- **Dữ liệu cũ**: user tạo trước thay đổi này vẫn `ACTIVE`, đăng nhập bình thường. Chỉ user đăng ký mới phải xác thực email.
- **Keycloak**: không cần import lại realm, không cần mapper mới.

Nếu boot workspace-service báo `Schema-validation: missing table [workspace_invitations]` nghĩa là Flyway chưa chạy (thường do cấu hình datasource sai). Kiểm tra `flyway_workspace_schema_history` trong DB `datn_main_db`.

## 3. Email: mặc định không cần cấu hình gì

Mặc định notification-service gửi vào **Mailpit** (SMTP `localhost:1025`, xem mail tại http://localhost:8025). Mailpit có sẵn trong compose nhóm (`docker-compose.yml`, `docker-compose.infra.yml`). Không cần tài khoản, không cần mật khẩu.

### Muốn gửi Gmail thật

1. Vào https://myaccount.google.com/security, bật **Xác minh 2 bước**.
2. Vào https://myaccount.google.com/apppasswords, tạo App password, nhận mã 16 ký tự.
3. Tạo file `.env` (đã gitignore, **không commit**):
   - Chạy từ IntelliJ: copy `backend/notification-service/.env.example` → `backend/notification-service/.env`.
   - Chạy bằng Docker: tạo `infrastructure/.env` cùng nội dung.
   ```
   MAIL_HOST=smtp.gmail.com
   MAIL_PORT=587
   MAIL_USERNAME=ten-ban@gmail.com
   MAIL_PASSWORD=xxxx xxxx xxxx xxxx
   MAIL_SMTP_AUTH=true
   MAIL_SMTP_STARTTLS=true
   MAIL_FROM=DATN-2026 <ten-ban@gmail.com>
   ```
4. Restart notification-service. Gmail không cho `MAIL_FROM` khác tài khoản đăng nhập.

Mỗi người dùng Gmail riêng của mình. Không chia sẻ App password qua chat; lộ thì vào lại trang App passwords xóa và tạo cái mới.

## 4. Chạy

### 4a. Toàn bộ bằng Docker (cổng nhóm 808x)

```bash
cd infrastructure
docker compose -f docker-compose.yml up -d --build
```
Hoặc tách hạ tầng/app: `docker compose -f docker-compose.infra.yml up -d` rồi `docker compose -f docker-compose.app.yml up -d --build`.

Lưu ý: `work-service` và `workspace-service` chưa có trong compose, phải chạy từ IDE.

### 4b. Hạ tầng Docker + service từ IntelliJ

```bash
cd infrastructure && docker compose -f docker-compose.infra.yml up -d   # Postgres, Keycloak 8088, Mailpit, Redis, Kafka, MinIO
```
Chạy lần lượt từ IntelliJ (không cần VM option nếu dùng cổng nhóm):

| Service | Cổng |
|---|---|
| identity-service | 8082 |
| notification-service | 9994 |
| work-service | 8083 |
| workspace-service | 8087 |
| api-gateway | 8081 |

Frontend: `cd frontend && npm install && npm run dev` → http://localhost:5173.

Biến môi trường có giá trị mặc định, chỉ đổi khi chạy khác máy:

| Biến | Service | Mặc định |
|---|---|---|
| `APP_FRONTEND_URL` | identity, work, workspace | `http://localhost:5173` (link trong email trỏ về đây) |
| `APP_SERVICES_NOTIFICATION_URL` | identity, work, workspace | `http://localhost:9994` |
| `MAIL_*` | notification | Mailpit |

## 5. Kiểm tra nhanh sau khi chạy

1. Đăng ký tài khoản mới trên frontend. Thấy thông báo "đã gửi email xác thực".
2. Đăng nhập ngay phải bị chặn với nút "Gửi lại email xác thực".
3. Mở http://localhost:8025, bấm link trong mail "[DATN-2026] Xác thực email của bạn". Đăng nhập được.
4. Quên mật khẩu: mail "Đặt lại mật khẩu" dùng template `forgot-password.html`.
5. Vào Thành viên workspace, mời một user khác: toast "Đã gửi lời mời", bảng thành viên chưa có người đó, mail "Lời mời tham gia workspace ...". Người được mời vào trang Lời mời (badge sidebar) để chấp nhận.
6. Mời vào nhóm/dự án cũng có mail tương ứng.

Xóa user test để đăng ký lại cùng email:
```bash
KC_URL=http://localhost:8088 bash infrastructure/delete-test-user.sh <username|email>
```
(script mặc định trỏ Keycloak 9998 của máy cá nhân, nhóm dùng 8088 nên đặt `KC_URL`).

## 6. Sự cố thường gặp

| Hiện tượng | Nguyên nhân / cách xử lý |
|---|---|
| `docker compose --build` fail `Child module /app/workspace-service of /app/pom.xml does not exist` | Dockerfile thiếu dòng `COPY workspace-service/pom.xml`. Branch này đã sửa, kiểm tra 4 Dockerfile có 6 dòng COPY pom. |
| Đăng ký xong không có mail, log identity có `Không gửi được email tới ...` | notification-service chưa chạy hoặc sai `APP_SERVICES_NOTIFICATION_URL`. Đăng ký vẫn thành công, người dùng bấm "Gửi lại email xác thực" sau khi service lên. |
| Log notification `Authentication failed` | Sai App password hoặc chưa bật xác minh 2 bước. |
| Log notification `Connection timed out` tới smtp.gmail.com:587 | Mạng chặn cổng 587, dùng Mailpit hoặc đổi mạng. |
| Frontend gọi `/workspace/...` bị "No static resource ..." | Gateway thiếu route `/workspace/**`. File nhóm `api-gateway/application.yml` đã có; nếu dùng profile cá nhân thì profile đó phải khai lại đủ route vì `routes` bị ghi đè toàn bộ. |
| Mời thành viên báo 400 "Đã có lời mời đang chờ" | Người đó đang có lời mời PENDING. Hủy ở block "Lời mời đang chờ" rồi mời lại. |
| Chấp nhận lời mời workspace xong vẫn không vào được project | work-service cache role workspace 10 phút. Chờ hoặc restart work-service. Xem `docs/services/workspace_service/WORKSPACE_AUTHZ_REVIEW.md`. |

## 7. Thêm template email mới

1. Tạo `backend/notification-service/src/main/resources/templates/<ten>.html`, placeholder dạng `{{key}}`.
2. Service gửi gọi `POST {notification}/internal/notifications/email/template` với `{to, subject, template: "<ten>", vars: {key: value}}`. identity-service dùng `NotificationClient.sendTemplate(...)`, work/workspace dùng Feign `NotificationClient.sendTemplate(new TemplateEmailRequest(...))`.
3. Giá trị trong `vars` được escape HTML, không cần tự escape.

Tài liệu liên quan: `docs/services/identity_service/VERIFY_EMAIL.md` (luồng chi tiết), `infrastructure/docker-setup.md`.
