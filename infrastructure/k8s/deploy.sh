#!/usr/bin/env bash
# Deploy chart datn-app lên Minikube. CHẠY TRÊN datn-minikube bằng user `deployer` (kubeconfig giới hạn của ci-deployer).
# Cài một lần ở bước 6:  sudo install -m 755 -o deployer -g deployer deploy.sh /home/deployer/deploy.sh
#
# Jenkins gửi chart theo từng lần deploy qua stdin để chart và image luôn cùng một commit:
#   tar cz -C infrastructure helm | ssh -o BatchMode=yes deployer@<IP-private-datn-minikube> "./deploy.sh <git-sha-ngắn>"
# Chạy tay để thử (từ bản clone repo trên server):
#   HELM_DIR=~/DATN-2026/infrastructure/helm ./deploy.sh <git-sha-ngắn>
#
# Biến môi trường tuỳ chọn: NAMESPACE (datn), DEPLOY_TIMEOUT (15m), SKIP_SMOKE=1 (bỏ smoke test), KUBECONFIG.
set -euo pipefail

TAG="${1:?Cách dùng: $0 <git-sha-ngắn>   (chart gửi qua stdin, hoặc đặt HELM_DIR=<thư-mục-helm>)}"
NS="${NAMESPACE:-datn}"
RELEASE=datn-app
TIMEOUT="${DEPLOY_TIMEOUT:-15m}"
export KUBECONFIG="${KUBECONFIG:-$HOME/.kube/config}"
export HELM_DRIVER=configmap            # lịch sử release lưu ở ConfigMap: ci-deployer không cần quyền đọc Secret

[[ "$TAG" =~ ^[0-9a-f]{7,40}$ ]] || { echo "TAG phải là git SHA (7–40 ký tự hex thường): '$TAG'"; exit 1; }

# 1) Lấy thư mục helm/: từ HELM_DIR (chạy tay) hoặc giải nén từ stdin (Jenkins)
if [ -n "${HELM_DIR:-}" ]; then
  BASE="$(cd "$HELM_DIR" && pwd)"
else
  WORK="$(mktemp -d)"; trap 'rm -rf "$WORK"' EXIT
  [ -t 0 ] && { echo "Không có chart trên stdin. Jenkins: tar cz -C infrastructure helm | ssh ... ./deploy.sh <tag>. Chạy tay: HELM_DIR=<thư-mục-helm> $0 <tag>"; exit 1; }
  tar xz -C "$WORK" || { echo "Không giải nén được chart từ stdin. Jenkins phải gửi: tar cz -C infrastructure helm | ssh ... ./deploy.sh <tag>"; exit 1; }
  BASE="$WORK/helm"
fi
CHART="$BASE/datn-app"; ENVF="$BASE/env-minikube.yaml"; VALUES="$CHART/values-minikube.yaml"
for f in "$CHART/Chart.yaml" "$VALUES" "$ENVF"; do [ -f "$f" ] || { echo "Thiếu $f (chart gửi thiếu hoặc sai cấu trúc)"; exit 1; }; done

yaml_get() { sed -n "s/^  $1: *\([^ #]*\).*/\1/p" "$ENVF" | head -1; }
SCHEME="$(yaml_get scheme)"; APP_HOST="$(yaml_get appHost)"; REGISTRY="$(yaml_get registry)"
case "$APP_HOST" in ""|*.invalid) echo "env-minikube.yaml chưa đặt appHost ('$APP_HOST'). Chạy set-public-host.sh ở máy dev rồi commit."; exit 1 ;; esac
case "$REGISTRY" in ""|*CHANGE_ME*) echo "env-minikube.yaml chưa đặt registry ('$REGISTRY'). Chạy set-public-host.sh ở máy dev rồi commit."; exit 1 ;; esac
APP_URL="$SCHEME://$APP_HOST"

