#!/usr/bin/env bash
# Xóa user test để đăng ký lại (test luồng verify email).
# Dùng: ./delete-test-user.sh <username|email>   (chạy từ Git Bash, app-db và keycloak phải đang chạy)
# Xóa: verification_tokens, user_roles, users trong datn_main_db + user trong Keycloak (nếu còn).
set -euo pipefail
ID="${1:?Cần username hoặc email}"
KC="${KC_URL:-http://localhost:9998}"

KC_ID=$(docker exec app-db psql -U postgres -d datn_main_db -tAc \
  "select keycloak_id from users where username='$ID' or email='$ID'")
if [ -z "$KC_ID" ]; then echo "Không có user '$ID' trong bảng users"; else
  docker exec -i app-db psql -U postgres -d datn_main_db -v ON_ERROR_STOP=1 <<SQL
delete from verification_tokens where user_id in (select id from users where keycloak_id='$KC_ID');
delete from user_roles          where user_id in (select id from users where keycloak_id='$KC_ID');
delete from users               where keycloak_id='$KC_ID';
SQL
  echo "Đã xóa '$ID' khỏi DB (keycloak_id=$KC_ID)"
fi

# Keycloak: tìm theo username/email, xóa nếu còn (đã xóa tay trên UI thì bỏ qua)
TOKEN=$(curl -s -d 'client_id=admin-cli' -d 'username=admin' -d 'password=admin' -d 'grant_type=password' \
  "$KC/realms/master/protocol/openid-connect/token" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
UID_KC=$(curl -s -H "Authorization: Bearer $TOKEN" "$KC/admin/realms/datn-realm/users?search=$ID&exact=true" \
  | sed -n 's/.*"id":"\([^"]*\)".*/\1/p' | head -1)
if [ -n "$UID_KC" ]; then
  curl -s -o /dev/null -w "Keycloak: xóa user $UID_KC -> HTTP %{http_code}\n" -X DELETE \
    -H "Authorization: Bearer $TOKEN" "$KC/admin/realms/datn-realm/users/$UID_KC"
else
  echo "Keycloak: không còn user '$ID'"
fi
