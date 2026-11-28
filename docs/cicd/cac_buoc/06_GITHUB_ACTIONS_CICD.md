# 06 — CI/CD bằng GitHub Actions

> Thuộc [00 — Danh sách các bước](00_DANH_SACH_CAC_BUOC.md) (**bước 5–6**). Làm **sau** [05](05_TRIEN_KHAI_LAN_DAU.md): ứng dụng đã chạy bằng tay qua `deploy.sh`.
>
> Kết quả: PR chạy test; push / merge vào `main` → test → build + push 7 image → SSH sang server chạy `deploy.sh` → kiểm tra qua HTTPS; lỗi thì tự về bản trước. Workflow ở [`.github/workflows/ci-cd.yml`](../../../.github/workflows/ci-cd.yml).
>
> Quy ước: 🪟 = Git Bash trên **máy Windows** (ở `D:\DATN-2026`); 🐧 = phiên **SSH vào server ứng dụng** (`ubuntu@datn-app:~$`); 🌐 = bấm trên web (GitHub / AWS). **Đừng dán lệnh Linux vào PowerShell và ngược lại.**

## Mục lục

1. [Mô hình](#1-mô-hình)
2. [Khoá SSH riêng cho Actions](#2-khoá-ssh-riêng-cho-actions)
3. [Cho khoá vào `deployer` trên server (🐧)](#3-cho-khoá-vào-deployer-trên-server-)
4. [Mở cổng 22 trên Security Group](#4-mở-cổng-22-trên-security-group)
5. [Tạo secret trên GitHub](#5-tạo-secret-trên-github)
6. [Thời gian và hạn mức](#6-thời-gian-và-hạn-mức)
7. [Workflow làm gì](#7-workflow-làm-gì)
8. [Tuỳ chọn: duyệt tay trước khi deploy, bảo vệ `main`](#8-tuỳ-chọn-duyệt-tay-trước-khi-deploy-bảo-vệ-main)
9. [Kịch bản kiểm tra](#9-kịch-bản-kiểm-tra)
10. [Xử lý sự cố](#10-xử-lý-sự-cố)
11. [Checklist](#11-checklist)

---

## 1. Mô hình

```
 git push / PR ─► GitHub Actions (runner ubuntu-latest, IP thay đổi)
                    ├─ job backend   : mvn verify (JDK 21)
                    ├─ job frontend  : npm ci, lint (không chặn), build
                    └─ job deploy (chỉ main hoặc chạy tay có tick "deploy"):
                         1. docker login Docker Hub (secret DOCKERHUB_*)
                         2. build-push.sh <sha7>   ─► Docker Hub (7 image)
                         3. tar cz (compose + prod/ + keycloak/) | ssh deployer@<IP public> "deploy <sha7>"
                                                                   └─► server: deploy.sh → pull + up -d --wait + smoke test
                         4. curl https://app.<domain>/identity/actuator/health
```

- Runner của GitHub vào server bằng **IP public** (`DEPLOY_HOST`) và cổng 22.
- Khoá SSH bị **khoá chặt** bằng `command="/home/deployer/deploy.sh"` trong `authorized_keys`: khoá lộ thì kẻ tấn công chỉ chạy được `deploy <sha>`, `rollback`, `check`, không mở được shell. `deployer` thuộc nhóm `docker` (≈ root) nhưng không có `sudo`.
- `deploy.sh` thuộc **root** (`/home/deployer/deploy.sh`, cài ở [05 §3](05_TRIEN_KHAI_LAN_DAU.md#3-cài-deploysh-lên-server)): CI **không** tự cập nhật được nó (cố ý). File compose, cấu hình Spring, realm thì đi theo gói mỗi lần deploy.

---

## 2. Khoá SSH riêng cho Actions

Khoá này **chỉ** dùng cho Actions, để thu hồi độc lập. 🪟 Git Bash ở `D:\DATN-2026` (thư mục `secret/` đã nằm trong `.gitignore`):

```bash
mkdir -p secret
ssh-keygen -t ed25519 -N "" -C "github-actions-deploy" -f secret/actions-deploy-key
git check-ignore -v secret/actions-deploy-key           # phải in ra dòng ".gitignore:...:secret/" → an toàn

# Dấu vân tay server (để workflow chỉ tin đúng server này). Điền Elastic IP:
ssh-keyscan -T 10 <ELASTIC_IP> 2>/dev/null | grep -v '^#' > secret/deploy-known-hosts.txt
```

Sẽ có 3 file: `actions-deploy-key` (**khoá riêng**, dán vào secret), `actions-deploy-key.pub` (khoá công khai, đưa lên server), `deploy-known-hosts.txt`.

---

## 3. Cho khoá vào `deployer` trên server (🐧)

Dòng `authorized_keys` có tiền tố `command="..."` (khoá chặt). Dòng dưới **ghi đè** file, nên khoá cũ (ví dụ khoá Jenkins) bị thu hồi luôn. 🐧 trên server (thay `<NỘI DUNG actions-deploy-key.pub>` bằng cả dòng `ssh-ed25519 AAAA... github-actions-deploy`):

```bash
PUBKEY="<NỘI DUNG actions-deploy-key.pub>"
sudo -u deployer install -d -m 700 /home/deployer/.ssh
echo "command=\"/home/deployer/deploy.sh\",no-port-forwarding,no-agent-forwarding,no-X11-forwarding,no-pty $PUBKEY" \
  | sudo -u deployer tee /home/deployer/.ssh/authorized_keys > /dev/null
sudo chmod 600 /home/deployer/.ssh/authorized_keys
sudo wc -l /home/deployer/.ssh/authorized_keys        # 1 dòng
```

Kiểm tra từ **máy Windows** (🪟 Git Bash, đúng như Actions sẽ làm: IP public, kiểm tra dấu vân tay nghiêm ngặt):

```bash
ssh -i secret/actions-deploy-key -o BatchMode=yes -o UserKnownHostsFile=secret/deploy-known-hosts.txt -o StrictHostKeyChecking=yes \
  deployer@<ELASTIC_IP> check                        # in phiên bản Docker, danh sách container, "tag đang chạy: <sha>"
ssh -i secret/actions-deploy-key -o BatchMode=yes -o UserKnownHostsFile=secret/deploy-known-hosts.txt \
  deployer@<ELASTIC_IP> "ls /"                       # ❌ Lệnh không hợp lệ: 'ls' (chỉ in từ đầu tiên)
```

`deploy.sh` chỉ chấp nhận 3 lệnh: `deploy <sha>` (kèm gói `tar.gz` trên stdin; `<sha>` là 7–40 ký tự hex thường), `rollback`, `check`.

---

## 4. Mở cổng 22 trên Security Group

Runner của GitHub có IP **thay đổi** (hàng nghìn dải), không khoanh vùng thực tế được, nên cổng 22 của server phải mở cho `0.0.0.0/0`.

🌐 AWS Console → EC2 → server ứng dụng → tab **Security** → Security Group → **Edit inbound rules** → thêm:

| Type | Port | Source |
|---|---|---|
| SSH | 22 | `0.0.0.0/0` |

(Giữ nguyên các rule 80 / 443. Có thể xoá rule 22 từ Security Group của Jenkins cũ nếu còn.)

Rủi ro chấp nhận được vì: Ubuntu EC2 **chỉ cho khoá** (tắt đăng nhập bằng mật khẩu: kiểm tra `sudo sshd -T | grep passwordauthentication` → `no`), khoá của `deployer` chỉ chạy được `deploy.sh`, tài khoản `ubuntu` cần file `.pem`. Dù vậy sẽ có người dò cổng 22 (log `auth.log` đầy dòng `Invalid user`); nếu muốn bớt, cài `fail2ban`: `sudo apt-get install -y fail2ban`.

---

## 5. Tạo secret trên GitHub

🌐 `https://github.com/<chủ-repo>/<repo>/settings/secrets/actions` → **New repository secret** (cần quyền admin):

| Tên | Giá trị |
|---|---|
| `DOCKERHUB_USERNAME` | tên tài khoản Docker Hub (viết thường) |
| `DOCKERHUB_TOKEN` | Access Token Read & Write ([01 §2](01_CHUAN_BI_TAI_KHOAN.md#2-docker-hub-tài-khoản-và-access-token)) |
| `DEPLOY_SSH_KEY` | **toàn bộ** nội dung file `secret/actions-deploy-key`, gồm cả dòng `-----BEGIN OPENSSH PRIVATE KEY-----` và `-----END OPENSSH PRIVATE KEY-----` |
| `DEPLOY_HOST` | Elastic IP (**public**) của server ứng dụng. Không bí mật nên đặt ở tab **Variables** cũng được: workflow đọc `secrets.DEPLOY_HOST` rồi `vars.DEPLOY_HOST` |
| `DEPLOY_KNOWN_HOSTS` (nên có) | toàn bộ nội dung `secret/deploy-known-hosts.txt`. Workflow chỉ lấy phần `<kiểu khoá> <khoá>` của mỗi dòng rồi gắn đúng `DEPLOY_HOST`, nên dán dòng ghi IP hay tên máy đều được. Thiếu thì workflow vẫn chạy nhưng tin dấu vân tay lấy lúc chạy (có cảnh báo vàng) |

Mở file để chép nội dung (🪟 Git Bash): `cat secret/actions-deploy-key`, `cat secret/deploy-known-hosts.txt`. Dán thẳng vào ô *Secret*, không sửa dấu xuống dòng.

Workflow tự **kiểm tra đủ secret** ở đầu job deploy và báo `Thiếu secret ...` rõ ràng nếu quên. Secret **không** được đưa vào các lượt chạy từ PR của fork (GitHub tự chặn), và job deploy không chạy với PR.

---

## 6. Thời gian và hạn mức

- **Đo thực tế** (lượt chạy đầu trên `main`, 27/11/2026): job `backend` **75 giây** (`mvn verify` 63 s), `frontend` **31 giây** (chạy song song), job `deploy`: build + push 6 image **134 giây** (nhanh hơn dự kiến nhiều: runner GitHub mạnh, mạng tới Maven Central / Docker Hub tốt). Cộng thêm deploy lên server (kéo image + `up -d --wait`, 3–8 phút, ước tính) thì cả lượt khoảng **7–12 phút**. Job `deploy` đặt `timeout-minutes: 75` cho chắc.
- Repo **public**: Actions miễn phí không giới hạn phút. Repo **private** gói Free: 2.000 phút Linux / tháng, mỗi lượt ~10 phút ⇒ khoảng 200 lượt / tháng.
- Muốn nhanh hơn: chỉ build service có thay đổi, hoặc dùng cache layer của BuildKit (`docker/build-push-action` + `cache-from: type=gha`); chưa làm vì `build-push.sh` dùng `docker build` thường.

---

## 7. Workflow làm gì

| Sự kiện | Chạy gì |
|---|---|
| **Pull request** | `backend` + `frontend` (không secret, không deploy) |
| **Push vào `main`** | `backend` + `frontend` → `deploy` |
| **Run workflow** (Actions → CI/CD → *Run workflow*) | chạy test; **tick `deploy`** để build + deploy nhánh đang chọn (hoặc chạy trên `main` thì tự deploy) |

| Job | Việc |
|---|---|
| `backend` | JDK 21 (Temurin), `mvn -B -ntp verify '-Dtest=!*ApplicationTests'` (549 unit test; bỏ các `*ApplicationTests` vì cần DB thật), tải báo cáo surefire |
| `frontend` | Node 22, `npm ci`, `npm run lint` (**chỉ cảnh báo**: code còn ~190 lỗi lint sẵn), `npm run build` (chặn nếu lỗi) |
| `deploy` | kiểm tra secret → `TAG = git rev-parse --short=7 HEAD` → **cấu hình SSH + thử `ssh ... check` ngay** (sai khoá / IP / cổng 22 thì dừng **trước** khi build, in lý do) → `docker login` → `build-push.sh $TAG` → `tar | ssh ... "deploy $TAG"` → `curl https://<APP_HOST>/identity/actuator/health` (tối đa 2 phút) → `docker logout` |

Hành vi cần biết:
- Tag image = **git SHA 7 ký tự**; rollback = deploy lại SHA cũ. `deploy.sh` luôn chạy theo SHA, không theo `latest`.
- Lỗi ở bước deploy: `deploy.sh` in chẩn đoán, **tự về bản trước**, thoát mã ≠ 0 → job đỏ, ứng dụng vẫn chạy bản cũ. Migration DB đã chạy **không** được hoàn tác ([04 §3](04_DOCKER_COMPOSE_PRODUCTION.md)).
- `concurrency`: hai lượt deploy không bao giờ chạy chồng nhau; PR mới đẩy lên thì lượt chạy cũ của PR đó bị huỷ, còn lượt trên `main` thì không bị huỷ giữa chừng.
- `build-push.sh` đọc `REGISTRY` và `SSO_HOST` từ `infrastructure/prod/public.env` (frontend nhúng URL Keycloak lúc build): **commit `public.env` đúng trước khi chạy**.
- `environment: production` được GitHub tự tạo ở lượt chạy đầu; mục 8 dùng nó để bắt duyệt tay.

---

## 8. Tuỳ chọn: duyệt tay trước khi deploy, bảo vệ `main`

🌐 Settings → **Environments** → `production` (có sau lượt chạy đầu) → *Required reviewers*: thêm người duyệt. Khi đó job `deploy` dừng ở "Waiting" tới khi có người bấm **Approve** (test vẫn chạy trước). Gói Free cho repo private có thể không có tính năng này (khi đó môi trường vẫn tạo được nhưng không bắt duyệt).

🌐 Settings → **Branches** → *Add branch ruleset / protection rule* cho `main`: *Require a pull request before merging* + *Require status checks to pass* (chọn `Test backend`, `Lint + build frontend`). Khi đó code chưa qua test không merge được, nên không deploy được.

---

## 9. Kịch bản kiểm tra

| # | Kịch bản | Kết quả mong đợi |
|---|---|---|
| 1 | Mở PR có test hỏng | `backend` đỏ trên PR, **không** build / push / deploy |
| 2 | Merge vào `main` | Test xanh → 7 image `<sha>` trên Docker Hub → `deploy.sh` in `✅` → bước HTTPS `OK` → 🐧 `deploy.sh check` báo tag `<sha>` |
| 3 | Sửa giao diện nhỏ rồi merge | Thay đổi hiện trên `https://<APP_HOST>` (gián đoạn ngắn lúc container được thay, [04 §3 mục 10](04_DOCKER_COMPOSE_PRODUCTION.md)) |
| 4 | Deploy bản cố ý lỗi (service không khởi động, ví dụ sai biến môi trường) | `up --wait` hết hạn / smoke test lỗi → **tự về bản trước**, job đỏ, ứng dụng vẫn chạy |
| 5 | Rollback tay | 🐧 `sudo -u deployer /home/deployer/deploy.sh rollback` |
| 6 | Khoá chặt | 🪟 `ssh -i secret/actions-deploy-key deployer@<IP> "ls /"` bị từ chối (mục 3) |
| 7 | Chạy tay nhánh khác | Actions → CI/CD → Run workflow, chọn nhánh, tick `deploy` → deploy nhánh đó |

**Đã kiểm chứng thật** (27/11/2026): file workflow qua `actionlint` (không lỗi); `mvn verify` (549 test) và `npm run build` chạy xanh trong container JDK 21 / Node 22; khoá SSH riêng của Actions đăng nhập được vào `deployer@<IP public>` từ máy ngoài mạng AWS với `StrictHostKeyChecking=yes`, `check` chạy được, lệnh `ls` bị từ chối, khoá cũ của Jenkins bị từ chối; cơ chế `deploy` qua SSH (gói stdin, `no-pty`), tự rollback khi SHA không tồn tại, smoke test, tất cả ở [04 §5](04_DOCKER_COMPOSE_PRODUCTION.md#5-đã-kiểm-chứng-gì-chưa-kiểm-chứng-gì). **Lượt chạy thật đầu tiên trên GitHub** (27/11/2026, commit `0678483` trên `main`): `Test backend` xanh (75 s), `Lint + build frontend` xanh (31 s), `Build + push 6 image` xanh (134 s, 6 image `0678483` có trên Docker Hub), nhưng bước **Deploy đỏ: `Host key verification failed.` (exit 255)**. Phía server log chỉ có một dòng `Connection closed by <IP runner> [preauth]` (client tự ngắt trước khi xác thực): ứng dụng **không bị đụng tới**, vẫn chạy bản cũ. Nguyên nhân: runner không tìm thấy dấu vân tay của server ứng với đúng giá trị `DEPLOY_HOST` trong `known_hosts` (secret `DEPLOY_KNOWN_HOSTS` ghi theo IP, còn `DEPLOY_HOST` được nhập kiểu khác hoặc có ký tự thừa; hoặc dán sai nội dung). Đã sửa trong workflow: (1) bỏ khoảng trắng thừa của `DEPLOY_HOST`; (2) dựng lại `known_hosts` bằng cách gắn **đúng `DEPLOY_HOST`** vào phần `<kiểu khoá> <khoá>` của secret, nên không phụ thuộc secret ghi IP hay tên máy (đã thử: host dạng tên DNS vẫn qua bước kiểm tra dấu vân tay); (3) thêm bước **Kiểm tra kết nối SSH** chạy **trước** khi build, in lý do thất bại. **Chưa kiểm chứng lại trên GitHub** sau bản sửa này.

---

## 10. Xử lý sự cố

| Triệu chứng | Nguyên nhân / cách xử lý |
|---|---|
| `Thiếu secret DOCKERHUB_*` / `DEPLOY_SSH_KEY` / `DEPLOY_HOST` | Chưa tạo secret (mục 5) hoặc gõ sai tên (phân biệt hoa thường) |
| `Error: Cannot perform an interactive login` / `unauthorized: incorrect username or password` | `DOCKERHUB_TOKEN` sai / hết hạn / chỉ có quyền Read, hoặc `DOCKERHUB_USERNAME` có chữ hoa |
| `denied: requested access to the resource is denied` khi push | Sai `REGISTRY` trong `public.env` (phải là `docker.io/<user>` viết thường), hoặc token không có quyền Write |
| `toomanyrequests` khi build | Giới hạn pull của Docker Hub: bước `docker login` đã giảm; nếu vẫn gặp thì chạy lại sau ít phút |
| `ssh: connect to host ... Connection timed out` | Cổng 22 chưa mở cho `0.0.0.0/0` (mục 4), hoặc `DEPLOY_HOST` sai / IP đổi |
| Job đỏ ở bước **Kiểm tra kết nối SSH** | Đọc phần "Chi tiết" workflow in ra (các dòng `debug1`). Lỗi này dừng job **trước** khi build nên không tốn 20 phút. Phân biệt: `Connection timed out` = cổng 22 / IP; `Host key verification failed` = `DEPLOY_KNOWN_HOSTS`; `Permission denied (publickey)` = `DEPLOY_SSH_KEY` / `authorized_keys`. Muốn thấy phía server: 🐧 `sudo journalctl -u ssh --since "15 min ago" | grep -E "deployer|preauth|Accepted"` (một dòng `Connection closed by <IP runner> [preauth]` nghĩa là client tự ngắt **trước** khi xác thực, thường do kiểm tra dấu vân tay thất bại) |
| `Host key verification failed` | `DEPLOY_KNOWN_HOSTS` không khớp server (máy được dựng lại / IP đổi): chạy lại `ssh-keyscan` (mục 2) và cập nhật secret |
| `Permission denied (publickey)` | `authorized_keys` sai (mục 3: quyền `700` cho `.ssh`, `600` cho file, chủ là `deployer`), hoặc `DEPLOY_SSH_KEY` dán thiếu dòng BEGIN / END, hoặc thừa khoảng trắng |
| `Lệnh không hợp lệ: ''` | Lệnh SSH không có đối số: phải là `deploy <sha>` / `rollback` / `check` |
| `Không có gói trên stdin` | Lệnh SSH thiếu `tar cz ... \|` phía trước |
| `Gói thiếu prod/public.env` | `tar` chạy sai thư mục: phải `-C infrastructure` rồi liệt kê `docker-compose.prod.yml prod keycloak` |
| `pull access denied` / `manifest unknown` trong log deploy | Bước build + push chưa chạy hoặc lỗi (không có image `<sha>` trên Docker Hub), hoặc repo Docker Hub đang private |
| Job `deploy` đỏ ở bước "Kiểm tra công khai" | Ứng dụng lên nhưng Nginx / HTTPS / DNS có vấn đề: 🐧 `curl -I https://<APP_HOST>/`, `sudo nginx -t`, `sudo certbot certificates` |
| `backend` đỏ: `Connection refused` ở `...ApplicationTests` | Ai đó bỏ cờ `-Dtest='!*ApplicationTests'` (các test này cần Postgres thật) |
| `frontend` vàng ở bước lint | Bình thường (lint còn ~190 lỗi sẵn, `continue-on-error`); dọn lint xong thì bỏ `continue-on-error` |
| Job `deploy` đứng ở "Waiting" | Đã bật *Required reviewers* ở environment `production` (mục 8): người duyệt bấm Approve |
| Workflow không chạy khi push nhánh | Chỉ `push` vào `main`, `pull_request` và chạy tay mới kích hoạt: mở PR hoặc chạy tay (mục 7) |
| Hết hạn mức phút Actions (repo private) | Mục 6: đợi sang tháng, chuyển repo public, hoặc build ít hơn |

---

## 11. Checklist

- [ ] `secret/actions-deploy-key`, `.pub`, `deploy-known-hosts.txt` đã tạo; `git check-ignore` xác nhận không bị commit
- [ ] 🐧 `authorized_keys` của `deployer` có đúng 1 dòng, tiền tố `command="/home/deployer/deploy.sh",...no-pty`
- [ ] 🪟 `ssh -i secret/actions-deploy-key ... deployer@<IP> check` chạy được; lệnh `ls` bị từ chối
- [ ] Security Group mở 22 cho `0.0.0.0/0`
- [ ] Secret: `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`, `DEPLOY_SSH_KEY`, `DEPLOY_HOST` (+ `DEPLOY_KNOWN_HOSTS`) đã tạo
- [ ] Mở PR hoặc merge vào `main` → workflow xanh; `deploy.sh check` báo tag mới
- [ ] Đủ các kịch bản 1–6 ở mục 9
