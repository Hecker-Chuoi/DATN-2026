# Danh sách các bước triển khai CI/CD (GitHub Actions + docker compose, một server)

> Hướng đã chốt: **GitHub Actions + Docker Hub + docker compose**, chạy trên **một EC2** (`datn-app`, `t3.large` 2 vCPU / 8 GB): Docker + Nginx + 13 container (6 hạ tầng + 6 service Spring + frontend). **Không còn Jenkins, Kubernetes, Helm.**
>
> Actions build + push image lên **Docker Hub**, rồi **SSH** vào `datn-app` (user `deployer`, khoá riêng, bị khoá chặt chỉ chạy được `deploy.sh`) và chạy `deploy.sh` → `docker compose pull && up -d --wait`.
>
> Nguyên tắc: **deploy tay thành công trước, rồi mới tự động hoá** (bước 0–4 tay, bước 5–6 tự động).
>
> Lịch sử: bản đầu dùng Minikube + Helm (git `e93a83d`), sau đó bỏ Kubernetes để chạy compose, rồi bỏ Jenkins để dùng GitHub Actions (đỡ phải duy trì một server CI và một tài khoản admin nữa).

## Kiến trúc

```
 Developer ── git push main / PR ──► GitHub Actions (runner ubuntu-latest)
                                      ├─ test backend (mvn) + build frontend (npm)
                                      └─ deploy (chỉ main): build 7 image ─► Docker Hub
                                                            tar (compose + cấu hình) │
                                         ssh deployer@<IP public> "deploy <sha>"  ◄──┘  (cổng 22 mở cho 0.0.0.0/0, khoá chặt command=)
                                                │
                     ┌───────────────────────────▼───────────────────────────────────────┐
                     │ EC2 datn-app  (2 vCPU / 8 GB)         ◄── pull image ── Docker Hub│
                     │  Nginx :443                                                       │
                     │   ├─ app.<domain> ─► 127.0.0.1:3000 (frontend → api-gateway)      │
                     │   └─ sso.<domain> ─► 127.0.0.1:8080 (keycloak)                    │
                     │  docker compose (project "datn"):                                 │
                     │   postgres, keycloak, kafka, redis, minio, mailpit                │
                     │   api-gateway, identity, work, workspace, notification, frontend  │
                     └───────────────────────────────────────────────────────────────────┘
```

## Đã xong / cần dọn

