# 02 — Nghiên cứu: triển khai CI/CD trên source code DATN-2026

> Mục tiêu: liệt kê **cần những gì** trong repo và trên hạ tầng để có CI/CD **Jenkins → Docker Hub → SSH → docker compose**, và những bẫy đặc thù của dự án. Mức định hướng (cái gì, đặt ở đâu, vì sao), không phải hướng dẫn từng lệnh.
> Đọc kèm: [01 — Setup Jenkins](cac_buoc/01_HUONG_DAN_SETUP_JENKINS.md), [03 — Tổng quan các bước](03_TONG_QUAN_CAC_BUOC_TRIEN_KHAI.md), [cac_buoc/04 — Docker compose production](cac_buoc/04_DOCKER_COMPOSE_PRODUCTION.md).
>
> Bản trước của tài liệu này dùng Helm + Minikube; đã bỏ (xem [cac_buoc/00](cac_buoc/00_DANH_SACH_CAC_BUOC.md)). Các bẫy ở mục 6 vẫn đúng và được giữ lại.

## Mục lục
1. [Luồng tổng thể](#1-luồng-tổng-thể)
2. [Checklist: đã có gì, cần thêm gì](#2-checklist-đã-có-gì-cần-thêm-gì)
3. [Docker image và Docker Hub](#3-docker-image-và-docker-hub)
4. [Deploy từ Jenkins sang server ứng dụng](#4-deploy-từ-jenkins-sang-server-ứng-dụng)
5. [Cấu hình ứng dụng khi chạy trong compose](#5-cấu-hình-ứng-dụng-khi-chạy-trong-compose)
6. [Bẫy đặc thù của dự án](#6-bẫy-đặc-thù-của-dự-án)
7. [Vì sao compose thay vì Kubernetes](#7-vì-sao-compose-thay-vì-kubernetes)

---

## 1. Luồng tổng thể

```
git push ─► GitHub ─webhook─► Jenkins (server riêng)
                                 │ 1. Checkout
                                 │ 2. Test backend (mvn verify) + lint/build frontend
                                 │ 3. docker build 6 image, tag = git SHA
                                 │ 4. docker push ─────────────► Docker Hub
                                 │ 5. tar (compose + cấu hình) | ssh deployer@datn-app "deploy <sha>"
                                 ▼
                          datn-app: deploy.sh ── docker compose pull + up -d --wait ──► kéo image <sha> từ Docker Hub
                                                 smoke test; lỗi thì tự về bản trước
```

| Giai đoạn | Khi nào chạy | Kết quả |
|---|---|---|
| **CI** (bước 1–2) | Mọi nhánh, mọi PR | Báo xanh/đỏ lên GitHub |
| **Build & Push** (bước 3–4) | Chỉ nhánh `main` | Image `<DOCKERHUB_USER>/datn-<service>:<sha>` |
| **CD** (bước 5) | Chỉ `main`, sau khi push xong | `datn-app` chạy bản mới |

Một file compose, hai nhóm dịch vụ:
- **Hạ tầng có trạng thái** (Postgres, Keycloak, Kafka, Redis, MinIO, Mailpit): dữ liệu nằm trong volume có tên. `up -d` chỉ tạo lại container khi **image / cấu hình của nó đổi**; deploy ứng dụng thường ngày không đụng tới.
- **Ứng dụng** (5 service Spring Boot + frontend): image đổi mỗi lần deploy (tag = SHA).

---

## 2. Checklist: đã có gì, cần thêm gì

| Hạng mục | Trạng thái | Ghi chú |
|---|---|---|
| Dockerfile 5 backend + frontend | ✅ Có (multi-stage, BuildKit cache; backend chạy user 10001) | Frontend đã có `ARG VITE_KEYCLOAK_URL` (§3.3) |
| `backend/.dockerignore` | ✅ Có | |
| Actuator health (`/actuator/health/liveness`, `/readiness`) | ✅ Có, đã mở trong Security | Bật `management.endpoint.health.probes.enabled=true` trong profile `prod` |
| CORS / cookie đọc từ cấu hình (`app.cors.allowed-origins`, `app.cookie.secure`) | ✅ Có | Đặt theo tên miền trong `public.env` |
| `frontend/nginx.conf` proxy tới `api-gateway:8081` | ✅ Có | Đã thêm `client_max_body_size 50m` |
| Cấu hình Spring cho production | ✅ `infrastructure/prod/spring/*/application-prod.yaml` | §5 |
| `docker-compose.prod.yml` + `infrastructure/prod/` | ✅ | [cac_buoc/04](cac_buoc/04_DOCKER_COMPOSE_PRODUCTION.md) |
| Script build + push image | ✅ `infrastructure/ci/build-push.sh` | §3.4; đọc registry / `SSO_HOST` từ `prod/public.env` |
| Script deploy trên server (`deploy.sh`) | ✅ `infrastructure/prod/scripts/deploy.sh` | §4 |
| `Jenkinsfile` ở gốc repo | ✅ | Chỉ phải đổi `APP_SERVER` (cac_buoc/06 §5) |
| Secret (mật khẩu DB, Keycloak, MinIO, SMTP) | Tạo tay trên server | `~/datn/secrets.env`, **không** commit (cac_buoc/03) |
| Realm Keycloak cho môi trường mới | ✅ `prod/scripts/make-realm.sh` sửa client `datn-backend` (secret, redirect URI, web origin) | `deploy.sh` gọi một lần; Keycloak import khi realm chưa có |

Cây thư mục: xem [cac_buoc/04 §1](cac_buoc/04_DOCKER_COMPOSE_PRODUCTION.md#1-cây-thư-mục).

---

## 3. Docker image và Docker Hub

### 3.1. Tên image

| Service | Build context | Dockerfile | Image |
|---|---|---|---|
| api-gateway | `backend/` | `backend/api-gateway/Dockerfile` | `<DOCKERHUB_USER>/datn-api-gateway` |
| identity-service | `backend/` | `backend/identity-service/Dockerfile` | `<DOCKERHUB_USER>/datn-identity-service` |
| work-service | `backend/` | `backend/work-service/Dockerfile` | `<DOCKERHUB_USER>/datn-work-service` |
| workspace-service | `backend/` | `backend/workspace-service/Dockerfile` | `<DOCKERHUB_USER>/datn-workspace-service` |
| notification-service | `backend/` | `backend/notification-service/Dockerfile` | `<DOCKERHUB_USER>/datn-notification-service` |
| frontend | `frontend/` | `frontend/Dockerfile` | `<DOCKERHUB_USER>/datn-frontend` |

> Build context backend **phải** là `backend/` (Dockerfile COPY đủ 6 pom vì parent pom liệt kê mọi module). Thêm module mới thì sửa cả các Dockerfile còn lại.

### 3.2. Chiến lược tag

- Tag chính = **git SHA ngắn** (`a1b2c3d`): bất biến, biết image nào ứng với commit nào, rollback = deploy lại SHA cũ (`deploy.sh rollback`).
- Có thể thêm tag phụ `main` / `latest` (`EXTRA_TAG` trong `build-push.sh`) để tiện xem, nhưng **`deploy.sh` luôn chạy theo SHA**. Deploy bằng `latest` thì compose không biết image đã đổi và không rollback được.

### 3.3. Frontend: biến build-time

`VITE_KEYCLOAK_URL` (`frontend/src/App.jsx`) được Vite **nhúng vào lúc build**, không đổi được khi chạy: Dockerfile frontend có `ARG VITE_KEYCLOAK_URL`, `build-push.sh` truyền `--build-arg VITE_KEYCLOAK_URL=https://sso.<domain>` lấy từ `public.env`. Hệ quả: image frontend gắn với một tên miền Keycloak. API thì frontend gọi đường dẫn tương đối (`/identity/...`, `/work/...`) nên không cần biến.

### 3.4. Script build + push

`infrastructure/ci/build-push.sh <tag>` lặp qua 6 service: `docker build` → (tuỳ chọn) `trivy image` chỉ báo cáo → `docker push`. Để trong script thay vì viết thẳng trong `Jenkinsfile`: chạy tay được khi Jenkins hỏng, dễ test. Ý cần nhớ:
- `DOCKER_BUILDKIT=1` (Dockerfile backend dùng `RUN --mount=type=cache`).
- `docker login` bằng **Access Token** (credential `dockerhub`), `docker logout` khi xong.
- 5 image backend build lại Maven mỗi lần → chậm (5–15 phút). Có thể tối ưu sau: chỉ build service có thay đổi.

### 3.5. Docker Hub

- Repo **public**: server ứng dụng kéo không cần đăng nhập (pull ẩn danh bị giới hạn số lần; nếu gặp `toomanyrequests` thì `docker login` bằng `deployer`).
- Repo **private**: `docker login` trên server ứng dụng (user `deployer`) bằng token **Read-only**.
- Dùng 2 token khác nhau: **Read & Write** cho Jenkins (push), **Read-only** cho server (pull, nếu cần).

---

## 4. Deploy từ Jenkins sang server ứng dụng

Jenkins **không** cài Docker Compose hay chạy lệnh trên server: nó SSH vào và gọi đúng một script.

- **Gói deploy** (`tar.gz` qua stdin): `docker-compose.prod.yml` + `prod/` + `keycloak/`. File compose và image luôn cùng một commit; giải nén vào `~/datn/releases/<sha>/`, giữ 5 bản gần nhất.
- **`deploy.sh`** (thuộc root, `/home/deployer/deploy.sh`): kiểm tra tag (chỉ SHA hex), giải nén, sinh `realm.json` lần đầu, `docker compose pull` + `up -d --wait`, smoke test (`/identity|work|workspace/actuator/health` qua `127.0.0.1:3000`), ghi `state/current_tag`; lỗi thì chẩn đoán + `up` lại bản trước. Lệnh `rollback`, `check` đi kèm.
- **Khoá SSH khoá chặt**: `command="/home/deployer/deploy.sh",no-pty,...` — lộ khoá Jenkins thì kẻ tấn công chỉ chạy được 3 lệnh trên, không mở được shell. `deployer` ≈ root qua nhóm `docker` nên việc khoá chặt này quan trọng.
- Vì sao không để Jenkins chạy `docker compose` trực tiếp qua `DOCKER_HOST=ssh://`: cần cấp quyền shell đầy đủ cho khoá Jenkins; cách trên chỉ cấp đúng việc cần.

Chi tiết và lệnh: [cac_buoc/06](cac_buoc/06_NOI_JENKINS_VOI_SERVER_UNG_DUNG.md).

---

## 5. Cấu hình ứng dụng khi chạy trong compose

`application.yml` trong code trỏ `localhost`/cổng 808x. Trong compose phải ghi đè, bằng **profile `prod`**: file `application-prod.yaml` của từng service nằm ở `infrastructure/prod/spring/<service>/`, mount vào `/config/`, chạy với `SPRING_PROFILES_ACTIVE=prod` + `SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:/config/`. Giá trị thay đổi theo môi trường đi qua biến `${...}` do compose đặt.

| Nhóm | Service | Giá trị trong compose |
|---|---|---|
| Datasource | identity, work, workspace, notification | `jdbc:postgresql://postgres:5432/datn_main_db`, user / mật khẩu từ `secrets.env` |
| JWT | cả 5 | `issuer-uri` = URL **công khai** `https://sso.<domain>/realms/datn-realm`; `jwk-set-uri` = **nội bộ** `http://keycloak:8080/realms/datn-realm/protocol/openid-connect/certs` |
| Keycloak admin | identity | URL nội bộ `http://keycloak:8080`, client secret `datn-backend` từ `secrets.env` |
| Kafka / Redis | work, workspace, notification | `kafka:9092`, `redis:6379` |
| MinIO | work | `http://minio:9000`, key từ `secrets.env` |
| Gọi chéo service | work, identity | `http://identity-service:8082`, `http://workspace-service:8087`, `http://notification-service:9994` |
| Route gateway | api-gateway | 4 route tới tên service compose (bẫy 2) |
| CORS / cookie | api-gateway, identity | `app.cors.allowed-origins=https://app.<domain>`, `app.cookie.secure=true` khi có HTTPS |
| Link trong email | identity, work, notification | `app.frontend-url=https://app.<domain>` |
| SMTP | notification | `MAIL_*` (Mailpit hoặc SMTP thật, `public.env` + `secrets.env`) |
| Cụm | work, notification | `app.cluster.redis-enabled=true` |
| Health | cả 5 | `management.endpoint.health.probes.enabled=true`, `server.shutdown=graceful` |

---

## 6. Bẫy đặc thù của dự án

1. **Issuer Keycloak**: token mang `iss` = URL mà trình duyệt dùng để login (công khai). Service phải khai `issuer-uri` công khai nhưng lấy khoá qua `jwk-set-uri` nội bộ; Keycloak đặt `KC_HOSTNAME_URL` = URL công khai. Sai chỗ này → mọi API trả 401. Đổi `scheme` / `ssoHost` sau khi cài = xoá realm và đăng nhập lại.
2. **Route gateway**: danh sách `routes` trong profile **thay thế toàn bộ** danh sách gốc → profile `prod` phải khai đủ 4 route (identity, work kèm `/work/ws-work/**`, workspace, notification kèm `/notification/ws/**`). **Không** route `/notification/internal/**` (gửi email nội bộ, không xác thực). Thêm service mới = thêm route vào cả `application-tuananh.yml` lẫn `application-prod.yaml`.
3. **Thứ tự migration trên DB trống**: Flyway của workspace-service cần bảng `projects` do work-service tạo → workspace-service phải chờ work-service `healthy` (`depends_on` trong compose).
4. **Thay container = có lúc hai bản cùng chạy** nếu sau này bạn chạy song song (blue/green) → `app.cluster.redis-enabled=true` để job định kỳ (nhắc hạn 08:00, OutboxRelay) không chạy trùng và WebSocket đẩy qua Redis. Với compose hiện tại (một bản) vẫn để `true`.
5. **Rollback image không rollback DB**: migration Flyway (work V1–V10, workspace V1–V6, notification V1–V2) chỉ chạy tiến. Sao lưu DB trước khi deploy bản có migration mới (`backup-postgres.sh`).
6. **Upload và WebSocket**: `client_max_body_size 50m` (đính kèm) phải có ở **cả hai** nginx (máy chủ và container frontend); `proxy_read_timeout / send_timeout 3600s` và `Upgrade` / `Connection` cho `/notification/ws` (STOMP).
7. **Realm Keycloak**: import `infrastructure/keycloak/realm-export_08082026.json` qua `make-realm.sh` (sửa *Valid redirect URIs* / *Web origins* sang tên miền mới; thêm redirect URI trong Google Console nếu dùng login Google). Client secret `datn-backend` trong `secrets.env` phải khớp realm. Import chỉ chạy khi realm **chưa có**.
8. **User id = Keycloak `sub`**: import realm mới thì `sub` của user đổi → không mang dữ liệu nghiệp vụ từ DB cũ sang realm mới mà không đối chiếu `users.keycloak_id`.
9. **Tài nguyên**: Keycloak bị OOMKilled ở limit 1 GB (đã gặp) → để ≥ 1,25 GB và heap 40%. Mỗi service Spring ~250–420 MiB lúc rảnh.
10. **CORS chỉ cho origin đã khai**: mở frontend bằng IP / cổng khác với `APP_HOST` → request ghi và WebSocket bị 403.
11. **Docker Hub**: luôn `docker login` trước khi build (image nền `maven`, `eclipse-temurin`, `node`, `nginx` cũng kéo từ Docker Hub) để tránh `toomanyrequests`.
12. **Line ending**: script `.sh`, Dockerfile, `infrastructure/prod/**`, `Jenkinsfile` phải là LF (đã khai trong `.gitattributes`). `deploy.sh` còn tự bỏ `\r` khỏi file `.sh/.env/.yml/.yaml` trong gói, phòng gói tạo trên Windows.
13. **Image MinIO**: `minio/minio` đã bị gỡ khỏi Docker Hub → dùng `cgr.dev/chainguard/minio:latest` (giống compose dev của nhóm); healthcheck `mc ready local`.
14. **CI trên Jenkins chạy test không có DB**: `mvn verify` trần đỏ ở `IdentityServiceApplicationTests.contextLoads` (cần Postgres); `Jenkinsfile` bỏ `*ApplicationTests` (còn 148 unit test, xanh với JDK 21). `npm run lint` có ~190 lỗi sẵn nên chỉ đánh dấu UNSTABLE; `npm run build` xanh. Đã chạy thử cả hai trong container `maven:3.9.9-eclipse-temurin-21-alpine` / `node:22-alpine` trước khi đưa vào pipeline.
---

## 7. Vì sao compose thay vì Kubernetes

| | docker compose (đang dùng) | Minikube + Helm (bản cũ, đã bỏ) |
|---|---|---|
| Số thứ phải cài trên server | Docker, Nginx, certbot, jq | + Minikube, kubectl, Helm, ingress addon, metrics-server, systemd unit tự khởi động |
| RAM chiếm trước khi chạy ứng dụng | ~0,8 GB | ~2 GB (HĐH + lõi Kubernetes) |
| Hạ tầng (Postgres, Keycloak…) | Cùng file compose, cùng cách chạy với dev | Chart Helm viết lại từ compose |
| Deploy | `compose up -d --wait` | `helm upgrade` + RBAC cho `deployer` + kubeconfig |
| Rolling update không gián đoạn | Không (vài chục giây / service) | Có, nhưng cần dư RAM |
| Tự co giãn, nhiều node | Không | Có (không cần cho đồ án này) |

Với một máy 8 GB và đồ án cần chạy ổn định, compose đủ và ít điểm hỏng hơn. Nếu đề tài yêu cầu Kubernetes thì bản cũ vẫn còn trong git (`e93a83d`).
