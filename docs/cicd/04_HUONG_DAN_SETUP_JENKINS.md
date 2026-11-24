# 04 — Hướng dẫn setup Jenkins trên máy chủ (tách biệt khỏi hệ thống ứng dụng)

> Jenkins là công cụ CI/CD của DATN-2026. Nó chạy **trên cùng máy chủ** với minikube nhưng **không thuộc hệ thống ứng dụng**: không nằm trong cluster, không có quyền admin cluster, không dùng chung Docker daemon với minikube.
> Đọc kèm: [01](01_NGHIEN_CUU_CICD_KUBERNETES.md) (lý do), [02](02_KE_HOACH_TRIEN_KHAI_CICD.md) (kế hoạch), [03](03_HUONG_DAN_TRIEN_KHAI.md) (cluster, Helm chart, `Jenkinsfile`, script deploy), [05](05_TRIEN_KHAI_TU_DAU_TREN_EC2.md) (thứ tự làm trên EC2).
>
> Tài liệu chia **hai phần độc lập**:
> - **Phần A (mục 1–9): cài Jenkins bằng tay trên máy chủ.** Chỉ cần một máy Ubuntu 24.04 có SSH và `sudo`. **Không** cần repo dự án, minikube hay cluster. Làm ngay sau khi có máy chủ. Xong phần A: Jenkins chạy, có HTTPS/HTTP qua Nginx, có plugin, bảo mật, công cụ build, nhưng **chưa có job nào**.
> - **Phần B (mục 10–14): nối Jenkins với dự án DATN** (credential, job, webhook). Làm khi cluster và 2 chart Helm đã chạy, đã có kubeconfig của `ci-deployer` (03 §1–§10 hoặc 05 phần C).
>
> Jenkins deploy bằng **Helm** (`helm upgrade --install` release `datn-app`, `HELM_DRIVER=configmap`). Chart `datn-platform` do quản trị viên cài, Jenkins không đụng tới.
> Ký hiệu `<JENKINS_HOST>`: tên host của Jenkins, ví dụ `jenkins.tenmien.vn`, hoặc khi chưa có tên miền thì `jenkins.<IP-gạch-ngang>.sslip.io` (05).

