# 03 — Tổng quan các bước triển khai CI/CD (từ code đến setup)

> Bản đồ tổng quát, mỗi bước chỉ nêu **làm gì** và **xong khi nào**. Chi tiết từng lệnh: [cac_buoc/00 — Danh sách các bước](cac_buoc/00_DANH_SACH_CAC_BUOC.md), [02 — Nghiên cứu CI/CD trên source code](02_NGHIEN_CUU_CICD_TREN_SOURCE_CODE.md).
>
> Mô hình: **GitHub Actions → Docker Hub → SSH → docker compose (một server ứng dụng)**.

```
 Bước 0          Bước 1            Bước 2                Bước 3              Bước 4              Bước 5
 Chuẩn bị  ──►  Code trong repo ──► Dựng server ──────► Deploy tay lần đầu ──► GitHub Actions ──► Chạy thử + vận hành
 tài khoản      (compose, script,   (Docker, Nginx,       (build-push.sh +     (khoá SSH, secret,   (6 kịch bản)
                 workflow)           HTTPS, secrets)       deploy.sh)           cổng 22)
```

---

## Bước 0 — Chuẩn bị tài khoản và tài nguyên ([01](cac_buoc/01_CHUAN_BI_TAI_KHOAN.md))

- [ ] 1 EC2 Ubuntu 22.04/24.04 `t3.large` (2 vCPU / 8 GB) kèm **Elastic IP**, mở cổng 22/80/443.
- [ ] Tài khoản **Docker Hub** + Access Token (Read & Write, có ngày hết hạn).
- [ ] **GitHub**: quyền **admin** repo (để tạo secret của Actions).
- [ ] **Tên miền** (khuyến nghị): `app.<domain>`, `sso.<domain>` → Elastic IP. Chưa có thì tạm dùng `sslip.io`.

**Xong khi:** SSH được vào server, đăng nhập được Docker Hub bằng token, vào được Settings → Secrets and variables → Actions của repo.

---

## Bước 1 — Chuẩn bị code trong repo (đã làm)

1. **Dockerfile**: đủ 6; frontend có `ARG VITE_KEYCLOAK_URL`.
2. **Cấu hình Spring cho production**: `infrastructure/prod/spring/*/application-prod.yaml` (DB, Keycloak issuer / jwk, Kafka, Redis, MinIO, route gateway, CORS…).
3. **`infrastructure/docker-compose.prod.yml`**: 6 hạ tầng + 5 service + frontend, healthcheck, `depends_on` theo chuỗi, `mem_limit`; `infrastructure/prod/` (`public.env`, init Postgres, Nginx mẫu, `deploy.sh`, `make-realm.sh`, `backup-postgres.sh`).
4. **Script**: `infrastructure/ci/build-push.sh` (build + push 6 image).
5. **Workflow** `.github/workflows/ci-cd.yml`: test → build / push → deploy → kiểm tra HTTPS.
6. Đặt tên miền + Docker Hub user: `bash infrastructure/prod/scripts/set-public-host.sh <domain> https <user>` rồi commit.

