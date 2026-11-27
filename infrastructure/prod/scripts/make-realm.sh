#!/usr/bin/env bash
# Sinh realm.json cho Keycloak từ file realm export của repo, đã CHỈNH cho môi trường production.
# deploy.sh gọi script này MỘT LẦN (khi chưa có $DATN_HOME/realm.json); Keycloak import file khi realm CHƯA tồn tại.
#
#   make-realm.sh <realm-export.json> <out.json> <public.env> <secrets.env>
#
# Sửa gì trong realm:
#   - client datn-backend: secret  <- KEYCLOAK_CLIENT_SECRET trong secrets.env (khớp biến của identity-service)
#   - client datn-backend: redirectUris / webOrigins  <- ${SCHEME}://${APP_HOST} trong public.env
# Giữ nguyên: identity provider Google (clientId/clientSecret trong file export). Muốn đăng nhập Google chạy được thì thêm
#   redirect URI  <scheme>://<SSO_HOST>/realms/datn-realm/broker/google/endpoint  trong Google Cloud Console.
set -euo pipefail

REALM_FILE="${1:?Cách dùng: $0 <realm-export.json> <out.json> <public.env> <secrets.env>}"
OUT="${2:?thiếu <out.json>}"; PUBLIC_ENV="${3:?thiếu <public.env>}"; SECRETS_ENV="${4:?thiếu <secrets.env>}"

command -v jq >/dev/null || { echo "Thiếu jq (sudo apt-get install -y jq)"; exit 1; }
for f in "$REALM_FILE" "$PUBLIC_ENV" "$SECRETS_ENV"; do [ -f "$f" ] || { echo "Không có $f"; exit 1; }; done

# Đọc từng khoá bằng sed (không `source`: giá trị như MAIL_FROM="... <...>" làm bash hiểu nhầm)
env_get() { sed -n "s/^$2=//p" "$1" | head -1 | sed -e 's/^"//' -e 's/"$//'; }
SCHEME="$(env_get "$PUBLIC_ENV" SCHEME)"; APP_HOST="$(env_get "$PUBLIC_ENV" APP_HOST)"
CLIENT_SECRET="$(env_get "$SECRETS_ENV" KEYCLOAK_CLIENT_SECRET)"
case "$APP_HOST" in ""|*.invalid) echo "public.env chưa đặt APP_HOST (đang là '$APP_HOST'). Chạy set-public-host.sh ở máy dev rồi commit."; exit 1 ;; esac
[ -n "$CLIENT_SECRET" ] || { echo "secrets.env thiếu KEYCLOAK_CLIENT_SECRET"; exit 1; }
APP_URL="$SCHEME://$APP_HOST"

umask 077
jq --arg secret "$CLIENT_SECRET" --arg app "$APP_URL" '
  if any(.clients[]; .clientId == "datn-backend") | not then error("realm không có client datn-backend") else . end
  | (.clients[] | select(.clientId == "datn-backend")) |= (
        .secret       = $secret
      | .redirectUris = [($app + "/*"), ($app + "/auth/callback")]
      | .webOrigins   = [$app]
    )' "$REALM_FILE" > "$OUT.tmp"

# Kiểm tra lại kết quả (không in secret)
jq -e --arg app "$APP_URL" '
  (.clients[] | select(.clientId == "datn-backend")) as $c
  | $c.redirectUris[0] == ($app + "/*") and $c.webOrigins == [$app] and ($c.secret | length) > 0' "$OUT.tmp" >/dev/null \
  || { rm -f "$OUT.tmp"; echo "Realm sau khi sửa không đúng kỳ vọng"; exit 1; }
mv "$OUT.tmp" "$OUT"
# Keycloak chạy uid 1000 trong container: file bind-mount mode 600 (umask 077 ở trên) → AccessDeniedException, Keycloak không khởi động.
# An toàn vì thư mục chứa file ($DATN_HOME) là 700 của deployer.
chmod 644 "$OUT"
echo "== Đã sinh $OUT: client datn-backend → $APP_URL"
