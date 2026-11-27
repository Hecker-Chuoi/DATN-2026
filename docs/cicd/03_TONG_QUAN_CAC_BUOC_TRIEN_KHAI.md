# 03 — Tổng quan các bước triển khai CI/CD (từ code đến setup)

> Bản đồ tổng quát, mỗi bước chỉ nêu **làm gì** và **xong khi nào**. Chi tiết: [01 — Setup Jenkins](cac_buoc/01_HUONG_DAN_SETUP_JENKINS.md), [02 — Nghiên cứu CI/CD trên source code](02_NGHIEN_CUU_CICD_TREN_SOURCE_CODE.md).
>
> Mô hình: **GitHub → Jenkins (server riêng) → Docker Hub → Helm → Minikube (server riêng)**.

```
 Bước 0          Bước 1            Bước 2              Bước 3            Bước 4             Bước 5        Bước 6
 Chuẩn bị  ──►  Code trong repo ──► Server Minikube ──► Server Jenkins ──► Nối Jenkins ──►  Chạy lần đầu ──► Vận hành
 tài khoản      (Docker, Helm,     (cluster, hạ tầng,   (cài đặt)         với dự án         + kiểm tra
                 Jenkinsfile)       app bản đầu)
```

---

## Bước 0 — Chuẩn bị tài khoản và tài nguyên

- [ ] 2 server Ubuntu 22.04/24.04: **Jenkins** (4 vCPU / 8 GB) và **Minikube** (4 vCPU / 16 GB), mở cổng 22/80/443.
- [ ] Tài khoản **Docker Hub** + 2 Access Token (Read & Write cho Jenkins, Read-only cho cluster).
- [ ] **GitHub**: quyền admin repo (để tạo webhook) + Fine-grained PAT cho Jenkins.
- [ ] **Tên miền** (khuyến nghị): `jenkins.<domain>` → server Jenkins; `app.<domain>`, `sso.<domain>` → server Minikube. Chưa có thì tạm dùng `sslip.io`.

**Xong khi:** SSH được vào 2 server, đăng nhập được Docker Hub bằng token.

---

## Bước 1 — Chuẩn bị code trong repo

Làm trên nhánh riêng (ví dụ `feat/cicd-jenkins`), thử trên Minikube ở máy cá nhân trước.

1. **Dockerfile**: đã có đủ 6; chỉ thêm `ARG VITE_KEYCLOAK_URL` cho frontend (02 §3.3). Thử `docker build` cả 6 image trên máy.
2. **Cấu hình k8s cho Spring**: viết `application-k8s.yaml` cho 5 service (DB, Keycloak issuer/jwk, Kafka, Redis, MinIO, route gateway, CORS...) — 02 §7.
3. **Helm chart**:
   - `datn-platform`: Postgres ×2, Keycloak, Kafka, Redis, MinIO, Mailpit (02 §4.2).
   - `datn-app`: 5 backend + frontend + Ingress, `image.repository` trỏ Docker Hub, `image.tag` do CI truyền (02 §4.1).
   - Kiểm tra: `helm lint`, `helm template ... | kubeconform`.
4. **Script**: `infrastructure/ci/build-push.sh` (build + push 6 image), `infrastructure/k8s/deploy.sh` (chạy trên server Minikube), `rbac-deployer.yaml` (02 §5).
5. **Jenkinsfile** ở gốc repo: test → build/push → deploy (khung mẫu 02 §6).
6. Thử toàn bộ trên Minikube máy cá nhân: `helm install` 2 chart, login được, tạo dự án/công việc, nhận thông báo.

**Xong khi:** trên Minikube cá nhân, ứng dụng chạy đủ chức năng chính bằng image kéo từ Docker Hub.

---

## Bước 2 — Dựng server Minikube

1. Cài Docker, `kubectl`, `helm`, `minikube`; `minikube start --driver=docker --cpus=4 --memory=12g`; bật addon `ingress`.
2. Tạo namespace `datn`; tạo Secret: `app-secrets` (mật khẩu DB, Keycloak, MinIO, SMTP), `keycloak-realm` (file realm đã sửa redirect URI), `dockerhub-pull` (token Read-only).
3. Cài `datn-platform` (admin, một lần) → chờ Postgres, Keycloak, Kafka... `Running`.
4. Import realm Keycloak; kiểm tra login Admin Console qua `sso.<domain>`.
5. Nginx trên host reverse proxy `app.<domain>`, `sso.<domain>` → `$(minikube ip)` (WebSocket, body 50m) + certbot HTTPS.
6. Cài `datn-app` lần đầu bằng tay với một tag image đã push → mở `https://app.<domain>` dùng thử.
7. Tạo user `deployer` + kubeconfig quyền hẹp (ServiceAccount `ci-deployer`), đặt `deploy.sh` vào `/home/deployer/`.