# Helm 4 đổi --atomic thành --rollback-on-failure: dò theo trợ giúp của chính bản helm đang cài
if helm upgrade --help 2>&1 | grep -q -- '--rollback-on-failure'; then ROLLBACK_FLAG=--rollback-on-failure; else ROLLBACK_FLAG=--atomic; fi

kubectl auth can-i patch deployments -n "$NS" 2>/dev/null | grep -qx yes \
  || { echo "kubeconfig hiện tại ($KUBECONFIG) không có quyền deploy trên namespace $NS"; exit 1; }

HELM_ARGS=(-n "$NS" -f "$VALUES" -f "$ENVF" --set "image.tag=$TAG")

echo "== helm lint + render (tag $TAG, $APP_URL)"
helm lint "$CHART" "${HELM_ARGS[@]}" --strict
helm template "$RELEASE" "$CHART" "${HELM_ARGS[@]}" | grep -E '^\s+image:' | sort -u

# Lần cài đầu chưa có release nên `helm history` báo lỗi: phải chịu được (pipefail sẽ làm script thoát nếu nối thẳng vào jq)
HISTORY=$(helm -n "$NS" history "$RELEASE" --max 20 -o json 2>/dev/null || echo '[]')
PREV=$(echo "$HISTORY" | jq -r '[.[] | select(.status=="deployed")] | last | .revision // empty' 2>/dev/null || true)
echo "== Revision đang chạy: ${PREV:-<chưa có, lần cài đầu>}"

diagnose() {
  helm -n "$NS" status "$RELEASE" || true
  helm -n "$NS" history "$RELEASE" --max 5 || true
  kubectl -n "$NS" get pods -o wide || true
  kubectl -n "$NS" get events --sort-by=.lastTimestamp 2>/dev/null | tail -40 || true
  for p in $(kubectl -n "$NS" get pods -l datn/layer=app -o name 2>/dev/null); do
    echo "--- $p"; kubectl -n "$NS" logs "$p" --all-containers --tail=60 || true
  done
}

smoke() {
  # /<service>/actuator/health đi qua Ingress -> api-gateway -> service. Kiểm cả NỘI DUNG: nếu route hỏng và rơi về frontend,
  # nginx của SPA vẫn trả index.html với mã 200.
  local p out
  for p in identity work workspace; do
    out=$(curl -fsS --retry 12 --retry-delay 10 --retry-all-errors --max-time 15 "$APP_URL/$p/actuator/health") || return 1
    echo "  $p: $out"
    echo "$out" | grep -q '"status":"UP"' || { echo "  -> không phải health JSON UP"; return 1; }
  done
  echo -n "  frontend: "; curl -fsS -o /dev/null -w '%{http_code}\n' --max-time 15 "$APP_URL/" || return 1
}

echo "== helm upgrade --install ($ROLLBACK_FLAG, timeout $TIMEOUT)"
echo "   (R6: pod cũ tắt rồi mới bật pod mới, nên các service gián đoạn lần lượt trong lúc deploy, thường 5–8 phút)"
if ! helm upgrade --install "$RELEASE" "$CHART" "${HELM_ARGS[@]}" \
       "$ROLLBACK_FLAG" --wait --timeout "$TIMEOUT" --history-max 10; then
  echo "❌ Rollout lỗi, Helm đã tự rollback"
  diagnose; exit 1
fi

if [ "${SKIP_SMOKE:-0}" = 1 ]; then
  echo "== Bỏ qua smoke test (SKIP_SMOKE=1)"
else
  echo "== Smoke test qua Ingress ($APP_URL)"
  if ! smoke; then
    echo "❌ Smoke test lỗi, rollback về revision ${PREV:-<không có>}"
    diagnose
    if [ -n "$PREV" ]; then helm -n "$NS" rollback "$RELEASE" "$PREV" --wait --timeout "$TIMEOUT"; fi
    exit 1
  fi
fi

helm -n "$NS" history "$RELEASE" --max 3
echo "✅ $RELEASE@$TAG đã chạy trên $NS"
