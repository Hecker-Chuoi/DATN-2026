#!/usr/bin/env bash
# Triển khai DATN-2026 lên server ứng dụng bằng docker compose. CHẠY TRÊN SERVER ỨNG DỤNG, bằng user `deployer`.
# Cài một lần (docs/cicd/cac_buoc/06):  sudo install -m 755 -o root -g root deploy.sh /home/deployer/deploy.sh
#
# Lệnh:
#   deploy <tag>   triển khai git SHA <tag>. STDIN = tar.gz chứa  docker-compose.prod.yml  prod/  keycloak/
#   rollback       về bản chạy tốt ngay trước (state/previous_tag)
#   check          in phiên bản docker + trạng thái container (dùng cho job smoke-test của Jenkins)
#
# Jenkins gửi gói theo từng lần deploy để file compose và image luôn cùng một commit:
#   tar cz -C infrastructure docker-compose.prod.yml prod keycloak | ssh -o BatchMode=yes deployer@<IP-private> "deploy <tag>"
# Khoá SSH của Jenkins đặt  command="/home/deployer/deploy.sh"  nên lệnh người gọi nằm trong $SSH_ORIGINAL_COMMAND.
# Chạy tay (ví dụ lần đầu):
#   tar cz -C infrastructure docker-compose.prod.yml prod keycloak | ssh ubuntu@<IP> "sudo -u deployer /home/deployer/deploy.sh deploy <tag>"
#
# Thư mục dữ liệu $DATN_HOME (mặc định /home/deployer/datn):
#   secrets.env      mật khẩu (tạo tay, KHÔNG đi theo gói, docs/cicd/cac_buoc/03)
#   realm.json       sinh một lần từ realm export + secrets.env (make-realm.sh)
#   releases/<tag>/  gói của từng lần deploy (giữ 5 bản gần nhất để rollback)      current → releases/<tag> đang chạy
#   state/           current_tag, previous_tag
# Biến tuỳ chọn: DATN_HOME, DEPLOY_TIMEOUT (giây, mặc định 1200), SMOKE_BASE (mặc định http://127.0.0.1:3000), SKIP_SMOKE=1,
#   SKIP_PULL=1 (dùng image có sẵn trên máy, để thử với image build local)
set -euo pipefail

# `sudo -u deployer` giữ thư mục hiện tại của người gọi (vd /home/ubuntu, deployer không vào được): find / compose báo lỗi rồi set -e thoát im lặng
cd /
DATN_HOME="${DATN_HOME:-/home/deployer/datn}"
TIMEOUT="${DEPLOY_TIMEOUT:-1200}"
SMOKE_BASE="${SMOKE_BASE:-http://127.0.0.1:3000}"
STATE="$DATN_HOME/state"; REL_ROOT="$DATN_HOME/releases"

# Lệnh đến từ SSH (khoá hạn chế) hoặc từ đối số khi chạy tay
read -r -a ARGS <<< "${SSH_ORIGINAL_COMMAND:-$*}"
CMD="${ARGS[0]:-}"; TAG="${ARGS[1]:-}"

die() { echo "❌ $*" >&2; exit 1; }
valid_tag() { [[ "${1:-}" =~ ^[0-9a-f]{7,40}$ ]]; }

# compose <tag> <thư-mục-release> <lệnh compose...>
compose() {
  local tag="$1" rel="$2"; shift 2
  DATN_HOME="$DATN_HOME" IMAGE_TAG="$tag" docker compose -p datn \
    --env-file "$rel/prod/public.env" --env-file "$DATN_HOME/secrets.env" \
    -f "$rel/docker-compose.prod.yml" "$@"
}

public_get() { sed -n "s/^$2=//p" "$1/prod/public.env" | head -1 | sed -e 's/^"//' -e 's/"$//'; }

smoke() {   # 3 service qua frontend → gateway, và trang chủ
  [ "${SKIP_SMOKE:-0}" = 1 ] && { echo "== Bỏ qua smoke test (SKIP_SMOKE=1)"; return 0; }
  local url deadline=$((SECONDS + 180))
  for url in "$SMOKE_BASE/identity/actuator/health" "$SMOKE_BASE/work/actuator/health" \
             "$SMOKE_BASE/workspace/actuator/health" "$SMOKE_BASE/"; do
    until curl -fsS -o /dev/null --max-time 10 "$url"; do
      [ $SECONDS -lt $deadline ] || { echo "Smoke test lỗi: $url"; return 1; }
      sleep 5
    done
    echo "   OK  $url"
  done
}

up() {      # up <tag> <rel>: cài/cập nhật và chờ mọi container healthy (nối bằng && vì hàm này chạy trong `if`, set -e không có tác dụng)
  local tag="$1" rel="$2"
  compose "$tag" "$rel" config -q &&
  echo "== Kéo image ($tag)" &&
  { [ "${SKIP_PULL:-0}" = 1 ] || compose "$tag" "$rel" pull --quiet; } &&
  echo "== Khởi động (chờ tối đa ${TIMEOUT}s; lần đầu trên DB trống mất 10–15 phút vì chuỗi identity → work → workspace)" &&
  compose "$tag" "$rel" up -d --remove-orphans --wait --wait-timeout "$TIMEOUT"
}

diagnose() {
  local tag="$1" rel="$2" s
  compose "$tag" "$rel" ps -a || true
  for s in $(compose "$tag" "$rel" ps --format '{{.Service}} {{.Health}}' 2>/dev/null | awk '$2!="healthy"{print $1}'); do
    echo "---- log $s (30 dòng cuối)"; compose "$tag" "$rel" logs --tail 30 "$s" || true
  done
}

