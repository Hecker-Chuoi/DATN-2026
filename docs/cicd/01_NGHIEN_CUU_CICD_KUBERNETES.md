# 01 — Nghiên cứu CI/CD trên Kubernetes (Minikube, 1 máy chủ, GitHub Actions + Helm) cho DATN-2026

> Tài liệu nghiên cứu: hiện trạng mã nguồn, kiến thức Kubernetes cần dùng (theo từng module học), các phương án và lựa chọn kiến trúc, những bẫy riêng của dự án.
> Đọc tiếp: [02](02_KE_HOACH_TRIEN_KHAI_CICD.md) (kế hoạch), [03](03_HUONG_DAN_TRIEN_KHAI.md) (hướng dẫn cluster, Helm chart, pipeline), [04](04_HUONG_DAN_GITHUB_ACTIONS.md) (cấu hình GitHub Actions), [05](05_TRIEN_KHAI_TU_DAU_TREN_EC2.md) (làm từ đầu trên EC2).
> Lịch sử: bản trước dùng Jenkins tự cài trên máy chủ. Nhóm chuyển sang GitHub Actions (24/11/2026) vì cài và bảo trì Jenkins quá phức tạp; máy chủ giờ **chỉ chạy dự án**.

---

## 1. Phạm vi

| Hạng mục | Quyết định |
|---|---|
| Cluster | **Minikube** chạy trên **1 máy chủ Linux** (giả định Ubuntu 22.04/24.04), driver `docker` |
| Môi trường | 2 namespace trên cùng cluster: `datn-staging` (tự deploy khi merge `main`) và `datn-prod` (deploy khi gắn tag `v*`, có duyệt tay) |
| Đóng gói manifest | **Helm** (M12), gồm 2 chart: `datn-platform` (quản trị viên cài) và `datn-app` (CI cài) |
| CI/CD | **GitHub Actions**, chạy hoàn toàn trên runner của GitHub. Máy chủ **không** cài runner hay công cụ CI nào; bước deploy tới API minikube qua **đường hầm SSH chỉ mở đúng cổng API** (§4.2), dùng kubeconfig quyền hẹp của `ci-deployer` |
| Repo | GitHub **public** (để dùng miễn phí Environments có duyệt tay và secret riêng cho prod) |
| Registry | GHCR private, push bằng `GITHUB_TOKEN` |
| Truy cập từ ngoài | Ứng dụng (frontend + API), Keycloak, và **công cụ quản trị**: pgAdmin (Pod trong cluster), Mailpit (staging), MinIO Console (tuỳ chọn). Tất cả đi qua Nginx máy chủ (TLS khi có tên miền; tạm thời HTTP qua sslip.io, xem 05) |
| Dev local | Giữ nguyên `docker-compose` + IntelliJ như hiện tại |
| Ngoài phạm vi | Nhiều node, HA thật, service mesh, Prometheus/Grafana đầy đủ (chỉ đề xuất ở mục nâng cao) |

---

## 2. Hiện trạng mã nguồn (đã đọc toàn bộ phần liên quan đến triển khai)

### 2.1. Các thành phần cần chạy

