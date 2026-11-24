# 03 — Hướng dẫn triển khai lên Minikube bằng Helm + Jenkins (từng bước)

> Lý do lựa chọn: [01](01_NGHIEN_CUU_CICD_KUBERNETES.md). Giai đoạn, ngân sách, nghiệm thu: [02](02_KE_HOACH_TRIEN_KHAI_CICD.md). Setup Jenkins: [04](04_HUONG_DAN_SETUP_JENKINS.md).
> Lệnh chạy trên **máy chủ Linux** (Ubuntu 22.04/24.04) bằng user quản trị `datnadmin`, trừ khi ghi rõ là chạy ở máy dev.
> **Các file ở mục 3–5, 6.1, 10, 12 và 14.1 đã có sẵn trong repo** (`backend/`, `frontend/Dockerfile`, `infrastructure/helm/`, `infrastructure/k8s/`, `infrastructure/scripts/`, `infrastructure/jenkins/`, `Jenkinsfile`). Khối code trong tài liệu là bản sao để đọc; sửa thì sửa file trong repo.

## Mục lục
0. [Quy ước](#0-quy-ước)
1. [Chuẩn bị máy chủ](#1-chuẩn-bị-máy-chủ)
2. [Khởi động minikube](#2-khởi-động-minikube)
3. [Sửa mã nguồn](#3-sửa-mã-nguồn-giai-đoạn-1)
4. [Helm chart `datn-platform`](#4-helm-chart-datn-platform-quản-trị-viên-cài)
5. [Helm chart `datn-app`](#5-helm-chart-datn-app-jenkins-cài)
6. [Tạo Secret và cài platform](#6-tạo-secret-và-cài-platform)
7. [Cài ứng dụng lần đầu](#7-cài-ứng-dụng-lần-đầu-bằng-tay)
8. [Nginx máy chủ và TLS](#8-nginx-máy-chủ-và-tls)
9. [Công cụ quản trị](#9-công-cụ-quản-trị-pgadmin-mailpit-minio-console-keycloak-admin-jenkins)
10. [Kubeconfig cho CI](#10-kubeconfig-cho-ci-serviceaccount-ci-deployer)
11. [Jenkins và GitHub](#11-jenkins-và-github)
12. [Pipeline Jenkins](#12-pipeline-jenkins)
13. [Kiểm thử nghiệm thu](#13-kiểm-thử-nghiệm-thu)
14. [Vận hành và xử lý sự cố](#14-vận-hành-và-xử-lý-sự-cố)

---

## 0. Quy ước

| Ký hiệu | Ý nghĩa |
|---|---|
| `example.com` | Tên miền của bạn. Viết xong file thì thay hàng loạt: `grep -rl example.com infrastructure Jenkinsfile \| xargs sed -i 's/example\.com/tenmien.vn/g'` |
| `hecker-chuoi` | Owner GitHub viết **chữ thường** (GHCR bắt buộc) |
| `datnadmin` | User quản trị máy chủ: chạy minikube, giữ kubeconfig admin, cài chart `datn-platform` |
| `jenkins` | User chạy Jenkins: không sudo, không nhóm docker, không kubeconfig admin ([04](04_HUONG_DAN_SETUP_JENKINS.md)) |
| `datn-staging`, `datn-prod` | Namespace |
| `datn-platform`, `datn-app` | Tên 2 release Helm trong mỗi namespace |

| Host | Đi tới | Ghi chú |
|---|---|---|
| `staging.example.com` / `app.example.com` | frontend + API | |
| `sso-staging.example.com` / `sso.example.com` | Keycloak | |
| `pgadmin-staging.example.com` / `pgadmin.example.com` | pgAdmin | basic auth + IP |
| `mail-staging.example.com` | Mailpit | basic auth + IP |
| `minio.example.com` | MinIO Console (tắt mặc định) | basic auth + IP |
| `jenkins.example.com` | Jenkins (ngoài cluster) | [04 §6](04_HUONG_DAN_SETUP_JENKINS.md#6-nginx-và-tls-cho-jenkins) |

---

## 1. Chuẩn bị máy chủ

```bash
sudo apt-get update && sudo apt-get install -y curl git jq nginx certbot python3-certbot-nginx conntrack apache2-utils

# Docker Engine (Docker gốc: chỉ datnadmin dùng để chạy minikube)
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker datnadmin && newgrp docker

# kubectl
curl -LO "https://dl.k8s.io/release/$(curl -Ls https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
sudo install -m 0755 kubectl /usr/local/bin/kubectl && rm kubectl

# minikube
curl -LO https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64
sudo install minikube-linux-amd64 /usr/local/bin/minikube && rm minikube-linux-amd64

# Helm: chọn bản ổn định mới nhất tại https://github.com/helm/helm/releases (Helm 4.x; Helm 3 cũng chạy được)
HELM_VERSION=v4.0.0            # <- thay bằng bản mới nhất
curl -fsSL "https://get.helm.sh/helm-${HELM_VERSION}-linux-amd64.tar.gz" | tar xz
sudo install -m 0755 linux-amd64/helm /usr/local/bin/helm && rm -rf linux-amd64
helm version

# Firewall
sudo ufw allow OpenSSH && sudo ufw allow 80/tcp && sudo ufw allow 443/tcp && sudo ufw enable

# Mã nguồn cho quản trị viên
sudo mkdir -p /opt/datn && sudo chown datnadmin: /opt/datn
git clone https://github.com/Hecker-Chuoi/DATN-2026.git /opt/datn
```

---

## 2. Khởi động minikube

```bash
# Máy 16 GB (staging + prod + Jenkins): 11g. Máy 32 GB: 14g. Chỉ prod trên máy 8 GB: --cpus=3 --memory=6g
minikube start --driver=docker --cni=calico --cpus=6 --memory=11g --disk-size=60g
minikube addons enable ingress
minikube addons enable metrics-server

# Tin X-Forwarded-* do Nginx máy chủ gửi: https cho gateway/Keycloak, IP thật cho whitelist
kubectl -n ingress-nginx patch configmap ingress-nginx-controller --type merge \
  -p '{"data":{"use-forwarded-headers":"true","proxy-body-size":"50m"}}'

kubectl get nodes && kubectl -n ingress-nginx get pods && minikube ip     # thường 192.168.49.2
```

`/etc/systemd/system/minikube.service` (tự khởi động cùng máy):

```ini
[Unit]
Description=Minikube cluster
After=docker.service
Requires=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
User=datnadmin
ExecStart=/usr/local/bin/minikube start
ExecStop=/usr/local/bin/minikube stop
TimeoutStartSec=600

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload && sudo systemctl enable minikube
```

> **Không chạy `minikube delete`** ở máy chủ thật: PVC (Postgres, MinIO, Kafka, pgAdmin) nằm trong container minikube.

---

## 3. Sửa mã nguồn (Giai đoạn 1)

Làm ở máy dev, trên PR riêng (ví dụ `feat/k8s-ready`).

### 3.1. Actuator cho 4 service
Thêm vào `pom.xml` của `identity-service`, `work-service`, `workspace-service`, `notification-service`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

### 3.2. Mở endpoint health trong Security

| File | Sửa |
|---|---|
| `backend/api-gateway/src/main/java/com/datn/api_gateway/security/SecurityConfig.java` | `PUBLIC_ENDPOINTS`: thay `"/actuator/health"` bằng `"/actuator/health", "/actuator/health/**", "/*/actuator/health", "/*/actuator/health/**"`. Hai mẫu sau cho smoke test gọi `/identity/actuator/health`… qua gateway (hiện đang trả 401) |
| `backend/identity-service/.../config/SecurityConfig.java` | Thêm `"/actuator/health/**"` |
| `backend/notification-service/.../config/SecurityConfig.java` | `.requestMatchers("/internal/**", "/ws/**", "/actuator/health", "/actuator/health/**", "/error").permitAll()` |

work-service và workspace-service đã `permitAll` cho `/actuator/**`.

### 3.3. CORS và cookie cấu hình được

`api-gateway/.../config/CorsConfig.java`:

```java
@Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
private List<String> allowedOrigins;
// trong corsWebFilter():  corsConfig.setAllowedOrigins(allowedOrigins);
```

`identity-service/.../controller/AuthController.java`:

```java
@Value("${app.cookie.secure:false}")
private boolean cookieSecure;
// buildAccessTokenCookie() và buildClearCookie():  .secure(false)  ->  .secure(cookieSecure)
```

### 3.4. `backend/.dockerignore` (mới)

```
# Build context của mọi Dockerfile backend là thư mục backend/
**/target
**/.env
**/.env.*
**/uploads
**/*.iml
**/.idea
**/.mvn
**/mvnw
**/mvnw.cmd
```

### 3.5. Dockerfile backend (mẫu work-service)

```dockerfile
# syntax=docker/dockerfile:1
# Stage 1: Build (build context = backend/, xem infrastructure/scripts/build-images.sh)
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app
# Build trên máy chủ dùng chung với cluster: giới hạn heap Maven
ENV MAVEN_OPTS="-Xmx768m"

# Parent pom liệt kê mọi module nên phải COPY đủ 6 pom
COPY pom.xml ./
COPY common/pom.xml ./common/
COPY identity-service/pom.xml ./identity-service/
COPY api-gateway/pom.xml ./api-gateway/
COPY work-service/pom.xml ./work-service/
COPY workspace-service/pom.xml ./workspace-service/
COPY notification-service/pom.xml ./notification-service/

# Source của common và work-service
COPY common/src ./common/src
COPY work-service/src ./work-service/src

# Cache ~/.m2 giữa các lần build (BuildKit)
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp clean package -DskipTests -pl work-service -am

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app
WORKDIR /app
COPY --from=builder /app/work-service/target/*.jar app.jar
USER 10001
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Bốn service còn lại chỉ đổi tên module (3 chỗ) và `EXPOSE`: api-gateway 8081, identity-service 8082, **workspace-service 8087 (tạo mới)**, notification-service 9994.

### 3.6. `frontend/Dockerfile`

```dockerfile
# Stage 1: Build React application (Vite 8 cần Node ^20.19 hoặc >=22.12)
FROM node:22-alpine AS builder
WORKDIR /app

# Cài đúng phiên bản trong package-lock.json (lỗi thì fail, không âm thầm npm install)
COPY package.json package-lock.json ./
RUN npm ci

# Copy source code and build
COPY . .
RUN npm run build

# Stage 2: Serve using Nginx
FROM nginx:1.27-alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
```

### 3.7. Kiểm tra ở máy dev
```bash
cd infrastructure && docker compose -f docker-compose.tuananh.yml --profile app up -d --build
curl -s http://localhost:9992/actuator/health/readiness      # {"status":"UP"}
```

---

## 4. Helm chart `datn-platform` (quản trị viên cài)

Gồm: RBAC cho CI, SA ứng dụng, quota, NetworkPolicy, Postgres ×2, Keycloak, Kafka, Redis, MinIO, Mailpit, pgAdmin và Ingress của công cụ quản trị.

```
infrastructure/helm/datn-platform/
  Chart.yaml  values.yaml  values-staging.yaml  values-prod.yaml
  templates/_helpers.tpl rbac-ci.yaml serviceaccount-app.yaml quota.yaml networkpolicy.yaml
            postgres.yaml keycloak.yaml kafka.yaml redis.yaml minio.yaml mailpit.yaml pgadmin.yaml
```

### 4.1. `Chart.yaml`

```yaml
apiVersion: v2
name: datn-platform
description: Hạ tầng của DATN-2026 trong 1 namespace (DB, Keycloak, Kafka, Redis, MinIO, công cụ quản trị, RBAC CI)
type: application
version: 0.1.0
appVersion: "1.0.0"
```

### 4.2. `values.yaml`

```yaml
# Giá trị mặc định. Mỗi môi trường ghi đè bằng values-staging.yaml / values-prod.yaml.
# Quản trị viên cài chart này (driver Secret mặc định của Helm); Jenkins KHÔNG có quyền với release này.

secretName: app-secrets            # quản trị viên tạo (03 §6.1)
pullSecretName: ghcr-pull          # quản trị viên tạo (03 §6.3)
ingressClassName: nginx

# Ingress công cụ quản trị (pgAdmin, Mailpit, MinIO Console)
adminAccess:
  basicAuthSecret: admin-basic-auth   # 03 §6.4, key bắt buộc tên "auth"
  allowCidrs: ""                      # ví dụ "203.0.113.0/24,198.51.100.7/32"; rỗng = không giới hạn IP

# Quota là TRẦN, không giữ chỗ. Tính cho lúc rolling update datn-app (mỗi Deployment thêm 1 pod, maxSurge 1):
#   prod    requests ≈ 2.0 core / 4.9 GiB lúc thường, ≈ 3.1 core / 7.0 GiB lúc deploy
#           limits   ≈ 12.1 core / 9.3 GiB lúc thường, ≈ 17.8 core / 13.0 GiB lúc deploy
#   staging nhỏ hơn prod một chút (1 replica gateway/frontend, thêm Mailpit)
quota:
  enabled: true
  hard:
    requests.cpu: "4"
    requests.memory: 8Gi
    limits.cpu: "24"
    limits.memory: 16Gi
    pods: "30"
    persistentvolumeclaims: "8"
    services.nodeports: "0"           # bắt buộc đi qua Ingress
    services.loadbalancers: "0"

networkPolicy:
  enabled: true
  # Pod mà ingress-nginx được gọi vào (theo nhãn app.kubernetes.io/name)
  ingressAllowed: [api-gateway, frontend, keycloak, pgadmin, mailpit, minio]

postgres:
  image: postgres:15-alpine
  instances:
    postgres-app:
      database: datn_main_db
      user: postgres
      passwordKey: DB_PASSWORD
      storage: 5Gi                    # volumeClaimTemplates không sửa được sau khi tạo
      resources:
        requests: { cpu: 100m, memory: 256Mi }
        limits: { cpu: "1", memory: 1Gi }
    postgres-keycloak:
      database: keycloak
      user: kc_user
      passwordKey: KC_DB_PASSWORD
      storage: 2Gi
      resources:
        requests: { cpu: 50m, memory: 128Mi }
        limits: { cpu: 500m, memory: 512Mi }

keycloak:
  image: quay.io/keycloak/keycloak:24.0.1     # cùng bản với docker-compose của nhóm
  host: sso.example.invalid
  hostnameUrl: http://sso.example.invalid     # URL công khai cố định -> claim iss của token
  realmSecret: keycloak-realm                 # 03 §6.2, key realm.json
  adminAllowCidrs: ""                         # giới hạn IP cho /admin (Admin Console); rỗng = không tách Ingress
  # Mặc định kc.sh dùng 70% RAM cho heap; cộng metaspace (tối đa 256m) và bộ nhớ native thì bước tự build
  # lúc "start" (không --optimized) bị OOMKilled ở limit 1Gi (đã gặp khi chạy thử). 50% của 1280Mi = 640m heap.
  heapOpts: "-XX:MaxRAMPercentage=50 -XX:InitialRAMPercentage=25 -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=20"
  resources:
    requests: { cpu: 250m, memory: 768Mi }
    limits: { cpu: 1500m, memory: 1280Mi }

kafka:
  image: apache/kafka:3.8.0
  heapOpts: "-Xms256m -Xmx512m"
  storage: 5Gi
  resources:
    requests: { cpu: 200m, memory: 768Mi }
    limits: { cpu: "1", memory: 1Gi }

redis:
  image: redis:7-alpine
  resources:
    requests: { cpu: 20m, memory: 64Mi }
    limits: { cpu: 200m, memory: 256Mi }

minio:
  # minio/minio đã bị gỡ khỏi Docker Hub. Chainguard bản miễn phí chỉ có tag latest (entrypoint /usr/bin/minio, uid 65532)
  image: cgr.dev/chainguard/minio:latest
  storage: 10Gi
  resources:
    requests: { cpu: 50m, memory: 256Mi }
    limits: { cpu: 500m, memory: 512Mi }
  console:
    ingress: false                           # bật khi cần xem file đính kèm
    host: minio.example.invalid

mailpit:
  enabled: false
  image: axllent/mailpit:v1.34.0
  host: mail.example.invalid
  resources:
    requests: { cpu: 10m, memory: 32Mi }
    limits: { cpu: 100m, memory: 128Mi }

pgadmin:
  enabled: true
  image: dpage/pgadmin4:9.19
  host: pgadmin.example.invalid
  storage: 1Gi
  resources:
    requests: { cpu: 50m, memory: 192Mi }
    limits: { cpu: 500m, memory: 512Mi }
```

### 4.3. `values-staging.yaml` và `values-prod.yaml`

```yaml
# helm upgrade --install datn-platform infrastructure/helm/datn-platform -n datn-staging -f values-staging.yaml
keycloak:
  host: sso-staging.example.com
  hostnameUrl: https://sso-staging.example.com
  adminAllowCidrs: ""            # điền IP nhóm, ví dụ "203.0.113.7/32"
mailpit:
  enabled: true
  host: mail-staging.example.com
pgadmin:
  host: pgadmin-staging.example.com
adminAccess:
  allowCidrs: ""                 # điền IP nhóm
```

```yaml
# helm upgrade --install datn-platform infrastructure/helm/datn-platform -n datn-prod -f values-prod.yaml
keycloak:
  host: sso.example.com
  hostnameUrl: https://sso.example.com
  adminAllowCidrs: ""            # điền IP nhóm
postgres:
  instances:
    postgres-app:
      storage: 10Gi
pgadmin:
  host: pgadmin.example.com
minio:
  console:
    host: minio.example.com
adminAccess:
  allowCidrs: ""                 # điền IP nhóm
```

### 4.4. `templates/_helpers.tpl`

```yaml
{{- define "platform.labels" -}}
app.kubernetes.io/part-of: datn
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
datn/layer: platform
{{- end }}

{{/* Nhãn cho pod template: KHÔNG có phiên bản chart, để nâng chart không làm restart DB */}}
{{- define "platform.podLabels" -}}
app.kubernetes.io/part-of: datn
datn/layer: platform
{{- end }}

{{/* Áp cho mọi pod của platform */}}
{{- define "platform.podDefaults" -}}
enableServiceLinks: false            # BẮT BUỘC cho Kafka (01 §5.3): không có biến KAFKA_PORT=tcp://... do Service "kafka" sinh ra
automountServiceAccountToken: false
{{- end }}

{{/* Ingress công cụ quản trị: basic auth + (tuỳ chọn) giới hạn IP */}}
{{- define "platform.adminIngressAnnotations" -}}
nginx.ingress.kubernetes.io/auth-type: basic
nginx.ingress.kubernetes.io/auth-secret: {{ .Values.adminAccess.basicAuthSecret }}
nginx.ingress.kubernetes.io/auth-realm: "DATN - khu vuc quan tri"
{{- with .Values.adminAccess.allowCidrs }}
nginx.ingress.kubernetes.io/whitelist-source-range: {{ . | quote }}
{{- end }}
{{- end }}
```

> Bản ingress-nginx mới dùng tên annotation `allowlist-source-range` song song với `whitelist-source-range`. Nếu giới hạn IP không có tác dụng, kiểm tra phiên bản controller (`kubectl -n ingress-nginx get deploy -o wide`) rồi đổi tên annotation ở đây.

### 4.5. `templates/rbac-ci.yaml`: quyền của Jenkins (M7)

```yaml
# Quyền của Jenkins (M7): ServiceAccount ci-deployer CHỈ thao tác trong namespace của release này.
# Kubeconfig cho Jenkins: infrastructure/scripts/make-ci-kubeconfig.sh
apiVersion: v1
kind: ServiceAccount
metadata:
  name: ci-deployer
  labels:
    {{- include "platform.labels" . | nindent 4 }}
automountServiceAccountToken: false
---
# Token dài hạn cho kubeconfig của CI (Kubernetes >= 1.24 không tự tạo nữa)
apiVersion: v1
kind: Secret
metadata:
  name: ci-deployer-token
  labels:
    {{- include "platform.labels" . | nindent 4 }}
  annotations:
    kubernetes.io/service-account.name: ci-deployer
type: kubernetes.io/service-account-token
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: ci-deployer
  labels:
    {{- include "platform.labels" . | nindent 4 }}
rules:
  # Tài nguyên của chart datn-app. Helm cần delete khi gỡ tài nguyên khỏi chart và khi rollback.
  # configmaps còn dùng để lưu release (HELM_DRIVER=configmap).
  - apiGroups: ["apps"]
    resources: ["deployments"]
    verbs: ["get", "list", "watch", "create", "update", "patch", "delete"]
  - apiGroups: [""]
    resources: ["services", "configmaps"]
    verbs: ["get", "list", "watch", "create", "update", "patch", "delete"]
  - apiGroups: ["networking.k8s.io"]
    resources: ["ingresses"]
    verbs: ["get", "list", "watch", "create", "update", "patch", "delete"]
  # helm --wait / kubectl rollout status / chẩn đoán
  - apiGroups: ["apps"]
    resources: ["replicasets"]
    verbs: ["get", "list", "watch"]
  - apiGroups: [""]
    resources: ["pods", "pods/log", "events", "endpoints"]
    verbs: ["get", "list", "watch"]
  # CỐ Ý KHÔNG có: secrets, statefulsets, persistentvolumeclaims, roles, rolebindings, serviceaccounts
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: ci-deployer
  labels:
    {{- include "platform.labels" . | nindent 4 }}
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: ci-deployer
subjects:
  - kind: ServiceAccount
    name: ci-deployer
    namespace: {{ .Release.Namespace }}
```

### 4.6. `templates/serviceaccount-app.yaml`

```yaml
# SA cho pod của chart datn-app: kéo image private từ GHCR, không cầm token API
apiVersion: v1
kind: ServiceAccount
metadata:
  name: datn-app
  labels:
    {{- include "platform.labels" . | nindent 4 }}
automountServiceAccountToken: false
imagePullSecrets:
  - name: {{ .Values.pullSecretName }}
```

### 4.7. `templates/quota.yaml` (M3)

```yaml
{{- if .Values.quota.enabled }}
# M3: trần tài nguyên của cả namespace + giá trị mặc định cho container không khai resources
apiVersion: v1
kind: ResourceQuota
metadata:
  name: datn-quota
  labels:
    {{- include "platform.labels" . | nindent 4 }}
spec:
  hard:
    {{- toYaml .Values.quota.hard | nindent 4 }}
---
apiVersion: v1
kind: LimitRange
metadata:
  name: datn-defaults
  labels:
    {{- include "platform.labels" . | nindent 4 }}
spec:
  limits:
    - type: Container
      defaultRequest: { cpu: 50m, memory: 64Mi }
      default: { cpu: 500m, memory: 256Mi }
      max: { cpu: "2", memory: 2Gi }
{{- end }}
```

### 4.8. `templates/networkpolicy.yaml` (M9, cần `--cni=calico`)

```yaml
{{- if .Values.networkPolicy.enabled }}
# M9, cần minikube --cni=calico (CNI mặc định của minikube không thực thi NetworkPolicy)
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: default-deny-ingress
  labels:
    {{- include "platform.labels" . | nindent 4 }}
spec:
  podSelector: {}
  policyTypes: ["Ingress"]
---
# Pod cùng namespace gọi nhau (identity -> notification /internal/**, pgadmin -> postgres...)
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-same-namespace
  labels:
    {{- include "platform.labels" . | nindent 4 }}
spec:
  podSelector: {}
  policyTypes: ["Ingress"]
  ingress:
    - from:
        - podSelector: {}
---
# ingress-nginx chỉ vào được các cửa công khai
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-from-ingress-controller
  labels:
    {{- include "platform.labels" . | nindent 4 }}
spec:
  podSelector:
    matchExpressions:
      - key: app.kubernetes.io/name
        operator: In
        values: {{ toJson .Values.networkPolicy.ingressAllowed }}
  policyTypes: ["Ingress"]
  ingress:
    - from:
        - namespaceSelector:
            matchLabels:
              kubernetes.io/metadata.name: ingress-nginx
{{- end }}
```

### 4.9. `templates/postgres.yaml` (một template cho 2 instance)

```yaml
{{- /* Một template cho 2 instance: postgres-app (datn_main_db) và postgres-keycloak */ -}}
{{- range $name, $pg := .Values.postgres.instances }}
---
apiVersion: v1
kind: Service
metadata:
  name: {{ $name }}
  labels:
    {{- include "platform.labels" $ | nindent 4 }}
    app.kubernetes.io/name: {{ $name }}
spec:
  selector:
    app.kubernetes.io/name: {{ $name }}
  ports:
    - name: pg
      port: 5432
      targetPort: 5432
---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: {{ $name }}
  labels:
    {{- include "platform.labels" $ | nindent 4 }}
    app.kubernetes.io/name: {{ $name }}
spec:
  serviceName: {{ $name }}
  replicas: 1
  selector:
    matchLabels:
      app.kubernetes.io/name: {{ $name }}
  template:
    metadata:
      labels:
        {{- include "platform.podLabels" $ | nindent 8 }}
        app.kubernetes.io/name: {{ $name }}
    spec:
      {{- include "platform.podDefaults" $ | nindent 6 }}
      terminationGracePeriodSeconds: 60
      containers:
        - name: postgres
          image: {{ $.Values.postgres.image }}
          ports:
            - name: pg
              containerPort: 5432
          env:
            - name: POSTGRES_DB
              value: {{ $pg.database | quote }}
            - name: POSTGRES_USER
              value: {{ $pg.user | quote }}
            - name: POSTGRES_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: {{ $.Values.secretName }}
                  key: {{ $pg.passwordKey }}
            # Thư mục con: tránh lỗi initdb khi gốc volume có sẵn lost+found
            - name: PGDATA
              value: /var/lib/postgresql/data/pgdata
          readinessProbe:
            exec:
              command: ["pg_isready", "-U", {{ $pg.user | quote }}, "-d", {{ $pg.database | quote }}]
            periodSeconds: 10
          livenessProbe:
            exec:
              command: ["pg_isready", "-U", {{ $pg.user | quote }}]
            initialDelaySeconds: 30
            periodSeconds: 20
          resources:
            {{- toYaml $pg.resources | nindent 12 }}
          volumeMounts:
            - name: data
              mountPath: /var/lib/postgresql/data
  volumeClaimTemplates:
    - metadata:
        name: data
      spec:
        accessModes: ["ReadWriteOnce"]
        resources:
          requests:
            storage: {{ $pg.storage }}
{{- end }}
```

### 4.10. `templates/keycloak.yaml`

```yaml
apiVersion: v1
kind: Service
metadata:
  name: keycloak
  labels:
    {{- include "platform.labels" . | nindent 4 }}
    app.kubernetes.io/name: keycloak
spec:
  selector:
    app.kubernetes.io/name: keycloak
  ports:
    - name: http
      port: 8080
      targetPort: http
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: keycloak
  labels:
    {{- include "platform.labels" . | nindent 4 }}
    app.kubernetes.io/name: keycloak
spec:
  replicas: 1
  strategy:
    type: Recreate                 # 1 bản, cache local
  selector:
    matchLabels:
      app.kubernetes.io/name: keycloak
  template:
    metadata:
      labels:
        {{- include "platform.podLabels" . | nindent 8 }}
        app.kubernetes.io/name: keycloak
    spec:
      {{- include "platform.podDefaults" . | nindent 6 }}
      initContainers:
        - name: wait-db
          image: busybox:1.36
          command: ["sh", "-c", "until nc -z -w 2 postgres-keycloak 5432; do echo 'chờ postgres-keycloak'; sleep 3; done"]
          resources:
            requests: { cpu: 10m, memory: 16Mi }
            limits: { cpu: 100m, memory: 64Mi }
      containers:
        - name: keycloak
          image: {{ .Values.keycloak.image }}
          args: ["start", "--import-realm"]          # M5: Command/Args
          ports:
            - name: http
              containerPort: 8080
          env:
            - { name: KC_DB, value: postgres }
            - { name: KC_DB_URL, value: "jdbc:postgresql://postgres-keycloak:5432/{{ (index .Values.postgres.instances "postgres-keycloak").database }}" }
            - { name: KC_DB_USERNAME, value: {{ (index .Values.postgres.instances "postgres-keycloak").user | quote }} }
            - name: KC_DB_PASSWORD
              valueFrom: { secretKeyRef: { name: {{ .Values.secretName }}, key: KC_DB_PASSWORD } }
            - { name: KEYCLOAK_ADMIN, value: admin }   # identity-service dùng keycloak.admin.username=admin
            - name: KEYCLOAK_ADMIN_PASSWORD
              valueFrom: { secretKeyRef: { name: {{ .Values.secretName }}, key: KC_ADMIN_PASSWORD } }
            # URL công khai cố định: token lấy qua http://keycloak:8080 (nội bộ) vẫn có iss = URL công khai
            - { name: KC_HOSTNAME_URL, value: {{ .Values.keycloak.hostnameUrl | quote }} }
            - { name: KC_HOSTNAME_STRICT, value: "false" }
            - { name: KC_HTTP_ENABLED, value: "true" }       # TLS kết thúc ở Nginx máy chủ
            - { name: KC_PROXY_HEADERS, value: xforwarded }
            - { name: KC_HEALTH_ENABLED, value: "true" }
            - { name: KC_CACHE, value: local }
            - { name: JAVA_OPTS_KC_HEAP, value: {{ .Values.keycloak.heapOpts | quote }} }
          startupProbe:                              # lần đầu: tự build + import realm, cho 5 phút
            httpGet: { path: /health/ready, port: http }
            periodSeconds: 5
            failureThreshold: 60
          readinessProbe:
            httpGet: { path: /health/ready, port: http }
            periodSeconds: 10
          livenessProbe:
            httpGet: { path: /health/live, port: http }
            periodSeconds: 20
          resources:
            {{- toYaml .Values.keycloak.resources | nindent 12 }}
          volumeMounts:
            # --import-realm đọc mọi *.json trong thư mục này, chỉ import khi realm CHƯA tồn tại
            - name: realm
              mountPath: /opt/keycloak/data/import
              readOnly: true
      volumes:
        - name: realm
          secret:
            secretName: {{ .Values.keycloak.realmSecret }}
---
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: keycloak
  labels:
    {{- include "platform.labels" . | nindent 4 }}
  annotations:
    nginx.ingress.kubernetes.io/proxy-buffer-size: "128k"
spec:
  ingressClassName: {{ .Values.ingressClassName }}
  rules:
    - host: {{ .Values.keycloak.host | quote }}
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: keycloak
                port:
                  number: 8080
{{- with .Values.keycloak.adminAllowCidrs }}
---
# Admin Console: CHỈ giới hạn IP. Không dùng basic auth vì console gọi API bằng header Authorization: Bearer (01 §5.17)
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: keycloak-admin
  labels:
    {{- include "platform.labels" $ | nindent 4 }}
  annotations:
    nginx.ingress.kubernetes.io/proxy-buffer-size: "128k"
    nginx.ingress.kubernetes.io/whitelist-source-range: {{ . | quote }}
spec:
  ingressClassName: {{ $.Values.ingressClassName }}
  rules:
    - host: {{ $.Values.keycloak.host | quote }}
      http:
        paths:
          - path: /admin
            pathType: Prefix
            backend:
              service:
                name: keycloak
                port:
                  number: 8080
{{- end }}
```

> `--import-realm` chỉ import khi realm **chưa tồn tại**. Sửa realm về sau thì làm trong Admin Console. identity-service gọi Admin API qua `http://keycloak:8080` (nội bộ) nên không bị ảnh hưởng bởi giới hạn IP.

### 4.11. `templates/kafka.yaml`

```yaml
# Kafka KRaft 1 node (broker + controller), cùng cấu hình docker-compose của nhóm nhưng chỉ 1 listener nội bộ
apiVersion: v1
kind: Service
metadata:
  name: kafka
  labels:
    {{- include "platform.labels" . | nindent 4 }}
    app.kubernetes.io/name: kafka
spec:
  selector:
    app.kubernetes.io/name: kafka
  ports:
    - name: broker
      port: 9092
      targetPort: 9092
---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: kafka
  labels:
    {{- include "platform.labels" . | nindent 4 }}
    app.kubernetes.io/name: kafka
spec:
  serviceName: kafka
  replicas: 1
  selector:
    matchLabels:
      app.kubernetes.io/name: kafka
  template:
    metadata:
      labels:
        {{- include "platform.podLabels" . | nindent 8 }}
        app.kubernetes.io/name: kafka
    spec:
      {{- include "platform.podDefaults" . | nindent 6 }}
      securityContext:
        fsGroup: 1000                       # appuser của image apache/kafka
      containers:
        - name: kafka
          image: {{ .Values.kafka.image }}
          ports:
            - name: broker
              containerPort: 9092
          env:
            - { name: KAFKA_NODE_ID, value: "1" }
            - { name: KAFKA_PROCESS_ROLES, value: "broker,controller" }
            - { name: KAFKA_LISTENERS, value: "PLAINTEXT://:9092,CONTROLLER://:9093" }
            - { name: KAFKA_ADVERTISED_LISTENERS, value: "PLAINTEXT://kafka:9092" }
            - { name: KAFKA_CONTROLLER_LISTENER_NAMES, value: CONTROLLER }
            - { name: KAFKA_LISTENER_SECURITY_PROTOCOL_MAP, value: "CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT" }
            - { name: KAFKA_CONTROLLER_QUORUM_VOTERS, value: "1@localhost:9093" }
            - { name: KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR, value: "1" }
            - { name: KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR, value: "1" }
            - { name: KAFKA_TRANSACTION_STATE_LOG_MIN_ISR, value: "1" }
            - { name: KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS, value: "0" }
            - { name: KAFKA_NUM_PARTITIONS, value: "3" }
            - { name: KAFKA_LOG_DIRS, value: /var/lib/kafka/data }
            - { name: KAFKA_HEAP_OPTS, value: {{ .Values.kafka.heapOpts | quote }} }
          readinessProbe:
            tcpSocket: { port: 9092 }
            initialDelaySeconds: 15
            periodSeconds: 10
          livenessProbe:
            tcpSocket: { port: 9092 }
            initialDelaySeconds: 60
            periodSeconds: 20
          resources:
            {{- toYaml .Values.kafka.resources | nindent 12 }}
          volumeMounts:
            - name: data
              mountPath: /var/lib/kafka/data
  volumeClaimTemplates:
    - metadata:
        name: data
      spec:
        accessModes: ["ReadWriteOnce"]
        resources:
          requests:
            storage: {{ .Values.kafka.storage }}
```

### 4.12. `templates/redis.yaml`

```yaml
apiVersion: v1
kind: Service
metadata:
  name: redis
  labels:
    {{- include "platform.labels" . | nindent 4 }}
    app.kubernetes.io/name: redis
spec:
  selector:
    app.kubernetes.io/name: redis
  ports:
    - name: redis
      port: 6379
      targetPort: 6379
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: redis
  labels:
    {{- include "platform.labels" . | nindent 4 }}
    app.kubernetes.io/name: redis
spec:
  replicas: 1
  selector:
    matchLabels:
      app.kubernetes.io/name: redis
  template:
    metadata:
      labels:
        {{- include "platform.podLabels" . | nindent 8 }}
        app.kubernetes.io/name: redis
    spec:
      {{- include "platform.podDefaults" . | nindent 6 }}
      containers:
        - name: redis
          image: {{ .Values.redis.image }}
          args: ["redis-server", "--save", "", "--appendonly", "no"]   # chỉ cache và khoá, không cần lưu đĩa
          ports:
            - name: redis
              containerPort: 6379
          readinessProbe:
            exec: { command: ["redis-cli", "ping"] }
            periodSeconds: 10
          livenessProbe:
            tcpSocket: { port: 6379 }
            periodSeconds: 20
          resources:
            {{- toYaml .Values.redis.resources | nindent 12 }}
```

### 4.13. `templates/minio.yaml`

```yaml
apiVersion: v1
kind: Service
metadata:
  name: minio
  labels:
    {{- include "platform.labels" . | nindent 4 }}
    app.kubernetes.io/name: minio
spec:
  selector:
    app.kubernetes.io/name: minio
  ports:
    - { name: s3, port: 9000, targetPort: 9000 }
    - { name: console, port: 9001, targetPort: 9001 }
---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: minio
  labels:
    {{- include "platform.labels" . | nindent 4 }}
    app.kubernetes.io/name: minio
spec:
  serviceName: minio
  replicas: 1
  selector:
    matchLabels:
      app.kubernetes.io/name: minio
  template:
    metadata:
      labels:
        {{- include "platform.podLabels" . | nindent 8 }}
        app.kubernetes.io/name: minio
    spec:
      {{- include "platform.podDefaults" . | nindent 6 }}
      securityContext:
        fsGroup: 65532                      # image Chainguard chạy uid 65532
      containers:
        - name: minio
          image: {{ .Values.minio.image }}
          args: ["server", "/data", "--console-address", ":9001"]
          ports:
            - { name: s3, containerPort: 9000 }
            - { name: console, containerPort: 9001 }
          env:
            - name: MINIO_ROOT_USER
              valueFrom: { secretKeyRef: { name: {{ .Values.secretName }}, key: MINIO_ROOT_USER } }
            - name: MINIO_ROOT_PASSWORD
              valueFrom: { secretKeyRef: { name: {{ .Values.secretName }}, key: MINIO_ROOT_PASSWORD } }
          readinessProbe:
            httpGet: { path: /minio/health/ready, port: s3 }
            periodSeconds: 10
          livenessProbe:
            httpGet: { path: /minio/health/live, port: s3 }
            periodSeconds: 20
          resources:
            {{- toYaml .Values.minio.resources | nindent 12 }}
          volumeMounts:
            - name: data
              mountPath: /data
  volumeClaimTemplates:
    - metadata:
        name: data
      spec:
        accessModes: ["ReadWriteOnce"]
        resources:
          requests:
            storage: {{ .Values.minio.storage }}
{{- if .Values.minio.console.ingress }}
---
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: minio-console
  labels:
    {{- include "platform.labels" . | nindent 4 }}
  annotations:
    {{- include "platform.adminIngressAnnotations" . | nindent 4 }}
spec:
  ingressClassName: {{ .Values.ingressClassName }}
  rules:
    - host: {{ .Values.minio.console.host | quote }}
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: minio
                port:
                  number: 9001
{{- end }}
```

Bucket `work-attachments` do `work-service` tự tạo khi khởi động.

### 4.14. `templates/mailpit.yaml`

```yaml
{{- if .Values.mailpit.enabled }}
# Hộp thư giả cho staging: notification-service gửi SMTP tới mailpit:1025, xem thư qua Ingress
apiVersion: v1
kind: Service
metadata:
  name: mailpit
  labels:
    {{- include "platform.labels" . | nindent 4 }}
    app.kubernetes.io/name: mailpit
spec:
  selector:
    app.kubernetes.io/name: mailpit
  ports:
    - { name: smtp, port: 1025, targetPort: 1025 }
    - { name: http, port: 8025, targetPort: 8025 }
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: mailpit
  labels:
    {{- include "platform.labels" . | nindent 4 }}
    app.kubernetes.io/name: mailpit
spec:
  replicas: 1
  selector:
    matchLabels:
      app.kubernetes.io/name: mailpit
  template:
    metadata:
      labels:
        {{- include "platform.podLabels" . | nindent 8 }}
        app.kubernetes.io/name: mailpit
    spec:
      {{- include "platform.podDefaults" . | nindent 6 }}
      containers:
        - name: mailpit
          image: {{ .Values.mailpit.image }}
          ports:
            - { name: smtp, containerPort: 1025 }
            - { name: http, containerPort: 8025 }
          readinessProbe:
            tcpSocket: { port: 8025 }
            periodSeconds: 10
          livenessProbe:
            tcpSocket: { port: 1025 }
            periodSeconds: 30
          resources:
            {{- toYaml .Values.mailpit.resources | nindent 12 }}
---
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: mailpit
  labels:
    {{- include "platform.labels" . | nindent 4 }}
  annotations:
    {{- include "platform.adminIngressAnnotations" . | nindent 4 }}
spec:
  ingressClassName: {{ .Values.ingressClassName }}
  rules:
    - host: {{ .Values.mailpit.host | quote }}
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: mailpit
                port:
                  number: 8025
{{- end }}
```

### 4.15. `templates/pgadmin.yaml`: pgAdmin chạy thành Pod, mở ra qua Ingress

```yaml
{{- if .Values.pgadmin.enabled }}
{{- $app := index .Values.postgres.instances "postgres-app" }}
{{- $kc := index .Values.postgres.instances "postgres-keycloak" }}
# Khai sẵn 2 server DB của CHÍNH namespace này (pgAdmin chỉ nạp ở lần khởi động đầu tiên, khi PVC còn trống)
apiVersion: v1
kind: ConfigMap
metadata:
  name: pgadmin-servers
  labels:
    {{- include "platform.labels" . | nindent 4 }}
data:
  servers.json: |
    {
      "Servers": {
        "1": {
          "Name": "datn_main_db ({{ .Release.Namespace }})",
          "Group": "DATN",
          "Host": "postgres-app",
          "Port": 5432,
          "MaintenanceDB": "{{ $app.database }}",
          "Username": "{{ $app.user }}",
          "SSLMode": "prefer"
        },
        "2": {
          "Name": "keycloak ({{ .Release.Namespace }})",
          "Group": "DATN",
          "Host": "postgres-keycloak",
          "Port": 5432,
          "MaintenanceDB": "{{ $kc.database }}",
          "Username": "{{ $kc.user }}",
          "SSLMode": "prefer"
        }
      }
    }
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: pgadmin-data
  labels:
    {{- include "platform.labels" . | nindent 4 }}
  annotations:
    helm.sh/resource-policy: keep           # helm uninstall không xoá cấu hình pgAdmin
spec:
  accessModes: ["ReadWriteOnce"]
  resources:
    requests:
      storage: {{ .Values.pgadmin.storage }}
---
apiVersion: v1
kind: Service
metadata:
  name: pgadmin
  labels:
    {{- include "platform.labels" . | nindent 4 }}
    app.kubernetes.io/name: pgadmin
spec:
  selector:
    app.kubernetes.io/name: pgadmin
  ports:
    - name: http
      port: 80
      targetPort: http
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: pgadmin
  labels:
    {{- include "platform.labels" . | nindent 4 }}
    app.kubernetes.io/name: pgadmin
spec:
  replicas: 1
  strategy:
    type: Recreate                          # PVC ReadWriteOnce
  selector:
    matchLabels:
      app.kubernetes.io/name: pgadmin
  template:
    metadata:
      labels:
        {{- include "platform.podLabels" . | nindent 8 }}
        app.kubernetes.io/name: pgadmin
      annotations:
        checksum/servers: {{ .Values.postgres.instances | toJson | sha256sum }}
    spec:
      {{- include "platform.podDefaults" . | nindent 6 }}
      securityContext:
        fsGroup: 5050                       # image pgadmin chạy uid 5050
      containers:
        - name: pgadmin
          image: {{ .Values.pgadmin.image }}
          ports:
            - name: http
              containerPort: 80
          env:
            - name: PGADMIN_DEFAULT_EMAIL
              valueFrom: { secretKeyRef: { name: {{ .Values.secretName }}, key: PGADMIN_EMAIL } }
            - name: PGADMIN_DEFAULT_PASSWORD
              valueFrom: { secretKeyRef: { name: {{ .Values.secretName }}, key: PGADMIN_PASSWORD } }
            - { name: PGADMIN_SERVER_JSON_FILE, value: /pgadmin4/servers-k8s/servers.json }
            - { name: PGADMIN_DISABLE_POSTFIX, value: "true" }
            - { name: PGADMIN_CONFIG_UPGRADE_CHECK_ENABLED, value: "False" }
          readinessProbe:
            httpGet: { path: /misc/ping, port: http }
            initialDelaySeconds: 20
            periodSeconds: 10
          livenessProbe:
            httpGet: { path: /misc/ping, port: http }
            initialDelaySeconds: 90
            periodSeconds: 30
          resources:
            {{- toYaml .Values.pgadmin.resources | nindent 12 }}
          volumeMounts:
            - name: data
              mountPath: /var/lib/pgadmin
            - name: servers
              mountPath: /pgadmin4/servers-k8s
              readOnly: true
      volumes:
        - name: data
          persistentVolumeClaim:
            claimName: pgadmin-data
        - name: servers
          configMap:
            name: pgadmin-servers
---
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: pgadmin
  labels:
    {{- include "platform.labels" . | nindent 4 }}
  annotations:
    {{- include "platform.adminIngressAnnotations" . | nindent 4 }}
spec:
  ingressClassName: {{ .Values.ingressClassName }}
  rules:
    - host: {{ .Values.pgadmin.host | quote }}
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: pgadmin
                port:
                  number: 80
{{- end }}
```

### 4.16. Kiểm tra chart

```bash
cd /opt/datn
for e in staging prod; do
  helm lint infrastructure/helm/datn-platform -f infrastructure/helm/datn-platform/values-$e.yaml
  helm template datn-platform infrastructure/helm/datn-platform -n datn-$e \
       -f infrastructure/helm/datn-platform/values-$e.yaml > /tmp/platform-$e.yaml && echo "render $e OK"
done
grep -n "kind: Ingress" -A12 /tmp/platform-staging.yaml | grep -E "host|auth-|whitelist"
```

---

## 5. Helm chart `datn-app` (Jenkins cài)

```
infrastructure/helm/datn-app/
  Chart.yaml  values.yaml  values-staging.yaml  values-prod.yaml
  files/config/{api-gateway,identity-service,work-service,workspace-service,notification-service}/application-k8s.yaml
  templates/_helpers.tpl configmap-env.yaml configmap-spring.yaml backend.yaml frontend.yaml ingress.yaml
```

### 5.1. `Chart.yaml`

```yaml
apiVersion: v2
name: datn-app
description: Ứng dụng DATN-2026 (api-gateway, identity, work, workspace, notification, frontend)
type: application
version: 0.1.0
appVersion: "1.0.0"
```

### 5.2. `values.yaml`

```yaml
# Giá trị mặc định. Mỗi môi trường ghi đè bằng values-staging.yaml / values-prod.yaml.
# Jenkins cài chart này bằng HELM_DRIVER=configmap và CHỈ đổi image.tag (infrastructure/scripts/deploy.sh).

image:
  registry: ghcr.io/hecker-chuoi    # GHCR bắt buộc chữ thường
  tag: bootstrap                    # Jenkins: --set image.tag=<git sha>
  pullPolicy: IfNotPresent

serviceAccountName: datn-app        # do datn-platform tạo (có imagePullSecrets)
secretName: app-secrets             # do quản trị viên tạo (03 §6.1)

publicUrl: http://app.example.invalid
cookieSecure: false
keycloak:
  publicUrl: http://sso.example.invalid
  realm: datn-realm
mail:
  host: mailpit
  port: "1025"
  smtpAuth: "false"
  starttls: "false"
  from: "DATN-2026 <no-reply@datn.local>"

javaToolOptions: "-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"

ingress:
  className: nginx
  host: app.example.invalid
  annotations:
    nginx.ingress.kubernetes.io/proxy-body-size: "50m"       # upload đính kèm
    nginx.ingress.kubernetes.io/proxy-read-timeout: "3600"   # STOMP WebSocket
    nginx.ingress.kubernetes.io/proxy-send-timeout: "3600"

# 5 service Spring Boot: một template (templates/backend.yaml) sinh Deployment + Service cho mỗi mục.
# waitFor: "host:port" chờ mở cổng TCP; waitForReady: URL chờ trả 2xx (init container).
backend:
  api-gateway:
    port: 8081
    replicas: 1
    waitFor: []
    secretEnv: []
    resources:
      requests: { cpu: 200m, memory: 384Mi }
      limits: { cpu: "1", memory: 640Mi }
  identity-service:
    port: 8082
    replicas: 1
    waitFor: ["postgres-app:5432", "keycloak:8080"]
    secretEnv: [DB_PASSWORD, KC_ADMIN_PASSWORD, KEYCLOAK_CLIENT_SECRET]
    resources:
      requests: { cpu: 200m, memory: 448Mi }
      limits: { cpu: "1", memory: 768Mi }
  work-service:
    port: 8083
    replicas: 1
    waitFor: ["postgres-app:5432", "redis:6379", "kafka:9092", "minio:9000"]
    secretEnv: [DB_PASSWORD, MINIO_ROOT_USER, MINIO_ROOT_PASSWORD]
    resources:
      requests: { cpu: 250m, memory: 512Mi }
      limits: { cpu: 1500m, memory: 1Gi }
  workspace-service:
    port: 8087
    replicas: 1
    waitFor: ["postgres-app:5432", "redis:6379"]
    # Thứ tự migration trên DB trống (01 §5.5): chờ work-service tạo bảng projects
    waitForReady: ["http://work-service:8083/actuator/health/readiness"]
    secretEnv: [DB_PASSWORD]
    resources:
      requests: { cpu: 200m, memory: 384Mi }
      limits: { cpu: "1", memory: 640Mi }
  notification-service:
    port: 9994
    replicas: 1
    waitFor: ["postgres-app:5432", "kafka:9092", "redis:6379"]
    secretEnv: [DB_PASSWORD, MAIL_USERNAME, MAIL_PASSWORD]
    resources:
      requests: { cpu: 200m, memory: 384Mi }
      limits: { cpu: "1", memory: 640Mi }

frontend:
  replicas: 1
  resources:
    requests: { cpu: 20m, memory: 32Mi }
    limits: { cpu: 200m, memory: 128Mi }
```

### 5.3. `values-staging.yaml` và `values-prod.yaml`

```yaml
# Jenkins: deploy.sh staging datn-staging <sha> https://staging.example.com
publicUrl: https://staging.example.com
cookieSecure: true
keycloak:
  publicUrl: https://sso-staging.example.com
ingress:
  host: staging.example.com
```

```yaml
# Jenkins: deploy.sh prod datn-prod <sha> https://app.example.com
publicUrl: https://app.example.com
cookieSecure: true
keycloak:
  publicUrl: https://sso.example.com
ingress:
  host: app.example.com
mail:                              # SMTP thật; tài khoản/mật khẩu trong app-secrets (MAIL_USERNAME, MAIL_PASSWORD)
  host: smtp.gmail.com
  port: "587"
  smtpAuth: "true"
  starttls: "true"
  from: "DATN-2026 <no-reply@example.com>"
backend:
  api-gateway:
    replicas: 2
frontend:
  replicas: 2
```

### 5.4. `templates/_helpers.tpl`

```yaml
{{- define "app.labels" -}}
app.kubernetes.io/part-of: datn
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
datn/layer: app
{{- end }}

{{/* Nhãn cho pod template: không có phiên bản chart */}}
{{- define "app.podLabels" -}}
app.kubernetes.io/part-of: datn
datn/layer: app
{{- end }}

{{/* dict "root" $ "name" "<service>"  ->  ghcr.io/<owner>/datn-<service>:<tag> */}}
{{- define "app.image" -}}
{{ .root.Values.image.registry }}/datn-{{ .name }}:{{ required "image.tag là bắt buộc" .root.Values.image.tag }}
{{- end }}
```

### 5.5. `templates/configmap-env.yaml`

```yaml
# Biến môi trường dùng chung cho 5 service (M5: ConfigMap). Mật khẩu nằm ở Secret app-secrets.
apiVersion: v1
kind: ConfigMap
metadata:
  name: env-config
  labels:
    {{- include "app.labels" . | nindent 4 }}
data:
  APP_PUBLIC_URL: {{ .Values.publicUrl | quote }}
  APP_CORS_ALLOWED_ORIGINS: {{ .Values.publicUrl | quote }}
  APP_COOKIE_SECURE: {{ .Values.cookieSecure | quote }}
  KEYCLOAK_PUBLIC_URL: {{ .Values.keycloak.publicUrl | quote }}
  KEYCLOAK_ISSUER_URI: {{ printf "%s/realms/%s" .Values.keycloak.publicUrl .Values.keycloak.realm | quote }}
  MAIL_HOST: {{ .Values.mail.host | quote }}
  MAIL_PORT: {{ .Values.mail.port | quote }}
  MAIL_SMTP_AUTH: {{ .Values.mail.smtpAuth | quote }}
  MAIL_SMTP_STARTTLS: {{ .Values.mail.starttls | quote }}
  MAIL_FROM: {{ .Values.mail.from | quote }}
```

### 5.6. `templates/configmap-spring.yaml`

```yaml
{{- /* Profile Spring "k8s" của từng service, mount vào /config (SPRING_CONFIG_ADDITIONAL_LOCATION) */ -}}
{{- range $name, $svc := .Values.backend }}
{{- $file := printf "files/config/%s/application-k8s.yaml" $name }}
{{- if not ($.Files.Get $file) }}
{{- fail (printf "Thiếu %s" $file) }}
{{- end }}
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: {{ $name }}-config
  labels:
    {{- include "app.labels" $ | nindent 4 }}
    app.kubernetes.io/name: {{ $name }}
data:
  application-k8s.yaml: |-
{{ $.Files.Get $file | indent 4 }}
{{- end }}
```

### 5.7. `templates/backend.yaml`: một template cho 5 service Spring Boot

```yaml
{{- /* Một template cho 5 service Spring Boot (values.backend): Service + Deployment */ -}}
{{- range $name, $svc := .Values.backend }}
---
apiVersion: v1
kind: Service
metadata:
  name: {{ $name }}
  labels:
    {{- include "app.labels" $ | nindent 4 }}
    app.kubernetes.io/name: {{ $name }}
spec:
  selector:
    app.kubernetes.io/name: {{ $name }}
  ports:
    - name: http
      port: {{ $svc.port }}
      targetPort: http
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ $name }}
  labels:
    {{- include "app.labels" $ | nindent 4 }}
    app.kubernetes.io/name: {{ $name }}
    tier: backend
spec:
  replicas: {{ $svc.replicas | default 1 }}
  revisionHistoryLimit: 5
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0               # pod mới Ready rồi mới tắt pod cũ (M5: zero downtime)
  selector:
    matchLabels:
      app.kubernetes.io/name: {{ $name }}
  template:
    metadata:
      labels:
        {{- include "app.podLabels" $ | nindent 8 }}
        app.kubernetes.io/name: {{ $name }}
        tier: backend
      annotations:
        # Đổi cấu hình là hash đổi, nên Deployment tự rolling update (M5)
        checksum/config: {{ $.Files.Get (printf "files/config/%s/application-k8s.yaml" $name) | sha256sum }}
        checksum/env: {{ include (print $.Template.BasePath "/configmap-env.yaml") $ | sha256sum }}
    spec:
      serviceAccountName: {{ $.Values.serviceAccountName }}
      automountServiceAccountToken: false
      enableServiceLinks: false
      terminationGracePeriodSeconds: 45
      securityContext:
        runAsNonRoot: true
        runAsUser: 10001
        runAsGroup: 10001
        fsGroup: 10001
        seccompProfile:
          type: RuntimeDefault
      {{- if or $svc.waitFor $svc.waitForReady }}
      initContainers:
        - name: wait-deps
          image: busybox:1.36
          command:
            - sh
            - -c
            - |
              for t in {{ join " " ($svc.waitFor | default list) }}; do
                until nc -z -w 2 "${t%:*}" "${t#*:}"; do echo "chờ $t"; sleep 3; done
              done
              {{- range $svc.waitForReady }}
              until wget -q -T 5 -O /dev/null {{ . | quote }}; do echo "chờ {{ . }}"; sleep 3; done
              {{- end }}
          securityContext:
            allowPrivilegeEscalation: false
            capabilities: { drop: ["ALL"] }
          resources:
            requests: { cpu: 10m, memory: 16Mi }
            limits: { cpu: 100m, memory: 64Mi }
      {{- end }}
      containers:
        - name: app
          image: {{ include "app.image" (dict "root" $ "name" $name) }}
          imagePullPolicy: {{ $.Values.image.pullPolicy }}
          ports:
            - name: http
              containerPort: {{ $svc.port }}
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: k8s
            - name: SPRING_CONFIG_ADDITIONAL_LOCATION
              value: "optional:file:/config/"
            - name: JAVA_TOOL_OPTIONS
              value: {{ $.Values.javaToolOptions | quote }}
            {{- range $svc.secretEnv }}
            - name: {{ . }}
              valueFrom:
                secretKeyRef:
                  name: {{ $.Values.secretName }}
                  key: {{ . }}
            {{- end }}
          envFrom:
            - configMapRef:
                name: env-config
          startupProbe:                     # tối đa 180 giây để khởi động
            httpGet: { path: /actuator/health/liveness, port: http }
            periodSeconds: 5
            failureThreshold: 36
          readinessProbe:
            httpGet: { path: /actuator/health/readiness, port: http }
            periodSeconds: 10
            failureThreshold: 3
          livenessProbe:
            httpGet: { path: /actuator/health/liveness, port: http }
            periodSeconds: 20
            failureThreshold: 3
          lifecycle:
            preStop:                        # chờ Endpoints gỡ pod rồi mới tắt (graceful shutdown)
              exec: { command: ["sh", "-c", "sleep 10"] }
          securityContext:
            allowPrivilegeEscalation: false
            capabilities: { drop: ["ALL"] }
          resources:
            {{- toYaml $svc.resources | nindent 12 }}
          volumeMounts:
            - name: config
              mountPath: /config
              readOnly: true
            - name: tmp
              mountPath: /tmp
      volumes:
        - name: config
          configMap:
            name: {{ $name }}-config
        - name: tmp
          emptyDir: {}
{{- end }}
```

### 5.8. `templates/frontend.yaml`

```yaml
# frontend/nginx.conf proxy tới http://api-gateway:8081 (đúng tên Service cùng namespace), nhưng trên k8s
# Ingress đã chuyển thẳng /identity, /work, /workspace, /notification tới api-gateway.
apiVersion: v1
kind: Service
metadata:
  name: frontend
  labels:
    {{- include "app.labels" . | nindent 4 }}
    app.kubernetes.io/name: frontend
spec:
  selector:
    app.kubernetes.io/name: frontend
  ports:
    - name: http
      port: 80
      targetPort: http
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: frontend
  labels:
    {{- include "app.labels" . | nindent 4 }}
    app.kubernetes.io/name: frontend
spec:
  replicas: {{ .Values.frontend.replicas }}
  revisionHistoryLimit: 5
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  selector:
    matchLabels:
      app.kubernetes.io/name: frontend
  template:
    metadata:
      labels:
        {{- include "app.podLabels" . | nindent 8 }}
        app.kubernetes.io/name: frontend
    spec:
      serviceAccountName: {{ .Values.serviceAccountName }}
      automountServiceAccountToken: false
      enableServiceLinks: false
      containers:
        - name: app
          image: {{ include "app.image" (dict "root" . "name" "frontend") }}
          imagePullPolicy: {{ .Values.image.pullPolicy }}
          ports:
            - name: http
              containerPort: 80
          readinessProbe:
            httpGet: { path: /, port: http }
            periodSeconds: 10
          livenessProbe:
            httpGet: { path: /, port: http }
            periodSeconds: 20
          resources:
            {{- toYaml .Values.frontend.resources | nindent 12 }}
```

`frontend/nginx.conf` proxy tới `http://api-gateway:8081`, đúng tên Service trong cùng namespace, nên không cần sửa.

### 5.9. `templates/ingress.yaml`

```yaml
# M9: 1 host cho cả frontend và API (cùng origin, không cần CORS)
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: datn-app
  labels:
    {{- include "app.labels" . | nindent 4 }}
  {{- with .Values.ingress.annotations }}
  annotations:
    {{- toYaml . | nindent 4 }}
  {{- end }}
spec:
  ingressClassName: {{ .Values.ingress.className }}
  rules:
    - host: {{ .Values.ingress.host | quote }}
      http:
        paths:
          {{- range list "/identity" "/work" "/workspace" "/notification" }}
          - path: {{ . }}
            pathType: Prefix
            backend:
              service:
                name: api-gateway
                port:
                  number: {{ (index $.Values.backend "api-gateway").port }}
          {{- end }}
          - path: /
            pathType: Prefix
            backend:
              service:
                name: frontend
                port:
                  number: 80
```

### 5.10. `files/config/<service>/application-k8s.yaml`

Đây là profile `k8s`, nạp đè lên `application.yml` có trong image. `${...}` lấy từ biến môi trường (`env-config`, `app-secrets`).

`files/config/api-gateway/application-k8s.yaml`:

```yaml
# Profile "k8s": nạp đè lên application.yml trong image. ${...} lấy từ env-config / app-secrets.
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 20s
  cloud:
    gateway:
      # Danh sách (list) trong profile THAY THẾ toàn bộ list gốc, nên phải khai ĐỦ 4 route (01 §5.2)
      routes:
        - id: identity-service
          uri: http://identity-service:8082
          predicates: ["Path=/identity/api/**,/identity/actuator/**"]
          filters: ["StripPrefix=1"]
        - id: work-service
          uri: http://work-service:8083
          predicates: ["Path=/work/api/**,/work/actuator/**,/work/ws-work/**"]
          filters: ["StripPrefix=1"]
        - id: workspace-service
          uri: http://workspace-service:8087
          predicates: ["Path=/workspace/api/**,/workspace/actuator/**"]
          filters: ["StripPrefix=1"]
        # CỐ Ý không route /notification/internal/** (gửi email nội bộ, không xác thực)
        - id: notification-service
          uri: http://notification-service:9994
          predicates: ["Path=/notification/api/**,/notification/ws/**"]
          filters: ["StripPrefix=1"]
  security:
    oauth2:
      resourceserver:
        jwt:
          # Lấy khoá qua DNS nội bộ; iss của token vẫn là URL công khai (KC_HOSTNAME_URL)
          jwk-set-uri: http://keycloak:8080/realms/datn-realm/protocol/openid-connect/certs
          issuer-uri: ${KEYCLOAK_ISSUER_URI}
app:
  cors:
    allowed-origins: ${APP_CORS_ALLOWED_ORIGINS}
management:
  endpoint:
    health:
      probes:
        enabled: true
logging:
  level:
    com.datn.api_gateway: INFO
```

`files/config/identity-service/application-k8s.yaml`:

```yaml
# Profile "k8s": nạp đè lên application.yaml trong image. ${...} lấy từ env-config / app-secrets.
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 20s
  datasource:
    url: jdbc:postgresql://postgres-app:5432/datn_main_db
    username: postgres
    password: ${DB_PASSWORD}
  jpa:
    show-sql: false
    properties:
      hibernate:
        format_sql: false
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: http://keycloak:8080/realms/datn-realm/protocol/openid-connect/certs
          issuer-uri: ${KEYCLOAK_ISSUER_URI}
keycloak:
  server-url: http://keycloak:8080          # Admin API / token qua DNS nội bộ
  public-url: ${KEYCLOAK_PUBLIC_URL}        # URL trình duyệt được chuyển tới (đăng nhập Google)
  admin:
    username: admin                         # = KEYCLOAK_ADMIN trong chart datn-platform
    password: ${KC_ADMIN_PASSWORD}
  client:
    client-secret: ${KEYCLOAK_CLIENT_SECRET}
app:
  frontend-url: ${APP_PUBLIC_URL}           # link trong email xác thực / đặt lại mật khẩu
  cookie:
    secure: ${APP_COOKIE_SECURE}
  services:
    notification:
      url: http://notification-service:9994
    workspace:
      url: http://workspace-service:8087
management:
  endpoint:
    health:
      probes:
        enabled: true
```

`files/config/work-service/application-k8s.yaml`:

```yaml
# Profile "k8s": nạp đè lên application.yaml trong image. ${...} lấy từ env-config / app-secrets.
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 20s
  datasource:
    url: jdbc:postgresql://postgres-app:5432/datn_main_db
    username: postgres
    password: ${DB_PASSWORD}
  jpa:
    show-sql: false
    properties:
      hibernate:
        format_sql: false
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: http://keycloak:8080/realms/datn-realm/protocol/openid-connect/certs
          issuer-uri: ${KEYCLOAK_ISSUER_URI}
  data:
    redis:
      host: redis
      port: 6379
  kafka:
    bootstrap-servers: kafka:9092
minio:
  endpoint: http://minio:9000
  access-key: ${MINIO_ROOT_USER}
  secret-key: ${MINIO_ROOT_PASSWORD}
app:
  cluster:
    redis-enabled: true            # rolling update luôn có lúc 2 pod chạy job (01 §5.6)
  frontend-url: ${APP_PUBLIC_URL}
  services:
    identity:
      url: http://identity-service:8082
    workspace:
      url: http://workspace-service:8087
    notification:
      url: http://notification-service:9994
management:
  endpoint:
    health:
      probes:
        enabled: true
logging:
  level:
    com.datn.workservice: INFO
    org.hibernate.SQL: WARN
```

`files/config/workspace-service/application-k8s.yaml`:

```yaml
# Profile "k8s": nạp đè lên application.yaml trong image. ${...} lấy từ env-config / app-secrets.
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 20s
  datasource:
    url: jdbc:postgresql://postgres-app:5432/datn_main_db
    username: postgres
    password: ${DB_PASSWORD}
  jpa:
    show-sql: false
    properties:
      hibernate:
        format_sql: false
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: http://keycloak:8080/realms/datn-realm/protocol/openid-connect/certs
          issuer-uri: ${KEYCLOAK_ISSUER_URI}
  data:
    redis:
      host: redis
      port: 6379
app:
  frontend-url: ${APP_PUBLIC_URL}
  services:
    identity:
      url: http://identity-service:8082
    notification:
      url: http://notification-service:9994
management:
  endpoint:
    health:
      probes:
        enabled: true
logging:
  level:
    com.datn.workspaceservice: INFO
    org.hibernate.SQL: WARN
```

`files/config/notification-service/application-k8s.yaml` (các biến `MAIL_*` đã được `application.yaml` đọc từ môi trường):

```yaml
# Profile "k8s": nạp đè lên application.yaml trong image. ${...} lấy từ env-config / app-secrets.
# Các biến MAIL_* và KEYCLOAK_ISSUER_URI được application.yaml gốc đọc thẳng từ môi trường.
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 20s
  datasource:
    url: jdbc:postgresql://postgres-app:5432/datn_main_db
    username: postgres
    password: ${DB_PASSWORD}
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: http://keycloak:8080/realms/datn-realm/protocol/openid-connect/certs
          issuer-uri: ${KEYCLOAK_ISSUER_URI}
  kafka:
    bootstrap-servers: kafka:9092
  data:
    redis:
      host: redis
      port: 6379
app:
  frontend-url: ${APP_PUBLIC_URL}           # link trong thông báo / email
  cluster:
    redis-enabled: true            # WebSocket qua Redis Pub/Sub khi 2 pod cùng chạy
  services:
    identity:
      url: http://identity-service:8082
management:
  endpoint:
    health:
      probes:
        enabled: true
logging:
  level:
    com.datn.notification: INFO
```

### 5.11. Kiểm tra chart

```bash
for e in staging prod; do
  helm lint infrastructure/helm/datn-app -f infrastructure/helm/datn-app/values-$e.yaml
  helm template datn-app infrastructure/helm/datn-app -n datn-$e -f infrastructure/helm/datn-app/values-$e.yaml \
       --set image.tag=0123456789abcdef0123456789abcdef01234567 > /tmp/app-$e.yaml && echo "render $e OK"
done
grep -E "^\s+image:|replicas:|host:" /tmp/app-prod.yaml
```

---

## 6. Tạo Secret và cài platform

Làm cho `datn-staging` trước, sau đó lặp lại với `ENV=prod`.

### 6.1. Namespace và `app-secrets`

`infrastructure/k8s/namespaces.yaml`:

```yaml
# Quản trị viên tạo một lần: kubectl apply -f infrastructure/k8s/namespaces.yaml (03 §6.1)
apiVersion: v1
kind: Namespace
metadata:
  name: datn-staging
  labels: { app.kubernetes.io/part-of: datn, datn/env: staging }
---
apiVersion: v1
kind: Namespace
metadata:
  name: datn-prod
  labels: { app.kubernetes.io/part-of: datn, datn/env: prod }
```

```bash
cd /opt/datn
kubectl apply -f infrastructure/k8s/namespaces.yaml

ENV=staging; NS=datn-$ENV
mkdir -p ~/datn-secrets && chmod 700 ~/datn-secrets
cat > ~/datn-secrets/$ENV.env <<EOF
DB_PASSWORD=$(openssl rand -hex 16)
KC_DB_PASSWORD=$(openssl rand -hex 16)
KC_ADMIN_PASSWORD=$(openssl rand -hex 12)
KEYCLOAK_CLIENT_SECRET=$(openssl rand -hex 16)
MINIO_ROOT_USER=datn-minio
MINIO_ROOT_PASSWORD=$(openssl rand -hex 16)
MAIL_USERNAME=
MAIL_PASSWORD=
PGADMIN_EMAIL=dba@example.com
PGADMIN_PASSWORD=$(openssl rand -hex 12)
EOF
chmod 600 ~/datn-secrets/$ENV.env
kubectl -n $NS create secret generic app-secrets --from-env-file=$HOME/datn-secrets/$ENV.env
```

Ở prod, điền `MAIL_USERNAME`/`MAIL_PASSWORD` (Gmail App Password). Sao lưu `~/datn-secrets` vào trình quản lý mật khẩu.

### 6.2. `keycloak-realm`

```bash
source ~/datn-secrets/$ENV.env
APP_URL=https://staging.example.com          # prod: https://app.example.com
jq --arg secret "$KEYCLOAK_CLIENT_SECRET" --arg app "$APP_URL" '
  (.clients[] | select(.clientId == "datn-backend")) |= (
      .secret = $secret
    | .redirectUris = [$app + "/*"]
    | .webOrigins = [$app]
    | .attributes["post.logout.redirect.uris"] = ($app + "/*"))
' infrastructure/keycloak/realm-export_08082026.json > ~/datn-secrets/realm-$ENV.json
# Chạy HTTP qua nip.io: thêm  | .sslRequired = "none"  vào cuối biểu thức jq
kubectl -n $NS create secret generic keycloak-realm --from-file=realm.json=$HOME/datn-secrets/realm-$ENV.json
```

> **Bảo mật:** `realm-export_08082026.json` trong Git chứa **client secret Google thật**. Hãy tạo lại secret trên Google Cloud Console, nhập lại ở Keycloak Admin Console (Identity providers → google), và xoá giá trị cũ khỏi file export.

Thêm redirect URI trên Google Cloud Console: `https://sso-staging.example.com/realms/datn-realm/broker/google/endpoint` (prod: `https://sso.example.com/...`).

### 6.3. `ghcr-pull`

```bash
kubectl -n $NS create secret docker-registry ghcr-pull \
  --docker-server=ghcr.io --docker-username=<github-username> --docker-password=<PAT_read_packages>
```

### 6.4. `admin-basic-auth` (Ingress công cụ quản trị)

```bash
htpasswd -c ~/datn-secrets/htpasswd-$ENV quantri      # thêm người: htpasswd ~/datn-secrets/htpasswd-$ENV <tên>
kubectl -n $NS create secret generic admin-basic-auth --from-file=auth=$HOME/datn-secrets/htpasswd-$ENV
```

Key trong Secret **bắt buộc** tên là `auth`.

### 6.5. Cài `datn-platform`

Quản trị viên cài với driver Secret mặc định, nên CI không đọc hay sửa được release này.

```bash
helm upgrade --install datn-platform infrastructure/helm/datn-platform -n $NS \
  -f infrastructure/helm/datn-platform/values-$ENV.yaml --wait --timeout 15m

kubectl -n $NS get pods,pvc,ing
kubectl -n $NS describe resourcequota datn-quota
helm -n $NS list
```

Kiểm tra issuer (chưa cần Nginx máy chủ):

```bash
curl -s -H "Host: sso-staging.example.com" -H "X-Forwarded-Proto: https" \
  http://$(minikube ip)/realms/datn-realm/.well-known/openid-configuration | jq -r .issuer
# Kỳ vọng: https://sso-staging.example.com/realms/datn-realm
```

Đổi cấu hình platform về sau: sửa values, chạy lại lệnh `helm upgrade` ở trên. `storage` của StatefulSet **không sửa được** sau khi tạo.

---

## 7. Cài ứng dụng lần đầu (bằng tay)

Chưa có Jenkins pipeline: build image ngay trên máy chủ và nạp vào minikube với tag `bootstrap`. Với tag khác `latest`, `IfNotPresent` dùng image có sẵn, không kéo từ GHCR.

```bash
cd /opt/datn && git pull
OWNER=hecker-chuoi
for s in api-gateway identity-service work-service workspace-service notification-service; do
  docker build -t ghcr.io/$OWNER/datn-$s:bootstrap -f backend/$s/Dockerfile backend
  minikube image load ghcr.io/$OWNER/datn-$s:bootstrap
done
docker build -t ghcr.io/$OWNER/datn-frontend:bootstrap frontend
minikube image load ghcr.io/$OWNER/datn-frontend:bootstrap

# BẮT BUỘC driver configmap: Jenkins (không có quyền Secret) phải thấy và nâng cấp được release này
export HELM_DRIVER=configmap
helm upgrade --install datn-app infrastructure/helm/datn-app -n datn-staging \
  -f infrastructure/helm/datn-app/values-staging.yaml --set image.tag=bootstrap --wait --timeout 15m
helm -n datn-staging history datn-app
kubectl -n datn-staging get cm -l owner=helm          # sh.helm.release.v1.datn-app.v1
```

Kiểm tra:

```bash
H="Host: staging.example.com"; P="X-Forwarded-Proto: https"; IP=$(minikube ip)
for p in identity work workspace; do curl -s -H "$H" -H "$P" http://$IP/$p/actuator/health; echo; done
curl -s -o /dev/null -w "%{http_code}\n" -H "$H" http://$IP/        # 200
```

---

## 8. Nginx máy chủ và TLS

Trỏ DNS A cho mọi host ở §0 (trừ `jenkins`, xem 04) về IP máy chủ. `/etc/nginx/sites-available/datn`:

```nginx
map $http_upgrade $connection_upgrade {
    default upgrade;
    ''      close;
}

server {
    listen 80;
    server_name app.example.com sso.example.com pgadmin.example.com minio.example.com
                staging.example.com sso-staging.example.com pgadmin-staging.example.com mail-staging.example.com;

    client_max_body_size 50m;

    location / {
        proxy_pass http://192.168.49.2;            # = $(minikube ip), cổng 80 của ingress-nginx
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;    # WebSocket (STOMP, pgAdmin)
        proxy_set_header Connection $connection_upgrade;
        proxy_set_header Host $host;
        # Lớp ngoài cùng: GHI ĐÈ, không nối thêm, để client không giả được IP vượt whitelist (01 §5.4)
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Host $host;
        proxy_set_header X-Forwarded-Port $server_port;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
        proxy_buffer_size 128k;                    # header/cookie lớn của Keycloak
        proxy_buffers 4 256k;
        proxy_busy_buffers_size 256k;
    }
}
```

```bash
sudo ln -s /etc/nginx/sites-available/datn /etc/nginx/sites-enabled/datn
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d app.example.com -d sso.example.com -d pgadmin.example.com \
  -d staging.example.com -d sso-staging.example.com -d pgadmin-staging.example.com -d mail-staging.example.com
# Bật MinIO Console thì thêm: -d minio.example.com
```

Kiểm tra `https://staging.example.com`: đăng nhập mật khẩu và Google, tạo workspace/dự án/công việc, tải đính kèm, thông báo realtime (WS `/notification/ws` = 101), email trong Mailpit.

---

## 9. Công cụ quản trị (pgAdmin, Mailpit, MinIO Console, Keycloak Admin, Jenkins)

### 9.1. Tổng quan

| Công cụ | URL | Chạy ở | Lớp bảo vệ | Đăng nhập |
|---|---|---|---|---|
| **pgAdmin** | `https://pgadmin[-staging].example.com` | Pod `pgadmin` mỗi namespace | TLS → whitelist IP → basic auth `admin-basic-auth` → đăng nhập pgAdmin | `PGADMIN_EMAIL` / `PGADMIN_PASSWORD` trong `~/datn-secrets/<env>.env` |
| **Mailpit** | `https://mail-staging.example.com` | Pod `mailpit` (staging) | TLS → whitelist → basic auth | Không có |
| **MinIO Console** | `https://minio.example.com` | Pod `minio` cổng 9001 | TLS → whitelist → basic auth → đăng nhập MinIO | `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` |
| **Keycloak Admin** | `https://sso[-staging].example.com/admin` | Pod `keycloak` | TLS → whitelist (`keycloak.adminAllowCidrs`) | `admin` / `KC_ADMIN_PASSWORD` |
| **Jenkins** | `https://jenkins.example.com` | Systemd trên máy chủ, **ngoài cluster** | TLS → (whitelist / SSH tunnel) → đăng nhập Jenkins | [04 §7–8](04_HUONG_DAN_SETUP_JENKINS.md#7-khởi-tạo-jenkins-và-cài-plugin) |

### 9.2. Bật/tắt và giới hạn IP

Sửa `values-<env>.yaml` của `datn-platform` rồi chạy lại `helm upgrade` (§6.5):

```yaml
adminAccess:
  allowCidrs: "203.0.113.0/24,198.51.100.7/32"     # IP nhóm; để "" thì bỏ giới hạn
keycloak:
  adminAllowCidrs: "203.0.113.0/24"
pgadmin:
  enabled: true
minio:
  console:
    ingress: true                                  # chỉ bật khi cần
```

Hoặc bật nhanh không sửa file: `helm upgrade datn-platform ... --reuse-values --set minio.console.ingress=true`.

### 9.3. pgAdmin lần đầu
1. Mở `https://pgadmin-staging.example.com`, nhập basic auth, rồi đăng nhập pgAdmin.
2. Nhóm **DATN** đã có sẵn `datn_main_db (datn-staging)` và `keycloak (datn-staging)` (từ `servers.json`, chỉ nạp ở lần khởi động đầu). Khi kết nối, nhập mật khẩu `DB_PASSWORD` / `KC_DB_PASSWORD`. Không nên tick "Save password" ở prod.
3. pgAdmin staging chỉ thấy DB staging: Service `postgres-app` được phân giải trong namespace của nó, và NetworkPolicy chặn chéo namespace.

### 9.4. Không muốn mở ra Internet
Đặt `pgadmin.enabled: false` (hoặc giữ pod nhưng bỏ host khỏi Nginx máy chủ) và dùng port-forward qua SSH:

```bash
# Trên máy chủ
kubectl -n datn-prod port-forward svc/pgadmin 5050:80
# Trên máy cá nhân
ssh -L 5050:127.0.0.1:5050 datnadmin@<server>      # rồi mở http://localhost:5050
```

---

## 10. Kubeconfig cho CI (ServiceAccount `ci-deployer`)

`infrastructure/scripts/make-ci-kubeconfig.sh`:

```bash
#!/usr/bin/env bash
# Dựng kubeconfig CHỈ có quyền của SA ci-deployer trong 1 namespace (03 §10). Chạy bằng kubeconfig admin.
# Cách dùng: make-ci-kubeconfig.sh <namespace>     ->  tạo file kubeconfig-<namespace> ở thư mục hiện tại
set -euo pipefail
NS="${1:?Cách dùng: $0 <namespace>}"
OUT="kubeconfig-${NS}"

SERVER=$(kubectl config view --minify -o jsonpath='{.clusters[0].cluster.server}')
CA=$(kubectl config view --raw --flatten --minify -o jsonpath='{.clusters[0].cluster.certificate-authority-data}')
TOKEN=$(kubectl -n "$NS" get secret ci-deployer-token -o jsonpath='{.data.token}' | base64 -d)
[ -n "$TOKEN" ] || { echo "Secret ci-deployer-token chưa có token (đã cài datn-platform vào $NS chưa?)"; exit 1; }

umask 077
cat > "$OUT" <<EOF
apiVersion: v1
kind: Config
clusters:
  - name: minikube
    cluster:
      server: ${SERVER}
      certificate-authority-data: ${CA}
users:
  - name: ci-deployer
    user:
      token: ${TOKEN}
contexts:
  - name: ci-deployer@${NS}
    context:
      cluster: minikube
      user: ci-deployer
      namespace: ${NS}
current-context: ci-deployer@${NS}
EOF

echo "== Kiểm tra quyền (kỳ vọng: yes yes yes no no no)"
for check in "patch deployments" "delete configmaps" "get replicasets" "get secrets" "delete statefulsets" "patch persistentvolumeclaims"; do
  # shellcheck disable=SC2086  # cố ý tách "động từ tài nguyên"
  printf '%-30s ' "$check"; KUBECONFIG="$OUT" kubectl auth can-i $check -n "$NS" || true
done
printf '%-30s ' "namespace default (kỳ vọng no)"; KUBECONFIG="$OUT" kubectl auth can-i patch deployments -n default || true
echo "== Helm (driver configmap) thấy release:"
KUBECONFIG="$OUT" HELM_DRIVER=configmap helm -n "$NS" list || true
echo "Tải $OUT lên Jenkins làm credential 'Secret file' (04 §10.3), rồi xoá file."
```

```bash
bash infrastructure/scripts/make-ci-kubeconfig.sh datn-staging
bash infrastructure/scripts/make-ci-kubeconfig.sh datn-prod
```

**Xoay token** khi nghi bị lộ: `kubectl -n datn-staging delete secret ci-deployer-token`, chạy lại `helm upgrade datn-platform ...` (§6.5) để tạo lại Secret, chạy lại script, cập nhật credential trong Jenkins.

---

## 11. Jenkins và GitHub

**Jenkins:** cài và cấu hình theo [04](04_HUONG_DAN_SETUP_JENKINS.md). Khi xong, máy chủ có:

| Thứ | Giá trị |
|---|---|
| Job | `DATN/ci/datn` (Multibranch, `Jenkinsfile`), `DATN/release/datn-deploy` (`infrastructure/jenkins/Jenkinsfile.deploy`) |
| Tools | `jdk-21`, `maven-3.9`, `node-22`; trên PATH: `kubectl`, `helm`, `kubeconform`, `trivy`, Docker rootless (`DOCKER_HOST`) |
| Credentials | `github-pat`, `ghcr-push`, `kubeconfig-staging`, `kubeconfig-prod` (chỉ folder release), `github-webhook-secret` |
| Lock | `datn-deploy-staging`, `datn-deploy-prod` (Lockable Resources tự tạo khi dùng lần đầu) |

**GitHub:**

| Nơi | Cấu hình |
|---|---|
| Settings → Webhooks | `https://jenkins.example.com/github-webhook/`, JSON, secret = `github-webhook-secret`, events: Pushes + Pull requests ([04 §13](04_HUONG_DAN_SETUP_JENKINS.md#13-webhook-github)) |
| Settings → Branches → rule `main` | Require PR + 1 approval; Require status checks: `continuous-integration/jenkins/pr-merge` |
| Packages (sau lần push đầu) | Mỗi `datn-*`: **Private**. Label `org.opencontainers.image.source` do `build-images.sh` gắn sẽ tự liên kết package với repo |

---

## 12. Pipeline Jenkins

### 12.1. `infrastructure/scripts/build-images.sh`

```bash
#!/usr/bin/env bash
# Build + quét + push 6 image, TUẦN TỰ để không tranh RAM với cluster (03 §12.1). Cần đã `docker login ghcr.io`.
# Chạy bằng Docker rootless của Jenkins (DOCKER_HOST). Cách dùng: build-images.sh <git-sha>
set -euo pipefail
TAG="${1:?Cách dùng: $0 <git-sha>}"
REGISTRY="${REGISTRY:-ghcr.io/hecker-chuoi}"         # GHCR bắt buộc chữ thường
SOURCE_URL="${SOURCE_URL:-https://github.com/Hecker-Chuoi/DATN-2026}"
SERVICES="${SERVICES:-api-gateway identity-service work-service workspace-service notification-service frontend}"
# Trivy CHỈ BÁO CÁO, không chặn (quyết định của nhóm, 24/11/2026): thư viện của Spring Boot 3.5.x có CVE CRITICAL
# mà bản vá chỉ có ở Spring 7. Muốn bật lại cổng chặn: đặt TRIVY_EXIT_CODE=1 (biến môi trường của job Jenkins).
TRIVY_EXIT_CODE="${TRIVY_EXIT_CODE:-0}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
export DOCKER_BUILDKIT=1

for s in $SERVICES; do
  if [ "$s" = frontend ]; then ctx=frontend; file=frontend/Dockerfile; else ctx=backend; file="backend/$s/Dockerfile"; fi
  img="$REGISTRY/datn-$s"
  echo "== Build $img:$TAG"
  docker build \
    --label "org.opencontainers.image.revision=$TAG" \
    --label "org.opencontainers.image.source=$SOURCE_URL" \
    -t "$img:$TAG" -t "$img:main" -f "$file" "$ctx"
  echo "== Quét $img:$TAG (lỗ hổng HIGH/CRITICAL đã có bản vá; exit-code $TRIVY_EXIT_CODE: 0 = chỉ báo cáo)"
  trivy image --quiet --severity HIGH,CRITICAL --ignore-unfixed --exit-code "$TRIVY_EXIT_CODE" "$img:$TAG"
  docker push "$img:$TAG"
  docker push "$img:main"
done
echo "✅ Đã push $(echo "$SERVICES" | wc -w) image với tag $TAG"
```

### 12.2. `infrastructure/scripts/deploy.sh`

```bash
#!/usr/bin/env bash
# helm upgrade --install release datn-app, tự rollback khi rollout lỗi hoặc smoke test lỗi (03 §12.2).
# Cách dùng: KUBECONFIG=<kubeconfig ci-deployer> deploy.sh <staging|prod> <namespace> <git-sha> <app-url>
set -euo pipefail
ENV_NAME="${1:?staging|prod}"; NS="${2:?namespace}"; TAG="${3:?git sha}"; APP_URL="${4:?app url}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CHART="$ROOT/infrastructure/helm/datn-app"
VALUES="$CHART/values-$ENV_NAME.yaml"
RELEASE=datn-app
SELECTOR="datn/layer=app"
TIMEOUT="${DEPLOY_TIMEOUT:-15m}"
export HELM_DRIVER=configmap           # release lưu ở ConfigMap: CI không cần quyền Secret

[ -f "$VALUES" ] || { echo "Không có $VALUES"; exit 1; }
[[ "$TAG" =~ ^[0-9a-f]{40}$ ]] || { echo "TAG phải là git SHA đầy đủ (40 ký tự hex): $TAG"; exit 1; }

# Helm 4 đổi --atomic thành --rollback-on-failure: dò theo trợ giúp của chính bản helm đang cài
if helm upgrade --help | grep -q -- '--rollback-on-failure'; then ROLLBACK_FLAG=--rollback-on-failure; else ROLLBACK_FLAG=--atomic; fi

kubectl auth can-i patch deployments -n "$NS" | grep -qx yes || { echo "ci-deployer không có quyền trên $NS"; exit 1; }

echo "== Lint + render"
helm lint "$CHART" -f "$VALUES" --set image.tag="$TAG"
helm template "$RELEASE" "$CHART" -n "$NS" -f "$VALUES" --set image.tag="$TAG" | grep -E '^\s+image:' | sort -u

PREV=$(helm -n "$NS" history "$RELEASE" --max 20 -o json 2>/dev/null \
       | jq -r '[.[] | select(.status=="deployed")] | last | .revision // empty' || true)
echo "== Revision đang chạy: ${PREV:-<chưa có>}"

diagnose() {
  helm -n "$NS" status "$RELEASE" || true
  helm -n "$NS" history "$RELEASE" --max 5 || true
  kubectl -n "$NS" get pods -o wide || true
  kubectl -n "$NS" get events --sort-by=.lastTimestamp | tail -40 || true
  for p in $(kubectl -n "$NS" get pods -l "$SELECTOR" -o name); do
    echo "--- $p"; kubectl -n "$NS" logs "$p" --all-containers --tail=80 || true
  done
}

smoke() {
  local p out
  # /<service>/actuator/health đi qua Ingress -> api-gateway -> service (gồm cả kiểm tra DB, Redis).
  # Kiểm cả NỘI DUNG: nếu route hỏng rơi về frontend, nginx của SPA vẫn trả index.html với mã 200.
  for p in identity work workspace; do
    out=$(curl -fsS --retry 10 --retry-delay 6 --retry-all-errors "$APP_URL/$p/actuator/health") || return 1
    echo "$p: $out"
    echo "$out" | grep -q '"status":"UP"' || { echo "  -> không phải health JSON UP"; return 1; }
  done
  echo -n "frontend: "; curl -fsS -o /dev/null -w '%{http_code}\n' "$APP_URL/" || return 1
}

echo "== helm upgrade --install ($ROLLBACK_FLAG, timeout $TIMEOUT)"
if ! helm upgrade --install "$RELEASE" "$CHART" -n "$NS" -f "$VALUES" --set image.tag="$TAG" \
       "$ROLLBACK_FLAG" --wait --timeout "$TIMEOUT" --history-max 10; then
  echo "❌ Rollout lỗi, Helm đã tự rollback"
  diagnose; exit 1
fi

echo "== Smoke test qua Ingress ($APP_URL)"
if ! smoke; then
  echo "❌ Smoke test lỗi, rollback về revision ${PREV:-<không có>}"
  diagnose
  if [ -n "$PREV" ]; then helm -n "$NS" rollback "$RELEASE" "$PREV" --wait --timeout "$TIMEOUT"; fi
  exit 1
fi

helm -n "$NS" history "$RELEASE" --max 3
echo "✅ $RELEASE@$TAG đã chạy trên $NS"
```

### 12.3. `Jenkinsfile` (gốc repo, job Multibranch `DATN/ci/datn`)

```groovy
// Job Multibranch DATN/ci/datn (docs/cicd/03 §12.3, 04 §11)
// PR / nhánh : test backend, lint+build frontend, helm lint + kubeconform
// main       : + build/quét/push 6 image (tag = git SHA) + helm upgrade staging
// tag vX.Y.Z : KHÔNG build lại; gắn tag phiên bản cho image đã có rồi kích hoạt job duyệt deploy prod
pipeline {
  agent none
  options {
    timestamps()
    buildDiscarder(logRotator(numToKeepStr: '20'))
    disableConcurrentBuilds()
    timeout(time: 90, unit: 'MINUTES')
  }
  environment {
    REGISTRY    = 'ghcr.io/hecker-chuoi'
    HELM_DRIVER = 'configmap'
    MAVEN_OPTS  = '-Xmx768m'
    STAGING_URL = 'https://staging.example.com'
  }
  stages {
    stage('CI') {
      when { not { buildingTag() } }
      agent any
      tools {
        jdk 'jdk-21'
        maven 'maven-3.9'
        nodejs 'node-22'
      }
      stages {
        stage('Backend: build & unit test') {
          steps {
            // IdentityServiceApplicationTests (@SpringBootTest) cần Postgres thật ở localhost:5433, CI không có:
            // loại riêng test đó, mọi test unit khác vẫn chạy.
            dir('backend') {
              sh 'mvn -B -ntp verify "-Dtest=!IdentityServiceApplicationTests" -Dsurefire.failIfNoSpecifiedTests=false'
            }
          }
          post {
            always { junit allowEmptyResults: true, testResults: 'backend/**/target/surefire-reports/*.xml' }
          }
        }
        stage('Frontend: lint & build') {
          steps {
            dir('frontend') {
              sh 'npm ci --no-audit --no-fund'
              // Lint CHƯA chặn (còn ~190 lỗi có từ trước): lỗi thì stage UNSTABLE, build vẫn tiếp tục
              catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') { sh 'npm run lint' }
              sh 'npm run build'
            }
          }
        }
        stage('Helm lint & kubeconform') {
          steps {
            sh '''
              set -eu
              for e in staging prod; do
                for c in datn-platform datn-app; do
                  helm lint infrastructure/helm/$c -f infrastructure/helm/$c/values-$e.yaml
                  helm template $c infrastructure/helm/$c -n datn-$e -f infrastructure/helm/$c/values-$e.yaml \
                    | kubeconform -strict -summary -ignore-missing-schemas
                done
              done
            '''
          }
        }
        stage('Build, quét & push image') {
          when { branch 'main' }
          steps {
            withCredentials([usernamePassword(credentialsId: 'ghcr-push', usernameVariable: 'GHCR_USER', passwordVariable: 'GHCR_TOKEN')]) {
              sh '''
                echo "$GHCR_TOKEN" | docker login ghcr.io -u "$GHCR_USER" --password-stdin
                bash infrastructure/scripts/build-images.sh "$GIT_COMMIT"
              '''
            }
          }
          post { always { sh 'docker logout ghcr.io || true' } }
        }
        stage('Deploy staging') {
          when { branch 'main' }
          options { lock('datn-deploy-staging') }
          steps {
            withCredentials([file(credentialsId: 'kubeconfig-staging', variable: 'KUBECONFIG')]) {
              sh 'bash infrastructure/scripts/deploy.sh staging datn-staging "$GIT_COMMIT" "$STAGING_URL"'
            }
          }
        }
      }
      post {
        always { cleanWs(notFailBuild: true) }
      }
    }

    stage('Release') {
      when { tag pattern: 'v\\d+\\.\\d+\\.\\d+', comparator: 'REGEXP' }
      stages {
        stage('Gắn tag phiên bản cho image') {
          agent any
          steps {
            script { env.RELEASE_SHA = env.GIT_COMMIT }
            withCredentials([usernamePassword(credentialsId: 'ghcr-push', usernameVariable: 'GHCR_USER', passwordVariable: 'GHCR_TOKEN')]) {
              sh '''
                set -eu
                echo "$GHCR_TOKEN" | docker login ghcr.io -u "$GHCR_USER" --password-stdin
                for s in api-gateway identity-service work-service workspace-service notification-service frontend; do
                  src="$REGISTRY/datn-$s:$GIT_COMMIT"
                  docker buildx imagetools inspect "$src" > /dev/null \
                    || { echo "Chưa có $src. Commit được tag phải qua pipeline main trước."; exit 1; }
                  docker buildx imagetools create -t "$REGISTRY/datn-$s:$TAG_NAME" "$src"
                done
              '''
            }
          }
          post {
            always {
              sh 'docker logout ghcr.io || true'
              cleanWs(notFailBuild: true)
            }
          }
        }
        stage('Kích hoạt job duyệt & deploy prod') {
          // Không có agent: không giữ executor
          steps {
            build job: '/DATN/release/datn-deploy', wait: false, parameters: [
              string(name: 'TARGET', value: 'production'),
              string(name: 'ACTION', value: 'deploy'),
              string(name: 'IMAGE_TAG', value: env.RELEASE_SHA),
              string(name: 'REVISION', value: ''),
              string(name: 'VERSION', value: env.TAG_NAME)
            ]
          }
        }
      }
    }
  }
}
```

### 12.4. `infrastructure/jenkins/Jenkinsfile.deploy` (job `DATN/release/datn-deploy`)

```groovy
// Job DATN/release/datn-deploy (docs/cicd/03 §12.4, 04 §12)
// Deploy prod sau khi gắn tag (được Jenkinsfile gọi), deploy lại / rollback staging hoặc prod bằng tay.
// Credential kubeconfig-prod CHỈ có trong folder DATN/release.
pipeline {
  agent none
  parameters {
    choice(name: 'TARGET', choices: ['staging', 'production'], description: 'Môi trường')
    choice(name: 'ACTION', choices: ['deploy', 'helm-rollback'],
           description: 'deploy: cài image IMAGE_TAG | helm-rollback: quay về REVISION (để trống = revision ngay trước)')
    string(name: 'IMAGE_TAG', defaultValue: '', description: 'Git SHA đầy đủ 40 ký tự (ACTION=deploy)')
    string(name: 'REVISION', defaultValue: '', description: 'Số revision Helm (ACTION=helm-rollback), xem helm history')
    string(name: 'VERSION', defaultValue: '', description: 'Tag phiên bản, chỉ để ghi chú (vd v1.2.0)')
  }
  options {
    timestamps()
    buildDiscarder(logRotator(numToKeepStr: '30'))
    disableConcurrentBuilds()
  }
  stages {
    stage('Kiểm tra tham số') {
      steps {
        script {
          if (params.ACTION == 'deploy' && !(params.IMAGE_TAG ==~ /[0-9a-f]{40}/)) {
            error 'IMAGE_TAG phải là git SHA đầy đủ (40 ký tự hex)'
          }
          if (params.ACTION == 'helm-rollback' && params.REVISION && !(params.REVISION ==~ /\d+/)) {
            error 'REVISION phải là số'
          }
          if (params.VERSION && !(params.VERSION ==~ /[0-9A-Za-z.\-]+/)) {
            error 'VERSION chỉ gồm chữ, số, dấu chấm và gạch ngang'
          }
          def what = params.ACTION == 'deploy' ? (params.VERSION ?: params.IMAGE_TAG.substring(0, 7)) : "rollback ${params.REVISION ?: 'prev'}"
          currentBuild.displayName = "#${env.BUILD_NUMBER} ${params.TARGET} ${what}"
        }
      }
    }
    stage('Duyệt') {
      when { expression { params.TARGET == 'production' } }
      // Không khai báo agent: chờ duyệt không giữ executor
      steps {
        timeout(time: 24, unit: 'HOURS') {
          input message: "${params.ACTION} ${params.VERSION ?: params.IMAGE_TAG ?: params.REVISION} lên PRODUCTION?",
                ok: 'Đồng ý', submitter: 'truongnhom'     // user ID Jenkins được duyệt, cách nhau dấu phẩy
        }
      }
    }
    stage('Thực hiện') {
      agent any
      environment { HELM_DRIVER = 'configmap' }
      steps {
        script {
          def cfg = params.TARGET == 'production'
            ? [env: 'prod',    ns: 'datn-prod',    url: 'https://app.example.com',     cred: 'kubeconfig-prod']
            : [env: 'staging', ns: 'datn-staging', url: 'https://staging.example.com', cred: 'kubeconfig-staging']
          lock("datn-deploy-${cfg.env}") {
            withCredentials([file(credentialsId: cfg.cred, variable: 'KUBECONFIG')]) {
              if (params.ACTION == 'deploy') {
                // IMAGE_TAG đã được kiểm tra là hex 40 ký tự, nên nối vào lệnh shell an toàn
                sh "bash infrastructure/scripts/deploy.sh ${cfg.env} ${cfg.ns} ${params.IMAGE_TAG} ${cfg.url}"
              } else {
                // REVISION đã được kiểm tra là số hoặc rỗng (rỗng = revision ngay trước)
                sh """
                  set -eu
                  helm -n ${cfg.ns} history datn-app --max 10
                  helm -n ${cfg.ns} rollback datn-app ${params.REVISION} --wait --timeout 15m
                  helm -n ${cfg.ns} history datn-app --max 3
                """
              }
            }
          }
        }
      }
      post { always { cleanWs(notFailBuild: true) } }
    }
  }
}
```

> Job này luôn lấy script từ nhánh `main` ([04 §12](04_HUONG_DAN_SETUP_JENKINS.md#12-job-deploy--duyệt-prod-datnreleasedatn-deploy)). Khi `ACTION=deploy` với SHA cũ thì dùng **image cũ + chart hiện tại**. Muốn quay về đúng cả chart lẫn image cũ thì dùng `ACTION=helm-rollback`.

### 12.5. Phát hành prod và rollback

```bash
# Máy dev, sau khi staging đã ổn với commit cần phát hành
git checkout main && git pull
git tag -a v1.0.0 -m "Phát hành v1.0.0" && git push origin v1.0.0
# Jenkins: DATN/ci/datn → v1.0.0 (gắn tag image) → DATN/release/datn-deploy chờ duyệt → "Đồng ý"
```

| Tình huống | Cách làm |
|---|---|
| Bản mới lỗi ngay khi deploy | Tự động: `--rollback-on-failure` hoặc smoke test rồi `helm rollback` |
| Phát hiện lỗi sau vài giờ | `datn-deploy` → `ACTION=helm-rollback`, `TARGET=…`, `REVISION` trống (về bản trước) hoặc số cụ thể |
| Muốn chạy lại một SHA cũ | `datn-deploy` → `ACTION=deploy`, `IMAGE_TAG=<sha>` |
| Khẩn cấp, Jenkins hỏng | Quản trị viên: `HELM_DRIVER=configmap helm -n datn-prod rollback datn-app` |

---

## 13. Kiểm thử nghiệm thu

Chạy bằng kubeconfig admin. Tương ứng [02 §9](02_KE_HOACH_TRIEN_KHAI_CICD.md#9-kịch-bản-nghiệm-thu-minh-chứng-cho-báo-cáo).

```bash
NS=datn-staging; export HELM_DRIVER=configmap

# T1 – zero downtime (chạy trong lúc Jenkins deploy)
while true; do curl -s -o /dev/null -w "%{http_code} " https://staging.example.com/work/actuator/health; sleep 0.2; done

# T2/T3 – lịch sử, rollback của Helm
helm -n $NS history datn-app
helm -n $NS rollback datn-app <REVISION> --wait

# T4 – self-healing
kubectl -n $NS delete pod -l app.kubernetes.io/name=identity-service
kubectl -n $NS get pods -l app.kubernetes.io/name=identity-service -w

# T5 – liveness
kubectl -n $NS exec deploy/work-service -- sh -c 'kill 1'
kubectl -n $NS get pods -l app.kubernetes.io/name=work-service      # RESTARTS +1

# T6 – RBAC
SA=system:serviceaccount:$NS:ci-deployer
for c in "patch deployments" "get secrets" "delete statefulsets"; do echo -n "$c: "; kubectl auth can-i $c -n $NS --as=$SA; done
echo -n "ns prod: "; kubectl auth can-i patch deployments -n datn-prod --as=$SA

# T7 – quota
# Mỗi container tối đa 2Gi (LimitRange), nên dùng 3 pod x 2Gi để vượt trần requests.memory 8Gi của namespace
kubectl -n $NS create deployment stress --image=busybox:1.36 --replicas=3 -- sleep 3600
kubectl -n $NS set resources deployment stress --requests=memory=2Gi --limits=memory=2Gi
sleep 10; kubectl -n $NS get events | grep -i "exceeded quota" ; kubectl -n $NS delete deployment stress

# T9 – NetworkPolicy chặn chéo namespace
kubectl -n $NS run np-test --rm -it --image=busybox:1.36 --restart=Never -- \
  wget -T 5 -qO- http://identity-service.datn-prod.svc.cluster.local:8082/actuator/health      # timeout

# T10 – đổi cấu hình thì checksum đổi
kubectl -n $NS get deploy work-service -o jsonpath='{.spec.template.metadata.annotations}'; echo

# T11 – cách ly Jenkins
sudo -u jenkins kubectl get pods -A ; sudo -u jenkins docker ps          # cả hai phải lỗi

# T12 – công cụ quản trị
curl -s -o /dev/null -w "%{http_code}\n" https://pgadmin-staging.example.com/                  # 401 (basic auth) hoặc 403 (ngoài whitelist)
curl -s -o /dev/null -w "%{http_code}\n" -u quantri:<mật-khẩu> https://pgadmin-staging.example.com/login   # 200

kubectl -n $NS top pods                                                   # đối chiếu ngân sách 02 §6
```

### 13.1. Kết quả chạy thử trước khi lên máy chủ (23/11/2026)

Chạy trên cluster `kind` tạm ở máy dev (Kubernetes 1.37, Helm 4.3.2, ingress-nginx 1.15.1), namespace `datn-staging`, HTTP và tên miền `*.localtest.me:18080` thay cho TLS. Image do chính `build-images.sh` build (không push); `datn-app` do `deploy.sh` cài bằng kubeconfig `ci-deployer` sinh từ `make-ci-kubeconfig.sh`.

| Kiểm tra | Kết quả |
|---|---|
| `helm lint` + `kubeconform -strict`, 2 chart × 2 môi trường | Đạt (31/19/28/19 tài nguyên hợp lệ) |
| Cài `datn-platform` | 8 pod Ready trong 66 giây, 0 restart (sau khi sửa Keycloak OOM, §14.2) |
| Issuer Keycloak | Đúng URL công khai `KC_HOSTNAME_URL` |
| RBAC `ci-deployer` (T6) | `patch deployments`, `delete configmaps`, `get replicasets` = yes; `get secrets`, `delete statefulsets`, `patch pvc`, namespace khác = no |
| Cài lần đầu bằng `deploy.sh` | Đạt sau 80 giây, smoke test UP |
| Luồng nghiệp vụ qua Ingress | Đăng ký → email xác thực tới Mailpit → xác thực → đăng nhập → identity/work/notification nhận token; tạo workspace, tạo dự án đều 201; token giả 401 |
| T1 zero downtime (rolling restart gateway + work) | 371/371 request trả 200 |
| T2/T3 deploy lần 2 | Nhận `PREV`, nâng revision, smoke test đạt |
| Smoke test lỗi | `deploy.sh` tự `helm rollback` về `PREV` bằng quyền `ci-deployer`, chẩn đoán không bị `forbidden` |
| Rollout lỗi (image không tồn tại) | Helm tự rollback (`--rollback-on-failure`), 197/197 request vẫn 200 trong lúc lỗi |
| T9 NetworkPolicy | Cùng namespace gọi được; từ `datn-prod` sang identity/Postgres của staging bị chặn |
| T12 công cụ quản trị | pgAdmin, Mailpit: 401 khi không có basic auth, 200 khi có |
| `helm uninstall datn-platform` | PVC của StatefulSet và `pgadmin-data` được giữ lại |

**Trivy** (quét riêng 6 image, 24/11/2026): cả 6 có lỗ hổng CRITICAL đã có bản vá. 5 image Java do thư viện của Spring Boot 3.5.4 (netty-handler, tomcat-embed-core, spring-security-web, bouncycastle, spring-webmvc/webflux; riêng spring-webmvc/webflux chỉ có bản vá ở Spring 7); frontend do gói OS của `nginx:1.27-alpine` (brotli, openssl). Nhóm quyết định Trivy **chỉ báo cáo, không chặn** (`TRIVY_EXIT_CODE=0` trong `build-images.sh`). Nên làm riêng: nâng Spring Boot lên bản vá 3.5.x mới nhất và đổi image nền frontend sang bản nginx alpine mới hơn.

Chưa kiểm được ở máy dev: TLS/certbot, Nginx máy chủ và whitelist IP thật, Docker rootless của Jenkins, push GHCR, Jenkins thật (job, credential, webhook, duyệt prod).

---

## 14. Vận hành và xử lý sự cố

### 14.1. Sao lưu DB: `infrastructure/scripts/backup-db.sh`

```bash
#!/usr/bin/env bash
# Sao lưu 2 DB của một namespace (03 §14.1). Chạy bằng kubeconfig admin (cần quyền exec vào StatefulSet).
# Luôn khôi phục CẢ HAI DB cùng thời điểm: user id toàn hệ thống là Keycloak sub.
# Cron: 0 2 * * * /opt/datn/infrastructure/scripts/backup-db.sh datn-prod >> /var/backups/datn/backup.log 2>&1
set -euo pipefail
NS="${1:-datn-prod}"
DIR="${BACKUP_DIR:-/var/backups/datn}/$NS"; TS=$(date +%Y%m%d-%H%M%S)
KEEP_DAYS="${KEEP_DAYS:-7}"
mkdir -p "$DIR"

dump() {  # dump <pod> <user> <db>
  local out="$DIR/$3-$TS.dump"
  # Ghi ra file tạm trước: lỗi giữa chừng thì không để lại bản dump hỏng mang tên hợp lệ
  kubectl -n "$NS" exec "$1" -- pg_dump -U "$2" -d "$3" -Fc > "$out.part"
  mv "$out.part" "$out"
  echo "OK $out ($(du -h "$out" | cut -f1))"
}
dump postgres-app-0      postgres datn_main_db
dump postgres-keycloak-0 kc_user  keycloak
find "$DIR" -name '*.dump' -mtime +"$KEEP_DAYS" -delete
```

```bash
sudo mkdir -p /var/backups/datn && sudo chown datnadmin: /var/backups/datn
crontab -e
# 0 2 * * * /opt/datn/infrastructure/scripts/backup-db.sh datn-prod >> /var/backups/datn/backup.log 2>&1
```

Khôi phục: luôn khôi phục **cả hai** DB cùng thời điểm (user id = Keycloak `sub`).

```bash
kubectl -n datn-prod scale deploy -l datn/layer=app --replicas=0
kubectl -n datn-prod exec -i postgres-app-0      -- pg_restore -U postgres -d datn_main_db --clean --if-exists < datn_main_db-XXXX.dump
kubectl -n datn-prod exec -i postgres-keycloak-0 -- pg_restore -U kc_user  -d keycloak     --clean --if-exists < keycloak-XXXX.dump
kubectl -n datn-prod rollout restart deploy/keycloak
# Bật lại app đúng số replicas: chạy datn-deploy (ACTION=deploy, SHA đang chạy) hoặc helm upgrade lại
```

### 14.2. Bảng xử lý sự cố

| Triệu chứng | Nguyên nhân | Cách xử lý |
|---|---|---|
| `Error: UPGRADE FAILED: another operation (install/upgrade/rollback) is in progress` | Lần deploy trước bị ngắt giữa chừng | `helm -n $NS history datn-app`; `helm -n $NS rollback datn-app <revision deployed gần nhất>` (driver configmap) |
| Jenkins: `rendered manifests contain a resource that already exists ... invalid ownership metadata` | Lần cài đầu không dùng `HELM_DRIVER=configmap`, hoặc tài nguyên tạo bằng `kubectl apply` | Quản trị viên: `helm -n $NS uninstall datn-app` (driver đã dùng khi cài), rồi cài lại theo §7 |
| Jenkins: `secrets is forbidden: User "system:serviceaccount:…:ci-deployer"` | Quên `HELM_DRIVER=configmap` | `deploy.sh` đã export; kiểm tra lệnh helm chạy tay trong Jenkinsfile |
| `UPGRADE FAILED: … StatefulSet … spec: Forbidden: updates to statefulset spec for fields other than …` | Sửa `storage`/selector của StatefulSet trong values platform | Trả lại giá trị cũ; muốn tăng dung lượng thì làm thủ tục riêng (backup, xoá StatefulSet `--cascade=orphan`, sửa PVC) |
| Pod `Pending`, `Insufficient memory` / `exceeded quota` | Hết RAM node hoặc quota | `kubectl describe node minikube`; giảm replicas; tạm tắt pgAdmin/Mailpit staging; tắt staging |
| `CreateContainerConfigError` | Thiếu Secret hoặc key (`app-secrets`, `PGADMIN_*`, `admin-basic-auth`…) | `kubectl describe pod`; bổ sung key: `kubectl -n $NS create secret generic app-secrets --from-env-file=... --dry-run=client -o yaml \| kubectl apply -f -` |
| `ImagePullBackOff` | PAT hết hạn, image chưa có, owner viết hoa | Tạo lại `ghcr-pull`; `kubectl -n $NS get sa datn-app -o yaml` |
| Spring `CrashLoopBackOff`, log `Connection refused postgres-app` | DB chưa Ready, sai mật khẩu | `kubectl logs <pod> -c wait-deps`; Postgres chỉ nhận mật khẩu ở lần khởi tạo đầu, đổi về sau phải `ALTER USER` |
| `Liveness probe failed: 401` | Chưa mở `/actuator/health/**` | §3.2 |
| `OOMKilled` | Limit thấp | Tăng `backend.<svc>.resources.limits.memory` trong values |
| Keycloak `OOMKilled` ngay sau dòng `Updating the server image` | Lệnh `start` tự build lại Quarkus, mặc định heap 70% RAM | Giữ `keycloak.heapOpts` (heap 50%) và limit 1280Mi (§4.2); đã gặp khi chạy thử với limit 1Gi |
| Smoke test báo `không phải health JSON UP` | Route `/identity`… hỏng nên rơi về frontend (nginx của SPA trả `index.html` mã 200) | `kubectl -n $NS get ing datn-app -o yaml`; kiểm tra Ingress và route của gateway |
| Mọi API 401 sau đăng nhập | `iss` ≠ `KEYCLOAK_ISSUER_URI` | So token (jwt.io) với `kubectl -n $NS get cm env-config -o yaml`; kiểm tra `keycloak.hostnameUrl` |
| POST/PUT/WebSocket 403 | CORS: gateway thấy `http` | `use-forwarded-headers` (§2); header `X-Forwarded-Proto/Port` ở Nginx máy chủ |
| Keycloak "HTTPS required" | `sslRequired=external` mà chạy HTTP | TLS (§8) hoặc `sslRequired=none` (§6.2) |
| Admin Console Keycloak trắng hoặc 401 liên tục | Đặt basic auth lên `/admin` | Chỉ dùng `keycloak.adminAllowCidrs` (01 §5.17) |
| pgAdmin: whitelist chặn cả IP hợp lệ | ingress-nginx không thấy IP thật | Kiểm tra `use-forwarded-headers`, Nginx máy chủ gửi `X-Forwarded-For $remote_addr` |
| pgAdmin `Permission denied: '/var/lib/pgadmin/...'` | Sai `fsGroup` | `fsGroup: 5050` (§4.15) |
| pgAdmin không có sẵn server | `servers.json` chỉ nạp ở lần đầu | Thêm tay trong UI, hoặc xoá PVC `pgadmin-data` rồi cài lại (mất cấu hình pgAdmin) |
| Kafka `CrashLoopBackOff`, log lạ về `port` | Thiếu `enableServiceLinks: false` | `platform.podDefaults` |
| Không có thông báo realtime | WS bị cắt | DevTools: WS `/notification/ws` = 101; timeout Nginx/Ingress |
| Mất kết nối sau khởi động lại máy | minikube chưa chạy / IP đổi | `systemctl status minikube`; `minikube ip` khớp Nginx |
| Sự cố về Jenkins | — | [04 §16](04_HUONG_DAN_SETUP_JENKINS.md#16-xử-lý-sự-cố) |

### 14.3. Lệnh hằng ngày

```bash
export HELM_DRIVER=configmap                               # khi xem release datn-app
helm -n datn-prod list ; helm -n datn-prod history datn-app
helm -n datn-prod get values datn-app                      # values + image.tag đang chạy
kubectl -n datn-prod get pods,deploy,sts,ing
kubectl -n datn-prod logs deploy/work-service -f --tail=100
unset HELM_DRIVER; helm -n datn-prod list                  # release datn-platform (driver secret)
minikube ssh -- df -h /
```
