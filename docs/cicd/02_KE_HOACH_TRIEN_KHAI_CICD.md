# 02 — Kế hoạch triển khai CI/CD lên Minikube (1 máy chủ, Jenkins + Helm)

> Lý do lựa chọn: [01](01_NGHIEN_CUU_CICD_KUBERNETES.md). Lệnh, Helm chart, pipeline: [03](03_HUONG_DAN_TRIEN_KHAI.md). Setup Jenkins: [04](04_HUONG_DAN_SETUP_JENKINS.md).

---

## 1. Mục tiêu và tiêu chí hoàn thành

| # | Mục tiêu | Tiêu chí kiểm chứng | Module |
|---|---|---|---|
| O1 | Mỗi PR vào `main` được Jenkins build và test | PR có check `continuous-integration/jenkins/pr-merge`; check đỏ thì không merge được (branch protection) | — |
| O2 | Merge `main` thì tự deploy **staging** | `helm -n datn-staging history datn-app` có revision mới; image tag = SHA commit | M2, M12 |
| O3 | Tag `vX.Y.Z` thì deploy **prod** sau khi duyệt, **cùng image với staging** (không build lại) | Job `DATN/release/datn-deploy` dừng ở bước duyệt; digest ở prod trùng staging | M12, M7 |
| O4 | Deploy không gián đoạn | Vòng `curl` liên tục trong lúc deploy không có lỗi | M5 |
| O5 | Tự rollback khi bản mới hỏng | Bản lỗi cố ý: `helm upgrade --rollback-on-failure` tự quay lại; `helm history` có dòng `rolled back` | M5, M12 |
| O6 | Tự phục hồi | Xoá pod hoặc kill tiến trình thì pod tự Ready lại | M5 |
| O7 | CI chỉ có quyền tối thiểu | `ci-deployer`: `patch deployments` = yes; `get secrets`, `delete statefulsets`, namespace khác = no. Jenkins: không có kubeconfig admin, không chạm được Docker gốc | M7 |
| O8 | Image private, non-root, đã quét | Pod kéo được qua `ghcr-pull`; `id` trong container = 10001; Trivy chạy trước push | M7 |
| O9 | DNS nội bộ + Ingress | Không có NodePort/LoadBalancer (`services.nodeports: 0` trong quota); URL nội bộ dạng `http://<svc>:<port>` | M9 |
| O10 | Không service nào ăn hết RAM | Mọi container có requests/limits; quota + LimitRange; Jenkins bị giới hạn bằng systemd | M3 |
| O11 | Công cụ quản trị truy cập được từ ngoài, an toàn | `https://pgadmin.<domain>` (và `mail-staging`, `jenkins`) hỏi basic auth, chặn IP ngoài danh sách, rồi mới tới trang đăng nhập riêng; pgAdmin thấy sẵn 2 server DB | M9, M7 |

---

## 2. Kiến trúc đích

```
 Lập trình viên ── git push / PR / tag ──► GitHub (repo) ── webhook ──┐
                                                                     ▼
┌──────────────────────────── Máy chủ Linux (1 máy) ─────────────────────────────────────┐
│  Nginx máy chủ :80/:443 (TLS Let's Encrypt)                                             │
│    app|sso|pgadmin|staging|sso-staging|pgadmin-staging|mail-staging ─► http://$(minikube ip)
│    jenkins.<domain> ─► 127.0.0.1:8080                                                   │
│                                                                                         │
│  Jenkins (systemd, user jenkins, KHÔNG thuộc hệ thống ứng dụng)                         │
│    Multibranch DATN/ci/datn : mvn verify · npm build · helm lint/template · kubeconform  │
│        main → docker build (rootless) → Trivy → push GHCR → helm upgrade staging        │
│        tag  → gắn tag phiên bản cho image → kích hoạt DATN/release/datn-deploy          │
│    DATN/release/datn-deploy : duyệt (input) → helm upgrade prod / helm rollback         │
│    Credential: kubeconfig của SA ci-deployer (staging ở folder ci; prod chỉ ở release)  │
│                                                                                         │
│  ┌───────────────────────── minikube (driver docker, CNI calico) ───────────────────┐   │
│  │ ingress-nginx                                                                    │   │
│  │ ns datn-staging                              ns datn-prod                        │   │
│  │  release datn-app (Jenkins, driver configmap) release datn-app                   │   │
│  │   gateway identity work workspace             (gateway, frontend: 2 replicas)    │   │
│  │   notification frontend                                                          │   │
│  │  release datn-platform (quản trị viên)         release datn-platform             │   │
│  │   postgres-app postgres-keycloak keycloak      … + SMTP thật, không Mailpit      │   │
│  │   kafka redis minio mailpit pgadmin                                              │   │
│  │   RBAC ci-deployer · SA datn-app · Quota · LimitRange · NetworkPolicy            │   │
│  └──────────────────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────────────┘
                     ▲ docker push (Jenkins)        │ image pull (ghcr-pull)
                     └────────── GHCR (private) ◄───┘
```

