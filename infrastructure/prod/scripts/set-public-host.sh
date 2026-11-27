#!/usr/bin/env bash
# Đặt tên miền, giao thức và tài khoản Docker Hub vào infrastructure/prod/public.env (nơi DUY NHẤT ghi các giá trị này).
# Cách dùng (chạy ở máy dev, gốc repo, rồi commit):
#   bash infrastructure/prod/scripts/set-public-host.sh <tên-miền-gốc> <http|https> <tài-khoản-dockerhub>
# Ví dụ:
#   bash infrastructure/prod/scripts/set-public-host.sh 13-250-1-2.sslip.io https dockeruser   # sslip.io khi chưa có tên miền
#   bash infrastructure/prod/scripts/set-public-host.sh datn.example.com https dockeruser
# Kết quả: APP_HOST = app.<tên-miền-gốc>, SSO_HOST = sso.<tên-miền-gốc>, REGISTRY = docker.io/<tài-khoản>.
set -euo pipefail

USAGE="Cách dùng: $0 <tên-miền-gốc> <http|https> <tài-khoản-dockerhub>"
DOMAIN="${1:?$USAGE}"; SCHEME="${2:?$USAGE}"; DH_USER="${3:?$USAGE}"

case "$SCHEME" in http|https) ;; *) echo "Giao thức phải là http hoặc https (nhận: $SCHEME)"; exit 1 ;; esac
[[ "$DOMAIN" =~ ^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$ ]] || { echo "Tên miền gốc không hợp lệ (chữ thường, số, dấu . và -): $DOMAIN"; exit 1; }
[[ "$DOMAIN" == *.* ]] || { echo "Tên miền gốc cần có dấu chấm, ví dụ example.com: $DOMAIN"; exit 1; }
# Docker Hub không cho chữ hoa trong tên repo
[[ "$DH_USER" =~ ^[a-z0-9][a-z0-9_.-]*$ ]] || { echo "Tài khoản Docker Hub phải viết thường (a-z, 0-9, _ . -): $DH_USER"; exit 1; }

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
FILE="${ENV_FILE:-$ROOT/infrastructure/prod/public.env}"
[ -f "$FILE" ] || { echo "Không có $FILE"; exit 1; }

COOKIE_SECURE=true; [ "$SCHEME" = http ] && COOKIE_SECURE=false
sed -i -E \
  -e "s#^SCHEME=.*#SCHEME=$SCHEME#" \
  -e "s#^APP_HOST=.*#APP_HOST=app.$DOMAIN#" \
  -e "s#^SSO_HOST=.*#SSO_HOST=sso.$DOMAIN#" \
  -e "s#^REGISTRY=.*#REGISTRY=docker.io/$DH_USER#" \
  -e "s#^COOKIE_SECURE=.*#COOKIE_SECURE=$COOKIE_SECURE#" \
  "$FILE"

echo "== $FILE"
grep -E '^(SCHEME|APP_HOST|SSO_HOST|REGISTRY|COOKIE_SECURE)=' "$FILE"
echo
echo "Cần có DNS: app.$DOMAIN và sso.$DOMAIN cùng trỏ về Elastic IP của server ứng dụng (sslip.io tự phân giải, không cần cấu hình)."
echo "Giá trị này KHÔNG bí mật, commit được. Commit TRƯỚC khi chạy CI build (frontend nhúng URL Keycloak lúc build)."
