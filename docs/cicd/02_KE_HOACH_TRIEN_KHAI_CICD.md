# 02 — Kế hoạch triển khai CI/CD lên Minikube (1 máy chủ, GitHub Actions + Helm)

> Lý do lựa chọn: [01](01_NGHIEN_CUU_CICD_KUBERNETES.md). Lệnh, Helm chart, workflow: [03](03_HUONG_DAN_TRIEN_KHAI.md). Cấu hình GitHub Actions: [04](04_HUONG_DAN_GITHUB_ACTIONS.md). Làm từ đầu trên EC2: [05](05_TRIEN_KHAI_TU_DAU_TREN_EC2.md).

---

## 1. Mục tiêu và tiêu chí hoàn thành

| # | Mục tiêu | Tiêu chí kiểm chứng | Module |
|---|---|---|---|
| O1 | Mỗi PR vào `main` được build và test | PR có các check `Backend`, `Frontend`, `Helm chart` của workflow `CI/CD`; check đỏ thì không merge được (branch protection) | — |
| O2 | Merge `main` thì tự deploy **staging** | `helm -n datn-staging history datn-app` có revision mới; image tag = SHA commit | M2, M12 |
| O3 | Tag `vX.Y.Z` thì deploy **prod** sau khi duyệt, **cùng image với staging** (không build lại) | Workflow `Release` dừng ở *Waiting for review* của Environment `production`; digest ở prod trùng staging | M12, M7 |
| O4 | Deploy không gián đoạn | Vòng `curl` liên tục trong lúc deploy không có lỗi | M5 |
| O5 | Tự rollback khi bản mới hỏng | Bản lỗi cố ý: `helm upgrade --rollback-on-failure` tự quay lại; `helm history` có dòng `Rollback to …` | M5, M12 |
| O6 | Tự phục hồi | Xoá pod hoặc kill tiến trình thì pod tự Ready lại | M5 |
| O7 | CI chỉ có quyền tối thiểu | `ci-deployer`: `patch deployments` = yes; `get secrets`, `delete statefulsets`, namespace khác = no. Khoá SSH của CI chỉ port-forward được tới API server, không có shell | M7 |
| O8 | Image private, non-root, đã quét | Pod kéo được qua `ghcr-pull`; `id` trong container = 10001; Trivy chạy trước push (báo cáo trong log workflow) | M7 |
| O9 | DNS nội bộ + Ingress | Không có NodePort/LoadBalancer (`services.nodeports: 0` trong quota); URL nội bộ dạng `http://<svc>:<port>` | M9 |
| O10 | Không service nào ăn hết RAM | Mọi container có requests/limits; quota + LimitRange | M3 |
| O11 | Công cụ quản trị truy cập được từ ngoài, an toàn | `pgadmin.<domain>` (và `mail-staging`) hỏi basic auth, chặn IP ngoài danh sách, rồi mới tới trang đăng nhập riêng; pgAdmin thấy sẵn 2 server DB | M9, M7 |

---

## 2. Kiến trúc đích

```
 Lập trình viên ── git push / PR / tag ──► GitHub (repo public)
                                              │
                         GitHub Actions (runner của GitHub, KHÔNG chạy trên máy chủ)
                           ci-cd.yml   : PR/main → mvn verify · npm build · helm lint · kubeconform
                                         main    → build 6 image song song → Trivy → push GHCR (GITHUB_TOKEN)
                                                 → deploy.yml (environment staging)
                           release.yml : tag v* → gắn tag phiên bản cho image → deploy.yml (environment
                                         production: chờ người duyệt bấm Approve)
                           deploy-manual.yml : chạy tay → deploy SHA cũ / helm rollback
                                              │
                    deploy.yml: ssh -L 127.0.0.1:8443:192.168.49.2:8443 tunnel@<server>
                                + helm/kubectl bằng secret KUBE_CONFIG (SA ci-deployer) của Environment
                                              │ SSH :22 (chỉ port-forward)
┌──────────────────────────── Máy chủ Linux (CHỈ chạy dự án) ──▼──────────────────────────┐
│  sshd: user tunnel (nologin, khoá chỉ permitopen 192.168.49.2:8443)                     │
│  Nginx máy chủ :80/:443                                                                 │
│    app|sso|pgadmin|staging|sso-staging|pgadmin-staging|mail-staging ─► http://$(minikube ip)
│                                                                                         │
│  ┌───────────────────────── minikube (driver docker, CNI calico) ───────────────────┐   │
│  │ API server 192.168.49.2:8443 · ingress-nginx                                     │   │
│  │ ns datn-staging                              ns datn-prod                        │   │
│  │  release datn-app (CI, driver configmap)      release datn-app                   │   │
│  │   gateway identity work workspace             (gateway, frontend: 2 replicas)    │   │
│  │   notification frontend                                                          │   │
│  │  release datn-platform (quản trị viên)         release datn-platform             │   │
│  │   postgres-app postgres-keycloak keycloak      … + SMTP thật, không Mailpit      │   │
│  │   kafka redis minio mailpit pgadmin                                              │   │
│  │   RBAC ci-deployer · SA datn-app · Quota · LimitRange · NetworkPolicy            │   │
│  └──────────────────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────────────┘
                     ▲ docker push (workflow)       │ image pull (ghcr-pull)
                     └────────── GHCR (private) ◄───┘
```

