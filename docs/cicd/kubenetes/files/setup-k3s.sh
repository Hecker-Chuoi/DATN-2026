#!/usr/bin/env bash
# =============================================================================
# setup-k3s.sh – HƯỚNG 2: chuẩn bị EC2 Ubuntu 24.04 cho Docker + k3s (chạy MỘT lần)
#   scp setup-k3s.sh ubuntu@<EIP>:~ && ssh ubuntu@<EIP> 'sudo bash setup-k3s.sh'
#
# Docker vẫn cần: chạy Jenkins + registry và để Jenkins build image.
# k3s (Kubernetes rút gọn, 1 file binary) chạy ứng dụng; có sẵn Traefik (Ingress),
# local-path (ổ đĩa cho PVC), CoreDNS, metrics-server.
# =============================================================================
set -euo pipefail

CERT_MANAGER_VERSION="${CERT_MANAGER_VERSION:-v1.16.2}"   # xem bản mới: github.com/cert-manager/cert-manager/releases

echo "==> 1. Cài Docker Engine + Compose plugin (+ jq cho registry-cleanup.sh)"
apt-get update
apt-get install -y ca-certificates curl gnupg git jq
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  > /etc/apt/sources.list.d/docker.list
apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
usermod -aG docker ubuntu
cat > /etc/docker/daemon.json <<'EOF'
{ "log-driver": "json-file", "log-opts": { "max-size": "20m", "max-file": "3" } }
EOF
systemctl restart docker

echo "==> 2. Swap 4GB (k3s mặc định chạy được khi bật swap)"
if ! swapon --show | grep -q /swapfile; then
  fallocate -l 4G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi
echo 'vm.swappiness=10' > /etc/sysctl.d/99-datn.conf
sysctl --system >/dev/null

echo "==> 3. Cho k3s biết registry nội bộ localhost:5000 (http, không TLS)"
mkdir -p /etc/rancher/k3s
cat > /etc/rancher/k3s/registries.yaml <<'EOF'
mirrors:
  "localhost:5000":
    endpoint:
      - "http://localhost:5000"
EOF

echo "==> 4. Cài k3s (Traefik + local-path + CoreDNS + metrics-server đi kèm)"
curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC="server --write-kubeconfig-mode=644" sh -
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
until kubectl get nodes 2>/dev/null | grep -q ' Ready'; do echo "  ...chờ node Ready"; sleep 5; done
kubectl get nodes -o wide

echo "==> 5. Cài cert-manager ${CERT_MANAGER_VERSION} (tự xin chứng chỉ Let's Encrypt cho Ingress)"
kubectl apply -f "https://github.com/cert-manager/cert-manager/releases/download/${CERT_MANAGER_VERSION}/cert-manager.yaml"
kubectl -n cert-manager rollout status deploy/cert-manager-webhook --timeout=300s

echo "==> 6. Namespace ứng dụng"
kubectl create namespace datn --dry-run=client -o yaml | kubectl apply -f -

echo "==> 7. Thư mục làm việc + kubeconfig cho Jenkins"
mkdir -p /opt/datn/{platform,state/history,backups}
chown -R 1000:1000 /opt/datn/state
PRIVATE_IP=$(hostname -I | awk '{print $1}')
# Jenkins chạy trong container: 127.0.0.1 là chính container đó → đổi sang private IP của EC2
# (chứng chỉ API server của k3s đã có sẵn IP này trong SAN).
sed "s#https://127.0.0.1:6443#https://${PRIVATE_IP}:6443#" /etc/rancher/k3s/k3s.yaml > /opt/datn/kubeconfig-jenkins.yaml
chmod 600 /opt/datn/kubeconfig-jenkins.yaml
chown ubuntu:ubuntu /opt/datn/kubeconfig-jenkins.yaml
[ -f /opt/datn/platform/.env ] || echo "DOCKER_GID=$(getent group docker | cut -d: -f3)" > /opt/datn/platform/.env

echo
echo "XONG. Private IP của EC2: ${PRIVATE_IP}"
echo "Bước tiếp theo:"
echo "  1) Copy jenkins.Dockerfile + docker-compose.platform.yml vào /opt/datn/platform rồi:"
echo "       cd /opt/datn/platform && docker compose up -d --build"
echo "  2) Sửa email trong bootstrap/cluster-issuer.yaml; IP ${PRIVATE_IP} + tên miền trong bootstrap/jenkins-ingress.yaml:"
echo "       kubectl apply -f cluster-issuer.yaml && kubectl apply -f jenkins-ingress.yaml"
echo "  3) Tải /opt/datn/kubeconfig-jenkins.yaml về máy, upload vào Jenkins (Secret file, ID: k3s-kubeconfig), rồi XOÁ file trên server."
