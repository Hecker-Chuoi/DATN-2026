# CI/CD DATN-2026 – 1 EC2, Jenkins, Docker / Kubernetes

Tài liệu chính ở dạng HTML (mở bằng trình duyệt, có mục lục và nút sao chép code):

| Trang | Nội dung |
|---|---|
| [00_TONG_QUAN_CICD.html](00_TONG_QUAN_CICD.html) | Khái niệm CI/CD, hệ thống cần deploy, **14 điểm trong repo phải sửa trước khi deploy**, so sánh 2 hướng, chi phí EC2, việc chung (DNS, Security Group, GitHub ↔ Jenkins, Keycloak/Google) |
| [docker_only/HUONG_1_DOCKER_THUAN.html](docker_only/HUONG_1_DOCKER_THUAN.html) | Hướng 1: Jenkins + Docker Compose + Caddy |
| [kubenetes/HUONG_2_KUBERNETES_K3S.html](kubenetes/HUONG_2_KUBERNETES_K3S.html) | Hướng 2: Jenkins + Docker + k3s (Traefik, cert-manager, registry nội bộ) |

File mẫu (đã nhúng nguyên văn vào các trang HTML):

```
common/            Dockerfile runtime backend, Dockerfile + nginx prod frontend, application-prod.yml gateway,
                   image Jenkins, mẫu prod.env           → dùng cho CẢ 2 hướng
docker_only/files/ Jenkinsfile, docker-compose.prod.yml, docker-compose.platform.yml, Caddyfile, setup-ec2.sh
kubenetes/files/   Jenkinsfile, manifests/ (Kustomize), bootstrap/, docker-compose.platform.yml,
                   setup-k3s.sh, registry-cleanup.sh
```

> Đây là **bản đề xuất**, chưa được áp vào `backend/`, `frontend/`, `infrastructure/` và chưa chạy thử trên EC2 thật.
> Thay `datn.example.com` và các mật khẩu mẫu bằng giá trị thật trước khi dùng.
> Khuyến nghị: làm Hướng 1 trước, nâng lên Hướng 2 khi còn thời gian (dùng lại ~70% công sức).
