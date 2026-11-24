# 05 — Triển khai từ đầu trên AWS EC2 (runbook)

> Dành cho người dựng máy chủ. Làm **theo đúng thứ tự**; mỗi bước có lệnh cần chạy và cách kiểm tra trước khi sang bước sau.
> Tài liệu này là **trình tự**. Nội dung chi tiết nằm ở [03](03_HUONG_DAN_TRIEN_KHAI.md) (cluster, chart, workflow) và [04](04_HUONG_DAN_GITHUB_ACTIONS.md) (GitHub Actions); chỗ nào trỏ sang thì làm đúng mục đó rồi quay lại đây.
> Mọi file chart, script, workflow **đã có trong repo** và đã chạy thử ([03 §13.1](03_HUONG_DAN_TRIEN_KHAI.md), [04 §2](04_HUONG_DAN_GITHUB_ACTIONS.md)).
>
> **CI/CD dùng GitHub Actions: máy chủ CHỈ chạy dự án** (minikube, Nginx). Không cài Jenkins, không cài runner.
> **Chế độ hiện tại: TẠM DÙNG IP CỦA SERVER, CHƯA CÓ TÊN MIỀN, CHẠY HTTP.** Khi có tên miền thật, làm [mục cuối](#chuyển-sang-tên-miền-thật-và-https).

## Tổng quan

| Phần | Bước | Việc | Làm ở đâu |
|---|---|---|---|
| **A. Server** | 1–2 | Tài khoản AWS, tạo EC2, Elastic IP, SSH vào được | AWS Console |
| **B. Hệ thống DATN** | 3–8 | Ghi IP vào repo, chuẩn bị máy chủ, minikube, Secret + `datn-platform`, Nginx | Máy dev + máy chủ |
| **C. GitHub Actions** | 9–10 | User `tunnel`, Environments, secret; deploy staging, phát hành prod | Máy chủ + GitHub |
| D. Vận hành | 11 | Sao lưu, nghiệm thu, tắt/bật máy | AWS + máy chủ |

> **Đã lỡ cài Jenkins theo bản trước của tài liệu?** Gỡ theo [04 §9](04_HUONG_DAN_GITHUB_ACTIONS.md) rồi làm tiếp từ bước 3. Docker Engine đã cài thì giữ nguyên (minikube cần).

---

## Tên host khi chưa có tên miền

Hệ thống phân biệt app, Keycloak, pgAdmin, staging, prod… bằng **tên host**, nên không thể dùng một địa chỉ `http://<IP>` trần cho tất cả. Thay vào đó dùng dịch vụ DNS miễn phí **[sslip.io](https://sslip.io)**: mọi tên có dạng `<gì-đó>.<IP-viết-gạch-ngang>.sslip.io` tự trỏ về đúng IP đó. Không cần mua tên miền, không cần cấu hình DNS.

Ví dụ Elastic IP là `52.62.135.59` thì **tên gốc** (gọi là `BASE`) là `52-62-135-59.sslip.io`:

| Tên | Dùng cho |
|---|---|
| `http://staging.BASE` / `http://app.BASE` | Ứng dụng staging / prod |
| `http://sso-staging.BASE` / `http://sso.BASE` | Keycloak staging / prod |
| `http://pgadmin-staging.BASE` / `http://pgadmin.BASE` | pgAdmin staging / prod |
| `http://mail-staging.BASE` | Mailpit (staging) |

**Giới hạn của chế độ tạm (chấp nhận được cho giai đoạn phát triển, không dùng cho người dùng thật):**
- **Không có HTTPS.** Let's Encrypt giới hạn số chứng chỉ theo tên miền gốc; `sslip.io` không nằm trong Public Suffix List (đã kiểm tra 24/11/2026) nên hạn mức dùng chung cho mọi người trên thế giới và thường đã cạn. Mật khẩu đi qua mạng dạng chữ thường → **nên giới hạn IP** cho công cụ quản trị (bước 7.4).
- **Đăng nhập Google không dùng được**: Google chỉ chấp nhận redirect URI `https` (trừ `localhost`). Đăng ký/đăng nhập bằng mật khẩu và email xác thực vẫn chạy bình thường.
- Keycloak phải tắt yêu cầu HTTPS (`sslRequired=none`) ở realm ứng dụng và realm `master` (bước 7.2, 7.6).
- **Giữ nguyên Elastic IP.** Đổi IP là đổi toàn bộ tên host.

---

# Phần A. Server

## Bước 1. Tài khoản AWS

1. **Region:** nên chọn **Asia Pacific (Singapore) `ap-southeast-1`**, gần Việt Nam nhất. Đã tạo ở region khác (ví dụ Sydney `ap-southeast-2`) vẫn chạy được, chỉ có độ trễ cao hơn. Mọi tài nguyên (máy, Elastic IP, Security Group) phải **cùng một region**.
2. **Hạn mức vCPU:** tài khoản mới thường chỉ được chạy ít vCPU. Vào **Service Quotas → Amazon EC2 → Running On-Demand Standard (A, C, D, H, I, M, R, T, Z) instances**. Giá trị phải **≥ 8**, nếu thấp hơn thì bấm *Request increase* lên 8 (thường được duyệt trong vài giờ tới 1 ngày).
3. **Cảnh báo chi phí:** **Billing → Budgets → Create budget** → *Monthly cost budget*, đặt mức bạn chấp nhận được (ví dụ 100 USD), nhận email khi chạm 80%.

## Bước 2. Tạo EC2

### 2.1. Chọn cỡ máy

Máy chủ chỉ gánh cluster (build chạy trên GitHub): staging + prod ≈ 3,8 core / 9,4 GiB requests, cộng thành phần Kubernetes và phần dôi ra lúc rolling update ([02 §6](02_KE_HOACH_TRIEN_KHAI_CICD.md)).

| Lựa chọn | Instance | vCPU / RAM | `minikube start` (bước 6) |
|---|---|---|---|
| **Khuyến nghị** | `t3.2xlarge` (hoặc `m7i-flex.2xlarge`) | 8 / 32 GiB | `--cpus=6 --memory=14g` |
| Tối thiểu staging + prod | `c6i.2xlarge` / `c7i-flex.2xlarge` | 8 / 16 GiB | `--cpus=7 --memory=13g` |
| Chỉ một môi trường | `t3.xlarge` | 4 / 16 GiB | `--cpus=3 --memory=9g` |

> Giá tham khảo (chạy 24/7): `t3.2xlarge` khoảng 300 USD/tháng, đĩa 100 GiB khoảng 10 USD/tháng, Elastic IP khoảng 4 USD/tháng. Kiểm tra lại trên [AWS Pricing Calculator](https://calculator.aws/). **Tắt máy khi không dùng** (bước 11.3) để chỉ trả tiền đĩa và IP.

### 2.2. Key pair

EC2 → **Key Pairs → Create key pair**: tên `datn-ec2` (dưới đây dùng tên này; tên khác thì thay theo), loại **ED25519**, định dạng **.pem**. Lưu file vào `C:\Users\<bạn>\.ssh\` (**không** để trong thư mục repo), rồi khoá quyền đọc (PowerShell):

```powershell
icacls "$env:USERPROFILE\.ssh\datn-ec2.pem" /inheritance:r
icacls "$env:USERPROFILE\.ssh\datn-ec2.pem" /grant:r "$($env:USERNAME):(R)"
icacls "$env:USERPROFILE\.ssh\datn-ec2.pem"     # chỉ còn SYSTEM, Administrators và tài khoản của bạn (R)
```

### 2.3. Security Group

EC2 → **Security Groups → Create security group**, tên `datn-sg`:

| Inbound | Port | Source | Lý do |
|---|---|---|---|
| SSH | 22 | **My IP** lúc đầu; đổi thành `0.0.0.0/0` ở bước 9 | GitHub Actions deploy qua SSH (user `tunnel`, chỉ port-forward), runner của GitHub không có IP cố định. Đăng nhập chỉ bằng khoá |
| HTTP | 80 | `0.0.0.0/0` | Ứng dụng, Keycloak, công cụ quản trị (chế độ tạm chạy HTTP) |
| HTTPS | 443 | `0.0.0.0/0` | Chưa dùng; mở sẵn cho lúc chuyển sang tên miền thật |

**Không** mở thêm cổng nào khác (8443, 5432…): mọi thứ đi qua Nginx máy chủ hoặc đường hầm SSH. Outbound để mặc định (cho phép hết).

### 2.4. Launch instance

EC2 → **Launch instance**:

| Mục | Giá trị |
|---|---|
| Name | `datn-server` |
| Application and OS Images | Tab **Ubuntu** → **Ubuntu Server 24.04 LTS (HVM), SSD Volume Type**, kiến trúc **64-bit (x86)**. **Không** để mặc định Amazon Linux (tài liệu dùng `apt` của Ubuntu); không chọn Arm |
| Instance type | Theo bước 2.1 |
| Key pair | `datn-ec2` |
| Network settings | Chọn *Select existing security group* → `datn-sg`; *Auto-assign public IP*: Enable |
| Configure storage | Đổi `8` thành **100** GiB, **gp3**, giữ 3000 IOPS mặc định. *Advanced* → **Encrypted** (KMS key mặc định `aws/ebs`, miễn phí), *Delete on termination*: Yes. Không *Add new volume*; *File systems*: **None** |
| Advanced details → IAM instance profile | **Để trống** (máy không cần quyền AWS nào) |
| Advanced details → Metadata version | **V2 only (token required)** |
| Advanced details → Metadata response hop limit | **1** (container và pod không gọi được metadata của EC2) |
| Advanced details → Termination protection | **Enable** (tránh xoá nhầm) |

Bấm **Launch**, chờ trạng thái *Running* và *2/2 checks passed*.

### 2.5. Elastic IP

IP công khai mặc định **đổi mỗi lần tắt/bật máy**, mà mọi tên host đều dựng từ IP, nên bắt buộc dùng IP tĩnh:
1. EC2 → **Elastic IPs → Allocate Elastic IP address**: *Amazon's pool of IPv4 addresses*, *Network border group* để mặc định (đúng region), không tạo Global Accelerator → **Allocate**.
2. Chọn IP vừa cấp → **Actions → Associate Elastic IP address** → Resource type *Instance* → `datn-server` → **Associate**.

Ghi lại IP này (`<EIP>`) và tính `BASE`:

```bash
EIP=52.62.135.59                                 # <- IP thật của bạn
echo "BASE=$(echo $EIP | tr . -).sslip.io"       # BASE=52-62-135-59.sslip.io
```

### 2.6. SSH vào máy

```powershell
ssh -i "$env:USERPROFILE\.ssh\datn-ec2.pem" ubuntu@<EIP>
```

Vào được thì kiểm tra hệ điều hành: `lsb_release -d` phải ra `Ubuntu 24.04…`. Gặp lỗi xem [Sự cố](#sự-cố-riêng-trên-ec2--chế-độ-ip) (quyền file `.pem`, nhầm AMI, *REMOTE HOST IDENTIFICATION HAS CHANGED*).

**Hết phần A.**

---

# Phần B. Hệ thống DATN

## Bước 3. Kiểm tra tên host

Máy dev:

```powershell
nslookup staging.52-62-135-59.sslip.io      # Address: 52.62.135.59  (thay bằng BASE của bạn)
nslookup sso-staging.52-62-135-59.sslip.io
```

## Bước 4. Chuẩn bị repo (máy dev)

### 4.1. Ghi IP vào chart

`infrastructure/helm/*/values-staging.yaml`, `values-prod.yaml` đang dùng `https://….example.com`. Script `set-public-host.sh` đổi một lần cả tên host, giao thức và cờ cookie `Secure` (workflow và `deploy.sh` tự đọc URL từ đây):

```bash
# Git Bash, ở gốc repo, trên nhánh feat/cicd-helm-jenkins
bash infrastructure/scripts/set-public-host.sh 52-62-135-59.sslip.io http
```

Script in lại mọi URL/host sau khi đổi; phần *Còn sót tên cũ* phải là `(không còn)`. Kiểm tra nhanh:

```bash
git diff --stat           # 4 file values thay đổi
grep -n "cookieSecure" infrastructure/helm/datn-app/values-*.yaml    # false (vì chạy HTTP)
```

Sửa thêm bằng tay:

| File | Sửa |
|---|---|
| `infrastructure/helm/datn-app/values-prod.yaml` | `mail.from`: địa chỉ Gmail dùng gửi mail, ví dụ `"DATN-2026 <ten.ban@gmail.com>"` (Gmail từ chối địa chỉ gửi khác tài khoản) |
| `infrastructure/helm/datn-app/values.yaml`, `.github/workflows/ci-cd.yml`, `.github/workflows/release.yml`, `infrastructure/scripts/build-images.sh` | Chỉ khi owner GitHub **không phải** `Hecker-Chuoi`: đổi `ghcr.io/hecker-chuoi` (chữ thường) |

> **Không** ghi IP của nhóm (IP được vào công cụ quản trị) vào repo: chúng nằm trong file riêng trên máy chủ (bước 7.4). IP của server thì ghi vào repo được, vì ai mở web cũng thấy.

### 4.2. Chuyển repo sang public và merge

1. Làm **[04 §1](04_HUONG_DAN_GITHUB_ACTIONS.md)**: xoay client secret Google, kiểm tra lịch sử Git, chuyển repo sang **public**, cài đặt Actions.
2. Commit, push, mở PR từ `feat/cicd-helm-jenkins` vào `main`. Workflow **CI/CD** chạy 3 check trên PR (`Backend`, `Frontend`, `Helm chart`) và phải xanh.
3. **Chưa merge vội**: merge vào `main` sẽ kích hoạt build + deploy staging, mà lúc này cluster chưa có. Merge ở bước 10.

## Bước 5. Chuẩn bị máy chủ

### 5.1. Tạo user quản trị `datnadmin`

Cluster (minikube) và kubeconfig admin thuộc về user `datnadmin`. SSH bằng `ubuntu` rồi tạo:

```bash
sudo adduser --disabled-password --gecos "" datnadmin
sudo usermod -aG sudo datnadmin
echo "datnadmin ALL=(ALL) NOPASSWD:ALL" | sudo tee /etc/sudoers.d/90-datnadmin
sudo install -d -m 700 -o datnadmin -g datnadmin /home/datnadmin/.ssh
sudo install -m 600 -o datnadmin -g datnadmin ~/.ssh/authorized_keys /home/datnadmin/.ssh/authorized_keys
exit
```

Cho tiện, thêm vào `C:\Users\<bạn>\.ssh\config` rồi từ giờ SSH bằng `ssh datn`:

```
Host datn
    HostName <EIP>
    User datnadmin
    IdentityFile ~/.ssh/datn-ec2.pem
```

### 5.2. Cập nhật hệ thống, cài công cụ

```bash
sudo apt-get update && sudo apt-get -y upgrade
sudo timedatectl set-timezone Asia/Ho_Chi_Minh          # cron sao lưu 02:00 theo giờ Việt Nam
sudo hostnamectl set-hostname datn-server

# Nhiều pod: tăng giới hạn inotify (tránh lỗi "too many open files" trong minikube)
cat <<'EOF' | sudo tee /etc/sysctl.d/90-datn.conf
fs.inotify.max_user_watches = 524288
fs.inotify.max_user_instances = 512
EOF
sudo sysctl --system
```

Rồi làm **[03 §1](03_HUONG_DAN_TRIEN_KHAI.md)** (gói apt, Docker, kubectl, minikube, Helm 4.3.2), với 3 điểm khác:
1. **Bỏ qua dòng `ufw`.** Security Group đã là firewall; bật thêm `ufw` có thể chặn mạng của container minikube.
2. Lệnh `newgrp docker` mở một shell mới: thay bằng `exit` rồi `ssh datn` lại để nhóm `docker` có hiệu lực.
3. Lệnh `git clone` ở cuối §1 để sang 5.3.

Docker đã có (ví dụ cài từ trước) thì `curl … get.docker.com` vẫn chạy lại được; chỉ cần đảm bảo `datnadmin` thuộc nhóm `docker`.

```bash
docker run --rm hello-world | head -2
kubectl version --client && minikube version && helm version
```

### 5.3. Lấy mã nguồn

Repo đã public (bước 4.2):

```bash
sudo mkdir -p /opt/datn && sudo chown datnadmin: /opt/datn
git clone https://github.com/Hecker-Chuoi/DATN-2026.git /opt/datn
cd /opt/datn && git checkout feat/cicd-helm-jenkins     # sau khi merge (bước 10) thì: git checkout main && git pull
grep -m1 "host:" infrastructure/helm/datn-app/values-staging.yaml   # host: staging.<BASE>
```

## Bước 6. Khởi động minikube

Làm theo **[03 §2](03_HUONG_DAN_TRIEN_KHAI.md)** bằng user `datnadmin`, với cỡ máy ở bước 2.1, ví dụ máy 32 GiB:

```bash
minikube start --driver=docker --cni=calico --cpus=6 --memory=14g --disk-size=60g
```

Bật addon `ingress`, `metrics-server`, patch ConfigMap `ingress-nginx-controller`, tạo unit systemd `minikube.service` đúng như 03 §2.

Kiểm tra:

```bash
kubectl get nodes                                  # Ready
kubectl -n ingress-nginx get pods                  # controller Running
minikube ip                                        # thường 192.168.49.2; khác thì nhớ sửa ở bước 8 và 9
sudo systemctl is-enabled minikube                 # enabled
```

## Bước 7. Secret và `datn-platform`

Đặt sẵn biến cho các lệnh bên dưới (thay bằng BASE thật):

```bash
BASE=52-62-135-59.sslip.io
cd /opt/datn && kubectl apply -f infrastructure/k8s/namespaces.yaml
```

### 7.1. Token GitHub cho cluster

GitHub → Settings → Developer settings → **Personal access tokens (classic)** → token `cluster-ghcr`, scope **`read:packages`**, hạn 90 ngày. Dùng ở 7.2 (Secret `ghcr-pull`, để cluster kéo image private). Đây là token duy nhất phải tự tạo: workflow push image bằng `GITHUB_TOKEN` có sẵn.

### 7.2. Secret cho staging

Làm **[03 §6.1 → §6.4](03_HUONG_DAN_TRIEN_KHAI.md)** với `ENV=staging`:

| Mục | Ghi chú cho chế độ tạm (HTTP) |
|---|---|
| 6.1 `app-secrets` | `PGADMIN_EMAIL`: email thật của bạn (pgAdmin từ chối tên miền đặc biệt như `.local`) |
| 6.2 `keycloak-realm` | **Dùng lệnh dưới thay cho lệnh ở 03 §6.2**: `APP_URL` là `http://…` và có thêm `sslRequired = "none"` |
| 6.3 `ghcr-pull` | `--docker-username=<user GitHub>`, `--docker-password=<token cluster-ghcr>` |
| 6.4 `admin-basic-auth` | Tạo user basic auth, ví dụ `quantri` |

Lệnh 6.2 đầy đủ cho chế độ HTTP (đã chạy thử biểu thức jq trên file realm của repo):

```bash
source ~/datn-secrets/$ENV.env
APP_URL=http://staging.$BASE                  # prod: http://app.$BASE
jq --arg secret "$KEYCLOAK_CLIENT_SECRET" --arg app "$APP_URL" '
  (.clients[] | select(.clientId == "datn-backend")) |= (
      .secret = $secret
    | .redirectUris = [$app + "/*"]
    | .webOrigins = [$app]
    | .attributes["post.logout.redirect.uris"] = ($app + "/*"))
  | .sslRequired = "none"
' infrastructure/keycloak/realm-export_08082026.json > ~/datn-secrets/realm-$ENV.json
jq -r '.sslRequired, (.clients[] | select(.clientId=="datn-backend") | .redirectUris[0])' ~/datn-secrets/realm-$ENV.json
# none
# http://staging.<BASE>/*
kubectl -n datn-$ENV create secret generic keycloak-realm --from-file=realm.json=$HOME/datn-secrets/realm-$ENV.json
```

> Thiếu `sslRequired = "none"` thì người dùng truy cập từ Internet qua HTTP sẽ gặp lỗi **"HTTPS required"** khi đăng nhập. Keycloak chỉ import realm ở **lần khởi động đầu**; quên bước này thì sửa trong Admin Console (*Realm settings → General → Require SSL: none*) hoặc bằng lệnh ở 7.6 (đổi `realms/master` thành `realms/datn-realm`).

### 7.3. Secret cho prod

Lặp lại 03 §6.1 → §6.4 và lệnh 6.2 ở trên với `ENV=prod`, `APP_URL=http://app.$BASE`. Khác staging:
- `MAIL_USERNAME` = địa chỉ Gmail, `MAIL_PASSWORD` = **App Password** của Gmail (Google Account → Security → 2-Step Verification → App passwords). EC2 chặn cổng 25 nhưng cho phép 587, đúng cổng chart dùng.
- Mật khẩu mọi thứ khác **sinh mới**, không dùng lại của staging.

Sao lưu cả thư mục `~/datn-secrets` vào trình quản lý mật khẩu của nhóm. Mất file này thì phải đặt lại mật khẩu DB bằng tay.

### 7.4. Giới hạn IP cho công cụ quản trị (rất nên làm khi chạy HTTP)

IP của nhóm để ngoài repo, trong file riêng trên máy chủ:

```bash
for ENV in staging prod; do
cat > ~/datn-secrets/platform-$ENV-local.yaml <<'EOF'
# IP/CIDR được vào pgAdmin, Mailpit, MinIO Console (cùng basic auth) và Keycloak /admin. Rỗng = không giới hạn IP.
adminAccess:
  allowCidrs: ""            # ví dụ "113.161.1.2/32,14.232.0.0/16"
keycloak:
  adminAllowCidrs: ""
EOF
done
```

Tra IP hiện tại của bạn: `curl -s https://checkip.amazonaws.com` (chạy trên máy cá nhân). IP nhà mạng thường đổi; nếu không cố định được thì để trống và dựa vào basic auth + mật khẩu của từng công cụ, hoặc dùng SSH tunnel ([03 §9.4](03_HUONG_DAN_TRIEN_KHAI.md)).

### 7.5. Cài `datn-platform`

Tạo script để lần nào nâng cấp cũng dùng đúng tham số:

```bash
cat > ~/datn-secrets/upgrade-platform.sh <<'EOF'
#!/usr/bin/env bash
# Cách dùng: upgrade-platform.sh staging|prod   (chạy bằng kubeconfig admin, driver Secret mặc định)
set -euo pipefail
ENV="${1:?staging|prod}"
cd /opt/datn && git pull --ff-only
helm upgrade --install datn-platform infrastructure/helm/datn-platform -n "datn-$ENV" \
  -f "infrastructure/helm/datn-platform/values-$ENV.yaml" \
  -f "$HOME/datn-secrets/platform-$ENV-local.yaml" \
  --wait --timeout 15m
kubectl -n "datn-$ENV" get pods,pvc,ing
EOF
chmod 700 ~/datn-secrets/upgrade-platform.sh

~/datn-secrets/upgrade-platform.sh staging
~/datn-secrets/upgrade-platform.sh prod
```

Kiểm tra (chưa cần Nginx máy chủ):

```bash
for e in staging prod; do kubectl -n datn-$e get pods; done          # mọi pod 1/1 Running, RESTARTS 0
curl -s -H "Host: sso-staging.$BASE" http://$(minikube ip)/realms/datn-realm/.well-known/openid-configuration | jq -r .issuer
# Kỳ vọng: http://sso-staging.<BASE>/realms/datn-realm
```

Keycloak lần đầu mất 1–2 phút để tự build và import realm. Nếu thấy `OOMKilled`, xem [03 §14.2](03_HUONG_DAN_TRIEN_KHAI.md).

### 7.6. Admin Console của Keycloak qua HTTP (chỉ khi cần)

Realm `master` (tài khoản `admin` của Admin Console) do Keycloak tự tạo, vẫn bắt HTTPS với truy cập từ Internet. Muốn mở `http://sso-staging.$BASE/admin` thì tắt yêu cầu đó bằng lệnh chạy **bên trong pod** (truy cập từ `localhost` luôn được phép):

```bash
ENV=staging; source ~/datn-secrets/$ENV.env
kubectl -n datn-$ENV exec deploy/keycloak -- bash -c "
  /opt/keycloak/bin/kcadm.sh config credentials --config /tmp/kcadm.config \
    --server http://localhost:8080 --realm master --user admin --password '$KC_ADMIN_PASSWORD' &&
  /opt/keycloak/bin/kcadm.sh update realms/master --config /tmp/kcadm.config -s sslRequired=NONE &&
  /opt/keycloak/bin/kcadm.sh get realms/master --config /tmp/kcadm.config --fields sslRequired"
# { "sslRequired" : "none" }      (đã chạy thử trên Keycloak 24.0.1)
```

Đăng nhập Admin Console bằng user `admin` và `KC_ADMIN_PASSWORD`. Nhớ đặt lại `EXTERNAL` khi chuyển sang HTTPS (mục cuối).

## Bước 8. Nginx cho ứng dụng (HTTP)

Tạo `/etc/nginx/sites-available/datn` theo **[03 §8](03_HUONG_DAN_TRIEN_KHAI.md)**, chỉ khác dòng `server_name` (và `proxy_pass` nếu `minikube ip` khác `192.168.49.2`). Sinh sẵn dòng `server_name` đúng BASE:

```bash
echo "server_name app.$BASE sso.$BASE pgadmin.$BASE staging.$BASE sso-staging.$BASE pgadmin-staging.$BASE mail-staging.$BASE;"
```

Bật site như 03 §8 (`ln -s …`, xoá site `default`, `nginx -t`, `reload`). **Bỏ qua lệnh `certbot`** (chế độ HTTP).

Kiểm tra từ **máy cá nhân** (Git Bash):

```bash
BASE=52-62-135-59.sslip.io
curl -s -o /dev/null -w "%{http_code}\n" http://sso-staging.$BASE/realms/datn-realm      # 200
curl -s -o /dev/null -w "%{http_code}\n" http://pgadmin-staging.$BASE/                   # 401 (basic auth) hoặc 403 (ngoài IP cho phép)
curl -s -o /dev/null -w "%{http_code}\n" http://staging.$BASE/                           # 503: chưa có datn-app, bước 10 sẽ cài
```

**Hết phần B.** Cluster và hạ tầng của dự án đã chạy, chưa có ứng dụng.

---

# Phần C. GitHub Actions

## Bước 9. Cấu hình

Làm **[04 §2 → §5](04_HUONG_DAN_GITHUB_ACTIONS.md)**:

| Mục 04 | Việc | Ở đâu |
|---|---|---|
| §2 | User `tunnel`, khoá SSH chỉ port-forward tới API minikube; khối `Match` trong sshd; tự kiểm tra; **Security Group: đổi rule 22 thành `0.0.0.0/0`** | Máy chủ + AWS |
| §3 | Kubeconfig `ci-deployer` cho staging và prod (`make-ci-kubeconfig.sh … https://127.0.0.1:8443`) | Máy chủ |
| §4 | Environment `staging`, `production` (required reviewers), secret `KUBE_CONFIG`, `DEPLOY_SSH_KEY`, biến `DEPLOY_SSH_HOST`, `DEPLOY_SSH_KNOWN_HOSTS` | GitHub |
| §5 | Ruleset bảo vệ `main` | GitHub |

## Bước 10. Deploy lần đầu

### 10.1. Staging

Merge PR của bước 4.2 vào `main`. **Actions → CI/CD**: 3 check → 6 job `Image (…)` song song → `Deploy staging`. Lần đầu 10–20 phút. `datn-app` **không cần cài bằng tay**: `deploy.sh` tự `helm upgrade --install` bằng quyền `ci-deployer`. Chi tiết và cách đọc log: [04 §6.1](04_HUONG_DAN_GITHUB_ACTIONS.md).

Trên máy chủ chuyển `/opt/datn` sang `main`: `cd /opt/datn && git checkout main && git pull`.

Kiểm tra bằng trình duyệt `http://staging.<BASE>` (trình duyệt sẽ báo "Not secure", đúng với chế độ HTTP):
- Đăng ký tài khoản → email xác thực trong `http://mail-staging.<BASE>` (basic auth) → bấm link → đăng nhập.
- Tạo workspace, dự án, công việc; tải đính kèm.
- Thông báo realtime: DevTools → Network → WS `/notification/ws` trả `101`.

### 10.2. Prod

```bash
# máy dev, khi staging đã ổn
git checkout main && git pull
git tag -a v1.0.0 -m "Phát hành v1.0.0" && git push origin v1.0.0
```

**Actions → Release** → `Deploy production` chờ duyệt → người trong *Required reviewers* bấm **Approve and deploy** → kiểm tra `http://app.<BASE>` giống 10.1 (email thật gửi qua Gmail).

Deploy lại, rollback: [04 §6.3](04_HUONG_DAN_GITHUB_ACTIONS.md).

---

# Phần D. Vận hành

## Bước 11. Sao lưu, nghiệm thu, tiết kiệm

### 11.1. Sao lưu

| Lớp | Cách làm |
|---|---|
| DB (logic) | Cron `backup-db.sh` lúc 02:00 cho `datn-prod` (và `datn-staging` nếu muốn): [03 §14.1](03_HUONG_DAN_TRIEN_KHAI.md) |
| Cả ổ đĩa EC2 | EC2 → **Lifecycle Manager → Create lifecycle policy** → *EBS snapshot policy*, target: volume gắn với `datn-server` (gắn tag `Backup=datn` cho volume rồi chọn theo tag), lịch hằng ngày 03:00, giữ 7 bản |
| Bí mật | `~/datn-secrets` trong trình quản lý mật khẩu (bước 7.3) |

Snapshot EBS chụp cả dữ liệu Postgres, Kafka, MinIO (nằm trong container minikube). Khôi phục máy hỏng: tạo volume từ snapshot, gắn vào instance mới (và gắn lại **đúng Elastic IP cũ**, vì tên host dựng từ IP).

### 11.2. Nghiệm thu

Chạy các kịch bản **T1 → T12** ở [03 §13](03_HUONG_DAN_TRIEN_KHAI.md) và **G1 → G7** ở [04 §7](04_HUONG_DAN_GITHUB_ACTIONS.md), thay `https://….example.com` bằng `http://….<BASE>`. Chụp màn hình/kết quả làm minh chứng.

### 11.3. Tắt / bật máy để tiết kiệm

- **Tắt:** EC2 → Instance state → **Stop** (không chọn *Terminate*: Terminate là xoá máy). Khi dừng chỉ trả tiền đĩa và Elastic IP. **Không** *Release* Elastic IP.
- **Bật:** **Start**. `minikube.service` tự khởi động cluster (1–3 phút). Elastic IP giữ nguyên nên tên host không đổi.
- Kiểm tra sau khi bật: `kubectl get pods -A | grep -v Running` (chỉ còn dòng tiêu đề và pod `Completed`), rồi mở `http://staging.<BASE>`.
- Máy đang tắt mà có người merge vào `main`: job `Deploy staging` lỗi `Connection timed out`. Bật máy rồi *Re-run failed jobs*.

### 11.4. Định kỳ

| Tần suất | Việc |
|---|---|
| Hằng tuần | `df -h /` và `minikube ssh -- df -h /` (đĩa < 80%) |
| Hằng tháng | `sudo apt-get update && sudo apt-get -y upgrade`; xem báo cáo Trivy trong log job `Image (…)` |
| Trước 90 ngày | Xoay token `cluster-ghcr` (Secret `ghcr-pull`); cân nhắc xoay `DEPLOY_SSH_KEY` và token `ci-deployer` ([04 §2.3, §3](04_HUONG_DAN_GITHUB_ACTIONS.md)) |

---

## Chuyển sang tên miền thật và HTTPS

Làm khi đã mua tên miền (ví dụ `tenmien.vn`). Dữ liệu giữ nguyên; người dùng chỉ phải đăng nhập lại (issuer của token đổi).

1. **DNS:** tạo bản ghi A `*.tenmien.vn` (hoặc từng tên `app`, `sso`, `pgadmin`, `staging`, `sso-staging`, `pgadmin-staging`, `mail-staging`) → `<EIP>`. Kiểm tra `nslookup staging.tenmien.vn`.
2. **Repo (máy dev):** `bash infrastructure/scripts/set-public-host.sh tenmien.vn https` (đổi host, `https`, `cookieSecure: true`), commit, PR. **Chưa merge** (merge ở bước 6).
3. **Nginx + chứng chỉ (máy chủ):** đổi `server_name` trong `/etc/nginx/sites-available/datn` sang tên mới, `sudo nginx -t && sudo systemctl reload nginx`, rồi chạy lệnh `certbot --nginx` ở 03 §8.
4. **Platform:** sau khi merge (bước 6), `~/datn-secrets/upgrade-platform.sh staging` rồi `prod` (Keycloak nhận `hostnameUrl` mới). Có thể chạy ngay trên nhánh PR: `cd /opt/datn && git fetch && git checkout <nhánh>` trước khi chạy script.
5. **Keycloak (Admin Console, mỗi môi trường):**
   - Realm `datn-realm` → *Realm settings → General → Require SSL* = **external requests**. Realm `master`: tương tự (hoặc lệnh 7.6 với `sslRequired=EXTERNAL`).
   - *Clients → datn-backend*: *Valid redirect URIs* = `https://staging.tenmien.vn/*` (prod: `https://app.tenmien.vn/*`), *Web origins* = `https://staging.tenmien.vn`, *Valid post logout redirect URIs* tương tự. (Realm đã import từ lần đầu nên **không** tự cập nhật theo file.)
   - Lúc này mới bật được đăng nhập Google: nhập client secret Google mới ở *Identity providers → google*, thêm redirect URI `https://sso-staging.tenmien.vn/realms/datn-realm/broker/google/endpoint` (và bản `sso.tenmien.vn`) trên Google Cloud Console.
6. **Deploy lại ứng dụng:** merge PR của bước 2 → workflow tự deploy staging với URL mới; prod: *Deploy thủ công* với `target=production`, `action=deploy`, `image_tag=<sha đang chạy ở prod>` (xem `HELM_DRIVER=configmap helm -n datn-prod get values datn-app`).
7. Security Group: **giữ** cổng 80 để Let's Encrypt gia hạn và chuyển hướng HTTP → HTTPS. Cổng 22 vẫn mở cho GitHub Actions.

---

## Sự cố riêng trên EC2 / chế độ IP

| Triệu chứng | Nguyên nhân | Cách xử lý |
|---|---|---|
| Launch báo `VcpuLimitExceeded` | Hạn mức vCPU thấp | Bước 1, xin tăng lên 8 |
| SSH `Bad permissions … BUILTIN\Users`, `UNPROTECTED PRIVATE KEY FILE` | File `.pem` kế thừa quyền của thư mục (ví dụ ổ `D:`) | Chuyển vào `%USERPROFILE%\.ssh` và chạy lệnh `icacls` ở bước 2.2. Không để file khoá trong thư mục repo |
| SSH `ubuntu@…: Permission denied (publickey,gssapi-keyex,gssapi-with-mic)`, trang *Connect* gợi ý `ec2-user` | Máy tạo nhầm AMI **Amazon Linux** (mặc định của Launch instance) | Thử `ec2-user@<EIP>`: thấy *Amazon Linux* thì tắt Termination protection, Terminate, Launch lại với **Ubuntu Server 24.04 LTS**, gắn lại đúng Elastic IP |
| SSH `WARNING: REMOTE HOST IDENTIFICATION HAS CHANGED!` ngay sau khi tạo lại máy | Máy mới dùng lại IP cũ nhưng host key mới | `ssh-keygen -R <EIP>` (và `ssh-keygen -R ec2-…compute.amazonaws.com`), SSH lại, gõ `yes`. Nếu **không** vừa tạo lại máy thì đừng bỏ qua cảnh báo này. Tạo lại máy thì cũng phải cập nhật biến `DEPLOY_SSH_KNOWN_HOSTS` (04 §2.3) |
| SSH `Connection timed out` | IP của bạn đổi trong khi rule 22 còn là *My IP* | Security Group → sửa rule 22 (sau bước 9 là `0.0.0.0/0`) |
| Đăng nhập báo **"HTTPS required"** | Realm còn `sslRequired=external` mà đang chạy HTTP | Realm ứng dụng: bước 7.2 (hoặc Admin Console); Admin Console: lệnh 7.6 |
| Tên `*.sslip.io` không phân giải | Mạng chặn DNS lạ, hoặc gõ sai định dạng IP | `nslookup staging.<BASE> 8.8.8.8`; IP phải viết bằng gạch ngang (`52-62-135-59`) |
| Mọi tên host hỏng sau khi bật lại máy | Elastic IP bị gỡ (Release/Disassociate) nên IP đổi | Gắn lại đúng EIP cũ; nếu buộc phải đổi IP: làm lại bước 4.1, 7.2 (realm qua Admin Console), 8, và biến `DEPLOY_SSH_HOST`, `DEPLOY_SSH_KNOWN_HOSTS` |
| Mọi trang 502 sau khi bật lại máy | minikube chưa lên xong, hoặc `minikube ip` đổi | `systemctl status minikube`; so `minikube ip` với `proxy_pass` trong `/etc/nginx/sites-available/datn` và `permitopen` của user `tunnel` (04 §2) |
| Pod `Pending`, `Insufficient cpu` | Máy không đủ cho cả 2 môi trường | Đúng cỡ máy ở bước 2.1; tạm `kubectl -n datn-staging scale deploy --all --replicas=0` |
| Không gửi được email prod | Dùng cổng 25 (EC2 chặn) hoặc sai App Password | Chart dùng 587; tạo lại App Password, cập nhật `app-secrets` |
| Hoá đơn cao bất thường | Máy chạy 24/7, CPU credit của `t3` | Bước 11.3; Billing → Cost Explorer; cân nhắc `m7i-flex` |

Các sự cố về Kubernetes, Helm, Keycloak: [03 §14.2](03_HUONG_DAN_TRIEN_KHAI.md). Về GitHub Actions và đường hầm SSH: [04 §8](04_HUONG_DAN_GITHUB_ACTIONS.md).
