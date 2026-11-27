# 02 — Dựng server ứng dụng `datn-app` (Docker + Nginx + HTTPS)

> Thuộc [00 — Danh sách các bước](00_DANH_SACH_CAC_BUOC.md) (**bước 0–1**). Đã có tài khoản Docker Hub / GitHub theo [01](01_CHUAN_BI_TAI_KHOAN.md); tài liệu này dựng **một server** chạy toàn bộ ứng dụng bằng `docker compose` (không còn server Jenkins).
>
> Kết quả: EC2 `datn-app` có Docker + Compose, Nginx + chứng chỉ HTTPS cho `app.<domain>` và `sso.<domain>`, sẵn sàng nhận `docker compose up` ở bước sau. Chưa cần repo trên server.
>
> **Cấu hình: 2 vCPU / ~7,6 GB RAM (`t3.large`)** — mức tối đa tài khoản được cấp. Không còn Minikube nên máy dư ~1,5 GB so với bản cũ.
>
> Quy ước: 🪟 = PowerShell trên **máy Windows** của bạn (dấu nhắc `PS D:\...>`); 🐧 = phiên **SSH vào `datn-app`** (dấu nhắc `ubuntu@datn-app:~$`). **Đừng dán lệnh Linux vào PowerShell và ngược lại.**

## Mục lục

