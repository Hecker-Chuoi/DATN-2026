# 03 — User `deployer`, thư mục dữ liệu và `secrets.env`

> Thuộc [00 — Danh sách các bước](00_DANH_SACH_CAC_BUOC.md) (**bước 2**). Làm **sau** [02](02_CHUAN_BI_SERVER_UNG_DUNG.md): `datn-app` đã có Docker, Nginx, HTTPS.
>
> Kết quả: trên `datn-app` có user `deployer` (nhóm `docker`), thư mục `/home/deployer/datn/` và file **`secrets.env`** (15 khoá, quyền 600) chứa mọi mật khẩu. File này **chỉ nằm trên server + bản sao lưu cá nhân của bạn, không commit**.
>
> Quy ước: 🪟 = PowerShell / Git Bash trên **máy Windows**; 🐧 = phiên **SSH vào `datn-app`** (`ubuntu@datn-app:~$`). **Đừng dán lệnh Linux vào PowerShell và ngược lại.**

## Mục lục

1. [Cấu hình nằm ở đâu](#1-cấu-hình-nằm-ở-đâu)
2. [Tạo user `deployer` và thư mục dữ liệu](#2-tạo-user-deployer-và-thư-mục-dữ-liệu)
3. [Sinh mật khẩu vào `secrets.env`](#3-sinh-mật-khẩu-vào-secretsenv)
4. [Sao lưu `secrets.env` về máy cá nhân](#4-sao-lưu-secretsenv-về-máy-cá-nhân)
5. [Gửi mail thật (Gmail) thay cho Mailpit](#5-gửi-mail-thật-gmail-thay-cho-mailpit)
   - [5b. Tích hợp GitHub (OAuth App)](#5b-tích-hợp-github-oauth-app)
6. [Đổi mật khẩu về sau](#6-đổi-mật-khẩu-về-sau)
7. [Không được làm](#7-không-được-làm)
8. [Xử lý sự cố](#8-xử-lý-sự-cố)
9. [Checklist](#9-checklist)

---

## 1. Cấu hình nằm ở đâu

| File | Nằm ở | Commit? | Chứa |
|---|---|---|---|
| `infrastructure/prod/public.env` | repo | **có** | tên miền, `https`, registry Docker Hub, cấu hình SMTP không bí mật |
| `/home/deployer/datn/secrets.env` | **chỉ server** | **không** | mật khẩu Postgres, Keycloak, MinIO, SMTP |
| `/home/deployer/datn/realm.json` | chỉ server (sinh tự động) | không | realm Keycloak đã chèn client secret + redirect URI |

`docker-compose.prod.yml` đọc cả hai file `.env` (`--env-file public.env --env-file secrets.env`) và truyền từng biến cho đúng container. Spring Boot ưu tiên **biến môi trường** hơn `application.yaml` nên không phải sửa code.

Hiện mật khẩu đang **viết cứng** trong repo (chỉ hợp cho máy dev): `postgres` / `123456` trong `application.yaml`, Keycloak admin `admin` / `admin`, client secret `datn-backend` trong `identity-service/application.yaml` và realm export, MinIO `minioadmin` / `minioadminpassword`. Trên production **thay toàn bộ** bằng giá trị ngẫu nhiên:

| Khoá trong `secrets.env` | Thành phần dùng | Biến compose gán |
|---|---|---|
| `POSTGRES_PASSWORD` | Postgres superuser `postgres` (chỉ quản trị + script khởi tạo) | `POSTGRES_PASSWORD` |
| `APP_DB_USERNAME`, `APP_DB_PASSWORD` | User **`datn`** sở hữu DB `datn_main_db`; identity, work, workspace, notification | Postgres init; 4 service: `SPRING_DATASOURCE_USERNAME/PASSWORD` |
| `KC_DB_USERNAME`, `KC_DB_PASSWORD` | User **`kc_user`** sở hữu DB `keycloak` (cùng một instance Postgres) | Postgres init; Keycloak `KC_DB_USERNAME/PASSWORD` |
| `KEYCLOAK_ADMIN_USERNAME`, `KEYCLOAK_ADMIN_PASSWORD` | Admin realm `master`; identity-service gọi Admin API | Keycloak `KEYCLOAK_ADMIN(_PASSWORD)`; identity `KEYCLOAK_ADMIN_USERNAME/PASSWORD` |
| `KEYCLOAK_CLIENT_SECRET` | Client `datn-backend` | identity `KEYCLOAK_CLIENT_SECRET`; `make-realm.sh` ghi vào realm trước khi Keycloak import |
| `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD` | MinIO; work-service (bucket `work-attachments`) | MinIO `MINIO_ROOT_*`; work `MINIO_ACCESS_KEY/SECRET_KEY` |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | notification-service gửi email | `MAIL_USERNAME`, `MAIL_PASSWORD` (để **trống** nếu dùng Mailpit) |
| `GITHUB_CLIENT_SECRET`, `GITHUB_TOKEN_ENCRYPTION_KEY`, `GITHUB_STATE_SECRET` | Tích hợp GitHub của work-service ([5b](#5b-tích-hợp-github-oauth-app)); `GITHUB_CLIENT_SECRET` để trống tới khi có OAuth App | work-service `GITHUB_*` |

Redis và Kafka **không có mật khẩu** (giống dev) vì chỉ truy cập được trong mạng Docker nội bộ `app-net`, không publish cổng ra ngoài.

---

## 2. Tạo user `deployer` và thư mục dữ liệu

`deployer` là user chỉ dùng để triển khai (GitHub Actions SSH vào bằng khoá riêng, ở [06](06_GITHUB_ACTIONS_CICD.md)). Nó thuộc nhóm `docker` nên chạy được `docker compose`, **không** có `sudo`. Mọi file dữ liệu của ứng dụng thuộc user này.

🐧:

```bash
sudo adduser --disabled-password --gecos "" deployer
sudo usermod -aG docker deployer
sudo -u deployer install -d -m 700 /home/deployer/datn
sudo ls -ld /home/deployer/datn                 # drwx------ deployer deployer
```

> Ai có quyền `docker` ≈ có quyền root trên máy này. Vì vậy khoá SSH của `deployer` được khoá chặt chỉ chạy `deploy.sh` ([06 §3](06_GITHUB_ACTIONS_CICD.md#3-cho-khoá-vào-deployer-trên-server-)).

---

## 3. Sinh mật khẩu vào `secrets.env`

Sinh bằng `openssl rand -hex` (chỉ gồm `0-9a-f`, không vướng ký tự đặc biệt trong JDBC URL / YAML / shell). Mật khẩu chỉ **ngẫu nhiên một lần** lúc chạy lệnh rồi cố định trong file; Postgres / Keycloak / MinIO là bản **mới**, lần đầu khởi động tự tạo tài khoản bằng đúng các giá trị này, còn service đọc cùng file → luôn khớp.

⚠️ **Chỉ chạy một lần.** Chạy lại = bộ mật khẩu mới ghi đè file, mà DB giữ mật khẩu cũ → ứng dụng không đăng nhập được DB. Lệnh đầu tiên bên dưới **dừng lại** nếu file đã có; đổi mật khẩu thì làm theo mục 6.

🐧:

```bash
sudo test -e /home/deployer/datn/secrets.env && echo "ĐÃ CÓ secrets.env — DỪNG, KHÔNG chạy các lệnh dưới" || echo "chưa có, tiếp tục"
```

Chỉ khi in `chưa có, tiếp tục`:

```bash
umask 077
cat > /tmp/secrets.env <<EOF
POSTGRES_PASSWORD=$(openssl rand -hex 24)
APP_DB_USERNAME=datn
APP_DB_PASSWORD=$(openssl rand -hex 24)
KC_DB_USERNAME=kc_user
KC_DB_PASSWORD=$(openssl rand -hex 24)
KEYCLOAK_ADMIN_USERNAME=admin
KEYCLOAK_ADMIN_PASSWORD=$(openssl rand -hex 16)
KEYCLOAK_CLIENT_SECRET=$(openssl rand -hex 16)
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=$(openssl rand -hex 24)
MAIL_USERNAME=
MAIL_PASSWORD=
GITHUB_CLIENT_SECRET=
GITHUB_TOKEN_ENCRYPTION_KEY=$(openssl rand -hex 32)
GITHUB_STATE_SECRET=$(openssl rand -hex 32)
EOF
sudo install -o deployer -g deployer -m 600 /tmp/secrets.env /home/deployer/datn/secrets.env
shred -u /tmp/secrets.env
sudo ls -l /home/deployer/datn/secrets.env           # -rw------- deployer deployer
sudo grep -c '=' /home/deployer/datn/secrets.env     # 15
```

Mật khẩu admin Keycloak (cần khi đăng nhập Admin Console ở 05): `sudo grep '^KEYCLOAK_ADMIN' /home/deployer/datn/secrets.env`.

---

## 4. Sao lưu `secrets.env` về máy cá nhân

Mất file này **và** volume Postgres thì không còn cách nào biết mật khẩu DB cũ. Chép một bản về thư mục `secret/` của repo trên Windows (thư mục này **đã nằm trong `.gitignore`**).

🪟 **Git Bash** ở `D:\DATN-2026` (mở cửa sổ mới, **không** dùng cửa sổ SSH; dùng Git Bash thay vì PowerShell vì `>` của PowerShell 5.1 ghi UTF-16 làm hỏng file):

```bash
mkdir -p secret
ssh -i <KEY>.pem ubuntu@<ELASTIC_IP> "sudo cat /home/deployer/datn/secrets.env" > secret/datn-secrets.env
grep -c '=' secret/datn-secrets.env            # 15
git status --short secret                      # rỗng = đã bị gitignore
```

---

## 5. Gửi mail thật (Gmail) thay cho Mailpit

Mặc định compose có **Mailpit**: mail **được giữ lại** chứ không gửi đi → người đăng ký mới **không nhận được** link xác thực email (tài khoản `INACTIVE`, đăng nhập bị 403), bạn phải mở Mailpit bấm link thay họ. Đủ cho demo tự làm; muốn người khác tự đăng ký thì dùng Gmail:

1. Google Account → bật xác minh 2 bước → tạo **App Password** (16 ký tự).
2. 🪟 Sửa `infrastructure/prod/public.env` rồi **commit**:
   ```
   MAIL_HOST=smtp.gmail.com
   MAIL_PORT=587
   MAIL_SMTP_AUTH=true
   MAIL_SMTP_STARTTLS=true
   MAIL_FROM="DATN-2026 <địa-chỉ-gmail>"
   ```
3. 🐧 Điền `MAIL_USERNAME` / `MAIL_PASSWORD` (App Password viết liền, không dấu cách) vào `secrets.env`:
   ```bash
   sudo -u deployer nano /home/deployer/datn/secrets.env
   ```
4. Deploy lại ([05 §4](05_TRIEN_KHAI_LAN_DAU.md#4-deploy-lần-đầu) hoặc đẩy commit lên `main` cho Actions) để notification-service đọc cấu hình mới.

---

## 5b. Tích hợp GitHub (OAuth App)

Từ khi nhánh `develop` gộp tính năng **tích hợp GitHub** (work-service, migration V11–V16), `work-service` đọc thêm cấu hình GitHub. Thiếu cấu hình thì **service vẫn chạy bình thường** (đã thử: V11–V16 chạy tiếp được trên DB đang ở V10, service `healthy`), chỉ là nút kết nối GitHub chưa dùng được và token GitHub (nếu có) được lưu **không mã hoá**.

| Biến | Nằm ở | Ý nghĩa |
|---|---|---|
| `GITHUB_CLIENT_ID` | `public.env` (commit) | Client ID của GitHub OAuth App (không bí mật). Để trống = tắt tính năng |
| `GITHUB_CLIENT_SECRET` | `secrets.env` | Client Secret của OAuth App. Mặc định trống |
| `GITHUB_TOKEN_ENCRYPTION_KEY` | `secrets.env` | Khoá mã hoá token GitHub lưu trong DB (AES-GCM). Đã sinh ngẫu nhiên sẵn. **Đổi khoá = token cũ không giải mã được**, người dùng phải kết nối lại |
| `GITHUB_STATE_SECRET` | `secrets.env` | Khoá ký tham số `state` của OAuth. Đã sinh ngẫu nhiên sẵn |
| `GITHUB_REDIRECT_URI` | compose (tự sinh) | `https://<APP_HOST>/integrations/github/callback` |
| `GITHUB_WEBHOOK_URL` | compose (tự sinh) | `https://<APP_HOST>/work/api/v1/work/integrations/github/webhook` (GitHub gọi không kèm JWT; work-service xác thực bằng chữ ký HMAC; gateway và Nginx đã cho qua) |

Muốn bật tính năng (🌐 trên GitHub, 🐧 trên server, 🪟 trong repo):

1. 🌐 GitHub → Settings → Developer settings → **OAuth Apps** → *New OAuth App* (hoặc sửa app đang dùng cho dev, nhưng mỗi app chỉ có **một** callback URL nên nên tạo app riêng cho production):
   - *Homepage URL*: `https://<APP_HOST>`
   - *Authorization callback URL*: `https://<APP_HOST>/integrations/github/callback`
2. Bấm *Generate a new client secret*, chép **Client ID** và **Client Secret**.
3. 🪟 Điền Client ID vào `infrastructure/prod/public.env` (`GITHUB_CLIENT_ID=...`) rồi commit.
4. 🐧 Điền Client Secret vào `secrets.env`:
   ```bash
   sudo -u deployer nano /home/deployer/datn/secrets.env      # GITHUB_CLIENT_SECRET=<secret>
   ```
5. Deploy lại (đẩy commit lên `main`, hoặc [05 §4](05_TRIEN_KHAI_LAN_DAU.md#4-deploy-lần-đầu)) để work-service nạp cấu hình mới. Kiểm tra log không còn dòng cảnh báo `Chưa cấu hình github.token-encryption-key`: `docker logs datn-work-service-1 2>&1 | grep -i github`.

Webhook của từng repo do chính ứng dụng tự đăng ký khi kết nối repo (cần `GITHUB_WEBHOOK_URL` truy cập được từ Internet: đã đúng với `https://<APP_HOST>`).

---

## 6. Đổi mật khẩu về sau

- **Mật khẩu DB (`APP_DB_PASSWORD`, `KC_DB_PASSWORD`, `POSTGRES_PASSWORD`)**: script khởi tạo Postgres chỉ chạy lúc volume còn trống, nên sửa file **không** đổi mật khẩu trong DB. Làm hai việc: `docker exec -it $(docker ps -qf label=com.docker.compose.service=postgres) psql -U postgres -c "ALTER USER datn PASSWORD '<mới>'"` rồi sửa `secrets.env` và deploy lại (container nạp biến mới khi được tạo lại).
- **`KEYCLOAK_ADMIN_PASSWORD`**: Keycloak chỉ đọc biến này lần đầu tạo tài khoản admin. Đổi trong Admin Console, rồi sửa `secrets.env` cho khớp (identity-service dùng nó gọi Admin API).
- **`KEYCLOAK_CLIENT_SECRET`**: đổi trong Admin Console (client `datn-backend` → Credentials), sửa `secrets.env`, deploy lại identity-service. Sửa `realm.json` không có tác dụng sau lần import đầu.
- **`MINIO_ROOT_PASSWORD`**: MinIO nhớ mật khẩu root trong volume; đổi bằng `mc admin user` hoặc xoá volume (mất file đính kèm). Với đồ án: giữ nguyên.

---

## 7. Không được làm

- Commit `secrets.env`, `realm.json` hoặc dán mật khẩu vào `public.env` / `docker-compose.prod.yml`.
- Chạy lại lệnh sinh mật khẩu ở mục 3 khi đã có file.
- Sinh lại `secrets.env` mà **giữ** volume Postgres: DB giữ mật khẩu cũ nên ứng dụng không vào được DB. Ngược lại, xoá volume mà giữ `secrets.env` thì ổn (DB mới tạo bằng đúng mật khẩu trong file).
- Cho `deployer` quyền `sudo`.

---

## 8. Xử lý sự cố

| Triệu chứng | Cách xử lý |
|---|---|
| `adduser: The user 'deployer' already exists` | Đã tạo (hoặc còn từ lần cài cũ): bỏ qua, kiểm tra `id deployer` có nhóm `docker` |
| `sudo install: cannot create regular file ... Permission denied` | Chưa tạo `/home/deployer/datn` (mục 2) |
| Compose báo `thiếu POSTGRES_PASSWORD trong secrets.env` | File thiếu khoá hoặc sai tên; so với bảng mục 1 (`grep -c '=' ...` phải ra 12) |
| Ứng dụng `password authentication failed for user "datn"` | `secrets.env` khác mật khẩu đang nằm trong volume Postgres: mục 6 (`ALTER USER`) |

---

## 9. Checklist

- [ ] `id deployer` có nhóm `docker`; `/home/deployer/datn` quyền 700
- [ ] `sudo ls -l /home/deployer/datn/secrets.env` → `-rw------- deployer`, `grep -c '='` = 15
- [ ] Đã chép bản sao về `secret/datn-secrets.env` trên Windows (không bị git thấy)

Xong → [04 — Docker compose production](04_DOCKER_COMPOSE_PRODUCTION.md) (giải thích những gì có trong repo) rồi [05 — Triển khai lần đầu](05_TRIEN_KHAI_LAN_DAU.md).