### Tên miền

| Môi trường | Ứng dụng | Keycloak | pgAdmin | Khác |
|---|---|---|---|---|
| staging | `staging.<domain>` | `sso-staging.<domain>` | `pgadmin-staging.<domain>` | `mail-staging.<domain>` (Mailpit) |
| prod | `app.<domain>` | `sso.<domain>` | `pgadmin.<domain>` | `minio.<domain>` (tuỳ chọn, tắt mặc định) |
| chung | `jenkins.<domain>` (ngoài cluster) | | | |

Không có tên miền thì dùng `nip.io` (ví dụ `pgadmin.203.0.113.10.nip.io`) với HTTP. **Không khuyến nghị mở pgAdmin qua HTTP**: dùng `kubectl port-forward` qua SSH tunnel thay thế.

---

## 3. Cấu trúc thư mục sẽ thêm

```
Jenkinsfile                                   # Multibranch: CI + build + staging + kích hoạt release
infrastructure/
  jenkins/Jenkinsfile.deploy                  # Job duyệt/deploy prod, redeploy, helm rollback
  k8s/namespaces.yaml
  helm/
    datn-platform/                            # QUẢN TRỊ VIÊN cài (kubeconfig admin)
      Chart.yaml  values.yaml  values-staging.yaml  values-prod.yaml
      templates/  _helpers.tpl rbac-ci.yaml serviceaccount-app.yaml quota.yaml networkpolicy.yaml
                  postgres.yaml keycloak.yaml kafka.yaml redis.yaml minio.yaml mailpit.yaml pgadmin.yaml
    datn-app/                                 # JENKINS cài (ci-deployer, HELM_DRIVER=configmap)
      Chart.yaml  values.yaml  values-staging.yaml  values-prod.yaml
      files/config/<service>/application-k8s.yaml
      templates/  _helpers.tpl configmap-env.yaml configmap-spring.yaml backend.yaml frontend.yaml ingress.yaml
  scripts/
    make-ci-kubeconfig.sh  build-images.sh  deploy.sh  backup-db.sh
backend/.dockerignore
backend/workspace-service/Dockerfile
```

---

## 4. Luồng nhánh và pipeline

| Sự kiện | Job Jenkins | Kết quả |
|---|---|---|
| Mở/cập nhật PR vào `main` | `DATN/ci/datn` → nhánh `PR-<n>`: backend test, frontend lint/build, `helm lint` + `helm template \| kubeconform` | Check trên PR |
| Merge vào `main` | `DATN/ci/datn` → `main`: các bước trên + build 6 image (tuần tự, rootless) → Trivy → push `:<sha>` → `deploy.sh staging` (`helm upgrade --install --rollback-on-failure`) → smoke test | Staging chạy SHA mới |
| Push tag `v1.2.0` (trỏ vào commit đã qua `main`) | `DATN/ci/datn` → `v1.2.0`: kiểm tra image `:<sha>` có trên GHCR, gắn thêm tag `:v1.2.0` → kích hoạt `DATN/release/datn-deploy` (TARGET=production) | Job release chờ duyệt |
| Người phụ trách bấm **Deploy** | `DATN/release/datn-deploy` | Prod chạy cùng image với staging |
| Rollback / deploy lại | `datn-deploy` → *Build with Parameters*: `ACTION=deploy` + `IMAGE_TAG=<sha cũ>`, hoặc `ACTION=helm-rollback` (+ `REVISION`) | Quay về bản cũ, có ghi lịch sử trong Jenkins và `helm history` |

