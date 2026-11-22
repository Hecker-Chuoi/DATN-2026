#!/usr/bin/env bash
# helm upgrade --install release datn-app, tự rollback khi rollout lỗi hoặc smoke test lỗi (03 §12.2).
# Cách dùng: KUBECONFIG=<kubeconfig ci-deployer> deploy.sh <staging|prod> <namespace> <git-sha> <app-url>
set -euo pipefail
ENV_NAME="${1:?staging|prod}"; NS="${2:?namespace}"; TAG="${3:?git sha}"; APP_URL="${4:?app url}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CHART="$ROOT/infrastructure/helm/datn-app"
VALUES="$CHART/values-$ENV_NAME.yaml"
RELEASE=datn-app
SELECTOR="datn/layer=app"
TIMEOUT="${DEPLOY_TIMEOUT:-15m}"
export HELM_DRIVER=configmap           # release lưu ở ConfigMap: CI không cần quyền Secret

[ -f "$VALUES" ] || { echo "Không có $VALUES"; exit 1; }
[[ "$TAG" =~ ^[0-9a-f]{40}$ ]] || { echo "TAG phải là git SHA đầy đủ (40 ký tự hex): $TAG"; exit 1; }

# Helm 4 đổi --atomic thành --rollback-on-failure: dò theo trợ giúp của chính bản helm đang cài
if helm upgrade --help | grep -q -- '--rollback-on-failure'; then ROLLBACK_FLAG=--rollback-on-failure; else ROLLBACK_FLAG=--atomic; fi

kubectl auth can-i patch deployments -n "$NS" | grep -qx yes || { echo "ci-deployer không có quyền trên $NS"; exit 1; }

echo "== Lint + render"
helm lint "$CHART" -f "$VALUES" --set image.tag="$TAG"
helm template "$RELEASE" "$CHART" -n "$NS" -f "$VALUES" --set image.tag="$TAG" | grep -E '^\s+image:' | sort -u

PREV=$(helm -n "$NS" history "$RELEASE" --max 20 -o json 2>/dev/null \
       | jq -r '[.[] | select(.status=="deployed")] | last | .revision // empty' || true)
echo "== Revision đang chạy: ${PREV:-<chưa có>}"

diagnose() {
  helm -n "$NS" status "$RELEASE" || true
  helm -n "$NS" history "$RELEASE" --max 5 || true
  kubectl -n "$NS" get pods -o wide || true
  kubectl -n "$NS" get events --sort-by=.lastTimestamp | tail -40 || true
  for p in $(kubectl -n "$NS" get pods -l "$SELECTOR" -o name); do
    echo "--- $p"; kubectl -n "$NS" logs "$p" --all-containers --tail=80 || true
  done
}

smoke() {
  local p out
  # /<service>/actuator/health đi qua Ingress -> api-gateway -> service (gồm cả kiểm tra DB, Redis).
  # Kiểm cả NỘI DUNG: nếu route hỏng rơi về frontend, nginx của SPA vẫn trả index.html với mã 200.
  for p in identity work workspace; do
    out=$(curl -fsS --retry 10 --retry-delay 6 --retry-all-errors "$APP_URL/$p/actuator/health") || return 1
    echo "$p: $out"
    echo "$out" | grep -q '"status":"UP"' || { echo "  -> không phải health JSON UP"; return 1; }
  done
  echo -n "frontend: "; curl -fsS -o /dev/null -w '%{http_code}\n' "$APP_URL/" || return 1
}

echo "== helm upgrade --install ($ROLLBACK_FLAG, timeout $TIMEOUT)"
if ! helm upgrade --install "$RELEASE" "$CHART" -n "$NS" -f "$VALUES" --set image.tag="$TAG" \
       "$ROLLBACK_FLAG" --wait --timeout "$TIMEOUT" --history-max 10; then
  echo "❌ Rollout lỗi, Helm đã tự rollback"
  diagnose; exit 1
fi

echo "== Smoke test qua Ingress ($APP_URL)"
if ! smoke; then
  echo "❌ Smoke test lỗi, rollback về revision ${PREV:-<không có>}"
  diagnose
  if [ -n "$PREV" ]; then helm -n "$NS" rollback "$RELEASE" "$PREV" --wait --timeout "$TIMEOUT"; fi
  exit 1
fi

helm -n "$NS" history "$RELEASE" --max 3
echo "✅ $RELEASE@$TAG đã chạy trên $NS"