## Mục lục
0. [Kiến trúc và nguyên tắc tách biệt](#0-kiến-trúc-và-nguyên-tắc-tách-biệt)

**Phần A: cài Jenkins trên máy chủ (không cần repo dự án)**

1. [Yêu cầu tài nguyên, chuẩn bị máy chủ](#1-yêu-cầu-tài-nguyên-và-chuẩn-bị-máy-chủ)
2. [Cài Java 21 và Jenkins LTS](#2-cài-java-21-và-jenkins-lts)
3. [Cấu hình dịch vụ Jenkins](#3-cấu-hình-dịch-vụ-jenkins-systemd)
4. [Docker rootless riêng cho Jenkins](#4-docker-rootless-riêng-cho-user-jenkins)
5. [Công cụ dòng lệnh](#5-công-cụ-dòng-lệnh-cho-pipeline)
6. [Nginx cho Jenkins](#6-nginx-cho-jenkins)
7. [Khởi tạo Jenkins và cài plugin](#7-khởi-tạo-jenkins-và-cài-plugin)
8. [Bảo mật Jenkins](#8-bảo-mật-jenkins)
9. [Global Tools](#9-global-tools)

**Phần B: nối Jenkins với dự án DATN (sau khi cluster chạy)**

10. [Folder và Credentials](#10-folder-và-credentials)
11. [Job CI (Multibranch)](#11-job-ci-multibranch-datncidatn)
12. [Job deploy/duyệt prod](#12-job-deploy--duyệt-prod-datnreleasedatn-deploy)
13. [Webhook GitHub](#13-webhook-github)
14. [Kiểm tra end-to-end](#14-kiểm-tra-end-to-end)
15. [Bảo trì](#15-bảo-trì)
16. [Xử lý sự cố](#16-xử-lý-sự-cố)
17. [Phụ lục: Jenkins đã có sẵn](#17-phụ-lục-jenkins-đã-có-sẵn-trên-máy-chủ)

---

## 0. Kiến trúc và nguyên tắc tách biệt

```
                         Internet
                            │ 443
                 ┌──────────▼──────────┐
                 │ Nginx máy chủ (TLS) │
                 └──┬───────────────┬──┘
   jenkins.<domain> │               │ app / sso / staging / sso-staging
                    ▼               ▼
  ┌─────────────────────────┐   ┌────────────────────────────────────────┐
  │ Jenkins (systemd)       │   │ minikube (container trên Docker gốc)   │
  │ user jenkins            │   │  ingress-nginx → datn-staging, datn-prod│
  │ 127.0.0.1:8080          │   │  API server https://192.168.49.2:8443  │
  │ JENKINS_HOME            │   └──────────────▲─────────────────────────┘
  │   /var/lib/jenkins      │                  │ helm/kubectl bằng kubeconfig của
  │ Docker ROOTLESS riêng   │──────────────────┘ ServiceAccount ci-deployer
  │   (build image, Trivy)  │                    (credential Jenkins, quyền hẹp)
  └──────────┬──────────────┘
             │ docker push / imagetools
             ▼
        GHCR (private)  ◄──── cluster kéo image bằng Secret ghcr-pull
```

| Nguyên tắc | Cách thực hiện | Vì sao |
|---|---|---|
| Jenkins nằm ngoài cluster | Cài bằng gói apt, chạy systemd | Cluster hỏng thì Jenkins vẫn chạy để deploy lại hoặc rollback; build không bị tính vào ResourceQuota của namespace |
| Jenkins **không** vào nhóm `docker` | Dùng Docker **rootless** của riêng user `jenkins` | minikube (driver docker) là một container trên Docker gốc. Ai điều khiển được Docker gốc thì `docker exec minikube ...` là có **toàn quyền cluster** và quyền root máy chủ |
| Jenkins không có kubeconfig admin | Chỉ dùng credential `kubeconfig-staging` / `kubeconfig-prod` (SA `ci-deployer`), nạp khi chạy bước deploy | Đúng yêu cầu M7: CI chỉ có quyền trong namespace của app |
| Credential prod tách riêng | `kubeconfig-prod` chỉ nằm trong folder `DATN/release` | `Jenkinsfile` của một nhánh hay PR bất kỳ không thể lấy được credential prod |
| UI không mở trực tiếp | Jenkins chỉ nghe `127.0.0.1`, đi ra ngoài qua Nginx có TLS | Không lộ cổng 8080; có thể giới hạn IP |

---

# Phần A: cài Jenkins trên máy chủ

## 1. Yêu cầu tài nguyên và chuẩn bị máy chủ

SSH vào máy chủ bằng user có `sudo` (trên EC2 Ubuntu là `ubuntu`). Cập nhật hệ thống và đặt múi giờ:

```bash
sudo apt-get update && sudo apt-get -y upgrade
sudo timedatectl set-timezone Asia/Ho_Chi_Minh
sudo hostnamectl set-hostname datn-server
sudo reboot          # nếu upgrade có kernel mới; SSH lại sau 1 phút
```

| Thành phần | RAM | Đĩa |
|---|---|---|
| Jenkins controller (`-Xmx1g`) | khoảng 1,3 GB | `/var/lib/jenkins`: 10–20 GB (workspace, `~/.m2`, Node, log build) |
| Mỗi build đang chạy (Maven test, build 6 image tuần tự, Trivy) | đỉnh khoảng 2–2,5 GB | Docker rootless (`/var/lib/jenkins/.local/share/docker`): 20–30 GB |

Bảng trên **chỉ để tham khảo** (Jenkins tốn bao nhiêu); ngân sách cả máy chủ (minikube + Jenkins) nằm ở [02 §6](02_KE_HOACH_TRIEN_KHAI_CICD.md#6-ngân-sách-tài-nguyên-và-cấu-hình-máy-chủ). Xem RAM của máy bằng `free -h` (dòng `Mem:`, cột `total`):

- Khoảng **31 Gi** (máy 32 GB): **không** cần swap; mục 8 đặt **2 executor**.
- Khoảng **15 Gi** (máy 16 GB): mục 8 đặt **1 executor** (mỗi lúc chỉ 1 build) và thêm swap 4 GB làm RAM dự phòng, tránh bị hệ điều hành giết tiến trình khi build đẩy RAM lên cao. Kiểm tra sau khi chạy: `swapon --show` hiện `/swapfile`.

```bash
sudo fallocate -l 4G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

---

## 2. Cài Java 21 và Jenkins LTS

```bash
sudo apt-get update
sudo apt-get install -y fontconfig openjdk-21-jdk git curl jq
java -version        # openjdk 21.x. Jenkins và pipeline (mvn verify) dùng chung JDK này

# Kho apt Jenkins LTS. Jenkins thay khoá ký định kỳ: lấy đúng URL khoá mới nhất tại
# https://www.jenkins.io/doc/book/installing/linux/#debianubuntu  (dưới đây là ví dụ)
sudo mkdir -p /etc/apt/keyrings
sudo wget -O /etc/apt/keyrings/jenkins-keyring.asc https://pkg.jenkins.io/debian-stable/jenkins.io-2023.key
echo "deb [signed-by=/etc/apt/keyrings/jenkins-keyring.asc] https://pkg.jenkins.io/debian-stable binary/" \
  | sudo tee /etc/apt/sources.list.d/jenkins.list > /dev/null
sudo apt-get update && sudo apt-get install -y jenkins

id jenkins           # KHÔNG được có nhóm docker hay sudo
```

---

## 3. Cấu hình dịch vụ Jenkins (systemd)

```bash
sudo systemctl edit jenkins
```

Dán nội dung sau. Thay `<UID>` bằng kết quả của `id -u jenkins`:

```ini
[Service]
# Chỉ nghe nội bộ, ra ngoài qua Nginx. Nếu 8080 đã bị web khác dùng thì đổi (ví dụ 8090) và sửa Nginx tương ứng
Environment="JENKINS_LISTEN_ADDRESS=127.0.0.1"
Environment="JENKINS_PORT=8080"
Environment="JAVA_OPTS=-Djava.awt.headless=true -Xms512m -Xmx1g"
# Pipeline dùng Docker rootless của chính user jenkins (mục 4), không dùng Docker gốc
Environment="DOCKER_HOST=unix:///run/user/<UID>/docker.sock"
# Giới hạn RAM toàn bộ tiến trình Jenkins và build con (mvn, node, trivy), bảo vệ minikube
MemoryMax=4G
```

```bash
sudo systemctl daemon-reload && sudo systemctl restart jenkins
sudo systemctl status jenkins --no-pager
curl -sI http://127.0.0.1:8080/login | head -1      # HTTP/1.1 200
```

> `MemoryMax` không áp cho phần `docker build` vì việc build chạy trong daemon rootless (một systemd user service riêng). Giới hạn daemon đó ở mục 4.4.

---

## 4. Docker rootless riêng cho user `jenkins`

### 4.1. Gói cần thiết và subuid

Docker rootless cần các gói của Docker Engine. Máy chưa có Docker thì cài bằng script chính thức (cài cả Docker gốc: sau này **chỉ quản trị viên** dùng để chạy minikube, user `jenkins` **không** vào nhóm `docker`):

```bash
command -v docker || curl -fsSL https://get.docker.com | sudo sh
# docker-ce-rootless-extras đi kèm khi cài bằng get.docker.com
sudo apt-get install -y uidmap dbus-user-session systemd-container slirp4netns docker-ce-rootless-extras
sudo usermod --add-subuids 200000-265535 --add-subgids 200000-265535 jenkins
sudo loginctl enable-linger jenkins     # user service của jenkins chạy cả khi không ai đăng nhập
```

### 4.2. Ubuntu 24.04: cho phép rootlesskit dùng user namespace

Ubuntu 24.04 chặn user namespace không đặc quyền bằng AppArmor. Thiếu bước này, `dockerd-rootless-setuptool.sh` sẽ báo lỗi. Ubuntu 22.04 bỏ qua bước này.

```bash
cat <<'EOF' | sudo tee /etc/apparmor.d/usr.bin.rootlesskit
abi <abi/4.0>,
include <tunables/global>

/usr/bin/rootlesskit flags=(unconfined) {
  userns,
  include if exists <local/usr.bin.rootlesskit>
}
EOF
sudo systemctl restart apparmor.service
```

### 4.3. Cài và bật daemon

```bash
sudo machinectl shell jenkins@ /usr/bin/dockerd-rootless-setuptool.sh install
sudo machinectl shell jenkins@ /usr/bin/systemctl --user enable --now docker

# Kiểm tra: phải thấy "rootless", và KHÔNG thấy container minikube
J=$(id -u jenkins)
sudo -u jenkins DOCKER_HOST=unix:///run/user/$J/docker.sock docker info --format '{{.SecurityOptions}}'
sudo -u jenkins DOCKER_HOST=unix:///run/user/$J/docker.sock docker ps     # danh sách rỗng
sudo -u jenkins docker ps                                                 # Docker gốc: permission denied (đúng)
```

### 4.4. Giới hạn tài nguyên của daemon rootless (khuyến nghị)

```bash
sudo machinectl shell jenkins@ /usr/bin/bash -lc 'mkdir -p ~/.config/systemd/user/docker.service.d && printf "[Service]\nMemoryMax=3G\nCPUQuota=200%%\n" > ~/.config/systemd/user/docker.service.d/limits.conf && systemctl --user daemon-reload && systemctl --user restart docker'
```

Build image bị giới hạn ở 3 GB RAM và 2 core, nên prod trong minikube không bị build lấn.

---

## 5. Công cụ dòng lệnh cho pipeline

Cài vào `/usr/local/bin` để mọi user (kể cả `jenkins` và quản trị viên) dùng chung. Đã có (ví dụ cài theo 03 §1) thì bỏ qua dòng tương ứng.

```bash
# kubectl
command -v kubectl || { curl -LO "https://dl.k8s.io/release/$(curl -Ls https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl" \
  && sudo install -m 0755 kubectl /usr/local/bin/kubectl && rm kubectl; }

# Helm 4 (bản đã chạy thử với 2 chart của dự án)
HELM_VERSION=v4.3.2
command -v helm || { curl -fsSL "https://get.helm.sh/helm-${HELM_VERSION}-linux-amd64.tar.gz" | tar xz \
  && sudo install -m 0755 linux-amd64/helm /usr/local/bin/helm && rm -rf linux-amd64; }

# kubeconform: kiểm tra schema manifest
curl -sSL https://github.com/yannh/kubeconform/releases/download/v0.8.0/kubeconform-linux-amd64.tar.gz | tar xz
sudo install -m 0755 kubeconform /usr/local/bin/ && rm kubeconform

# Trivy: quét lỗ hổng image TRƯỚC khi push
wget -qO - https://aquasecurity.github.io/trivy-repo/deb/public.key | gpg --dearmor | sudo tee /usr/share/keyrings/trivy.gpg > /dev/null
echo "deb [signed-by=/usr/share/keyrings/trivy.gpg] https://aquasecurity.github.io/trivy-repo/deb generic main" \
  | sudo tee /etc/apt/sources.list.d/trivy.list
sudo apt-get update && sudo apt-get install -y trivy

# Kiểm tra bằng chính user jenkins
sudo -u jenkins bash -lc 'kubectl version --client && helm version && kubeconform -v && trivy --version && jq --version'
sudo -u jenkins kubectl get ns     # PHẢI lỗi (không có kubeconfig): Jenkins không có quyền admin cluster
```

Maven và Node **không** cài bằng apt: Jenkins tự tải về qua Global Tools (mục 9), ghim đúng phiên bản.

---

## 6. Nginx cho Jenkins

```bash
sudo apt-get install -y nginx certbot python3-certbot-nginx
```

Tên host `<JENKINS_HOST>` phải trỏ về IP máy chủ: có tên miền thì tạo bản ghi DNS A; chưa có tên miền thì dùng `jenkins.<IP-gạch-ngang>.sslip.io` (tự trỏ, không cần cấu hình). Tạo file `/etc/nginx/sites-available/jenkins`, thay `jenkins.example.com` bằng `<JENKINS_HOST>`:

```nginx
upstream jenkins_local {
    server 127.0.0.1:8080;
    keepalive 16;
}

server {
    listen 80;
    server_name jenkins.example.com;
    client_max_body_size 20m;

    # Webhook GitHub: phải mở cho Internet
    location /github-webhook/ {
        proxy_pass http://jenkins_local;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Giao diện Jenkins
    location / {
        # Nên giới hạn IP của nhóm (bỏ comment). Không cố định được IP thì dùng SSH tunnel (ghi chú dưới).
        # allow 203.0.113.0/24;
        # deny all;
        proxy_pass http://jenkins_local;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Port $server_port;
        proxy_read_timeout 90s;
        proxy_request_buffering off;
        proxy_buffering off;              # log build hiển thị trực tiếp
    }
}
```

```bash
sudo ln -s /etc/nginx/sites-available/jenkins /etc/nginx/sites-enabled/jenkins
sudo nginx -t && sudo systemctl reload nginx
# CHỈ khi có tên miền thật (HTTPS). Chạy tạm bằng IP (sslip.io, HTTP) thì bỏ qua dòng này:
sudo certbot --nginx -d jenkins.example.com
```

**Chạy tạm bằng IP (HTTP, chưa có tên miền):** mật khẩu đăng nhập Jenkins sẽ đi qua mạng dạng chữ thường, nên **bắt buộc** dùng cách an toàn nhất ngay dưới đây (chỉ mở `/github-webhook/`, vào UI qua SSH tunnel).

- **Cách an toàn nhất:** chỉ mở `/github-webhook/`, đặt `deny all;` cho `location /`, và vào UI bằng `ssh -L 8080:127.0.0.1:8080 datnadmin@<server>` rồi mở `http://localhost:8080`.
- **Không có tên miền hoặc không muốn mở webhook:** bỏ mục này, vào UI qua SSH tunnel, và dùng quét định kỳ thay webhook (mục 11, *Scan triggers*).

---

## 7. Khởi tạo Jenkins và cài plugin

1. Lấy mật khẩu khởi tạo: `sudo cat /var/lib/jenkins/secrets/initialAdminPassword`.
2. Mở Jenkins: qua SSH tunnel `ssh -L 8080:127.0.0.1:8080 <user>@<IP>` rồi vào `http://localhost:8080` (hoặc `https://<JENKINS_HOST>` nếu đã mở UI qua Nginx có TLS). Chọn **Select plugins to install**, bỏ chọn hết, rồi tick các plugin ở bảng dưới (hoặc cài bằng CLI ở cuối mục).
3. Tạo tài khoản quản trị (đừng đặt tên `admin`; ghi lại user ID này, nó là người được duyệt deploy prod ở mục 12). **Jenkins URL** = `https://<JENKINS_HOST>/` (có tên miền) hoặc `http://<JENKINS_HOST>/` (chạy tạm bằng IP).

| Plugin (ID) | Dùng cho |
|---|---|
| `workflow-aggregator` | Pipeline (Declarative) |
| `pipeline-stage-view` | Xem trực quan các stage |
| `git` | Checkout |
| `github-branch-source` | Multibranch từ GitHub, báo trạng thái commit/PR, nhận webhook (kéo theo plugin `github`) |
| `basic-branch-build-strategies` | Tự build khi có **tag** mới (mặc định Multibranch không tự build tag) |
| `cloudbees-folder` | Folder, credential theo folder |
| `credentials-binding` | `withCredentials` |
| `matrix-auth` | Phân quyền theo user và theo folder |
| `lockable-resources` | `lock()`: không cho 2 lần deploy cùng môi trường chạy song song |
| `nodejs` | Tool Node 22 |
| `junit` | Báo cáo test Surefire |
| `timestamper` | `timestamps()` trong log |
| `ws-cleanup` | `cleanWs()` |
| `configuration-as-code` | *(tuỳ chọn)* lưu cấu hình Jenkins thành YAML |

Cài bằng CLI (dùng API token của tài khoản quản trị: tên tài khoản → Security → API Token):

```bash
wget -q http://127.0.0.1:8080/jnlpJars/jenkins-cli.jar
java -jar jenkins-cli.jar -s http://127.0.0.1:8080/ -auth <user>:<api-token> install-plugin \
  workflow-aggregator pipeline-stage-view git github-branch-source basic-branch-build-strategies \
  cloudbees-folder credentials-binding matrix-auth lockable-resources nodejs junit timestamper ws-cleanup -restart
```

---

## 8. Bảo mật Jenkins

**Manage Jenkins → Security**

| Mục | Giá trị |
|---|---|
| Security Realm | *Jenkins' own user database*, **bỏ tick** "Allow users to sign up" |
| Authorization | *Project-based Matrix Authorization Strategy* |
| Quyền toàn cục | Quản trị: Administer. Nhóm dev: Overall/Read, Job/Read, Job/Build, Job/Cancel, View/Read. **Anonymous: không có quyền gì** (webhook vẫn chạy vì `/github-webhook/` không cần đăng nhập) |
| Agents → TCP port for inbound agents | **Disable** (không có agent ngoài) |
| CSRF Protection | Để bật (mặc định) |
| Markup Formatter | Plain text |

**Manage Jenkins → Nodes → Built-In Node → Configure**

| Mục | Giá trị |
|---|---|
| Number of executors | **1** (máy 16 GB) hoặc 2 (máy ≥ 24 GB) |
| Usage | Use this node as much as possible |

Lưu ý: Jenkins thường khuyên không build trên controller. Ở đây chỉ có 1 máy chủ, nên chấp nhận build trên built-in node, và bù lại bằng: user `jenkins` không có sudo, không vào nhóm docker, dùng Docker rootless, có `MemoryMax`.

**Thói quen vận hành an toàn**
- Mỗi người một tài khoản; không dùng chung tài khoản quản trị.
- Cập nhật Jenkins và plugin hằng tháng (Manage Jenkins → Plugins → Updates); theo dõi https://www.jenkins.io/security/advisories/.
- Không cài plugin không dùng. Không duyệt *In-process Script Approval* khi chưa hiểu đoạn script đó làm gì.

---

## 9. Global Tools

**Manage Jenkins → Tools**

| Loại | Name (Jenkinsfile dùng đúng tên này) | Cấu hình |
|---|---|---|
| JDK | `jdk-21` | Bỏ tick "Install automatically"; `JAVA_HOME` = `/usr/lib/jvm/java-21-openjdk-amd64` |
| Maven | `maven-3.9` | Install automatically → *Install from Apache* → `3.9.9` |
| NodeJS | `node-22` | Install automatically → phiên bản `22.x` mới nhất (≥ 22.12, đúng yêu cầu Vite 8) |
| Git | `Default` | `git` |

---

**Hết phần A.** Kiểm tra trước khi dừng:

```bash
systemctl is-active jenkins                                              # active
curl -sI http://127.0.0.1:8080/login | head -1                           # HTTP/1.1 200
J=$(id -u jenkins); sudo -u jenkins DOCKER_HOST=unix:///run/user/$J/docker.sock docker info --format '{{.SecurityOptions}}'   # có "rootless"
sudo -u jenkins docker ps                                                # PHẢI lỗi permission denied (không chạm Docker gốc)
sudo -u jenkins bash -lc 'kubectl version --client && helm version && kubeconform -v && trivy --version && jq --version'
id jenkins                                                               # không có nhóm docker, sudo
```

Jenkins giờ chạy độc lập, chưa biết gì về dự án. Phần B làm khi cluster của dự án đã chạy.

---

# Phần B: nối Jenkins với dự án DATN

## 10. Folder và Credentials

### 10.1. Cây job

```
DATN/                          (Folder)  credential: github-pat
├── ci/                        (Folder)  credential: ghcr-push, kubeconfig-staging
│   └── datn                   (Multibranch Pipeline, Jenkinsfile ở gốc repo)
└── release/                   (Folder)  credential: kubeconfig-staging, kubeconfig-prod
    └── datn-deploy            (Pipeline, infrastructure/jenkins/Jenkinsfile.deploy)
```

Tạo: Dashboard → New Item → *Folder* `DATN`; vào `DATN` → New Item → *Folder* `ci`, rồi *Folder* `release`.

### 10.2. Tạo token trên GitHub

GHCR chỉ chấp nhận **PAT classic**. Tạo tại GitHub → Settings → Developer settings → Personal access tokens (classic):

| Token | Scope | Dùng ở |
|---|---|---|
| `jenkins-repo` | `repo` (repo private), hoặc `public_repo` + `repo:status` (repo public) | `github-pat`: Jenkins đọc repo, báo trạng thái commit/PR |
| `jenkins-ghcr` | `write:packages` (bao gồm read) | `ghcr-push`: Jenkins push image, gắn tag phiên bản |
| `cluster-ghcr` | `read:packages` | Secret `ghcr-pull` trong cluster ([03 §6.3](03_HUONG_DAN_TRIEN_KHAI.md#63-ghcr-pull)), **không** đưa vào Jenkins |

Đặt hạn dùng (ví dụ 90 ngày) và ghi lịch xoay token.

### 10.3. Thêm credential (vào folder → Credentials → *Stores scoped to …* → Global credentials → Add)

| ID | Kind | Folder | Nội dung |
|---|---|---|---|
| `github-pat` | Username with password | `DATN` | Username GitHub + token `jenkins-repo` |
| `ghcr-push` | Username with password | `DATN/ci` | Username GitHub + token `jenkins-ghcr` |
| `kubeconfig-staging` | Secret file | `DATN/ci` **và** `DATN/release` | File `kubeconfig-datn-staging` do [03 §10](03_HUONG_DAN_TRIEN_KHAI.md#10-kubeconfig-cho-ci-serviceaccount-ci-deployer) sinh ra |
| `kubeconfig-prod` | Secret file | **chỉ** `DATN/release` | File `kubeconfig-datn-prod` |
| `github-webhook-secret` | Secret text | Toàn cục (Manage Jenkins → Credentials → System) | Chuỗi ngẫu nhiên `openssl rand -hex 20`, dùng ở mục 13 |

Tải lên xong thì xoá file kubeconfig trên đĩa: `rm kubeconfig-datn-*`.

> Vì `kubeconfig-prod` không nằm trong `DATN/ci`, nên kể cả khi ai đó sửa `Jenkinsfile` trong một PR để "deploy prod", build đó vẫn không lấy được credential.

### 10.4. Phân quyền theo folder

`DATN/release` → Configure → **Enable project-based security**: bỏ kế thừa quyền Job/Build từ cấp trên (*Inherit permissions* → chọn không kế thừa), chỉ cấp Job/Build cho người phụ trách phát hành. Các thành viên khác chỉ giữ Job/Read.

---

## 11. Job CI (Multibranch): `DATN/ci/datn`

`DATN/ci` → New Item → **Multibranch Pipeline**, tên `datn`.

| Mục | Cấu hình |
|---|---|
| Branch Sources → Add source → **GitHub** | Credentials: `github-pat`; Repository HTTPS URL: `https://github.com/Hecker-Chuoi/DATN-2026` |
| Behaviours → Discover branches | *Exclude branches that are also filed as PRs* (nhánh tính năng chỉ build qua PR) |
| Behaviours → Discover pull requests from origin | *Merging the pull request with the current target branch revision* |
| Behaviours → Discover pull requests from forks | Trust: **Nobody** (PR từ fork chạy bằng `Jenkinsfile` của nhánh đích, không chạy bản mà PR đã sửa) |
| Behaviours → Add → **Discover tags** | Bật |
| Behaviours → Add → *Filter by name (with wildcards)* | Include: `main PR-* v*` |
| **Build strategies** (plugin basic-branch-build-strategies) | Thêm **cả ba**: *Regular branches*, *Change requests*, *Tags* (Ignore tags older than: `1` ngày). Khi đã thêm strategy, Jenkins bỏ hành vi mặc định, nên thiếu cái nào thì loại đó không tự build |
| Build Configuration | Mode: *by Jenkinsfile*, Script Path: `Jenkinsfile` |
| Scan Multibranch Pipeline Triggers | *Periodically if not otherwise run*: `1 day` (dự phòng khi webhook lỗi). Không có webhook thì đặt `2 minutes` |
| Orphaned Item Strategy | Discard old items, Max # of old items to keep: `10` |

Save, Jenkins tự quét repo. Nội dung `Jenkinsfile` nằm ở [03 §12.3](03_HUONG_DAN_TRIEN_KHAI.md#123-jenkinsfile-gốc-repo-job-multibranch-datncidatn).

GitHub Branch Source tự gửi trạng thái về commit/PR với tên `continuous-integration/jenkins/pr-merge` (PR) và `continuous-integration/jenkins/branch` (nhánh). Dùng tên này cho branch protection ([03 §11](03_HUONG_DAN_TRIEN_KHAI.md#11-jenkins-và-github)).

---

## 12. Job deploy / duyệt prod: `DATN/release/datn-deploy`

`DATN/release` → New Item → **Pipeline**, tên `datn-deploy`.

| Mục | Cấu hình |
|---|---|
| General | Tick *Do not allow concurrent builds* |
| Pipeline → Definition | *Pipeline script from SCM* → Git |
| Repository URL / Credentials | `https://github.com/Hecker-Chuoi/DATN-2026` / `github-pat` |
| Branch Specifier | `*/main` (chỉ chạy script đã được review và merge vào main) |
| Script Path | `infrastructure/jenkins/Jenkinsfile.deploy` |

Nội dung `Jenkinsfile.deploy` nằm ở [03 §12.4](03_HUONG_DAN_TRIEN_KHAI.md#124-infrastructurejenkinsjenkinsfiledeploy-job-datnreleasedatn-deploy).

> **Lần đầu:** bấm *Build Now* một lần. Build này sẽ lỗi ở bước kiểm tra `IMAGE_TAG` (đúng như mong đợi), nhưng sau đó Jenkins mới hiện nút **Build with Parameters**, vì tham số khai báo trong `Jenkinsfile` chỉ được đăng ký sau lần chạy đầu.

Tham số của job:

| Tham số | Giá trị | Dùng khi |
|---|---|---|
| `TARGET` | `staging` / `production` | `production` luôn qua bước duyệt (`input`) |
| `ACTION` | `deploy` | Cài image `IMAGE_TAG` (git SHA 40 ký tự) bằng `deploy.sh` (`helm upgrade --install`) |
| | `helm-rollback` | `helm rollback datn-app [REVISION]`; để trống `REVISION` thì quay về revision ngay trước |
| `VERSION` | `v1.2.0` | Chỉ để ghi chú tên build |

Trong `Jenkinsfile.deploy`, sửa `submitter: 'truongnhom'` thành danh sách user ID Jenkins được phép duyệt prod, cách nhau bằng dấu phẩy.

---

## 13. Webhook GitHub

1. Jenkins: Manage Jenkins → System → **GitHub** → Advanced → *Shared secrets* → chọn `github-webhook-secret`. Bỏ tick *Manage hooks* (tạo webhook bằng tay ở bước 2).
2. GitHub: repo → Settings → Webhooks → **Add webhook**:
   - Payload URL: `https://<JENKINS_HOST>/github-webhook/` (có dấu `/` cuối). Đã chạy certbot thì **phải** dùng https, vì Nginx chuyển hướng http → https và GitHub không theo chuyển hướng (301). Chạy tạm bằng IP (chưa certbot) thì dùng `http://<JENKINS_HOST>/github-webhook/`; payload vẫn được ký bằng secret nên Jenkins từ chối request giả
   - Content type: `application/json`
   - Secret: cùng chuỗi với `github-webhook-secret`
   - Events: *Let me select individual events* → **Pushes**, **Pull requests** (tag cũng được gửi qua sự kiện Push)
3. Kiểm tra: Webhooks → *Recent Deliveries* → response `200`.

---

## 14. Kiểm tra end-to-end

| # | Hành động | Kỳ vọng |
|---|---|---|
| E1 | Mở một PR nhỏ vào `main` | `DATN/ci/datn` có nhánh `PR-<n>` đang chạy; PR trên GitHub có check `continuous-integration/jenkins/pr-merge` |
| E2 | Merge PR | Build `main` chạy CI, build/quét/push 6 image `:<sha>`, deploy staging, smoke test xanh |
| E3 | `git tag -a v1.0.0 -m v1.0.0 && git push origin v1.0.0` | Build `v1.0.0` gắn tag phiên bản cho image, rồi khởi chạy `DATN/release/datn-deploy` với `TARGET=production` |
| E4 | Mở `datn-deploy` → build đang chờ → **Deploy** (bằng tài khoản có trong `submitter`) | Prod chạy đúng SHA của tag; tài khoản không có trong `submitter` không bấm duyệt được |
| E5 | `datn-deploy` → Build with Parameters → `TARGET=staging`, `ACTION=deploy`, `IMAGE_TAG=<sha cũ>` | Staging chạy SHA cũ; `helm -n datn-staging history datn-app` có revision mới |
| E5b | `datn-deploy` → `TARGET=staging`, `ACTION=helm-rollback`, `REVISION` trống | Staging quay về revision Helm ngay trước |
| E6 | Trong một PR, thêm dòng `withCredentials([file(credentialsId: 'kubeconfig-prod', ...)])` vào `Jenkinsfile` | Build PR lỗi: `Could not find credentials entry with ID 'kubeconfig-prod'` |
| E7 | `sudo -u jenkins kubectl get pods -A` ; `sudo -u jenkins docker ps` | Cả hai đều lỗi: Jenkins không có quyền admin cluster, không chạm được Docker gốc |

---

## 15. Bảo trì

**Sao lưu JENKINS_HOME** (cấu hình job, credential đã mã hoá, lịch sử build), bỏ qua workspace và cache:

```bash
sudo mkdir -p /var/backups/jenkins
# crontab của root:  0 3 * * * tar ... (một dòng)
sudo tar --exclude='workspace' --exclude='caches' --exclude='.m2' --exclude='.local/share/docker' \
         --exclude='tools' -czf /var/backups/jenkins/jenkins-home-$(date +%F).tgz -C /var/lib jenkins
sudo find /var/backups/jenkins -name '*.tgz' -mtime +14 -delete
```

`secrets/master.key` và `secrets/hudson.util.Secret` nằm trong bản sao lưu. Thiếu hai file này thì credential không giải mã được, nên phải giữ bản sao lưu ở nơi an toàn.

**Dọn đĩa Docker rootless** hằng tuần (crontab của user `jenkins`: `sudo crontab -u jenkins -e`):

```cron
0 4 * * 0 DOCKER_HOST=unix:///run/user/<UID>/docker.sock docker image prune -af --filter until=168h && DOCKER_HOST=unix:///run/user/<UID>/docker.sock docker builder prune -f --filter until=168h
```

**Nâng cấp:** `sudo apt-get update && sudo apt-get install --only-upgrade jenkins`, rồi Manage Jenkins → Plugins → Updates. Sao lưu trước khi nâng cấp.

**Làm mới image nền** (maven, temurin, node, nginx) mỗi tháng: `docker pull` các image đó bằng Docker rootless, rồi build lại để nhận bản vá bảo mật. Pipeline không dùng `--pull` để tránh giới hạn số lần pull của Docker Hub.

---

## 16. Xử lý sự cố

| Triệu chứng | Nguyên nhân | Cách xử lý |
|---|---|---|
| Jenkins không lên, `journalctl -u jenkins` báo `Address already in use` | Cổng 8080 đã bị dùng | Đổi `JENKINS_PORT` (mục 3) và `upstream` trong Nginx |
| `Cannot connect to the Docker daemon at unix:///run/user/...` | Daemon rootless chưa chạy, thiếu linger, sai `<UID>` | `sudo machinectl shell jenkins@ /usr/bin/systemctl --user status docker`; `loginctl show-user jenkins \| grep Linger`; kiểm tra `DOCKER_HOST` |
| `dockerd-rootless-setuptool.sh` lỗi `permission denied` / `newuidmap` | Ubuntu 24.04 chặn userns, hoặc thiếu subuid | Mục 4.2; `grep jenkins /etc/subuid` |
| `got permission denied while trying to connect to /var/run/docker.sock` | Pipeline đang gọi Docker gốc | `DOCKER_HOST` chưa vào môi trường dịch vụ: `sudo systemctl show jenkins -p Environment` |
| Webhook GitHub báo `302`/`301` | Payload URL dùng http trong khi Nginx đã chuyển sang https (sau certbot), hoặc thiếu `/` cuối | Mục 13 |
| Webhook `403 No valid crumb` | URL sai (không phải `/github-webhook/`) | Sửa URL |
| PR không có check trên GitHub | Token `github-pat` thiếu scope `repo` / `repo:status` | Tạo lại token (mục 10.2) |
| Tag mới không tự build | Thiếu *Discover tags* hoặc *Build strategies → Tags*, hoặc tag cũ hơn 1 ngày | Mục 11; hoặc vào job tag và bấm Build |
| Không thấy *Build with Parameters* | Chưa chạy lần đầu | Mục 12 |
| `RejectedAccessException: Scripts not permitted` | Groovy sandbox chặn một method | Manage Jenkins → In-process Script Approval: đọc kỹ rồi mới duyệt |
| Build bị `Killed` / máy chủ chậm | Thiếu RAM | 1 executor, swap, `MemoryMax` (mục 3, 4.4), xem `free -h` |
| `kubectl: Unauthorized` ở bước deploy | Token SA đã xoay hoặc credential cũ | Chạy lại 03 §10, cập nhật credential `kubeconfig-*` |
| `helm: command not found` / `kubeconform: not found` | Công cụ chưa cài vào `/usr/local/bin` | Mục 5 |
| `secrets is forbidden … ci-deployer` | Lệnh helm chạy thiếu `HELM_DRIVER=configmap` | `Jenkinsfile` và `deploy.sh` đều đặt biến này; kiểm tra lệnh helm tự viết thêm |
| `another operation (install/upgrade/rollback) is in progress` | Lần deploy trước bị huỷ giữa chừng | `datn-deploy` → `ACTION=helm-rollback`; chi tiết ở [03 §14](03_HUONG_DAN_TRIEN_KHAI.md#14-vận-hành-và-xử-lý-sự-cố) |
| Bước duyệt prod giữ executor, các build khác phải chờ | Stage `input` có `agent` | Stage duyệt không được khai báo `agent` (xem `Jenkinsfile.deploy`) |

---

## 17. Phụ lục: Jenkins đã có sẵn trên máy chủ

Nếu máy chủ đã có Jenkins từ trước (phục vụ việc khác), **không cần cài lại**. Đối chiếu danh sách sau:

| Kiểm tra | Lệnh / nơi xem | Cần đạt |
|---|---|---|
| Phiên bản | Manage Jenkins → About | LTS còn được hỗ trợ, Java 17 hoặc 21 |
| User chạy Jenkins không thuộc nhóm `docker` | `id jenkins` | Nếu đang thuộc: `sudo gpasswd -d jenkins docker`, rồi làm mục 4 |
| Có JDK 21 cho build | `ls /usr/lib/jvm` | Thiếu thì `apt-get install openjdk-21-jdk`, khai báo ở mục 9 |
| Plugin | Mục 7 | Cài phần còn thiếu |
| Tách biệt với job cũ | — | Đặt mọi thứ của DATN trong folder `DATN` với credential riêng (mục 10); job cũ không thấy credential của DATN |
| Executors / RAM | Mục 1, 8 | Tính cả tải của các job cũ vào ngân sách |

**Nếu Jenkins hiện chạy trong container Docker** (ví dụ image `jenkins/jenkins:lts` có mount `/var/run/docker.sock`; bản đề xuất cũ `docs/cicd/common/jenkins.Dockerfile` trong lịch sử git, commit `f280c77`, dùng đúng cách này):
- Mount `docker.sock` của Docker gốc thì Jenkins có **toàn quyền cluster** (`docker exec minikube ...`). Nên chuyển sang cài native theo tài liệu này, hoặc cho container dùng daemon riêng (Docker-in-Docker hoặc rootless) thay vì socket gốc.
- Container Jenkins nằm khác mạng Docker với minikube nên không gọi được `https://192.168.49.2:8443`. Cần nối vào mạng của minikube: `docker network connect minikube <tên-container-jenkins>`.
- Image Jenkins phải có sẵn `kubectl`, `helm`, `kubeconform`, `trivy`, `jq`, Docker CLI và JDK 21 (tự build image Jenkins riêng từ `jenkins/jenkins:lts-jdk21`).
