# 04 — Helm chart, profile Spring `k8s` và script triển khai (bước 3)

> Thuộc [00 — Danh sách các bước](00_DANH_SACH_CAC_BUOC.md) (**bước 3**). Làm **sau** [03](03_TAO_NAMESPACE_VA_SECRET.md) (Secret) và **trước** khi cài lên cụm (bước 4).
>
> **Bước này đã được làm sẵn trong repo.** Tài liệu này giải thích những gì đã tạo, vì sao thiết kế như vậy, đã kiểm chứng những gì (và chưa gì), và việc bạn còn phải làm (§8). Chart được viết cho Minikube **2 vCPU / 8 GB** theo ngân sách R2–R6 của [00](00_DANH_SACH_CAC_BUOC.md#ngân-sách-tài-nguyên-datn-minikube-2-vcpu--8-gb).
>
> Quy ước: lệnh có nhãn **🪟 Windows** chạy trong PowerShell trên máy bạn (cần Docker Desktop); nhãn **🐧 server** chạy trong phiên SSH vào `datn-minikube`.

## Mục lục

1. [Đã tạo gì](#1-đã-tạo-gì)
2. [Hai chart phối hợp thế nào](#2-hai-chart-phối-hợp-thế-nào)
3. [Những quyết định thiết kế đáng biết](#3-những-quyết-định-thiết-kế-đáng-biết)
4. [Kiểm tra chart trên máy bạn](#4-kiểm-tra-chart-trên-máy-bạn)
5. [R2–R6 nằm ở đâu trong chart](#5-r2r6-nằm-ở-đâu-trong-chart)
6. [Đã kiểm chứng gì, chưa kiểm chứng gì](#6-đã-kiểm-chứng-gì-chưa-kiểm-chứng-gì)
7. [Hiện tượng lạ ở lần chạy đầu (không phải lỗi)](#7-hiện-tượng-lạ-ở-lần-chạy-đầu-không-phải-lỗi)
8. [Việc của bạn trước khi sang bước 4](#8-việc-của-bạn-trước-khi-sang-bước-4)
9. [Bước 4 sẽ dùng các file này thế nào](#9-bước-4-sẽ-dùng-các-file-này-thế-nào)
10. [Xử lý sự cố](#10-xử-lý-sự-cố)
11. [Checklist hoàn thành](#11-checklist-hoàn-thành)

---

## 1. Đã tạo gì

```
frontend/Dockerfile                          sửa: ARG VITE_KEYCLOAK_URL (§3, mục 9)
infrastructure/
├── ci/build-push.sh                         build + (quét trivy nếu có) + push 6 image lên Docker Hub
├── helm/
│   ├── env-minikube.yaml                    tên miền, scheme, registry — NƠI DUY NHẤT, cả hai chart đọc qua -f
│   ├── datn-platform/                       hạ tầng, admin cài 1 lần
│   │   ├── values.yaml                      mặc định (rộng rãi)
│   │   ├── values-minikube.yaml             R2, R4: ngân sách 8 GB
│   │   └── templates/                       postgres · keycloak · kafka · redis · minio · mailpit
│   └── datn-app/                            ứng dụng, Jenkins chỉ đổi image.tag
│       ├── values.yaml
│       ├── values-minikube.yaml             R3, R5, R6: JVM, probe, kiểu deploy
│       ├── files/config/<service>/application-k8s.yaml     profile Spring k8s của 5 service
│       └── templates/                       backend (lặp 5 service) · frontend · ingress · configmap-env · configmap-spring
└── k8s/
    ├── namespace-limits.yaml                LimitRange + ResourceQuota của namespace datn
    ├── rbac-deployer.yaml                   ServiceAccount ci-deployer + Role quyền tối thiểu
    ├── make-ci-kubeconfig.sh                kubeconfig giới hạn quyền cho user deployer (bước 6)
    ├── make-realm-secret.sh                 Secret keycloak-realm từ realm export đã chỉnh (bước 4)
    ├── set-public-host.sh                   đặt tên miền / registry vào env-minikube.yaml (§8)
    └── deploy.sh                            chạy trên datn-minikube: helm upgrade --install, smoke test, tự rollback
```

Chart cũ (GitHub Actions + GHCR) vẫn còn trong lịch sử git tại commit `2e53884` ([02 nghiên cứu §9](../02_NGHIEN_CUU_CICD_TREN_SOURCE_CODE.md#9-tham-khảo-bản-triển-khai-cũ-trong-git)); chart mới **viết lại** chứ không copy, vì khác 2 server + Docker Hub, một Postgres, ngân sách 8 GB, và đã đối chiếu với code hiện tại.

---

## 2. Hai chart phối hợp thế nào

```
Secret app-secrets (bước 2, tạo tay) ────────┐      Secret keycloak-realm (make-realm-secret.sh) ─┐
                                              ▼                                                    ▼
┌─ datn-platform (admin cài 1 lần) ─────────────────────────────────────────────────────────────────────┐
│  postgres   keycloak   kafka   redis   minio   mailpit         Service nội bộ: tên trùng tên workload │
└────────────────────────────────────────────────────────────────────────────────────────────────────────┘
                     ▲ gọi nhau bằng DNS trong cụm: postgres:5432, keycloak:8080, kafka:9092, redis:6379, minio:9000
┌─ datn-app (Jenkins đổi image.tag) ────────────────────────────────────────────────────────────────────┐
│  api-gateway   identity-service   work-service   workspace-service   notification-service   frontend   │
│  Ingress: app.<domain>  /identity /work /workspace /notification → api-gateway ; / → frontend          │
└────────────────────────────────────────────────────────────────────────────────────────────────────────┘
Ingress của datn-platform: sso.<domain> → keycloak
env-minikube.yaml (global.scheme / appHost / ssoHost / registry) được truyền vào CẢ HAI chart
```

Đường đi của một request: trình duyệt → Nginx trên `datn-minikube` (TLS, bước 5) → ingress-nginx của Minikube (`192.168.49.2:80`) → Ingress → Service → Pod.

Ba nơi chứa cấu hình, phân theo **mức bí mật**:

| Nơi | Chứa | Ai tạo | Commit? |
|---|---|---|---|
| Secret `app-secrets` | mật khẩu DB, admin Keycloak, client secret, MinIO, SMTP | bạn, bước 2 | **Không** |
| ConfigMap `env-config` (chart `datn-app`) | URL công khai, URL issuer / JWKS, `MAIL_*`, origin CORS, cookie secure | chart | Có |
| ConfigMap `<service>-config` (chart `datn-app`) | `application-k8s.yaml`: DNS nội bộ giữa các service, tên host Postgres / Kafka / Redis / MinIO, cờ cụm | chart | Có |

Mọi giá trị bí mật vào pod bằng **biến môi trường** (`secretKeyRef`); Spring ưu tiên biến môi trường hơn `application.yaml`, nên **không phải sửa code**. Bảng key → biến nằm ở [03 §1](03_TAO_NAMESPACE_VA_SECRET.md#1-secret-cần-những-gì-đối-chiếu-source-code).

---

## 3. Những quyết định thiết kế đáng biết

| # | Quyết định | Vì sao (bằng chứng) |
|---|---|---|
| 1 | **Một Postgres, hai database**, mỗi DB một user riêng không phải superuser | R2, tiết kiệm RAM. Đã chạy thử trên PG 15: user `datn` chạy đủ 10 migration Flyway của work-service và 6 của workspace-service; `kc_user` không vào được `datn_main_db` và ngược lại. Script khởi tạo tạo sẵn `pgcrypto` vì migration V1 của work / workspace có `CREATE EXTENSION` |
| 2 | JWT: `issuer-uri` = URL **công khai**, `jwk-set-uri` = URL **nội bộ** (`http://keycloak:8080`); Keycloak đặt `KC_HOSTNAME_URL` = URL công khai | "Bẫy lớn nhất" ở [02 nghiên cứu §8.1](../02_NGHIEN_CUU_CICD_TREN_SOURCE_CODE.md). **Đã chứng minh:** token lấy qua `http://keycloak:8080` mang `iss=https://sso.<domain>/realms/datn-realm`, gateway và work-service chấp nhận (200), token giả bị 401 |
| 3 | Gateway ghi đè **cả** `app.cors.allowed-origins` **và** `spring.cloud.gateway.globalcors` | Code có hai lớp CORS (`CorsWebFilter` và `globalcors`), danh sách gốc chỉ có `localhost`. Sau ingress-nginx mà gateway không thấy `X-Forwarded-*` thì request ghi / WebSocket từ `https://app.<domain>` bị 403. Xem thêm §10 (`use-forwarded-headers`) |
| 4 | `profile k8s` khai **đủ 4 route** của gateway, **cố ý không** có `/*/internal/**` | Danh sách trong profile **thay thế** danh sách gốc. Đã thử: `/notification/internal/email`, `/workspace/internal/...`, `/identity/internal/...` đều 404 dù có token hợp lệ (không bị lộ ra Internet) |
| 5 | Init container chờ theo **chuỗi** `identity → work → workspace` | Đúng thứ tự mà chính code ghi (comment trong migration V3 của workspace-service): identity tạo bảng `users` (ddl-auto) → work tạo `projects` → workspace mới tạo được FK `fk_projects_workspace` (đã thử: đúng thứ tự thì FK được tạo). Các migration đổi user id sang Keycloak sub cũng chỉ chạy khi bảng `users` đã có. notification-service **không khởi động được** khi không phân giải được `kafka` nên cũng chờ Kafka |
| 6 | Deploy `maxSurge: 0, maxUnavailable: 1` | R6: tránh bật cùng lúc pod mới của cả 5 service (thêm ~2 GB RAM → OOM). Đánh đổi: gián đoạn lần lượt lúc deploy |
| 7 | Release `datn-app` lưu bằng `HELM_DRIVER=configmap`; Jenkins không có quyền đọc Secret | `rbac-deployer.yaml` cố ý **không** có `secrets`, `statefulsets`, `persistentvolumeclaims`, `pods/exec`: Jenkins không đọc được mật khẩu và không đụng được Postgres / Kafka / MinIO |
| 8 | **Không** có pgAdmin, Ingress của MinIO / Mailpit, NetworkPolicy | pgAdmin ~200 MB RAM và lộ công cụ DB ra Internet; MinIO không cần public vì file tải qua API `/attachments/{id}/download` của work-service; Mailpit đọc được = chiếm tài khoản; NetworkPolicy cần CNI Calico, Minikube mặc định **không thực thi** nên chỉ là hình thức |
| 9 | `ARG VITE_KEYCLOAK_URL` trong `frontend/Dockerfile` | Theo kế hoạch ([02 nghiên cứu §3.3](../02_NGHIEN_CUU_CICD_TREN_SOURCE_CODE.md#33-frontend-biến-build-time)); đã kiểm chứng giá trị vào được stage build. **Lưu ý:** hiện `frontend/src/App.jsx` khai báo hằng `KEYCLOAK_URL` nhưng **không chỗ nào dùng**, nên biến này chưa có tác dụng cho tới khi code dùng nó |
| 10 | Tag image = git SHA, `pullPolicy: IfNotPresent`, `image.tag` **bắt buộc** (không có mặc định) | Quên truyền tag thì `helm` báo lỗi ngay thay vì deploy nhầm `latest` |

---

## 4. Kiểm tra chart trên máy bạn

Không cần cài Helm: dùng container (Docker Desktop đang chạy). **🪟 Windows (PowerShell):**

```powershell
cd D:\DATN-2026\infrastructure\helm

# 1) Lint nghiêm ngặt (kỳ vọng: "0 chart(s) failed"; dòng "icon is recommended" là bình thường)
docker run --rm -v "${PWD}:/charts" -w /charts alpine/helm:4.3.2 lint datn-platform -f datn-platform/values-minikube.yaml -f env-minikube.yaml --strict
docker run --rm -v "${PWD}:/charts" -w /charts alpine/helm:4.3.2 lint datn-app -f datn-app/values-minikube.yaml -f env-minikube.yaml --set image.tag=abc1234 --strict

# 2) Render ra thư mục tạm rồi kiểm tra theo schema Kubernetes (kỳ vọng: "Valid: 33, Invalid: 0")
$out = Join-Path $env:TEMP "datn-render"; Remove-Item $out -Recurse -Force -ErrorAction SilentlyContinue
docker run --rm -v "${PWD}:/charts" -v "${out}:/out" -w /charts alpine/helm:4.3.2 template datn-platform datn-platform -n datn -f datn-platform/values-minikube.yaml -f env-minikube.yaml --output-dir /out
docker run --rm -v "${PWD}:/charts" -v "${out}:/out" -w /charts alpine/helm:4.3.2 template datn-app datn-app -n datn -f datn-app/values-minikube.yaml -f env-minikube.yaml --set image.tag=abc1234 --output-dir /out
docker run --rm -v "${out}:/out:ro" ghcr.io/yannh/kubeconform:latest -strict -summary -kubernetes-version 1.34.0 /out
```

Dùng `--output-dir` (không `| pipe`) vì PowerShell 5.1 làm hỏng ký tự tiếng Việt khi pipe giữa các lệnh. Trên **🐧 server** (đã có Helm 4 ở [02 §4](02_CAI_MINIKUBE_KUBECTL_HELM.md#4-cài-helm)) chỉ cần `helm lint ...` và `helm template ... | less` trong thư mục `infrastructure/helm` của bản clone.

Xem nhanh những gì chart sẽ tạo ra, ví dụ chỉ deployment của work-service: thêm `--show-only templates/backend.yaml` vào lệnh `template`.

---

## 5. R2–R6 nằm ở đâu trong chart

| R | File | Điểm chính |
|---|---|---|
| R2 gộp Postgres | `datn-platform/templates/postgres.yaml`, `values-minikube.yaml` | 1 StatefulSet, ConfigMap `postgres-init`, `shared_buffers=128MB`, `max_connections=100` |
| R3 JVM + tài nguyên | `datn-app/values-minikube.yaml` (`javaToolOptions`, `backend.*.resources`), `files/config/*/application-k8s.yaml` (`hikari.maximum-pool-size: 5`) | `MaxRAMPercentage=65`, SerialGC, `TieredStopAtLevel=1`; **không** đặt limit CPU; `requests.cpu` 50m |
| R4 hạ tầng | `datn-platform/values-minikube.yaml` | Kafka heap 384m + giữ log 72h; Keycloak limit 1280Mi, heap 40%, `KC_CACHE=local`; Redis `maxmemory 64mb`; Mailpit tự xoá thư cũ |
| R5 probe | `datn-app/values-minikube.yaml` (`probes`), `templates/backend.yaml` | `startupProbe` 10 giây × 36; init container chờ chuỗi phụ thuộc |
| R6 kiểu deploy | `datn-app/values-minikube.yaml` (`rollout`) | `maxSurge: 0`, `maxUnavailable: 1`; `deploy.sh` đặt `--timeout 15m` |

Tổng ngân sách **tính trực tiếp từ manifest đã render** (không ước lượng): `requests.memory` = **2.944 Mi ≈ 2,9 Gi** (tiêu chí ≤ 4,5 Gi, ResourceQuota 4.608 Mi), `requests.cpu` = **545m** (quota 1 CPU), `limits.memory` = 5,6 Gi. Bảng từng thành phần ở [00](00_DANH_SACH_CAC_BUOC.md#ngân-sách-tài-nguyên-datn-minikube-2-vcpu--8-gb).

> **Cái bẫy CPU:** nút Minikube chỉ có 2 CPU mà kube-system đã chiếm ~0,95. Nếu để `requests.cpu` như chart cũ (200–250m mỗi service, tổng ~1,5 CPU) thì pod kẹt `Pending: Insufficient cpu`. Vì vậy `requests.cpu` đặt 5–100m; ResourceQuota `requests.cpu: 1` bắt lỗi này ngay lúc tạo.

---

## 6. Đã kiểm chứng gì, chưa kiểm chứng gì

### Đã chạy thật (trên máy dev bằng Docker, mạng riêng có tên host giống hệt Service Kubernetes)

Mô phỏng dùng **chính manifest đã render** của hai chart (biến môi trường, tham số, giới hạn bộ nhớ), image Postgres / Redis / Kafka / Keycloak 24.0.1 / MinIO / Mailpit thật và các jar của 5 service. Không đụng tới container dev của bạn.

| Kiểm tra | Kết quả |
|---|---|
| `helm lint --strict` (Helm 4.3.2) cả hai chart; kubeconform K8s 1.34 | sạch; 33/33 tài nguyên hợp lệ |
| Script khởi tạo Postgres trên PG 15 | tạo 2 role + 2 DB + `pgcrypto`; cách ly đúng; user app không phải superuser, tạo bảng / FK được |
| identity-service, work-service, workspace-service, notification-service, api-gateway với profile `k8s` | cả 5 `readiness=UP`; nối Postgres bằng user `datn`; Flyway work 10/10, workspace 6/6 thành công; không còn log SQL |
| Kafka theo cấu hình chart | heap `-Xms384m -Xmx384m`, retention 72h, advertised `kafka:9092`, notification nhận partition |
| Keycloak 24.0.1 import realm đã chỉnh | realm `datn-realm` import OK; `iss` của token = URL công khai dù lấy qua địa chỉ nội bộ; secret sai bị từ chối |
| Đăng ký → chặn đăng nhập khi chưa xác thực (403) → kích hoạt → đăng nhập (200) | identity dùng đúng tài khoản admin và client secret lấy từ biến môi trường |
| API qua gateway | token hợp lệ 200; token giả / không token 401; `/*/internal/**` 404; smoke test `/identity|work|workspace/actuator/health` = `UP` |
| MinIO (uid 65532, args của chart) | health live / ready = 200, console 9001 mở |
| Mailpit v1.34.0 | giao diện 200; gửi SMTP không đăng nhập OK, thư vào hộp |
| Image frontend build bằng `build-push.sh` (registry cục bộ tạm) | push được cả tag SHA và tag phụ; SPA trả 200 cho `/`, `/notifications`, `/w/abc/projects`; nginx 12 MiB |
| `set-public-host.sh`, `make-realm-secret.sh` trên Ubuntu 24.04 | đổi đúng 4 dòng; chỉ sửa client `datn-backend`, phần còn lại của realm giữ nguyên; từ chối đầu vào sai |
| `deploy.sh` với Helm 4 thật (không có cụm) | qua lint, render, dò `--rollback-on-failure`, nhận chart qua stdin / `HELM_DIR`; dừng đúng ở bước chạm cụm. `shellcheck` sạch cho cả 5 script |

### Chưa kiểm chứng được (chỉ làm được trên Minikube thật, ở bước 4–5)

- ingress-nginx định tuyến `/identity`, `/work`... và việc nó **ghi đè header `X-Forwarded-*`** (§10).
- Thời gian khởi động và `startupProbe` trên **2 vCPU** thật (mô phỏng chạy trên máy 12 CPU nhanh hơn nhiều). Con số "5–8 phút mỗi lần deploy" là **ước tính**.
- Quyền thư mục PVC với `fsGroup` (Kafka, MinIO) trên provisioner `standard` của Minikube; kéo image từ Docker Hub bằng `dockerhub-pull`.
- `helm install` thật và RBAC của `ci-deployer`; đăng nhập Google; CORS / WebSocket qua Nginx máy chủ.
- RAM thật của cả hệ thống: các số "Đo thử" trong [00](00_DANH_SACH_CAC_BUOC.md) là trên máy dev, chỉ là mốc (R7).

### Lỗi tìm thấy khi tự kiểm tra và đã sửa

1. `postgres.yaml` có dòng `checksum` tự `include` chính nó → đệ quy vô hạn. Gỡ (script khởi tạo chỉ chạy lần đầu nên không cần restart pod khi đổi).
2. `keycloak.importRealm | default true` làm cờ `false` không bao giờ có tác dụng (Helm coi `false` là rỗng). Khai báo mặc định ở `values.yaml`.
3. `make-realm-secret.sh` in **nguyên giá trị secret** ra màn hình khi dùng biến `KEYCLOAK_CLIENT_SECRET`. Đã bỏ.
4. `deploy.sh` thoát ngay ở **lần cài đầu** vì `helm history` lỗi (chưa có release) cộng với `set -o pipefail`. Đã tách bước.
5. Thêm `-XX:+UseSerialGC` cho Keycloak làm JVM không khởi động (`kc.sh` đã chọn ParallelGC). Chuyển sang hạ heap 50% → 40%: **1,06 GiB → 0,76 GiB** cùng tải.
6. `defaultMode: 0755` đổi thành `493` (YAML 1.2 hiểu `0755` là thập phân).

---

## 7. Hiện tượng lạ ở lần chạy đầu (không phải lỗi)

| Thấy gì | Nghĩa là gì |
|---|---|
| work-service log `ERROR ... Lỗi khi tự động seed các dự án mẫu ... relation "workspaces" does not exist` | **Vô hại trên DB trống.** `DataSeeder` hỏi bảng `workspaces` trước khi workspace-service tạo nó. 3 mẫu dự án vẫn có đủ vì migration V7 đã seed. Kiểm tra: `kubectl -n datn exec postgres-0 -- psql -U postgres -d datn_main_db -tAc "select key from projects where is_template"` → `TPLAGRI, TPLFNB, TPLWEB` |
| workspace-service log `WARN ... extension "pgcrypto" already exists, skipping` | Bình thường, script khởi tạo Postgres đã tạo sẵn |
| Keycloak khởi động 1–3 phút lần đầu | Tự build + import realm. `startupProbe` cho phép tới 10 phút |
| Pod `Init:0/1` nhiều phút | Init container đang chờ phụ thuộc (chuỗi identity → work → workspace). `kubectl -n datn logs <pod> -c wait-deps` xem đang chờ cái gì |
| Realm **không có user nào** | Tài khoản đầu tiên đăng ký qua giao diện, xác thực email bằng thư ở Mailpit (`kubectl -n datn port-forward svc/mailpit 8025:8025`), rồi gán realm role `ADMIN` trong Keycloak Admin Console → Users → Role mapping |
| Đăng nhập Google không chạy | Thêm redirect URI `<scheme>://<ssoHost>/realms/datn-realm/broker/google/endpoint` vào Google Cloud Console (`make-realm-secret.sh` cũng nhắc lại) |

---

## 8. Việc của bạn trước khi sang bước 4

Hai giá trị chưa thể điền sẵn vì chỉ bạn biết: **tên miền** (hoặc Elastic IP của `datn-minikube`) và **tài khoản Docker Hub**. File [env-minikube.yaml](../../../infrastructure/helm/env-minikube.yaml) đang để placeholder (`example.invalid`, `CHANGE_ME`) và `deploy.sh` / `build-push.sh` **từ chối chạy** cho tới khi bạn đặt.

1. Lấy **Elastic IP** của `datn-minikube` (EC2 → Elastic IPs). Chưa có tên miền thì dùng `sslip.io`: IP `54.1.2.3` → tên miền gốc `54-1-2-3.sslip.io` (thay dấu `.` bằng `-`); `app.54-1-2-3.sslip.io` và `sso.54-1-2-3.sslip.io` tự phân giải về đúng IP đó, không cần cấu hình DNS.
2. **🪟 Windows**, mở **Git Bash** (không phải PowerShell) ở gốc repo:
   ```bash
   bash infrastructure/k8s/set-public-host.sh 54-1-2-3.sslip.io https <tài-khoản-dockerhub-viết-thường>
   git diff infrastructure/helm/env-minikube.yaml        # kỳ vọng: đổi đúng 4 dòng
   ```
   Chọn `https` **ngay từ đầu**: scheme gắn vào `KC_HOSTNAME_URL` của Keycloak và claim `iss` của mọi token, đổi sau thì token cũ vô hiệu và phải cài lại Keycloak. Ở bước 5 sẽ làm certbot trước khi mở ứng dụng.
3. Commit file đó (giá trị này **không bí mật**). Jenkins build frontend cũng đọc nó nên phải có trên nhánh trước khi build.
4. Đưa repo lên `datn-minikube` để bước 4 dùng được `infrastructure/helm`, `infrastructure/k8s`, `infrastructure/keycloak`. Cách đơn giản: `git clone` repo (hoặc `scp -r` thư mục `infrastructure`). Chi tiết ở bước 4.

---

## 9. Bước 4 sẽ dùng các file này thế nào

Chỉ để bạn thấy bức tranh; từng lệnh sẽ có ở tài liệu bước 4.

```bash
# 🐧 server, trong bản clone của repo, bằng user ubuntu
kubectl apply -f infrastructure/k8s/namespace-limits.yaml          # LimitRange + ResourceQuota
bash infrastructure/k8s/make-realm-secret.sh                       # Secret keycloak-realm (cần jq)
helm upgrade --install datn-platform infrastructure/helm/datn-platform -n datn \
  -f infrastructure/helm/datn-platform/values-minikube.yaml -f infrastructure/helm/env-minikube.yaml
```

Cài `datn-app` lần đầu cần image đã push (bước 5). Từ bước 6, Jenkins gọi `deploy.sh` qua SSH và không ai phải chạy tay lệnh `helm` của `datn-app` nữa.

---

## 10. Xử lý sự cố

| Triệu chứng | Nguyên nhân / cách xử lý |
|---|---|
| `helm lint datn-app` báo `image.tag là bắt buộc` | Thiếu `--set image.tag=abc1234` (cố ý: không có tag mặc định) |
| `deploy.sh` / `build-push.sh`: "chưa đặt appHost / registry" | Chưa làm §8 (`set-public-host.sh`) hoặc chưa commit / chưa copy lên server |
| Pod `Pending`, `Insufficient cpu` | Tổng `requests.cpu` vượt phần còn lại của nút 2 CPU (§5). Không tăng requests; đo bằng `kubectl describe node minikube` |
| Pod không tạo được, `exceeded quota` | Vượt ResourceQuota `datn-quota` (4,5 Gi / 1 CPU) → có thành phần nào đó đang vượt ngân sách |
| `CreateContainerConfigError` : `secret "app-secrets" not found` / `couldn't find key ...` | Chưa làm [03](03_TAO_NAMESPACE_VA_SECRET.md), hoặc file `app-secrets.env` thiếu key (cần đủ 12) |
| Keycloak kẹt `ContainerCreating`, `secret "keycloak-realm" not found` | Chưa chạy `make-realm-secret.sh` (hoặc cài với `--set keycloak.importRealm=false` rồi tự import) |
| Mọi API trả **401** | Sai issuer. So `KEYCLOAK_ISSUER_URI` trong ConfigMap `env-config` với claim `iss` của token; hai cái phải giống từng ký tự (kể cả `https`). Kiểm tra `global.scheme` / `ssoHost` |
| POST / WebSocket trả **403** từ trình duyệt | Origin không khớp. ingress-nginx mặc định **ghi đè** `X-Forwarded-Proto` thành `http`; ở bước 5 bật tin tưởng header của Nginx máy chủ: `kubectl -n ingress-nginx patch configmap ingress-nginx-controller --type merge -p '{"data":{"use-forwarded-headers":"true"}}'` |
| `ImagePullBackOff` | Sai `registry` / tag chưa push / repo Docker Hub đang private mà `dockerhub-pull` sai token ([03 §6](03_TAO_NAMESPACE_VA_SECRET.md#6-token-docker-hub-read-only-và-secret-dockerhub-pull)) |
| notification-service `No resolvable bootstrap urls given in bootstrap.servers` | Service `kafka` chưa tồn tại (chưa cài `datn-platform`) |
| Postgres báo `database "datn_main_db" does not exist` hoặc `password authentication failed` sau khi đổi Secret | Script khởi tạo **chỉ chạy lần đầu** lúc thư mục dữ liệu trống. Đổi mật khẩu về sau: [03 §8](03_TAO_NAMESPACE_VA_SECRET.md#8-đổi-mật-khẩu-về-sau). Khởi tạo lại từ đầu = xoá PVC (**mất dữ liệu**) |
| `helm uninstall datn-platform` rồi cài lại, dữ liệu còn nguyên | Đúng thiết kế: PVC của StatefulSet **không** bị xoá khi gỡ chart (an toàn). Muốn sạch hẳn thì tự xoá PVC |

---

## 11. Checklist hoàn thành

- [x] Chart `datn-platform` + `datn-app`, 5 profile `k8s`, `ARG VITE_KEYCLOAK_URL`, 5 script và 3 file YAML (`env-minikube`, `rbac-deployer`, `namespace-limits`) trong `infrastructure/` *(đã có trong repo)*
- [x] `helm lint --strict` sạch cả hai chart; kubeconform 33/33
- [x] `requests.memory` ≈ 2,9 Gi (≤ 4,5 Gi); `requests.cpu` ≈ 0,55 (≤ 1)
- [ ] **Bạn:** chạy `set-public-host.sh` với tên miền + tài khoản Docker Hub thật, `git diff` đúng 4 dòng, **commit**
- [ ] **Bạn:** chạy lại §4 trên máy mình (lint + kubeconform) để chắc mọi thứ nguyên vẹn sau khi commit
- [ ] **Bạn:** quyết định cách đưa repo lên `datn-minikube` (git clone hoặc scp)

Xong → sang **bước 4**: `datn-platform`, realm Keycloak và kiểm tra R2, R4 trên cụm thật.
