# Tích hợp GitHub (work-service) — đợt 1: liên kết tài khoản và repo

Tích hợp GitHub là **tùy chọn của dự án phần mềm** (`projects.project_type` ≠ `BUSINESS`). Dự án Kinh doanh không thấy UI GitHub và backend trả 400 khi liên kết repo.

## Kiến trúc

- Code: `backend/work-service/src/main/java/com/datn/workservice/integration/github/` (entity, repository, `GithubService`, 2 controller, `GithubApi` dùng `RestClient`, `TokenCrypto` AES-GCM). Không service mới, không route gateway mới ngoài whitelist webhook.
- Migration `V6__github_integration.sql`: `github_connections` (token user, mã hóa), `github_repo_links` (1 repo ↔ 1 dự án), `work_item_dev_links` (branch/commit/PR theo issue, đợt sau mới ghi).
- Hai cấp: **cá nhân** kết nối GitHub một lần (OAuth App) → **dự án** admin chọn repo từ danh sách của mình, hệ thống tự tạo webhook.

## Cấu hình (bắt buộc trước khi dùng)

1. Tạo OAuth App: GitHub → Settings → Developer settings → OAuth Apps → New OAuth App.
   - Homepage URL: `http://localhost:5173`
   - Authorization callback URL: `http://localhost:5173/github/callback`
   - Lấy Client ID, bấm Generate a new client secret.
2. `backend/work-service/.env` (copy từ `.env.example`, gitignored):
   ```
   GITHUB_CLIENT_ID=...
   GITHUB_CLIENT_SECRET=...
   GITHUB_WEBHOOK_SECRET=<openssl rand -hex 20>
   GITHUB_TOKEN_ENC_KEY=<openssl rand -base64 32>
   APP_PUBLIC_URL=https://xxxx.ngrok-free.app      # chỉ cần khi muốn GitHub gọi được webhook
   ```
   Không có `.env` thì vẫn chạy với secret dev mặc định, nhưng không kết nối được GitHub (thiếu client id).
3. Restart work-service và api-gateway.

Scope xin khi authorize: `repo read:user` (`repo` cần để tạo webhook, tạo branch, đọc repo private).

## Endpoint (qua gateway `/work/api/v1/work/...`)

| Method | Path | Quyền | Việc |
|---|---|---|---|
| GET | `/github/oauth/authorize-url?redirectUri=` | JWT | Trả URL GitHub, state lưu Redis 10 phút |
| POST | `/github/oauth/callback {code,state,redirectUri}` | JWT | Đổi code lấy token, lưu mã hóa |
| GET / DELETE | `/github/me` | JWT | Trạng thái kết nối / ngắt |
| GET | `/github/repos` | JWT | Repo của user (100 mới nhất) |
| GET | `/projects/{id}/github/repo` | PROJECT_VIEW | Link hiện tại hoặc null |
| PUT | `/projects/{id}/github/repo {owner,name,autoOnPrOpen,autoOnPrMerge}` | PROJECT_ADMIN | Liên kết + tạo webhook; cùng repo thì chỉ đổi toggle |
| DELETE | `/projects/{id}/github/repo` | PROJECT_ADMIN | Gỡ + xóa webhook (best-effort) |
| POST | `/github/webhook` | public, HMAC `X-Hub-Signature-256` | Đợt này chỉ xác thực và log |

## Màn hình

- Trang cá nhân: card "Tài khoản liên kết" (`components/profile/GithubConnectCard.jsx`), redirect toàn trang sang GitHub, về `/github/callback` (`pages/GithubCallbackPage.jsx`).
- Cấu hình dự án → sub-tab "Tích hợp GitHub" (`components/project/GithubIntegrationTab.jsx`), chỉ hiện khi `isSoftwareProject(project) && isAdmin`.
- Tạo dự án: card thứ 3 "Dự án Kinh doanh" (BUSINESS). Dự án BUSINESS dùng board Kanban.

## Webhook và ngrok

GitHub không gọi được localhost. Khi dev: `ngrok http 9991` (nhóm: 8081) → đặt `APP_PUBLIC_URL` → restart work-service → liên kết repo → kiểm tra GitHub repo Settings → Webhooks → Recent Deliveries trả 200. URL ngrok free đổi mỗi lần chạy: sửa `.env`, gỡ rồi liên kết lại repo. Không có ngrok: liên kết vẫn thành công, chỉ deliver lỗi.

Test endpoint không cần GitHub (Git Bash):
```bash
SECRET=change-me   # = GITHUB_WEBHOOK_SECRET
BODY='{"repository":{"full_name":"o/r","id":1}}'
SIG=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac "$SECRET" | sed 's/^.* //')
curl -i -X POST http://localhost:9991/work/api/v1/work/github/webhook -H "Content-Type: application/json" \
  -H "X-GitHub-Event: ping" -H "X-Hub-Signature-256: sha256=$SIG" --data-binary "$BODY"   # 200; sai chữ ký -> 403
```

## Đợt sau (chưa làm)

Xử lý nội dung webhook (push / pull_request / create / delete → `work_item_dev_links`), panel "Phát triển" trên issue, nút "Tạo branch", tự chuyển trạng thái theo PR qua `WorkflowEngineService.executeTransition`. Thiết kế chi tiết đã có trong plan.
