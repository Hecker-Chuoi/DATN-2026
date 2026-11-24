# 05 — Triển khai từ đầu trên AWS EC2 (runbook)

> Dành cho người dựng máy chủ. Làm **theo đúng thứ tự** từ bước 1 đến bước 12. Mỗi bước có lệnh cần chạy và cách kiểm tra trước khi sang bước sau.
> Tài liệu này là **trình tự**. Nội dung chi tiết (file chart, cấu hình Nginx, Jenkins…) nằm ở [03](03_HUONG_DAN_TRIEN_KHAI.md) và [04](04_HUONG_DAN_SETUP_JENKINS.md); chỗ nào trỏ sang thì làm đúng mục đó rồi quay lại đây.
> Mọi file chart, script, Jenkinsfile **đã có trong repo** và đã chạy thử trên cluster thật ([03 §13.1](03_HUONG_DAN_TRIEN_KHAI.md)).

## Tổng quan

| Bước | Việc | Làm ở đâu | Thời gian |
|---|---|---|---|
| 1 | Chuẩn bị tên miền, tài khoản AWS, token GitHub | Trình duyệt | 30 phút |
| 2 | Tạo EC2, Security Group, Elastic IP | AWS Console | 30 phút |
| 3 | Trỏ DNS | Nhà cung cấp tên miền | 10 phút (+ chờ cập nhật) |
| 4 | Sửa tên miền trong repo, merge vào `main` | Máy dev | 30 phút |
| 5 | Chuẩn bị máy chủ, cài công cụ | SSH vào EC2 | 45 phút |
| 6 | Khởi động minikube | EC2 | 15 phút |
| 7 | Secret + cài `datn-platform` (staging, prod) | EC2 | 45 phút |
| 8 | Nginx máy chủ + TLS | EC2 | 30 phút |
| 9 | Cài và cấu hình Jenkins | EC2 + trình duyệt | 2–3 giờ |
| 10 | Kubeconfig cho Jenkins, webhook GitHub | EC2 + trình duyệt | 30 phút |
| 11 | Deploy staging lần đầu, phát hành prod | GitHub + Jenkins | 1 giờ |
| 12 | Sao lưu, theo dõi chi phí, nghiệm thu | AWS + EC2 | 1 giờ |

Quy ước trong tài liệu: tên miền là `tenmien.vn`, IP tĩnh của EC2 là `<EIP>`, owner GitHub là `Hecker-Chuoi` (image trên GHCR viết **chữ thường**: `hecker-chuoi`). Thay bằng giá trị thật.

---

## Bước 1. Chuẩn bị

### 1.1. Tên miền

