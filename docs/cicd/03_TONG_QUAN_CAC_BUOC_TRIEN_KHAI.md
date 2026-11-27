# 03 — Tổng quan các bước triển khai CI/CD (từ code đến setup)

> Bản đồ tổng quát, mỗi bước chỉ nêu **làm gì** và **xong khi nào**. Chi tiết từng lệnh: [cac_buoc/00 — Danh sách các bước](cac_buoc/00_DANH_SACH_CAC_BUOC.md), [01 — Setup Jenkins](cac_buoc/01_HUONG_DAN_SETUP_JENKINS.md), [02 — Nghiên cứu CI/CD trên source code](02_NGHIEN_CUU_CICD_TREN_SOURCE_CODE.md).
>
> Mô hình: **GitHub → Jenkins (server riêng) → Docker Hub → SSH → docker compose (server ứng dụng)**.

```
 Bước 0          Bước 1            Bước 2              Bước 3            Bước 4             Bước 5
 Chuẩn bị  ──►  Code trong repo ──► Server ứng dụng ──► Server Jenkins ──► Nối Jenkins ──►  Chạy lần đầu
 tài khoản      (compose, script,   (Docker, Nginx,      (cài đặt)         với dự án         + kiểm tra
                 Jenkinsfile)        HTTPS, deploy tay)                                                       → Bước 6: vận hành
```

---

## Bước 0 — Chuẩn bị tài khoản và tài nguyên

- [ ] 2 server Ubuntu 22.04/24.04, mỗi máy `t3.large` (2 vCPU / 8 GB): **Jenkins** và **ứng dụng** (`datn-app`), cùng VPC, mở cổng 22/80/443 (22 giữa hai máy qua Security Group).
- [ ] Tài khoản **Docker Hub** + 2 Access Token (Read & Write cho Jenkins, Read-only nếu để repo private).
- [ ] **GitHub**: quyền admin repo (để tạo webhook) + Fine-grained PAT cho Jenkins.
- [ ] **Tên miền** (khuyến nghị): `jenkins.<domain>` → server Jenkins; `app.<domain>`, `sso.<domain>` → server ứng dụng. Chưa có thì tạm dùng `sslip.io`.

**Xong khi:** SSH được vào 2 server, đăng nhập được Docker Hub bằng token.

---

## Bước 1 — Chuẩn bị code trong repo (đã làm)

1. **Dockerfile**: đủ 6; frontend có `ARG VITE_KEYCLOAK_URL`.
2. **Cấu hình Spring cho production**: `infrastructure/prod/spring/*/application-prod.yaml` (DB, Keycloak issuer / jwk, Kafka, Redis, MinIO, route gateway, CORS…).
3. **`infrastructure/docker-compose.prod.yml`**: 6 hạ tầng + 5 service + frontend, healthcheck, `depends_on` theo chuỗi, `mem_limit`; `infrastructure/prod/` (`public.env`, init Postgres, Nginx mẫu, `deploy.sh`, `make-realm.sh`, `backup-postgres.sh`).
4. **Script**: `infrastructure/ci/build-push.sh` (build + push 6 image).
5. **`Jenkinsfile`** ở gốc repo: test → build / push → deploy.
6. Đặt tên miền + Docker Hub user: `bash infrastructure/prod/scripts/set-public-host.sh <domain> https <user>` rồi commit.