mark_current() {   # mark_current <tag> <rel>
  local prev; prev="$(cat "$STATE/current_tag" 2>/dev/null || true)"
  if [ -n "$prev" ] && [ "$prev" != "$1" ]; then echo "$prev" > "$STATE/previous_tag"; fi
  echo "$1" > "$STATE/current_tag"
  ln -sfn "$2" "$DATN_HOME/current"
}

prune() {
  # Giữ 5 bản release gần nhất + bản đang chạy / bản trước; image không dùng quá 7 ngày thì xoá (giữ lại để rollback nhanh)
  local d keep1 keep2
  keep1="$(cat "$STATE/current_tag" 2>/dev/null || true)"; keep2="$(cat "$STATE/previous_tag" 2>/dev/null || true)"
  ls -1t "$REL_ROOT" 2>/dev/null | tail -n +6 | while read -r d; do
    if [ "$d" != "$keep1" ] && [ "$d" != "$keep2" ]; then rm -rf "${REL_ROOT:?}/$d"; fi
  done
  docker image prune -af --filter "until=168h" >/dev/null || true
}

case "$CMD" in
  check)
    docker version --format 'docker {{.Server.Version}}'; docker compose version
    docker ps --format 'table {{.Names}}\t{{.Status}}' --filter label=com.docker.compose.project=datn
    echo "tag đang chạy: $(cat "$STATE/current_tag" 2>/dev/null || echo '<chưa có>')"
    ;;

  deploy)
    valid_tag "$TAG" || die "Cách dùng: deploy <git-sha 7–40 ký tự hex thường> (nhận: '$TAG')"
    if [ -t 0 ]; then die "Không có gói trên stdin. Jenkins: tar cz -C infrastructure docker-compose.prod.yml prod keycloak | ssh ... \"deploy <tag>\""; fi
    [ -f "$DATN_HOME/secrets.env" ] || die "Thiếu $DATN_HOME/secrets.env (docs/cicd/cac_buoc/03)"
    mkdir -p "$STATE" "$REL_ROOT"

    REL="$REL_ROOT/$TAG"; rm -rf "$REL"; mkdir -p "$REL"
    tar xz -C "$REL" || die "Không giải nén được gói từ stdin"
    for f in docker-compose.prod.yml prod/public.env keycloak/realm-export_08082026.json; do
      [ -f "$REL/$f" ] || die "Gói thiếu $f"
    done
    # Gói tạo trên Windows có thể mang CRLF: \r dính vào giá trị trong .env làm hỏng tên miền / mật khẩu
    find "$REL" -type f \( -name '*.sh' -o -name '*.env' -o -name '*.yml' -o -name '*.yaml' \) -exec sed -i 's/\r$//' {} +
    chmod +x "$REL"/prod/postgres/*.sh "$REL"/prod/scripts/*.sh

    case "$(public_get "$REL" APP_HOST)" in ""|*.invalid) die "public.env chưa đặt APP_HOST: chạy set-public-host.sh ở máy dev rồi commit" ;; esac
    case "$(public_get "$REL" REGISTRY)" in ""|*CHANGE_ME*) die "public.env chưa đặt REGISTRY" ;; esac

    if [ ! -f "$DATN_HOME/realm.json" ]; then
      "$REL/prod/scripts/make-realm.sh" "$REL/keycloak/realm-export_08082026.json" "$DATN_HOME/realm.json" \
        "$REL/prod/public.env" "$DATN_HOME/secrets.env"
    fi

    PREV="$(cat "$STATE/current_tag" 2>/dev/null || true)"
    echo "== Deploy $TAG (đang chạy: ${PREV:-<chưa có, lần cài đầu>})"

    if up "$TAG" "$REL" && smoke; then
      mark_current "$TAG" "$REL"; prune
      echo "✅ DATN-2026@$TAG đã chạy ($(public_get "$REL" SCHEME)://$(public_get "$REL" APP_HOST))"
    else
      echo "❌ Deploy $TAG lỗi — chẩn đoán:"; diagnose "$TAG" "$REL"
      if [ -n "$PREV" ] && [ -d "$REL_ROOT/$PREV" ]; then
        echo "== Rollback về $PREV (lưu ý: migration DB đã chạy KHÔNG được hoàn tác)"
        if up "$PREV" "$REL_ROOT/$PREV"; then echo "↩️  Đã về $PREV"; else echo "Rollback cũng lỗi: kiểm tra tay"; fi
      fi
      exit 1
    fi
    ;;

  rollback)
    PREV="$(cat "$STATE/previous_tag" 2>/dev/null || true)"
    if ! valid_tag "$PREV" || [ ! -d "$REL_ROOT/$PREV" ]; then die "Không có bản trước để rollback (state/previous_tag)"; fi
    echo "== Rollback về $PREV (migration DB KHÔNG được hoàn tác)"
    up "$PREV" "$REL_ROOT/$PREV"
    smoke
    CUR="$(cat "$STATE/current_tag")"; echo "$CUR" > "$STATE/previous_tag"; echo "$PREV" > "$STATE/current_tag"
    ln -sfn "$REL_ROOT/$PREV" "$DATN_HOME/current"
    echo "✅ Đang chạy $PREV"
    ;;

  *) die "Lệnh không hợp lệ: '${CMD}'. Dùng: deploy <tag> | rollback | check" ;;
esac