**Xong khi:** `https://app.<domain>` chạy; `sudo -u deployer helm list -n datn` thấy release `datn-app`.

---

## Bước 3 — Dựng server Jenkins

Theo **[01 — Phần A](cac_buoc/01_HUONG_DAN_SETUP_JENKINS.md#phần-a--cài-jenkins)**: Java 21 + Jenkins LTS → Docker (user `jenkins` vào nhóm `docker`) → Nginx + HTTPS → plugin → bảo mật → Global Tools (`jdk-21`, `maven-3.9`, `node-22`).

**Xong khi:** đăng nhập được `https://jenkins.<domain>`, `sudo -u jenkins docker ps` chạy.

---

## Bước 4 — Nối Jenkins với dự án

Theo **[01 — Phần B](cac_buoc/01_HUONG_DAN_SETUP_JENKINS.md#phần-b--nối-jenkins-với-dự-án)**:

1. Credentials: `github-token`, `dockerhub`, `minikube-ssh` (khoá SSH của `deployer`) + `known_hosts`.
2. Job `smoke-test` kiểm tra tool, login Docker Hub, SSH + `helm version` sang Minikube.
3. Job Multibranch `DATN/datn` trỏ repo, đọc `Jenkinsfile`.
4. Webhook GitHub → `https://jenkins.<domain>/github-webhook/`.

**Xong khi:** job `smoke-test` xanh cả 3 stage; push thử một nhánh thì job tự chạy.

---

## Bước 5 — Chạy lần đầu và kiểm tra

| # | Kịch bản | Kết quả mong đợi |
|---|---|---|
| 1 | Mở PR có test hỏng | Jenkins chạy CI, báo đỏ trên PR, **không** build/push/deploy |
| 2 | Merge vào `main` | Test xanh → 6 image `<sha>` xuất hiện trên Docker Hub → `helm history` có revision mới → pod chạy image `<sha>` |
| 3 | Sửa giao diện nhỏ rồi merge | Thay đổi hiện trên `https://app.<domain>`, không gián đoạn (rolling update) |
| 4 | Deploy bản cố ý lỗi (service không khởi động) | `helm` hết thời gian chờ → tự rollback về revision trước, ứng dụng vẫn chạy |
| 5 | Rollback tay | `helm rollback datn-app <rev> -n datn` đưa về bản cũ |
| 6 | Kiểm tra quyền | User `deployer` **không** xoá được namespace khác / không `kubectl get secrets -n kube-system` |

**Xong khi:** 6 kịch bản đạt. Ghi lại ảnh chụp màn hình (Jenkins stage view, Docker Hub tags, `kubectl get pods`, `helm history`) cho báo cáo đồ án.

---

## Bước 6 — Vận hành

- **Hằng ngày**: dọn image cũ trên server Jenkins (cron `docker image prune`); xem build lỗi.
- **Trước deploy có migration mới**: sao lưu DB (`pg_dump` trong pod Postgres).
- **Hằng tháng**: cập nhật Jenkins + plugin; kiểm tra đĩa 2 server; xoay vòng token Docker Hub/GitHub nếu cần.
- **Khi hỏng**: Jenkins hỏng vẫn deploy tay được (`build-push.sh` trên máy có Docker + `deploy.sh` trên server Minikube); Minikube hỏng thì dựng lại từ Bước 2 (dữ liệu nằm trong PVC — sao lưu định kỳ).

---

## Tóm tắt thứ tự ưu tiên khi bắt tay làm

1. Chart Helm + cấu hình k8s chạy được trên Minikube **máy cá nhân** (phần khó nhất, quyết định thành bại).
2. Server Minikube chạy app bằng tay.
3. Server Jenkins + pipeline tự động hoá đúng những lệnh đã chạy tay ở bước 2.
