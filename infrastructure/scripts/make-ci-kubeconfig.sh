#!/usr/bin/env bash
# Dựng kubeconfig CHỈ có quyền của SA ci-deployer trong 1 namespace (03 §10). Chạy bằng kubeconfig admin trên máy chủ.
# Cách dùng: make-ci-kubeconfig.sh <namespace> [server-url]   ->  tạo file kubeconfig-<namespace> ở thư mục hiện tại
#   server-url: địa chỉ API server ghi vào file. GitHub Actions đi qua đường hầm SSH nên dùng https://127.0.0.1:8443
#   (chứng chỉ API minikube có sẵn IP 127.0.0.1). Bỏ trống = địa chỉ thật của cluster (dùng ngay trên máy chủ).
set -euo pipefail
NS="${1:?Cách dùng: $0 <namespace> [server-url]}"
OUT="kubeconfig-${NS}"

REAL_SERVER=$(kubectl config view --minify -o jsonpath='{.clusters[0].cluster.server}')
SERVER="${2:-$REAL_SERVER}"
CA=$(kubectl config view --raw --flatten --minify -o jsonpath='{.clusters[0].cluster.certificate-authority-data}')
TOKEN=$(kubectl -n "$NS" get secret ci-deployer-token -o jsonpath='{.data.token}' | base64 -d)
[ -n "$TOKEN" ] || { echo "Secret ci-deployer-token chưa có token (đã cài datn-platform vào $NS chưa?)"; exit 1; }

write_kubeconfig() {  # write_kubeconfig <file> <server>
  umask 077
  cat > "$1" <<EOF
apiVersion: v1
kind: Config
clusters:
  - name: minikube
    cluster:
      server: $2
      certificate-authority-data: ${CA}
users:
  - name: ci-deployer
    user:
      token: ${TOKEN}
contexts:
  - name: ci-deployer@${NS}
    context:
      cluster: minikube
      user: ci-deployer
      namespace: ${NS}
current-context: ci-deployer@${NS}
EOF
}

# Kiểm tra bằng địa chỉ thật (máy chủ không có đường hầm 127.0.0.1:8443), rồi mới ghi file với SERVER
CHECK=$(mktemp); trap 'rm -f "$CHECK"' EXIT
write_kubeconfig "$CHECK" "$REAL_SERVER"
echo "== Kiểm tra quyền (kỳ vọng: yes yes yes no no no)"
for check in "patch deployments" "delete configmaps" "get replicasets" "get secrets" "delete statefulsets" "patch persistentvolumeclaims"; do
  # shellcheck disable=SC2086  # cố ý tách "động từ tài nguyên"
  printf '%-30s ' "$check"; KUBECONFIG="$CHECK" kubectl auth can-i $check -n "$NS" || true
done
printf '%-30s ' "namespace default (kỳ vọng no)"; KUBECONFIG="$CHECK" kubectl auth can-i patch deployments -n default || true
echo "== Helm (driver configmap) thấy release:"
KUBECONFIG="$CHECK" HELM_DRIVER=configmap helm -n "$NS" list || true

write_kubeconfig "$OUT" "$SERVER"
echo "== Đã ghi $OUT (server: $SERVER)"
echo "Dán toàn bộ nội dung file vào secret KUBE_CONFIG của GitHub Environment tương ứng (04 §4), rồi xoá file."