### Tên miền

| Môi trường | Ứng dụng | Keycloak | pgAdmin | Khác |
|---|---|---|---|---|
| staging | `staging.<domain>` | `sso-staging.<domain>` | `pgadmin-staging.<domain>` | `mail-staging.<domain>` (Mailpit) |
| prod | `app.<domain>` | `sso.<domain>` | `pgadmin.<domain>` | `minio.<domain>` (tuỳ chọn, tắt mặc định) |

Chưa có tên miền: dùng `<tên>.<IP-gạch-ngang>.sslip.io` với HTTP ([05](05_TRIEN_KHAI_TU_DAU_TREN_EC2.md)).

---

## 3. Cấu trúc thư mục

```
.github/workflows/
  ci-cd.yml                                   # PR + main: CI, build/push image, deploy staging
  release.yml                                 # tag vX.Y.Z: gắn tag image, deploy prod (chờ duyệt)
  deploy-manual.yml                           # chạy tay: deploy SHA bất kỳ / helm rollback
  deploy.yml                                  # reusable: đường hầm SSH + deploy.sh / helm rollback
infrastructure/
  k8s/namespaces.yaml
  helm/
    datn-platform/                            # QUẢN TRỊ VIÊN cài (kubeconfig admin)
      Chart.yaml  values.yaml  values-staging.yaml  values-prod.yaml
      templates/  _helpers.tpl rbac-ci.yaml serviceaccount-app.yaml quota.yaml networkpolicy.yaml
                  postgres.yaml keycloak.yaml kafka.yaml redis.yaml minio.yaml mailpit.yaml pgadmin.yaml
    datn-app/                                 # CI cài (ci-deployer, HELM_DRIVER=configmap)
      Chart.yaml  values.yaml  values-staging.yaml  values-prod.yaml
      files/config/<service>/application-k8s.yaml
      templates/  _helpers.tpl configmap-env.yaml configmap-spring.yaml backend.yaml frontend.yaml ingress.yaml
  scripts/
    make-ci-kubeconfig.sh  build-images.sh  deploy.sh  backup-db.sh  set-public-host.sh
backend/.dockerignore
backend/workspace-service/Dockerfile
```

---

## 4. Luồng nhánh và pipeline

| Sự kiện | Workflow → job | Kết quả |
|---|---|---|
| Mở/cập nhật PR vào `main` | `ci-cd.yml`: `Backend` (mvn verify), `Frontend` (lint không chặn, build), `Helm chart` (`helm lint` + `helm template \| kubeconform`) | Check trên PR |
| Merge vào `main` | `ci-cd.yml`: các job trên → `Image` (matrix 6 service, song song: build, Trivy, push `:<sha>` và `:main`) → `Deploy staging` (gọi `deploy.yml`, environment `staging`) → smoke test | Staging chạy SHA mới |
| Push tag `v1.2.0` (trỏ vào commit đã qua `main`) | `release.yml`: `Gắn tag phiên bản` (kiểm tra image `:<sha>` có trên GHCR, gắn thêm `:v1.2.0`) → `Deploy production` (environment `production`) | Chờ người duyệt |
| Người duyệt bấm **Approve and deploy** | `deploy.yml` (environment `production`) | Prod chạy cùng image với staging |
| Rollback / deploy lại | `deploy-manual.yml` → *Run workflow*: `action=deploy` + `image_tag=<sha cũ>`, hoặc `action=rollback` (+ `revision`) | Quay về bản cũ; lịch sử ở trang Actions và `helm history` |

