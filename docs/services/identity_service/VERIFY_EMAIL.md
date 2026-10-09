# Xác thực email khi đăng ký & đặt lại mật khẩu (identity-service)

## Luồng

```
Đăng ký  ──► Keycloak user enabled=false, users.status=INACTIVE
         ──► verification_tokens (EMAIL_VERIFY, 24h, dùng 1 lần)
         ──► notification-service POST /internal/notifications/email ──► SMTP (Mailpit khi dev)
Email    ──► {APP_FRONTEND_URL}/verify-email?token=...
FE       ──► GET /identity/api/v1/auth/verify-email?token=...
         ──► KeycloakService.activateVerifiedUser (enabled + emailVerified) + status=ACTIVE
Login    ──► UserService.assertEmailVerified: INACTIVE ⇒ 403, FE hiện nút "Gửi lại email xác thực"
```

Quên mật khẩu: `POST /auth/forgot-password` (luôn trả 200 để không lộ email tồn tại) → mail link `/reset-password?token=` (PASSWORD_RESET, 30 phút) → `POST /auth/reset-password`. Vì link chỉ đến được qua email, **reset thành công cũng kích hoạt tài khoản** nếu đang INACTIVE (`AccountVerificationService.activateIfPending`).

Gửi mail là fail-safe: lỗi SMTP chỉ ghi log, đăng ký vẫn 201; người dùng dùng `POST /auth/resend-verification` (`identifier` = email hoặc username).

## Endpoint (public, qua gateway `/identity/api/v1/auth/...`)

| Method | Path | Ghi chú |
|---|---|---|
| GET | `/verify-email?token=` | 400 nếu token sai / đã dùng / hết hạn |
| POST | `/resend-verification` `{identifier}` | 400 nếu tài khoản đã ACTIVE |
| POST | `/forgot-password` `{email}` | luôn 200 |
| POST | `/reset-password` `{token,newPassword}` | kích hoạt nếu INACTIVE |

## Dữ liệu

Bảng `verification_tokens` (identity-service, `ddl-auto: update`, không Flyway): `user_id`, `token` (unique, 32 byte SecureRandom base64url), `type` (EMAIL_VERIFY | PASSWORD_RESET), `expires_at`, `used_at`. Mỗi lần phát token mới sẽ xóa token cũ cùng loại của user, nên tối đa 2 dòng/user — không cần job dọn token hết hạn.

## Cấu hình

| Service | Biến | Mặc định |
|---|---|---|
| identity-service | `APP_FRONTEND_URL` | `http://localhost:5173` (compose nhóm app.yml: 3000) |
| identity-service | `APP_SERVICES_NOTIFICATION_URL` | `http://localhost:9994` (compose: `http://notification-service:9994`) |
| identity-service | `app.verification.email-ttl-hours` / `reset-ttl-minutes` | 24 / 30 |
| notification-service | `MAIL_HOST/MAIL_PORT/MAIL_USERNAME/MAIL_PASSWORD/MAIL_SMTP_AUTH/MAIL_SMTP_STARTTLS/MAIL_FROM` | Mailpit `mailpit:1025`, không auth |

SMTP thật (Gmail): `MAIL_HOST=smtp.gmail.com MAIL_PORT=587 MAIL_USERNAME=... MAIL_PASSWORD=<app password> MAIL_SMTP_AUTH=true MAIL_SMTP_STARTTLS=true`.

Bảo mật: `/internal/notifications/email` không có auth (giống `/internal/**` của identity-service). Cổng 9994 chỉ bind `127.0.0.1` trong mọi compose; container khác gọi qua docker network `app-net`. `ponytail:` thêm shared-secret header khi deploy ra ngoài docker network.

## Kịch bản test thủ công (compose cá nhân + Mailpit)

1. `cd infrastructure && docker compose -f docker-compose.tuananh.yml up -d --build`; `docker port notification-service` phải in `127.0.0.1:9994`.
2. `cd frontend && npm run dev` → đăng ký user mới → thấy "đã gửi email xác thực tới ...". Login ngay → 403 + nút "Gửi lại email xác thực".
3. Mở http://localhost:8025 → mail "[DATN-2026] Xác thực email của bạn" → bấm link → trang `/verify-email` báo thành công → login được.
4. Bấm lại link cũ → 400 "Liên kết này đã được sử dụng".
5. Đăng ký user thứ hai, KHÔNG verify → `/forgot-password` → mail "Đặt lại mật khẩu" → đặt mật khẩu mới → login thành công; DB `users.status=ACTIVE`, Keycloak Admin user `enabled=true`.
6. `docker logs notification-service` có dòng gửi mail; `docker logs identity-service` không có `Không gửi được email`.
