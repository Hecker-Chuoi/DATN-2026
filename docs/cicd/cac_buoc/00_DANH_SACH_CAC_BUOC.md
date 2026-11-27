# Danh sách các bước triển khai CI/CD (2 server: Jenkins + ứng dụng bằng docker compose)

> Hướng đã chốt: **Jenkins + Docker Hub + docker compose**, chạy trên **2 EC2**:
> - **`datn-jenkins`** (đã có, cài theo [01](01_HUONG_DAN_SETUP_JENKINS.md)): chỉ chạy Jenkins — test, build, push image.
> - **`datn-app`** (tạo mới, theo [02](02_CHUAN_BI_SERVER_UNG_DUNG.md)): chỉ chạy Docker + Nginx — chạy ứng dụng (6 hạ tầng + 5 service Spring + frontend = 12 container).
>
> Jenkins deploy bằng **SSH** sang `datn-app` (user `deployer`, credential `app-server-ssh`) rồi chạy `deploy.sh` → `docker compose pull && up -d --wait`.
>
> Nguyên tắc: **deploy tay thành công trước, rồi mới tự động hoá** (bước 0–4 tay, bước 5–7 tự động).
>
> **Thay đổi so với bản trước:** bỏ Minikube + Helm (đã xoá khỏi nhánh, còn trong lịch sử git: `git show e93a83d:infrastructure/helm/...`). Lý do: ứng dụng chạy trên **một** máy, không cần điều phối container; bỏ được Kubernetes, Ingress, RBAC, Helm chart, ~1,2 GB RAM của lõi Kubernetes và hơn nửa số tài liệu. Đánh đổi: deploy có gián đoạn ngắn từng service (04 §3, mục 10), không tự co giãn.

## Kiến trúc

```
 Developer ── git push ──► GitHub ── webhook ──┐
                                               ▼
                     ┌─────────────────────────────────────┐
                     │ EC2 datn-jenkins  (2 vCPU / 8 GB)   │
                     │  Nginx :443 ─► Jenkins 127.0.0.1:8080│
                     │  Docker (build image), JDK, Maven, Node
                     └──────┬──────────────────┬───────────┘
                docker push │                  │ ssh deployer@<IP private datn-app>
                            ▼                  ▼   "deploy <tag>" + gói tar.gz (compose, cấu hình)
                     ┌────────────┐   ┌───────────────────────────────────────────┐
                     │ Docker Hub │◄──│ EC2 datn-app  (2 vCPU / 8 GB)             │
                     └────────────┘   │  Nginx :443                               │
                       pull image     │   ├─ app.<domain> ─► 127.0.0.1:3000 (frontend → api-gateway)
                                      │   └─ sso.<domain> ─► 127.0.0.1:8080 (keycloak)
                                      │  docker compose (project "datn"):         │
                                      │   postgres, keycloak, kafka, redis, minio, mailpit
                                      │   api-gateway, identity, work, workspace, notification, frontend
                                      └───────────────────────────────────────────┘
```

## Đã xong / cần dọn

- [x] Phần A của [01](01_HUONG_DAN_SETUP_JENKINS.md) (mục 1–8) trên `datn-jenkins`: Jenkins, Docker, Nginx, plugin, Global Tools.
- [x] Credential `github-token`, `dockerhub`.
- [x] Cặp khoá `/var/lib/jenkins/.ssh/id_deploy` (+ `.pub`) trên `datn-jenkins` — **giữ lại**, dùng ở bước 5.
- [x] Trong repo: `docker-compose.prod.yml`, `infrastructure/prod/*`, `Jenkinsfile`, `build-push.sh` (04). **Chỉ còn việc điền tên miền + Docker Hub user** (04 §7).
- [ ] **Dọn** (làm khi tạo lại `datn-app`):
  - EC2 `datn-minikube` cũ: terminate (hoặc dựng lại hệ điều hành) và dùng lại Elastic IP cho `datn-app`; đổi tên Security Group `datn-minikube-sg` → `datn-app-sg` (hoặc tạo mới, mở 22 / 80 / 443).
  - Jenkins: đổi tên credential `minikube-ssh` → `app-server-ssh` (hoặc tạo mới); xoá dòng host cũ trong `known_hosts`: `sudo -u jenkins ssh-keygen -R <IP private cũ>`.
  - Nếu từng tạo user `deployer` nhầm trên `datn-jenkins`: `sudo deluser --remove-home deployer`.