Quy ước: nhánh `feat/*`, `fix/*` → PR vào `main`; branch protection cho `main` (bắt buộc 3 check CI xanh, ít nhất 1 review). Hai lần deploy cùng môi trường không chạy song song (`concurrency: deploy-<env>`).

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

### 5.2. Trong GitHub ([04](04_HUONG_DAN_GITHUB_ACTIONS.md))

| Loại | Tên | Phạm vi | Nội dung |
|---|---|---|---|
| Biến repo | `DEPLOY_SSH_HOST` | Repo | IP (hoặc tên) máy chủ |
| Biến repo | `DEPLOY_SSH_KNOWN_HOSTS` | Repo | Host key của máy chủ (`ssh-keyscan`), chống giả mạo |
| Secret repo | `DEPLOY_SSH_KEY` | Repo | Khoá riêng ed25519 của user `tunnel` (chỉ port-forward được tới API server) |
| Secret environment | `KUBE_CONFIG` | Environment `staging` | Kubeconfig SA `ci-deployer` của `datn-staging`, server `https://127.0.0.1:8443` |
| Secret environment | `KUBE_CONFIG` | Environment `production` | Kubeconfig SA `ci-deployer` của `datn-prod` |
| Token có sẵn | `GITHUB_TOKEN` | Mỗi lần chạy | Push GHCR (`permissions: packages: write`) |

Environment `staging`: chỉ nhánh `main`. Environment `production`: nhánh `main` + tag `v*`, **required reviewers** (trưởng nhóm), không cho người kích hoạt tự duyệt.

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
| keycloak | 250 | 768 | 1500 | 1280 | 1 / 1 |
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

### 6.2. Cấu hình máy chủ

Build chạy trên runner của GitHub, nên máy chủ chỉ phải gánh cluster: staging + prod ≈ 3,8 core / 9,4 GiB requests, cộng thành phần Kubernetes (calico, ingress, coredns, apiserver…) ≈ 1,2 core / 1 GiB, cộng phần dôi ra lúc rolling update ≈ 1,1 core / 2,1 GiB.

| Kịch bản | CPU | RAM | Ổ đĩa | `minikube start` |
|---|---|---|---|---|
| **Khuyến nghị** (ví dụ EC2 `t3.2xlarge`) | 8 vCPU | 32 GB | 100 GB SSD | `--cpus=6 --memory=14g --disk-size=60g` |
| Tối thiểu staging + prod (ví dụ `c6i.2xlarge`, `c7i-flex.2xlarge`) | 8 vCPU | 16 GB | 100 GB | `--cpus=7 --memory=13g --disk-size=60g`. Sát giới hạn: có thể đặt `pgadmin.enabled=false` ở staging hoặc tắt staging ngoài giờ |
| Chỉ một môi trường (ví dụ `t3.xlarge`) | 4 vCPU | 16 GB | 80 GB | `--cpus=3 --memory=9g --disk-size=40g` |

---

## 7. Các giai đoạn triển khai

### Giai đoạn 0. Khảo sát (0,5 ngày)
- Chạy test backend bằng JDK 21 (đã làm: xanh, trừ `IdentityServiceApplicationTests` cần Postgres thật).
- Chốt tên miền (hoặc tạm dùng sslip.io), máy chủ, Security Group.
- Rà lịch sử Git trước khi chuyển repo public; xoay secret thật (client secret Google).
- **Đầu ra:** danh sách test đỏ (nếu có); domain; repo sẵn sàng public.

### Giai đoạn 1. Sửa mã nguồn (1–1,5 ngày), PR riêng
Actuator ×4; mở `/actuator/health/**` (gateway thêm `/*/actuator/health/**`); Dockerfile workspace-service; Dockerfile non-root + `MAVEN_OPTS` + BuildKit cache; frontend `node:22-alpine`; `backend/.dockerignore`; CORS và cookie Secure cấu hình được.
**Đầu ra:** compose dev vẫn chạy; `/actuator/health/readiness` = UP.

