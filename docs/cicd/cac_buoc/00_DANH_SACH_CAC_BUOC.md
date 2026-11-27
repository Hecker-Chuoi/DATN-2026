# Danh sách các bước triển khai CI/CD (2 server: Jenkins + Minikube)

> Hướng đã chốt: **Jenkins + Docker Hub + Minikube (driver docker) + Helm 4**, chạy trên **2 EC2**:
> - **`datn-jenkins`** (đã có, cài theo [01](01_HUONG_DAN_SETUP_JENKINS.md)): chỉ chạy Jenkins — test, build, push image.
> - **`datn-minikube`** (tạo mới, theo [02](02_CAI_MINIKUBE_KUBECTL_HELM.md)): chỉ chạy Minikube + Nginx — chạy ứng dụng.
>
> Jenkins deploy bằng **SSH** sang `datn-minikube` (user `deployer`, credential `minikube-ssh`) rồi chạy `deploy.sh` → `helm upgrade`. Đây đúng là mô hình gốc của [01 §1](01_HUONG_DAN_SETUP_JENKINS.md#1-kiến-trúc-và-yêu-cầu-server) và [02 nghiên cứu §5 (cách A)](../02_NGHIEN_CUU_CICD_TREN_SOURCE_CODE.md#5-deploy-từ-jenkins-sang-minikube).
>
> Nguyên tắc: **deploy tay thành công trước, rồi mới tự động hoá** (bước 0–5 tay, bước 6–8 tự động).

## Kiến trúc

```
 Developer ── git push ──► GitHub ── webhook ──┐
                                               ▼
                     ┌─────────────────────────────────────┐
                     │ EC2 datn-jenkins  (2 vCPU / 8 GB)   │
                     │  Nginx :443 ─► Jenkins 127.0.0.1:8080│
                     │  Docker (build image), JDK, Maven, Node
                     └──────┬──────────────────┬───────────┘
                docker push │                  │ ssh deployer@<IP private datn-minikube>
                            ▼                  ▼   "deploy.sh <tag>"  (cổng 22, trong VPC)
                     ┌────────────┐   ┌──────────────────────────────────────────┐
                     │ Docker Hub │◄──│ EC2 datn-minikube  (2 vCPU / 8 GB)       │
                     └────────────┘   │  Nginx :443                              │
                       pull image     │   ├─ app.<domain> ─► 192.168.49.2:80     │
                                      │   └─ sso.<domain> ─► 192.168.49.2:80     │
                                      │  container "minikube" (Kubernetes)       │
                                      │   ns datn: datn-platform (Postgres,      │
                                      │            Keycloak, Kafka, Redis, MinIO)│
                                      │            datn-app (5 service + FE)     │
                                      └──────────────────────────────────────────┘
```

## Đã xong / cần dọn

- [x] Phần A của [01](01_HUONG_DAN_SETUP_JENKINS.md) (mục 1–8) trên `datn-jenkins`: Jenkins, Docker, Nginx, plugin, Global Tools.
- [x] Credential `github-token`, `dockerhub`.
- [x] Cặp khoá `/var/lib/jenkins/.ssh/id_deploy` (+ `.pub`) trên `datn-jenkins` — **giữ lại**, dùng ở bước 6.
- [ ] Credential `minikube-ssh` (Username `deployer`, Private Key = `id_deploy`) — tạo được ngay, chưa test được tới bước 6.
- [ ] **Dọn trên `datn-jenkins`** (nếu chưa làm): `sudo deluser --remove-home deployer` (user tạo nhầm), `sudo -u jenkins ssh-keygen -R 172.31.45.0` (known_hosts trỏ nhầm vào chính máy Jenkins).

## Các bước

| # | Việc | Server / nơi làm | Xong khi |
|---|---|---|---|
| 0 | **[Chi tiết: 02 §1–2](02_CAI_MINIKUBE_KUBECTL_HELM.md#1-tạo-ec2-datn-minikube)** — Tạo EC2 `datn-minikube` (**2 vCPU / 8 GB `t3.large`**, credit Unlimited, **cùng VPC** với `datn-jenkins`, Elastic IP, Security Group cho 22 từ máy bạn + từ `datn-jenkins`, 80/443 công khai). Chuẩn bị máy giống 01 §2, §4: timezone, hostname, swap, ufw, Docker + **R1** (swappiness, tắt snapd) | `datn-minikube` | SSH được vào; `docker run --rm hello-world` chạy bằng `ubuntu` |
| 1 | **[Chi tiết: 02 §3–9](02_CAI_MINIKUBE_KUBECTL_HELM.md#3-cài-kubectl)** — Cài `kubectl`, `helm` (Helm 4), `minikube`. `minikube start --driver=docker --cpus=2 --memory=6g --disk-size=50g` bằng user `ubuntu` (**R1**). Bật addon `ingress`, `metrics-server`. Tạo **systemd unit** để Minikube tự khởi động sau reboot | `datn-minikube` | `kubectl get nodes` → `Ready`; reboot xong Minikube tự lên |
| 2 | **[Chi tiết: 03](03_TAO_NAMESPACE_VA_SECRET.md)** — Tạo namespace `datn`, Secret `app-secrets` (12 key: mật khẩu Postgres / user `datn` / user `keycloak`, Keycloak admin + client secret, MinIO, SMTP — sinh ngẫu nhiên vào `~/datn-secrets/app-secrets.env`), Secret `dockerhub-pull` (token **Read-only**) gắn vào ServiceAccount `default`, LimitRange `default-limits` | `datn-minikube`, **không commit** | `kubectl -n datn get secrets` thấy `app-secrets` (12) + `dockerhub-pull` (1) |
| 3 | ✅ **[Chi tiết: 04](04_VIET_HELM_CHART_VA_SCRIPT.md) — ĐÃ LÀM trong repo.** Helm chart `datn-platform` + `datn-app` (`infrastructure/helm/`), profile Spring `k8s` (`files/config/*/application-k8s.yaml`), `ARG VITE_KEYCLOAK_URL` trong Dockerfile frontend, `infrastructure/ci/build-push.sh`, `infrastructure/k8s/` (`deploy.sh`, `rbac-deployer.yaml`, `make-ci-kubeconfig.sh`, `make-realm-secret.sh`, `namespace-limits.yaml`, `set-public-host.sh`). Viết theo **R2–R6** (`values-minikube.yaml`). **Việc còn lại của bạn:** chạy `set-public-host.sh` với tên miền thật rồi commit (04 §8) | repo | `helm lint --strict` sạch, kubeconform 33/33, đã chạy thử thật 5 service + Keycloak + Kafka (04 §6); `requests.memory` = **2,9 Gi** ≤ 4,5 Gi |
| 4 | `kubectl apply -f infrastructure/k8s/namespace-limits.yaml`; `bash infrastructure/k8s/make-realm-secret.sh` (sửa realm: secret + redirect URI của client `datn-backend`); `helm install datn-platform` (`-f values-minikube.yaml -f ../env-minikube.yaml`). Kiểm tra **R2, R4** chạy đúng. Chi tiết: 04 §9 | `datn-minikube`, một lần | Pod hạ tầng `Running`, vào được Keycloak admin; `kubectl top pods -n datn` khớp bảng ngân sách |
| 5 | Build + push image bằng tay một lần (trên `datn-jenkins` hoặc máy cá nhân), `helm install datn-app --set image.tag=<sha>`; DNS `app.` / `sso.` → Elastic IP `datn-minikube`; Nginx + certbot trên `datn-minikube`. Đo và chỉnh theo **R7** | cả hai | **Mở trình duyệt đăng nhập được** vào ứng dụng; không pod nào `OOMKilled` sau 30 phút dùng thử |
| 6 | Nối Jenkins → Minikube ([01 §9](01_HUONG_DAN_SETUP_JENKINS.md#9-credentials)): trên `datn-minikube` tạo user `deployer` + `authorized_keys` (= `id_deploy.pub`), `kubectl apply -f infrastructure/k8s/rbac-deployer.yaml` + `bash infrastructure/k8s/make-ci-kubeconfig.sh` (kubeconfig giới hạn quyền cho `deployer`), chép `infrastructure/k8s/deploy.sh` vào `/home/deployer/`; trên `datn-jenkins` chạy `ssh-keyscan` **IP private** của `datn-minikube` | cả hai | `sudo -u jenkins ssh -i /var/lib/jenkins/.ssh/id_deploy deployer@<IP private> 'kubectl -n datn get pods && helm version'` chạy được |
| 7 | Viết `Jenkinsfile` ([02 nghiên cứu §6](../02_NGHIEN_CUU_CICD_TREN_SOURCE_CODE.md#6-jenkinsfile)): test → `bash infrastructure/ci/build-push.sh $TAG` (chỉ nhánh `main`) → `tar cz -C infrastructure helm \| ssh deployer@... ./deploy.sh $TAG`. `deploy.sh` (đã có) tự dò `--rollback-on-failure` (Helm 4) / `--atomic` (Helm 3), timeout 15 phút vì **R5, R6** | repo | Commit lên nhánh, cú pháp hợp lệ |
| 8 | Làm [01](01_HUONG_DAN_SETUP_JENKINS.md) §10–12: Multibranch, webhook, smoke-test (đủ 3 stage, kể cả `SSH Minikube`) | Jenkins | Push code → pipeline xanh → app chạy bản mới |
| 9 | Vận hành: `datn-jenkins` theo 01 §13 (cron dọn image, 1 executor); `datn-minikube`: `pg_dump` Postgres định kỳ, **không** `docker system prune` (02 §10), theo dõi **R8** | cả hai | Có cron chạy, có file backup |

## Ngân sách tài nguyên `datn-minikube` (2 vCPU / 8 GB)

Tài khoản chỉ được cấp tối đa `t3.large` (2 vCPU / 7,6 GB). Để mặc định thì cả hệ thống cần ~8–9 GB → OOM. Mục tiêu: **pod ứng dụng ~5 GB** trong 6 GB cấp cho Minikube. Cột `requests / limits` là giá trị **đã viết trong chart** (`values-minikube.yaml` của hai chart), cột "Đo thử" là RAM thật khi chạy thử trên máy dev (04 §6):

| Thành phần | Mặc định | `requests` / `limits` memory (chart) | `requests` CPU | Đo thử |
|---|---|---|---|---|
| Ubuntu + Docker + Nginx (ngoài Minikube) | ~0,8 GB | — (phần 1,6 GB không cấp cho Minikube) | — | — |
| Lõi Kubernetes + ingress + metrics-server | ~1,2 GB | — (đo bằng `docker stats` lúc cụm rỗng, 02 §6) | ~0,95 CPU | — |
| Postgres (**1 instance**, 2 DB) | 2 instance ~0,4 GB | 256Mi / 512Mi | 50m | 78 MiB |
| Keycloak | 1,2+ GB | 640Mi / **1280Mi**, heap 40% | 100m | 764 MiB |
| Kafka (KRaft 1 node) | ~1 GB | 512Mi / 768Mi, heap 384m | 100m | 308 MiB |
| Redis | ~0,05 GB | 32Mi / 96Mi | 10m | 4 MiB |
| MinIO | ~0,3 GB | 128Mi / 256Mi | 25m | (chưa đo) |
| Mailpit | ~0,05 GB | 16Mi / 64Mi | 5m | (chưa đo) |
| api-gateway | ~0,6 GB | 256Mi / 512Mi | 50m | 198 MiB |
| identity / workspace / notification | ~0,6–0,8 GB mỗi cái | 256Mi / 512Mi | 50m | 243 / 245 / 280 MiB |
| work-service | ~0,8 GB | 320Mi / 640Mi | 50m | 347 MiB |
| Frontend (nginx) | ~0,03 GB | 16Mi / 64Mi | 5m | 12 MiB |
| **Cộng (datn-platform + datn-app)** | **~7–8 GB** ❌ | **requests 2,9 Gi** ✅ / limits 5,6 Gi | **0,55 CPU** ✅ | ≈ 2,6 GiB |

Hai con số cần nhớ: **`requests.memory` ≤ 4,5 Gi** (đạt: 2,9 Gi) và **`requests.cpu` ≤ 1** (đạt: 0,55). CPU là bẫy: nút Minikube chỉ có 2 CPU mà kube-system đã chiếm ~0,95, nên `requests.cpu` của ứng dụng đặt thấp (5–100m); đặt như bản cũ (200–250m mỗi service, tổng ~1,5 CPU) thì pod kẹt `Pending`. `infrastructure/k8s/namespace-limits.yaml` có ResourceQuota (`requests.cpu: 1`, `requests.memory: 4608Mi`) để vượt ngân sách là bị từ chối ngay.

Các việc tối ưu — **đã làm** thì đánh dấu, mỗi mục ghi nơi nó nằm trong repo:

- [x] **R1 — Tầng máy + Minikube** (bước 0–1, [02 §2.2, §6](02_CAI_MINIKUBE_KUBECTL_HELM.md#22-tối-ưu-bộ-nhớ-ở-tầng-máy-vì-chỉ-có-8-gb)): `minikube start --cpus=2 --memory=6g`; swap 4 GB + `vm.swappiness=10`; tắt `snapd`; chỉ bật addon `ingress` + `metrics-server`.
- [x] **R2 — Gộp Postgres** (`datn-platform/templates/postgres.yaml`): **một** StatefulSet `postgres:15-alpine` chứa 2 database `datn_main_db` (owner `datn`) + `keycloak` (owner `kc_user`), tạo bằng script trong `/docker-entrypoint-initdb.d` (ConfigMap `postgres-init`, đọc mật khẩu từ `app-secrets`); script chạy `CREATE EXTENSION IF NOT EXISTS pgcrypto` bằng superuser (migration V1 của work / workspace cần) và thu hồi `CONNECT` để mỗi user chỉ vào DB của mình. `shared_buffers=128MB`, `max_connections=100`. **Đã chạy thử thật** trên PG 15: user `datn` không phải superuser vẫn chạy được 10 migration Flyway của work-service và 6 của workspace-service. Lệch với [02 nghiên cứu §4.2](../02_NGHIEN_CUU_CICD_TREN_SOURCE_CODE.md#42-chart-datn-platform) (đã sửa).
- [x] **R3 — Giới hạn JVM 5 service** (`datn-app/values-minikube.yaml`):
  - `requests.memory` 256Mi, `limits.memory` 512Mi (work-service 320Mi / 640Mi); `requests.cpu: 50m`, **không đặt `limits.cpu`** (giới hạn CPU làm Spring khởi động bị bóp).
  - `JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError"`. **65% chứ không phải 70%** vì metaspace, code cache và luồng nằm ngoài heap mà limit chỉ 512–640Mi; `TieredStopAtLevel=1` (chỉ JIT C1) giảm tải CPU khi 5 JVM khởi động cùng lúc trên 2 vCPU.
  - `replicas: 1`; `spring.datasource.hikari.maximum-pool-size: 5` trong từng profile `k8s` (không bật `lazy-initialization`, dễ lỗi lúc chạy).
- [x] **R4 — Hạ tầng có giới hạn** (`datn-platform/values-minikube.yaml`):
  - Kafka: `KAFKA_HEAP_OPTS=-Xms384m -Xmx384m`, `KAFKA_LOG_RETENTION_HOURS=72` (đã xác nhận trong file cấu hình broker thật), listener quảng bá `kafka:9092`.
  - **Keycloak: limit 1280Mi (không phải 1Gi)**, heap `MaxRAMPercentage=40`, `KC_CACHE=local`, chạy `start` (không `start-dev`). Repo từng gặp OOMKilled ở 1Gi ([02 nghiên cứu §8.9](../02_NGHIEN_CUU_CICD_TREN_SOURCE_CODE.md)); đo thật: heap 50% → 1,06 GiB (85% limit), **heap 40% → 0,76 GiB (60%)** cùng tải. Không thêm `-XX:+UseSerialGC` được vì `kc.sh` đã tự chọn ParallelGC.
  - Redis: `--maxmemory 64mb --maxmemory-policy allkeys-lru`. Mailpit: `MP_MAX_MESSAGES=200`, không có Ingress; dùng SMTP thật thì đặt `mailpit.enabled: false`.
- [x] **R5 — Probe cho máy 2 vCPU** (`datn-app/values-minikube.yaml`, `templates/backend.yaml`): `startupProbe` `/actuator/health/liveness` 10 giây × 36 lần (tối đa 6 phút); `livenessProbe` `timeoutSeconds: 5`, `failureThreshold: 5`; `readinessProbe` `/actuator/health/readiness`. `initContainers` chờ theo **chuỗi** `identity → work → workspace` (đúng thứ tự migration trên DB trống: FK `fk_projects_workspace` chỉ được tạo khi bảng `projects` đã có) và Postgres / Redis / Kafka / MinIO / Keycloak sẵn sàng. notification-service **không khởi động được nếu không phân giải được `kafka`** (đã thử), nên init container chờ Kafka là bắt buộc.
- [x] **R6 — Deploy kiểu thay thế, không song song** (`rollout:` trong `datn-app/values-minikube.yaml`): `maxSurge: 0`, `maxUnavailable: 1` (tắt pod cũ rồi mới bật pod mới); Keycloak / Redis / Mailpit `Recreate`. Mặc định `maxSurge: 1` sẽ bật thêm pod mới của cả 5 service (cần thêm ~2 GB) → OOM. **Đánh đổi:** mỗi lần deploy các service gián đoạn **lần lượt** (do chuỗi init ở R5), ước tính 5–8 phút cả đợt, **chưa đo trên Minikube thật** (đo ở bước 5) → ghi trong báo cáo là hạn chế do tài nguyên; có máy 16 GB thì đổi về `maxSurge: 1, maxUnavailable: 0`. `helm upgrade --timeout 15m` (trong `deploy.sh`).
- [ ] **R7 — Đo thực tế rồi chỉnh** (bước 4–5): cột "Đo thử" ở bảng trên là số đo trên **máy dev** (JVM giống hệt, nhưng CPU và nền tảng khác Minikube), chỉ là mốc để so.
  - `kubectl top pods -n datn` (sau khi đăng nhập, tạo dự án, kéo thả vài công việc) → đặt `limits` ≈ mức dùng cao nhất × 1,3.
  - `kubectl describe node minikube | grep -A8 "Allocated resources"` → tổng `requests` memory < 80 %.
  - `free -h` trên host: `available` ≥ 1 GB.
- [ ] **R8 — Theo dõi khi vận hành** (bước 9):
  - `kubectl get pods -n datn` → cột `RESTARTS` không tăng; `kubectl get events -n datn --field-selector reason=OOMKilling`.
  - `dmesg -T | grep -i oom` trên host (OOM ở tầng host = nguy hiểm, có thể sập cả cụm).
  - Vẫn OOM thường xuyên dù đã làm R2–R6 → phương án cuối: thay Minikube bằng **k3s** (không lồng Kubernetes trong container, tiết kiệm ~0,5–1 GB), hoặc xin cấp `t3.xlarge`.

## Vì sao tách 2 server

- Tài khoản chỉ được cấp tối đa **2 vCPU / 7,6 GB mỗi EC2** (`t3.large`). Một máy không đủ chạy cả Jenkins (build ~4–5 GB lúc cao điểm) lẫn Minikube (~6 GB) → tách 2 máy để có tổng ~15 GB.
- Build nặng (Maven, `docker build`) không làm chậm / OOM ứng dụng; Jenkins lỗi hay đang nâng cấp thì app vẫn chạy.
- Đúng mô hình đã viết trong 01 / 02 nghiên cứu / 03 → **không phải sửa** các tài liệu đó (trừ chỗ ghi cấu hình 16 GB và 2 Postgres, xem R2).

## Lưu ý

- Jenkins SSH sang Minikube bằng **IP private** (`172.31.x.x`, không đổi khi stop/start, không tốn băng thông Internet). IP public / Elastic IP chỉ dùng cho DNS `app.` / `sso.` và để bạn SSH vào quản trị.
- Hai EC2 phải **cùng VPC** (thường là VPC mặc định) thì mới thấy nhau qua IP private.
- `deployer` **không** vào nhóm `docker` / `sudo`, và dùng kubeconfig của ServiceAccount `ci-deployer` (chỉ quyền trong ns `datn`), **không** dùng `~/.kube/config` admin của `ubuntu`.
- Muốn tiết kiệm: stop `datn-minikube` khi không dùng; systemd unit (02 §9) tự bật lại cụm khi start.