## Các bước

| # | Việc | Server / nơi làm | Xong khi |
|---|---|---|---|
| 0 | **[02 §1–3](02_CHUAN_BI_SERVER_UNG_DUNG.md#1-tạo-ec2-datn-app)** — Tạo EC2 `datn-app` (**`t3.large`**, credit Unlimited, **cùng VPC** với `datn-jenkins`, Elastic IP, Security Group: 22 từ máy bạn + SG Jenkins, 80/443 công khai). Chuẩn bị máy: timezone, swap, ufw, cài Docker + Compose, giới hạn log container | `datn-app` | `docker run --rm hello-world` chạy bằng `ubuntu`; `docker compose version` ≥ 2.20 |
| 1 | **[02 §4–6](02_CHUAN_BI_SERVER_UNG_DUNG.md#4-cài-nginx-certbot-jq)** — Cài Nginx + certbot + jq; DNS `app.` / `sso.` → Elastic IP; Nginx proxy vào `127.0.0.1:3000` / `:8080`; HTTPS bằng certbot; đặt tên miền vào `public.env` rồi commit | `datn-app` + repo | `certbot renew --dry-run` thành công; `https://app.<domain>/` có phản hồi (502 là đúng lúc này) |
| 2 | **[03](03_SECRET_VA_CAU_HINH.md)** — Tạo user `deployer` (nhóm `docker`, không `sudo`), `/home/deployer/datn/`, sinh `secrets.env` (12 khoá, quyền 600), sao lưu về máy cá nhân (`secret/`, đã gitignore) | `datn-app`, **không commit** | `secrets.env` có 12 khoá; bản sao lưu có trên Windows |
| 3 | ✅ **[04](04_DOCKER_COMPOSE_PRODUCTION.md) — ĐÃ LÀM trong repo.** `docker-compose.prod.yml` (12 container, healthcheck, `depends_on` theo chuỗi, `mem_limit`), `prod/` (`public.env`, profile Spring `prod`, init Postgres, Nginx mẫu, `deploy.sh`, `make-realm.sh`, `backup-postgres.sh`), `Jenkinsfile`. Đã chạy thử thật 12/12 container `healthy` từ DB trống (04 §5) | repo | `docker compose config` hợp lệ; việc còn lại của bạn: 04 §7 |
| 4 | **[05](05_TRIEN_KHAI_LAN_DAU.md)** — `build-push.sh` trên Windows (6 image, tag = git SHA); cài `deploy.sh` lên server; `tar \| ssh ... deploy.sh deploy <tag>`; mở trình duyệt thử; đo RAM; thử `rollback`; cron sao lưu | cả hai | **Mở trình duyệt đăng nhập được**; WebSocket `101`; upload file; không container nào `OOMKilled` sau 30 phút dùng thử |
| 5 | **[06 §1–4](06_NOI_JENKINS_VOI_SERVER_UNG_DUNG.md)** — `authorized_keys` của `deployer` (= `id_deploy.pub`) **kèm** `command="/home/deployer/deploy.sh",no-pty` (khoá chỉ chạy được `deploy` / `rollback` / `check`); trên `datn-jenkins` `ssh-keyscan` **IP private** + credential `app-server-ssh` | cả hai | 🐧J `ssh -i id_deploy deployer@<IP private> check` chạy được; lệnh `ls /` bị từ chối |
| 6 | **[06 §5–6](06_NOI_JENKINS_VOI_SERVER_UNG_DUNG.md#5-jenkinsfile-trong-repo)** + [01 §10–12](01_HUONG_DAN_SETUP_JENKINS.md#10-tạo-job-multibranch-pipeline) — Sửa `APP_SERVER` trong `Jenkinsfile`; job `smoke-test` (3 stage); Multibranch `DATN/datn`; webhook GitHub | Jenkins + repo | Job `smoke-test` xanh cả 3 stage; push một nhánh thì job tự chạy |
| 7 | **[06 §7](06_NOI_JENKINS_VOI_SERVER_UNG_DUNG.md#7-kịch-bản-kiểm-tra)** — 6 kịch bản: PR hỏng test, merge `main`, sửa giao diện, deploy lỗi (tự rollback), rollback tay, khoá SSH chặn lệnh lạ | Jenkins + `datn-app` | Push code → pipeline xanh → ứng dụng chạy bản mới |
| 8 | Vận hành: `datn-jenkins` theo [01 §13](01_HUONG_DAN_SETUP_JENKINS.md#13-bảo-trì) (cron dọn image, 1 executor); `datn-app`: cron `backup-postgres.sh` (05 §8), **không** `docker compose down -v` / `docker volume prune` (02 §7), theo dõi **R8** | cả hai | Có cron chạy, có file backup |

## Ngân sách tài nguyên `datn-app` (2 vCPU / ~7,6 GB)

Tài khoản chỉ được cấp tối đa `t3.large` (2 vCPU / 7,6 GB). Hạ tầng + 5 JVM + Keycloak ở cấu hình mặc định cần ~7–8 GB → phải giới hạn. Cột `mem_limit` là giá trị **đã viết trong `docker-compose.prod.yml`**, cột "Đo thử" là RAM thật khi 12 container đã lên và rảnh trên máy dev (04 §4) — **chưa đo trên EC2**:

| Thành phần | `mem_limit` | Đo thử | Ghi chú |
|---|---|---|---|
| Ubuntu + Docker + Nginx (ngoài container) | — | ~0,8 GB (ước tính) | |
| keycloak | 1280m | ~893 MiB | heap 40%, `KC_CACHE=local`, `start` (không `start-dev`) |
| kafka | 768m | ~313 MiB | KRaft 1 nút, heap 384m, giữ log 72 giờ |
| work-service | 768m | ~415 MiB | |
| notification / workspace / identity / api-gateway | 640m mỗi cái | 330 / 320 / 257 / 236 MiB | |
| postgres (**1 instance**, 2 DB) | 512m | ~106 MiB | |
| minio / redis / mailpit / frontend | 256m / 96m / 64m / 64m | 89 / 8 / 16 / 15 MiB | |
| **Cộng 12 container** | 6,3 GB (giới hạn) | **≈ 3,0 GiB** | |

Mục tiêu: `free -h` → `available` ≥ 1 GB khi chạy ổn định, không container nào `OOMKilled`.

Các việc tối ưu — **đã làm** thì đánh dấu, mỗi mục ghi nơi nó nằm trong repo:

- [x] **R1 — Tầng máy** (02 §2): swap 4 GB + `vm.swappiness=10`; tắt `snapd`; giới hạn log container 10 MB × 3 (`daemon.json`) để log không ăn đầy đĩa.
- [x] **R2 — Gộp Postgres** (`prod/postgres/10-init-databases.sh`): **một** `postgres:15-alpine` chứa `datn_main_db` (owner `datn`) + `keycloak` (owner `kc_user`); script chạy `CREATE EXTENSION pgcrypto` bằng superuser (migration V1 của work / workspace cần) và thu hồi `CONNECT` để mỗi user chỉ vào DB của mình. **Đã chạy thử thật** (owner, extension đúng; 5 service chạy được Flyway).
- [x] **R3 — Giới hạn JVM 5 service** (`x-spring-env` trong compose): `-XX:MaxRAMPercentage=65 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError`; **không đặt giới hạn CPU** (bị bóp thì Spring khởi động lâu gấp mấy lần); `hikari.maximum-pool-size: 5` trong từng `application-prod.yaml`.
- [x] **R4 — Hạ tầng có giới hạn**: Kafka `-Xms384m -Xmx384m` + giữ log 72 giờ; **Keycloak limit 1280m (không phải 1 GB)**, heap 40% (repo từng gặp OOMKilled ở 1 GB; không thêm `-XX:+UseSerialGC`); Redis `--maxmemory 64mb --maxmemory-policy allkeys-lru` (không lưu đĩa); Mailpit `MP_MAX_MESSAGES=200`, giao diện chỉ nghe `127.0.0.1`.
- [x] **R5 — Healthcheck + khởi động theo chuỗi** (compose): `start_period: 120s`, `retries: 10` cho Spring; `depends_on … service_healthy` theo chuỗi `identity → work → workspace` (đúng thứ tự migration trên DB trống) và Postgres / Redis / Kafka / MinIO / Keycloak sẵn sàng; notification chờ Kafka (không khởi động được nếu không phân giải được `kafka`).
- [x] **R6 — Deploy kiểu thay thế, không song song**: `docker compose up -d` chỉ tạo lại container có image mới, từng cái một theo chuỗi `depends_on`; hạ tầng giữ nguyên. **Đánh đổi:** mỗi service gián đoạn vài chục giây lúc được thay; cả đợt deploy ước tính 5–8 phút (**chưa đo**) → ghi trong báo cáo là hạn chế do tài nguyên. Có máy 16 GB thì chạy hai bản song song được.
- [ ] **R7 — Đo thực tế rồi chỉnh** (bước 4, [05 §6](05_TRIEN_KHAI_LAN_DAU.md#6-đo-ram-và-chỉnh)): `docker stats --no-stream`, `free -h`, `docker inspect ... OOMKilled`; đặt `mem_limit` ≈ mức dùng cao nhất × 1,3.
- [ ] **R8 — Theo dõi khi vận hành** (bước 8): cột `RestartCount` không tăng; `dmesg -T | grep -i oom` trên host (OOM ở tầng host nguy hiểm hơn OOM của container); `docker system df` cho đĩa. Vẫn OOM thường xuyên dù đã làm R2–R6 → phương án cuối: xin cấp `t3.xlarge`, hoặc tách Keycloak + Kafka sang máy thứ ba.

## Vì sao tách 2 server

- Tài khoản chỉ được cấp tối đa **2 vCPU / 7,6 GB mỗi EC2** (`t3.large`). Một máy không đủ chạy cả Jenkins (build ~4–5 GB lúc cao điểm) lẫn ứng dụng (~3 GB + đỉnh lúc khởi động) → tách 2 máy.
- Build nặng (Maven, `docker build`) không làm chậm / OOM ứng dụng; Jenkins lỗi hay đang nâng cấp thì ứng dụng vẫn chạy.

## Lưu ý

- Jenkins SSH sang `datn-app` bằng **IP private** (`172.31.x.x`, không đổi khi stop/start). IP public / Elastic IP chỉ dùng cho DNS `app.` / `sso.` và để bạn SSH vào quản trị.
- Hai EC2 phải **cùng VPC** (thường là VPC mặc định) thì mới thấy nhau qua IP private.
- `deployer` thuộc nhóm `docker` (≈ quyền root trên máy `datn-app`) nhưng **không có `sudo`**, và khoá SSH của Jenkins bị khoá chặt bằng `command="…deploy.sh"`; `deploy.sh` thuộc root nên Jenkins không tự sửa được.
- Muốn tiết kiệm: stop `datn-app` khi không dùng; Docker + `restart: unless-stopped` tự bật lại các container khi máy start (chờ vài phút vì chuỗi khởi động).
- Bản cũ (Minikube + Helm) còn trong lịch sử git tại commit `e93a83d`.