**Xong khi:** `docker compose config` hợp lệ; (đã thử) 12 container `healthy` trên máy dev bằng image build local ([cac_buoc/04 §5](cac_buoc/04_DOCKER_COMPOSE_PRODUCTION.md#5-đã-kiểm-chứng-gì-chưa-kiểm-chứng-gì)).

---

## Bước 2 — Dựng server ứng dụng

1. Cài Docker + Compose (giới hạn log container), Nginx, certbot, jq ([cac_buoc/02](cac_buoc/02_CHUAN_BI_SERVER_UNG_DUNG.md)).
2. DNS `app.` / `sso.` → Elastic IP; Nginx proxy vào `127.0.0.1:3000` (frontend) và `127.0.0.1:8080` (Keycloak); HTTPS bằng certbot.
3. Tạo user `deployer` (nhóm `docker`), `~/datn/secrets.env` (12 mật khẩu ngẫu nhiên), sao lưu về máy cá nhân ([cac_buoc/03](cac_buoc/03_SECRET_VA_CAU_HINH.md)).
4. Build + push 6 image từ máy Windows; cài `deploy.sh`; deploy tay: `tar | ssh ... deploy.sh deploy <tag>` → mở `https://app.<domain>` dùng thử ([cac_buoc/05](cac_buoc/05_TRIEN_KHAI_LAN_DAU.md)).

**Xong khi:** `https://app.<domain>` chạy (đăng ký, đăng nhập, tạo dự án, WebSocket, upload); `deploy.sh check` thấy 12 container `healthy`.

---

## Bước 3 — Dựng server Jenkins (đã làm)

Theo **[01 — Phần A](cac_buoc/01_HUONG_DAN_SETUP_JENKINS.md#phần-a--cài-jenkins)**: Java 21 + Jenkins LTS → Docker (user `jenkins` vào nhóm `docker`) → Nginx + HTTPS → plugin → bảo mật → Global Tools (`jdk-21`, `maven-3.9`, `node-22`).

**Xong khi:** đăng nhập được `https://jenkins.<domain>`, `sudo -u jenkins docker ps` chạy.

---

## Bước 4 — Nối Jenkins với dự án

Theo **[cac_buoc/06](cac_buoc/06_NOI_JENKINS_VOI_SERVER_UNG_DUNG.md)** và **[01 — Phần B](cac_buoc/01_HUONG_DAN_SETUP_JENKINS.md#phần-b--nối-jenkins-với-dự-án)**:

1. `authorized_keys` của `deployer` có khoá Jenkins **kèm `command="…/deploy.sh"`** (khoá chỉ chạy được `deploy` / `rollback` / `check`); `ssh-keyscan` IP private; credentials `github-token`, `dockerhub`, `app-server-ssh`.
2. Sửa `APP_SERVER` trong `Jenkinsfile` thành IP private của `datn-app`.
3. Job `smoke-test` kiểm tra tool, login Docker Hub, `ssh ... check`.
4. Job Multibranch `DATN/datn` đọc `Jenkinsfile`; webhook GitHub → `https://jenkins.<domain>/github-webhook/`.

**Xong khi:** job `smoke-test` xanh cả 3 stage; push thử một nhánh thì job tự chạy.

---

## Bước 5 — Chạy lần đầu và kiểm tra

| # | Kịch bản | Kết quả mong đợi |
|---|---|---|
| 1 | Mở PR có test hỏng | Jenkins chạy CI, báo đỏ trên PR, **không** build / push / deploy |
| 2 | Merge vào `main` | Test xanh → 6 image `<sha>` trên Docker Hub → `deploy.sh` in `✅` → `check` báo tag `<sha>` |
| 3 | Sửa giao diện nhỏ rồi merge | Thay đổi hiện trên `https://app.<domain>` (gián đoạn ngắn lúc container được thay) |
| 4 | Deploy bản cố ý lỗi (service không khởi động) | `up --wait` hết hạn / smoke test lỗi → **tự về bản trước**, ứng dụng vẫn chạy |
| 5 | Rollback tay | `sudo -u deployer /home/deployer/deploy.sh rollback` |
| 6 | Kiểm tra khoá chặt | `ssh -i id_deploy deployer@<IP> "ls /"` bị từ chối |

**Xong khi:** 6 kịch bản đạt. Ghi lại ảnh chụp màn hình (Jenkins stage view, Docker Hub tags, `docker ps`, `deploy.sh check`) cho báo cáo đồ án.

---

## Bước 6 — Vận hành

- **Hằng ngày**: dọn image cũ trên server Jenkins (cron `docker image prune`); xem build lỗi.
- **Trước deploy có migration mới**: sao lưu DB (`backup-postgres.sh`, cron 02:00 mỗi ngày).
- **Hằng tháng**: cập nhật Jenkins + plugin; kiểm tra đĩa 2 server (`df -h`, `docker system df`); xoay vòng token Docker Hub / GitHub nếu cần.
- **Khi hỏng**: Jenkins hỏng vẫn deploy tay được (`build-push.sh` trên máy có Docker + `tar | ssh ... deploy.sh deploy <tag>`); `datn-app` hỏng thì dựng lại từ Bước 2 (dữ liệu nằm trong volume Docker — sao lưu định kỳ, **không** `docker volume prune` / `down -v`).

---

## Tóm tắt thứ tự ưu tiên khi bắt tay làm

1. Server ứng dụng chạy được ứng dụng **bằng tay** (bước 2): phần quyết định thành bại, và là bản kiểm chứng cho file compose.
2. Server Jenkins + pipeline tự động hoá đúng những lệnh đã chạy tay ở bước 2.
3. Chỉ sau đó mới đo RAM, chỉnh `mem_limit`, thêm sao lưu.
