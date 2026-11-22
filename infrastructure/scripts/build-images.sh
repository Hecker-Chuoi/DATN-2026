#!/usr/bin/env bash
# Build + quét + push 6 image, TUẦN TỰ để không tranh RAM với cluster (03 §12.1). Cần đã `docker login ghcr.io`.
# Chạy bằng Docker rootless của Jenkins (DOCKER_HOST). Cách dùng: build-images.sh <git-sha>
set -euo pipefail
TAG="${1:?Cách dùng: $0 <git-sha>}"
REGISTRY="${REGISTRY:-ghcr.io/hecker-chuoi}"         # GHCR bắt buộc chữ thường
SOURCE_URL="${SOURCE_URL:-https://github.com/Hecker-Chuoi/DATN-2026}"
SERVICES="${SERVICES:-api-gateway identity-service work-service workspace-service notification-service frontend}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
export DOCKER_BUILDKIT=1

for s in $SERVICES; do
  if [ "$s" = frontend ]; then ctx=frontend; file=frontend/Dockerfile; else ctx=backend; file="backend/$s/Dockerfile"; fi
  img="$REGISTRY/datn-$s"
  echo "== Build $img:$TAG"
  docker build \
    --label "org.opencontainers.image.revision=$TAG" \
    --label "org.opencontainers.image.source=$SOURCE_URL" \
    -t "$img:$TAG" -t "$img:main" -f "$file" "$ctx"
  echo "== Quét $img:$TAG (chặn nếu có lỗ hổng CRITICAL đã có bản vá)"
  trivy image --quiet --severity CRITICAL --ignore-unfixed --exit-code 1 "$img:$TAG"
  docker push "$img:$TAG"
  docker push "$img:main"
done
echo "✅ Đã push $(echo "$SERVICES" | wc -w) image với tag $TAG"