Quy ước: nhánh `feat/*`, `fix/*` → PR vào `main`; branch protection cho `main` (bắt buộc check Jenkins xanh, ít nhất 1 review).

---

## 5. Cấu hình, Secret và Credential

### 5.1. Trong cluster

| Loại | Tên | Nội dung | Ai tạo |
|---|---|---|---|
| ConfigMap | `env-config` | URL công khai, issuer, SMTP, cờ cookie, sinh từ `values-<env>.yaml` | Helm (`datn-app`) |
| ConfigMap | `<svc>-config` | `application-k8s.yaml` (`files/config/`) | Helm (`datn-app`) |
| ConfigMap | `pgadmin-servers` | `servers.json`: khai sẵn `postgres-app`, `postgres-keycloak` | Helm (`datn-platform`) |
| ConfigMap | `sh.helm.release.v1.datn-app.vN` | Lịch sử release `datn-app` (driver configmap) | Helm |
| Secret | `app-secrets` | `DB_PASSWORD`, `KC_DB_PASSWORD`, `KC_ADMIN_PASSWORD`, `KEYCLOAK_CLIENT_SECRET`, `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `PGADMIN_EMAIL`, `PGADMIN_PASSWORD` | Quản trị viên, **không commit** |
| Secret | `keycloak-realm` | `realm.json` đã sửa redirect URI và client secret | Quản trị viên |
| Secret | `ghcr-pull` | Kéo image GHCR (PAT `read:packages`) | Quản trị viên |
| Secret | `admin-basic-auth` | File `htpasswd` cho Ingress của công cụ quản trị | Quản trị viên |
| Secret | `ci-deployer-token` | Token SA | Chart `datn-platform` (Kubernetes tự điền) |
| Secret | `sh.helm.release.v1.datn-platform.vN` | Lịch sử release platform (driver secret, CI không đọc được) | Helm |

### 5.2. Trong Jenkins ([04 §10](04_HUONG_DAN_SETUP_JENKINS.md#10-folder-và-credentials))

| ID | Kind | Folder |
|---|---|---|
| `github-pat` | Username/password (PAT `repo`) | `DATN` |
| `ghcr-push` | Username/password (PAT `write:packages`) | `DATN/ci` |
| `kubeconfig-staging` | Secret file | `DATN/ci`, `DATN/release` |
| `kubeconfig-prod` | Secret file | **chỉ** `DATN/release` |
| `github-webhook-secret` | Secret text | Toàn cục |

---

## 6. Ngân sách tài nguyên và cấu hình máy chủ

### 6.1. Requests / limits mỗi container (CPU: millicore, RAM: MiB)

| Thành phần | req CPU | req RAM | lim CPU | lim RAM | Replicas staging / prod |
|---|---:|---:|---:|---:|---|
| api-gateway | 200 | 384 | 1000 | 640 | 1 / 2 |
| identity-service | 200 | 448 | 1000 | 768 | 1 / 1 |
| work-service | 250 | 512 | 1500 | 1024 | 1 / 1 |
| workspace-service | 200 | 384 | 1000 | 640 | 1 / 1 |
| notification-service | 200 | 384 | 1000 | 640 | 1 / 1 |
| frontend | 20 | 32 | 200 | 128 | 1 / 2 |
| keycloak | 250 | 768 | 1500 | 1024 | 1 / 1 |
| postgres-app | 100 | 256 | 1000 | 1024 | 1 / 1 |
| postgres-keycloak | 50 | 128 | 500 | 512 | 1 / 1 |
| kafka | 200 | 768 | 1000 | 1024 | 1 / 1 |
| redis | 20 | 64 | 200 | 256 | 1 / 1 |
| minio | 50 | 256 | 500 | 512 | 1 / 1 |
| **pgadmin** | 50 | 192 | 500 | 512 | 1 / 1 |
| mailpit | 10 | 32 | 100 | 128 | 1 / 0 |
| **Tổng requests staging** | **≈1,8 core** | **≈4,5 GiB** | | | |
| **Tổng requests prod** | **≈2,0 core** | **≈4,9 GiB** | | | |

**ResourceQuota mỗi namespace:** `requests.cpu: 4`, `requests.memory: 8Gi`, `limits.cpu: 24`, `limits.memory: 16Gi`, `pods: 30`, `persistentvolumeclaims: 8`, `services.nodeports: 0`, `services.loadbalancers: 0`.
Quota là trần, không giữ chỗ, nên phải tính cho **lúc rolling update** (mỗi Deployment của `datn-app` thêm 1 pod): prod lên ≈3,1 core / 7,0 GiB requests và ≈17,8 core / 13,0 GiB limits. Trần cũ (3 core requests, 16 core limits) sẽ chặn pod mới với lỗi `exceeded quota` giữa lúc deploy.
**LimitRange:** mặc định 50m/64Mi → 500m/256Mi; tối đa 2 core/2Gi mỗi container.

### 6.2. Jenkins (ngoài cluster)

| Thành phần | RAM | Giới hạn |
|---|---|---|
| Jenkins controller | khoảng 1,3 GB (`-Xmx1g`) | `MemoryMax=4G` cho cả service (gồm `mvn verify`, `npm`, `trivy`) |
| Docker rootless (`docker build`) | đỉnh khoảng 2–3 GB | `MemoryMax=3G`, `CPUQuota=200%` |
| Maven trong `docker build` | ≤ 768 MB | `MAVEN_OPTS=-Xmx768m` trong Dockerfile |

### 6.3. Cấu hình máy chủ

| Kịch bản | CPU | RAM | Ổ đĩa | `minikube start` | Jenkins |
|---|---|---|---|---|---|
| **Khuyến nghị** | 8 vCPU | 32 GB | 150 GB SSD | `--cpus=6 --memory=14g --disk-size=60g` | 2 executor |
| Tối thiểu (staging + prod) | 8 vCPU | 16 GB + swap 4 GB | 120 GB | `--cpus=6 --memory=11g --disk-size=60g` | 1 executor. Sát giới hạn: có thể đặt `pgadmin.enabled=false` ở staging hoặc tắt staging ngoài giờ |
| Chỉ prod (không khuyến nghị) | 4 vCPU | 8 GB + swap 4 GB | 80 GB | `--cpus=3 --memory=6g --disk-size=40g` | 1 executor, build chậm |

---

## 7. Các giai đoạn triển khai

### Giai đoạn 0. Khảo sát (0,5 ngày)
- Chạy test backend bằng JDK 21: `docker run --rm -v "$PWD/backend:/src" -w /src maven:3.9.9-eclipse-temurin-21-alpine mvn -B verify`.
- Chốt tên miền, mở 80/443; kiểm tra Jenkins đã có trên máy chủ ([04 §17](04_HUONG_DAN_SETUP_JENKINS.md#17-phụ-lục-jenkins-đã-có-sẵn-trên-máy-chủ)).
- Tạo 3 PAT classic: `jenkins-repo`, `jenkins-ghcr`, `cluster-ghcr`.
- **Đầu ra:** danh sách test đỏ (nếu có); domain; PAT.

### Giai đoạn 1. Sửa mã nguồn (1–1,5 ngày), PR riêng
Actuator ×4; mở `/actuator/health/**` (gateway thêm `/*/actuator/health/**`); Dockerfile workspace-service; Dockerfile non-root + `MAVEN_OPTS` + BuildKit cache; frontend `node:22-alpine`; `backend/.dockerignore`; CORS và cookie Secure cấu hình được.
**Đầu ra:** compose dev vẫn chạy; `/actuator/health/readiness` = UP.

### Giai đoạn 2. Máy chủ và minikube (0,5 ngày)
Docker, kubectl, minikube, **helm**, Nginx, certbot; `minikube start --cni=calico`; addon `ingress`, `metrics-server`; `use-forwarded-headers`; systemd unit cho minikube.
**Đầu ra:** node Ready; `kubectl top nodes` có số liệu; `helm version` chạy.

### Giai đoạn 3. Chart `datn-platform` (2 ngày), M2, M3, M7, M9, M12
Viết chart; tạo Secret; `helm upgrade --install datn-platform` cho từng namespace.
**Đầu ra:** mọi pod Ready; `issuer` của `.well-known` đúng URL công khai; `kubectl auth can-i` của `ci-deployer` đúng như O7.

### Giai đoạn 4. Chart `datn-app` (1,5 ngày), M2, M5, M12
Template `range` cho 5 backend, frontend, ConfigMap có checksum, Ingress; values theo môi trường; cài lần đầu bằng tay (`HELM_DRIVER=configmap`, image `bootstrap` nạp bằng `minikube image load`).
**Đầu ra:** luồng nghiệp vụ chạy đủ trên staging (đăng nhập, dự án, công việc, đính kèm, thông báo realtime, email Mailpit).

### Giai đoạn 5. Ingress công khai, TLS, công cụ quản trị (1 ngày), M9, M7
Nginx máy chủ (TLS, `X-Forwarded-For $remote_addr`); `admin-basic-auth`; pgAdmin, Mailpit qua Ingress; whitelist IP; giới hạn `/admin` của Keycloak; redirect URI Google.
**Đầu ra:** O11; đăng nhập Google chạy trên domain thật.

### Giai đoạn 6. Jenkins (1 ngày), theo [04](04_HUONG_DAN_SETUP_JENKINS.md)
Cài hoặc kiểm tra Jenkins; Docker rootless; công cụ (kubectl, helm, kubeconform, trivy); Nginx `jenkins.<domain>`; plugin; bảo mật; tools; folder và credential.
**Đầu ra:** E7 của 04 đạt (Jenkins không có quyền admin cluster).

### Giai đoạn 7. CI (1 ngày)
`Jenkinsfile` (stage CI), job Multibranch, webhook, branch protection.
**Đầu ra:** PR thử có check xanh.

### Giai đoạn 8. CD, rollback, phát hành (1,5 ngày), M5, M7, M12
`build-images.sh`, `deploy.sh`, stage build/deploy staging, tag → promote, `Jenkinsfile.deploy` (duyệt, deploy, `helm rollback`).
**Đầu ra:** O2, O3, O5.

### Giai đoạn 9. Vận hành và nghiệm thu (1 ngày)
Backup `pg_dump` + `JENKINS_HOME`; chạy kịch bản §9; runbook ([03 §14](03_HUONG_DAN_TRIEN_KHAI.md#14-vận-hành-và-xử-lý-sự-cố), [04 §16](04_HUONG_DAN_SETUP_JENKINS.md#16-xử-lý-sự-cố)).

**Tổng: khoảng 11–12 ngày công.**

---

## 8. Rủi ro và giảm thiểu

| Rủi ro | Khả năng | Ảnh hưởng | Giảm thiểu |
|---|---|---|---|
| Build của Jenkins lấn RAM/CPU của prod | **Cao** (cùng máy) | Pod OOMKilled hoặc chậm khi đang build | 1 executor; `MemoryMax`/`CPUQuota`; `MAVEN_OPTS`; build tuần tự; máy 32 GB |
| Jenkins bị chiếm quyền | Thấp–TB | Lộ credential staging/prod; nếu Jenkins thuộc nhóm docker thì mất cả máy chủ | Docker rootless; không kubeconfig admin; UI sau TLS + giới hạn IP/SSH tunnel; vá plugin hằng tháng |
| PR đọc credential qua `Jenkinsfile` | TB | Lộ kubeconfig staging | Credential prod chỉ ở folder release; fork PR Trust: Nobody; review thay đổi `Jenkinsfile` |
| pgAdmin bị dò mật khẩu | TB | Toàn quyền DB | Basic auth + whitelist IP + mật khẩu pgAdmin mạnh; Nginx máy chủ ghi đè `X-Forwarded-For` |
| Test backend đỏ trên JDK 21 | TB | Chặn CD | Giai đoạn 0 |
| Sai issuer JWT, lỗi 401 hàng loạt | TB | Không đăng nhập được | `KC_HOSTNAME_URL`; kiểm tra `.well-known` |
| CORS/WebSocket 403 | TB | Không ghi, không realtime | `use-forwarded-headers`, `X-Forwarded-Proto/Port` |
| Release Helm "kẹt" (`another operation is in progress`) | TB | Không deploy được | `lock()` trong Jenkins; `helm rollback` về revision tốt; [03 §14](03_HUONG_DAN_TRIEN_KHAI.md#14-vận-hành-và-xử-lý-sự-cố) |
| Cài lần đầu sai driver Helm | TB | Jenkins báo `resource already exists` | Luôn `HELM_DRIVER=configmap` cho `datn-app` ([03 §7](03_HUONG_DAN_TRIEN_KHAI.md#7-cài-ứng-dụng-lần-đầu-bằng-tay)) |
| Mất dữ liệu (`minikube delete`, hỏng đĩa) | Thấp | Mất DB | Backup hằng ngày ra ngoài minikube |
| Migration không tương thích ngược | TB | Pod cũ lỗi khi chuyển | Expand → contract; review migration |
| ingress-nginx không còn được phát triển | Chắc chắn (đã thông báo) | Không có bản vá mới | Chấp nhận cho đồ án; nâng cao: Gateway API |
| Docker Hub giới hạn số lần pull | Thấp | Build hoặc pod lỗi pull | Ghim tag; không `--pull` mỗi build |

---

## 9. Kịch bản nghiệm thu (minh chứng cho báo cáo)

| # | Kịch bản | Cách làm | Kỳ vọng |
|---|---|---|---|
| T1 | Zero-downtime | Vòng `curl` liên tục vào `/work/actuator/health` trong lúc merge PR | Toàn `200` |
| T2 | Tự rollback | PR đổi `readinessProbe` của work-service sang đường dẫn không tồn tại, rồi merge | Stage deploy đỏ; `helm history` có revision `failed` rồi `rolled back`; pod chạy bản cũ |
| T3 | Rollback tay | `datn-deploy`: `ACTION=helm-rollback`, `TARGET=staging` | Về revision trước |
| T4 | Self-healing | `kubectl delete pod -l app.kubernetes.io/name=identity-service` | Pod mới Ready |
| T5 | Liveness | `kubectl exec … kill 1` | `RESTARTS` +1 |
| T6 | RBAC | `kubectl auth can-i --as=system:serviceaccount:datn-staging:ci-deployer …` | patch deployments=yes; get secrets / delete statefulsets / ns prod = no |
| T7 | Quota | Deployment `stress` request 8Gi | `exceeded quota` |
| T8 | Duyệt prod | Push tag `v1.0.0` | `datn-deploy` chờ duyệt; người ngoài `submitter` không duyệt được; duyệt xong prod = digest staging |
| T9 | NetworkPolicy | Pod staging gọi `identity-service.datn-prod...` | Timeout |
| T10 | Đổi cấu hình | Sửa `values-staging.yaml` (`mail.from`), merge | `checksum/env` đổi nên Deployment tự rolling update |
| T11 | Cách ly Jenkins | `sudo -u jenkins kubectl get pods -A`; `sudo -u jenkins docker ps` | Cả hai lỗi |
| T12 | Công cụ quản trị | Mở `https://pgadmin-staging.<domain>` từ IP ngoài danh sách, rồi từ IP trong danh sách | Ngoài danh sách: 403; trong: hỏi basic auth rồi tới đăng nhập pgAdmin; thấy 2 server DB của **đúng** namespace |

---

## 10. Nâng cao (sau phần chính)
- **GitOps (Argo CD)** quản lý release Helm từ Git; Jenkins chỉ build và commit tag.
- **Sealed Secrets / helm-secrets (SOPS)**: commit secret đã mã hoá.
- **Giám sát:** `kube-prometheus-stack` (Helm) + Micrometer; plugin Prometheus cho Jenkins.
- **Jenkins agent trên máy khác** để build không ăn tài nguyên prod.
- **Jenkins Configuration as Code** (JCasC) lưu cấu hình Jenkins vào Git.
- **HPA** cho api-gateway; **Gateway API** thay ingress-nginx; ký image bằng cosign.