| Thành phần | Cổng | Build / image | Phụ thuộc khi chạy | Ghi chú |
|---|---|---|---|---|
| `api-gateway` | 8081 | `backend/api-gateway/Dockerfile` | Keycloak (JWKS) | Spring Cloud Gateway (WebFlux). Có actuator. Route hard-code `localhost:*` |
| `identity-service` | 8082 | `backend/identity-service/Dockerfile` | Postgres, Keycloak (admin API + token), notification, workspace | `ddl-auto: update`. Client secret và mật khẩu admin Keycloak đang ghi thẳng trong `application.yaml` |
| `work-service` | 8083 | `backend/work-service/Dockerfile` | Postgres (Flyway V1–V10), Redis, Kafka, MinIO, identity, workspace | Service nặng nhất. File đính kèm proxy qua `/attachments/{id}/download`, **MinIO không cần lộ ra ngoài** |
| `workspace-service` | 8087 | **chưa có Dockerfile** | Postgres (Flyway V1–V6, `ddl-auto: validate`), Redis, identity | Phải tạo Dockerfile |
| `notification-service` | 9994 | `backend/notification-service/Dockerfile` | Postgres (Flyway V1–V2), Kafka consumer, SMTP, (Redis khi chạy cụm) | Đọc hầu hết cấu hình từ biến môi trường |
| `frontend` | 80 | `frontend/Dockerfile` (Node → Nginx) | api-gateway (qua `nginx.conf`) | Gọi API bằng đường dẫn tương đối, WebSocket dựng từ `window.location.host`, nên **một image dùng được cho mọi môi trường** |
| Postgres app / Keycloak | 5432 | `postgres:15-alpine` | — | `datn_main_db` dùng chung 4 service; `keycloak` riêng |
| Keycloak | 8080 | `quay.io/keycloak/keycloak:24.0.1` | Postgres Keycloak | Realm `datn-realm`, client `datn-backend`, IdP `google` |
| Kafka | 9092 | `apache/kafka:3.8.0` (KRaft) | — | Topic `notification-events` **hard-code** trong `common/.../NotificationEvent.TOPIC` |
| Redis | 6379 | `redis:7-alpine` | — | Cache, khoá job khi chạy cụm |
| MinIO | 9000/9001 | `cgr.dev/chainguard/minio` | — | `minio/minio` đã bị gỡ khỏi Docker Hub |
| Mailpit | 1025/8025 | `axllent/mailpit` | — | Chỉ cho staging |
| **pgAdmin** | 80 | `dpage/pgadmin4` | Postgres ×2 | Compose đang có (`5050:80`). Trên cluster: **Pod riêng mỗi namespace**, ra ngoài qua Ingress có basic auth |

### 2.2. Luồng request

```
Trình duyệt ──► Nginx máy chủ (TLS) ──► ingress-nginx ─┬─ app.<domain>/identity|work|workspace|notification ─► api-gateway :8081
                                                       ├─ app.<domain>/            ─► frontend :80
                                                       ├─ sso.<domain>             ─► keycloak :8080
                                                       ├─ pgadmin.<domain>         ─► pgadmin :80   (basic auth, giới hạn IP)
                                                       └─ mail-staging.<domain>    ─► mailpit :8025 (basic auth)
GitHub Actions ──SSH :22 (user tunnel, chỉ port-forward)──► 192.168.49.2:8443 (API minikube, kubeconfig ci-deployer)

api-gateway ─► identity :8082, work :8083 ──Kafka──► notification :9994, workspace :8087, notification (REST + STOMP /ws)
identity ──► notification /internal/**, workspace /internal/** ; work ──► identity /internal/**, workspace /internal/**
```

`/internal/**` của identity, workspace và notification **không xác thực**. Gateway cố ý không route các đường này. Trên cluster cần NetworkPolicy (§5.10).

### 2.3. Những điểm phải sửa trước khi lên Kubernetes

| # | Vấn đề | Vị trí | Hậu quả nếu bỏ qua |
|---|---|---|---|
| G1 | `workspace-service` không có Dockerfile | `backend/workspace-service/` | Không build được image |
| G2 | Chỉ `api-gateway` có `spring-boot-starter-actuator` | `backend/*/pom.xml` | Không có `/actuator/health/{liveness,readiness}` cho probe |
| G3 | Security chỉ mở `/actuator/health` (khớp chính xác) ở gateway, identity, notification; gateway còn chặn `/identity/actuator/health`, `/work/...` vì đó không phải health của chính nó | `SecurityConfig.java` | Probe trả 401 khiến pod restart liên tục; smoke test qua Ingress trả 401 |
| G4 | Container chạy root, không có `USER` | 5 Dockerfile backend | Không đặt được `runAsNonRoot` |
| G5 | Frontend build bằng `node:20-alpine`, `npm ci \|\| npm install` | `frontend/Dockerfile` | Vite 8 cần Node `^20.19` hoặc `>=22.12`; build không tái lập được |
| G6 | CORS hard-code `localhost:3000/5173` | `api-gateway/config/CorsConfig.java` | Thiết kế này dùng **cùng origin** nên không lỗi, nhưng nên cho cấu hình được |
| G7 | Cookie `access_token` luôn `secure(false)` | `identity-service/.../AuthController.java` | Thiếu cờ Secure trên HTTPS |
| G8 | Secret nằm trong `application.yaml` và realm export (client secret, `admin/admin`, `123456`, **client secret Google thật**) | identity, work, workspace, `infrastructure/keycloak/*.json` | Phải ghi đè bằng Secret; đổi giá trị cho prod; xoay secret Google |
| G9 | `show-sql: true`, log `DEBUG` | identity, work, workspace | Log rất lớn |
| G10 | Chưa có `.dockerignore` cho `backend/` | `backend/` | Build context kéo theo `target/`, `uploads/`, `.env` |
| G11 | Lint frontend đang có **190 lỗi** | `frontend/` | Không dùng lint làm cổng chặn ngay |
| G12 | Chưa xác nhận test backend có xanh trên JDK 21 | 63 file test | Đã chạy: xanh, trừ `IdentityServiceApplicationTests` cần Postgres thật nên CI loại riêng test này |
| G13 | Các Dockerfile có dòng `COPY workspace-service/pom.xml` lặp 2 lần | 4 Dockerfile | Vô hại, dọn cho gọn |
| G14 | Maven trong `docker build` không giới hạn heap (mặc định 1/4 RAM máy) | 5 Dockerfile | Build trên máy chủ chung có thể lấn RAM của prod |

