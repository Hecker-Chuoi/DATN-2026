#!/usr/bin/env bash
# Đặt tên miền, giao thức và tài khoản Docker Hub vào infrastructure/helm/env-minikube.yaml (nơi DUY NHẤT ghi các giá trị này).
# Cách dùng (chạy ở máy dev, gốc repo, rồi commit):
#   bash infrastructure/k8s/set-public-host.sh <tên-miền-gốc> <http|https> <tài-khoản-dockerhub>
# Ví dụ:
#   bash infrastructure/k8s/set-public-host.sh 13-250-1-2.sslip.io https dockeruser   # dùng sslip.io khi chưa có tên miền
#   bash infrastructure/k8s/set-public-host.sh datn.example.com https dockeruser
# Kết quả: appHost = app.<tên-miền-gốc>, ssoHost = sso.<tên-miền-gốc>, registry = docker.io/<tài-khoản>.
set -euo pipefail

DOMAIN="${1:?Cách dùng: $0 <tên-miền-gốc> <http|https> <tài-khoản-dockerhub>}"
SCHEME="${2:?Cách dùng: $0 <tên-miền-gốc> <http|https> <tài-khoản-dockerhub>}"
DH_USER="${3:?Cách dùng: $0 <tên-miền-gốc> <http|https> <tài-khoản-dockerhub>}"

case "$SCHEME" in http|https) ;; *) echo "Giao thức phải là http hoặc https (nhận: $SCHEME)"; exit 1 ;; esac
[[ "$DOMAIN" =~ ^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$ ]] || { echo "Tên miền gốc không hợp lệ (chữ thường, số, dấu . và -): $DOMAIN"; exit 1; }
[[ "$DOMAIN" == *.* ]] || { echo "Tên miền gốc cần có dấu chấm, ví dụ example.com: $DOMAIN"; exit 1; }
# Tài khoản Docker Hub: chữ thường + số (Docker Hub không cho chữ hoa trong tên repo)
[[ "$DH_USER" =~ ^[a-z0-9][a-z0-9_.-]*$ ]] || { echo "Tài khoản Docker Hub phải viết thường (a-z, 0-9, _ . -): $DH_USER"; exit 1; }

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
FILE="${ENV_FILE:-$ROOT/infrastructure/helm/env-minikube.yaml}"
[ -f "$FILE" ] || { echo "Không có $FILE"; exit 1; }

sed -i -E \
  -e "s#^(  scheme:)[[:space:]]+[^[:space:]#]+#\1 $SCHEME#" \
  -e "s#^(  appHost:)[[:space:]]+[^[:space:]#]+#\1 app.$DOMAIN#" \
  -e "s#^(  ssoHost:)[[:space:]]+[^[:space:]#]+#\1 sso.$DOMAIN#" \
  -e "s#^(  registry:)[[:space:]]+[^[:space:]#]+#\1 docker.io/$DH_USER#" \
  "$FILE"

echo "== $FILE"
grep -E '^  (scheme|appHost|ssoHost|registry):' "$FILE"
echo
echo "Cần có DNS: app.$DOMAIN và sso.$DOMAIN cùng trỏ về Elastic IP của datn-minikube (sslip.io tự phân giải, không cần cấu hình)."
echo "Giá trị này KHÔNG bí mật, commit được. Nhớ commit trước khi Jenkins build (frontend nhúng URL Keycloak lúc build)."
