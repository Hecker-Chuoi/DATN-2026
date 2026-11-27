# 06 — Nối Jenkins với server ứng dụng

> Thuộc [00 — Danh sách các bước](00_DANH_SACH_CAC_BUOC.md) (**bước 5–7**). Làm **sau** [05](05_TRIEN_KHAI_LAN_DAU.md): ứng dụng đã chạy bằng tay qua `deploy.sh`. Jenkins đã cài theo [01](01_HUONG_DAN_SETUP_JENKINS.md) Phần A.
>
> Kết quả: `git push` lên `main` → Jenkins chạy test → build + push 6 image → SSH sang `datn-app` chạy `deploy.sh` → ứng dụng chạy bản mới (lỗi thì tự về bản trước).
>
> Quy ước: 🐧J = phiên SSH vào **`datn-jenkins`**; 🐧A = phiên SSH vào **`datn-app`**; 🪟 = máy Windows. **Mỗi khối lệnh ghi rõ chạy ở đâu — đừng dán nhầm giữa hai server.**

## Mục lục

1. [Mô hình truy cập](#1-mô-hình-truy-cập)
2. [Cho khoá của Jenkins vào `deployer` (🐧A)](#2-cho-khoá-của-jenkins-vào-deployer-a)
3. [Khoá SSH chỉ chạy được `deploy.sh`](#3-khoá-ssh-chỉ-chạy-được-deploysh)
4. [Phía Jenkins: known_hosts và credential (🐧J)](#4-phía-jenkins-known_hosts-và-credential-j)
5. [`Jenkinsfile` trong repo](#5-jenkinsfile-trong-repo)
6. [Job `smoke-test`, Multibranch, webhook](#6-job-smoke-test-multibranch-webhook)
7. [Kịch bản kiểm tra](#7-kịch-bản-kiểm-tra)
8. [Xử lý sự cố](#8-xử-lý-sự-cố)
9. [Checklist](#9-checklist)

---

## 1. Mô hình truy cập

```
 datn-jenkins ──ssh (IP private, cổng 22, khoá id_deploy)──► deployer@datn-app
                                   │
              authorized_keys:  command="/home/deployer/deploy.sh",no-pty,...  ssh-ed25519 AAAA... jenkins-deploy
                                   │
                    Jenkins chỉ nói được 3 lệnh:  deploy <sha> (kèm gói qua stdin) | rollback | check
```

- Jenkins SSH bằng **IP private** (`172.31.x.x`, không đổi khi stop/start, không tốn băng thông Internet). Hai EC2 phải **cùng VPC** và Security Group `datn-app-sg` cho phép 22 từ SG của `datn-jenkins` ([02 §1.3](02_CHUAN_BI_SERVER_UNG_DUNG.md#13-security-group-datn-app-sg)).
- `deployer` thuộc nhóm `docker` (≈ quyền root trên máy này) nhưng **không có `sudo`**, và khoá SSH của Jenkins bị **khoá chặt** (mục 3): kẻ lấy được khoá chỉ chạy được `deploy.sh`, không mở được shell.
- `deploy.sh` thuộc **root** (`/home/deployer/deploy.sh`, đã cài ở [05 §3](05_TRIEN_KHAI_LAN_DAU.md#3-cài-deploysh-lên-server)): `deployer` chạy được nhưng không sửa được.

---

## 2. Cho khoá của Jenkins vào `deployer` (🐧A)

Nếu chưa có cặp khoá `id_deploy` trên `datn-jenkins` thì sinh ở [01 §9](01_HUONG_DAN_SETUP_JENKINS.md#9-credentials). Lấy **khoá công khai** (🐧J):

```bash
sudo cat /var/lib/jenkins/.ssh/id_deploy.pub        # ssh-ed25519 AAAA... jenkins-deploy
```

🐧A trên `datn-app` (thay `<nội dung id_deploy.pub>` bằng cả dòng vừa in; giữ nháy kép ngoài cùng):

```bash
PUBKEY="<nội dung id_deploy.pub>"
sudo -u deployer install -d -m 700 /home/deployer/.ssh
echo "command=\"/home/deployer/deploy.sh\",no-port-forwarding,no-agent-forwarding,no-X11-forwarding,no-pty $PUBKEY" \
  | sudo -u deployer tee /home/deployer/.ssh/authorized_keys > /dev/null
sudo chmod 600 /home/deployer/.ssh/authorized_keys
sudo cat /home/deployer/.ssh/authorized_keys        # một dòng, bắt đầu bằng command="/home/deployer/deploy.sh",...
```

Tiền tố `command="..."` là phần **khoá chặt** (mục 3): ghi luôn từ đầu, không cần làm hai lần.

---

## 3. Khoá SSH chỉ chạy được `deploy.sh`

Với `command="/home/deployer/deploy.sh"`, dù Jenkins gõ lệnh gì, sshd cũng chạy `deploy.sh` và đặt lệnh người gọi vào biến `SSH_ORIGINAL_COMMAND`. `deploy.sh` chỉ chấp nhận:

| Lệnh Jenkins gửi | Tác dụng |
|---|---|
| `deploy <sha>` + gói `tar.gz` trên stdin | triển khai commit `<sha>` (7–40 ký tự hex thường; tag khác bị từ chối) |
| `rollback` | về bản chạy tốt ngay trước |
| `check` | in phiên bản Docker / Compose, trạng thái container, tag đang chạy (dùng cho `smoke-test`) |

Mọi thứ khác (`bash`, `ls`, `docker ...`) → `Lệnh không hợp lệ`. `no-pty` nghĩa là không cấp terminal tương tác. Kiểm tra (🐧J, sau mục 4):

```bash
sudo -u jenkins ssh -i /var/lib/jenkins/.ssh/id_deploy -o BatchMode=yes deployer@<APP_HOST_IP> check          # chạy được
sudo -u jenkins ssh -i /var/lib/jenkins/.ssh/id_deploy -o BatchMode=yes deployer@<APP_HOST_IP> "ls /"          # ❌ Lệnh không hợp lệ: 'ls /'
```

> Sửa `deploy.sh` thì cài lại bản mới bằng `sudo install -m 755 -o root -g root ...` ([05 §3](05_TRIEN_KHAI_LAN_DAU.md#3-cài-deploysh-lên-server)): **Jenkins không tự cập nhật được `deploy.sh`** (cố ý, để lộ khoá không đủ sửa chính script). File compose, cấu hình Spring, realm thì đi theo gói mỗi lần deploy.

---

## 4. Phía Jenkins: known_hosts và credential (🐧J)

```bash
# Ghi dấu vân tay server để SSH không hỏi yes/no khi chạy pipeline (IP PRIVATE)
sudo -u jenkins bash -c 'ssh-keyscan -H <APP_HOST_IP> >> /var/lib/jenkins/.ssh/known_hosts'
```

Credential trong Jenkins ([01 §9](01_HUONG_DAN_SETUP_JENKINS.md#9-credentials)): **`app-server-ssh`** (SSH Username with private key, Username = `deployer`, Private key = nội dung `sudo cat /var/lib/jenkins/.ssh/id_deploy`) và **`dockerhub`** (Access Token Read & Write). `github-token` đã có từ trước.

---

## 5. `Jenkinsfile` trong repo

`Jenkinsfile` ở **gốc repo** (đã viết sẵn). Các stage:

| Stage | Chạy khi | Làm gì |
|---|---|---|
| Init | luôn | `TAG` = `git rev-parse --short=7 HEAD` |
| Test backend | luôn (mọi nhánh, mọi PR) | `mvn -B -ntp verify` trong `backend/` |
| Frontend lint + build | luôn | `npm ci && npm run lint && npm run build` |
| Build & push images | **chỉ `main`** | `docker login` bằng credential `dockerhub`, `bash infrastructure/ci/build-push.sh "$TAG"` |
| Deploy | **chỉ `main`** | `tar cz -C infrastructure docker-compose.prod.yml prod keycloak \| ssh deployer@$APP_SERVER "deploy $TAG"` |

**Việc phải sửa:** dòng `APP_SERVER = '172.31.0.0'` trong `environment` → IP **private** của `datn-app` (commit). `REGISTRY` không cần khai ở đây: `build-push.sh` đọc từ `infrastructure/prod/public.env`.

Hành vi cần biết:
- `deploy.sh` chờ container `healthy` + smoke test, **tự rollback** khi lỗi và thoát mã ≠ 0 → stage Deploy đỏ, ứng dụng vẫn chạy bản cũ.
- PR chỉ chạy test + lint, **không** build / push / deploy (điều kiện `when { branch 'main' }`).
- `disableConcurrentBuilds()`: hai lần push liền nhau xếp hàng, không deploy chồng nhau.
- Tool `jdk-21`, `maven-3.9`, `node-22` phải có ở Global Tools ([01 §8](01_HUONG_DAN_SETUP_JENKINS.md#8-global-tools-jdk-maven-node)).

---

## 6. Job `smoke-test`, Multibranch, webhook

Làm theo [01 §10–12](01_HUONG_DAN_SETUP_JENKINS.md#10-tạo-job-multibranch-pipeline):

1. Job **`smoke-test`** (01 §12): 3 stage `Tools`, `Docker Hub login`, `SSH server ứng dụng` (lệnh `check`) đều xanh.
2. Job Multibranch `DATN/datn` trỏ repo, đọc `Jenkinsfile`.
3. Webhook GitHub → `https://<JENKINS_HOST>/github-webhook/`.

---

## 7. Kịch bản kiểm tra

| # | Kịch bản | Kết quả mong đợi |
|---|---|---|
| 1 | Mở PR có test hỏng | Jenkins báo đỏ trên PR, **không** build / push / deploy |
| 2 | Merge vào `main` | Test xanh → 6 image `<sha>` trên Docker Hub → `deploy.sh` in `✅` → 🐧A `deploy.sh check` báo tag `<sha>` |
| 3 | Sửa giao diện nhỏ rồi merge | Thay đổi hiện trên `https://<APP_HOST>` (gián đoạn ngắn lúc container được thay, xem 04 §3 mục 10) |
| 4 | Deploy bản cố ý lỗi (service không khởi động, ví dụ sai biến môi trường) | `up --wait` hết hạn / smoke test lỗi → `deploy.sh` in chẩn đoán, **tự về bản trước**, stage đỏ, ứng dụng vẫn chạy |
| 5 | Rollback tay | 🐧A `sudo -u deployer /home/deployer/deploy.sh rollback` đưa về bản cũ |
| 6 | Kiểm tra khoá chặt | 🐧J `ssh -i id_deploy deployer@<IP> "ls /"` bị từ chối (mục 3) |

Ghi lại ảnh chụp (Jenkins stage view, Docker Hub tags, `docker ps` / `deploy.sh check`) cho báo cáo đồ án.

---

## 8. Xử lý sự cố

| Triệu chứng | Nguyên nhân / cách xử lý |
|---|---|
| `Host key verification failed` | Chưa `ssh-keyscan` IP **private** vào `known_hosts` của user `jenkins` (mục 4) |
| `Permission denied (publickey)` | `authorized_keys` sai dòng / sai quyền (mục 2: `700` cho `.ssh`, `600` cho file, chủ là `deployer`); credential `app-server-ssh` dán sai khoá riêng |
| `Lệnh không hợp lệ: ''` | Lệnh SSH không có đối số (mục 3 chỉ chấp nhận `deploy <sha>`, `rollback`, `check`) |
| `Không có gói trên stdin` | Lệnh SSH thiếu `tar cz ... \|` phía trước (stdin phải là gói) |
| `Gói thiếu prod/public.env` | Chạy `tar` sai thư mục: phải `-C infrastructure` rồi liệt kê `docker-compose.prod.yml prod keycloak` |
| Stage Deploy treo lâu rồi đỏ | Lần deploy đầu chậm (Keycloak, chuỗi khởi động). Xem 🐧A `docker ps`; hạn `up --wait` mặc định 20 phút (`DEPLOY_TIMEOUT`) |
| `pull access denied` trong log deploy | Stage Build & push chưa chạy / lỗi (không có image `<sha>` trên Docker Hub), hoặc repo đang private |
| Jenkins đỏ ở stage Test | Test hỏng thật: xem Console; không liên quan tới server ứng dụng |
| Muốn dùng cách khác khi Jenkins hỏng | Deploy tay đầy đủ như [05 §2, §4](05_TRIEN_KHAI_LAN_DAU.md): `build-push.sh` trên Windows + `tar \| ssh ... deploy.sh deploy <tag>` |

---

## 9. Checklist

- [ ] `authorized_keys` của `deployer` có tiền tố `command="/home/deployer/deploy.sh",...no-pty`
- [ ] 🐧J `ssh -i id_deploy deployer@<IP private> check` chạy được; lệnh `ls /` bị từ chối
- [ ] Credential `app-server-ssh` và `dockerhub` có trong Jenkins; job `smoke-test` xanh cả 3 stage
- [ ] `Jenkinsfile`: `APP_SERVER` đã đổi sang IP private thật và đã commit
- [ ] Multibranch + webhook chạy: push một nhánh thì job tự chạy
- [ ] Merge vào `main` → pipeline xanh → ứng dụng chạy bản mới; đủ 6 kịch bản ở mục 7