Không sửa `application.yml` dùng chung của nhóm. Cấu hình Kubernetes nằm trong **profile `k8s`** (`application-k8s.yaml`), được Helm đóng vào ConfigMap rồi mount vào pod.

---

## 3. Kiến thức nền theo module và cách áp dụng

### 3.1. M2 Core: Pod, Deployment, Service, Namespace, `kubectl apply`

| Khái niệm | Áp dụng |
|---|---|
| **Pod** | 1 container ứng dụng cộng 1 init container `wait-deps` chờ Postgres/Kafka/Redis. Kubernetes không có `depends_on` |
| **Deployment** | 6 service không trạng thái, Keycloak, Redis, pgAdmin, Mailpit |
| **StatefulSet + PVC** | Postgres ×2, Kafka, MinIO. Minikube có sẵn StorageClass `standard` (hostPath) |
| **Service (ClusterIP)** | Tên Service trùng tên host trong compose (`api-gateway`, `keycloak`, `kafka`…), nên `frontend/nginx.conf` không cần sửa. Không dùng NodePort/LoadBalancer: mọi thứ ra ngoài qua Ingress |
| **Namespace** | `datn-staging`, `datn-prod`, mỗi namespace đủ bộ dữ liệu riêng (§4.7) |
| **`kubectl apply`** | Helm cũng gửi manifest lên API server; vẫn dùng `kubectl get/describe/logs/rollout` để quan sát. Hiểu `kubectl apply` để hiểu Helm đang làm gì |

### 3.2. M3: Resource requests và limits (bài 16–19)