**Xong khi:** `docker compose config` hợp lệ; workflow qua `actionlint`; (đã thử) 12 container `healthy` bằng image build local ([cac_buoc/04 §5](cac_buoc/04_DOCKER_COMPOSE_PRODUCTION.md#5-đã-kiểm-chứng-gì-chưa-kiểm-chứng-gì)).

---

## Bước 2 — Dựng server ứng dụng

1. Cài Docker + Compose (giới hạn log container), Nginx, certbot, jq ([cac_buoc/02](cac_buoc/02_CHUAN_BI_SERVER_UNG_DUNG.md)).
2. DNS `app.` / `sso.` → Elastic IP; Nginx proxy vào `127.0.0.1:3000` (frontend) và `127.0.0.1:8080` (Keycloak); HTTPS bằng certbot.
3. Tạo user `deployer` (nhóm `docker`), `~/datn/secrets.env` (15 khoá ngẫu nhiên), sao lưu về máy cá nhân ([cac_buoc/03](cac_buoc/03_SECRET_VA_CAU_HINH.md)).

**Xong khi:** `certbot renew --dry-run` thành công; `secrets.env` có 15 khoá.

---

## Bước 3 — Deploy tay lần đầu ([cac_buoc/05](cac_buoc/05_TRIEN_KHAI_LAN_DAU.md))

Build + push 6 image từ máy Windows (`build-push.sh <sha>`); cài `deploy.sh` lên server; `tar | ssh ... deploy.sh deploy <sha>`; mở `https://app.<domain>` dùng thử.

**Xong khi:** `https://app.<domain>` chạy (đăng ký, đăng nhập, tạo dự án, WebSocket, upload); `deploy.sh check` thấy 12 container `healthy`.

---

## Bước 4 — Nối GitHub Actions với server ([cac_buoc/06](cac_buoc/06_GITHUB_ACTIONS_CICD.md))

1. Sinh khoá SSH **riêng cho Actions**; đưa khoá công khai vào `authorized_keys` của `deployer` **kèm `command="…/deploy.sh"`** (khoá chỉ chạy được `deploy` / `rollback` / `check`).
2. Mở cổng 22 `0.0.0.0/0` ở Security Group (IP runner GitHub thay đổi).
3. Tạo secret: `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`, `DEPLOY_SSH_KEY`, `DEPLOY_HOST` (+ `DEPLOY_KNOWN_HOSTS`).

**Xong khi:** từ máy ngoài, `ssh -i <khoá> deployer@<IP> check` chạy được còn lệnh khác bị từ chối; đủ secret trên GitHub.

---

## Bước 5 — Chạy thử và vận hành

| # | Kịch bản | Kết quả mong đợi |
|---|---|---|
| 1 | Mở PR có test hỏng | Actions báo đỏ trên PR, **không** build / push / deploy |
| 2 | Merge vào `main` | Test xanh → 6 image `<sha>` trên Docker Hub → `deploy.sh` in `✅` → bước kiểm tra HTTPS `OK` |
| 3 | Sửa giao diện nhỏ rồi merge | Thay đổi hiện trên `https://app.<domain>` (gián đoạn ngắn lúc container được thay) |
| 4 | Deploy bản cố ý lỗi (service không khởi động) | `up --wait` hết hạn / smoke test lỗi → **tự về bản trước**, job đỏ, ứng dụng vẫn chạy |
| 5 | Rollback tay | `sudo -u deployer /home/deployer/deploy.sh rollback` |
| 6 | Kiểm tra khoá chặt | `ssh -i secret/actions-deploy-key deployer@<IP> "ls /"` bị từ chối |

**Xong khi:** 6 kịch bản đạt. Ghi lại ảnh chụp (trang Actions với các job xanh, Docker Hub tags, `docker ps`, `deploy.sh check`) cho báo cáo đồ án.

**Vận hành:**
- **Hằng ngày**: xem lượt chạy Actions lỗi; `docker system df` trên server.
- **Trước deploy có migration mới**: sao lưu DB (`backup-postgres.sh`, cron 02:00 mỗi ngày).
- **Định kỳ**: xoay vòng Docker Hub token (nhớ ngày hết hạn), kiểm tra đĩa, cập nhật gói hệ điều hành.
- **Khi Actions hỏng**: deploy tay vẫn được (`build-push.sh` trên máy có Docker + `tar | ssh ... deploy.sh deploy <tag>`); `datn-app` hỏng thì dựng lại từ Bước 2 (dữ liệu nằm trong volume Docker — sao lưu định kỳ, **không** `docker volume prune` / `down -v`).

---

## Tóm tắt thứ tự ưu tiên khi bắt tay làm

1. Server ứng dụng chạy được ứng dụng **bằng tay** (bước 3): phần quyết định thành bại, và là bản kiểm chứng cho file compose.
2. Workflow tự động hoá đúng những lệnh đã chạy tay ở bước 3.
3. Chỉ sau đó mới đo RAM, chỉnh `mem_limit`, thêm sao lưu.
