#!/usr/bin/env bash
# Sao lưu 2 DB của một namespace (03 §14.1). Chạy bằng kubeconfig admin (cần quyền exec vào StatefulSet).
# Luôn khôi phục CẢ HAI DB cùng thời điểm: user id toàn hệ thống là Keycloak sub.
# Cron: 0 2 * * * /opt/datn/infrastructure/scripts/backup-db.sh datn-prod >> /var/backups/datn/backup.log 2>&1
set -euo pipefail
NS="${1:-datn-prod}"
DIR="${BACKUP_DIR:-/var/backups/datn}/$NS"; TS=$(date +%Y%m%d-%H%M%S)
KEEP_DAYS="${KEEP_DAYS:-7}"
mkdir -p "$DIR"

dump() {  # dump <pod> <user> <db>
  local out="$DIR/$3-$TS.dump"
  # Ghi ra file tạm trước: lỗi giữa chừng thì không để lại bản dump hỏng mang tên hợp lệ
  kubectl -n "$NS" exec "$1" -- pg_dump -U "$2" -d "$3" -Fc > "$out.part"
  mv "$out.part" "$out"
  echo "OK $out ($(du -h "$out" | cut -f1))"
}
dump postgres-app-0      postgres datn_main_db
dump postgres-keycloak-0 kc_user  keycloak
find "$DIR" -name '*.dump' -mtime +"$KEEP_DAYS" -delete
