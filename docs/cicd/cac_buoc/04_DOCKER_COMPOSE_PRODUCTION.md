# 04 — Docker compose production: có gì trong repo và vì sao viết như vậy

> Thuộc [00 — Danh sách các bước](00_DANH_SACH_CAC_BUOC.md) (**bước 3**). **Đã làm sẵn trong repo**, tài liệu này giải thích để bạn hiểu và sửa được; không có lệnh nào bắt buộc chạy, trừ mục 7 (việc của bạn trước khi sang bước 4).
>
> Thay thế bản Helm + Minikube cũ (đã xoá khỏi nhánh, còn trong lịch sử git: `git show e93a83d:infrastructure/helm/...`).

## Mục lục

1. [Cây thư mục](#1-cây-thư-mục)
2. [Cách các file ghép với nhau](#2-cách-các-file-ghép-với-nhau)
3. [Những quyết định thiết kế đáng biết](#3-những-quyết-định-thiết-kế-đáng-biết)
4. [Ngân sách RAM](#4-ngân-sách-ram)
5. [Đã kiểm chứng gì, chưa kiểm chứng gì](#5-đã-kiểm-chứng-gì-chưa-kiểm-chứng-gì)
6. [Hiện tượng lạ ở lần chạy đầu (không phải lỗi)](#6-hiện-tượng-lạ-ở-lần-chạy-đầu-không-phải-lỗi)
7. [Việc của bạn trước khi sang bước 4](#7-việc-của-bạn-trước-khi-sang-bước-4)

---

## 1. Cây thư mục

```
Jenkinsfile                                   # test → build/push → SSH deploy (06 §5)
frontend/nginx.conf                           # đã thêm client_max_body_size 50m (upload đính kèm)
infrastructure/
├── docker-compose.prod.yml                   # 12 container: 6 hạ tầng + 5 service Spring + frontend
├── ci/
│   └── build-push.sh                         # build + (quét) + push 6 image; đọc registry / SSO_HOST từ public.env
├── prod/
│   ├── public.env                            # tên miền, scheme, registry, SMTP: NƠI DUY NHẤT (commit)
│   ├── postgres/10-init-databases.sh         # 2 database, 2 user, pgcrypto (chạy một lần khi volume trống)
│   ├── spring/<service>/application-prod.yaml  # profile Spring "prod", mount vào /config của từng service
│   ├── nginx/datn-app.conf                   # mẫu Nginx của MÁY CHỦ (02 §6)
│   └── scripts/
│       ├── set-public-host.sh                # đặt tên miền / registry vào public.env (máy dev)
│       ├── make-realm.sh                     # sinh realm.json cho Keycloak (deploy.sh gọi một lần)
│       ├── deploy.sh                         # CHẠY TRÊN SERVER: deploy / rollback / check (06)
│       └── backup-postgres.sh                # pg_dump hai database (cron, 05 §8)
├── keycloak/realm-export_08082026.json       # realm gốc (đi theo gói deploy)
└── docker-compose*.yml (yml, .app, .infra, .tuananh)   # BẢN DEV của nhóm: không đụng tới
```

Các Dockerfile (6 cái) **không đổi**: `frontend/Dockerfile` đã có sẵn `ARG VITE_KEYCLOAK_URL`.

---

## 2. Cách các file ghép với nhau

```
 máy dev (Windows)                       Jenkins                          server ứng dụng (datn-app)
 ─────────────────                       ───────                          ──────────────────────────
 git push ──────────────────────────────► build-push.sh <sha>
                                           ├─ đọc public.env (REGISTRY, SSO_HOST)
                                           └─ push 6 image ─► Docker Hub
                                          tar cz docker-compose.prod.yml prod keycloak
                                           └─ ssh deployer@… "deploy <sha>" ─► deploy.sh
                                                                               ├─ giải nén → ~/datn/releases/<sha>/
                                                                               ├─ (lần đầu) make-realm.sh → ~/datn/realm.json
                                                                               ├─ docker compose pull + up -d --wait
                                                                               ├─ smoke test qua 127.0.0.1:3000
                                                                               └─ lỗi → tự về bản trước
 Internet ─► Nginx :443 ─┬─ app.<domain> ─► 127.0.0.1:3000 ─► frontend (nginx) ─► api-gateway ─► 4 service
                         └─ sso.<domain> ─► 127.0.0.1:8080 ─► keycloak
```

Compose đọc biến từ **ba nguồn**:

| Nguồn | Ví dụ biến | Ai đặt |
|---|---|---|
| `prod/public.env` (commit) | `SCHEME`, `APP_HOST`, `SSO_HOST`, `REGISTRY`, `MAIL_*` | bạn, bằng `set-public-host.sh` |
| `~/datn/secrets.env` (chỉ server) | `POSTGRES_PASSWORD`, `KEYCLOAK_CLIENT_SECRET`… | bạn, một lần ([03](03_SECRET_VA_CAU_HINH.md)) |
| môi trường lúc chạy | `IMAGE_TAG` (git SHA), `DATN_HOME` | `deploy.sh` |

Dữ liệu bền nằm ở **3 volume có tên**: `datn_postgres_data`, `datn_kafka_data`, `datn_minio_data` (tên project cố định `datn` bằng `name:` trong compose và `-p datn` trong `deploy.sh`). Deploy chỉ tạo lại container, không đụng volume.

---

## 3. Những quyết định thiết kế đáng biết

1. **Không dùng Kubernetes, không dùng Ingress.** Nginx của máy chủ → container `frontend` (cổng `127.0.0.1:3000`). Container `frontend` vừa phục vụ file tĩnh vừa proxy `/identity`, `/work`, `/workspace`, `/notification` vào `api-gateway:8081` (đúng `frontend/nginx.conf` đang dùng ở dev, có WebSocket). Nginx máy chủ chỉ cần chuyển *mọi thứ* của `app.<domain>` tới một cổng. Keycloak có cổng riêng `127.0.0.1:8080` cho `sso.<domain>`.
2. **Chỉ 3 cổng publish, đều `127.0.0.1`**: `3000` (frontend), `8080` (Keycloak), `8025` (giao diện Mailpit). Postgres, Kafka, Redis, MinIO không mở cổng nào ra máy chủ.
3. **Issuer Keycloak**: token mang `iss` = URL công khai (`KC_HOSTNAME_URL=https://sso.<domain>`), các service khai `issuer-uri` công khai nhưng lấy khoá qua `jwk-set-uri` **nội bộ** `http://keycloak:8080/...`. Sai chỗ này → mọi API trả 401. Đổi tên miền / `scheme` sau khi cài là phải xoá realm (volume Keycloak) và đăng nhập lại.
4. **Route gateway**: `application-prod.yaml` của api-gateway khai đủ 4 route (danh sách `routes` trong profile **thay thế toàn bộ** danh sách gốc) và **cố ý không** route `/notification/internal/**`, `/workspace/internal/**` (gửi mail nội bộ, không xác thực). Thêm service mới = thêm route vào cả `application-tuananh.yml` lẫn file này.
5. **Chuỗi khởi động bằng `depends_on` + `condition: service_healthy`**: `identity → work → workspace`. Trên DB trống, Flyway của workspace-service cần bảng `projects` do work-service tạo; identity tạo bảng `users` trước. Notification chờ Kafka (không khởi động được nếu không phân giải được `kafka`); frontend chờ `api-gateway` (nginx phân giải tên lúc khởi động, thiếu là không lên).
6. **Healthcheck không cần công cụ ngoài**: Spring dùng `wget` của busybox (có sẵn trong image Alpine) tới `/actuator/health/readiness`; Keycloak (không có curl/wget) dùng `/dev/tcp` của bash tới `/health/ready`; Postgres `pg_isready -h 127.0.0.1` (có `-h` vì lúc chạy script khởi tạo, server tạm chỉ nghe unix socket → thiếu `-h` thì báo healthy quá sớm); Kafka `nc -z`; MinIO `mc ready local` (giống compose dev).
7. **Một Postgres, hai database** (`datn_main_db` owner `datn`, `keycloak` owner `kc_user`), user ứng dụng **không phải superuser**. Migration V1 của work / workspace có `CREATE EXTENSION pgcrypto` → script khởi tạo tạo sẵn bằng superuser.
8. **Realm Keycloak**: `make-realm.sh` chèn client secret (từ `secrets.env`) và redirect URI / web origin (từ `public.env`) vào bản export, giữ nguyên cấu hình Google. Keycloak chạy `start --import-realm`: **chỉ import khi realm chưa tồn tại**, sửa `realm.json` về sau không có tác dụng (sửa trong Admin Console).
9. **Upload 50 MB**: container frontend và Nginx máy chủ đều `client_max_body_size 50m` (nginx mặc định chỉ 1 MB; ở bản Minikube Ingress đứng trước nên không bị).
10. **Deploy kiểu thay thế, có gián đoạn ngắn**: `docker compose up -d` chỉ tạo lại container có image mới (5 service + frontend; hạ tầng giữ nguyên). Mỗi service tắt rồi bật nên có khoảng gián đoạn vài chục giây, kéo dài hơn vì chuỗi `depends_on`. Chấp nhận được cho đồ án; muốn không gián đoạn cần chạy 2 bản song song (tốn thêm ~2 GB RAM) → không làm.
11. **`app.cluster.redis-enabled: true`** giữ nguyên trong profile `prod` của work / notification: một instance vẫn chạy bình thường, Redis có sẵn nên không cần tắt.
12. **JVM**: `-XX:MaxRAMPercentage=65 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError`; heap tính theo `mem_limit` của container. `ExitOnOutOfMemoryError` + `restart: unless-stopped` = hết bộ nhớ thì container tự khởi động lại thay vì treo. Keycloak **không** thêm `-XX:+UseSerialGC` (`kc.sh` đã chọn ParallelGC, hai cờ cùng lúc làm JVM không khởi động) và heap 40% (50% từng bị OOMKilled / sát limit).

---

## 4. Ngân sách RAM

Máy `t3.large` có ~7,6 GB; HĐH + Docker + Nginx chiếm ~0,8 GB. Cột "Đo thử" là RAM thật khi **cả 12 container đã lên và rảnh** (Docker Desktop trên máy dev, JVM giống hệt; số trên EC2 sẽ hơi khác, đo lại ở [05 §6](05_TRIEN_KHAI_LAN_DAU.md#6-đo-ram-và-chỉnh)):

| Container | `mem_limit` | Đo thử |
|---|---|---|
| keycloak | 1280m | ~893 MiB |
| kafka (`-Xms384m -Xmx384m`, giữ log 72 giờ) | 768m | ~313 MiB |
| work-service | 768m | ~415 MiB |
| notification-service | 640m | ~330 MiB |
| workspace-service | 640m | ~320 MiB |
| identity-service | 640m | ~257 MiB |
| api-gateway | 640m | ~236 MiB |
| postgres (`shared_buffers=128MB`, `max_connections=100`) | 512m | ~106 MiB |
| minio | 256m | ~89 MiB |
| redis (`maxmemory 64mb`, `allkeys-lru`) | 96m | ~8 MiB |
| mailpit (giữ tối đa 200 thư) | 64m | ~16 MiB |
| frontend (nginx) | 64m | ~15 MiB |
| **Tổng** | **6,3 GB (giới hạn, không ai dùng hết cùng lúc)** | **≈ 3,0 GiB** |

So với Minikube cũ (~2,6 GiB pod + ~1,2 GiB lõi Kubernetes): dư thêm ~1 GB. Lúc khởi động nhiều JVM chạy cùng lúc nên đỉnh RAM và CPU cao hơn mức rảnh, chuỗi `depends_on` giúp dàn ra.

---

## 5. Đã kiểm chứng gì, chưa kiểm chứng gì

**Đã chạy thật** (Docker 29 / Compose v5 trên máy dev, 6 image build bằng đúng các Dockerfile trong repo, `REGISTRY=localtest`):

- `docker compose config` hợp lệ; thiếu khoá bắt buộc thì báo lỗi rõ.
- **12/12 container `healthy`** từ **DB trống**, qua đúng chuỗi `depends_on` (identity → work → workspace); không phải sửa tay lần nào.
- Postgres: 2 database đúng owner (`datn`, `kc_user`), extension `pgcrypto`. Keycloak import realm; `issuer` = `https://sso.<domain>/realms/datn-realm` dù gọi qua `localhost`.
- Qua `127.0.0.1:3000` (frontend → gateway): `/identity|work|workspace/actuator/health` đều 200; route SPA `/w/1/notifications` ra `index.html`; `POST` đăng nhập kèm `Origin: https://…` + `X-Forwarded-Proto: https` **không** bị 403 CORS; `/notification/internal/**` không được chuyển tiếp (401); file 5 MB qua được nginx, file 60 MB bị chặn 413; work-service tự tạo bucket `work-attachments`.

**Chưa chạy** (không làm được ngoài EC2 / Jenkins thật):

- `deploy.sh` end-to-end (giải nén gói, `--wait`, smoke test, **rollback**), `make-realm.sh` (máy dev không có `jq`; realm thử được sinh bằng một đoạn Python tương đương — file `jq` trong script là bản đã dùng ở cấu hình Minikube cũ), khoá SSH `command="…"`.
- Nginx máy chủ + certbot, WebSocket `101` qua cả hai lớp nginx, đăng nhập Google, gửi mail thật.
- RAM thật trên `t3.large` và thời gian deploy lần đầu (ước tính 10–15 phút, **chưa đo**).

Những mục trên có checklist kiểm tra ở [05](05_TRIEN_KHAI_LAN_DAU.md) và [06](06_NOI_JENKINS_VOI_SERVER_UNG_DUNG.md).

---

## 6. Hiện tượng lạ ở lần chạy đầu (không phải lỗi)

- Log work-service lần đầu có `ERROR ... DataSeeder : Lỗi khi tự động seed các dự án mẫu ...` (đã gặp khi thử, kèm `current transaction is aborted`): `DataSeeder` chạy khi DB còn trống; ghi chú cũ của nhóm quy cho việc bảng `workspaces` (do workspace-service tạo) chưa có lúc đó, **chưa điều tra sâu**. Service vẫn lên `healthy`; hệ quả là dự án mẫu không được seed tự động ở lần đầu.
- Keycloak lần đầu `start` tự build + import realm: **3–10 phút** trên 2 vCPU, `healthcheck` cho phép tới ~15 phút (`retries: 60` × 15 giây).

---

## 7. Việc của bạn trước khi sang bước 4

Nếu chưa làm ở [02 §5](02_CHUAN_BI_SERVER_UNG_DUNG.md#5-dns): 🪟 Git Bash ở gốc repo —

```bash
bash infrastructure/prod/scripts/set-public-host.sh <DOMAIN> https <DOCKERHUB_USER>
git diff infrastructure/prod/public.env          # kiểm tra: APP_HOST, SSO_HOST, REGISTRY đã đúng
git add infrastructure/prod/public.env && git commit -m "chore(deploy): đặt tên miền và registry"
```

Nhớ **commit trước khi build image**: frontend nhúng URL Keycloak (`VITE_KEYCLOAK_URL`) lúc build, `build-push.sh` đọc từ `public.env`.
