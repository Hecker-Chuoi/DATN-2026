#!/usr/bin/env bash
# Dựng kubeconfig CHỈ có quyền của SA ci-deployer trong 1 namespace (03 §10). Chạy bằng kubeconfig admin.
# Cách dùng: make-ci-kubeconfig.sh <namespace>     ->  tạo file kubeconfig-<namespace> ở thư mục hiện tại
set -euo pipefail
NS="${1:?Cách dùng: $0 <namespace>}"
OUT="kubeconfig-${NS}"

SERVER=$(kubectl config view --minify -o jsonpath='{.clusters[0].cluster.server}')
CA=$(kubectl config view --raw --flatten --minify -o jsonpath='{.clusters[0].cluster.certificate-authority-data}')
TOKEN=$(kubectl -n "$NS" get secret ci-deployer-token -o jsonpath='{.data.token}' | base64 -d)
[ -n "$TOKEN" ] || { echo "Secret ci-deployer-token chưa có token (đã cài datn-platform vào $NS chưa?)"; exit 1; }

umask 077
cat > "$OUT" <<EOF
apiVersion: v1
kind: Config
clusters:
  - name: minikube
    cluster:
      server: ${SERVER}
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

echo "== Kiểm tra quyền (kỳ vọng: yes yes yes no no no)"
for check in "patch deployments" "delete configmaps" "get replicasets" "get secrets" "delete statefulsets" "patch persistentvolumeclaims"; do
  # shellcheck disable=SC2086  # cố ý tách "động từ tài nguyên"
  printf '%-30s ' "$check"; KUBECONFIG="$OUT" kubectl auth can-i $check -n "$NS" || true
done
printf '%-30s ' "namespace default (kỳ vọng no)"; KUBECONFIG="$OUT" kubectl auth can-i patch deployments -n default || true
echo "== Helm (driver configmap) thấy release:"
KUBECONFIG="$OUT" HELM_DRIVER=configmap helm -n "$NS" list || true
echo "Tải $OUT lên Jenkins làm credential 'Secret file' (04 §10.3), rồi xoá file."