Cần **một tên miền** để có HTTPS (Let's Encrypt) và để Keycloak có URL công khai cố định. Mua tên miền rẻ (`.id.vn`, `.site`, `.xyz`…) ở bất kỳ nhà cung cấp nào cho sửa bản ghi DNS. Sẽ dùng các tên sau:

| Tên | Dùng cho |
|---|---|
| `app.tenmien.vn` | Ứng dụng (prod) |
| `sso.tenmien.vn` | Keycloak (prod) |
| `pgadmin.tenmien.vn` | pgAdmin (prod) |
| `staging.tenmien.vn` | Ứng dụng (staging) |
| `sso-staging.tenmien.vn` | Keycloak (staging) |
| `pgadmin-staging.tenmien.vn` | pgAdmin (staging) |
| `mail-staging.tenmien.vn` | Mailpit (staging) |
| `jenkins.tenmien.vn` | Jenkins |
| `minio.tenmien.vn` | MinIO Console (chỉ khi bật, mặc định tắt) |

### 1.2. Tài khoản AWS

1. **Region:** chọn **Asia Pacific (Singapore) `ap-southeast-1`**, gần Việt Nam nhất. Mọi bước sau làm trong cùng region này.
2. **Hạn mức vCPU:** tài khoản mới thường chỉ được chạy ít vCPU. Vào **Service Quotas → Amazon EC2 → Running On-Demand Standard (A, C, D, H, I, M, R, T, Z) instances**. Giá trị phải **≥ 8**, nếu thấp hơn thì bấm *Request increase* lên 8 (thường được duyệt trong vài giờ tới 1 ngày). Làm việc này **trước tiên**.
3. **Cảnh báo chi phí:** **Billing → Budgets → Create budget** → *Monthly cost budget*, đặt mức bạn chấp nhận được (ví dụ 100 USD), nhận email khi chạm 80%.

### 1.3. Token GitHub

Tạo tại GitHub → Settings → Developer settings → **Personal access tokens (classic)**. GHCR chỉ nhận PAT classic. Đặt hạn dùng 90 ngày, lưu vào trình quản lý mật khẩu.

| Token | Scope | Dùng ở |
|---|---|---|
| `jenkins-repo` | `repo` (repo private) | Jenkins đọc repo, báo trạng thái PR (bước 9) |
| `jenkins-ghcr` | `write:packages` | Jenkins push image (bước 9) |
| `cluster-ghcr` | `read:packages` | Secret `ghcr-pull` trong cluster (bước 7) |

---

## Bước 2. Tạo EC2

### 2.1. Chọn cỡ máy

Hệ thống gồm staging + prod (mỗi bên khoảng 2 core / 4,5–4,9 GiB requests) + thành phần Kubernetes + Jenkins và build image ([02 §6](02_KE_HOACH_TRIEN_KHAI_CICD.md)).

| Lựa chọn | Instance | vCPU / RAM | Dùng khi |
|---|---|---|---|
| **Khuyến nghị** | `t3.2xlarge` (hoặc `m7i-flex.2xlarge`) | 8 / 32 GiB | Chạy cả staging, prod và Jenkins |
| Tối thiểu | `t3.xlarge` | 4 / 16 GiB | Chỉ **một** môi trường (ví dụ chỉ staging) + Jenkins. Phải giảm `--cpus=3 --memory=9g` ở bước 6, thêm swap, Jenkins 1 executor |

> Giá tham khảo (Singapore, chạy 24/7): `t3.2xlarge` khoảng 300 USD/tháng, đĩa 100 GiB khoảng 10 USD/tháng, Elastic IP khoảng 4 USD/tháng. Kiểm tra lại trên [AWS Pricing Calculator](https://calculator.aws/). **Tắt máy khi không dùng** (bước 12.3) để chỉ trả tiền đĩa và IP.
> `t3` là dòng "burst": build liên tục lâu có thể tính thêm phí CPU credit. `m7i-flex` đắt hơn chút nhưng không có khái niệm credit.

### 2.2. Key pair

EC2 → **Key Pairs → Create key pair**: tên `datn-ec2`, loại **ED25519**, định dạng **.pem**. Lưu file vào `C:\Users\<bạn>\.ssh\datn-ec2.pem`, rồi khoá quyền đọc (PowerShell):

```powershell
icacls "$env:USERPROFILE\.ssh\datn-ec2.pem" /inheritance:r /grant:r "$($env:USERNAME):(R)"
```

### 2.3. Security Group

EC2 → **Security Groups → Create security group**, tên `datn-sg`:

| Inbound | Port | Source | Lý do |
|---|---|---|---|
| SSH | 22 | **My IP** | Chỉ máy của bạn SSH được. Đổi mạng thì sửa lại rule này |
| HTTP | 80 | `0.0.0.0/0` | Let's Encrypt cấp chứng chỉ, chuyển hướng sang HTTPS |
| HTTPS | 443 | `0.0.0.0/0` | Ứng dụng, Keycloak, công cụ quản trị, Jenkins, webhook GitHub |

**Không** mở thêm cổng nào khác (8080, 8443, 5432…): mọi thứ đi qua Nginx ở cổng 443. Outbound để mặc định (cho phép hết).

### 2.4. Launch instance

EC2 → **Launch instance**:

| Mục | Giá trị |
|---|---|
| Name | `datn-server` |
| AMI | **Ubuntu Server 24.04 LTS**, kiến trúc **64-bit (x86)** (không chọn Arm: các lệnh cài đặt dùng bản `amd64`) |
| Instance type | `t3.2xlarge` (bước 2.1) |
| Key pair | `datn-ec2` |
| Network settings | Chọn *Select existing security group* → `datn-sg`; *Auto-assign public IP*: Enable |
| Storage | **100 GiB, gp3** |
| Advanced details → IAM instance profile | **Để trống** (máy không cần quyền AWS nào) |
| Advanced details → Metadata version | **V2 only (token required)** |
| Advanced details → Metadata response hop limit | **1** (container và pod không gọi được metadata của EC2) |
| Advanced details → Termination protection | **Enable** (tránh xoá nhầm) |

Bấm **Launch**, chờ trạng thái *Running* và *2/2 checks passed*.

### 2.5. Elastic IP

IP công khai mặc định **đổi mỗi lần tắt/bật máy**, nên cần IP tĩnh: EC2 → **Elastic IPs → Allocate** → chọn IP vừa cấp → **Associate** → instance `datn-server`. Ghi lại IP này, gọi là `<EIP>`.

### 2.6. SSH thử

```powershell
ssh -i "$env:USERPROFILE\.ssh\datn-ec2.pem" ubuntu@<EIP>
```

Cho tiện, thêm vào `C:\Users\<bạn>\.ssh\config`:

```
Host datn
    HostName <EIP>
    User datnadmin
    IdentityFile ~/.ssh/datn-ec2.pem
```

(user `datnadmin` sẽ tạo ở bước 5.1; trước đó vẫn dùng `ubuntu@<EIP>`.)

---

## Bước 3. Trỏ DNS

Ở trang quản lý DNS của tên miền, tạo **bản ghi A** cho từng tên ở bước 1.1 (trừ `minio` nếu chưa bật), giá trị `<EIP>`, TTL 300. Có thể dùng một bản ghi wildcard `*.tenmien.vn → <EIP>` thay cho từng bản ghi.

Kiểm tra (máy dev, chờ vài phút tới vài giờ tuỳ nhà cung cấp):

```powershell
nslookup staging.tenmien.vn
nslookup jenkins.tenmien.vn      # cả hai phải ra <EIP>
```

---

## Bước 4. Sửa repo cho tên miền thật (máy dev)

Các file sau đang dùng `example.com`: `Jenkinsfile`, `infrastructure/jenkins/Jenkinsfile.deploy`, `infrastructure/helm/*/values-staging.yaml`, `infrastructure/helm/*/values-prod.yaml`.

```bash
# Git Bash, ở gốc repo, trên nhánh feat/cicd-helm-jenkins
grep -rl 'example\.com' Jenkinsfile infrastructure/jenkins infrastructure/helm \
  | xargs sed -i 's/example\.com/tenmien.vn/g'
grep -rn 'example\.com' Jenkinsfile infrastructure/jenkins infrastructure/helm   # phải rỗng
```

Sửa thêm bằng tay:

| File | Sửa |
|---|---|
| `infrastructure/helm/datn-app/values-prod.yaml` | `mail.from`: địa chỉ Gmail dùng gửi mail, ví dụ `"DATN-2026 <ten.ban@gmail.com>"` (Gmail từ chối địa chỉ gửi khác tài khoản) |
| `infrastructure/jenkins/Jenkinsfile.deploy` | `submitter: 'truongnhom'` → user ID Jenkins được duyệt deploy prod (bạn sẽ tạo ở bước 9, ví dụ `sonadmin`). Nhiều người: `'sonadmin,tuananh'` |
| `infrastructure/helm/datn-app/values.yaml`, `Jenkinsfile`, `infrastructure/scripts/build-images.sh` | Chỉ khi owner GitHub **không phải** `Hecker-Chuoi`: đổi `ghcr.io/hecker-chuoi` (chữ thường) và URL repo |

Commit, push, mở PR từ `feat/cicd-helm-jenkins` vào `main`, review rồi **merge**. Từ đây máy chủ và Jenkins chỉ dùng nhánh `main`.

> **Không** ghi IP của nhóm vào repo: danh sách IP được phép vào công cụ quản trị nằm trong file riêng trên máy chủ (bước 7.4).

---

## Bước 5. Chuẩn bị máy chủ

### 5.1. Tạo user quản trị `datnadmin`

Các tài liệu dùng user `datnadmin` (chạy minikube, giữ kubeconfig admin). SSH bằng `ubuntu` rồi tạo:

```bash
sudo adduser --disabled-password --gecos "" datnadmin
sudo usermod -aG sudo datnadmin
echo "datnadmin ALL=(ALL) NOPASSWD:ALL" | sudo tee /etc/sudoers.d/90-datnadmin
sudo install -d -m 700 -o datnadmin -g datnadmin /home/datnadmin/.ssh
sudo install -m 600 -o datnadmin -g datnadmin ~/.ssh/authorized_keys /home/datnadmin/.ssh/authorized_keys
exit
```

Từ giờ SSH bằng `ssh datn` (user `datnadmin`).

### 5.2. Cập nhật hệ thống và tinh chỉnh

```bash
sudo apt-get update && sudo apt-get -y upgrade
sudo timedatectl set-timezone Asia/Ho_Chi_Minh          # cron sao lưu 02:00 theo giờ Việt Nam
sudo hostnamectl set-hostname datn-server

# Nhiều pod + Jenkins: tăng giới hạn inotify (tránh lỗi "too many open files" trong minikube)
cat <<'EOF' | sudo tee /etc/sysctl.d/90-datn.conf
fs.inotify.max_user_watches = 524288
fs.inotify.max_user_instances = 512
EOF
sudo sysctl --system

sudo reboot      # nếu upgrade có kernel mới; SSH lại sau 1 phút
```

Máy 32 GiB không cần swap. Máy 16 GiB thì thêm swap 4 GiB theo [04 §1](04_HUONG_DAN_SETUP_JENKINS.md).

### 5.3. Cài công cụ

Làm theo **[03 §1](03_HUONG_DAN_TRIEN_KHAI.md)** (gói apt, Docker, kubectl, minikube, Helm 4.3.2), với 3 điểm khác trên EC2:

1. **Bỏ qua dòng `ufw`.** Security Group (bước 2.3) đã là firewall; bật thêm `ufw` có thể chặn mạng của container minikube.
2. Lệnh `newgrp docker` mở một shell mới: thay bằng `exit` rồi SSH lại để nhóm `docker` có hiệu lực.
3. **Chưa** chạy lệnh `git clone` ở cuối §1 (làm ở 5.4).

Kiểm tra:

```bash
docker run --rm hello-world | head -2
kubectl version --client && minikube version && helm version
```

### 5.4. Lấy mã nguồn bằng deploy key

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
cd /opt/datn && git log --oneline -1          # đúng commit merge ở bước 4
ls infrastructure/helm                        # datn-app  datn-platform
```

Repo public thì clone bằng HTTPS như 03 §1.

---

## Bước 6. Khởi động minikube

Làm theo **[03 §2](03_HUONG_DAN_TRIEN_KHAI.md)** với cỡ máy 32 GiB:

```bash
minikube start --driver=docker --cni=calico --cpus=6 --memory=14g --disk-size=60g
```

(máy 16 GiB, một môi trường: `--cpus=3 --memory=9g`). Bật addon `ingress`, `metrics-server`, patch ConfigMap `ingress-nginx-controller`, tạo unit systemd `minikube.service` đúng như 03 §2.

Kiểm tra:

```bash
kubectl get nodes                                  # Ready
kubectl -n ingress-nginx get pods                  # controller Running
minikube ip                                        # thường 192.168.49.2; khác thì nhớ sửa ở bước 8
sudo systemctl is-enabled minikube                 # enabled
```

---

## Bước 7. Secret và `datn-platform`

### 7.1. Namespace

```bash
cd /opt/datn && kubectl apply -f infrastructure/k8s/namespaces.yaml
```

### 7.2. Secret cho staging

Làm **[03 §6.1 → §6.4](03_HUONG_DAN_TRIEN_KHAI.md)** với `ENV=staging`:

| Mục | Ghi chú trên EC2 |
|---|---|
| 6.1 `app-secrets` | `PGADMIN_EMAIL`: email thật của bạn (pgAdmin từ chối tên miền đặc biệt như `.local`) |
| 6.2 `keycloak-realm` | `APP_URL=https://staging.tenmien.vn`. Không cần thêm `sslRequired = "none"` vì có HTTPS |
| 6.3 `ghcr-pull` | `--docker-username=<user GitHub>`, `--docker-password=<token cluster-ghcr>` |
| 6.4 `admin-basic-auth` | Tạo user basic auth, ví dụ `quantri` |

**Google đăng nhập:** file realm export trong repo chứa client secret Google thật (đã lộ trong Git). Tạo secret mới trên Google Cloud Console; sau khi Keycloak chạy (7.5), vào Admin Console → `datn-realm` → *Identity providers* → `google` để nhập secret mới. Thêm *Authorized redirect URI* trên Google: `https://sso-staging.tenmien.vn/realms/datn-realm/broker/google/endpoint` (và bản `sso.tenmien.vn` cho prod).

### 7.3. Secret cho prod

Lặp lại 03 §6.1 → §6.4 với `ENV=prod`, `APP_URL=https://app.tenmien.vn`. Khác staging:
- `MAIL_USERNAME` = địa chỉ Gmail, `MAIL_PASSWORD` = **App Password** của Gmail (Google Account → Security → 2-Step Verification → App passwords). EC2 chặn cổng 25 nhưng cho phép 587, đúng cổng chart dùng.
- Mật khẩu mọi thứ khác **sinh mới**, không dùng lại của staging.

Sao lưu cả thư mục `~/datn-secrets` vào trình quản lý mật khẩu của nhóm. Mất file này thì phải đặt lại mật khẩu DB bằng tay.

### 7.4. Giới hạn IP cho công cụ quản trị (tuỳ chọn, nên làm)

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
curl -s -H "Host: sso-staging.tenmien.vn" -H "X-Forwarded-Proto: https" \
  http://$(minikube ip)/realms/datn-realm/.well-known/openid-configuration | jq -r .issuer
# Kỳ vọng: https://sso-staging.tenmien.vn/realms/datn-realm
```

Keycloak lần đầu mất 1–2 phút để tự build và import realm. Nếu thấy `OOMKilled`, xem [03 §14.2](03_HUONG_DAN_TRIEN_KHAI.md).

---

## Bước 8. Nginx máy chủ và HTTPS

Làm theo **[03 §8](03_HUONG_DAN_TRIEN_KHAI.md)**: tạo `/etc/nginx/sites-available/datn` (thay `example.com` bằng `tenmien.vn`, `proxy_pass` bằng `minikube ip` nếu khác `192.168.49.2`), bật site, chạy `certbot --nginx`.

Thêm bản ghi `/etc/hosts` để các lệnh **chạy ngay trên máy chủ** (smoke test của Jenkins) đi thẳng vào Nginx cục bộ thay vì vòng ra IP công khai:

```bash
echo "127.0.0.1 app.tenmien.vn staging.tenmien.vn sso.tenmien.vn sso-staging.tenmien.vn" | sudo tee -a /etc/hosts
```

Kiểm tra từ **máy cá nhân**:

```bash
curl -sI https://sso-staging.tenmien.vn/realms/datn-realm | head -1         # HTTP/2 200
curl -s -o /dev/null -w "%{http_code}\n" https://pgadmin-staging.tenmien.vn/ # 401 (basic auth) hoặc 403 (ngoài IP cho phép)
sudo certbot renew --dry-run                                                 # (trên máy chủ) gia hạn tự động hoạt động
```

`https://staging.tenmien.vn` lúc này trả 503 vì chưa có `datn-app`. Bình thường, bước 11 sẽ cài.

---

## Bước 9. Jenkins

Làm lần lượt **[04 §2 → §13](04_HUONG_DAN_SETUP_JENKINS.md)**:

| Mục 04 | Ghi chú trên EC2 |
|---|---|
| §2 Java + Jenkins | |
| §3 systemd | Máy 32 GiB giữ `MemoryMax=4G` |
| §4 Docker rootless | Ubuntu 24.04: **bắt buộc** làm §4.2 (AppArmor) |
| §5 Công cụ | `kubeconform`, `trivy`. Trivy hiện chỉ báo cáo, không chặn build |
| §6 Nginx + TLS | `jenkins.tenmien.vn`. Nên chỉ mở `/github-webhook/` và vào UI qua SSH tunnel: `ssh -L 8080:127.0.0.1:8080 datn` rồi mở `http://localhost:8080` |
| §7 Plugin | |
| §8 Bảo mật | Tạo tài khoản quản trị đúng user ID đã ghi vào `submitter` ở bước 4. Máy 32 GiB: **2 executor** |
| §9 Tools | |
| §10 Folder, credential | `kubeconfig-staging`, `kubeconfig-prod` tạo ở bước 10.1 |
| §11 Job `DATN/ci/datn` | |
| §12 Job `DATN/release/datn-deploy` | Nhớ bấm *Build Now* một lần để Jenkins đăng ký tham số |

Kiểm tra cách ly (bắt buộc):

```bash
sudo -u jenkins kubectl get pods -A        # PHẢI lỗi: Jenkins không có kubeconfig admin
sudo -u jenkins docker ps                  # PHẢI lỗi: Jenkins không chạm được Docker gốc (nơi chạy minikube)
```

---

## Bước 10. Nối Jenkins với cluster và GitHub

### 10.1. Kubeconfig hạn chế quyền

```bash
cd ~ && bash /opt/datn/infrastructure/scripts/make-ci-kubeconfig.sh datn-staging
bash /opt/datn/infrastructure/scripts/make-ci-kubeconfig.sh datn-prod
```

Mỗi lần chạy in bảng quyền, kỳ vọng `yes yes yes no no no` và `no` ở namespace khác. Tải file lên Jenkins:
- `kubeconfig-datn-staging` → credential `kubeconfig-staging` (Secret file) ở folder `DATN/ci` **và** `DATN/release`.
- `kubeconfig-datn-prod` → credential `kubeconfig-prod` **chỉ** ở folder `DATN/release`.

Tải từ máy chủ về máy cá nhân: `scp datn:kubeconfig-datn-* .` Tải lên Jenkins xong thì xoá cả hai nơi: `rm ~/kubeconfig-datn-*`.

### 10.2. GitHub

| Việc | Ở đâu |
|---|---|
| Webhook `https://jenkins.tenmien.vn/github-webhook/` | [04 §13](04_HUONG_DAN_SETUP_JENKINS.md); *Recent Deliveries* phải trả `200` |
| Branch protection cho `main` (cần PR + check `continuous-integration/jenkins/pr-merge`) | [03 §11](03_HUONG_DAN_TRIEN_KHAI.md) |

---

## Bước 11. Deploy lần đầu

### 11.1. Staging

`datn-app` **không cần cài bằng tay**: lần build `main` đầu tiên, `deploy.sh` tự `helm upgrade --install` bằng quyền `ci-deployer` (đã chạy thử, 03 §13.1).

1. Jenkins → `DATN/ci/datn` → **Scan Multibranch Pipeline Now**, rồi chạy nhánh `main` (hoặc merge một PR nhỏ).
2. Build lần đầu lâu (20–40 phút): Maven và npm tải thư viện, Trivy tải cơ sở dữ liệu lỗ hổng, build 6 image.
3. Kỳ vọng log kết thúc bằng `✅ datn-app@<sha> đã chạy trên datn-staging`.
4. GitHub → **Packages**: 6 package `datn-*`, đặt **Private** nếu chưa phải.

Kiểm tra bằng trình duyệt `https://staging.tenmien.vn`:
- Đăng ký tài khoản → email xác thực trong `https://mail-staging.tenmien.vn` (basic auth) → bấm link → đăng nhập.
- Tạo workspace, dự án, công việc; tải đính kèm.
- Thông báo realtime: DevTools → Network → WS `/notification/ws` trả `101`.
- Đăng nhập Google (nếu đã làm 7.2).

### 11.2. Prod

```bash
# máy dev, khi staging đã ổn
git checkout main && git pull
git tag -a v1.0.0 -m "Phát hành v1.0.0" && git push origin v1.0.0
```

Jenkins: build `v1.0.0` gắn tag image → job `DATN/release/datn-deploy` chờ duyệt → người có trong `submitter` bấm **Đồng ý** → kiểm tra `https://app.tenmien.vn` giống 11.1 (email thật gửi qua Gmail).

Rollback, deploy lại SHA cũ: [03 §12.5](03_HUONG_DAN_TRIEN_KHAI.md).

---

## Bước 12. Vận hành

### 12.1. Sao lưu

| Lớp | Cách làm |
|---|---|
| DB (logic) | Cron `backup-db.sh` lúc 02:00 cho `datn-prod` (và `datn-staging` nếu muốn): [03 §14.1](03_HUONG_DAN_TRIEN_KHAI.md) |
| Cả ổ đĩa EC2 | EC2 → **Lifecycle Manager → Create lifecycle policy** → *EBS snapshot policy*, target: volume gắn với `datn-server` (gắn tag `Backup=datn` cho volume rồi chọn theo tag), lịch hằng ngày 03:00, giữ 7 bản |
| Bí mật | `~/datn-secrets` trong trình quản lý mật khẩu (bước 7.3) |

Snapshot EBS chụp cả dữ liệu Postgres, Kafka, MinIO (nằm trong container minikube) và Jenkins. Khôi phục máy hỏng: tạo volume từ snapshot, gắn vào instance mới.

### 12.2. Nghiệm thu

Chạy các kịch bản **T1 → T12** ở [03 §13](03_HUONG_DAN_TRIEN_KHAI.md) và **E1 → E7** ở [04 §14](04_HUONG_DAN_SETUP_JENKINS.md). Chụp màn hình/kết quả làm minh chứng.

### 12.3. Tắt / bật máy để tiết kiệm

- **Tắt:** EC2 → Instance state → **Stop** (không chọn *Terminate*: Terminate là xoá máy). Khi dừng chỉ trả tiền đĩa và Elastic IP.
- **Bật:** **Start**. `minikube.service` tự khởi động cluster (1–3 phút), Jenkins tự chạy. Elastic IP giữ nguyên nên DNS không phải sửa.
- Kiểm tra sau khi bật: `kubectl get pods -A | grep -v Running` (chỉ còn dòng tiêu đề và pod `Completed`), rồi mở `https://staging.tenmien.vn`.

### 12.4. Định kỳ

| Tần suất | Việc |
|---|---|
| Hằng tuần | `df -h /` và `minikube ssh -- df -h /` (đĩa < 80%); dọn Docker rootless của Jenkins ([04 §15](04_HUONG_DAN_SETUP_JENKINS.md)) |
| Hằng tháng | `sudo apt-get update && sudo apt-get -y upgrade`; cập nhật Jenkins và plugin; xem báo cáo Trivy trong log build `main` |
| Trước 90 ngày | Xoay 3 token GitHub (bước 1.3): cập nhật credential Jenkins và Secret `ghcr-pull` |

---

## Sự cố riêng trên EC2

| Triệu chứng | Nguyên nhân | Cách xử lý |
|---|---|---|
| Launch báo `VcpuLimitExceeded` | Hạn mức vCPU thấp | Bước 1.2, xin tăng lên 8 |
| SSH `Connection timed out` | IP của bạn đổi, rule SSH chỉ cho IP cũ | Security Group → sửa rule 22 → *My IP* |
| Certbot `Timeout during connect` | DNS chưa trỏ đúng, hoặc thiếu rule 80 | `nslookup`; Security Group mở 80 cho `0.0.0.0/0` |
| Mọi trang 502 sau khi bật lại máy | minikube chưa lên xong, hoặc `minikube ip` đổi | `systemctl status minikube`; so `minikube ip` với `proxy_pass` trong `/etc/nginx/sites-available/datn` |
| Pod `Pending`, `Insufficient cpu` | Máy không đủ cho cả 2 môi trường | Đúng cỡ máy ở bước 2.1; tạm `kubectl -n datn-staging scale deploy --all --replicas=0` |
| Không gửi được email prod | Dùng cổng 25 (EC2 chặn) hoặc sai App Password | Chart dùng 587; tạo lại App Password, cập nhật `app-secrets` |
| Smoke test Jenkins lỗi `Could not resolve host` / chứng chỉ | Thiếu dòng `/etc/hosts` (bước 8) hoặc DNS chưa cập nhật | Thêm dòng `/etc/hosts`; `curl -sI https://staging.tenmien.vn` trên máy chủ |
| Hoá đơn cao bất thường | Máy chạy 24/7, CPU credit của `t3` | Bước 12.3; Billing → Cost Explorer; cân nhắc `m7i-flex` |

Các sự cố về Kubernetes, Helm, Keycloak: [03 §14.2](03_HUONG_DAN_TRIEN_KHAI.md). Về Jenkins: [04 §16](04_HUONG_DAN_SETUP_JENKINS.md).
