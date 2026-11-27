# 03 — Tạo namespace `datn` và Secret

> Thuộc [00 — Danh sách các bước](00_DANH_SACH_CAC_BUOC.md) (**bước 2**). Làm **sau** [02](02_CAI_MINIKUBE_KUBECTL_HELM.md): cụm Minikube trên `datn-minikube` đã `Ready`.
>
> Kết quả: namespace `datn` có
> - Secret **`app-secrets`** — mọi mật khẩu của hạ tầng + ứng dụng (Postgres, Keycloak, MinIO, SMTP);
> - Secret **`dockerhub-pull`** — token Docker Hub **Read-only** để cụm kéo image, gắn sẵn vào ServiceAccount `default`;
> - (khuyến nghị) **LimitRange** mặc định cho ngân sách 8 GB.
>
> Quy ước giống 02: lệnh Linux chạy trong phiên SSH vào **`datn-minikube`** bằng user **`ubuntu`** (dấu nhắc `ubuntu@datn-minikube:~$`), dùng kubeconfig admin của `ubuntu`. Mật khẩu **chỉ nằm trên server + bản sao lưu cá nhân**, **không commit**, không ghi vào `values.yaml`.

## Mục lục

1. [Secret cần những gì (đối chiếu source code)](#1-secret-cần-những-gì-đối-chiếu-source-code)
2. [Tạo namespace](#2-tạo-namespace)
3. [Sinh mật khẩu vào file `app-secrets.env`](#3-sinh-mật-khẩu-vào-file-app-secretsenv)
4. [Tạo Secret `app-secrets`](#4-tạo-secret-app-secrets)
5. [Sao lưu file mật khẩu về máy cá nhân](#5-sao-lưu-file-mật-khẩu-về-máy-cá-nhân)
6. [Token Docker Hub Read-only và Secret `dockerhub-pull`](#6-token-docker-hub-read-only-và-secret-dockerhub-pull)
7. [(Khuyến nghị) LimitRange cho ngân sách 8 GB](#7-khuyến-nghị-limitrange-cho-ngân-sách-8-gb)
8. [Đổi mật khẩu về sau](#8-đổi-mật-khẩu-về-sau)
9. [Không được làm](#9-không-được-làm)
10. [Xử lý sự cố](#10-xử-lý-sự-cố)
11. [Checklist hoàn thành](#11-checklist-hoàn-thành)

---

## 1. Secret cần những gì (đối chiếu source code)

Hiện mật khẩu đang **viết cứng** trong repo (chỉ hợp cho máy dev): `postgres` / `123456` trong `application.yaml` của identity / work / workspace / notification, Keycloak admin `admin` / `admin` và `client-secret` của `datn-backend` trong `identity-service/application.yaml` + realm export, MinIO `minioadmin` / `minioadminpassword` trong `docker-compose.infra.yml`. Trên Minikube **thay toàn bộ** bằng giá trị ngẫu nhiên.

Không cần sửa code: Spring Boot ưu tiên **biến môi trường** hơn giá trị trong `application.yaml` (relaxed binding, ví dụ `SPRING_DATASOURCE_PASSWORD` ghi đè `spring.datasource.password`). Chart ở bước 3 (`infrastructure/helm/`, xem [04](04_VIET_HELM_CHART_VA_SCRIPT.md)) đọc từng key của Secret bằng `secretKeyRef` và gán vào đúng biến dưới đây — đã chạy thử thật, các biến này khớp với `application.yaml` của từng service.

| Key trong `app-secrets` | Thành phần dùng | Biến môi trường chart gán (bước 3) |
|---|---|---|
| `postgres-password` | Postgres (superuser `postgres`, chỉ để quản trị + script khởi tạo) | `POSTGRES_PASSWORD` |
| `app-db-username`, `app-db-password` | User **`datn`** sở hữu DB `datn_main_db`; identity, work, workspace, notification | Postgres init script; 4 service: `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` |
| `keycloak-db-username`, `keycloak-db-password` | User **`kc_user`** (giống compose) sở hữu DB `keycloak` (cùng instance Postgres — R2 trong 00) | Postgres init script; Keycloak: `KC_DB_USERNAME`, `KC_DB_PASSWORD` |
| `keycloak-admin-username`, `keycloak-admin-password` | Tài khoản admin realm `master` của Keycloak; identity-service dùng để gọi Admin API | Keycloak 24: `KEYCLOAK_ADMIN`, `KEYCLOAK_ADMIN_PASSWORD`; identity: `KEYCLOAK_ADMIN_USERNAME`, `KEYCLOAK_ADMIN_PASSWORD` (→ `keycloak.admin.*`) |
| `keycloak-client-secret` | Client `datn-backend` trong realm `datn-realm` | identity: `KEYCLOAK_CLIENT_SECRET` (profile `k8s` đặt `keycloak.client.client-secret: ${KEYCLOAK_CLIENT_SECRET}`); `infrastructure/k8s/make-realm-secret.sh` (bước 4) ghi giá trị này vào realm trước khi Keycloak import |
| `minio-root-user`, `minio-root-password` | MinIO; work-service (bucket `work-attachments`) | MinIO: `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD`; work: `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY` |
| `mail-username`, `mail-password` | notification-service gửi email | `MAIL_USERNAME`, `MAIL_PASSWORD` (để **trống** nếu dùng Mailpit) |

**Đối chiếu với cấu hình dev hiện tại** (`docker-compose*.yml`, `application.yaml`):

| | Dev (compose) | Minikube (Secret) | Lý do |
|---|---|---|---|
| DB ứng dụng | `datn_main_db`, user `postgres` / `123456` | `datn_main_db`, user **`datn`** / ngẫu nhiên | Ứng dụng không dùng superuser. 2 migration `V1` (work, workspace) có `CREATE EXTENSION pgcrypto` → script khởi tạo (R2) tạo sẵn extension bằng superuser |
| DB Keycloak | instance riêng, DB `keycloak`, user `kc_user` / `kc_password` | **cùng** instance (R2), DB `keycloak`, user `kc_user` / ngẫu nhiên | Giữ tên, đổi mật khẩu |
| Keycloak admin | `admin` / `admin` | `admin` / ngẫu nhiên | Admin Console lộ ra Internet qua `sso.<domain>` → **bắt buộc** đổi |
| Client `datn-backend` | secret viết cứng trong `identity-service/application.yaml` + `realm-export*.json` (đã nằm trong git) | ngẫu nhiên, ghi vào realm ở bước 4 | Giá trị cũ ai đọc repo cũng biết |
| MinIO | `minioadmin` / `minioadminpassword` | `minioadmin` / ngẫu nhiên | Giữ tên, đổi mật khẩu |
| Email | Mailpit (`mailpit:1025`, không xác thực) | Mailpit, `mail-*` để trống | Giống dev |

**Không** nằm trong Secret (không bí mật → ConfigMap / `values-minikube.yaml` ở bước 3): `MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM`, `APP_FRONTEND_URL`, `KEYCLOAK_ISSUER_URI`, URL giữa các service, `SPRING_KAFKA_BOOTSTRAP_SERVERS`, Redis host. Redis và Kafka hiện **không có mật khẩu** (giống compose) — chỉ truy cập được trong cụm, chấp nhận cho đồ án.

---

## 2. Tạo namespace

```bash
kubectl create namespace datn
kubectl label namespace datn app.kubernetes.io/part-of=datn
kubectl get namespace datn --show-labels    # Active
```

(Tuỳ chọn, cho đỡ gõ `-n datn`: `kubectl config set-context --current --namespace=datn`. Tài liệu vẫn ghi `-n datn` cho rõ.)

---

## 3. Sinh mật khẩu vào file `app-secrets.env`

Sinh bằng `openssl rand -hex` (chỉ gồm `0-9a-f` → không vướng ký tự đặc biệt trong JDBC URL / YAML / shell), ghi thẳng vào file quyền `600` — **không gõ mật khẩu ra màn hình**, không lọt vào lịch sử lệnh.

Mật khẩu chỉ **ngẫu nhiên một lần** lúc chạy lệnh, sau đó cố định trong file. Postgres / Keycloak / MinIO trên Minikube là bản **mới**, lần đầu khởi động (bước 4) tự tạo tài khoản bằng đúng các giá trị này, service cũng đọc cùng Secret → luôn khớp; máy dev / compose không bị ảnh hưởng.

⚠️ **Chỉ chạy một lần.** Chạy lại = bộ mật khẩu mới ghi đè file → nếu bước 4 đã chạy thì DB giữ mật khẩu cũ, app không đăng nhập được. `set -o noclobber` dưới đây khiến lệnh **báo lỗi `cannot overwrite existing file`** thay vì ghi đè; muốn đổi mật khẩu thì làm theo mục 8.

```bash
install -d -m 700 ~/datn-secrets
umask 077
set -o noclobber                            # không cho ghi đè file đã có
cat > ~/datn-secrets/app-secrets.env <<EOF
postgres-password=$(openssl rand -hex 24)
app-db-username=datn
app-db-password=$(openssl rand -hex 24)
keycloak-db-username=kc_user
keycloak-db-password=$(openssl rand -hex 24)
keycloak-admin-username=admin
keycloak-admin-password=$(openssl rand -hex 16)
keycloak-client-secret=$(openssl rand -hex 16)
minio-root-user=minioadmin
minio-root-password=$(openssl rand -hex 24)
mail-username=
mail-password=
EOF
set +o noclobber
umask 022
ls -l ~/datn-secrets/                       # -rw------- ubuntu ubuntu ... app-secrets.env
grep -c '=' ~/datn-secrets/app-secrets.env  # 12
```

**SMTP:**
- Dùng **Mailpit** (giống dev, mặc định): để trống 2 dòng `mail-*`. Lưu ý: Mailpit **giữ** mail lại chứ không gửi đi → người đăng ký mới không nhận được link xác thực email (tài khoản `INACTIVE`, login bị 403); bạn phải mở giao diện Mailpit để bấm link thay họ. Đủ cho demo tự làm; muốn người khác tự đăng ký thì dùng Gmail.
- Dùng **Gmail thật**: bật xác minh 2 bước → tạo **App Password** (16 ký tự) → sửa file:
  ```bash
  nano ~/datn-secrets/app-secrets.env
  # mail-username=<địa chỉ gmail>
  # mail-password=<app password, viết liền, không dấu cách>
  ```
  `MAIL_HOST=smtp.gmail.com`, `MAIL_PORT=587`, `MAIL_SMTP_AUTH=true`, `MAIL_SMTP_STARTTLS=true` là cấu hình không bí mật → đặt ở bước 3.

> Mật khẩu admin Keycloak cần khi đăng nhập Admin Console ở bước 4: xem bằng `grep keycloak-admin ~/datn-secrets/app-secrets.env`.

---

## 4. Tạo Secret `app-secrets`

```bash
kubectl -n datn create secret generic app-secrets \
  --from-env-file="$HOME/datn-secrets/app-secrets.env"

kubectl -n datn describe secret app-secrets
# Type: Opaque — liệt kê đủ 12 key kèm số byte (KHÔNG hiện giá trị); mail-* là 0 bytes nếu dùng Mailpit
```

Kiểm tra một key có đúng không (in ra màn hình — chỉ làm khi cần):

```bash
kubectl -n datn get secret app-secrets -o jsonpath='{.data.app-db-username}' | base64 -d; echo   # datn
```

---

## 5. Sao lưu file mật khẩu về máy cá nhân

Nếu mất file này **và** cụm bị xoá (`minikube delete`) thì không còn cách nào biết mật khẩu DB cũ. Chép 1 bản về thư mục `secret/` trong repo trên máy Windows — thư mục này **đã nằm trong `.gitignore`** (`secret/`) nên không bị commit.

🪟 Chạy trong **PowerShell trên Windows** — mở **cửa sổ PowerShell mới**, dấu nhắc phải là `PS D:\...>`. **Không** chạy trong cửa sổ SSH (`ubuntu@datn-minikube:~$` sẽ báo `cannot change to 'D:DATN-2026'` vì server không có ổ D):

```powershell
cd D:\DATN-2026\secret
scp -i .\<tên-key>.pem ubuntu@<Elastic IP datn-minikube>:~/datn-secrets/app-secrets.env .\app-secrets.minikube.env
git -C D:\DATN-2026 check-ignore -v secret\app-secrets.minikube.env   # phải in ra dòng ".gitignore:...:secret/" → an toàn
```

Tốt hơn nữa: chép nội dung vào trình quản lý mật khẩu (Bitwarden, 1Password…).

---

## 6. Token Docker Hub Read-only và Secret `dockerhub-pull`

### 6.1. Tạo token Read-only

Khác token **Read & Write** của credential `dockerhub` trong Jenkins ([01 §9](01_HUONG_DAN_SETUP_JENKINS.md#9-credentials)). Cụm chỉ cần **kéo** image, nên dùng token riêng chỉ đọc (lộ token này thì người khác cũng không push đè image được).

Docker Hub → **Account settings → Personal access tokens → Generate new token**:
- Description: `datn-minikube-pull`
- Access permissions: **Read-only**
- Copy token (`dckr_pat_...`) — Docker Hub chỉ hiện một lần.

### 6.2. Tạo Secret mà không để token lọt vào lịch sử lệnh

`read -s` nhận token mà không hiện ra màn hình; lịch sử lệnh chỉ lưu câu lệnh `read`, không lưu giá trị.

```bash
read -rp  "Docker Hub username: " DH_USER                # tên đăng nhập Docker Hub (chữ thường), cùng tài khoản với credential dockerhub
read -rsp "Docker Hub token (Read-only): " DH_TOKEN; echo
kubectl -n datn create secret docker-registry dockerhub-pull \
  --docker-server=https://index.docker.io/v1/ \
  --docker-username="$DH_USER" \
  --docker-password="$DH_TOKEN"
unset DH_TOKEN
```

### 6.3. Gắn vào ServiceAccount `default` của namespace

Mọi pod trong `datn` (cả hạ tầng lẫn app) tự dùng secret này khi kéo image → chart ở bước 3 **không cần** khai báo `imagePullSecrets`, và việc kéo `postgres`, `redis`, `apache/kafka`… từ Docker Hub cũng tính theo tài khoản (giới hạn pull cao hơn ẩn danh — [02 nghiên cứu §3.5](../02_NGHIEN_CUU_CICD_TREN_SOURCE_CODE.md#35-docker-hub)).

```bash
kubectl -n datn patch serviceaccount default \
  -p '{"imagePullSecrets":[{"name":"dockerhub-pull"}]}'
kubectl -n datn get serviceaccount default -o jsonpath='{.imagePullSecrets}'; echo   # [{"name":"dockerhub-pull"}]
```

### 6.4. Kiểm tra token hoạt động

Token sai thì Docker Hub từ chối cả image công khai → chạy thử 1 pod nhỏ là biết:

```bash
kubectl -n datn run pulltest --image=docker.io/library/busybox:1.36 --restart=Never --command -- echo ok
kubectl -n datn wait --for=jsonpath='{.status.phase}'=Succeeded pod/pulltest --timeout=120s
kubectl -n datn logs pulltest                   # ok
kubectl -n datn delete pod pulltest
```

Image repo `datn-*` **chưa có** trên Docker Hub cũng không sao — bước 5 mới push.

---

## 7. (Khuyến nghị) LimitRange cho ngân sách 8 GB

Theo [00 — Ngân sách tài nguyên](00_DANH_SACH_CAC_BUOC.md#ngân-sách-tài-nguyên-datn-minikube-2-vcpu--8-gb): container nào **quên** khai báo `resources` sẽ được gán mặc định, không thể ăn hết RAM của cụm. Chỉ đặt giới hạn **memory**, **không** đặt giới hạn CPU (R3).

```bash
kubectl apply -f - <<'EOF'
apiVersion: v1
kind: LimitRange
metadata:
  name: default-limits
  namespace: datn
spec:
  limits:
    - type: Container
      defaultRequest:
        memory: 64Mi
        cpu: 50m
      default:
        memory: 256Mi
EOF
kubectl -n datn describe limitrange default-limits
```

Chart ở bước 3 vẫn khai báo `resources` rõ ràng cho từng thành phần (ghi đè mặc định này). **File thật nằm trong repo:** `infrastructure/k8s/namespace-limits.yaml` có cùng LimitRange `default-limits` (apply đè lên bản tạo tay ở trên, không nhân đôi) **cộng thêm ResourceQuota `datn-quota`** (`requests.cpu: 1`, `requests.memory: 4608Mi`) để cấu hình vượt ngân sách bị từ chối ngay lúc tạo pod. Ở bước 4 bạn chạy `kubectl apply -f infrastructure/k8s/namespace-limits.yaml` thay cho đoạn tạo tay này (không chứa bí mật).

---

## 8. Đổi mật khẩu về sau

```bash
nano ~/datn-secrets/app-secrets.env
kubectl -n datn create secret generic app-secrets \
  --from-env-file="$HOME/datn-secrets/app-secrets.env" \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl -n datn rollout restart deployment      # pod chỉ đọc biến môi trường lúc khởi động
```

⚠️ Chỉ sửa Secret là **chưa đủ** với các mật khẩu đã được "nạp" vào dữ liệu:

| Key | Sau khi hạ tầng đã chạy (bước 4), muốn đổi thì |
|---|---|
| `postgres-password`, `app-db-password`, `keycloak-db-password` | Postgres chỉ đọc lúc tạo DB lần đầu → phải `ALTER USER ... PASSWORD '...'` trong `psql` **rồi** mới sửa Secret |
| `keycloak-admin-*` | Keycloak chỉ tạo admin ở lần khởi động đầu → đổi trong Admin Console rồi sửa Secret |
| `keycloak-client-secret` | Đổi ở Admin Console → `datn-realm` → Clients → `datn-backend` → Credentials, rồi sửa Secret |
| `minio-root-*` | Đổi env MinIO + work-service cùng lúc (Secret) rồi restart cả hai |
| `mail-*` | Chỉ cần sửa Secret + restart notification-service |

Nhớ chép lại file đã sửa về máy cá nhân (mục 5).

---

## 9. Không được làm

| Không làm | Vì sao |
|---|---|
| Commit `app-secrets.env`, token Docker Hub, hay output `kubectl get secret -o yaml` | `data:` trong Secret chỉ là **base64**, không phải mã hoá — ai có file là có mật khẩu |
| Gõ mật khẩu trực tiếp trong câu lệnh (`--from-literal=...=matkhau`, `--docker-password=dckr_pat_...`) | Lưu nguyên văn vào `~/.bash_history` |
| Ghi mật khẩu vào `values.yaml` / `values-minikube.yaml` | Các file này commit vào repo; chart chỉ được tham chiếu tên Secret |
| Dùng token Read & Write (của Jenkins) cho cụm | Cụm chỉ cần đọc; lộ token R/W là người khác push đè được image |
| `kubectl delete namespace datn` khi đã có hạ tầng | Xoá luôn PVC → **mất DB** |

Lưu ý: Minikube không bật mã hoá Secret trong etcd (dữ liệu nằm trong volume `minikube` trên server). Ai có quyền `docker` / `sudo` trên `datn-minikube` đều đọc được → chỉ `ubuntu` có quyền này; user `deployer` (bước 6) **không** vào nhóm `docker`.

---

## 10. Xử lý sự cố

| Triệu chứng | Nguyên nhân / cách xử lý |
|---|---|
| `error: failed to create secret secrets "app-secrets" already exists` | Đã tạo rồi → cập nhật theo mục 8 (`--dry-run=client -o yaml \| kubectl apply -f -`) |
| `describe` ra ít hơn 12 key | File có dòng sai định dạng (dấu cách quanh `=`, dòng trống ở giữa thì không sao) → sửa file, cập nhật lại |
| `pulltest` kẹt `ErrImagePull` / `ImagePullBackOff`, `describe pod` báo `unauthorized: incorrect username or password` | Sai username (phải là username, không phải email) hoặc token → `kubectl -n datn delete secret dockerhub-pull`, làm lại 6.2 |
| `pulltest` báo `toomanyrequests` | Secret chưa gắn vào ServiceAccount (6.3) nên đang kéo ẩn danh |
| `kubectl wait` báo `timed out` nhưng `get pod` là `Completed` | Bản kubectl cũ không hỗ trợ `--for=jsonpath` → bỏ qua, xem `kubectl -n datn logs pulltest` |

---

## 11. Checklist hoàn thành

- [ ] `kubectl get ns datn` → `Active`
- [ ] `~/datn-secrets/app-secrets.env` quyền `600`, đủ 12 dòng; đã quyết định Mailpit hay Gmail
- [ ] Đã sao lưu file về `D:\DATN-2026\secret\` (đã `check-ignore`) hoặc trình quản lý mật khẩu
- [ ] Token Docker Hub **Read-only** `datn-minikube-pull` đã tạo
- [ ] `kubectl -n datn get secrets` thấy:
  ```
  NAME             TYPE                             DATA
  app-secrets      Opaque                           12
  dockerhub-pull   kubernetes.io/dockerconfigjson   1
  ```
- [ ] ServiceAccount `default` có `imagePullSecrets: dockerhub-pull`; pod `pulltest` in `ok` (đã xoá)
- [ ] (Khuyến nghị) `kubectl -n datn get limitrange` → `default-limits`

Xong → sang **bước 3** trong [00](00_DANH_SACH_CAC_BUOC.md): viết Helm chart (dùng đúng tên Secret `app-secrets` và các key ở mục 1).
