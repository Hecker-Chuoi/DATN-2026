# 01 — Hướng dẫn setup Jenkins trên một server riêng

> Jenkins chạy trên **server riêng** (gọi là **server Jenkins**), tách khỏi **server Minikube** đang chạy ứng dụng.
> Jenkins làm: chạy test → build Docker image → push lên **Docker Hub** → SSH sang server Minikube chạy `helm upgrade` (đổi tag image) → Minikube kéo image mới từ Docker Hub.
> Đọc kèm: [02 — Nghiên cứu CI/CD trên source code](../02_NGHIEN_CUU_CICD_TREN_SOURCE_CODE.md), [03 — Tổng quan các bước](../03_TONG_QUAN_CAC_BUOC_TRIEN_KHAI.md).
>
> Tài liệu chia 2 phần:
> - **Phần A (mục 1–8)**: cài Jenkins. Chỉ cần một máy Ubuntu có SSH + `sudo`, chưa cần repo hay Minikube.
> - **Phần B (mục 9–12)**: nối Jenkins với Docker Hub, GitHub và server Minikube. Làm khi đã có `Jenkinsfile` trong repo và server Minikube đã chạy.
>
> Ký hiệu dùng trong tài liệu: `<JENKINS_HOST>` (tên miền/IP server Jenkins), `<MINIKUBE_HOST>` (IP server Minikube), `<DOCKERHUB_USER>` (tài khoản Docker Hub).

## Mục lục

