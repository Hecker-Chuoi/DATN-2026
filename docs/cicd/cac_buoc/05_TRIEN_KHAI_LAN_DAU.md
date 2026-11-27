# 05 — Triển khai lần đầu bằng tay và kiểm tra

> Thuộc [00 — Danh sách các bước](00_DANH_SACH_CAC_BUOC.md) (**bước 4**). Làm **sau** [02](02_CHUAN_BI_SERVER_UNG_DUNG.md) (Docker, Nginx, HTTPS), [03](03_SECRET_VA_CAU_HINH.md) (`deployer`, `secrets.env`) và [04 §7](04_DOCKER_COMPOSE_PRODUCTION.md#7-việc-của-bạn-trước-khi-sang-bước-4) (`public.env` đã commit).
>
> Nguyên tắc: **deploy tay thành công trước, rồi mới giao cho GitHub Actions** (bước 5). Tay ở đây vẫn dùng đúng `deploy.sh` mà Actions sẽ gọi, để cái bạn thử là cái chạy thật.
>
> Kết quả: `https://app.<domain>` mở được, đăng ký → xác thực email → đăng nhập → tạo dự án và công việc, WebSocket `101`, upload file được; đã ghi lại RAM thật.
>
> Quy ước: 🪟 = PowerShell hoặc Git Bash trên **máy Windows**; 🐧 = phiên **SSH vào `datn-app`** (`ubuntu@datn-app:~$`). **Đừng dán lệnh Linux vào PowerShell và ngược lại.**

## Mục lục

1. [Điều kiện](#1-điều-kiện)
2. [Build và push 7 image (🪟 Windows)](#2-build-và-push-7-image--windows)
3. [Cài `deploy.sh` lên server](#3-cài-deploysh-lên-server)
4. [Deploy lần đầu](#4-deploy-lần-đầu)
5. [Mở trình duyệt thử](#5-mở-trình-duyệt-thử)
6. [Đo RAM và chỉnh](#6-đo-ram-và-chỉnh)
7. [Thử rollback](#7-thử-rollback)
8. [Sao lưu Postgres định kỳ](#8-sao-lưu-postgres-định-kỳ)
9. [Đăng nhập Google (tuỳ chọn)](#9-đăng-nhập-google-tuỳ-chọn)
10. [Xử lý sự cố](#10-xử-lý-sự-cố)
11. [Checklist](#11-checklist)

---

## 1. Điều kiện

- [ ] 02: `docker run --rm hello-world` chạy bằng `ubuntu`; HTTPS cho `app.` và `sso.` đã cấp (502 là bình thường lúc này).
- [ ] 03: `/home/deployer/datn/secrets.env` có 17 khoá.
- [ ] 04 §7: `infrastructure/prod/public.env` có `APP_HOST`, `SSO_HOST`, `REGISTRY` thật và **đã commit** (`git status --short infrastructure/prod/public.env` rỗng).

---

## 2. Build và push 7 image (🪟 Windows)

Làm trên máy Windows (có Docker Desktop, Git Bash, repo), không cần server. Ba điều kiện:

1. Repo đang ở **commit có `public.env` đã điền**. `build-push.sh` đọc registry và URL Keycloak từ file đó (frontend **nhúng** `VITE_KEYCLOAK_URL` lúc build, không đổi được khi chạy).
2. Có **Access Token Docker Hub quyền Read & Write** (cùng loại với secret `DOCKERHUB_TOKEN` của Actions, [01 §2](01_CHUAN_BI_TAI_KHOAN.md#2-docker-hub-tài-khoản-và-access-token)).
3. 7 repo Docker Hub sẽ **public** (gói miễn phí chỉ cho 1 repo private): `datn-api-gateway`, `datn-identity-service`, `datn-work-service`, `datn-workspace-service`, `datn-notification-service`, `datn-ai-service`, `datn-frontend`. Docker Hub tự tạo repo ở lần push đầu; vào kiểm tra đều `Public`.

🪟 **PowerShell** (đăng nhập tương tác ở đây vì Git Bash dễ treo ở bước nhập mật khẩu):

```powershell
docker login -u <DOCKERHUB_USER>        # mật khẩu = Access Token Read & Write, KHÔNG phải mật khẩu tài khoản
```

🪟 **Git Bash** ở gốc repo:

```bash
git status --short infrastructure/prod/public.env              # rỗng = đã commit
TAG=$(git rev-parse --short=7 HEAD); echo "$TAG"               # ghi lại: dùng ở mục 4
bash infrastructure/ci/build-push.sh "$TAG"
```

Script build tuần tự 7 image (6 backend build Maven bên trong Docker, rồi frontend), push từng cái, kết thúc bằng `✅ Đã push 7 image với tag <TAG> lên docker.io/<user>`. **Ước tính** lần đầu 15–30 phút (tải thư viện Maven, `npm ci`); các lần sau nhanh hơn nhờ cache BuildKit. Chưa cài `trivy` thì script in "Bỏ qua quét" — không sao.

Xong, 🪟 PowerShell:

```powershell
docker logout                           # xoá token khỏi máy
docker manifest inspect docker.io/<DOCKERHUB_USER>/datn-api-gateway:<TAG>     # in JSON = đã public và kéo được
```

---

## 3. Cài `deploy.sh` lên server

`deploy.sh` thuộc **root** (user `deployer` chạy được nhưng không tự sửa được). Cài một lần; sửa script về sau thì cài lại bằng đúng lệnh này.

🪟 **Git Bash** ở gốc repo:

```bash
scp -i <KEY>.pem infrastructure/prod/scripts/deploy.sh ubuntu@<ELASTIC_IP>:/tmp/deploy.sh
```

🐧 Trên server:

```bash
sudo install -m 755 -o root -g root /tmp/deploy.sh /home/deployer/deploy.sh
sudo -u deployer /home/deployer/deploy.sh check          # docker x.y, compose v2.x, "tag đang chạy: <chưa có>"
```

(Nếu `check` báo `permission denied ... docker.sock` thì `deployer` chưa trong nhóm `docker`: [03 §2](03_SECRET_VA_CAU_HINH.md#2-tạo-user-deployer-và-thư-mục-dữ-liệu).)

---

## 4. Deploy lần đầu

Gói deploy = `docker-compose.prod.yml` + `prod/` + `keycloak/`. 🪟 **Git Bash** ở gốc repo (`<TAG>` là SHA ở mục 2):

```bash
tar cz -C infrastructure docker-compose.prod.yml prod keycloak \
  | ssh -i <KEY>.pem -o ServerAliveInterval=30 ubuntu@<ELASTIC_IP> "sudo -u deployer /home/deployer/deploy.sh deploy <TAG>"
```

`deploy.sh` làm theo thứ tự:

1. giải nén gói vào `/home/deployer/datn/releases/<TAG>/`; từ chối nếu `public.env` còn `example.invalid` / `CHANGE_ME`;
2. **lần đầu**: chạy `make-realm.sh` sinh `/home/deployer/datn/realm.json` (cần `jq`, đã cài ở 02 §4);
3. `docker compose config -q`, `pull`, rồi `up -d --wait` (chờ mọi container `healthy`, tối đa 20 phút);
4. **smoke test** qua `http://127.0.0.1:3000/{identity,work,workspace}/actuator/health` và trang chủ;
5. in `✅ DATN-2026@<TAG> đã chạy`; lỗi thì in log của container hỏng rồi tự về bản trước (lần đầu chưa có bản trước nên chỉ báo lỗi).

Mở **cửa sổ SSH thứ hai** để theo dõi:

```bash
watch -n 5 'docker ps --format "table {{.Names}}\t{{.Status}}"'
```

Dự kiến (**ước tính, chưa đo trên EC2**): `postgres`, `redis`, `mailpit`, `minio`, `kafka` lên đầu (1–3 phút, tính cả kéo image); `keycloak` **3–10 phút** (tự build + import realm); rồi chuỗi `identity-service` → `work-service` → `workspace-service`; `api-gateway`, `notification-service` song song; `frontend` cuối. Cả đợt ~10–15 phút. Hết `--wait-timeout` thì chạy lại với `DEPLOY_TIMEOUT=2000` đặt trước `deploy.sh` (trong lệnh `sudo -u deployer env DEPLOY_TIMEOUT=2000 /home/deployer/deploy.sh ...`).

Log lỗi `DataSeeder ... seed các dự án mẫu` của work-service ở lần đầu: xem [04 §6](04_DOCKER_COMPOSE_PRODUCTION.md#6-hiện-tượng-lạ-ở-lần-chạy-đầu-không-phải-lỗi), không phải lỗi deploy.

Cài xong, 🐧:

```bash
sudo -u deployer /home/deployer/deploy.sh check      # 13 container (healthy), tag đang chạy: <TAG>
```

---

## 5. Mở trình duyệt thử

### 5.1. Keycloak Admin Console

🪟 Mở `https://<SSO_HOST>/admin/` → đăng nhập bằng `KEYCLOAK_ADMIN_USERNAME` / `KEYCLOAK_ADMIN_PASSWORD` (🐧 `sudo grep '^KEYCLOAK_ADMIN' /home/deployer/datn/secrets.env`) → góc trên trái đổi realm sang **`datn-realm`**; phải thấy client `datn-backend`.

### 5.2. Đăng ký, xác thực email, đăng nhập

1. 🪟 Mở `https://<APP_HOST>` → trang đăng nhập → **Đăng ký** một tài khoản (email bất kỳ, ví dụ `thu@example.com`).
2. Thư xác thực nằm trong Mailpit (không có Nginx / cổng công khai, xem qua đường hầm SSH). 🪟 **PowerShell**, giữ cửa sổ mở:
   ```powershell
   ssh -i <KEY>.pem -L 18025:127.0.0.1:8025 ubuntu@<ELASTIC_IP>
   ```
   Mở `http://localhost:18025` (cổng 18025 để không đụng Mailpit dev ở 8025), bấm thư "xác thực email" → bấm link `https://<APP_HOST>/verify-email?token=...`.
3. Đăng nhập. Tạo một Workspace → một Dự án → vài công việc, kéo thả giữa các cột.

### 5.3. Những điều phải đúng

| Kiểm tra | Cách thấy | Nếu sai |
|---|---|---|
| Đăng nhập thành công, không 401 | tạo được workspace | mục 10: "mọi API 401" |
| Request ghi (POST/PUT) không 403 | tạo dự án, đổi trạng thái công việc | mục 10: "POST trả 403" |
| WebSocket thông báo | DevTools (F12) → Network → WS → `notification/ws` trạng thái `101 Switching Protocols`; chuông không báo lỗi kết nối | mục 10: "WebSocket không nối" |
| Upload đính kèm | đính kèm file ~5 MB vào công việc | mục 10: "413 / upload lỗi" |
| Link trong thư đúng tên miền | link xác thực bắt đầu bằng `https://<APP_HOST>` | sai `APP_PUBLIC_URL`: `docker exec <identity> printenv APP_PUBLIC_URL` |

### 5.3b. Kiểm tra nhanh bằng dòng lệnh (không cần trình duyệt)

Đã chạy thật khi dựng. 🪟 Git Bash (đổi `<APP_HOST>` / `<SSO_HOST>`; đăng ký dùng email giả, Mailpit giữ thư):

```bash
A=https://<APP_HOST>; H="Origin: $A"; J="Content-Type: application/json"
curl -s -o /dev/null -w "%{http_code}\n" $A/                                             # 200
curl -s https://<SSO_HOST>/realms/datn-realm/.well-known/openid-configuration | grep -o '"issuer":"[^"]*"'   # issuer = https://<SSO_HOST>/realms/datn-realm
curl -s -X POST $A/identity/api/v1/auth/register -H "$H" -H "$J" -d '{"username":"e2etest","password":"E2eTest#2026","email":"e2e@example.com","firstName":"E2E","lastName":"Test"}'   # success:true
# lấy link xác thực từ Mailpit trên server (🐧: curl -s http://127.0.0.1:8025/api/v1/messages ; .../message/<ID>), rồi:
curl -s "$A/identity/api/v1/auth/verify-email?token=<TOKEN>" -H "$H"                      # "Xác thực email thành công"
curl -s -X POST $A/identity/api/v1/auth/login -H "$H" -H "$J" -c cj.txt -d '{"username":"e2etest","password":"E2eTest#2026"}'   # "Login successful"
curl -s -X POST $A/workspace/api/v1/workspaces -H "$H" -H "$J" -b cj.txt -d '{"name":"E2E"}'      # "Đã tạo workspace" (POST không bị 403)
curl -s -i -H "$H" -H "Connection: Upgrade" -H "Upgrade: websocket" -H "Sec-WebSocket-Version: 13" -H "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==" -b cj.txt $A/notification/ws | head -1   # 101 Switching Protocols
```

Danh sách workspace của người dùng là `GET /workspace/api/v1/workspaces/my` (gọi `GET /workspaces` trơn trả 500 do code chưa xử lý 405, không phải lỗi deploy). Xoá tài khoản thử sau khi xong (Admin Console → Users).

### 5.4. Gán quyền ADMIN (nếu cần trang quản trị)

Realm không có user nào, tài khoản đầu tiên chỉ là `USER`. Muốn thử chức năng quản trị: Admin Console → realm `datn-realm` → **Users** → chọn user → **Role mapping** → **Assign role** → `ADMIN`. Đăng xuất / đăng nhập lại để token có role mới.

---

## 6. Đo RAM và chỉnh

Dùng thử khoảng 30 phút (nhiều tab, kéo thả, tạo vài chục công việc, đính kèm file) để JVM ổn định rồi mới đo. 🐧:

```bash
docker stats --no-stream --format 'table {{.Name}}\t{{.MemUsage}}\t{{.MemPerc}}'
free -h                                                        # "available" >= 1 Gi
docker ps -a --format '{{.Names}} {{.Status}}'                 # không container nào "Restarting" hoặc Exited
docker inspect -f '{{.Name}} restarts={{.RestartCount}} oom={{.State.OOMKilled}}' $(docker ps -aq)   # restarts=0, oom=false
sudo dmesg -T | grep -iE "out of memory|killed process" | tail   # OOM ở cấp máy chủ: phải rỗng
```

Đối chiếu với mốc ở [04 §4](04_DOCKER_COMPOSE_PRODUCTION.md#4-ngân-sách-ram) và điền:

| Container | Mức dùng | `mem_limit` hiện tại | `mức dùng × 1,3` | Sửa limit? |
|---|---|---|---|---|
| keycloak | | 1280m | | |
| kafka | | 768m | | |
| work-service | | 768m | | |
| identity / workspace / notification / api-gateway | | 640m | | |
| postgres | | 512m | | |

Quy tắc: nếu mức dùng cao nhất × 1,3 **vượt** `mem_limit` thì nâng `mem_limit` của chính container đó trong `docker-compose.prod.yml` (commit, rồi deploy lại). Container `oom=true` hoặc `restarts` tăng: nâng limit của nó trước. `available` < 1 Gi hoặc có `dmesg` OOM: **dừng**, giảm tải (bỏ Mailpit nếu dùng SMTP thật; hạ `max_connections` Postgres) trước khi tăng.

Số liệu thời gian cũng ghi lại để thay các **ước tính** trong tài liệu: thời gian từ lúc chạy `deploy.sh` tới `✅`, thời gian Keycloak lên lần đầu.

---

## 7. Thử rollback

Làm sau khi đã có **hai** bản deploy (deploy thêm một commit khác, ví dụ sửa chữ trong giao diện). 🐧:

```bash
sudo -u deployer /home/deployer/deploy.sh check                      # tag đang chạy: <B>
sudo -u deployer /home/deployer/deploy.sh rollback                   # về <A>; smoke test; "✅ Đang chạy <A>"
sudo -u deployer /home/deployer/deploy.sh check                      # tag đang chạy: <A>
```

`rollback` **không** hoàn tác migration DB (Flyway chỉ chạy tiến). Deploy bản có migration mới thì sao lưu trước (mục 8).

Thử deploy lỗi có chủ đích (kiểm tra tự rollback): deploy một SHA chưa được push image → `pull` báo lỗi, `deploy.sh` in chẩn đoán và về bản trước, thoát mã khác 0.

---

## 8. Sao lưu Postgres định kỳ

🐧 Bằng user `deployer` (cron của nó):

```bash
( sudo -u deployer crontab -l 2>/dev/null; echo '0 2 * * * /home/deployer/datn/current/prod/scripts/backup-postgres.sh >> /home/deployer/datn/backup.log 2>&1' ) | sudo -u deployer crontab -
sudo -u deployer /home/deployer/datn/current/prod/scripts/backup-postgres.sh      # chạy thử một lần
ls -lh /home/deployer/datn/backups/                                               # datn_main_db-*.dump, keycloak-*.dump
```

Script giữ 7 ngày. File sao lưu cũng nằm trên cùng máy, nên định kỳ chép về máy cá nhân (`scp`) hoặc bật snapshot EBS cho đĩa.

---

## 9. Đăng nhập Google (tuỳ chọn)

Google Cloud Console → Credentials → OAuth client đang dùng → thêm **Authorized redirect URI**: `https://<SSO_HOST>/realms/datn-realm/broker/google/endpoint`. Realm đã mang sẵn cấu hình Google từ file export. Thử nút "Đăng nhập bằng Google".

> Realm export **trong git chứa secret Google OAuth thật**. Nếu repo có người ngoài đọc được, nên đổi secret Google trong Google Cloud Console rồi cập nhật realm.

---

## 10. Xử lý sự cố

| Triệu chứng | Nguyên nhân / cách xử lý |
|---|---|
| `deploy.sh` chạy chỉ in `find: Failed to change directory: /home/ubuntu: Permission denied` rồi thoát im lặng | **Lỗi đã gặp và đã sửa** (`cd /` ở đầu `deploy.sh`): `sudo -u deployer` giữ thư mục hiện tại của người gọi (`/home/ubuntu`), `deployer` không vào được. Cài lại bản `deploy.sh` mới ở mục 3 |
| Keycloak `Restarting`, log `AccessDeniedException: /opt/keycloak/bin/../data/import/realm.json` | **Lỗi đã gặp và đã sửa** (`chmod 644` trong `make-realm.sh`): `realm.json` sinh với quyền 600 của `deployer`, Keycloak chạy uid 1000 trong container không đọc được. Sửa tay trên server: `sudo chmod 644 /home/deployer/datn/realm.json` (container tự khởi động lại), rồi deploy lại |
| `deploy.sh`: "Thiếu /home/deployer/datn/secrets.env" | Chưa làm [03](03_SECRET_VA_CAU_HINH.md), hoặc gọi bằng user khác `deployer` (`sudo -u deployer`) |
| `deploy.sh`: "public.env chưa đặt APP_HOST / REGISTRY" | Chưa chạy `set-public-host.sh` hoặc quên commit trước khi tạo gói (04 §7) |
| `pull access denied` / `manifest unknown` | Chưa push image với đúng `<TAG>` (mục 2), tên `REGISTRY` sai, hoặc repo Docker Hub đang private (cần `docker login` bằng `deployer` với token Read-only) |
| `unhealthy` / `up --wait` hết hạn | `docker compose -p datn logs <service>` (hoặc đọc phần chẩn đoán `deploy.sh` đã in). Keycloak lần đầu chậm: nâng `DEPLOY_TIMEOUT` |
| keycloak `Restarting` / `OOMKilled` | Ai đó sửa `JAVA_OPTS_KC_HEAP`; trả về `-XX:MaxRAMPercentage=40 ...` (04 §3, mục 12) |
| identity-service chờ mãi | Keycloak chưa `healthy`: `docker logs datn-keycloak-1 \| tail` |
| `workspace-service` chờ rất lâu | Đang chờ work-service (chuỗi khởi động): `docker logs datn-work-service-1 \| tail` |
| Postgres `password authentication failed` | `secrets.env` khác mật khẩu trong volume: [03 §6](03_SECRET_VA_CAU_HINH.md#6-đổi-mật-khẩu-về-sau) |
| Mọi API trả **401** (đăng nhập xong vẫn bị đá ra) | `iss` của token khác `KEYCLOAK_ISSUER_URI`. So `curl -s https://<SSO_HOST>/realms/datn-realm/.well-known/openid-configuration \| jq -r .issuer` với `docker exec datn-identity-service-1 printenv KEYCLOAK_ISSUER_URI`; phải **giống từng ký tự** (kể cả `https`). Khác = `SCHEME`/`SSO_HOST` đổi sau khi cài Keycloak: xoá volume Keycloak (hoặc cả DB `keycloak`) và import lại realm |
| POST/PUT trả **403**, WebSocket không nối | Nginx máy chủ thiếu `X-Forwarded-Proto` / `X-Forwarded-Host` / `Upgrade` (dùng đúng `prod/nginx/datn-app.conf`), hoặc mở frontend bằng IP / cổng khác với `APP_HOST` (CORS gateway chỉ cho `APP_CORS_ALLOWED_ORIGINS`) |
| Upload lỗi **413** | Thiếu `client_max_body_size 50m` ở Nginx máy chủ (`sudo nginx -T \| grep client_max`) hoặc image frontend cũ chưa có dòng này trong `nginx.conf` |
| Trang trắng / **502** từ Nginx máy chủ | Container `frontend` chưa lên: `docker ps`, `curl -I http://127.0.0.1:3000/` |
| certbot / HTTPS lỗi | [02 §6, §8](02_CHUAN_BI_SERVER_UNG_DUNG.md) |
| Thư xác thực không thấy trong Mailpit | `docker logs datn-notification-service-1 \| tail -30`; Mailpit: `docker ps --filter name=mailpit` |
| Sau reboot ứng dụng không lên | `restart: unless-stopped` tự bật container khi Docker lên; chờ vài phút (khởi động theo chuỗi). `systemctl is-enabled docker nginx` phải `enabled` |

---

## 11. Checklist

- [ ] 7 image `datn-*` đã push với tag `<TAG>`, repo Docker Hub **public**; đã `docker logout`
- [ ] `deploy.sh deploy <TAG>` chạy tới `✅`, kể cả smoke test; `check` báo 13 container `healthy`
- [ ] `https://<SSO_HOST>/admin/` vào được Admin Console, thấy client `datn-backend`
- [ ] **Mở trình duyệt, đăng ký, xác thực email, đăng nhập, tạo dự án và công việc được**; WebSocket `101`; upload file được
- [ ] Dùng thử 30 phút: không container nào `restarts` tăng / `oom=true`; `free -h` available ≥ 1 Gi; đã điền bảng mục 6 và cập nhật số "Đo thử" ở [04 §4](04_DOCKER_COMPOSE_PRODUCTION.md#4-ngân-sách-ram)
- [ ] Đã thử `rollback` (mục 7); cron `backup-postgres.sh` chạy được (mục 8)
- [ ] Đã ghi lại `<TAG>` đang chạy

Xong → [06 — CI/CD bằng GitHub Actions](06_GITHUB_ACTIONS_CICD.md).