### Giai đoạn 2. Máy chủ và minikube (0,5 ngày)
Docker, kubectl, minikube, **helm**, Nginx; `minikube start --cni=calico`; addon `ingress`, `metrics-server`; `use-forwarded-headers`; systemd unit cho minikube.
**Đầu ra:** node Ready; `kubectl top nodes` có số liệu; `helm version` chạy.

### Giai đoạn 3. Chart `datn-platform` (2 ngày), M2, M3, M7, M9, M12
Viết chart; tạo Secret; `helm upgrade --install datn-platform` cho từng namespace.
**Đầu ra:** mọi pod Ready; `issuer` của `.well-known` đúng URL công khai; `kubectl auth can-i` của `ci-deployer` đúng như O7.

### Giai đoạn 4. Chart `datn-app` (1,5 ngày), M2, M5, M12
Template `range` cho 5 backend, frontend, ConfigMap có checksum, Ingress; values theo môi trường.
**Đầu ra:** luồng nghiệp vụ chạy đủ trên staging (đăng nhập, dự án, công việc, đính kèm, thông báo realtime, email Mailpit).

### Giai đoạn 5. Ingress công khai, TLS, công cụ quản trị (1 ngày), M9, M7
Nginx máy chủ (TLS khi có tên miền, `X-Forwarded-For $remote_addr`); `admin-basic-auth`; pgAdmin, Mailpit qua Ingress; whitelist IP; giới hạn `/admin` của Keycloak; redirect URI Google.
**Đầu ra:** O11.

### Giai đoạn 6. GitHub Actions (0,5 ngày), theo [04](04_HUONG_DAN_GITHUB_ACTIONS.md)
User `tunnel` + khoá SSH bị giới hạn; kubeconfig `ci-deployer` trỏ `127.0.0.1:8443`; Environments `staging`/`production` (luật nhánh, required reviewers, secret `KUBE_CONFIG`); biến và secret repo; branch protection.
**Đầu ra:** T11 đạt (khoá SSH không mở được shell hay cổng khác).

### Giai đoạn 7. CI (0,5 ngày)
`ci-cd.yml` (job `Backend`, `Frontend`, `Helm chart`).
**Đầu ra:** PR thử có 3 check xanh.

### Giai đoạn 8. CD, rollback, phát hành (1 ngày), M5, M7, M12
Job `Image` (matrix, Trivy, GHCR), `deploy.yml` (đường hầm + `deploy.sh`), `release.yml`, `deploy-manual.yml`.
**Đầu ra:** O2, O3, O5.