**Phần A — Cài Jenkins**
1. [Kiến trúc và yêu cầu server](#1-kiến-trúc-và-yêu-cầu-server)
2. [Chuẩn bị server](#2-chuẩn-bị-server)
3. [Cài Java 21 và Jenkins LTS](#3-cài-java-21-và-jenkins-lts)
4. [Cài Docker cho Jenkins](#4-cài-docker-cho-jenkins)
5. [Nginx + HTTPS trước Jenkins](#5-nginx--https-trước-jenkins)
6. [Khởi tạo Jenkins, cài plugin](#6-khởi-tạo-jenkins-cài-plugin)
7. [Bảo mật Jenkins](#7-bảo-mật-jenkins)
8. [Global Tools (JDK, Maven, Node)](#8-global-tools-jdk-maven-node)

**Phần B — Nối Jenkins với dự án**

9. [Credentials](#9-credentials)
10. [Tạo job Multibranch Pipeline](#10-tạo-job-multibranch-pipeline)
11. [Webhook GitHub](#11-webhook-github)
12. [Chạy thử pipeline kiểm tra](#12-chạy-thử-pipeline-kiểm-tra)
13. [Bảo trì](#13-bảo-trì)
14. [Xử lý sự cố thường gặp](#14-xử-lý-sự-cố-thường-gặp)

---

# Phần A — Cài Jenkins

## 1. Kiến trúc và yêu cầu server

```
 Developer ── git push ──► GitHub ──── webhook ────┐
                                                    ▼
                                ┌──────────────────────────────────┐
                                │ SERVER JENKINS                   │
                                │  Nginx :443 ─► Jenkins 127.0.0.1:8080
                                │  Docker Engine (build image)     │
                                │  JDK 21, Maven, Node 22          │
                                └──────┬─────────────────┬─────────┘
                          docker push  │                 │ ssh deployer@<MINIKUBE_HOST>
                                       ▼                 ▼   "deploy <tag>"
                                ┌────────────┐   ┌──────────────────────────┐
                                │ Docker Hub │◄──│ SERVER MINIKUBE          │
                                └────────────┘   │  minikube (k8s) pull image│
                                     pull image  │  helm/kubectl            │
                                                 └──────────────────────────┘
```

| Thành phần | Tối thiểu | Khuyến nghị | Ghi chú |
|---|---|---|---|
| CPU | 2 vCPU | 4 vCPU | Maven build 5 service + build 6 image |
| RAM | 4 GB (+ swap 4 GB) | 8 GB | Jenkins ~1 GB, Maven ~1,5 GB, `docker build` ~2 GB |
| Đĩa | 40 GB | 60–80 GB | `/var/lib/jenkins` (workspace, `~/.m2`, log) + `/var/lib/docker` (layer image) |
| HĐH | Ubuntu 22.04 / 24.04 LTS | | |
| Cổng mở | 22 (SSH), 80, 443 | | **Không** mở 8080 ra Internet |

> Server Jenkins **không** chạy Minikube, nên cho user `jenkins` vào nhóm `docker` là chấp nhận được (Docker này chỉ để build). Cần nhớ: ai có quyền `docker` ≈ có quyền root **trên server Jenkins**, vì vậy chỉ cho người tin cậy quyền sửa `Jenkinsfile`/cấu hình job.

---

## 2. Chuẩn bị server

```bash
sudo apt-get update && sudo apt-get -y upgrade
sudo timedatectl set-timezone Asia/Ho_Chi_Minh
sudo hostnamectl set-hostname datn-jenkins

# Swap (bắt buộc nếu RAM <= 4-8 GB, tránh OOM lúc build)
sudo fallocate -l 4G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# Tường lửa: chỉ SSH + HTTP/HTTPS
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
```

Nếu dùng cloud (AWS EC2, GCP, ...) thì mở các cổng tương ứng trong **Security Group / Firewall rule** của nhà cung cấp.

---

## 3. Cài Java 21 và Jenkins LTS

```bash
sudo apt-get install -y fontconfig openjdk-21-jdk git curl wget jq unzip
java -version            # openjdk 21.x — dùng chung cho Jenkins và để build backend (dự án cần JDK 21)

# Kho apt Jenkins LTS. Jenkins thay khoá ký định kỳ (khoá 2023 đã bỏ, apt báo NO_PUBKEY).
# Khoá hiện tại: jenkins.io-2026.key. Nếu lỗi, xem khoá mới nhất tại:
# https://www.jenkins.io/doc/book/installing/linux/#debianubuntu
sudo mkdir -p /etc/apt/keyrings
sudo wget -O /etc/apt/keyrings/jenkins-keyring.asc https://pkg.jenkins.io/debian-stable/jenkins.io-2026.key
echo "deb [signed-by=/etc/apt/keyrings/jenkins-keyring.asc] https://pkg.jenkins.io/debian-stable binary/" \
  | sudo tee /etc/apt/sources.list.d/jenkins.list > /dev/null
sudo apt-get update && sudo apt-get install -y jenkins
```

Cấu hình dịch vụ: chỉ nghe `127.0.0.1` (ra ngoài qua Nginx), giới hạn heap.

```bash
sudo systemctl edit jenkins
```

Dán vào:

```ini
[Service]
Environment="JENKINS_LISTEN_ADDRESS=127.0.0.1"
Environment="JENKINS_PORT=8080"
Environment="JAVA_OPTS=-Djava.awt.headless=true -Xms512m -Xmx1g -Duser.timezone=Asia/Ho_Chi_Minh"
```

```bash
sudo systemctl daemon-reload && sudo systemctl restart jenkins
sudo systemctl status jenkins --no-pager
curl -sI http://127.0.0.1:8080/login | head -1     # HTTP/1.1 200 (hoặc 403 trước khi khởi tạo)
```

---

## 4. Cài Docker cho Jenkins

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker jenkins
sudo systemctl restart jenkins          # để Jenkins nhận nhóm docker mới

# Bật BuildKit mặc định (Dockerfile backend dùng RUN --mount=type=cache)
echo '{ "features": { "buildkit": true }, "log-driver": "json-file", "log-opts": { "max-size": "10m", "max-file": "3" } }' \
  | sudo tee /etc/docker/daemon.json
sudo systemctl restart docker

# Kiểm tra bằng chính user jenkins
sudo -u jenkins docker version
sudo -u jenkins docker run --rm hello-world
```

**(Tuỳ chọn)** Công cụ dùng trong pipeline:

```bash
# Trivy — quét lỗ hổng image trước khi push (khuyến nghị, chỉ cần báo cáo)
wget -qO - https://aquasecurity.github.io/trivy-repo/deb/public.key | gpg --dearmor | sudo tee /usr/share/keyrings/trivy.gpg > /dev/null
echo "deb [signed-by=/usr/share/keyrings/trivy.gpg] https://aquasecurity.github.io/trivy-repo/deb generic main" \
  | sudo tee /etc/apt/sources.list.d/trivy.list
sudo apt-get update && sudo apt-get install -y trivy

# kubectl + helm — CHỈ cần nếu chọn cách deploy "Jenkins tự chạy helm qua đường hầm SSH" (xem 02 §5).
# Cách khuyến nghị (SSH sang server Minikube chạy script) thì KHÔNG cần cài ở đây.
```

---

## 5. Nginx + HTTPS trước Jenkins

Cần một tên miền trỏ về **IP public** của server Jenkins (bản ghi DNS A). Chưa có tên miền thì dùng tạm `jenkins.<IP-dạng-gạch-ngang>.sslip.io` (ví dụ `jenkins.13-250-1-2.sslip.io`, tự trỏ về `13.250.1.2`).

> Trên EC2, IP trong prompt (`ip-172-31-...`) là IP **nội bộ**, không dùng được. IP public lấy bằng `curl -s http://checkip.amazonaws.com`. IP public đổi mỗi lần stop/start instance → nên gắn **Elastic IP**, nếu không thì tên miền sslip.io, chứng chỉ và webhook GitHub đều phải làm lại.

**Security Group** phải mở cổng **80** và **443** cho `0.0.0.0/0` (Let's Encrypt và webhook GitHub gọi từ Internet).

```bash
sudo apt-get install -y nginx certbot python3-certbot-nginx

# Chọn MỘT trong hai dòng DOMAIN:
DOMAIN=jenkins.tenmien-that.com                                              # có tên miền riêng
DOMAIN="jenkins.$(curl -s http://checkip.amazonaws.com | tr . -).sslip.io"   # chưa có tên miền
echo "$DOMAIN"
```

Tạo `/etc/nginx/sites-available/jenkins`.

> **Không dán nội dung cấu hình thẳng vào terminal**: bash sẽ chạy từng dòng như một lệnh (`upstream: command not found`…). Dán **nguyên khối** lệnh dưới đây, kể cả dòng `EOF` cuối. Giữ `'EOF'` trong nháy đơn, nếu không bash sẽ thay `$host`, `$scheme`… thành chuỗi rỗng.

```bash
sudo tee /etc/nginx/sites-available/jenkins > /dev/null <<'EOF'
upstream jenkins_local {
    server 127.0.0.1:8080;
    keepalive 16;
}

server {
    listen 80;
    server_name jenkins.example.com;
    client_max_body_size 20m;

    # Webhook GitHub: phải mở cho Internet (không cần đăng nhập)
    location /github-webhook/ {
        proxy_pass http://jenkins_local;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        # Nên giới hạn IP của nhóm nếu có IP cố định:
        # allow 203.0.113.10; deny all;
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
        proxy_buffering off;          # log build hiển thị trực tiếp
    }
}
EOF
```

Thay tên mẫu bằng `$DOMAIN`, bật site và kiểm tra:

```bash
sudo sed -i "s/server_name .*;/server_name $DOMAIN;/" /etc/nginx/sites-available/jenkins
sudo ln -sf /etc/nginx/sites-available/jenkins /etc/nginx/sites-enabled/jenkins
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx     # phải thấy "syntax is ok" + "test is successful"

curl -I "http://$DOMAIN/login"                   # phải trả 200/403 từ Jenkins; timeout = Security Group chưa mở cổng 80
```

Cấp HTTPS (certbot tự thêm khối 443 và chuyển hướng 80 → 443; lần đầu hỏi email + đồng ý điều khoản):

```bash
sudo certbot --nginx -d "$DOMAIN"
```

> **Lỗi thường gặp với certbot:**
> - `Cannot issue for "jenkins.example.com" ... forbidden by policy`: vẫn đang dùng tên mẫu. Đặt `DOMAIN` rồi chạy lại khối `sed` ở trên trước.
> - Lỗi `Timeout during connect` / `Connection refused`: cổng 80 chưa mở trong Security Group, hoặc DNS chưa trỏ đúng IP public.
> - Với sslip.io, Let's Encrypt thường vẫn cấp nhưng có thể dính rate limit (nhiều người dùng chung). Khi đó dùng tạm HTTP như ghi chú dưới.

> **Chưa có HTTPS**: mật khẩu Jenkins đi dạng chữ thường. Khi đó đặt `deny all;` cho `location /`, chỉ để mở `/github-webhook/`, và vào giao diện bằng SSH tunnel:
> `ssh -L 8080:127.0.0.1:8080 <user>@<JENKINS_HOST>` rồi mở `http://localhost:8080`.

---

## 6. Khởi tạo Jenkins, cài plugin

1. Lấy mật khẩu khởi tạo: `sudo cat /var/lib/jenkins/secrets/initialAdminPassword`.
2. Mở `https://<JENKINS_HOST>` (hoặc qua SSH tunnel). Chọn **Select plugins to install** → bỏ chọn hết → tick các plugin ở bảng dưới.
3. Tạo tài khoản quản trị (đừng đặt tên `admin`). **Jenkins URL** = `https://<JENKINS_HOST>/`.

| Plugin (ID) | Dùng cho |
|---|---|
| `workflow-aggregator` | Pipeline (Declarative) |
| `pipeline-stage-view` | Xem trực quan các stage |
| `git` | Checkout code |
| `github-branch-source` | Multibranch từ GitHub, báo trạng thái commit/PR, nhận webhook |
| `cloudbees-folder` | Folder, credential theo folder |
| `credentials-binding` | `withCredentials` (Docker Hub token) |
| `ssh-agent` | `sshagent([...])` để SSH sang server Minikube |
| `matrix-auth` | Phân quyền theo user |
| `nodejs` | Tool Node 22 (build frontend) |
| `junit` | Báo cáo test Maven Surefire |
| `timestamper` | Thời gian trong log |
| `ws-cleanup` | `cleanWs()` dọn workspace |
| `docker-workflow` | *(tuỳ chọn)* cú pháp `docker.build()/push()` trong pipeline |

Hoặc cài bằng CLI (lấy API token: tên tài khoản → Security → API Token):

```bash
wget -q http://127.0.0.1:8080/jnlpJars/jenkins-cli.jar
java -jar jenkins-cli.jar -s http://127.0.0.1:8080/ -auth <user>:<api-token> install-plugin \
  workflow-aggregator pipeline-stage-view git github-branch-source cloudbees-folder credentials-binding \
  ssh-agent matrix-auth nodejs junit timestamper ws-cleanup -restart
```

---

## 7. Bảo mật Jenkins

**Manage Jenkins → Security**

| Mục | Giá trị |
|---|---|
| Security Realm | *Jenkins' own user database*, **bỏ tick** "Allow users to sign up" |
| Authorization | *Matrix-based security* |
| Quyền | Quản trị: Administer. Thành viên nhóm: Overall/Read, Job/Read, Job/Build, Job/Cancel. **Anonymous: không có quyền** (webhook vẫn chạy) |
| Agents → TCP port for inbound agents | **Disable** |
| CSRF Protection | Giữ bật (mặc định) |

**Manage Jenkins → Nodes → Built-In Node → Configure**: *Number of executors* = **1** (RAM 4–8 GB) hoặc 2 (RAM ≥ 16 GB).

Thói quen an toàn:
- Mỗi người một tài khoản, không dùng chung tài khoản quản trị.
- Cập nhật Jenkins + plugin hằng tháng; theo dõi https://www.jenkins.io/security/advisories/.
- Không duyệt *In-process Script Approval* khi chưa hiểu đoạn script.
- Pipeline chạy code từ PR có thể đọc credential: với Multibranch, chỉ build PR từ **collaborator** (mục 10), không build PR từ fork lạ.

---

## 8. Global Tools (JDK, Maven, Node)

**Manage Jenkins → Tools**

| Loại | Name (Jenkinsfile dùng đúng tên) | Cấu hình |
|---|---|---|
| JDK | `jdk-21` | Bỏ tick *Install automatically*; `JAVA_HOME` = `/usr/lib/jvm/java-21-openjdk-amd64` |
| Maven | `maven-3.9` | *Install automatically* → *Install from Apache* → `3.9.9` |
| NodeJS | `node-22` | *Install automatically* → bản `22.x` mới nhất (≥ 22.12, Vite 8 yêu cầu) |
| Git | `Default` | `git` |

**Kiểm tra cuối phần A:**

```bash
systemctl is-active jenkins nginx docker          # active x3
sudo -u jenkins docker ps                         # chạy được, không lỗi permission
curl -sI https://<JENKINS_HOST>/login | head -1   # HTTP/2 200
```

---

# Phần B — Nối Jenkins với dự án

## 9. Credentials

**Manage Jenkins → Credentials → System → Global credentials → Add Credentials** (hoặc đặt trong folder `DATN` nếu muốn giới hạn phạm vi).

| ID (Jenkinsfile dùng) | Kind | Nội dung | Tạo ở đâu |
|---|---|---|---|
| `github-token` | Username with password | Username = tài khoản GitHub, Password = **Fine-grained PAT** (repo DATN-2026: *Contents: Read*, *Commit statuses: Read & Write*, *Pull requests: Read*, *Metadata: Read*) | GitHub → Settings → Developer settings → Personal access tokens |
| `dockerhub` | Username with password | Username = `<DOCKERHUB_USER>`, Password = **Access Token** (quyền *Read & Write*), **không** dùng mật khẩu tài khoản | Docker Hub → Account settings → Personal access tokens |
| `minikube-ssh` | SSH Username with private key | Username = `deployer`, Private key = khoá riêng (xem dưới) | Tự sinh |

**Sinh khoá SSH cho bước deploy** (chạy trên server Jenkins):

```bash
sudo -u jenkins ssh-keygen -t ed25519 -N "" -f /var/lib/jenkins/.ssh/id_deploy -C "jenkins-deploy"
sudo cat /var/lib/jenkins/.ssh/id_deploy        # dán vào credential minikube-ssh
sudo cat /var/lib/jenkins/.ssh/id_deploy.pub    # đưa sang server Minikube (dưới)

# Ghi known_hosts để SSH không hỏi "yes/no" khi chạy pipeline
sudo -u jenkins bash -c 'ssh-keyscan -H <MINIKUBE_HOST> >> /var/lib/jenkins/.ssh/known_hosts'
```

Trên **server Minikube**, tạo user `deployer` chỉ dùng cho deploy:

```bash
sudo adduser --disabled-password --gecos "" deployer
sudo mkdir -p /home/deployer/.ssh
echo "<nội dung id_deploy.pub>" | sudo tee /home/deployer/.ssh/authorized_keys
sudo chown -R deployer:deployer /home/deployer/.ssh && sudo chmod 700 /home/deployer/.ssh && sudo chmod 600 /home/deployer/.ssh/authorized_keys
```

> `deployer` cần `kubectl` + `helm` (cài trên server Minikube) và một kubeconfig **chỉ có quyền trong namespace của ứng dụng** (ServiceAccount + Role), không dùng kubeconfig admin của minikube. Chi tiết ở [02 §5](../02_NGHIEN_CUU_CICD_TREN_SOURCE_CODE.md#5-deploy-từ-jenkins-sang-minikube).
> Có thể khoá thêm: trong `authorized_keys` thêm tiền tố `command="/home/deployer/deploy.sh",no-port-forwarding,no-pty ` để khoá này chỉ chạy được đúng script deploy.

Kiểm tra từ server Jenkins: `sudo -u jenkins ssh -i /var/lib/jenkins/.ssh/id_deploy deployer@<MINIKUBE_HOST> 'echo ok'`.

**Docker Hub — chuẩn bị repository:** tạo sẵn 6 repo (hoặc để lần push đầu tự tạo): `datn-api-gateway`, `datn-identity-service`, `datn-work-service`, `datn-workspace-service`, `datn-notification-service`, `datn-frontend`. Repo **public** thì Minikube kéo về không cần đăng nhập; repo **private** thì phải tạo `imagePullSecret` trong cluster (02 §4). Gói miễn phí giới hạn số repo private và số lần pull — kiểm tra gói hiện tại của tài khoản.

---

## 10. Tạo job Multibranch Pipeline

Dashboard → **New Item** → *Folder* `DATN` → trong `DATN` → **New Item** → *Multibranch Pipeline* tên `datn`.

| Mục | Giá trị |
|---|---|
| Branch Sources | **GitHub**; Credentials = `github-token`; Repository URL = `https://github.com/<owner>/DATN-2026` |
| Behaviours | Discover branches: *Exclude branches that are also filed as PRs*; Discover pull requests from origin: *Merging the PR with the target branch*; Discover PRs from forks: **Trust = Nobody** (hoặc xoá behaviour này) |
| Build Configuration | Mode = *by Jenkinsfile*, Script Path = `Jenkinsfile` |
| Scan Triggers | *Periodically if not otherwise run* = 1 day (dự phòng khi webhook lỗi) |
| Orphaned Item Strategy | Giữ 7 ngày / 20 build |

Bấm **Save** → Jenkins quét repo, tạo job con cho mỗi nhánh có `Jenkinsfile`.

---

## 11. Webhook GitHub

Repo GitHub → **Settings → Webhooks → Add webhook**:

| Mục | Giá trị |
|---|---|
| Payload URL | `https://<JENKINS_HOST>/github-webhook/` (có `/` cuối) |
| Content type | `application/json` |
| Events | *Just the push event* + *Pull requests* (chọn "Let me select individual events") |

Sau khi lưu, tab **Recent Deliveries** phải hiện mã `200`. Push một commit thử → job nhánh tương ứng tự chạy.

---

## 12. Chạy thử pipeline kiểm tra

Trước khi viết `Jenkinsfile` thật, tạo một job **Pipeline** thường (New Item → Pipeline) tên `smoke-test`, dán script dưới để kiểm tra đủ công cụ và credential:

```groovy
pipeline {
  agent any
  tools { jdk 'jdk-21'; maven 'maven-3.9'; nodejs 'node-22' }
  stages {
    stage('Tools') {
      steps {
        sh 'java -version && mvn -v && node -v && npm -v && docker version'
      }
    }
    stage('Docker Hub login') {
      steps {
        withCredentials([usernamePassword(credentialsId: 'dockerhub', usernameVariable: 'DH_USER', passwordVariable: 'DH_TOKEN')]) {
          sh 'echo "$DH_TOKEN" | docker login -u "$DH_USER" --password-stdin && docker logout'
        }
      }
    }
    stage('SSH Minikube') {
      steps {
        sshagent(['minikube-ssh']) {
          sh 'ssh -o BatchMode=yes deployer@<MINIKUBE_HOST> "hostname && kubectl get pods -n datn && helm version"'
        }
      }
    }
  }
}
```

Cả 3 stage xanh là Jenkins sẵn sàng. `Jenkinsfile` thật của dự án: xem khung mẫu ở [02 §6](../02_NGHIEN_CUU_CICD_TREN_SOURCE_CODE.md#6-jenkinsfile).

---

## 13. Bảo trì

| Việc | Cách làm | Tần suất |
|---|---|---|
| Dọn image cũ (đĩa đầy rất nhanh với 6 image Java) | `sudo crontab -e` → `0 3 * * * docker image prune -af --filter "until=72h" && docker builder prune -f --filter "until=72h"` | Hằng ngày |
| Giữ số build | Jenkinsfile: `buildDiscarder(logRotator(numToKeepStr: '20'))` | — |
| Sao lưu Jenkins | `sudo tar czf jenkins-$(date +%F).tgz -C /var/lib jenkins --exclude='jenkins/workspace' --exclude='jenkins/.m2'` | Hằng tuần / trước khi nâng cấp |
| Cập nhật Jenkins | `sudo apt-get update && sudo apt-get install --only-upgrade jenkins`; plugin: Manage Jenkins → Plugins → Updates | Hằng tháng |
| Xem log Jenkins | `sudo journalctl -u jenkins -f` | Khi lỗi |
| Kiểm tra đĩa | `df -h /var/lib/jenkins /var/lib/docker` | Hằng tuần |

---

## 14. Xử lý sự cố thường gặp

| Triệu chứng | Nguyên nhân / cách xử lý |
|---|---|
| `apt-get update` báo `NO_PUBKEY` với kho Jenkins | Khoá ký đã đổi → tải lại khoá mới theo link ở mục 3 |
| `permission denied ... /var/run/docker.sock` trong build | Chưa `usermod -aG docker jenkins` hoặc chưa restart Jenkins sau khi thêm nhóm |
| Build Maven bị kill / `exit code 137` | Hết RAM → thêm swap, giảm executor về 1, đặt `MAVEN_OPTS=-Xmx1g` |
| `docker push` báo `denied: requested access to the resource is denied` | Sai `<DOCKERHUB_USER>` trong tên image hoặc token chỉ có quyền Read |
| `toomanyrequests` khi build/pull | Giới hạn pull của Docker Hub → luôn `docker login` trước khi build (tính theo tài khoản, không theo IP) |
| Webhook GitHub báo `403`/`404` | Thiếu `/` cuối URL, Nginx chặn `/github-webhook/`, hoặc Jenkins URL cấu hình sai |
| `Host key verification failed` khi SSH | Chưa chạy `ssh-keyscan` vào `known_hosts` của user `jenkins` (mục 9) |
| Node báo lỗi `util.styleText` khi build frontend | Node < 20.19 → kiểm tra tool `node-22` |
| Giao diện Jenkins báo "reverse proxy setup is broken" | Thiếu header `X-Forwarded-*` trong Nginx hoặc Jenkins URL không khớp tên miền |