- [x] Trong repo: `docker-compose.prod.yml`, `infrastructure/prod/*`, `infrastructure/ci/build-push.sh`, `.github/workflows/ci-cd.yml` (04, 06).
- [x] Đã dựng và chạy thật `datn-app` (xem "Môi trường đã dựng thật" bên dưới).
- [ ] **Dọn**: server `datn-jenkins` **không còn cần** (Jenkins bị bỏ): stop để khỏi tốn tiền, hoặc terminate khi chắc chắn. Khoá SSH `id_deploy` của Jenkins đã bị thu hồi trên `datn-app` (file `authorized_keys` của `deployer` chỉ còn khoá của Actions).
- [ ] **Việc bạn phải làm tay** (cần quyền admin GitHub / AWS): 1) tạo 4–5 secret trên GitHub; 2) mở cổng 22 `0.0.0.0/0` ở Security Group; 3) gắn Elastic IP cho `datn-app`; 4) tạo Docker Hub token mới và thu hồi token cũ đã lộ. Chi tiết ở [06 §4–5](06_GITHUB_ACTIONS_CICD.md#4-mở-cổng-22-trên-security-group).

## Môi trường đã dựng thật (27/11/2026)

| | Giá trị |
|---|---|
| `datn-app` | `t3.large`, Ubuntu 24.04, public `16.179.211.188`, hostname `datn-app` (máy cũ `datn-minikube`: đã gỡ Minikube và dùng lại) |
| Tên miền | `app.16-179-211-188.sslip.io`, `sso.16-179-211-188.sslip.io` (HTTPS Let's Encrypt) |
| Registry | `docker.io/yamaaaaaaaa` (7 repo `datn-*`, public), tag đang chạy: `47a74a1` |
| Đã làm | bước 0–4: ứng dụng chạy, đăng ký → xác thực email → đăng nhập → tạo workspace qua HTTPS; khoá SSH riêng của Actions đã cài (`authorized_keys` 1 dòng, `command=` khoá chặt) và kiểm chứng từ máy ngoài; workflow đã qua `actionlint`; test backend (549) và build frontend đã chạy xanh |
| Lượt Actions đầu (commit `0678483` trên `main`) | test + build + push 6 image **xanh** (tổng ~2,5 phút); **Deploy đỏ `Host key verification failed`** (xem [06 §9](06_GITHUB_ACTIONS_CICD.md#9-kịch-bản-kiểm-tra)); đã sửa workflow, chờ chạy lại. Server vẫn chạy bản `47a74a1` |
| Còn lại | đẩy bản sửa workflow lên `main` để chạy lại; (tuỳ chọn) bật tích hợp GitHub của ứng dụng: [03 §5b](03_SECRET_VA_CAU_HINH.md#5b-tích-hợp-github-oauth-app) |

## Các bước

| # | Việc | Nơi làm | Xong khi |
|---|---|---|---|
| 0 | **[01](01_CHUAN_BI_TAI_KHOAN.md)** — Tài khoản Docker Hub + Access Token (Read & Write), quyền admin GitHub, Elastic IP, tên miền. **[02 §1–3](02_CHUAN_BI_SERVER_UNG_DUNG.md#1-tạo-ec2-datn-app)** — Tạo EC2 `datn-app` (`t3.large`, Elastic IP, Security Group 22 từ máy bạn + 80/443), chuẩn bị máy (timezone, swap, ufw, giới hạn log), cài Docker + Compose | web + `datn-app` | `docker run --rm hello-world` chạy bằng `ubuntu`; `docker compose version` ≥ 2.20 |
| 1 | **[02 §4–6](02_CHUAN_BI_SERVER_UNG_DUNG.md#4-cài-nginx-certbot-jq)** — Cài Nginx + certbot + jq; DNS `app.` / `sso.` → Elastic IP; Nginx proxy vào `127.0.0.1:3000` / `:8080`; HTTPS bằng certbot; đặt tên miền vào `public.env` rồi commit | `datn-app` + repo | `certbot renew --dry-run` thành công; `https://app.<domain>/` có phản hồi (502 là đúng lúc này) |
| 2 | **[03](03_SECRET_VA_CAU_HINH.md)** — Tạo user `deployer` (nhóm `docker`, không `sudo`), `/home/deployer/datn/`, sinh `secrets.env` (17 khoá, quyền 600), sao lưu về máy cá nhân (`secret/`, đã gitignore) | `datn-app`, **không commit** | `secrets.env` có 17 khoá; bản sao lưu có trên Windows |
| 3 | ✅ **[04](04_DOCKER_COMPOSE_PRODUCTION.md) — ĐÃ LÀM trong repo.** `docker-compose.prod.yml` (13 container, healthcheck, `depends_on` theo chuỗi, `mem_limit`), `prod/` (`public.env`, profile Spring `prod`, init Postgres, Nginx mẫu, `deploy.sh`, `make-realm.sh`, `backup-postgres.sh`), workflow Actions | repo | `docker compose config` hợp lệ; việc còn lại của bạn: 04 §7 |
| 4 | **[05](05_TRIEN_KHAI_LAN_DAU.md)** — `build-push.sh` trên Windows (7 image, tag = git SHA); cài `deploy.sh` lên server; `tar \| ssh ... deploy.sh deploy <tag>`; mở trình duyệt thử; đo RAM; thử `rollback`; cron sao lưu | cả hai | **Mở trình duyệt đăng nhập được**; WebSocket `101`; upload file; không container nào `OOMKilled` sau 30 phút dùng thử |
| 5 | **[06 §2–5](06_GITHUB_ACTIONS_CICD.md#2-khoá-ssh-riêng-cho-actions)** — Sinh khoá SSH riêng cho Actions; `authorized_keys` của `deployer` kèm `command="/home/deployer/deploy.sh",no-pty`; mở cổng 22 `0.0.0.0/0`; tạo secret `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`, `DEPLOY_SSH_KEY`, `DEPLOY_HOST` (+ `DEPLOY_KNOWN_HOSTS`) | Windows + `datn-app` + GitHub + AWS | 🪟 `ssh -i secret/actions-deploy-key deployer@<IP> check` chạy được, lệnh `ls` bị từ chối; secret đã có trên GitHub |
| 6 | **[06 §7–9](06_GITHUB_ACTIONS_CICD.md#7-workflow-làm-gì)** — Mở PR (chỉ test), merge `main` (test → build + push → deploy → kiểm tra HTTPS); 7 kịch bản: PR hỏng test, merge `main`, sửa giao diện, deploy lỗi (tự rollback), rollback tay, khoá SSH chặn lệnh lạ, chạy tay nhánh khác | GitHub | Push code → workflow xanh → ứng dụng chạy bản mới |
| 7 | Vận hành: cron `backup-postgres.sh` (05 §8), **không** `docker compose down -v` / `docker volume prune` (02 §7), theo dõi **R8**, xoay vòng Docker Hub token | `datn-app` | Có cron chạy, có file backup |

## Ngân sách tài nguyên `datn-app` (2 vCPU / ~7,6 GB)

Tài khoản chỉ được cấp tối đa `t3.large` (2 vCPU / 7,6 GB). Hạ tầng + 6 JVM + Keycloak ở cấu hình mặc định cần ~7–8 GB → phải giới hạn. Cột `mem_limit` là giá trị **đã viết trong `docker-compose.prod.yml`**, cột "Đo thử" là RAM thật khi 12 container đã lên và rảnh: máy dev (04 §4) và **EC2 thật** (đo ngay sau khi deploy xong; `free -m` cho `available` ≈ 4,9 GB; **chưa đo sau 30 phút dùng thử**, mục R7):

| Thành phần | `mem_limit` | Đo thử | Ghi chú |
|---|---|---|---|
| Ubuntu + Docker + Nginx (ngoài container) | — | ~0,8 GB (ước tính) | |
| keycloak | 1280m | ~893 MiB (dev) / 364 MiB (EC2) | heap 40%, `KC_CACHE=local`, `start` (không `start-dev`) |
| kafka | 768m | ~313 / 350 MiB | KRaft 1 nút, heap 384m, giữ log 72 giờ |
| work-service | 768m | ~415 / 338 MiB | |
| notification / workspace / identity / api-gateway | 640m mỗi cái | dev 330 / 320 / 257 / 236; EC2 295 / 271 / 237 / 160 MiB | |
| postgres (**1 instance**, 2 DB) | 512m | ~106 / 90 MiB | |
| minio / redis / mailpit / frontend | 256m / 96m / 64m / 64m | dev 89 / 8 / 16 / 15; EC2 49 / 3 / 8 / 3 MiB | |
| **Cộng 12 container** | 6,3 GB (giới hạn) | **≈ 3,0 GiB** (dev) / **≈ 2,2 GiB** (EC2, ngay sau deploy) | |

Mục tiêu: `free -h` → `available` ≥ 1 GB khi chạy ổn định, không container nào `OOMKilled`.

Các việc tối ưu — **đã làm** thì đánh dấu, mỗi mục ghi nơi nó nằm trong repo:

- [x] **R1 — Tầng máy** (02 §2): swap 4 GB + `vm.swappiness=10`; tắt `snapd`; giới hạn log container 10 MB × 3 (`daemon.json`) để log không ăn đầy đĩa.
- [x] **R2 — Gộp Postgres** (`prod/postgres/10-init-databases.sh`): **một** `postgres:15-alpine` chứa `datn_main_db` (owner `datn`) + `keycloak` (owner `kc_user`); script chạy `CREATE EXTENSION pgcrypto` bằng superuser (migration V1 của work / workspace cần) và thu hồi `CONNECT` để mỗi user chỉ vào DB của mình. **Đã chạy thử thật** (owner, extension đúng; 5 service chạy được Flyway).
- [x] **R3 — Giới hạn JVM 6 service** (`x-spring-env` trong compose): `-XX:MaxRAMPercentage=65 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError`; **không đặt giới hạn CPU** (bị bóp thì Spring khởi động lâu gấp mấy lần); `hikari.maximum-pool-size: 5` trong từng `application-prod.yaml`.
- [x] **R4 — Hạ tầng có giới hạn**: Kafka `-Xms384m -Xmx384m` + giữ log 72 giờ; **Keycloak limit 1280m (không phải 1 GB)**, heap 40% (repo từng gặp OOMKilled ở 1 GB; không thêm `-XX:+UseSerialGC`); Redis `--maxmemory 64mb --maxmemory-policy allkeys-lru` (không lưu đĩa); Mailpit `MP_MAX_MESSAGES=200`, giao diện chỉ nghe `127.0.0.1`.
- [x] **R5 — Healthcheck + khởi động theo chuỗi** (compose): `start_period: 120s`, `retries: 10` cho Spring; `depends_on … service_healthy` theo chuỗi `identity → work → workspace` (đúng thứ tự migration trên DB trống) và Postgres / Redis / Kafka / MinIO / Keycloak sẵn sàng; notification chờ Kafka (không khởi động được nếu không phân giải được `kafka`).
- [x] **R6 — Deploy kiểu thay thế, không song song**: `docker compose up -d` chỉ tạo lại container có image mới, từng cái một theo chuỗi `depends_on`; hạ tầng giữ nguyên. **Đánh đổi:** mỗi service gián đoạn vài chục giây lúc được thay; cả đợt deploy ước tính 5–8 phút (**chưa đo**) → ghi trong báo cáo là hạn chế do tài nguyên. Có máy 16 GB thì chạy hai bản song song được.
- [ ] **R7 — Đo thực tế rồi chỉnh** (bước 4, [05 §6](05_TRIEN_KHAI_LAN_DAU.md#6-đo-ram-và-chỉnh)): `docker stats --no-stream`, `free -h`, `docker inspect ... OOMKilled`; đặt `mem_limit` ≈ mức dùng cao nhất × 1,3.
- [ ] **R8 — Theo dõi khi vận hành** (bước 7): cột `RestartCount` không tăng; `dmesg -T | grep -i oom` trên host (OOM ở tầng host nguy hiểm hơn OOM của container); `docker system df` cho đĩa. Vẫn OOM thường xuyên dù đã làm R2–R6 → phương án cuối: xin cấp `t3.xlarge`, hoặc tách Keycloak + Kafka sang máy thứ ba.

## Vì sao một server

- Toàn bộ ứng dụng (13 container) dùng khoảng 3 GB RAM lúc rảnh, nên một `t3.large` đủ. Không còn máy build: Actions dùng runner của GitHub (không tốn RAM / đĩa của bạn), image đi qua Docker Hub.
- Tài khoản chỉ được cấp tối đa `t3.large` (2 vCPU / 7,6 GB); bản Minikube + Jenkins từng phải dùng 2 máy.

## Lưu ý

- Runner GitHub vào server bằng **IP public** (Elastic IP) và cổng 22 mở cho mọi IP; khoá SSH bị khoá chặt bằng `command="…deploy.sh"`, `deploy.sh` thuộc root nên CI không tự sửa được.
- `deployer` thuộc nhóm `docker` (≈ quyền root trên máy `datn-app`) nhưng **không có `sudo`**.
- Muốn tiết kiệm: stop `datn-app` khi không dùng; Docker + `restart: unless-stopped` tự bật lại các container khi máy start (chờ vài phút vì chuỗi khởi động). IP public đổi nếu chưa gắn Elastic IP.
- Bản cũ (Minikube + Helm) còn trong lịch sử git tại commit `e93a83d`; bản có Jenkins ở `bc02081`.
