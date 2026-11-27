# 02 — Nghiên cứu: triển khai CI/CD trên source code DATN-2026

> Mục tiêu: liệt kê **cần những gì** trong repo và trên hạ tầng để có CI/CD **Jenkins → Docker Hub → Helm → Minikube**. Tài liệu chỉ ở mức định hướng (cái gì, đặt ở đâu, bẫy nào), không phải hướng dẫn từng lệnh.
> Đọc kèm: [01 — Setup Jenkins](cac_buoc/01_HUONG_DAN_SETUP_JENKINS.md), [03 — Tổng quan các bước](03_TONG_QUAN_CAC_BUOC_TRIEN_KHAI.md).

## Mục lục
1. [Luồng tổng thể](#1-luồng-tổng-thể)
2. [Checklist: đã có gì, cần thêm gì](#2-checklist-đã-có-gì-cần-thêm-gì)
3. [Docker image và Docker Hub](#3-docker-image-và-docker-hub)
4. [Helm chart và Minikube](#4-helm-chart-và-minikube)
5. [Deploy từ Jenkins sang Minikube](#5-deploy-từ-jenkins-sang-minikube)
6. [Jenkinsfile](#6-jenkinsfile)
7. [Cấu hình ứng dụng khi chạy trên Kubernetes](#7-cấu-hình-ứng-dụng-khi-chạy-trên-kubernetes)
8. [Bẫy đặc thù của dự án](#8-bẫy-đặc-thù-của-dự-án)
9. [Tham khảo bản triển khai cũ trong git](#9-tham-khảo-bản-triển-khai-cũ-trong-git)

---

## 1. Luồng tổng thể

```
git push ─► GitHub ─webhook─► Jenkins (server riêng)
                                 │ 1. Checkout
                                 │ 2. Test backend (mvn verify) + lint/build frontend
                                 │ 3. docker build 6 image, tag = git SHA
                                 │ 4. docker push ─────────────► Docker Hub
                                 │ 5. ssh deployer@minikube-server          ▲
                                 ▼    "helm upgrade --set image.tag=<sha>"  │ pull image
                          Server Minikube ── helm ─► Deployment đổi image ──┘
                                                     rolling update, lỗi thì rollback
```

| Giai đoạn | Khi nào chạy | Kết quả |
|---|---|---|
| **CI** (bước 1–2) | Mọi nhánh, mọi PR | Báo xanh/đỏ lên GitHub |
| **Build & Push** (bước 3–4) | Chỉ nhánh `main` (hoặc tag `vX.Y.Z`) | Image `<DOCKERHUB_USER>/datn-<service>:<sha>` |
| **CD** (bước 5) | Chỉ `main`, sau khi push xong | Minikube chạy bản mới |

Hai chart Helm, hai vai trò:
- **`datn-platform`** — hạ tầng có trạng thái (Postgres, Keycloak, Kafka, Redis, MinIO, Mailpit). **Quản trị viên cài tay một lần**, CI không đụng tới (tránh pipeline lỡ tay xoá DB).
- **`datn-app`** — 5 service Spring Boot + frontend + Ingress. **CI chỉ đổi `image.tag`** của chart này.

---

## 2. Checklist: đã có gì, cần thêm gì

| Hạng mục | Trạng thái | Việc cần làm |
|---|---|---|
| Dockerfile 5 backend + frontend | ✅ Có (multi-stage, BuildKit cache; backend chạy user 10001) | ✅ Đã thêm `ARG VITE_KEYCLOAK_URL` cho frontend (§3.3) |
| `backend/.dockerignore` | ✅ Có | — |
| Actuator health (`/actuator/health/liveness`, `/readiness`) | ✅ Có, đã mở trong Security | ✅ Đã bật `management.endpoint.health.probes.enabled=true` trong profile `k8s` |
| CORS / cookie đọc từ cấu hình (`app.cors.allowed-origins`, `app.cookie.secure`) | ✅ Có | Đặt giá trị theo tên miền thật |
| `frontend/nginx.conf` proxy tới `api-gateway:8081` | ✅ Có | ✅ Service k8s tên `api-gateway`, cổng 8081 (chart `datn-app`) |
| Cấu hình Spring cho k8s (profile `k8s` hoặc biến môi trường) | ✅ `infrastructure/helm/datn-app/files/config/*/application-k8s.yaml` | §7 |
| Helm chart `datn-platform`, `datn-app` | ✅ `infrastructure/helm/` (viết lại cho Minikube 2 vCPU / 8 GB, [cac_buoc/04](cac_buoc/04_VIET_HELM_CHART_VA_SCRIPT.md)) | §4 |
| Script build + push image | ✅ `infrastructure/ci/build-push.sh` | §3.4 |
| `Jenkinsfile` ở gốc repo | ❌ | §6 |
| Script deploy trên server Minikube + RBAC cho `deployer` | ✅ `infrastructure/k8s/deploy.sh`, `rbac-deployer.yaml`, `make-ci-kubeconfig.sh` | §5 |
| Secret (mật khẩu DB, Keycloak, MinIO, SMTP) | ❌ | Tạo tay bằng `kubectl create secret`, **không** commit |
| Realm Keycloak cho môi trường mới | ✅ `infrastructure/k8s/make-realm-secret.sh` sửa client `datn-backend` (secret, redirect URI, web origin) rồi tạo Secret `keycloak-realm` | Keycloak tự import khi khởi động lần đầu |

Gợi ý cây thư mục mới:

```
Jenkinsfile
infrastructure/
├── ci/
│   └── build-push.sh               # build + (quét) + push 6 image
├── helm/
│   ├── env-minikube.yaml           # tên miền, scheme, registry: NƠI DUY NHẤT, cả hai chart đọc qua -f
│   ├── datn-platform/              # hạ tầng, admin cài 1 lần (Postgres, Keycloak, Kafka, Redis, MinIO, Mailpit)
│   │   ├── values.yaml
│   │   ├── values-minikube.yaml    # R2, R4: ngân sách 8 GB
│   │   └── templates/ (postgres, keycloak, kafka, redis, minio, mailpit)
│   └── datn-app/                   # ứng dụng, CI đổi image.tag
│       ├── values.yaml
│       ├── values-minikube.yaml    # R3, R5, R6: JVM, probe, kiểu deploy
│       ├── files/config/<service>/application-k8s.yaml
│       └── templates/ (backend.yaml, frontend.yaml, ingress.yaml, configmap-*.yaml)
└── k8s/
    ├── namespace-limits.yaml       # LimitRange + ResourceQuota của namespace datn
    ├── rbac-deployer.yaml          # ServiceAccount + Role cho user deployer
    ├── make-ci-kubeconfig.sh       # kubeconfig giới hạn quyền cho deployer
    ├── make-realm-secret.sh        # Secret keycloak-realm từ realm export đã chỉnh
    ├── set-public-host.sh          # đặt tên miền / registry vào env-minikube.yaml
    └── deploy.sh                   # copy sang server Minikube (/home/deployer/deploy.sh)
```

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

- Tag chính = **git SHA ngắn** (`a1b2c3d`): bất biến, biết image nào ứng với commit nào, rollback = deploy lại SHA cũ.
- Có thể thêm tag phụ `main` / `latest` để tiện xem, nhưng **Helm luôn deploy theo SHA**. Deploy bằng `latest` thì Kubernetes không biết image đã đổi (cùng tag, `IfNotPresent` không kéo lại) và không rollback được.
- Release: khi tạo git tag `v1.0.0`, gắn thêm tag `v1.0.0` cho image cùng SHA (không build lại).

### 3.3. Frontend: biến build-time

`VITE_KEYCLOAK_URL` (`frontend/src/App.jsx`) được Vite **nhúng vào lúc build**, không đổi được khi chạy. Cần:
- Dockerfile frontend: `ARG VITE_KEYCLOAK_URL` + `ENV VITE_KEYCLOAK_URL=$VITE_KEYCLOAK_URL` trước `npm run build`.
- Jenkins truyền `--build-arg VITE_KEYCLOAK_URL=https://sso.<domain>`.
- Hệ quả: image frontend gắn với một tên miền Keycloak. Muốn một image chạy nhiều môi trường thì đổi sang nạp cấu hình lúc chạy (file `config.js` do nginx phục vụ, mount từ ConfigMap) — không bắt buộc cho DATN.

API thì frontend gọi đường dẫn tương đối (`/identity/...`, `/work/...`) nên không cần biến.

### 3.4. Script build + push

Một script `infrastructure/ci/build-push.sh <tag>` lặp qua 6 service: `docker build` → (tuỳ chọn) `trivy image` chỉ báo cáo → `docker push`. Lý do để trong script thay vì viết thẳng trong `Jenkinsfile`: chạy tay được khi Jenkins hỏng, dễ test.

Ý cần nhớ:
- `export DOCKER_BUILDKIT=1` (Dockerfile backend dùng `RUN --mount=type=cache`).
- `docker login` bằng **Access Token** (credential `dockerhub`), `docker logout` khi xong.
- 5 image backend build lại Maven mỗi lần → chậm (5–15 phút). Có thể tối ưu sau: build jar một lần bằng Maven trên Jenkins rồi Dockerfile chỉ COPY jar, hoặc chỉ build service có thay đổi.

### 3.5. Docker Hub

- Repo **public**: Minikube kéo không cần đăng nhập, nhưng vẫn nên tạo `imagePullSecret` vì pull ẩn danh bị giới hạn số lần.
- Repo **private**: bắt buộc `imagePullSecret`:
  `kubectl -n datn create secret docker-registry dockerhub-pull --docker-server=https://index.docker.io/v1/ --docker-username=<user> --docker-password=<token-read-only>`
  rồi gắn vào ServiceAccount của app hoặc `imagePullSecrets` trong chart.
- Dùng 2 token khác nhau: token **Read & Write** cho Jenkins (push), token **Read-only** cho cluster (pull).

---

## 4. Helm chart và Minikube

### 4.1. Chart `datn-app`

| Template | Nội dung |
|---|---|
| `backend.yaml` | **Một template lặp** qua map `backend:` trong values → Deployment + Service cho 5 service (port, replicas, resources, env từ Secret) |
| `frontend.yaml` | Deployment + Service `frontend:80` |
| `configmap-env.yaml` | Biến dùng chung: `APP_PUBLIC_URL`, `KEYCLOAK_ISSUER_URI`, `MAIL_*`, ... |
| `configmap-spring.yaml` | File `application-k8s.yaml` từng service (mount vào `/config`, chạy với `SPRING_PROFILES_ACTIVE=k8s`) |
| `ingress.yaml` | `/` → frontend; `/identity`, `/work`, `/workspace`, `/notification` → api-gateway |

Mỗi Deployment backend cần: `readinessProbe` `/actuator/health/readiness`, `livenessProbe` `/actuator/health/liveness` (có `startupProbe` vì Spring khởi động chậm 30–90 s), `resources.requests/limits`, `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=70`, chiến lược `RollingUpdate maxUnavailable: 0`.

Values chỉ chứa thứ **không bí mật**. Bí mật nằm trong Secret `app-secrets` do admin tạo; chart chỉ tham chiếu (`secretKeyRef`).

### 4.2. Chart `datn-platform`

Mỗi thành phần một StatefulSet/Deployment đơn giản 1 replica + PVC (Minikube có sẵn `storage-provisioner`):

| Thành phần | Image gợi ý | Lưu ý |
|---|---|---|
| Postgres (**một instance**, 2 database) | `postgres:15-alpine` | `datn_main_db` dùng chung cho identity/work/workspace/notification (owner `datn`) và `keycloak` cho Keycloak (owner `kc_user`). Gộp một instance để tiết kiệm RAM trên máy 8 GB (R2, xem [cac_buoc/00](cac_buoc/00_DANH_SACH_CAC_BUOC.md)); nếu có máy lớn thì tách hai StatefulSet như bản cũ |
| Keycloak | `quay.io/keycloak/keycloak:24.0.1` (cùng bản compose của nhóm) | limit 1280Mi, heap 40%, `KC_HOSTNAME_URL` = URL công khai |
| Kafka | `apache/kafka:3.8.0` (KRaft, 1 node) | `advertised.listeners` = `kafka:9092` |
| Redis | `redis:7-alpine` | |
| MinIO | **không** dùng `minio/minio` (đã bị gỡ khỏi Docker Hub) | Bản cũ dùng `cgr.dev/chainguard/minio` |
| Mailpit | `axllent/mailpit:v1.34.0` | Chỉ để test email, không có Ingress (xem qua `kubectl port-forward`) |
| pgAdmin | — (bỏ) | Đỡ tốn ~200 MB và không lộ công cụ quản trị DB ra Internet; cần xem DB thì `kubectl exec ... psql` hoặc `port-forward` rồi dùng pgAdmin trên máy bạn |

Có thể dùng chart dựng sẵn (Bitnami, ...) thay vì tự viết, nhưng nhiều image Bitnami miễn phí đã bị chuyển sang `bitnamilegacy` / thu hẹp từ 2025 — kiểm tra trước khi chọn.

### 4.3. Minikube trên server

- Cấu hình gợi ý: server **4 vCPU / 16 GB RAM / 60+ GB đĩa**; `minikube start --driver=docker --cpus=4 --memory=12g --disk-size=50g`. Toàn bộ hệ thống (hạ tầng + 6 app) cần khoảng 5–7 GB RAM lúc chạy, thêm phần dư cho rolling update.
- Addon: `minikube addons enable ingress` (và `metrics-server` nếu muốn xem tài nguyên).
- **Truy cập từ Internet**: với driver docker, Minikube chỉ có IP nội bộ (`minikube ip`, thường `192.168.49.2`) — máy ngoài không vào được. Cách đơn giản: **Nginx trên server Minikube** reverse proxy `app.<domain>` và `sso.<domain>` → `http://192.168.49.2:80` (giữ header `Host`, nâng cấp WebSocket, `client_max_body_size 50m`), certbot cho HTTPS. Cách khác: `minikube tunnel` hoặc `kubectl port-forward --address 0.0.0.0` (chỉ hợp để thử).
- Namespace: một namespace `datn` là đủ. Muốn có staging thì thêm `datn-staging` + `values-staging.yaml`.

---

## 5. Deploy từ Jenkins sang Minikube

API server của Minikube chỉ nghe trên IP nội bộ của server Minikube, nên Jenkins (server khác) không gọi trực tiếp được. Ba cách:

| Cách | Mô tả | Đánh giá |
|---|---|---|
| **A. SSH + script (khuyến nghị)** | Jenkins gửi chart qua SSH, chạy `deploy.sh <tag>` trên server Minikube; script gọi `helm upgrade` | Đơn giản nhất, Jenkins không cần kubectl/helm, không mở cổng API |
| B. Đường hầm SSH tới API | `ssh -L 8443:192.168.49.2:8443`, Jenkins tự chạy `helm` với kubeconfig trỏ `https://127.0.0.1:8443` | Mọi công cụ nằm ở Jenkins; cấu hình phức tạp hơn |
| C. GitOps (Argo CD trong Minikube) | Jenkins chỉ sửa `image.tag` trong git; Argo CD tự kéo và đồng bộ | Chuẩn hiện đại nhưng thêm một hệ thống phải học/vận hành |

**Cách A — những gì cần có:**

1. **Trên server Minikube**: user `deployer` (01 §9) có `kubectl` + `helm`, **không** vào nhóm `docker`/`sudo`.
2. **RBAC** (`infrastructure/k8s/rbac-deployer.yaml`): ServiceAccount `ci-deployer` trong namespace `datn` + Role chỉ cho phép thao tác Deployment, Service, ConfigMap, Ingress, Pod (xem log), và Secret (Helm lưu lịch sử release trong Secret; hoặc đặt `HELM_DRIVER=configmap` để không cần quyền Secret). Tạo kubeconfig từ token của SA này cho `deployer`. **Không** đưa `~/.kube/config` admin của minikube cho `deployer`.
3. **Script `deploy.sh`** trên server Minikube, ý chính:

   ```bash
   TAG="$1"                                      # git SHA
   WORK=$(mktemp -d); trap 'rm -rf "$WORK"' EXIT
   tar xz -C "$WORK"                             # chart Jenkins gửi qua stdin
   helm upgrade --install datn-app "$WORK/datn-app" -n datn \
     -f "$WORK/datn-app/values-minikube.yaml" \
     --set image.tag="$TAG" \
     --wait --timeout 15m --rollback-on-failure  # Helm 3: dùng --atomic thay cho --rollback-on-failure
   # Smoke test: curl https://app.<domain>/identity/actuator/health ... lỗi thì: helm rollback datn-app
   ```

4. **Trong Jenkinsfile**: `tar cz -C infrastructure helm | ssh deployer@<MINIKUBE_HOST> "./deploy.sh $TAG"`. Gửi **cả thư mục `helm/`** (chứ không chỉ `datn-app`) vì `env-minikube.yaml` nằm cạnh hai chart. Gửi chart theo từng lần deploy đảm bảo chart và image cùng một commit.
5. **Rollback**: `helm history datn-app -n datn` → `helm rollback datn-app <revision> -n datn`. Có thể tạo thêm một job Jenkins "Deploy SHA cũ / Rollback" có tham số.

---

## 6. Jenkinsfile

Khung mẫu (Declarative), đặt ở gốc repo. Tên tool/credential khớp với [01 §8–9](cac_buoc/01_HUONG_DAN_SETUP_JENKINS.md#8-global-tools-jdk-maven-node).

```groovy
pipeline {
  agent any
  options {
    timestamps()
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '20'))
    timeout(time: 60, unit: 'MINUTES')
  }
  tools { jdk 'jdk-21'; maven 'maven-3.9'; nodejs 'node-22' }
  environment {
    REGISTRY      = 'docker.io/<DOCKERHUB_USER>'
    MINIKUBE_HOST = '<MINIKUBE_HOST>'
  }
  stages {
    stage('Init') {
      steps { script { env.TAG = sh(script: 'git rev-parse --short=7 HEAD', returnStdout: true).trim() } }
    }
    stage('Test backend') {
      steps { dir('backend') { sh 'mvn -B -ntp verify' } }
      post  { always { junit allowEmptyResults: true, testResults: 'backend/**/target/surefire-reports/*.xml' } }
    }
    stage('Frontend lint + build') {
      steps { dir('frontend') { sh 'npm ci && npm run lint && npm run build' } }
    }
    stage('Build & push images') {
      when { branch 'main' }
      steps {
        withCredentials([usernamePassword(credentialsId: 'dockerhub', usernameVariable: 'DH_USER', passwordVariable: 'DH_TOKEN')]) {
          sh '''
            echo "$DH_TOKEN" | docker login -u "$DH_USER" --password-stdin
            bash infrastructure/ci/build-push.sh "$TAG"
          '''
        }
      }
      post { always { sh 'docker logout || true' } }
    }
    stage('Deploy Minikube') {
      when { branch 'main' }
      steps {
        sshagent(['minikube-ssh']) {
          sh 'tar cz -C infrastructure/helm datn-app | ssh -o BatchMode=yes deployer@$MINIKUBE_HOST "./deploy.sh $TAG"'
        }
      }
    }
  }
  post { always { cleanWs() } }
}
```

Mở rộng sau (không bắt buộc): `helm lint` + `helm template | kubeconform` ở stage CI để bắt lỗi chart trước khi deploy; stage `input` chờ duyệt trước khi deploy; chỉ build image cho service có thay đổi (`changeset`).

---

## 7. Cấu hình ứng dụng khi chạy trên Kubernetes

`application.yml` trong code đang trỏ `localhost`/cổng 808x. Trên k8s phải ghi đè, bằng **một trong hai** cách:
- **Profile `k8s`**: file `application-k8s.yaml` mỗi service đặt trong chart (ConfigMap mount vào `/config/`), chạy với `SPRING_PROFILES_ACTIVE=k8s` + `SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:/config/`. Bản cũ dùng cách này (dễ đọc).
- **Biến môi trường** (Spring relaxed binding): `SPRING_DATASOURCE_URL`, `SPRING_KAFKA_BOOTSTRAP_SERVERS`, ... Không cần file, nhưng danh sách dài khó đọc.

Các nhóm giá trị cần đặt:

| Nhóm | Service | Giá trị trên k8s |
|---|---|---|
| Datasource | identity, work, workspace, notification | `jdbc:postgresql://postgres-app:5432/datn_main_db`, mật khẩu từ Secret |
| JWT | cả 5 | `issuer-uri` = URL **công khai** `https://sso.<domain>/realms/datn-realm`; `jwk-set-uri` = **nội bộ** `http://keycloak:8080/realms/datn-realm/protocol/openid-connect/certs` |
| Keycloak admin | identity | URL nội bộ `http://keycloak:8080`, client secret `datn-backend` từ Secret |
| Kafka / Redis | work, workspace, notification | `kafka:9092`, `redis:6379` |
| MinIO | work | `http://minio:9000`, key từ Secret |
| Gọi chéo service | work, identity | `http://identity-service:8082`, `http://workspace-service:8087`, `http://notification-service:9994` |
| Route gateway | api-gateway | 4 route tới tên Service k8s (xem bẫy 2) |
| CORS / cookie | api-gateway, identity | `app.cors.allowed-origins=https://app.<domain>`, `app.cookie.secure=true` khi có HTTPS |
| Link trong email | identity, work, notification | `app.frontend-url=https://app.<domain>` |
| SMTP | notification | `MAIL_HOST/PORT/USERNAME/PASSWORD/...` (Mailpit hoặc SMTP thật) |
| Cụm | work, notification | `app.cluster.redis-enabled=true` |
| Probe | cả 5 | `management.endpoint.health.probes.enabled=true`, `server.shutdown=graceful` |

---

## 8. Bẫy đặc thù của dự án

1. **Issuer Keycloak**: token mang `iss` = URL mà trình duyệt dùng để login (công khai). Service phải khai `issuer-uri` công khai nhưng lấy khoá qua `jwk-set-uri` nội bộ; Keycloak đặt `KC_HOSTNAME` = URL công khai. Sai chỗ này → mọi API trả 401.
2. **Route gateway**: danh sách `routes` trong profile **thay thế toàn bộ** danh sách gốc → profile `k8s` phải khai đủ 4 route (identity, work kèm `/work/ws-work/**`, workspace, notification kèm `/notification/ws/**`). **Không** route `/notification/internal/**` (gửi email nội bộ, không xác thực).
3. **Thứ tự migration trên DB trống**: Flyway của workspace-service cần bảng `projects` do work-service tạo → workspace-service phải chờ work-service `ready` (initContainer gọi `/actuator/health/readiness` của work-service).
4. **Rolling update = có lúc 2 pod cùng chạy** → bật `app.cluster.redis-enabled=true` để job định kỳ (nhắc hạn 08:00, OutboxRelay) không chạy trùng và WebSocket đẩy qua Redis.
5. **Rollback image không rollback DB**: migration Flyway (work V1–V10, workspace V1–V6, notification V1–V2) chỉ chạy tiến. Sao lưu DB trước khi deploy bản có migration mới.
6. **Ingress**: `proxy-body-size: 50m` (upload đính kèm), `proxy-read-timeout/send-timeout: 3600` (STOMP WebSocket `/notification/ws`). Nginx ở host đứng trước Minikube cũng phải có `Upgrade`/`Connection` và cùng giới hạn.
7. **Realm Keycloak**: import `infrastructure/keycloak/realm-export_08082026.json`, sửa *Valid redirect URIs* / *Web origins* sang tên miền mới (và thêm redirect URI trong Google Console nếu dùng login Google). Client secret `datn-backend` trong Secret phải khớp realm.
8. **User id = Keycloak `sub`**: import realm mới thì `sub` của user đổi → không mang dữ liệu nghiệp vụ từ DB cũ sang realm mới mà không đối chiếu `users.keycloak_id`.
9. **Tài nguyên**: Keycloak bị OOMKilled ở limit 1 Gi (đã gặp) → để ≥ 1,25 Gi và giới hạn heap 50%. Mỗi service Spring ~400–700 Mi.
10. **CORS chỉ cho origin đã khai**: mở frontend bằng IP/cổng khác với `allowed-origins` → request ghi và WebSocket bị 403.
11. **Docker Hub**: luôn `docker login` trước khi build (image nền `maven`, `eclipse-temurin`, `node`, `nginx` cũng kéo từ Docker Hub) để tránh `toomanyrequests`.
12. **Line ending**: script `.sh`, Dockerfile, chart phải là LF (đã khai trong `.gitattributes`); thêm dòng cho `Jenkinsfile` nếu cần.

---

## 9. Tham khảo bản triển khai cũ trong git

Bản cũ (Helm + GitHub Actions + GHCR) đã xoá khỏi nhánh nhưng còn trong lịch sử git tại commit **`2e53884`** — dùng làm tài liệu tham khảo khi viết lại chart:

```bash
git show 2e53884 --stat                                                  # danh sách file
git show 2e53884:infrastructure/helm/datn-app/values.yaml                # xem một file
git show 2e53884:infrastructure/helm/datn-app/files/config/api-gateway/application-k8s.yaml
git restore --source=2e53884 -- infrastructure/helm                      # lấy lại cả thư mục chart
```

Khác biệt cần sửa nếu tái dùng: registry `ghcr.io/...` → `docker.io/<DOCKERHUB_USER>`; Secret pull `ghcr-pull` → `dockerhub-pull`; bỏ phần RBAC/kubeconfig dành cho GitHub Actions; script deploy chạy trên server Minikube thay vì qua workflow.