1. [Tạo EC2 `datn-app`](#1-tạo-ec2-datn-app)
2. [Chuẩn bị hệ điều hành](#2-chuẩn-bị-hệ-điều-hành)
3. [Cài Docker Engine + Compose](#3-cài-docker-engine--compose)
4. [Cài Nginx, certbot, jq](#4-cài-nginx-certbot-jq)
5. [DNS](#5-dns)
6. [Nginx + HTTPS](#6-nginx--https)
7. [Những điều KHÔNG được làm trên server này](#7-những-điều-không-được-làm-trên-server-này)
8. [Xử lý sự cố](#8-xử-lý-sự-cố)
9. [Checklist](#9-checklist)

---

## 1. Tạo EC2 `datn-app`

### 1.1. Chọn hệ điều hành và kiến trúc

Ubuntu Server 24.04 LTS, **64-bit x86** (chọn dòng Intel/AMD như `t3`, **không** chọn Graviton `t4g`: image Docker Hub của dự án build cho amd64).

### 1.2. Launch instance

EC2 → **Launch instance**:

| Mục | Giá trị |
|---|---|
| Name | `datn-app` |
| AMI | **Ubuntu Server 24.04 LTS**, 64-bit (x86) |
| Instance type | `t3.large` (2 vCPU / 8 GB) |
| Key pair | Tạo mới hoặc dùng key pair có sẵn (file `.pem` để SSH quản trị) |
| Network settings → Edit | VPC / Subnet mặc định; **Auto-assign public IP**: Enable |
| Firewall | **Create security group** `datn-app-sg` (rules ở 1.3) |
| Storage | **50 GiB gp3** (image Docker + dữ liệu Postgres / Kafka / MinIO; không cần 80 GiB như Minikube) |
| Advanced details → Credit specification | **Unlimited** (dòng `t3`: Spring khởi động liên tục sẽ hết CPU credit) |

### 1.3. Security Group `datn-app-sg`

| Type | Port | Source | Để làm gì |
|---|---|---|---|
| SSH | 22 | **My IP** | Bạn SSH vào quản trị |
| SSH | 22 | `0.0.0.0/0` | GitHub Actions SSH vào deploy ([06 §4](06_GITHUB_ACTIONS_CICD.md#4-mở-cổng-22-trên-security-group)): IP runner thay đổi nên không khoanh vùng được; làm **sau** khi ứng dụng đã chạy, ban đầu chỉ cần `My IP` |
| HTTP | 80 | `0.0.0.0/0` | certbot + chuyển hướng HTTPS |
| HTTPS | 443 | `0.0.0.0/0` | Người dùng vào `app.<domain>`, `sso.<domain>` |

**Không** mở 3000, 8080, 8025, 5432, 9092…: các cổng đó chỉ nghe `127.0.0.1` (xem compose), mọi truy cập từ ngoài đi qua Nginx 80/443.

### 1.4. Elastic IP và ghi lại 2 địa chỉ

EC2 → **Elastic IPs → Allocate** → **Associate** vào `datn-app`. DNS `app.` / `sso.` trỏ vào IP này nên stop/start máy không làm đổi.

| | Lấy ở đâu | Dùng cho |
|---|---|---|
| **Elastic IP** (public) | mục này | DNS `app.` / `sso.`, bạn SSH từ máy cá nhân, secret `DEPLOY_HOST` của Actions |

> ⚠️ Dùng `sslip.io` mà **chưa gắn Elastic IP** thì IP public đổi mỗi lần stop/start máy: tên miền, chứng chỉ, `public.env` và image frontend (nhúng URL Keycloak) đều hỏng theo. Gắn Elastic IP **trước** khi đặt tên miền ở mục 5.

### 1.5. Kiểm tra SSH từ máy bạn

🪟 Git Bash: `ssh -i <KEY>.pem ubuntu@<ELASTIC_IP> hostname`. `timed out` → kiểm tra rule 22 `My IP` ở Security Group (IP nhà mạng của bạn có thể đã đổi).

---

## 2. Chuẩn bị hệ điều hành

🪟 SSH vào `datn-app` bằng Elastic IP (PowerShell): `ssh -i <KEY>.pem ubuntu@<ELASTIC_IP>`. Từ đây là 🐧 trừ khi ghi khác.

```bash
sudo apt-get update && sudo apt-get -y upgrade
sudo apt-get install -y curl wget unzip git
sudo timedatectl set-timezone Asia/Ho_Chi_Minh
sudo hostnamectl set-hostname datn-app

# Swap 4 GB: lưới an toàn khi có đột biến RAM (không phải RAM thêm)
sudo fallocate -l 4G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
echo 'vm.swappiness=10' | sudo tee /etc/sysctl.d/99-datn-memory.conf && sudo sysctl --system | grep swappiness

# Tường lửa: chỉ SSH + HTTP/HTTPS
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
```

Kiểm tra:

```bash
nproc && free -h && df -h /     # 2 CPU, ~7.6Gi RAM, Swap 4.0Gi
sudo ufw status                  # PHẢI có đủ OpenSSH, 80/tcp, 443/tcp ALLOW
```

> ⚠️ `ufw` đang `active` mà **thiếu dòng OpenSSH** thì lần SSH sau bị chặn. Kiểm tra trước khi đăng xuất.

Tắt `snapd` (server này không dùng snap, tiết kiệm RAM):

```bash
sudo systemctl disable --now snapd.service snapd.socket snapd.seeded.service 2>/dev/null || true
```

Đăng xuất rồi SSH lại để dấu nhắc hiện `ubuntu@datn-app`.

---

## 3. Cài Docker Engine + Compose

Cài từ kho chính thức của Docker (bản `docker compose` v2 là plugin đi kèm, cần để dùng `up --wait`):

```bash
sudo apt-get install -y ca-certificates
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

Cho user `ubuntu` dùng `docker` không cần `sudo`, và **giới hạn dung lượng log container** (mặc định log không bị cắt, đầy đĩa sau vài tuần):

```bash
sudo usermod -aG docker ubuntu
sudo tee /etc/docker/daemon.json > /dev/null <<'EOF'
{
  "log-driver": "json-file",
  "log-opts": { "max-size": "10m", "max-file": "3" }
}
EOF
sudo systemctl restart docker
```

Đăng xuất, SSH lại (để nhóm `docker` có hiệu lực) rồi kiểm tra:

```bash
docker run --rm hello-world           # "Hello from Docker!"
docker compose version                # v2.x (cần ≥ 2.20)
docker info --format '{{.LoggingDriver}}'   # json-file
```

> `daemon.json` chỉ áp dụng cho container **tạo sau** khi restart Docker. Làm bước này trước khi chạy ứng dụng.

---

## 4. Cài Nginx, certbot, jq

```bash
sudo apt-get install -y nginx certbot python3-certbot-nginx jq
sudo systemctl enable --now nginx
systemctl is-enabled nginx             # enabled (reboot xong tự lên)
```

`jq` cần cho `make-realm.sh` (deploy lần đầu sinh `realm.json` cho Keycloak).

---

## 5. DNS

Cần hai tên miền trỏ về **Elastic IP** của `datn-app`: `app.<domain>` và `sso.<domain>`. Chưa có tên miền thì dùng `sslip.io` (tự trỏ, không cần cấu hình): với Elastic IP `13.250.1.2` thì tên miền gốc là `13-250-1-2.sslip.io` → `app.13-250-1-2.sslip.io`, `sso.13-250-1-2.sslip.io`.

🪟 PowerShell:

```powershell
nslookup app.<DOMAIN>
nslookup sso.<DOMAIN>
# Cả hai phải trả về đúng Elastic IP của datn-app
```

Sau đó đặt tên miền vào repo (🪟 Git Bash ở gốc repo `D:\DATN-2026`) rồi **commit**:

```bash
bash infrastructure/prod/scripts/set-public-host.sh <DOMAIN> https <DOCKERHUB_USER>
git add infrastructure/prod/public.env && git commit -m "chore(deploy): đặt tên miền và registry"
```

> Giá trị này gắn vào `iss` của token Keycloak và bundle frontend: **chọn tên miền và `https` ngay từ đầu**, đổi sau nghĩa là build lại image, xoá realm và mọi người đăng nhập lại.

---

## 6. Nginx + HTTPS

Mô hình: trình duyệt → **Nginx của server** (80/443, giữ chứng chỉ) → container `frontend` (`127.0.0.1:3000`, vừa phục vụ giao diện vừa proxy API vào gateway) hoặc container `keycloak` (`127.0.0.1:8080`).

🪟 Chép file cấu hình mẫu từ repo lên server (PowerShell, gốc repo):

```powershell
scp -i <KEY>.pem infrastructure\prod\nginx\datn-app.conf ubuntu@<ELASTIC_IP>:/tmp/datn-app.conf
```

🐧 Trên server — thay tên miền, bật site, xin chứng chỉ:

```bash
APP_HOST=app.<DOMAIN>
SSO_HOST=sso.<DOMAIN>

sed -e "s/__APP_HOST__/$APP_HOST/" -e "s/__SSO_HOST__/$SSO_HOST/" /tmp/datn-app.conf | sudo tee /etc/nginx/sites-available/datn-app > /dev/null
sudo ln -sf /etc/nginx/sites-available/datn-app /etc/nginx/sites-enabled/datn-app
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx      # "syntax is ok" + "test is successful"

sudo certbot --nginx -d "$APP_HOST" -d "$SSO_HOST" --redirect
```

Không tương tác (đã dùng khi dựng thật; `--register-unsafely-without-email` = không gửi email của bạn cho Let's Encrypt, đổi lại không nhận cảnh báo hết hạn): `sudo certbot --nginx -d "$APP_HOST" -d "$SSO_HOST" --redirect --non-interactive --agree-tos --register-unsafely-without-email`. Lần đầu certbot hỏi email và đồng ý điều khoản; certbot tự thêm khối 443, chứng chỉ và chuyển hướng 80 → 443. Kiểm tra:

```bash
sudo certbot renew --dry-run                       # "all simulated renewals succeeded" (có thể mất vài phút)
systemctl list-timers | grep -i certbot            # có timer tự gia hạn
curl -sI "https://$APP_HOST/" | head -1            # HTTP/2 502 là ĐÚNG lúc này (chưa có ứng dụng sau Nginx)
```

Lỗi thường gặp: `Timeout during connect` / `Could not resolve` (cổng 80 chưa mở ở Security Group hoặc ufw, DNS chưa đúng, hoặc đang dùng tên mẫu `example.invalid`); sslip.io bị giới hạn số chứng chỉ trên mỗi tên miền gốc (dùng IP khác hoặc tên miền thật).

---

## 7. Những điều KHÔNG được làm trên server này

- **`docker compose down -v`** hoặc `docker volume prune` / `docker system prune --volumes`: xoá sạch Postgres, Kafka, MinIO (dữ liệu nằm trong volume `datn_postgres_data`, `datn_kafka_data`, `datn_minio_data`). Dọn image thì chỉ dùng `docker image prune` (deploy.sh đã tự làm).
- Mở thêm cổng 5432 / 8080 / 8025 ra Security Group: Postgres không có mật khẩu mạnh nào cứu được khi lộ ra Internet; hộp thư Mailpit đọc được là chiếm được tài khoản (link đặt lại mật khẩu).
- Sửa file trong `~/datn/releases/*` bằng tay: lần deploy sau ghi đè.

---

## 8. Xử lý sự cố

| Triệu chứng | Cách xử lý |
|---|---|
| `permission denied ... docker.sock` | Chưa đăng xuất / đăng nhập lại sau `usermod -aG docker` |
| certbot `Timeout during connect` | Cổng 80 chưa mở (Security Group / ufw) hoặc DNS chưa trỏ đúng |
| `nginx -t` báo `conflicting server name` | Còn file site cũ trong `/etc/nginx/sites-enabled/` (`ls` rồi xoá) |
| Máy chậm, `free -h` cho `available` < 500 Mi | Xem 05 §6 (đo RAM); đừng chạy thêm gì ngoài ứng dụng trên máy này |
| Đĩa đầy | `docker system df`; log container đã giới hạn ở mục 3, image cũ dọn bằng `docker image prune -af --filter "until=72h"` (không dùng `--volumes`) |

---

## 9. Checklist

- [ ] SSH được vào `datn-app` bằng file `.pem`
- [ ] `docker run --rm hello-world` chạy bằng `ubuntu`; `docker info` báo `json-file`; `docker compose version` ≥ 2.20
- [ ] `nslookup app.<domain>` và `sso.<domain>` ra Elastic IP
- [ ] `sudo certbot renew --dry-run` thành công; `curl -sI https://app.<domain>/` có phản hồi (502 là đúng)
- [ ] `infrastructure/prod/public.env` đã đặt tên miền + Docker Hub user thật và **đã commit**

Xong → [03 — Secret và cấu hình](03_SECRET_VA_CAU_HINH.md).
