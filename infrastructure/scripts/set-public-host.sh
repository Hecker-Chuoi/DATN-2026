#!/usr/bin/env bash
# Đổi tên miền gốc và giao thức công khai trong repo (docs/cicd/05 bước 4).
# Cách dùng (chạy ở máy dev, gốc repo, rồi commit):
#   set-public-host.sh 13-250-1-2.sslip.io http    # tạm dùng IP EC2 13.250.1.2, chưa có tên miền, chưa HTTPS
#   set-public-host.sh tenmien.vn https            # khi đã có tên miền thật và chứng chỉ TLS
# Đổi: host và URL trong values của 2 chart, cookieSecure của datn-app. Workflow GitHub Actions và deploy.sh
# tự đọc URL công khai từ publicUrl của values, nên không phải sửa ở nơi khác.
set -euo pipefail
NEW="${1:?Cách dùng: $0 <tên miền gốc> <http|https>}"
SCHEME="${2:?Cách dùng: $0 <tên miền gốc> <http|https>}"
case "$SCHEME" in http) SECURE=false ;; https) SECURE=true ;; *) echo "Giao thức phải là http hoặc https"; exit 1 ;; esac
[[ "$NEW" =~ ^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$ ]] || { echo "Tên miền gốc không hợp lệ: $NEW"; exit 1; }

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
FILES=(
  infrastructure/helm/datn-app/values-staging.yaml
  infrastructure/helm/datn-app/values-prod.yaml
  infrastructure/helm/datn-platform/values-staging.yaml
  infrastructure/helm/datn-platform/values-prod.yaml
)

# Tên miền gốc hiện tại lấy từ "host: staging.<gốc>" của datn-app
OLD=$(sed -n 's/^  host: staging\.\(.*\)$/\1/p' infrastructure/helm/datn-app/values-staging.yaml)
[ -n "$OLD" ] || { echo "Không đọc được tên miền hiện tại từ values-staging.yaml"; exit 1; }
OLD_RE=$(printf '%s' "$OLD" | sed 's/[.]/\\./g')
echo "== $OLD  ->  $NEW ($SCHEME)"

for f in "${FILES[@]}"; do
  # 1) URL đầy đủ: đổi cả giao thức; 2) host trần (app./sso./pgadmin./mail./minio./staging....)
  #    Chỉ thay khi có tiền tố "<tên>." nên địa chỉ email kiểu no-reply@<gốc> không bị đụng.
  sed -i -E \
    -e "s#https?://([a-z0-9-]+\.)${OLD_RE}#${SCHEME}://\1${NEW}#g" \
    -e "s#(^|[^@a-z0-9-])([a-z0-9-]+\.)${OLD_RE}([^a-z0-9.-]|$)#\1\2${NEW}\3#g" \
    "$f"
done
sed -i -E "s#^cookieSecure: (true|false)#cookieSecure: ${SECURE}#" \
  infrastructure/helm/datn-app/values-staging.yaml infrastructure/helm/datn-app/values-prod.yaml

echo "== Còn sót tên cũ (phải rỗng):"
grep -n -E "([a-z0-9-]+\.)${OLD_RE}" "${FILES[@]}" | grep -v "@${OLD_RE}" || echo "(không còn)"
echo "== URL và host sau khi đổi:"
grep -n -E "https?://|^\s+host:|hostnameUrl|publicUrl|cookieSecure" "${FILES[@]}" | grep -v "github.com" | sed 's/^/  /'
