# 05 — Triển khai từ đầu trên AWS EC2 (runbook)

> Dành cho người dựng máy chủ. Làm **theo đúng thứ tự**; mỗi bước có lệnh cần chạy và cách kiểm tra trước khi sang bước sau.
> Tài liệu này là **trình tự**. Nội dung chi tiết nằm ở [03](03_HUONG_DAN_TRIEN_KHAI.md) (cluster, chart) và [04](04_HUONG_DAN_SETUP_JENKINS.md) (Jenkins); chỗ nào trỏ sang thì làm đúng mục đó rồi quay lại đây.
> Mọi file chart, script, Jenkinsfile **đã có trong repo** và đã chạy thử trên cluster thật ([03 §13.1](03_HUONG_DAN_TRIEN_KHAI.md)).
>
> **Chế độ hiện tại: TẠM DÙNG IP CỦA SERVER, CHƯA CÓ TÊN MIỀN, CHẠY HTTP.** Khi có tên miền thật, làm [mục cuối](#chuyển-sang-tên-miền-thật-và-https) để chuyển sang HTTPS.

## Tổng quan

Bốn phần, mỗi phần **độc lập** với phần sau:

| Phần | Bước | Việc | Cần repo dự án? |
|---|---|---|---|
| **A. Server** | 1–2 | Tài khoản AWS, tạo EC2, Elastic IP, SSH vào được | Không |
| **B. Jenkins** | 3 | Cài Jenkins **bằng tay trên server** (04 phần A). Xong: Jenkins chạy, chưa có job | Không |
| **C. Hệ thống DATN** | 4–9 | Ghi IP vào repo, minikube, Secret, `datn-platform`, Nginx cho ứng dụng | Có |
| **D. Nối Jenkins với dự án** | 10–11 | Token GitHub, credential, job, webhook (04 phần B); deploy staging, prod | Có |
| E. Vận hành | 12 | Sao lưu, nghiệm thu, tắt/bật máy | |

---

## Tên host khi chưa có tên miền

Hệ thống phân biệt app, Keycloak, pgAdmin, staging, prod, Jenkins… bằng **tên host**, nên không thể dùng một địa chỉ `http://<IP>` trần cho tất cả. Thay vào đó dùng dịch vụ DNS miễn phí **[sslip.io](https://sslip.io)**: mọi tên có dạng `<gì-đó>.<IP-viết-gạch-ngang>.sslip.io` tự trỏ về đúng IP đó. Không cần mua tên miền, không cần cấu hình DNS.

Ví dụ Elastic IP là `52.62.135.59` thì **tên gốc** (gọi là `BASE`) là `52-62-135-59.sslip.io`:

| Tên | Dùng cho |
|---|---|
| `http://jenkins.BASE` | Jenkins (chỉ mở webhook GitHub; giao diện vào qua SSH tunnel) |
| `http://staging.BASE` / `http://app.BASE` | Ứng dụng staging / prod |
| `http://sso-staging.BASE` / `http://sso.BASE` | Keycloak staging / prod |
| `http://pgadmin-staging.BASE` / `http://pgadmin.BASE` | pgAdmin staging / prod |
| `http://mail-staging.BASE` | Mailpit (staging) |

**Giới hạn của chế độ tạm (chấp nhận được cho giai đoạn phát triển, không dùng cho người dùng thật):**
- **Không có HTTPS.** Let's Encrypt giới hạn số chứng chỉ theo tên miền gốc; `sslip.io` không nằm trong Public Suffix List (đã kiểm tra 24/11/2026) nên hạn mức dùng chung cho mọi người trên thế giới và thường đã cạn. Mật khẩu đi qua mạng dạng chữ thường → vào Jenkins qua SSH tunnel, **nên giới hạn IP** cho công cụ quản trị (bước 8.4).
- **Đăng nhập Google không dùng được**: Google chỉ chấp nhận redirect URI `https` (trừ `localhost`). Đăng ký/đăng nhập bằng mật khẩu và email xác thực vẫn chạy bình thường.
- Keycloak phải tắt yêu cầu HTTPS (`sslRequired=none`) ở realm ứng dụng và realm `master` (bước 8.2, 8.6).
- **Giữ nguyên Elastic IP.** Đổi IP là đổi toàn bộ tên host.

---

# Phần A. Server

## Bước 1. Tài khoản AWS

1. **Region:** nên chọn **Asia Pacific (Singapore) `ap-southeast-1`**, gần Việt Nam nhất. Đã tạo ở region khác (ví dụ Sydney `ap-southeast-2`) vẫn chạy được, chỉ có độ trễ cao hơn. Mọi tài nguyên (máy, Elastic IP, Security Group) phải **cùng một region**.
2. **Hạn mức vCPU:** tài khoản mới thường chỉ được chạy ít vCPU. Vào **Service Quotas → Amazon EC2 → Running On-Demand Standard (A, C, D, H, I, M, R, T, Z) instances**. Giá trị phải **≥ 8**, nếu thấp hơn thì bấm *Request increase* lên 8 (thường được duyệt trong vài giờ tới 1 ngày).
3. **Cảnh báo chi phí:** **Billing → Budgets → Create budget** → *Monthly cost budget*, đặt mức bạn chấp nhận được (ví dụ 100 USD), nhận email khi chạm 80%.

## Bước 2. Tạo EC2

### 2.1. Chọn cỡ máy

Hệ thống gồm staging + prod (mỗi bên khoảng 2 core / 4,5–4,9 GiB requests) + thành phần Kubernetes + Jenkins và build image ([02 §6](02_KE_HOACH_TRIEN_KHAI_CICD.md)).

| Lựa chọn | Instance | vCPU / RAM | Dùng khi |
|---|---|---|---|
| **Khuyến nghị** | `t3.2xlarge` (hoặc `m7i-flex.2xlarge`) | 8 / 32 GiB | Chạy cả staging, prod và Jenkins |
| Tối thiểu | `t3.xlarge` | 4 / 16 GiB | Chỉ **một** môi trường (ví dụ chỉ staging) + Jenkins. Phải giảm `--cpus=3 --memory=9g` ở bước 7, thêm swap, Jenkins 1 executor |

> Giá tham khảo (chạy 24/7): `t3.2xlarge` khoảng 300 USD/tháng, đĩa 100 GiB khoảng 10 USD/tháng, Elastic IP khoảng 4 USD/tháng. Kiểm tra lại trên [AWS Pricing Calculator](https://calculator.aws/). **Tắt máy khi không dùng** (bước 12.3) để chỉ trả tiền đĩa và IP.
> `t3` là dòng "burst": build liên tục lâu có thể tính thêm phí CPU credit. `m7i-flex` đắt hơn chút nhưng không có khái niệm credit.

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
| SSH | 22 | **My IP** | Chỉ máy của bạn SSH được. Đổi mạng thì sửa lại rule này |
| HTTP | 80 | `0.0.0.0/0` | Ứng dụng, Keycloak, công cụ quản trị, webhook GitHub (chế độ tạm chạy HTTP) |
| HTTPS | 443 | `0.0.0.0/0` | Chưa dùng; mở sẵn cho lúc chuyển sang tên miền thật |

**Không** mở thêm cổng nào khác (8080, 8443, 5432…): mọi thứ đi qua Nginx máy chủ. Outbound để mặc định (cho phép hết).

### 2.4. Launch instance

EC2 → **Launch instance**:

| Mục | Giá trị |
|---|---|
| Name | `datn-server` |
| Application and OS Images | Tab **Ubuntu** → **Ubuntu Server 24.04 LTS (HVM), SSD Volume Type**, kiến trúc **64-bit (x86)**. **Không** để mặc định Amazon Linux (tài liệu dùng `apt`, AppArmor của Ubuntu); không chọn Arm |
| Instance type | `t3.2xlarge` (bước 2.1) |
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

**Hết phần A.** Server đã sẵn sàng.

---

# Phần B. Jenkins

## Bước 3. Cài Jenkins trên server

Làm **[04 phần A, mục 1 → 9](04_HUONG_DAN_SETUP_JENKINS.md)** ngay trên server (SSH bằng `ubuntu`). Phần này **không cần repo dự án, minikube hay cluster**: chỉ cài Jenkins và công cụ build lên máy.

| Mục 04 | Ghi chú cho EC2, chế độ tạm bằng IP |
|---|---|
| §1 Chuẩn bị máy chủ | Máy 32 GiB không cần swap |
| §2 Java + Jenkins | |
| §3 systemd | Giữ `MemoryMax=4G` |
| §4 Docker rootless | Ubuntu 24.04: **bắt buộc** làm §4.2 (AppArmor). §4.1 tự cài Docker Engine nếu máy chưa có |
| §5 Công cụ | kubectl, Helm 4.3.2, kubeconform, Trivy (Trivy hiện chỉ báo cáo, không chặn build) |
| §6 Nginx | `server_name jenkins.<BASE>;` (ví dụ `jenkins.52-62-135-59.sslip.io`). **Bỏ qua `certbot`.** Bỏ comment `deny all;` trong `location /` để chỉ mở `/github-webhook/` |
| §7 Plugin | Vào giao diện qua SSH tunnel: `ssh -i "$env:USERPROFILE\.ssh\datn-ec2.pem" -L 8080:127.0.0.1:8080 ubuntu@<EIP>` rồi mở `http://localhost:8080`. *Jenkins URL* = `http://jenkins.<BASE>/` |
| §8 Bảo mật | Ghi lại user ID quản trị bạn tạo (ví dụ `sonadmin`): cần ở bước 5. Máy 32 GiB: **2 executor** |
| §9 Global Tools | |

Kiểm tra cuối phần A của 04 phải đạt hết, thêm:

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://jenkins.<BASE>/login    # 403: giao diện không mở ra Internet
```

**Hết phần B.** Jenkins chạy độc lập, chưa có job nào. Phần D mới nối nó với dự án.

---

# Phần C. Hệ thống DATN

## Bước 4. Kiểm tra tên host

Máy dev:

```powershell
nslookup staging.52-62-135-59.sslip.io      # Address: 52.62.135.59  (thay bằng BASE của bạn)
nslookup sso-staging.52-62-135-59.sslip.io
```

## Bước 5. Ghi IP vào repo (máy dev)

Các file `Jenkinsfile`, `infrastructure/jenkins/Jenkinsfile.deploy`, `infrastructure/helm/*/values-staging.yaml`, `infrastructure/helm/*/values-prod.yaml` đang dùng `https://….example.com`. Script `set-public-host.sh` đổi một lần cả tên host, giao thức và cờ cookie `Secure`:

```bash
# Git Bash, ở gốc repo, trên nhánh feat/cicd-helm-jenkins
bash infrastructure/scripts/set-public-host.sh 52-62-135-59.sslip.io http
```

Script in lại mọi URL/host sau khi đổi; phần *Còn sót tên cũ* phải là `(không còn)`. Kiểm tra nhanh:

```bash
git diff --stat           # 6 file thay đổi
grep -n "cookieSecure" infrastructure/helm/datn-app/values-*.yaml    # false (vì chạy HTTP)
```

Sửa thêm bằng tay:

| File | Sửa |
|---|---|
| `infrastructure/helm/datn-app/values-prod.yaml` | `mail.from`: địa chỉ Gmail dùng gửi mail, ví dụ `"DATN-2026 <ten.ban@gmail.com>"` (Gmail từ chối địa chỉ gửi khác tài khoản) |
| `infrastructure/jenkins/Jenkinsfile.deploy` | `submitter: 'truongnhom'` → user ID Jenkins đã tạo ở bước 3 (04 §8). Nhiều người: `'sonadmin,tuananh'` |
| `infrastructure/helm/datn-app/values.yaml`, `Jenkinsfile`, `infrastructure/scripts/build-images.sh` | Chỉ khi owner GitHub **không phải** `Hecker-Chuoi`: đổi `ghcr.io/hecker-chuoi` (chữ thường) và URL repo |

Commit, push, mở PR từ `feat/cicd-helm-jenkins` vào `main`, review rồi **merge**. Từ đây máy chủ và Jenkins chỉ dùng nhánh `main`.

> **Không** ghi IP của nhóm (IP được vào công cụ quản trị) vào repo: chúng nằm trong file riêng trên máy chủ (bước 8.4). IP của server thì ghi vào repo được, vì ai mở web cũng thấy.

## Bước 6. Chuẩn bị máy chủ cho cluster

### 6.1. Tạo user quản trị `datnadmin`

Cluster (minikube) và kubeconfig admin thuộc về user `datnadmin`, tách khỏi user `jenkins`. SSH bằng `ubuntu` rồi tạo:

```bash
sudo adduser --disabled-password --gecos "" datnadmin
sudo usermod -aG sudo,docker datnadmin         # nhóm docker: để chạy minikube (CHỈ datnadmin, không phải jenkins)
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

### 6.2. Tinh chỉnh và cài phần còn thiếu

```bash
# Nhiều pod + Jenkins: tăng giới hạn inotify (tránh lỗi "too many open files" trong minikube)
cat <<'EOF' | sudo tee /etc/sysctl.d/90-datn.conf
fs.inotify.max_user_watches = 524288
fs.inotify.max_user_instances = 512
EOF
sudo sysctl --system

# Docker, kubectl, Helm, Nginx đã cài ở bước 3 (04 phần A). Còn thiếu:
sudo apt-get install -y conntrack apache2-utils jq
curl -LO https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64
sudo install minikube-linux-amd64 /usr/local/bin/minikube && rm minikube-linux-amd64

docker run --rm hello-world | head -2          # datnadmin dùng được Docker gốc
minikube version && helm version && kubectl version --client
```

(Đây là phần còn lại của [03 §1](03_HUONG_DAN_TRIEN_KHAI.md). **Không** bật `ufw`: Security Group đã là firewall, `ufw` có thể chặn mạng của container minikube.)

### 6.3. Lấy mã nguồn bằng deploy key

Nếu repo private, dùng **deploy key chỉ đọc** (không cần PAT cá nhân trên máy chủ):

```bash
ssh-keygen -t ed25519 -N "" -C "datn-server deploy key" -f ~/.ssh/datn_deploy
cat ~/.ssh/datn_deploy.pub
```

GitHub → repo → **Settings → Deploy keys → Add deploy key**: dán khoá vừa in, **không** tick *Allow write access*.

```bash
cat >> ~/.ssh/config <<'EOF'
Host github.com
    IdentityFile ~/.ssh/datn_deploy
    IdentitiesOnly yes
EOF
sudo mkdir -p /opt/datn && sudo chown datnadmin: /opt/datn
git clone git@github.com:Hecker-Chuoi/DATN-2026.git /opt/datn
cd /opt/datn && git log --oneline -1          # đúng commit merge ở bước 5
grep -m1 "host:" infrastructure/helm/datn-app/values-staging.yaml   # host: staging.<BASE>
```

Repo public thì clone bằng HTTPS: `git clone https://github.com/Hecker-Chuoi/DATN-2026.git /opt/datn`.

## Bước 7. Khởi động minikube

Làm theo **[03 §2](03_HUONG_DAN_TRIEN_KHAI.md)** bằng user `datnadmin`, với cỡ máy 32 GiB:

```bash
minikube start --driver=docker --cni=calico --cpus=6 --memory=14g --disk-size=60g
```

(máy 16 GiB, một môi trường: `--cpus=3 --memory=9g`). Bật addon `ingress`, `metrics-server`, patch ConfigMap `ingress-nginx-controller`, tạo unit systemd `minikube.service` đúng như 03 §2.

Kiểm tra:

```bash
kubectl get nodes                                  # Ready
kubectl -n ingress-nginx get pods                  # controller Running
minikube ip                                        # thường 192.168.49.2; khác thì nhớ sửa ở bước 9
sudo systemctl is-enabled minikube                 # enabled
```

## Bước 8. Secret và `datn-platform`

Đặt sẵn biến cho các lệnh bên dưới (thay bằng BASE thật):

```bash
BASE=52-62-135-59.sslip.io
cd /opt/datn && kubectl apply -f infrastructure/k8s/namespaces.yaml
```

### 8.1. Token GitHub cho cluster

GitHub → Settings → Developer settings → **Personal access tokens (classic)** → token `cluster-ghcr`, scope **`read:packages`**, hạn 90 ngày. Dùng ở 8.2 (Secret `ghcr-pull`, để cluster kéo image private).

### 8.2. Secret cho staging

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

> Thiếu `sslRequired = "none"` thì người dùng truy cập từ Internet qua HTTP sẽ gặp lỗi **"HTTPS required"** khi đăng nhập. Keycloak chỉ import realm ở **lần khởi động đầu**; quên bước này thì sửa trong Admin Console (*Realm settings → General → Require SSL: none*) hoặc bằng lệnh ở 8.6 (đổi `realms/master` thành `realms/datn-realm`).

### 8.3. Secret cho prod

Lặp lại 03 §6.1 → §6.4 và lệnh 6.2 ở trên với `ENV=prod`, `APP_URL=http://app.$BASE`. Khác staging:
- `MAIL_USERNAME` = địa chỉ Gmail, `MAIL_PASSWORD` = **App Password** của Gmail (Google Account → Security → 2-Step Verification → App passwords). EC2 chặn cổng 25 nhưng cho phép 587, đúng cổng chart dùng.
- Mật khẩu mọi thứ khác **sinh mới**, không dùng lại của staging.

Sao lưu cả thư mục `~/datn-secrets` vào trình quản lý mật khẩu của nhóm. Mất file này thì phải đặt lại mật khẩu DB bằng tay.

### 8.4. Giới hạn IP cho công cụ quản trị (rất nên làm khi chạy HTTP)

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

### 8.5. Cài `datn-platform`

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

### 8.6. Admin Console của Keycloak qua HTTP (chỉ khi cần)

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

## Bước 9. Nginx cho ứng dụng (HTTP)

Nginx đã cài ở bước 3 (và đang phục vụ `jenkins.<BASE>`). Thêm site cho ứng dụng: tạo `/etc/nginx/sites-available/datn` theo **[03 §8](03_HUONG_DAN_TRIEN_KHAI.md)**, chỉ khác dòng `server_name` (và `proxy_pass` nếu `minikube ip` khác `192.168.49.2`). Sinh sẵn dòng `server_name` đúng BASE:

```bash
echo "server_name app.$BASE sso.$BASE pgadmin.$BASE staging.$BASE sso-staging.$BASE pgadmin-staging.$BASE mail-staging.$BASE;"
```

Bật site như 03 §8 (`ln -s …`, xoá site `default`, `nginx -t`, `reload`). **Bỏ qua lệnh `certbot`** (chế độ HTTP).

Thêm bản ghi `/etc/hosts` để các lệnh **chạy ngay trên máy chủ** (smoke test của Jenkins) đi thẳng vào Nginx cục bộ thay vì vòng ra IP công khai:

```bash
echo "127.0.0.1 app.$BASE staging.$BASE sso.$BASE sso-staging.$BASE" | sudo tee -a /etc/hosts
```

Kiểm tra từ **máy cá nhân** (Git Bash):

```bash
BASE=52-62-135-59.sslip.io
curl -s -o /dev/null -w "%{http_code}\n" http://sso-staging.$BASE/realms/datn-realm      # 200
curl -s -o /dev/null -w "%{http_code}\n" http://pgadmin-staging.$BASE/                   # 401 (basic auth) hoặc 403 (ngoài IP cho phép)
curl -s -o /dev/null -w "%{http_code}\n" http://staging.$BASE/                           # 503: chưa có datn-app, bước 11 sẽ cài
curl -s -o /dev/null -w "%{http_code}\n" http://jenkins.$BASE/login                      # 403: Jenkins vẫn chỉ mở webhook
```

**Hết phần C.** Cluster và hạ tầng của dự án đã chạy, chưa có ứng dụng.

---

# Phần D. Nối Jenkins với dự án

## Bước 10. Credential, job, webhook

### 10.1. Token GitHub cho Jenkins

GitHub → Settings → Developer settings → **Personal access tokens (classic)**, hạn 90 ngày:

| Token | Scope | Dùng ở |
|---|---|---|
| `jenkins-repo` | `repo` (repo private) | Credential `github-pat`: Jenkins đọc repo, báo trạng thái PR |
| `jenkins-ghcr` | `write:packages` | Credential `ghcr-push`: Jenkins push image |

### 10.2. Kubeconfig hạn chế quyền

Trên máy chủ, user `datnadmin`:

```bash
cd ~ && bash /opt/datn/infrastructure/scripts/make-ci-kubeconfig.sh datn-staging
bash /opt/datn/infrastructure/scripts/make-ci-kubeconfig.sh datn-prod
```

Mỗi lần chạy in bảng quyền, kỳ vọng `yes yes yes no no no` và `no` ở namespace khác. Tải về máy cá nhân: `scp datn:kubeconfig-datn-* .`

### 10.3. Cấu hình trong Jenkins

Làm **[04 phần B, mục 10 → 13](04_HUONG_DAN_SETUP_JENKINS.md)** (giao diện qua SSH tunnel như bước 3):

| Mục 04 | Ghi chú |
|---|---|
| §10 Folder, credential | `kubeconfig-datn-staging` → `kubeconfig-staging` ở `DATN/ci` **và** `DATN/release`; `kubeconfig-datn-prod` → `kubeconfig-prod` **chỉ** ở `DATN/release`. Tải lên xong: `rm kubeconfig-datn-*` ở cả máy cá nhân và máy chủ |
| §11 Job `DATN/ci/datn` | |
| §12 Job `DATN/release/datn-deploy` | Nhớ bấm *Build Now* một lần để Jenkins đăng ký tham số |
| §13 Webhook | Payload URL `http://jenkins.<BASE>/github-webhook/`. *Recent Deliveries* phải trả `200` |

Branch protection cho `main` (cần PR + check `continuous-integration/jenkins/pr-merge`): [03 §11](03_HUONG_DAN_TRIEN_KHAI.md).

## Bước 11. Deploy lần đầu

### 11.1. Staging

`datn-app` **không cần cài bằng tay**: lần build `main` đầu tiên, `deploy.sh` tự `helm upgrade --install` bằng quyền `ci-deployer` (đã chạy thử, 03 §13.1).

1. Jenkins → `DATN/ci/datn` → **Scan Multibranch Pipeline Now**, rồi chạy nhánh `main` (hoặc merge một PR nhỏ).
2. Build lần đầu lâu (20–40 phút): Maven và npm tải thư viện, Trivy tải cơ sở dữ liệu lỗ hổng, build 6 image.
3. Kỳ vọng log kết thúc bằng `✅ datn-app@<sha> đã chạy trên datn-staging`.
4. GitHub → **Packages**: 6 package `datn-*`, đặt **Private** nếu chưa phải.

Kiểm tra bằng trình duyệt `http://staging.<BASE>` (trình duyệt sẽ báo "Not secure", đúng với chế độ HTTP):
- Đăng ký tài khoản → email xác thực trong `http://mail-staging.<BASE>` (basic auth) → bấm link → đăng nhập.
- Tạo workspace, dự án, công việc; tải đính kèm.
- Thông báo realtime: DevTools → Network → WS `/notification/ws` trả `101`.

### 11.2. Prod

```bash
# máy dev, khi staging đã ổn
git checkout main && git pull
git tag -a v1.0.0 -m "Phát hành v1.0.0" && git push origin v1.0.0
```

Jenkins: build `v1.0.0` gắn tag image → job `DATN/release/datn-deploy` chờ duyệt → người có trong `submitter` bấm **Đồng ý** → kiểm tra `http://app.<BASE>` giống 11.1 (email thật gửi qua Gmail).

Rollback, deploy lại SHA cũ: [03 §12.5](03_HUONG_DAN_TRIEN_KHAI.md).

---

# Phần E. Vận hành

## Bước 12. Sao lưu, nghiệm thu, tiết kiệm

### 12.1. Sao lưu

| Lớp | Cách làm |
|---|---|
| DB (logic) | Cron `backup-db.sh` lúc 02:00 cho `datn-prod` (và `datn-staging` nếu muốn): [03 §14.1](03_HUONG_DAN_TRIEN_KHAI.md) |
| Jenkins | Sao lưu `JENKINS_HOME`: [04 §15](04_HUONG_DAN_SETUP_JENKINS.md) |
| Cả ổ đĩa EC2 | EC2 → **Lifecycle Manager → Create lifecycle policy** → *EBS snapshot policy*, target: volume gắn với `datn-server` (gắn tag `Backup=datn` cho volume rồi chọn theo tag), lịch hằng ngày 03:00, giữ 7 bản |
| Bí mật | `~/datn-secrets` trong trình quản lý mật khẩu (bước 8.3) |

Snapshot EBS chụp cả dữ liệu Postgres, Kafka, MinIO (nằm trong container minikube) và Jenkins. Khôi phục máy hỏng: tạo volume từ snapshot, gắn vào instance mới (và gắn lại **đúng Elastic IP cũ**, vì tên host dựng từ IP).

### 12.2. Nghiệm thu

Chạy các kịch bản **T1 → T12** ở [03 §13](03_HUONG_DAN_TRIEN_KHAI.md) và **E1 → E7** ở [04 §14](04_HUONG_DAN_SETUP_JENKINS.md), thay `https://….example.com` bằng `http://….<BASE>`. Chụp màn hình/kết quả làm minh chứng.

### 12.3. Tắt / bật máy để tiết kiệm

- **Tắt:** EC2 → Instance state → **Stop** (không chọn *Terminate*: Terminate là xoá máy). Khi dừng chỉ trả tiền đĩa và Elastic IP. **Không** *Release* Elastic IP.
- **Bật:** **Start**. `minikube.service` tự khởi động cluster (1–3 phút), Jenkins tự chạy. Elastic IP giữ nguyên nên tên host không đổi.
- Kiểm tra sau khi bật: `kubectl get pods -A | grep -v Running` (chỉ còn dòng tiêu đề và pod `Completed`), rồi mở `http://staging.<BASE>`.

### 12.4. Định kỳ

| Tần suất | Việc |
|---|---|
| Hằng tuần | `df -h /` và `minikube ssh -- df -h /` (đĩa < 80%); dọn Docker rootless của Jenkins ([04 §15](04_HUONG_DAN_SETUP_JENKINS.md)) |
| Hằng tháng | `sudo apt-get update && sudo apt-get -y upgrade`; cập nhật Jenkins và plugin; xem báo cáo Trivy trong log build `main` |
| Trước 90 ngày | Xoay 3 token GitHub (8.1, 10.1): cập nhật credential Jenkins và Secret `ghcr-pull` |

---

## Chuyển sang tên miền thật và HTTPS

Làm khi đã mua tên miền (ví dụ `tenmien.vn`). Dữ liệu giữ nguyên; người dùng chỉ phải đăng nhập lại (issuer của token đổi).

1. **DNS:** tạo bản ghi A `*.tenmien.vn` (hoặc từng tên `app`, `sso`, `pgadmin`, `staging`, `sso-staging`, `pgadmin-staging`, `mail-staging`, `jenkins`) → `<EIP>`. Kiểm tra `nslookup staging.tenmien.vn`.
2. **Repo (máy dev):** `bash infrastructure/scripts/set-public-host.sh tenmien.vn https` (đổi host, `https`, `cookieSecure: true`), commit, PR, merge vào `main`.
3. **Nginx + chứng chỉ (máy chủ):** đổi `server_name` trong `/etc/nginx/sites-available/datn` và `jenkins` sang tên mới, `sudo nginx -t && sudo systemctl reload nginx`, rồi chạy các lệnh `certbot --nginx` ở 03 §8 và 04 §6. Sửa dòng `/etc/hosts` (bước 9) sang tên mới.
4. **Platform:** `~/datn-secrets/upgrade-platform.sh staging` rồi `prod` (Keycloak nhận `hostnameUrl` mới).
5. **Keycloak (Admin Console, mỗi môi trường):**
   - Realm `datn-realm` → *Realm settings → General → Require SSL* = **external requests**. Realm `master`: tương tự (hoặc lệnh 8.6 với `sslRequired=EXTERNAL`).
   - *Clients → datn-backend*: *Valid redirect URIs* = `https://staging.tenmien.vn/*` (prod: `https://app.tenmien.vn/*`), *Web origins* = `https://staging.tenmien.vn`, *Valid post logout redirect URIs* tương tự. (Realm đã import từ lần đầu nên **không** tự cập nhật theo file.)
   - Lúc này mới bật được đăng nhập Google: tạo secret mới trên Google Cloud Console (secret trong file realm export của repo đã lộ), nhập ở *Identity providers → google*, thêm redirect URI `https://sso-staging.tenmien.vn/realms/datn-realm/broker/google/endpoint` (và bản `sso.tenmien.vn`).
6. **Jenkins:** *Jenkins URL* = `https://jenkins.tenmien.vn/`; GitHub webhook đổi Payload URL sang `https://jenkins.tenmien.vn/github-webhook/`.
7. **Deploy lại ứng dụng:** build `main` (staging) tự deploy với URL mới; prod: chạy `DATN/release/datn-deploy` với `ACTION=deploy`, `IMAGE_TAG=<sha đang chạy ở prod>` (xem `HELM_DRIVER=configmap helm -n datn-prod get values datn-app`).
8. Security Group: **giữ** cổng 80 để Let's Encrypt gia hạn và chuyển hướng HTTP → HTTPS.

---

## Sự cố riêng trên EC2 / chế độ IP

| Triệu chứng | Nguyên nhân | Cách xử lý |
|---|---|---|
| Launch báo `VcpuLimitExceeded` | Hạn mức vCPU thấp | Bước 1, xin tăng lên 8 |
| SSH `Bad permissions … BUILTIN\Users`, `UNPROTECTED PRIVATE KEY FILE` | File `.pem` kế thừa quyền của thư mục (ví dụ ổ `D:`) | Chuyển vào `%USERPROFILE%\.ssh` và chạy lệnh `icacls` ở bước 2.2. Không để file khoá trong thư mục repo |
| SSH `ubuntu@…: Permission denied (publickey,gssapi-keyex,gssapi-with-mic)`, trang *Connect* gợi ý `ec2-user` | Máy tạo nhầm AMI **Amazon Linux** (mặc định của Launch instance) | Thử `ec2-user@<EIP>`: thấy *Amazon Linux* thì tắt Termination protection, Terminate, Launch lại với **Ubuntu Server 24.04 LTS**, gắn lại đúng Elastic IP |
| SSH `WARNING: REMOTE HOST IDENTIFICATION HAS CHANGED!` ngay sau khi tạo lại máy | Máy mới dùng lại IP cũ nhưng host key mới | `ssh-keygen -R <EIP>` (và `ssh-keygen -R ec2-…compute.amazonaws.com`), SSH lại, gõ `yes`. Nếu **không** vừa tạo lại máy thì đừng bỏ qua cảnh báo này |
| SSH `Connection timed out` | IP của bạn đổi, rule SSH chỉ cho IP cũ | Security Group → sửa rule 22 → *My IP* |
| Đăng nhập báo **"HTTPS required"** | Realm còn `sslRequired=external` mà đang chạy HTTP | Realm ứng dụng: bước 8.2 (hoặc Admin Console); Admin Console: lệnh 8.6 |
| Tên `*.sslip.io` không phân giải | Mạng chặn DNS lạ, hoặc gõ sai định dạng IP | `nslookup staging.<BASE> 8.8.8.8`; IP phải viết bằng gạch ngang (`52-62-135-59`) |
| Mọi tên host hỏng sau khi bật lại máy | Elastic IP bị gỡ (Release/Disassociate) nên IP đổi | Gắn lại đúng EIP cũ; nếu buộc phải đổi IP: làm lại bước 5, 8.2 (realm qua Admin Console), 9, 10.3 (webhook) |
| Mọi trang 502 sau khi bật lại máy | minikube chưa lên xong, hoặc `minikube ip` đổi | `systemctl status minikube`; so `minikube ip` với `proxy_pass` trong `/etc/nginx/sites-available/datn` |
| Pod `Pending`, `Insufficient cpu` | Máy không đủ cho cả 2 môi trường | Đúng cỡ máy ở bước 2.1; tạm `kubectl -n datn-staging scale deploy --all --replicas=0` |
| Không gửi được email prod | Dùng cổng 25 (EC2 chặn) hoặc sai App Password | Chart dùng 587; tạo lại App Password, cập nhật `app-secrets` |
| Smoke test Jenkins lỗi `Could not resolve host` | Thiếu dòng `/etc/hosts` (bước 9) | Thêm dòng `/etc/hosts`; `curl -s http://staging.<BASE>/` trên máy chủ |
| Hoá đơn cao bất thường | Máy chạy 24/7, CPU credit của `t3` | Bước 12.3; Billing → Cost Explorer; cân nhắc `m7i-flex` |

Các sự cố về Kubernetes, Helm, Keycloak: [03 §14.2](03_HUONG_DAN_TRIEN_KHAI.md). Về Jenkins: [04 §16](04_HUONG_DAN_SETUP_JENKINS.md).
