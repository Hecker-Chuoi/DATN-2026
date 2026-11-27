#!/usr/bin/env bash
# Dựng kubeconfig CHỈ có quyền của ServiceAccount ci-deployer trong một namespace, cho user `deployer` trên datn-minikube.
# Chạy bằng user ubuntu (kubeconfig admin), SAU khi đã `kubectl apply -f infrastructure/k8s/rbac-deployer.yaml`:
#   bash infrastructure/k8s/make-ci-kubeconfig.sh [namespace]          # mặc định: datn
# Kết quả: file ./kubeconfig-<namespace> (quyền 600). Cài cho deployer (docs/cicd/cac_buoc/04, bước 6):
#   sudo install -d -m 700 -o deployer -g deployer /home/deployer/.kube
#   sudo install -m 600 -o deployer -g deployer kubeconfig-datn /home/deployer/.kube/config ; rm kubeconfig-datn
# Địa chỉ API server ghi vào file là địa chỉ THẬT của cụm (https://192.168.49.2:8443): deployer chạy trên cùng máy nên tới được.
set -euo pipefail

NS="${1:-datn}"
OUT="kubeconfig-${NS}"

kubectl -n "$NS" get serviceaccount ci-deployer >/dev/null 2>&1 \
  || { echo "Chưa có ServiceAccount ci-deployer trong $NS. Chạy: kubectl apply -f infrastructure/k8s/rbac-deployer.yaml"; exit 1; }

SERVER=$(kubectl config view --minify -o jsonpath='{.clusters[0].cluster.server}')
CA=$(kubectl config view --raw --flatten --minify -o jsonpath='{.clusters[0].cluster.certificate-authority-data}')
TOKEN=$(kubectl -n "$NS" get secret ci-deployer-token -o jsonpath='{.data.token}' | base64 -d)
[ -n "$TOKEN" ] || { echo "Secret ci-deployer-token chưa có token (đợi vài giây rồi chạy lại)"; exit 1; }
[ -n "$CA" ] || { echo "Không đọc được certificate-authority-data từ kubeconfig hiện tại"; exit 1; }

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

echo "== Kiểm tra quyền của kubeconfig vừa tạo (kỳ vọng: yes yes yes | no no no no no no)"
check() {  # check <kỳ vọng yes|no> <động từ> <tài nguyên> [namespace]
  local want=$1 verb=$2 res=$3 ns=${4:-$NS} got
  got=$(KUBECONFIG="$OUT" kubectl auth can-i "$verb" "$res" -n "$ns" 2>/dev/null || true)
  printf '  %-4s %-28s ns=%-10s %s\n' "$got" "$verb $res" "$ns" "$([ "$got" = "$want" ] && echo OK || echo "!!! KỲ VỌNG $want")"
  [ "$got" = "$want" ]
}
ok=1
check yes patch deployments        || ok=0
check yes delete configmaps        || ok=0
check yes get replicasets          || ok=0
check no  get secrets              || ok=0
check no  patch statefulsets       || ok=0
check no  delete persistentvolumeclaims || ok=0
check no  create rolebindings      || ok=0
check no  patch deployments default || ok=0
[ "$ok" = 1 ] || { echo "Quyền không đúng kỳ vọng, KHÔNG dùng file $OUT. Kiểm tra rbac-deployer.yaml."; rm -f "$OUT"; exit 1; }

echo "== Đã ghi $OUT (server: $SERVER). Cài cho user deployer rồi xoá file này."
