#!/usr/bin/env bash
# Build + (quét) + push image của DATN-2026 lên Docker Hub.
# Cách dùng:  bash infrastructure/ci/build-push.sh <tag>        (tag = git SHA ngắn, ví dụ a1b2c3d)
# Jenkins gọi sau khi đã `docker login` bằng credential `dockerhub` (docs/cicd/cac_buoc/01 §9). Chạy tay được khi Jenkins hỏng.
#
# Đọc từ infrastructure/prod/public.env (nơi duy nhất ghi tên miền và registry):
#   REGISTRY          docker.io/<tài-khoản-dockerhub>          (ghi đè bằng biến môi trường REGISTRY)
#   VITE_KEYCLOAK_URL <scheme>://<SSO_HOST>  — Vite NHÚNG URL này vào bundle frontend lúc build (02 nghiên cứu §3.3)
# Biến môi trường tuỳ chọn:
#   SERVICES          danh sách service cần build (mặc định cả 6)
#   EXTRA_TAG         gắn thêm một tag phụ (ví dụ main). deploy.sh luôn chạy theo TAG (SHA), không theo tag phụ.
#   TRIVY_EXIT_CODE   0 (mặc định) = quét chỉ báo cáo; 1 = chặn khi có lỗ hổng HIGH/CRITICAL đã có bản vá
set -euo pipefail

TAG="${1:?Cách dùng: $0 <tag>  (git SHA ngắn)}"
[[ "$TAG" =~ ^[0-9a-f]{7,40}$ ]] || { echo "Tag phải là git SHA (7–40 ký tự hex thường): '$TAG'"; exit 1; }

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT/infrastructure/prod/public.env}"
yaml_get() { sed -n "s/^$1=//p" "$ENV_FILE" | head -1 | sed -e 's/^"//' -e 's/"$//'; }   # đọc KEY=value

REGISTRY="${REGISTRY:-$(yaml_get REGISTRY)}"
case "$REGISTRY" in ""|*CHANGE_ME*) echo "Chưa đặt registry. Chạy: bash infrastructure/prod/scripts/set-public-host.sh <tên-miền> <http|https> <dockerhub-user>"; exit 1 ;; esac

if [ -z "${VITE_KEYCLOAK_URL:-}" ]; then
  SSO_HOST="$(yaml_get SSO_HOST)"; SCHEME="$(yaml_get SCHEME)"
  case "$SSO_HOST" in ""|*.invalid) echo "Chưa đặt SSO_HOST trong $ENV_FILE (VITE_KEYCLOAK_URL sẽ sai). Chạy prod/scripts/set-public-host.sh trước."; exit 1 ;; esac
  VITE_KEYCLOAK_URL="$SCHEME://$SSO_HOST"
fi

SERVICES="${SERVICES:-api-gateway identity-service work-service workspace-service notification-service frontend}"
TRIVY_EXIT_CODE="${TRIVY_EXIT_CODE:-0}"
SOURCE_URL="${SOURCE_URL:-}"
export DOCKER_BUILDKIT=1                     # Dockerfile backend dùng RUN --mount=type=cache

cd "$ROOT"
echo "== Registry: $REGISTRY | tag: $TAG | VITE_KEYCLOAK_URL: $VITE_KEYCLOAK_URL"

for s in $SERVICES; do
  case "$s" in
    frontend) ctx=frontend; file=frontend/Dockerfile; extra=(--build-arg "VITE_KEYCLOAK_URL=$VITE_KEYCLOAK_URL") ;;
    api-gateway|identity-service|work-service|workspace-service|notification-service) ctx=backend; file="backend/$s/Dockerfile"; extra=() ;;
    *) echo "Service không hợp lệ: $s"; exit 1 ;;
  esac
  [ -f "$file" ] || { echo "Không có $file"; exit 1; }
  img="$REGISTRY/datn-$s"

  echo "== Build $img:$TAG"
  tags=(-t "$img:$TAG"); [ -n "${EXTRA_TAG:-}" ] && tags+=(-t "$img:$EXTRA_TAG")
  labels=(--label "org.opencontainers.image.revision=$TAG"); [ -n "$SOURCE_URL" ] && labels+=(--label "org.opencontainers.image.source=$SOURCE_URL")
  docker build "${labels[@]}" "${extra[@]}" "${tags[@]}" -f "$file" "$ctx"

  if command -v trivy >/dev/null 2>&1; then
    echo "== Quét $img:$TAG (HIGH/CRITICAL đã có bản vá; exit-code=$TRIVY_EXIT_CODE, 0 = chỉ báo cáo)"
    trivy image --quiet --severity HIGH,CRITICAL --ignore-unfixed --exit-code "$TRIVY_EXIT_CODE" "$img:$TAG"
  else
    echo "== Bỏ qua quét: chưa cài trivy (tuỳ chọn, 01 §4)"
  fi

  docker push "$img:$TAG"
  [ -n "${EXTRA_TAG:-}" ] && docker push "$img:$EXTRA_TAG"
done

echo "✅ Đã push $(echo "$SERVICES" | wc -w) image với tag $TAG lên $REGISTRY"
