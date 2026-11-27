#!/usr/bin/env bash
# Tạo Secret `keycloak-realm` (key realm.json) từ file realm export của repo, đã CHỈNH cho môi trường Minikube.
# Keycloak (chart datn-platform) mount Secret này vào /opt/keycloak/data/import và import khi realm chưa tồn tại.
#
# Chạy trên datn-minikube, bằng user ubuntu (kubeconfig admin), TRƯỚC khi `helm install datn-platform`:
#   bash infrastructure/k8s/make-realm-secret.sh [namespace]            # mặc định namespace: datn
#
# Sửa gì trong realm:
#   - client datn-backend: secret  <- key keycloak-client-secret của Secret app-secrets (khớp biến KEYCLOAK_CLIENT_SECRET của identity-service)
#   - client datn-backend: redirectUris / webOrigins  <- URL công khai trong infrastructure/helm/env-minikube.yaml
# Giữ nguyên: identity provider Google (clientId/clientSecret trong file export). Muốn đăng nhập Google chạy được thì thêm
#   redirect URI  <scheme>://<ssoHost>/realms/datn-realm/broker/google/endpoint  trong Google Cloud Console.
#
# Biến môi trường (tuỳ chọn):
#   ENV_FILE                file env-minikube.yaml (mặc định infrastructure/helm/env-minikube.yaml)
#   REALM_FILE              file realm gốc (mặc định infrastructure/keycloak/realm-export_08082026.json)
#   KEYCLOAK_CLIENT_SECRET  dùng giá trị này thay vì đọc từ Secret app-secrets (để thử khi không có cụm)
#   OUT_ONLY=1              chỉ ghi file ra OUT (mặc định ./realm.patched.json), KHÔNG tạo Secret
set -euo pipefail

NS="${1:-datn}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
REALM_FILE="${REALM_FILE:-$ROOT/infrastructure/keycloak/realm-export_08082026.json}"
ENV_FILE="${ENV_FILE:-$ROOT/infrastructure/helm/env-minikube.yaml}"
OUT="${OUT:-./realm.patched.json}"

command -v jq >/dev/null || { echo "Thiếu jq (sudo apt-get install -y jq)"; exit 1; }
[ -f "$REALM_FILE" ] || { echo "Không có $REALM_FILE"; exit 1; }

# Đọc scheme / appHost từ env-minikube.yaml (khoá `global`, thụt 2 dấu cách)
yaml_get() { sed -n "s/^  $1: *\([^ #]*\).*/\1/p" "$ENV_FILE" | head -1; }
SCHEME="$(yaml_get scheme)"; APP_HOST="$(yaml_get appHost)"; SSO_HOST="$(yaml_get ssoHost)"
case "$APP_HOST" in ""|*.invalid) echo "env-minikube.yaml chưa đặt appHost (đang là '$APP_HOST'). Chạy: bash infrastructure/k8s/set-public-host.sh <tên-miền> <http|https> <dockerhub-user>"; exit 1 ;; esac
APP_URL="$SCHEME://$APP_HOST"

if [ -n "${KEYCLOAK_CLIENT_SECRET:-}" ]; then
  CLIENT_SECRET="$KEYCLOAK_CLIENT_SECRET"; SECRET_SOURCE="biến môi trường KEYCLOAK_CLIENT_SECRET"
else
  CLIENT_SECRET="$(kubectl -n "$NS" get secret app-secrets -o jsonpath='{.data.keycloak-client-secret}' | base64 -d)"
  SECRET_SOURCE="Secret app-secrets"
fi
[ -n "$CLIENT_SECRET" ] || { echo "Không đọc được keycloak-client-secret (đã tạo app-secrets ở bước 2 chưa?)"; exit 1; }

umask 077
jq --arg secret "$CLIENT_SECRET" --arg app "$APP_URL" '
  if any(.clients[]; .clientId == "datn-backend") | not then error("realm không có client datn-backend") else . end
  | (.clients[] | select(.clientId == "datn-backend")) |= (
        .secret       = $secret
      | .redirectUris = [($app + "/*"), ($app + "/auth/callback")]
      | .webOrigins   = [$app]
    )' "$REALM_FILE" > "$OUT"

# Kiểm tra lại kết quả (không in secret)
jq -e --arg app "$APP_URL" '
  (.clients[] | select(.clientId == "datn-backend")) as $c
  | $c.redirectUris[0] == ($app + "/*") and $c.webOrigins == [$app] and ($c.secret | length) > 0' "$OUT" >/dev/null \
  || { echo "Realm sau khi sửa không đúng kỳ vọng"; exit 1; }
echo "== Đã chỉnh realm: client datn-backend → $APP_URL (secret lấy từ $SECRET_SOURCE)"

if [ "${OUT_ONLY:-0}" = 1 ]; then echo "== OUT_ONLY=1: đã ghi $OUT, không tạo Secret"; exit 0; fi

kubectl -n "$NS" create secret generic keycloak-realm --from-file=realm.json="$OUT" --dry-run=client -o yaml | kubectl apply -f -
rm -f "$OUT"
echo "== Secret keycloak-realm đã sẵn sàng trong namespace $NS"
echo "   Google Console cần redirect URI: $SCHEME://$SSO_HOST/realms/datn-realm/broker/google/endpoint"
