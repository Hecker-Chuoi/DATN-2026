# 02 — Tạo server `datn-minikube`, cài Minikube, kubectl, Helm

> Thuộc [00 — Danh sách các bước](00_DANH_SACH_CAC_BUOC.md) (bước 0 và 1). Server Jenkins đã có theo [01 — Setup Jenkins](01_HUONG_DAN_SETUP_JENKINS.md); tài liệu này dựng **server thứ hai** chỉ để chạy ứng dụng.
>
> Kết quả: EC2 `datn-minikube` chuẩn bị **giống hệt cách làm ở 01** (timezone, swap, ufw, Docker), có cụm Minikube (driver docker) chạy dưới user `ubuntu`, addon `ingress` + `metrics-server`, tự khởi động lại sau reboot; `kubectl` và `helm` ở `/usr/local/bin` (user `deployer` dùng ở bước 6).
>
> **Cấu hình đang dùng: 2 vCPU / 7,6 GB RAM (`t3.large`)** — mức tối đa tài khoản được cấp. Đây là mức **sàn**: chạy được nhưng phải tiết kiệm. Tài liệu này làm phần tiết kiệm ở tầng máy + Minikube (mục 2.2, 6); phần tiết kiệm ở tầng ứng dụng (JVM, Kafka, Keycloak, cách deploy…) ghi ở [00 — Ngân sách tài nguyên](00_DANH_SACH_CAC_BUOC.md#ngân-sách-tài-nguyên-datn-minikube-2-vcpu--8-gb), làm ở các bước sau.
>
> Quy ước: lệnh chạy trên **`datn-minikube`**, bằng user **`ubuntu`** (user đăng nhập EC2), trừ khi ghi khác.
>
> ⚠️ Mọi lệnh là lệnh **Linux (bash)** — chạy trong phiên SSH (`ssh -i <key>.pem ubuntu@<Elastic IP>`), dấu nhắc dạng `ubuntu@datn-minikube:~$`. **Không** dán vào PowerShell trên Windows (dấu nhắc `PS D:\...>`): sẽ báo `not recognized`, `curl -fsSLO` lỗi tham số, `sudo is disabled`…

## Mục lục

1. [Tạo EC2 `datn-minikube`](#1-tạo-ec2-datn-minikube)
2. [Chuẩn bị server (giống 01 §2, §4)](#2-chuẩn-bị-server-giống-01-2-4)
3. [Cài kubectl](#3-cài-kubectl)
4. [Cài Helm](#4-cài-helm)
5. [Cài Minikube](#5-cài-minikube)
6. [Khởi tạo cụm](#6-khởi-tạo-cụm)
7. [Bật addon ingress, metrics-server](#7-bật-addon-ingress-metrics-server)
8. [Kiểm tra Ingress bằng app thử](#8-kiểm-tra-ingress-bằng-app-thử)
9. [Tự khởi động sau reboot (systemd)](#9-tự-khởi-động-sau-reboot-systemd)
10. [Những điều KHÔNG được làm trên server này](#10-những-điều-không-được-làm-trên-server-này)
11. [Xử lý sự cố](#11-xử-lý-sự-cố)
12. [Checklist hoàn thành](#12-checklist-hoàn-thành)

---

## 1. Tạo EC2 `datn-minikube`

### 1.1. Xem thông số máy Jenkins để làm cho đồng nhất

Trên **`datn-jenkins`**:

```bash
lsb_release -ds          # ví dụ Ubuntu 24.04.x LTS → server mới dùng CÙNG bản
dpkg --print-architecture   # amd64 → chọn instance Intel/AMD (t3, m7i), KHÔNG chọn Graviton (t4g, m7g)
hostname -I              # 172.31.45.0 ... → IP private của Jenkins (để đối chiếu)
```

Trên AWS Console → EC2 → chọn `datn-jenkins` → tab **Networking**: ghi lại **VPC ID**, **Subnet ID / Availability Zone**; tab **Security**: ghi lại **Security Group** (ví dụ `sg-0abc...`).

### 1.2. Launch instance

EC2 → **Launch instance**:

| Mục | Giá trị |
|---|---|
| Name | `datn-minikube` |
| AMI | **Ubuntu Server** cùng bản với `datn-jenkins` (mục 1.1), 64-bit (x86) |
| Instance type | `t3.large` (2 vCPU / 8 GB) — **mức tối thiểu, đang dùng**. Nếu sau này được cấp lớn hơn: `t3.xlarge` (4 vCPU / 16 GB) rồi đổi tham số theo bảng mục 6 |
| Key pair | Dùng lại key pair của `datn-jenkins` (cho tiện) |
| Network settings → Edit | **VPC**: cùng VPC với `datn-jenkins`; **Subnet**: cùng subnet (hoặc cùng AZ); **Auto-assign public IP**: Enable |
| Firewall (security groups) | **Create security group** tên `datn-minikube-sg` (rules ở mục 1.3) |
| Storage | **80 GiB gp3** |
| Advanced details → Credit specification | **Unlimited** (chỉ với dòng `t3`; build/khởi động Spring liên tục sẽ hết CPU credit) |

### 1.3. Security Group `datn-minikube-sg`

| Type | Port | Source | Để làm gì |
|---|---|---|---|
| SSH | 22 | **My IP** | Bạn SSH vào quản trị |
| SSH | 22 | **Security Group của `datn-jenkins`** (`sg-0abc...`, mục 1.1) | Jenkins SSH sang deploy (bước 6) — chọn theo SG thay vì IP để khỏi phải sửa khi IP đổi |
| HTTP | 80 | `0.0.0.0/0` | certbot + chuyển hướng HTTPS |
| HTTPS | 443 | `0.0.0.0/0` | Người dùng vào `app.<domain>`, `sso.<domain>` |

**Không** mở 8443 (API Kubernetes) hay 30000–32767 (NodePort): mọi truy cập từ ngoài đi qua Nginx 80/443.

### 1.4. Elastic IP

EC2 → **Elastic IPs → Allocate** → **Associate** vào `datn-minikube`. DNS `app.` / `sso.` (bước 5) trỏ vào IP này, nên stop/start máy không làm đổi IP.

Ghi lại 2 địa chỉ, dùng suốt các bước sau:

| | Lấy ở đâu | Dùng cho |
|---|---|---|
| **IP private** `172.31.x.x` (máy hiện tại: `172.31.34.47`, cũng thấy trong prompt `ubuntu@ip-172-31-34-47`) | EC2 → `datn-minikube` → Private IPv4 | Jenkins SSH sang (bước 6), `<MINIKUBE_HOST>` trong 01 §9 / §12 |
| **Elastic IP** | mục 1.4 | DNS `app.` / `sso.`, bạn SSH từ máy cá nhân |

### 1.5. Kiểm tra 2 máy thấy nhau

Từ **`datn-jenkins`** (chưa có key `deployer` nên chỉ kiểm tra cổng mở):

```bash
nc -zv <IP private datn-minikube> 22      # "succeeded" → mạng + Security Group đúng
```

Báo `timed out` → kiểm tra lại cùng VPC chưa và rule 22 từ SG `datn-jenkins` (mục 1.3).

---

## 2. Chuẩn bị server (giống 01 §2, §4)

SSH vào `datn-minikube` bằng Elastic IP: `ssh -i <key>.pem ubuntu@<Elastic IP>`.

### 2.1. Hệ điều hành, swap, tường lửa — y như [01 §2](01_HUONG_DAN_SETUP_JENKINS.md#2-chuẩn-bị-server)

```bash
sudo apt-get update && sudo apt-get -y upgrade
sudo apt-get install -y curl wget jq unzip git
sudo timedatectl set-timezone Asia/Ho_Chi_Minh
sudo hostnamectl set-hostname datn-minikube

# Swap 4 GB (cùng cách 01 §2)
sudo fallocate -l 4G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# Tường lửa: chỉ SSH + HTTP/HTTPS (cùng 01 §2)
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
```

Kiểm tra:

```bash
nproc && free -h && df -h /     # 2 CPU, ~7.6Gi RAM, Swap 4.0Gi, đĩa ~90G
sudo ufw status                  # PHẢI thấy đủ 3 dòng: OpenSSH, 80/tcp, 443/tcp  ALLOW
hostname                         # datn-minikube (đăng nhập lại mới thấy ở dấu nhắc lệnh)
```

> ⚠️ `ufw` đang `active` mà **thiếu dòng OpenSSH** thì lần SSH sau sẽ bị chặn. Kiểm tra trước khi đăng xuất; thiếu thì `sudo ufw allow OpenSSH`.

### 2.2. Tối ưu bộ nhớ ở tầng máy (vì chỉ có 8 GB)

Swap 4 GB chỉ là **lưới an toàn** (tránh máy treo / OOM khi có đột biến), không phải RAM thêm: pod Java mà chạy trên swap thì chậm hẳn và trượt health check. Cho kernel chỉ dùng swap khi thật cần:

```bash
echo 'vm.swappiness=10' | sudo tee /etc/sysctl.d/99-datn-memory.conf
sudo sysctl --system | grep swappiness   # vm.swappiness = 10
```

Tắt `snapd` (server này không cài gì qua snap; tiết kiệm vài chục MB và bớt tiến trình nền):

```bash
snap list 2>/dev/null                     # chỉ thấy core/snapd/amazon-ssm-agent... → an toàn để tắt
sudo systemctl disable --now snapd.service snapd.socket snapd.seeded.service 2>/dev/null || true
free -h                                   # so "used" trước / sau
```

> Nếu `snap list` có `amazon-ssm-agent` và bạn dùng **Session Manager** để vào máy thì **bỏ qua** bước tắt snapd. Chỉ SSH bằng key thì tắt được.

### 2.3. Docker — y như [01 §4](01_HUONG_DAN_SETUP_JENKINS.md#4-cài-docker-cho-jenkins)

```bash
curl -fsSL https://get.docker.com | sudo sh

# Cùng daemon.json với server Jenkins (giới hạn log để không đầy đĩa)
echo '{ "features": { "buildkit": true }, "log-driver": "json-file", "log-opts": { "max-size": "10m", "max-file": "3" } }' \
  | sudo tee /etc/docker/daemon.json
sudo systemctl restart docker
```

Khác 01: server này **không có Jenkins** → không có user `jenkins`. Người dùng Docker là `ubuntu` (vì Minikube driver docker **không chạy bằng root**):

```bash
sudo usermod -aG docker ubuntu
exit                                      # đăng xuất rồi SSH lại để nhận nhóm mới
```

SSH lại rồi kiểm tra:

```bash
id -nG                                    # có "docker"
docker run --rm hello-world               # chạy được, KHÔNG cần sudo
```

### 2.4. So sánh với server Jenkins

| | `datn-jenkins` (01) | `datn-minikube` (tài liệu này) |
|---|---|---|
| Cấu hình | 2 vCPU / 7,6 GB | 2 vCPU / 7,6 GB |
| Timezone, ufw, `daemon.json` | như 01 §2, §4 | **giống hệt** |
| Swap | 4 GB | 4 GB + `vm.swappiness=10` (mục 2.2) |
| Java, Jenkins, Maven, Node | có | **không** |
| Docker dùng bởi | `jenkins` (build image) | `ubuntu` (chạy Minikube) |
| kubectl, helm | **không cần** (deploy qua SSH) | có (mục 3–4) |
| Nginx | `jenkins.<domain>` | `app.<domain>`, `sso.<domain>` (bước 5) |
| Cron `docker image prune` (01 §13) | có | **không cần** (máy không build) |

---

## 3. Cài kubectl

Bản ổn định mới nhất, có kiểm tra checksum:

```bash
ARCH=$(dpkg --print-architecture)
KVER=$(curl -fsSL https://dl.k8s.io/release/stable.txt)      # ví dụ v1.3x.y
cd /tmp
curl -fsSLO "https://dl.k8s.io/release/${KVER}/bin/linux/${ARCH}/kubectl"
curl -fsSLO "https://dl.k8s.io/release/${KVER}/bin/linux/${ARCH}/kubectl.sha256"
echo "$(cat kubectl.sha256)  kubectl" | sha256sum --check      # phải in: kubectl: OK
sudo install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl
rm -f kubectl kubectl.sha256
kubectl version --client
```

> kubectl chỉ được lệch **±1 bản minor** so với Kubernetes trong Minikube. Sau mục 6 sẽ so lại.

---

## 4. Cài Helm

Helm 4 là bản hiện hành (Helm 3 đã hết hỗ trợ). Tải tarball chính thức, kiểm tra checksum:

```bash
ARCH=$(dpkg --print-architecture)
HELM_VERSION=$(curl -fsSL https://get.helm.sh/helm-latest-version)   # ví dụ v4.x.y
echo "$HELM_VERSION"                      # nếu lệnh trên lỗi: xem bản mới nhất ở https://github.com/helm/helm/releases rồi gán tay
cd /tmp
curl -fsSLO "https://get.helm.sh/helm-${HELM_VERSION}-linux-${ARCH}.tar.gz"
curl -fsSLO "https://get.helm.sh/helm-${HELM_VERSION}-linux-${ARCH}.tar.gz.sha256sum"
sha256sum --check "helm-${HELM_VERSION}-linux-${ARCH}.tar.gz.sha256sum"   # ...: OK
tar -xzf "helm-${HELM_VERSION}-linux-${ARCH}.tar.gz"
sudo install -o root -g root -m 0755 "linux-${ARCH}/helm" /usr/local/bin/helm
rm -rf "linux-${ARCH}" helm-*.tar.gz*
helm version
```

> **Helm 4 vs Helm 3** khi viết `deploy.sh` (bước 6–7): Helm 4 dùng `--rollback-on-failure`, Helm 3 dùng `--atomic`. Ghi lại bản đã cài để dùng cờ đúng.

---

## 5. Cài Minikube

```bash
ARCH=$(dpkg --print-architecture)
cd /tmp
curl -fsSLO "https://github.com/kubernetes/minikube/releases/latest/download/minikube-linux-${ARCH}"
curl -fsSLO "https://github.com/kubernetes/minikube/releases/latest/download/minikube-linux-${ARCH}.sha256"
echo "$(cat minikube-linux-${ARCH}.sha256)  minikube-linux-${ARCH}" | sha256sum --check   # ...: OK
sudo install -o root -g root -m 0755 "minikube-linux-${ARCH}" /usr/local/bin/minikube
rm -f minikube-linux-*
minikube version
```

Tắt thông báo cập nhật (để log systemd ở mục 9 gọn):

```bash
minikube config set WantUpdateNotification false
```

---

## 6. Khởi tạo cụm

Server này **chỉ** chạy Minikube nên cấp phần lớn tài nguyên cho cụm:

| Instance | Lệnh |
|---|---|
| **2 vCPU / 8 GB (`t3.large`) — đang dùng** | `minikube start --driver=docker --cpus=2 --memory=6g --disk-size=50g` |
| 4 vCPU / 16 GB (`t3.xlarge`) | `minikube start --driver=docker --cpus=4 --memory=12g --disk-size=50g` (khớp [02 nghiên cứu §4.3](../02_NGHIEN_CUU_CICD_TREN_SOURCE_CODE.md#43-minikube-trên-server)) |

Vì sao `--memory=6g` với máy 7,6 GB:
- ~1,6 GB còn lại cho Ubuntu + Docker + Nginx (đã gọn ở mục 2.2). Cấp hơn 6 GB thì máy host thiếu RAM, chính container `minikube` dễ bị kernel OOM-kill → **sập cả cụm**.
- 6 GB **chỉ đủ** nếu ứng dụng được tinh chỉnh theo [00 — Ngân sách tài nguyên](00_DANH_SACH_CAC_BUOC.md#ngân-sách-tài-nguyên-datn-minikube-2-vcpu--8-gb) (mục tiêu ~5 GB cho pod). Để mặc định (JVM không giới hạn, 2 Postgres, rolling update song song) sẽ cần ~8–9 GB → OOM.
- `--cpus=2` = toàn bộ CPU của máy (đây là giới hạn trên, không giữ chỗ). Không có CPU dư nên Spring Boot khởi động chậm (3–5 phút cho cả 5 service) — xử lý bằng `startupProbe` dài ở bước 3.

```bash
# t3.large — chạy bằng user ubuntu, KHÔNG sudo
minikube start --driver=docker --cpus=2 --memory=6g --disk-size=50g
```

Nâng instance sau này: `sudo systemctl stop minikube` → `minikube config set memory 12g && minikube config set cpus 4` → `minikube delete && minikube start ...` (đổi RAM/CPU của cụm đã tạo **cần tạo lại cụm** → mất dữ liệu cụm, nhớ `pg_dump` trước).

Lần đầu mất vài phút (tải image `kicbase` + Kubernetes). Minikube **lưu lại** tham số này vào profile, nên các lần `minikube start` sau (kể cả từ systemd ở mục 9) không cần gõ lại.

Kiểm tra:

```bash
minikube status                           # host / kubelet / apiserver: Running
kubectl get nodes -o wide                 # minikube   Ready
minikube ip                               # 192.168.49.2 — ghi lại, Nginx ở bước 5 sẽ trỏ vào đây
kubectl version                           # so Client vs Server: lệch tối đa 1 minor
docker ps --filter name=minikube          # 1 container "minikube"
docker stats --no-stream minikube         # MEM LIMIT ≈ 6GiB; MEM USAGE lúc cụm rỗng ~1–1,5 GiB
free -h                                   # host còn "available" >= 1 GiB
```

Ghi lại `MEM USAGE` lúc cụm rỗng: đó là phần lõi Kubernetes chiếm sẵn, phần còn lại trong 6 GB mới là chỗ cho ứng dụng.

> `192.168.49.2` là IP **bên trong** server (mạng Docker), chỉ server này truy cập được — **khác** IP private `172.31.x.x` (mục 1.4) mà Jenkins dùng để SSH tới.

Các file sinh ra (đều thuộc user `ubuntu`):

| Đường dẫn | Nội dung |
|---|---|
| `~/.minikube/` | Chứng chỉ CA, cấu hình profile |
| `~/.kube/config` | kubeconfig **admin** của cụm — chỉ `ubuntu` dùng, **không** copy cho `deployer` (bước 6 tạo kubeconfig giới hạn quyền riêng) |
| Docker volume `minikube` | Toàn bộ dữ liệu bên trong cụm (image, PVC của Postgres, Keycloak…) |

---

## 7. Bật addon ingress, metrics-server

```bash
minikube addons enable ingress
minikube addons enable metrics-server

kubectl -n ingress-nginx get pods         # ingress-nginx-controller-...   1/1 Running (đợi 1–2 phút)
kubectl get ingressclass                  # nginx
kubectl top nodes                         # có số CPU/RAM (metrics-server cần ~1 phút sau khi bật)
minikube addons list | grep -E 'ingress |metrics-server'   # enabled
```

`metrics-server` tốn ~50 MB nhưng **giữ lại**: với 8 GB, `kubectl top pods` là công cụ chính để đo và chỉnh `limits` ở bước 5 (xem 00 — R7). Không bật thêm addon nào khác (`dashboard`, `registry`…) để tiết kiệm RAM.

> Addon `ingress` dùng dự án **ingress-nginx** của Kubernetes; dự án này đã ngừng phát triển (chỉ bảo trì tối thiểu). Với môi trường đồ án trên Minikube vẫn dùng được; nếu báo cáo có phần "hướng phát triển" có thể nhắc tới việc chuyển sang Gateway API.

---

## 8. Kiểm tra Ingress bằng app thử

Mục đích: chứng minh đường **server → 192.168.49.2:80 → Ingress → Service → Pod** đã thông, trước khi có app thật. Nginx ở bước 5 sẽ đi đúng đường này.

```bash
kubectl create namespace smoke
kubectl -n smoke create deployment hello --image=nginx:alpine
kubectl -n smoke expose deployment hello --port=80
kubectl -n smoke create ingress hello --class=nginx --rule="hello.local/*=hello:80"
kubectl -n smoke rollout status deployment hello

curl -s -H "Host: hello.local" http://$(minikube ip)/ | grep -i "<title>"
# <title>Welcome to nginx!</title>   → Ingress hoạt động

kubectl delete namespace smoke            # dọn
```

(Nếu `curl` trả `404 Not Found` của nginx: Ingress chạy nhưng chưa khớp rule — đợi thêm 30 giây rồi thử lại; `kubectl -n smoke describe ingress hello` xem `Address` đã có chưa.)

---

## 9. Tự khởi động sau reboot (systemd)

Mặc định sau khi server reboot (hoặc stop/start EC2), container `minikube` **dừng** và không tự chạy lại → toàn bộ app chết. Tạo dịch vụ systemd chạy `minikube start` bằng user `ubuntu` mỗi khi máy lên (cùng cách 01 §3 dùng systemd cho Jenkins).

```bash
sudo tee /etc/systemd/system/minikube.service > /dev/null <<'EOF'
[Unit]
Description=Minikube (cluster datn) - chay bang user ubuntu
After=docker.service network-online.target
Requires=docker.service
Wants=network-online.target

[Service]
Type=oneshot
RemainAfterExit=yes
User=ubuntu
Group=ubuntu
Environment=HOME=/home/ubuntu
Environment=MINIKUBE_IN_STYLE=false
ExecStart=/usr/local/bin/minikube start
ExecStop=/usr/local/bin/minikube stop
TimeoutStartSec=600
TimeoutStopSec=180

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable minikube
sudo systemctl start minikube             # cụm đang chạy sẵn → lệnh trả về nhanh
systemctl status minikube --no-pager      # active (exited) — bình thường với Type=oneshot
```

Giải thích:
- `minikube start` **không tham số** → dùng lại cấu hình đã lưu ở mục 6 (CPU, RAM, đĩa, addon).
- `Type=oneshot` + `RemainAfterExit=yes`: `minikube start` chạy xong là thoát, systemd vẫn coi dịch vụ đang "bật", nên `systemctl stop minikube` (và lúc tắt máy) sẽ gọi `minikube stop` cho cụm dừng gọn.
- `User=ubuntu`: systemd tự nạp nhóm phụ của `ubuntu` (có `docker`, mục 2.3).

**Kiểm tra thật bằng reboot:**

```bash
sudo reboot
# ... đợi 2–3 phút, SSH lại
journalctl -u minikube -f                 # (tuỳ chọn) xem trực tiếp, đợi dòng "Done! kubectl is now configured..." rồi Ctrl+C
systemctl status minikube --no-pager      # active (exited) — nếu còn "activating (start)" là CHƯA xong, đợi thêm
journalctl -u minikube -b --no-pager | tail -20
kubectl get nodes                         # Ready
kubectl -n ingress-nginx get pods         # Running
```

Thao tác tay về sau: dùng `sudo systemctl stop|start|restart minikube` thay vì gọi `minikube stop/start` trực tiếp, để systemd biết đúng trạng thái.

---

## 10. Những điều KHÔNG được làm trên server này

Toàn bộ dữ liệu cụm (kể cả DB Postgres ở bước 4) nằm trong Docker volume `minikube`.

| Không làm | Vì sao |
|---|---|
| `docker system prune`, `docker container prune`, `docker volume prune`, `docker rm -f $(docker ps -aq)` | Nếu lúc đó Minikube đang **dừng**, container `minikube` bị xoá → volume `minikube` thành "không dùng" → bị xoá luôn = **mất toàn bộ cụm và dữ liệu** |
| Chép cron dọn Docker của 01 §13 sang máy này | Không cần (máy không build image); image app nằm ở Docker **bên trong** container `minikube` |
| `minikube delete` | Xoá cụm + volume. Chỉ dùng khi chủ động làm lại từ đầu |
| `sudo minikube ...` / chạy `minikube` bằng user khác (`deployer`…) | Tạo ra cụm/profile thứ hai, lệch với cụm của `ubuntu` |
| Copy `~/.kube/config` của `ubuntu` cho `deployer` | Đó là quyền admin cả cụm; `deployer` dùng kubeconfig giới hạn của bước 6 |
| Thêm `deployer` vào nhóm `docker` / `sudo` | `docker` ≈ root trên máy → mất ý nghĩa của user deploy riêng |
| Mở cổng 8443 hoặc 30000–32767 trên ufw / Security Group | Không cần; ra ngoài chỉ qua Nginx 80/443 |

---

## 11. Xử lý sự cố

| Triệu chứng | Nguyên nhân / cách xử lý |
|---|---|
| Từ `datn-jenkins`, `nc -zv <IP private> 22` bị `timed out` | Khác VPC, hoặc SG `datn-minikube-sg` chưa cho 22 từ SG của `datn-jenkins` (mục 1.3) |
| `The "docker" driver should not be used with root privileges` | Đang chạy bằng root/sudo → chạy bằng `ubuntu` |
| `permission denied ... /var/run/docker.sock` | `ubuntu` chưa vào nhóm `docker` hoặc chưa đăng nhập lại (mục 2.3) |
| `Requested memory allocation (...) is more than your system limit` | `--memory` lớn hơn RAM máy → giảm theo bảng mục 6 |
| Cả cụm tự tắt, `dmesg \| grep -i oom` thấy tiến trình bị kill | Host hết RAM → **không** tăng `--memory`; giảm tải ứng dụng theo 00 — Ngân sách tài nguyên |
| Pod `Pending`, `describe` báo `Insufficient memory` | Tổng `requests.memory` của các pod vượt phần cụm có → hạ `requests` (00 — R3) |
| Pod `OOMKilled` / `RESTARTS` tăng dần | Pod vượt `limits.memory` → xem `kubectl top pods`, chỉnh theo 00 — R7 |
| `minikube start` treo ở "Pulling base image" | Mạng ra ngoài chậm / bị chặn; kiểm tra Security Group **outbound** (mặc định cho phép tất cả); thử lại |
| Ngay sau reboot `kubectl` báo `localhost:8080 was refused` | Bình thường khi `minikube.service` còn `activating`: `minikube stop` lúc tắt máy đã gỡ context kubectl, `minikube start` chạy xong mới ghi lại → đợi 2–3 phút |
| Sau reboot `kubectl` báo `connection refused` (đã quá 5 phút) | `journalctl -u minikube -b` xem lỗi; Docker chưa lên kịp → `sudo systemctl restart minikube` |
| `kubectl top` báo `Metrics API not available` | metrics-server chưa sẵn sàng, đợi 1–2 phút |
| Pod lỗi `too many open files` | Tăng inotify: `echo -e "fs.inotify.max_user_watches=524288\nfs.inotify.max_user_instances=512" \| sudo tee /etc/sysctl.d/99-minikube.conf && sudo sysctl --system` |
| Cụm hỏng nặng, muốn làm lại | `sudo systemctl stop minikube && minikube delete` → làm lại mục 6–8 → `sudo systemctl start minikube` (**mất dữ liệu cụm**) |

---

## 12. Checklist hoàn thành

- [ ] EC2 `datn-minikube`: cùng bản Ubuntu, cùng VPC với `datn-jenkins`, 2 vCPU / 8 GB (`t3.large`, credit Unlimited), ~96 GB, Elastic IP
- [ ] Đã ghi lại **IP private** (`172.31.34.47`) và **Elastic IP**
- [ ] Từ `datn-jenkins`: `nc -zv 172.31.34.47 22` → succeeded
- [ ] Timezone, hostname `datn-minikube`, swap 4 GB, ufw (OpenSSH/80/443), `daemon.json` giống 01
- [ ] `vm.swappiness=10`; đã tắt `snapd` (nếu không dùng Session Manager)
- [ ] `ubuntu` chạy `docker run --rm hello-world` không cần sudo
- [ ] `kubectl version --client`, `helm version`, `minikube version` chạy được
- [ ] `minikube start --cpus=2 --memory=6g`; `kubectl get nodes` → `minikube   Ready`; `minikube ip` = `192.168.49.2` (đã ghi lại)
- [ ] Đã ghi lại `MEM USAGE` của container `minikube` lúc cụm rỗng (`docker stats --no-stream minikube`)
- [ ] `kubectl -n ingress-nginx get pods` Running; `kubectl top nodes` có số
- [ ] App thử `hello.local` trả về trang nginx qua Ingress (đã xoá namespace `smoke`)
- [ ] `systemctl is-enabled minikube` → `enabled`; **reboot thử** xong cụm tự lên

Xong → sang **bước 2** trong [00](00_DANH_SACH_CAC_BUOC.md): namespace `datn` + Secret.
