#!/usr/bin/env bash
# Sao lưu hai database (datn_main_db, keycloak) ra $DATN_HOME/backups, giữ 7 ngày. Chạy bằng user deployer (cron).
#   0 2 * * * /home/deployer/datn/current/prod/scripts/backup-postgres.sh >> /home/deployer/datn/backups/backup.log 2>&1
# Khôi phục: docker exec -i <postgres> pg_restore -U postgres -d datn_main_db --clean --if-exists < file.dump
set -euo pipefail
DATN_HOME="${DATN_HOME:-/home/deployer/datn}"
DIR="$DATN_HOME/backups"; mkdir -p "$DIR"
CID="$(docker ps -q --filter label=com.docker.compose.project=datn --filter label=com.docker.compose.service=postgres)"
[ -n "$CID" ] || { echo "$(date -Is) Postgres không chạy"; exit 1; }
STAMP="$(date +%Y%m%d-%H%M%S)"
for db in datn_main_db keycloak; do
  docker exec "$CID" pg_dump -U postgres -Fc "$db" > "$DIR/$db-$STAMP.dump"
  echo "$(date -Is) đã sao lưu $db → $DIR/$db-$STAMP.dump ($(du -h "$DIR/$db-$STAMP.dump" | cut -f1))"
done
find "$DIR" -name '*.dump' -mtime +7 -delete
