#!/usr/bin/env bash
# =============================================================================
# registry-cleanup.sh – HƯỚNG 2: dọn registry localhost:5000
# Giữ các tag xuất hiện trong 5 lần deploy thành công gần nhất (+ bảng tag hiện tại),
# xoá các tag còn lại rồi chạy garbage-collect để giải phóng ổ đĩa.
# Chạy tay hoặc cron hằng tuần:  sudo bash /opt/datn/registry-cleanup.sh
# Cần: jq (setup-k3s.sh đã cài)
# =============================================================================
set -euo pipefail

REG=http://localhost:5000
STATE=/opt/datn/state
ACCEPT='Accept: application/vnd.docker.distribution.manifest.v2+json, application/vnd.oci.image.manifest.v1+json, application/vnd.oci.image.index.v1+json'

KEEP=$( { ls -t "$STATE"/history/[0-9]*.env 2>/dev/null | head -5 | xargs -r cat; cat "$STATE/deployed.env"; } \
        | cut -d= -f2 | sort -u )
echo "Giữ lại các tag:"; echo "$KEEP"

for repo in $(curl -fsS "$REG/v2/_catalog" | jq -r '.repositories[]'); do
  for tag in $(curl -fsS "$REG/v2/$repo/tags/list" | jq -r '.tags[]?'); do
    if echo "$KEEP" | grep -qx "$tag"; then continue; fi
    digest=$(curl -fsSI -H "$ACCEPT" "$REG/v2/$repo/manifests/$tag" \
             | grep -i '^docker-content-digest:' | awk '{print $2}' | tr -d '\r')
    [ -n "$digest" ] || continue
    curl -fsS -X DELETE "$REG/v2/$repo/manifests/$digest" >/dev/null && echo "Đã xoá $repo:$tag"
  done
done

# Xoá các lớp (blob) không còn manifest nào tham chiếu
docker exec registry bin/registry garbage-collect /etc/docker/registry/config.yml --delete-untagged
docker restart registry >/dev/null   # registry giữ cache metadata trong RAM → khởi động lại cho sạch
du -sh /var/lib/docker/volumes/datn-platform_registry_data 2>/dev/null || true
