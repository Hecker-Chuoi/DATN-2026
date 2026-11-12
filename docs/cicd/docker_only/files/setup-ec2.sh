#!/usr/bin/env bash
# =============================================================================
# setup-ec2.sh – HƯỚNG 1: chuẩn bị EC2 Ubuntu 24.04 (chạy MỘT lần)
#   scp setup-ec2.sh ubuntu@<EIP>:~ && ssh ubuntu@<EIP> 'sudo bash setup-ec2.sh'
# =============================================================================
set -euo pipefail

echo "==> 1. Cài Docker Engine + Compose plugin (repo chính thức của Docker)"
apt-get update
apt-get install -y ca-certificates curl gnupg git
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  > /etc/apt/sources.list.d/docker.list
apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
usermod -aG docker ubuntu   # user ubuntu chạy docker không cần sudo (đăng nhập lại để có hiệu lực)

echo "==> 2. Giới hạn log mặc định của Docker (tránh đầy ổ đĩa)"
cat > /etc/docker/daemon.json <<'EOF'
{
  "log-driver": "json-file",
  "log-opts": { "max-size": "20m", "max-file": "3" }
}
EOF
systemctl restart docker

echo "==> 3. Tạo swap 4GB (đệm khi Maven/npm/docker build ăn RAM đột biến)"
if ! swapon --show | grep -q /swapfile; then
  fallocate -l 4G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi
echo 'vm.swappiness=10' > /etc/sysctl.d/99-datn.conf
sysctl --system >/dev/null

echo "==> 4. Thư mục làm việc"
#   platform : jenkins.Dockerfile, docker-compose.platform.yml, Caddyfile, .env (bạn copy vào)
#   deploy   : Jenkins chép docker-compose.prod.yml + realm.json vào mỗi lần deploy
#   state    : deployed.env (tag đang chạy) + history/ (để rollback)
#   backups  : file pg_dump hằng ngày
mkdir -p /opt/datn/{platform,deploy/keycloak,state/history,backups}
chown -R 1000:1000 /opt/datn/deploy /opt/datn/state   # uid 1000 = user jenkins trong container

echo "==> 5. Network dùng chung giữa compose platform (Jenkins, Caddy) và compose ứng dụng"
docker network inspect datn-net >/dev/null 2>&1 || docker network create datn-net

echo "==> 6. Tạo file .env cho docker-compose.platform.yml"
DOCKER_GID=$(getent group docker | cut -d: -f3)
if [ ! -f /opt/datn/platform/.env ]; then
  cat > /opt/datn/platform/.env <<EOF
DOCKER_GID=${DOCKER_GID}
ACME_EMAIL=ban@example.com
APP_DOMAIN=datn.example.com
AUTH_DOMAIN=auth.datn.example.com
JENKINS_DOMAIN=jenkins.datn.example.com
EOF
fi

echo
echo "XONG. Bước tiếp theo:"
echo "  1) Sửa /opt/datn/platform/.env (email + 3 tên miền)"
echo "  2) Copy jenkins.Dockerfile, docker-compose.platform.yml, Caddyfile vào /opt/datn/platform"
echo "  3) cd /opt/datn/platform && docker compose up -d --build"
echo "  4) Lấy mật khẩu mở khoá Jenkins: docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword"