- **requests** là phần scheduler giữ chỗ; **limits** là trần. Vượt limit memory thì bị `OOMKilled`; vượt limit CPU thì bị bóp.
- **JVM:** `-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError`.
- **Spring Boot khởi động tốn CPU** (30–90 giây): limit CPU ≥ 1 core, có `startupProbe`.
- **LimitRange** đặt mặc định cho container không khai báo; **ResourceQuota** đặt trần cả namespace, để staging không lấn prod.
- **Build không chạy trên máy chủ:** Maven, Node, `docker build`, Trivy đều chạy trên runner của GitHub, nên toàn bộ CPU/RAM máy chủ dành cho cluster. Ngân sách ở [02 §6](02_KE_HOACH_TRIEN_KHAI_CICD.md#6-ngân-sách-tài-nguyên-và-cấu-hình-máy-chủ).

### 3.3. M5: Vòng đời ứng dụng (bài 2–19, 26)

| Bài | Áp dụng |
|---|---|
| Rolling update | `RollingUpdate`, `maxSurge: 1`, `maxUnavailable: 0`: không gián đoạn kể cả khi 1 replica |
| Rollback | Hai tầng: (1) `kubectl rollout undo` theo từng Deployment; (2) **`helm rollback`** cả release về revision trước (Deployment, ConfigMap, Ingress cùng lúc). Pipeline dùng `helm upgrade --rollback-on-failure` (Helm 3: `--atomic`): lỗi là tự rollback |
| Command / Args | Keycloak `args: ["start", "--import-realm"]`; MinIO `args: ["server", "/data", "--console-address", ":9001"]` |
| Env | `SPRING_PROFILES_ACTIVE=k8s`, `JAVA_TOOL_OPTIONS`, `valueFrom.secretKeyRef` |
| ConfigMap | `env-config` (URL công khai, SMTP) và `<svc>-config` (`application-k8s.yaml`). Helm gắn annotation `checksum/config` vào pod template, nên **đổi cấu hình là tự rolling update** |
| Secret | `app-secrets`, `keycloak-realm`, `ghcr-pull`, `admin-basic-auth`: quản trị viên tạo, **không commit**, Helm chỉ tham chiếu theo tên |
| Self-healing / probes | `startupProbe`, `readinessProbe`, `livenessProbe` trên `/actuator/health/{liveness,readiness}` |

**Tắt êm:** `server.shutdown: graceful` + `preStop: sleep 10` + `terminationGracePeriodSeconds: 45`.

### 3.4. M7: Bảo mật (Kubeconfig, RBAC/ServiceAccount, Image security)

1. **Kubeconfig** = clusters + users + contexts. Kubeconfig admin của minikube chỉ nằm ở user `datnadmin` trên máy chủ; **CI không bao giờ có nó**.
2. **ServiceAccount `ci-deployer`** mỗi namespace, gắn **Role** (không phải ClusterRole), chỉ có quyền trên những gì chart `datn-app` tạo ra:
   - `deployments`, `services`, `configmaps`, `ingresses`: get/list/watch/create/update/patch/**delete** (Helm xoá tài nguyên bị bỏ khỏi chart và khi rollback);
   - `replicasets`, `pods`, `pods/log`, `events`, `endpoints`: chỉ đọc;
   - **không có**: `secrets`, `statefulsets`, `persistentvolumeclaims`, `roles`, `rolebindings`.
3. **Helm lưu release bằng ConfigMap** (`HELM_DRIVER=configmap`). Mặc định Helm lưu release trong Secret, khi đó CI sẽ cần quyền đọc/ghi **mọi** Secret của namespace (RBAC không lọc được theo loại Secret). Đổi driver giúp giữ nguyên nguyên tắc "CI không đọc được mật khẩu". Chart `datn-platform` do quản trị viên cài vẫn dùng driver Secret mặc định, nên CI không sửa được release đó.
4. Token dài hạn của SA nằm trong Secret kiểu `kubernetes.io/service-account-token`. Kubeconfig dựng từ token này (trỏ tới `https://127.0.0.1:8443`, đầu ra của đường hầm SSH) được lưu thành **secret `KUBE_CONFIG` của GitHub Environment**: environment `staging` giữ kubeconfig staging, environment `production` giữ kubeconfig prod. Environment `production` chỉ cho `main` và tag `v*` dùng, và bắt **duyệt tay (required reviewers)**; workflow của PR hay nhánh khác không đọc được secret prod; PR từ fork không đọc được secret nào.
5. Kiểm chứng: `kubectl auth can-i --as=system:serviceaccount:datn-prod:ci-deployer get secrets -n datn-prod` → `no`.
6. **SA `datn-app`** cho pod ứng dụng: `automountServiceAccountToken: false`, `imagePullSecrets: ghcr-pull`.
7. **Image security:** GHCR private + `imagePullSecrets`; tag bất biến theo git SHA; **Trivy quét trước khi push** (hiện chỉ báo cáo, không chặn: thư viện của Spring Boot 3.5.x có CVE CRITICAL mà bản vá chỉ có ở Spring 7; bật chặn lại bằng `TRIVY_EXIT_CODE=1`); non-root (`runAsNonRoot`, `allowPrivilegeEscalation: false`, `drop: [ALL]`); ghim phiên bản image hạ tầng.
8. **Đường vào cluster của CI:** API minikube (`192.168.49.2:8443`) chỉ máy chủ thấy được. CI vào bằng SSH tới user `tunnel` trên máy chủ, khoá SSH bị giới hạn trong `authorized_keys`: `restrict,port-forwarding,permitopen="192.168.49.2:8443",command="/usr/sbin/nologin"`, tức **chỉ mở được đúng một đường tới API server**, không shell, không chạy lệnh, không đi nơi khác. Lộ khoá này thì kẻ tấn công vẫn cần thêm kubeconfig `ci-deployer` mới làm được gì, và cũng chỉ trong namespace của app. minikube là một container trên Docker gốc, nên user `tunnel` tuyệt đối không vào nhóm `docker`.
9. **Công cụ quản trị lộ ra ngoài** (pgAdmin, Mailpit, MinIO Console) có 3 lớp bảo vệ: TLS, basic auth ở Ingress (`admin-basic-auth`), danh sách IP cho phép (`whitelist-source-range`), cộng với đăng nhập riêng của từng công cụ.

### 3.5. M9: Networking (Service, DNS, Ingress)

- **DNS nội bộ** `<svc>.<ns>.svc.cluster.local`; cùng namespace chỉ cần `<svc>`. Cùng một chart cài vào 2 namespace nên **cố ý dùng tên ngắn**, để mỗi môi trường chỉ gọi trong namespace của nó. pgAdmin kết nối `postgres-app:5432` theo đúng cách này.
- **Ingress (ingress-nginx, addon minikube):**

| Host | Đi tới | Bảo vệ |
|---|---|---|
| `app.<domain>` | `/identity` `/work` `/workspace` `/notification` → `api-gateway:8081`; `/` → `frontend:80` | Đăng nhập Keycloak |
| `sso.<domain>` | `keycloak:8080`; riêng `/admin` có thể giới hạn IP | Đăng nhập Keycloak |
| `pgadmin.<domain>` | `pgadmin:80` | Basic auth + IP + đăng nhập pgAdmin |
| `mail-staging.<domain>` | `mailpit:8025` (chỉ staging) | Basic auth + IP |
| `minio.<domain>` *(tuỳ chọn)* | `minio:9001` (console) | Basic auth + IP + đăng nhập MinIO |

Frontend và API cùng origin nên không có CORS. ingress-nginx hỗ trợ WebSocket; tăng `proxy-read-timeout` cho STOMP.
- **Ra Internet:** với driver `docker`, IP minikube (`192.168.49.2`) chỉ máy chủ thấy được. **Nginx máy chủ** nghe 80/443, kết thúc TLS rồi `proxy_pass` tới IP minikube. Bật `use-forwarded-headers` ở ingress-nginx để `X-Forwarded-Proto: https` đi tới gateway/Keycloak và để whitelist IP đọc đúng IP thật.
- **NetworkPolicy** cần `--cni=calico`.

### 3.6. M12 Helm (đã chọn) và so sánh với M13 Kustomize

| Tiêu chí | **Helm (chọn)** | Kustomize |
|---|---|---|
| Cài thêm | Binary `helm` (trên máy chủ cho quản trị viên; trên runner bằng `azure/setup-helm`) | Có sẵn trong `kubectl` |
| Cách làm | Template Go + `values.yaml` + `values-<env>.yaml` | YAML thuần + overlay patch |
| Lặp 5 service Spring Boot giống nhau | **Một template** `range` qua `.Values.backend` | Patch theo label hoặc chép 5 file |
| Đổi image tag trong CI | `--set image.tag=<sha>` | `kustomize edit set image` |
| Bật/tắt thành phần theo môi trường | `pgadmin.enabled`, `mailpit.enabled` | Thêm/bớt resource trong overlay |
| Rollback | **`helm rollback`**: cả release, có lịch sử `helm history` | `kubectl rollout undo` từng Deployment |
| Tự rollback khi lỗi | `--rollback-on-failure` / `--atomic` có sẵn | Phải tự viết script |
| Kiểm tra trước khi áp | `helm lint`, `helm template \| kubeconform` | `kubectl kustomize \| kubeconform` |

**Hai chart:**
- `datn-platform` (quản trị viên cài, kubeconfig admin): RBAC `ci-deployer`, SA `datn-app`, quota, NetworkPolicy, Postgres ×2, Keycloak, Kafka, Redis, MinIO, Mailpit, pgAdmin, Ingress công cụ quản trị.
- `datn-app` (GitHub Actions cài bằng `ci-deployer`): 6 Deployment/Service, ConfigMap, Ingress ứng dụng.

Mỗi chart có `values.yaml` (mặc định) cộng `values-staging.yaml` / `values-prod.yaml`. CI chỉ đổi `image.tag`. Không dùng chart Bitnami: từ 2025 Bitnami đã giới hạn image miễn phí. Chart tự viết vẫn đủ nhỏ để đọc hiểu.

---

## 4. Các phương án kiến trúc CI/CD và lựa chọn

### 4.1. Nền tảng CI: **GitHub Actions**

| Tiêu chí | Jenkins tự cài trên máy chủ (bản trước) | **GitHub Actions (chọn)** |
|---|---|---|
| Cài đặt, bảo trì | Java, Jenkins, plugin, Docker rootless, Nginx, sao lưu, cập nhật hằng tháng | Không cài gì; workflow là file YAML trong repo |
| Tài nguyên máy chủ | Build ăn CPU/RAM của chính máy đang chạy prod | Build trên máy của GitHub; máy chủ chỉ chạy dự án |
| Chi phí | Không tính phút | Repo public: không giới hạn phút runner chuẩn |
| Tiếp cận API minikube | Trực tiếp (cùng máy) | Phải có đường vào (§4.2) |
| Duyệt prod, tách credential prod | Bước `input` + credential theo folder | **Environment** `production`: required reviewers + secret chỉ `main`/tag `v*` đọc được (miễn phí với repo public) |
| Đăng nhập GHCR | PAT classic `write:packages` | `GITHUB_TOKEN` có sẵn (`permissions: packages: write`), không cần PAT |

Chọn GitHub Actions: đơn giản hơn hẳn, và tách CI khỏi máy chủ.

### 4.2. CD tới API minikube bằng đường nào

API server của minikube (driver docker) nằm ở `192.168.49.2:8443`, chỉ máy chủ thấy được; runner của GitHub ở ngoài Internet.

| Phương án | Nhận xét |
|---|---|
| Self-hosted runner trên máy chủ | Phải cài và cập nhật runner; runner thực thi mã của workflow ngay trên máy chủ. Trái yêu cầu "máy chủ chỉ chạy dự án". Loại |
| Mở API server ra Internet | Lộ bề mặt tấn công của cả cluster. Loại |
| **Đường hầm SSH bị khoá chặt** | Runner `ssh -L 127.0.0.1:8443:192.168.49.2:8443 tunnel@<server>`, rồi `helm` gọi `https://127.0.0.1:8443` bằng kubeconfig `ci-deployer` (chứng chỉ API minikube có sẵn IP `127.0.0.1`). Khoá SSH chỉ được port-forward đúng đích đó (§3.4 mục 8). Máy chủ chỉ cần thêm 1 user hệ thống. **Chọn** |
| Argo CD (pull, GitOps) | Không cần đường vào từ ngoài, nhưng thêm một hệ thống phải học và vận hành. Ghi ở mục nâng cao |

Đổi lại, cổng 22 của Security Group phải mở cho Internet (runner GitHub không có IP cố định). Bù lại: SSH chỉ chấp nhận khoá (mặc định của Ubuntu trên EC2), user `tunnel` không có shell.

**Các workflow** (`.github/workflows/`):

| File | Kích hoạt | Làm gì |
|---|---|---|
| `ci-cd.yml` | PR vào `main`; push `main` | Test backend, lint + build frontend, `helm lint` + `kubeconform`. Riêng `main`: build/quét/push 6 image song song (tag = git SHA), rồi deploy staging |
| `release.yml` | Push tag `vX.Y.Z` | Gắn tag phiên bản cho image đã có của commit đó (không build lại), rồi deploy prod (chờ duyệt) |
| `deploy-manual.yml` | Bấm tay (*Run workflow*) | Deploy một SHA bất kỳ hoặc `helm rollback`, staging hoặc prod (prod chờ duyệt) |
| `deploy.yml` | Được 3 file trên gọi (`workflow_call`) | Mở đường hầm SSH, chạy `deploy.sh` hoặc `helm rollback` |

### 4.3. Registry: GHCR private
Miễn phí, private, thực hành `imagePullSecrets`. Workflow push bằng `GITHUB_TOKEN`; cluster kéo bằng PAT classic `read:packages` (Secret `ghcr-pull`). Tên image **bắt buộc chữ thường**: `ghcr.io/hecker-chuoi/datn-<service>`.
Dự phòng khi mất Internet: build xong `minikube image load` ([03 §7](03_HUONG_DAN_TRIEN_KHAI.md#7-cài-ứng-dụng-lần-đầu-bằng-tay)).

### 4.4. Push hay Pull (GitOps)
**Push**: workflow chạy `helm upgrade` bằng kubeconfig quyền hẹp, đúng trọng tâm M7. Argo CD (pull) ghi ở mục nâng cao.

### 4.5. Hạ tầng có trạng thái: trong cluster, do quản trị viên cài
Chart `datn-platform` do quản trị viên cài; Role của `ci-deployer` không có `statefulsets`/`pvc`/`secrets`, nên lỗi pipeline không phá được dữ liệu.

### 4.6. Công cụ quản trị: chạy ở đâu

| Công cụ | Chạy ở | Lý do |
|---|---|---|
| **pgAdmin** | **Pod trong mỗi namespace** (chart `datn-platform`) | Postgres chỉ có ClusterIP, nên pgAdmin phải ở trong cluster mới tới được `postgres-app:5432`. Mỗi namespace một pgAdmin nên không nhầm DB staging/prod; NetworkPolicy chặn chéo namespace |
| **Mailpit UI** | Pod trong `datn-staging` | Xem email thử (xác thực, lời mời…) |
| **MinIO Console** | Port 9001 của pod MinIO, Ingress **tắt mặc định** | Ít khi cần; mở khi phải kiểm tra file đính kèm |
| **Keycloak Admin Console** | `sso.<domain>/admin` | Có sẵn; nên giới hạn IP (`keycloak.adminAllowCidrs`) |
| **CI/CD** | Trang *Actions* của repo trên GitHub | Không có gì chạy trên máy chủ |

Phương án thay thế không mở ra Internet: `kubectl port-forward svc/pgadmin 5050:80` qua SSH tunnel. An toàn nhất nhưng bất tiện cho cả nhóm.

### 4.7. Hai môi trường trên một máy: mỗi namespace một bộ dữ liệu riêng
Topic `notification-events` và consumer group `notification-service` đều hard-code: dùng chung Kafka thì staging sẽ đọc sự kiện của prod và gửi email thật. `outbox_events` và `flyway_*_schema_history` cũng theo DB. Vì vậy mỗi namespace có Postgres, Kafka, Redis, MinIO, Keycloak riêng (khoảng 4,5–5 GiB request mỗi môi trường).

### 4.8. Quản lý Secret
`kubectl create secret` do quản trị viên chạy, giá trị không vào Git; Helm chỉ tham chiếu tên. Nâng cao: Sealed Secrets / SOPS (`helm-secrets`).

---

## 5. Những bẫy riêng của DATN-2026

1. **Issuer JWT:** `KC_HOSTNAME_URL=https://sso.<domain>` (Keycloak 24) cố định claim `iss`. Backend dùng `issuer-uri` công khai và `jwk-set-uri` nội bộ `http://keycloak:8080/...`. (Keycloak ≥ 25: `KC_HOSTNAME=https://...`.)
2. **Route gateway là danh sách:** ghi đè bằng `SPRING_CLOUD_GATEWAY_ROUTES_0_*` sẽ thay cả danh sách (lỗi "mất route `/work/**`" của compose hiện tại). Dùng `application-k8s.yaml` khai đủ 4 route.
3. **Service links:** Service `kafka` sinh biến `KAFKA_PORT=tcp://...`, và image `apache/kafka` coi mọi `KAFKA_*` là cấu hình broker. Đặt `enableServiceLinks: false` cho mọi pod.
4. **Forwarded headers:**
   - Chuỗi proxy: Nginx máy chủ → ingress-nginx → gateway. Thiếu `use-forwarded-headers` thì gateway thấy `http` trong khi `Origin` là `https://…`, nên trả 403 cho POST và WebSocket.
   - Nginx máy chủ là lớp ngoài cùng nên phải **ghi đè** `X-Forwarded-For $remote_addr` (không nối thêm). Nếu nối thêm, người ngoài tự gửi `X-Forwarded-For` giả để vượt danh sách IP cho phép của pgAdmin.
5. **Thứ tự migration trên DB trống:** workspace V3 chỉ thêm FK vào `projects` nếu bảng đã có. Init container của workspace-service chờ work-service Ready.
6. **Chạy từ 2 instance:** rolling update luôn có lúc 2 pod. Đặt `APP_CLUSTER_REDIS_ENABLED=true` cho work và notification.
7. **Flyway khi rolling update:** migration phải tương thích ngược (expand → contract).
8. **Keycloak realm:**
   - Thêm `redirectUris` công khai và thay client secret trước khi import.
   - Khai báo redirect URI broker Google.
   - Chạy HTTP (nip.io) thì đặt `sslRequired=none`.
9. **Dữ liệu minikube:** `minikube delete` xoá sạch PVC, nên phải `pg_dump` định kỳ.
10. **`/internal/**` không xác thực:** NetworkPolicy chặn chéo namespace, chỉ cho ingress-nginx vào các cửa công khai.
11. **UID của image:** MinIO Chainguard 65532, Kafka 1000, pgAdmin 5050, nên cần `fsGroup` tương ứng.
12. **Một frontend image cho mọi môi trường:** không có build-arg theo môi trường, đây là điều kiện để "build once, deploy many".
13. **Helm và quyền Secret:** driver mặc định lưu release ở Secret, khiến CI cần quyền Secret. Dùng `HELM_DRIVER=configmap` cho release `datn-app`, và **lần cài đầu bằng tay cũng phải dùng driver này**. Nếu không, CI không thấy release và báo `resource already exists` do tài nguyên đang thuộc release khác.
14. **Helm 4 đổi tên cờ:** `--atomic` thành `--rollback-on-failure`. Script deploy tự dò theo `helm upgrade --help`.
15. **Workflow là mã chạy với secret:** ai push được nhánh thì sửa được workflow. Vì vậy secret quan trọng nằm ở **Environment** có luật nhánh (`staging`: chỉ `main`; `production`: `main` + tag `v*`, thêm required reviewers) và bảo vệ nhánh `main` (bắt buộc PR + review). Workflow chạy cho PR từ fork không nhận secret nào; **không dùng `pull_request_target`**.
16. **Repo public:** mọi file trong lịch sử Git đều công khai. Trước khi chuyển public phải xoay mọi secret thật từng commit (client secret Google trong realm export, xem G8); mật khẩu mặc định trong `application.yaml` chỉ dùng cho máy dev.
17. **Basic auth và Keycloak Admin Console:** console gọi API bằng header `Authorization: Bearer`, nên đặt basic auth (cũng dùng header `Authorization`) lên `/admin` sẽ làm hỏng console. Với `/admin` chỉ giới hạn IP.
18. **ingress-nginx:** theo thông báo của Kubernetes SIG Network, dự án ingress-nginx đã dừng phát triển (khoảng 03/2026). Addon minikube vẫn dùng được cho đồ án; hướng lâu dài là Gateway API (ví dụ Envoy Gateway, Traefik). Ghi nhận ở mục rủi ro.

---

## 6. Tài liệu tham khảo

- Deployments, rolling update — https://kubernetes.io/docs/concepts/workloads/controllers/deployment/
- Probes — https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/
- Resource, LimitRange, ResourceQuota — https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/ ; https://kubernetes.io/docs/concepts/policy/
- RBAC — https://kubernetes.io/docs/reference/access-authn-authz/rbac/ ; ServiceAccount — https://kubernetes.io/docs/concepts/security/service-accounts/
- Kubeconfig — https://kubernetes.io/docs/concepts/configuration/organize-cluster-access-kubeconfig/
- Image private registry — https://kubernetes.io/docs/tasks/configure-pod-container/pull-image-private-registry/
- DNS, Ingress, NetworkPolicy — https://kubernetes.io/docs/concepts/services-networking/
- Ingress trên Minikube — https://kubernetes.io/docs/tasks/access-application-cluster/ingress-minikube/
- ingress-nginx annotations (basic auth, whitelist) — https://kubernetes.github.io/ingress-nginx/user-guide/nginx-configuration/annotations/
- Helm: chart template, values, `helm upgrade`, `helm rollback`, storage driver — https://helm.sh/docs/
- Spring Boot trên Kubernetes — https://docs.spring.io/spring-boot/how-to/deployment/cloud.html
- Keycloak: toàn bộ tuỳ chọn cấu hình — https://www.keycloak.org/server/all-config
- pgAdmin container — https://www.pgadmin.org/docs/pgadmin4/latest/container_deployment.html
- GitHub Actions: workflow syntax, reusable workflows, Environments (required reviewers, deployment branches), secrets — https://docs.github.com/actions
- Bảo mật GitHub Actions (fork PR, `pull_request_target`, `GITHUB_TOKEN` permissions) — https://docs.github.com/actions/security-for-github-actions
- OpenSSH `authorized_keys` (restrict, permitopen, command) — https://man.openbsd.org/sshd#AUTHORIZED_KEYS_FILE_FORMAT
- GHCR — https://docs.github.com/packages/working-with-a-github-packages-registry/working-with-the-container-registry
