# 01 — Chuẩn bị tài khoản và thông tin trước khi làm

> Thuộc [00 — Danh sách các bước](00_DANH_SACH_CAC_BUOC.md) (**bước 0**). Không có lệnh nào chạy trên server; chỉ là những thứ phải có trong tay để các bước sau khỏi bị dừng giữa chừng.
>
> Mô hình: **GitHub Actions** (build + deploy) → **Docker Hub** (kho image) → **một EC2** chạy `docker compose`. Không còn Jenkins.

## Mục lục

1. [Cần những gì](#1-cần-những-gì)
2. [Docker Hub: tài khoản và Access Token](#2-docker-hub-tài-khoản-và-access-token)
3. [GitHub: quyền và bật Actions](#3-github-quyền-và-bật-actions)
4. [AWS: một EC2 và Elastic IP](#4-aws-một-ec2-và-elastic-ip)
5. [Tên miền](#5-tên-miền)
6. [Bảng ghi lại thông tin](#6-bảng-ghi-lại-thông-tin)
7. [Checklist](#7-checklist)

---

## 1. Cần những gì

| Thứ | Để làm gì | Ai tạo |
|---|---|---|
| Tài khoản **Docker Hub** + 1 Access Token (Read & Write) | Actions push 6 image `datn-*` | bạn |
| Quyền **admin** repo GitHub | Tạo secret của Actions (Write không đủ) | chủ repo |
| 1 **EC2** `t3.large` (2 vCPU / 8 GB), Ubuntu 24.04, kèm **Elastic IP** | Chạy toàn bộ ứng dụng | bạn |
| **Tên miền** (hoặc `sslip.io`) trỏ về Elastic IP: `app.<domain>`, `sso.<domain>` | HTTPS cho ứng dụng và Keycloak | bạn |
| Khoá `.pem` của EC2 | SSH quản trị lần đầu | AWS |

---

## 2. Docker Hub: tài khoản và Access Token

1. Có tài khoản Docker Hub (tên tài khoản **viết thường**: tên này nằm trong tên image, ví dụ `docker.io/<user>/datn-work-service`).
2. Docker Hub → Account settings → **Personal access tokens** → **Generate new token**:

   | Mục | Giá trị |
   |---|---|
   | Description | `datn-github-actions` |
   | Expiration | đặt ngày hết hạn (ví dụ 90 ngày; **ghi lịch** để tạo token mới) |
   | Access permissions | **Read & Write** (không cần *Delete*) |

3. **Chép token ngay**: Docker Hub chỉ hiện một lần. Token này sẽ dán vào secret `DOCKERHUB_TOKEN` ở [06](06_GITHUB_ACTIONS_CICD.md); **không** dán vào chat, file trong repo hay ảnh chụp màn hình.
4. 6 repo `datn-api-gateway`, `datn-identity-service`, `datn-work-service`, `datn-workspace-service`, `datn-notification-service`, `datn-frontend` tự tạo ở lần push đầu và mặc định **public** (gói miễn phí chỉ cho 1 repo private). Server kéo image public không cần đăng nhập.

> Token lộ ra ngoài (ảnh chụp, tin nhắn, commit) thì vào Docker Hub **Revoke** ngay rồi tạo token khác.

---

## 3. GitHub: quyền và bật Actions

- Tạo **secret** cần quyền **admin** (Settings → Secrets and variables → Actions → *New repository secret*). Người chỉ có quyền Write đẩy được workflow nhưng không thêm được secret. Thấy tab **Settings** của repo là dấu hiệu bạn có quyền admin.
- Settings → **Actions → General**: *Actions permissions* = **Allow all actions and reusable workflows** (hoặc cho phép `actions/*`, bản workflow chỉ dùng `actions/checkout`, `setup-java`, `setup-node`, `upload-artifact`); *Workflow permissions* = **Read repository contents** (workflow tự khai `permissions: contents: read`).
- Không cần webhook, không cần PAT.
- Actions của repo **private** có hạn mức phút miễn phí hằng tháng (gói Free: 2.000 phút / tháng), mỗi lần deploy tốn khoảng 20–30 phút ([06 §6](06_GITHUB_ACTIONS_CICD.md#6-thời-gian-và-hạn-mức)).

---

## 4. AWS: một EC2 và Elastic IP

Chi tiết tạo máy ở [02 §1](02_CHUAN_BI_SERVER_UNG_DUNG.md#1-tạo-ec2-datn-app). Hai điểm phải quyết **trước**:

- **Cỡ máy**: tài khoản chỉ được cấp tối đa `t3.large` (2 vCPU / 7,6 GB); toàn bộ 12 container + HĐH dùng khoảng 3 GB lúc rảnh ([04 §4](04_DOCKER_COMPOSE_PRODUCTION.md#4-ngân-sách-ram)).
- **Elastic IP**: gắn **trước** khi chọn tên miền. IP public thường đổi mỗi lần stop/start máy, kéo theo tên miền `sslip.io`, chứng chỉ HTTPS, `public.env` và image frontend (nhúng URL Keycloak) hỏng cùng lúc.
- **Cổng 22 phải mở ra Internet** vì runner của GitHub có IP thay đổi ([06 §4](06_GITHUB_ACTIONS_CICD.md#4-mở-cổng-22-trên-security-group)).

---

## 5. Tên miền

Có tên miền thật: tạo hai bản ghi A `app` và `sso` trỏ về Elastic IP. Chưa có thì dùng `sslip.io` (tự trỏ, không cấu hình): Elastic IP `13.250.1.2` → tên miền gốc `13-250-1-2.sslip.io` → `app.13-250-1-2.sslip.io`, `sso.13-250-1-2.sslip.io`.

> Chọn **tên miền và `https`** ngay từ đầu. Đổi sau nghĩa là build lại image frontend, xoá realm Keycloak và mọi người đăng nhập lại ([04 §3, mục 3](04_DOCKER_COMPOSE_PRODUCTION.md#3-những-quyết-định-thiết-kế-đáng-biết)).

---

## 6. Bảng ghi lại thông tin

Điền khi làm, dùng ở các bước sau:

| Thông tin | Giá trị | Dùng ở |
|---|---|---|
| Docker Hub user | | `public.env` (`REGISTRY`), secret `DOCKERHUB_USERNAME` |
| Elastic IP (public) | | DNS, secret `DEPLOY_HOST` |
| Tên miền gốc | | `set-public-host.sh` |
| Khoá `.pem` + đường dẫn | | SSH quản trị |

---

## 7. Checklist

- [ ] Có Access Token Docker Hub Read & Write (đã chép an toàn) và ghi lịch hết hạn
- [ ] Vào được Settings → Secrets and variables → Actions của repo (admin)
- [ ] Có EC2 `t3.large` + Elastic IP; tên miền `app.` / `sso.` đã trỏ về IP đó

Xong → [02 — Dựng server ứng dụng](02_CHUAN_BI_SERVER_UNG_DUNG.md).
