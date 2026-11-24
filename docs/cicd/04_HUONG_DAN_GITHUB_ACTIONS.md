# 04 — Cấu hình CI/CD bằng GitHub Actions

> CI/CD của DATN-2026 chạy **hoàn toàn trên runner của GitHub**. Máy chủ **chỉ chạy dự án**: không cài Jenkins, không cài runner, không cần Docker cho CI. Thứ duy nhất thêm vào máy chủ là **một user `tunnel`** cho bước deploy đi qua.
> Đọc kèm: [01 §4](01_NGHIEN_CUU_CICD_KUBERNETES.md) (vì sao chọn cách này), [03 §12](03_HUONG_DAN_TRIEN_KHAI.md) (nội dung workflow và script), [05](05_TRIEN_KHAI_TU_DAU_TREN_EC2.md) (thứ tự làm trên EC2).
> Làm tài liệu này **sau khi** cluster và chart `datn-platform` đã chạy trên máy chủ (03 §1–§6 hoặc 05 phần B).

## Mục lục
0. [Tổng quan](#0-tổng-quan)
1. [Chuẩn bị repo](#1-chuẩn-bị-repo)
2. [Máy chủ: user `tunnel` và khoá SSH](#2-máy-chủ-user-tunnel-và-khoá-ssh)
3. [Kubeconfig của `ci-deployer`](#3-kubeconfig-của-ci-deployer)
4. [GitHub: Environments, secret, biến](#4-github-environments-secret-biến)
5. [Bảo vệ nhánh `main`](#5-bảo-vệ-nhánh-main)
6. [Chạy lần đầu](#6-chạy-lần-đầu)
7. [Kiểm tra bảo mật](#7-kiểm-tra-bảo-mật)
8. [Xử lý sự cố](#8-xử-lý-sự-cố)
9. [Phụ lục: gỡ Jenkins đã cài theo bản trước](#9-phụ-lục-gỡ-jenkins-đã-cài-theo-bản-trước)

---

## 0. Tổng quan

```
 git push / PR / tag
        │
        ▼
 GitHub Actions (runner ubuntu-latest của GitHub)
   ci-cd.yml        PR, main : Backend · Frontend · Helm chart
                    main     : Image x6 (song song) → GHCR (GITHUB_TOKEN) → deploy.yml (staging)
   release.yml      tag v*   : gắn tag phiên bản cho image → deploy.yml (production, CHỜ DUYỆT)
   deploy-manual.yml  tay    : deploy SHA cũ / helm rollback
   deploy.yml       reusable : ssh -L 127.0.0.1:8443:192.168.49.2:8443 tunnel@<máy chủ>
                               helm upgrade bằng secret KUBE_CONFIG (SA ci-deployer) của Environment
        │ SSH :22
        ▼
 Máy chủ: user tunnel (không shell; khoá chỉ mở được đúng 192.168.49.2:8443)
        │
        ▼
 minikube API server ── RBAC: ci-deployer chỉ có quyền trong 1 namespace
```

| Thành phần | Ở đâu | Bảo vệ |
|---|---|---|
| Build, test, Trivy, push image | Runner của GitHub | `GITHUB_TOKEN` chỉ `packages: write` ở job build |
| Khoá SSH `DEPLOY_SSH_KEY` | Secret của repo | Phía máy chủ chỉ cho port-forward tới API server (§2) |
| Kubeconfig `KUBE_CONFIG` | Secret của **Environment** `staging` / `production` | Luật nhánh; `production` cần người duyệt |
| Quyền trong cluster | SA `ci-deployer` (chart `datn-platform`) | Chỉ Deployment/Service/ConfigMap/Ingress trong 1 namespace; không đọc Secret |

---

## 1. Chuẩn bị repo

### 1.1. Chuyển repo sang public

Environments có duyệt tay (*required reviewers*) và secret riêng cho prod chỉ miễn phí với repo **public**. Repo public cũng không giới hạn phút chạy runner chuẩn.

**Trước khi chuyển**, mọi thứ trong lịch sử Git sẽ công khai:

| Việc | Lý do |
|---|---|
| Tạo **client secret Google mới** trên Google Cloud Console; secret cũ coi như đã lộ | `infrastructure/keycloak/realm-export_08082026.json` chứa client secret Google thật |
| Kiểm tra không có file `.env`, khoá `.pem`, `secret/` nào từng được commit: `git log --all --oneline -- '*.env' '*.pem' 'secret/*'` | Lịch sử Git không tự mất khi xoá file |
| Mật khẩu mặc định trong `application.yaml` (`123456`, `admin`, client secret Keycloak dev) | Chỉ dùng cho máy dev; môi trường thật lấy từ Secret của Kubernetes, không bị ảnh hưởng |

Chuyển: repo → **Settings → General → Danger Zone → Change repository visibility → Public**.

### 1.2. Cài đặt Actions

Repo → **Settings → Actions → General**:

| Mục | Giá trị |
|---|---|
| Actions permissions | *Allow all actions and reusable workflows* |
| Approval for running fork pull request workflows | **Require approval for all external contributors** (PR từ người ngoài phải được bấm duyệt mới chạy) |
| Workflow permissions | **Read repository contents and packages permissions** (mặc định). Job nào cần ghi (push GHCR) tự khai báo `packages: write` |
| Allow GitHub Actions to create and approve pull requests | Bỏ tick |

---

## 2. Máy chủ: user `tunnel` và khoá SSH

Làm trên máy chủ bằng user quản trị (`datnadmin`). Cluster phải đang chạy (`minikube ip` trả về IP).

### 2.1. Tạo user và khoá

```bash
API="$(minikube ip):8443"                     # thường 192.168.49.2:8443
echo "$API"

# User hệ thống, không shell đăng nhập
sudo adduser --system --group --shell /usr/sbin/nologin --home /home/tunnel tunnel
sudo usermod -p '*' tunnel                     # không có mật khẩu, nhưng không bị sshd coi là tài khoản "bị khoá"

# Cặp khoá riêng cho GitHub Actions (không dùng lại khoá cá nhân)
ssh-keygen -t ed25519 -N "" -C "github-actions-deploy" -f ~/gh-deploy-key

# Khoá CHỈ được: port-forward tới đúng API server; không shell, không lệnh, không agent/X11/PTY
sudo install -d -m 700 -o tunnel -g tunnel /home/tunnel/.ssh
echo "restrict,port-forwarding,permitopen=\"$API\",command=\"/usr/sbin/nologin\" $(cat ~/gh-deploy-key.pub)" \
  | sudo tee /home/tunnel/.ssh/authorized_keys > /dev/null
sudo chown tunnel:tunnel /home/tunnel/.ssh/authorized_keys
sudo chmod 600 /home/tunnel/.ssh/authorized_keys
sudo cat /home/tunnel/.ssh/authorized_keys     # 1 dòng, bắt đầu bằng restrict,port-forwarding,permitopen="192.168.49.2:8443"
```

Lớp thứ hai, ở cấu hình sshd: `port-forwarding` trong `authorized_keys` bật lại **cả** chiều ngược (`ssh -R`, mở cổng lắng nghe trên máy chủ), còn `permitopen` chỉ giới hạn chiều xuôi. Khối `Match` dưới đây chặn hẳn chiều ngược, và vẫn giữ các giới hạn kia kể cả khi ai đó sửa nhầm `authorized_keys`:

```bash
cat <<EOF | sudo tee /etc/ssh/sshd_config.d/60-tunnel.conf > /dev/null
# User tunnel (GitHub Actions deploy): CHỈ port-forward chiều đi tới API minikube
Match User tunnel
    AllowTcpForwarding local
    PermitOpen $API
    PermitListen none
    X11Forwarding no
    AllowAgentForwarding no
    PermitTTY no
    ForceCommand /usr/sbin/nologin
EOF
sudo sshd -t && sudo systemctl reload ssh

# Khối Match chỉ áp cho user tunnel, user khác giữ nguyên:
sudo sshd -T -C user=tunnel,host=x,addr=203.0.113.9    | grep -E "^(allowtcpforwarding|permitopen|permitlisten|forcecommand) "
# allowtcpforwarding local / forcecommand /usr/sbin/nologin / permitopen 192.168.49.2:8443 / permitlisten none
sudo sshd -T -C user=datnadmin,host=x,addr=203.0.113.9 | grep -E "^(allowtcpforwarding|forcecommand) "
# allowtcpforwarding yes / forcecommand none
```

> `minikube ip` đổi (hiếm gặp, ví dụ sau `minikube delete`) thì phải sửa `permitopen` trong `authorized_keys`, `PermitOpen` trong `60-tunnel.conf`, và biến `MINIKUBE_API` (§4.3).
>
> Toàn bộ §2.1–§2.2 và bước đường hầm của `deploy.yml` đã chạy thử trên Ubuntu 24.04 + cluster thật (24/11/2026): 11/11 kiểm tra đạt (đường hầm, TLS `127.0.0.1`, quyền `ci-deployer`, chặn shell, chặn lệnh, chặn đích khác, chặn `-R`).

### 2.2. Kiểm tra ngay trên máy chủ

```bash
# 1) Mở shell: phải bị từ chối
ssh -i ~/gh-deploy-key -o StrictHostKeyChecking=accept-new tunnel@127.0.0.1
# -> This account is currently not available.

# 2) Đường hầm tới API server: phải chạy (curl trả 401/403 JSON của Kubernetes là ĐÚNG, vì chưa gửi token)
ssh -i ~/gh-deploy-key -f -N -o ExitOnForwardFailure=yes -L 127.0.0.1:18443:$API tunnel@127.0.0.1
curl -sk https://127.0.0.1:18443/version | head -3
pkill -f "127.0.0.1:18443:"

# 3) Đường hầm tới đích khác: phải bị từ chối
ssh -i ~/gh-deploy-key -N -L 127.0.0.1:18022:127.0.0.1:22 tunnel@127.0.0.1 &
sleep 2; timeout 5 bash -c 'exec 3<>/dev/tcp/127.0.0.1/18022; head -c 20 <&3'; echo
# -> KHÔNG ra "SSH-2.0-…"; terminal hiện: channel 1: open failed: administratively prohibited: open failed
kill %1

# 4) Mở cổng ngược trên máy chủ: phải bị từ chối
timeout 5 ssh -i ~/gh-deploy-key -o ExitOnForwardFailure=yes -N -R 12000:127.0.0.1:22 tunnel@127.0.0.1
# -> Error: remote port forwarding failed for listen port 12000
```

### 2.3. Lấy giá trị cho GitHub

```bash
EIP=<IP công khai của máy chủ>                        # ví dụ 52.62.135.59
cat ~/gh-deploy-key                                    # -> secret DEPLOY_SSH_KEY (toàn bộ, gồm dòng BEGIN/END)
echo "$EIP $(cut -d' ' -f1-2 /etc/ssh/ssh_host_ed25519_key.pub)"   # -> biến DEPLOY_SSH_KNOWN_HOSTS
```

Dán xong vào GitHub (§4) thì xoá khoá riêng khỏi máy chủ: `shred -u ~/gh-deploy-key && rm ~/gh-deploy-key.pub`. Cần thay khoá: làm lại §2.1 (dòng `authorized_keys` bị ghi đè) và cập nhật secret.

### 2.4. Cổng SSH

Runner của GitHub không có IP cố định, nên **Security Group phải mở cổng 22 cho `0.0.0.0/0`**. Bù lại:
- Ubuntu trên EC2 mặc định chỉ cho đăng nhập bằng khoá (`PasswordAuthentication no`); kiểm tra: `sudo sshd -T | grep -E "passwordauthentication|kbdinteractiveauthentication"` → cả hai `no`.
- Tuỳ chọn chặn dò khoá: `sudo apt-get install -y fail2ban` (cấu hình mặc định đã bảo vệ `sshd`).

---

## 3. Kubeconfig của `ci-deployer`

Trên máy chủ (user `datnadmin`, kubeconfig admin):

```bash
cd ~
bash /opt/datn/infrastructure/scripts/make-ci-kubeconfig.sh datn-staging https://127.0.0.1:8443
bash /opt/datn/infrastructure/scripts/make-ci-kubeconfig.sh datn-prod    https://127.0.0.1:8443
```

Mỗi lần chạy in bảng quyền, kỳ vọng `yes yes yes no no no` và `no` ở namespace khác. File ghi ra trỏ tới `https://127.0.0.1:8443` (đầu ra của đường hầm trên runner; chứng chỉ API minikube có sẵn IP `127.0.0.1`).

```bash
cat kubeconfig-datn-staging     # -> secret KUBE_CONFIG của Environment staging
cat kubeconfig-datn-prod        # -> secret KUBE_CONFIG của Environment production
rm kubeconfig-datn-*            # dán xong thì xoá
```

**Xoay token** khi nghi bị lộ: `kubectl -n datn-staging delete secret ci-deployer-token`, chạy lại `upgrade-platform.sh staging` (05 bước 8.5) để tạo lại Secret, chạy lại lệnh trên, cập nhật `KUBE_CONFIG`.

---

## 4. GitHub: Environments, secret, biến

### 4.1. Environment `staging`

Repo → **Settings → Environments → New environment** → `staging`:

| Mục | Giá trị |
|---|---|
| Deployment branches and tags | **Selected branches and tags** → thêm rule nhánh `main` |
| Environment secrets | `KUBE_CONFIG` = nội dung `kubeconfig-datn-staging` |

### 4.2. Environment `production`

**New environment** → `production`:

| Mục | Giá trị |
|---|---|
| Required reviewers | Tick, thêm trưởng nhóm (tối đa 6 người). Ít nhất 1 người duyệt là đủ |
| Prevent self-review | Tick (người kích hoạt không tự duyệt được) |
| Deployment branches and tags | **Selected branches and tags** → rule nhánh `main` **và** rule tag `v*` |
| Environment secrets | `KUBE_CONFIG` = nội dung `kubeconfig-datn-prod` |

Nhờ luật nhánh, workflow chạy từ nhánh khác (kể cả nhánh bị sửa workflow) không vào được Environment nên không đọc được `KUBE_CONFIG`.

### 4.3. Secret và biến của repo

Repo → **Settings → Secrets and variables → Actions**:

| Tab | Tên | Giá trị |
|---|---|---|
| Secrets | `DEPLOY_SSH_KEY` | Khoá riêng `~/gh-deploy-key` (§2.3) |
| Variables | `DEPLOY_SSH_HOST` | IP công khai của máy chủ (Elastic IP) |
| Variables | `DEPLOY_SSH_KNOWN_HOSTS` | Dòng `<IP> ssh-ed25519 AAAA…` (§2.3): chống giả mạo máy chủ |
| Variables *(tuỳ chọn)* | `MINIKUBE_API` | Chỉ khi `minikube ip` khác `192.168.49.2`: ví dụ `192.168.58.2:8443` |

Không cần tạo token cho GHCR: workflow dùng `GITHUB_TOKEN` có sẵn. Secret `ghcr-pull` trong cluster (PAT `read:packages`, 03 §6.3) vẫn cần để cluster kéo image private.

---

## 5. Bảo vệ nhánh `main`

Repo → **Settings → Rules → Rulesets → New ruleset → New branch ruleset**:

| Mục | Giá trị |
|---|---|
| Ruleset name / Enforcement | `main` / **Active** |
| Target branches | *Include default branch* |
| Restrict deletions, Block force pushes | Tick |
| Require a pull request before merging | Tick, *Required approvals* = 1 |
| Require status checks to pass | Tick → **Add checks**: `Backend`, `Frontend`, `Helm chart` (chỉ hiện sau khi workflow chạy ít nhất một lần, ví dụ trên một PR) |

Tuỳ chọn: thêm **tag ruleset** cho `v*` (*Restrict creations* + bypass cho trưởng nhóm) để chỉ trưởng nhóm tạo được tag phát hành.

---

## 6. Chạy lần đầu

### 6.1. Staging

`datn-app` **không cần cài bằng tay**: lần chạy đầu `deploy.sh` tự `helm upgrade --install` bằng quyền `ci-deployer`.

1. Merge một PR vào `main` (hoặc push thẳng nếu chưa bật ruleset). Trang **Actions → CI/CD** chạy: `Backend`, `Frontend`, `Helm chart` → 6 job `Image (…)` song song → `Deploy staging`.
2. Lần đầu mất 10–20 phút (Maven, npm, Trivy tải dữ liệu; các lần sau có cache).
3. Job `Deploy staging / deploy staging` kết thúc bằng `✅ datn-app@<sha> đã chạy trên datn-staging`; trang Environments có link tới URL staging.
4. GitHub → hồ sơ (hoặc repo) → **Packages**: 6 package `datn-*`. Mở từng package → **Package settings**: giữ **Private**; mục *Manage Actions access* phải có repo `DATN-2026` (quyền Write). Package tạo bởi workflow tự có sẵn.

### 6.2. Prod

```bash
# máy dev, khi staging đã ổn với commit cần phát hành (workflow CI/CD của commit đó phải xanh)
git checkout main && git pull
git tag -a v1.0.0 -m "Phát hành v1.0.0" && git push origin v1.0.0
```

**Actions → Release**: job `Gắn tag phiên bản cho image` chạy, rồi `Deploy production` hiện **Waiting**. Người có trong *Required reviewers* bấm **Review deployments → production → Approve and deploy**.

### 6.3. Deploy lại, rollback

**Actions → Deploy thủ công → Run workflow** (nhánh `main`):

| `target` | `action` | Điền | Kết quả |
|---|---|---|---|
| staging / production | `deploy` | `image_tag` = git SHA 40 ký tự | Cài image cũ với chart hiện tại của `main` |
| staging / production | `rollback` | `revision` = số (xem `helm history`), hoặc để trống | `helm rollback` cả chart + image về revision đó (trống = ngay trước) |

`production` luôn qua bước duyệt. Hai lần deploy cùng môi trường xếp hàng, không chạy song song.

---

## 7. Kiểm tra bảo mật

| # | Hành động | Kỳ vọng |
|---|---|---|
| G1 | Mở PR | Có 3 check `Backend`, `Frontend`, `Helm chart`; **không** có job `Image`/`Deploy` |
| G2 | Merge PR | `Deploy staging` chạy xong, smoke test xanh |
| G3 | Push tag `v1.0.0` | `Deploy production` chờ duyệt; người không thuộc *Required reviewers* không thấy nút duyệt |
| G4 | Trên một nhánh thử, thêm job có `environment: production` rồi push | Job lỗi: nhánh không được phép deploy tới `production` (luật nhánh), không đọc được `KUBE_CONFIG` |
| G5 | Dùng `DEPLOY_SSH_KEY`: `ssh tunnel@<máy chủ>` | `This account is currently not available.` |
| G6 | Dùng `DEPLOY_SSH_KEY`: `ssh -N -L 15432:127.0.0.1:22 tunnel@<máy chủ> &` rồi kết nối cổng 15432; thử thêm `ssh -N -R 12000:127.0.0.1:22 …` | `administratively prohibited`; `remote port forwarding failed`. Log máy chủ (`journalctl -u ssh`): `…but the request was denied` |
| G7 | Dùng `KUBE_CONFIG` của staging (qua đường hầm): `kubectl auth can-i get secrets -n datn-staging`; `… patch deployments -n datn-prod` | `no`; `no` |

---

## 8. Xử lý sự cố

| Triệu chứng (log của job) | Nguyên nhân | Cách xử lý |
|---|---|---|
| `Thiếu secret DEPLOY_SSH_KEY hoặc biến DEPLOY_SSH_KNOWN_HOSTS / DEPLOY_SSH_HOST` | Chưa tạo, hoặc tạo nhầm tab Variables/Secrets | §4.3 |
| `Environment 'staging' thiếu secret KUBE_CONFIG` | Secret đặt ở repo thay vì ở Environment, hoặc sai tên | §4.1, §4.2: tên đúng `KUBE_CONFIG` trong Environment |
| `Host key verification failed` | `DEPLOY_SSH_KNOWN_HOSTS` sai hoặc máy chủ đã tạo lại | Lấy lại theo §2.3 |
| `Permission denied (publickey)` | Sai khoá, hoặc `authorized_keys` sai quyền/chủ sở hữu | `sudo ls -la /home/tunnel/.ssh`; làm lại §2.1; `sudo journalctl -u ssh -n 50` |
| `ssh: connect to host … port 22: Connection timed out` | Security Group chưa mở 22 cho `0.0.0.0/0` | §2.4 |
| `channel … open failed: administratively prohibited` | `permitopen` / `PermitOpen` khác đích mà workflow mở | `minikube ip` đổi: sửa `authorized_keys`, `60-tunnel.conf` (§2.1) và biến `MINIKUBE_API` |
| `x509: certificate is valid for …, not 127.0.0.1` | Kubeconfig trỏ sai địa chỉ | Chạy lại §3 với tham số `https://127.0.0.1:8443` |
| `Unauthorized` | Token `ci-deployer` đã xoay | §3 (xoay token) |
| `ci-deployer không có quyền trên datn-…` | Kubeconfig của namespace này dán nhầm sang Environment kia | Dán đúng file cho đúng Environment |
| `Branch "…" is not allowed to deploy to production due to environment protection rules` | Chạy *Deploy thủ công* từ nhánh khác `main` | Chọn nhánh `main` khi Run workflow |
| `denied: installation not allowed to Write organization package` / `permission_denied: write_package` | Package `datn-*` đã tạo trước đó bằng PAT, chưa cấp quyền cho repo | Package settings → *Manage Actions access* → Add repository `DATN-2026` (Write) |
| `Chưa có ghcr.io/…:<sha>` ở workflow Release | Tag trỏ vào commit chưa qua workflow CI/CD trên `main`, hoặc CI/CD chưa chạy xong | Chờ CI/CD của commit đó xanh rồi *Re-run jobs*; hoặc tag lại đúng commit |
| Smoke test `không phải health JSON UP` | Route `/identity…` rơi về frontend | 03 §14.2 |
| Job deploy chờ mãi ở *Waiting* | Environment `production` cần duyệt | §6.2 |
| `another operation (install/upgrade/rollback) is in progress` | Lần deploy trước bị huỷ giữa chừng | *Deploy thủ công* → `rollback` về revision tốt; chi tiết 03 §14.2 |

Các sự cố về Kubernetes, Helm, Keycloak: [03 §14.2](03_HUONG_DAN_TRIEN_KHAI.md).

---

## 9. Phụ lục: gỡ Jenkins đã cài theo bản trước

Nếu đã cài Jenkins trên máy chủ theo bản cũ của tài liệu, gỡ cho máy chủ chỉ còn dự án (giữ nguyên Docker Engine: minikube cần nó):

```bash
sudo systemctl disable --now jenkins
sudo machinectl shell jenkins@ /usr/bin/systemctl --user disable --now docker 2>/dev/null || true
sudo loginctl disable-linger jenkins
sudo apt-get purge -y jenkins && sudo apt-get autoremove -y
sudo rm -f /etc/apt/sources.list.d/jenkins.list /etc/apt/keyrings/jenkins-keyring.asc
sudo rm -f /etc/nginx/sites-enabled/jenkins /etc/nginx/sites-available/jenkins && sudo nginx -t && sudo systemctl reload nginx
sudo rm -f /etc/apparmor.d/usr.bin.rootlesskit && sudo systemctl restart apparmor
sudo pkill -u jenkins || true               # dừng tiến trình còn sót (daemon rootless), nếu không deluser báo "user is currently used by process"
sudo deluser --remove-home jenkins          # xoá cả /var/lib/jenkins (cấu hình, Docker rootless, cache)
sudo sed -i '/^jenkins:/d' /etc/subuid /etc/subgid
```

Công cụ đã cài ở `/usr/local/bin` (`kubectl`, `helm`, `kubeconform`) và Trivy vẫn dùng được cho quản trị viên; giữ hay gỡ đều được.