### Giai đoạn 9. Vận hành và nghiệm thu (1 ngày)
Backup `pg_dump` + snapshot EBS; chạy kịch bản §9; runbook ([03 §14](03_HUONG_DAN_TRIEN_KHAI.md#14-vận-hành-và-xử-lý-sự-cố), [04 §8](04_HUONG_DAN_GITHUB_ACTIONS.md)).

**Tổng: khoảng 9–10 ngày công.**

---

## 8. Rủi ro và giảm thiểu

| Rủi ro | Khả năng | Ảnh hưởng | Giảm thiểu |
|---|---|---|---|
| Lộ khoá SSH của CI | Thấp | Kẻ tấn công mở được đường tới API server | Khoá chỉ `permitopen` đúng API server, không shell; vẫn cần thêm kubeconfig `ci-deployer` (quyền hẹp) mới làm được gì; xoay khoá khi nghi ngờ |
| Cổng 22 mở cho Internet | Chắc chắn (runner GitHub không có IP cố định) | Bị dò mật khẩu SSH | Chỉ xác thực bằng khoá (mặc định Ubuntu trên EC2); tuỳ chọn `fail2ban` |
| Workflow của nhánh/PR đọc secret | TB | Lộ kubeconfig | Secret quan trọng ở Environment có luật nhánh; prod cần duyệt; fork PR không nhận secret; không dùng `pull_request_target`; branch protection cho `main` |
| Repo public lộ secret trong lịch sử | TB | Lộ client secret Google cũ | Xoay secret trước khi public; secret thật chỉ ở GitHub Secrets / Kubernetes Secret |
| pgAdmin bị dò mật khẩu | TB | Toàn quyền DB | Basic auth + whitelist IP + mật khẩu pgAdmin mạnh; Nginx máy chủ ghi đè `X-Forwarded-For` |
| Sai issuer JWT, lỗi 401 hàng loạt | TB | Không đăng nhập được | `KC_HOSTNAME_URL`; kiểm tra `.well-known` |
| CORS/WebSocket 403 | TB | Không ghi, không realtime | `use-forwarded-headers`, `X-Forwarded-Proto/Port` |
| Release Helm "kẹt" (`another operation is in progress`) | TB | Không deploy được | `concurrency` theo môi trường; `helm rollback` về revision tốt; [03 §14](03_HUONG_DAN_TRIEN_KHAI.md#14-vận-hành-và-xử-lý-sự-cố) |
| Cài lần đầu sai driver Helm | TB | CI báo `resource already exists` | Luôn `HELM_DRIVER=configmap` cho `datn-app` |
| Mất dữ liệu (`minikube delete`, hỏng đĩa) | Thấp | Mất DB | Backup hằng ngày + snapshot EBS |
| Migration không tương thích ngược | TB | Pod cũ lỗi khi chuyển | Expand → contract; review migration |
| ingress-nginx không còn được phát triển | Chắc chắn (đã thông báo) | Không có bản vá mới | Chấp nhận cho đồ án; nâng cao: Gateway API |
| Docker Hub giới hạn số lần pull | Thấp | Build hoặc pod lỗi pull | Ghim tag |

---

## 9. Kịch bản nghiệm thu (minh chứng cho báo cáo)

| # | Kịch bản | Cách làm | Kỳ vọng |
|---|---|---|---|
| T1 | Zero-downtime | Vòng `curl` liên tục vào `/work/actuator/health` trong lúc merge PR | Toàn `200` |
| T2 | Tự rollback | PR đổi `readinessProbe` của work-service sang đường dẫn không tồn tại, rồi merge | Job `Deploy staging` đỏ; `helm history` có revision `failed` rồi `Rollback to …`; pod chạy bản cũ |
| T3 | Rollback tay | `deploy-manual.yml`: `action=rollback`, `target=staging` | Về revision trước |
| T4 | Self-healing | `kubectl delete pod -l app.kubernetes.io/name=identity-service` | Pod mới Ready |
| T5 | Liveness | `kubectl exec … kill 1` | `RESTARTS` +1 |
| T6 | RBAC | `kubectl auth can-i --as=system:serviceaccount:datn-staging:ci-deployer …` | patch deployments=yes; get secrets / delete statefulsets / ns prod = no |
| T7 | Quota | Deployment `stress` 3 pod × 2Gi | `exceeded quota` |
| T8 | Duyệt prod | Push tag `v1.0.0` | `Release` chờ duyệt; người không thuộc *required reviewers* không bấm được; duyệt xong prod = digest staging |
| T9 | NetworkPolicy | Pod staging gọi `identity-service.datn-prod...` | Timeout |
| T10 | Đổi cấu hình | Sửa `values-staging.yaml` (`mail.from`), merge | `checksum/env` đổi nên Deployment tự rolling update |
| T11 | Khoá SSH của CI bị khoá chặt | Dùng khoá `DEPLOY_SSH_KEY`: `ssh tunnel@<server>` (mở shell); `ssh -N -L 5432:<postgres>:5432 …` (đích khác) | Shell: `This account is currently not available`; cổng khác: `administratively prohibited` |
| T12 | Công cụ quản trị | Mở `pgadmin-staging.<domain>` từ IP ngoài danh sách, rồi từ IP trong danh sách | Ngoài danh sách: 403; trong: hỏi basic auth rồi tới đăng nhập pgAdmin; thấy 2 server DB của **đúng** namespace |

---

## 10. Nâng cao (sau phần chính)
- **GitOps (Argo CD)** quản lý release Helm từ Git; CI chỉ build và commit tag, không cần đường hầm SSH.
- **Sealed Secrets / helm-secrets (SOPS)**: commit secret đã mã hoá.
- **Giám sát:** `kube-prometheus-stack` (Helm) + Micrometer.
- **Chặn lỗ hổng bằng Trivy** sau khi nâng Spring Boot (`TRIVY_EXIT_CODE=1`); tải báo cáo SARIF lên tab *Security* của GitHub.
- **Mở cổng 22 theo IP runner** trong lúc deploy (AWS CLI + OIDC) thay vì mở cho mọi IP.
- **HPA** cho api-gateway; **Gateway API** thay ingress-nginx; ký image bằng cosign.
