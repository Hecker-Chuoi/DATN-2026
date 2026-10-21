# Hướng dẫn Khởi chạy Môi trường Hạ tầng (Infrastructure) & Dọn dẹp Docker

Thư mục này chứa cấu hình Docker cho toàn bộ hạ tầng (Databases, Keycloak, Redis, Kafka, pgAdmin) và ứng dụng:

- **[`docker-compose.infra.yml`](file:///d:/DATN-2026/infrastructure/docker-compose.infra.yml)**: Chứa các service hạ tầng gồm PostgreSQL (cho App `datn_main_db` & Keycloak), Keycloak Server 24, pgAdmin 4, Redis 7, Apache Kafka 3.8.
- **[`docker-compose.app.yml`](file:///d:/DATN-2026/infrastructure/docker-compose.app.yml)**: Đóng gói toàn bộ backend và frontend nếu muốn chạy containerized.

---

## 🧹 I. Hướng dẫn Xóa sạch Docker (Container, Image, Volume, Network)

Khi muốn reset toàn bộ môi trường về trạng thái trắng (sạch hoàn toàn dữ liệu cũ):

### Cách 1: Xóa nhanh theo file `docker-compose.infra.yml` (Khuyên dùng)
Dừng toàn bộ container, xóa mạng, xóa sạch các volume chứa data cũ và gỡ images:
```powershell
cd d:\DATN-2026\backend\environment
docker compose -f docker-compose.infra.yml down -v --rmi all
```

> **Giải thích tham số:**
> - `down`: Dừng và gỡ bỏ containers, networks.
> - `-v` (`--volumes`): **Xóa sạch toàn bộ data volumes** (Xóa sạch DB cũ của PostgreSQL, Keycloak, Redis, Kafka).
> - `--rmi all`: Xóa toàn bộ images đã pull/build của compose này.

---

### Cách 2: Dọn sạch triệt để toàn bộ Docker trên máy (Xóa tất cả)

Nếu muốn xóa triệt để mọi thứ đang tồn đọng trong Docker daemon:

#### 1. Dừng và xóa toàn bộ Containers đang có:
```powershell
# Dừng tất cả container đang chạy
docker stop $(docker ps -aq)

# Xóa tất cả container
docker rm -f $(docker ps -aq)
```

#### 2. Xóa toàn bộ Volumes (Dữ liệu database lưu trữ cục bộ):
```powershell
# Xóa tất cả các volume không còn container nào dùng
docker volume prune -f

# Hoặc xóa đích danh các volume của dự án:
docker volume rm keycloak_data app_db_data redis_data kafka_data
```

#### 3. Xóa toàn bộ Images (Ảnh docker đã tải về):
```powershell
# Xóa toàn bộ images không dùng:
docker image prune -a -f

# Hoặc ép xóa toàn bộ image hiện có:
docker rmi -f $(docker images -q)
```

#### 4. Lệnh dọn dẹp "1 chạm" toàn diện:
```powershell
docker system prune -a --volumes -f
```

---

## 🚀 II. Hướng dẫn Cài đặt & Khởi chạy Môi trường Hạ tầng (Docker Infra)

### Bước 1: Khởi động các Service Hạ tầng
Mở PowerShell tại thư mục `infrastructure`:
```powershell
cd d:\DATN-2026\backend\environment
docker compose -f docker-compose.infra.yml up -d
```

### Bước 2: Kiểm tra trạng thái các Container
Chờ khoảng 20-30 giây để PostgreSQL và Keycloak khởi động hoàn tất (chuyển sang trạng thái `healthy`):
```powershell
docker compose -f docker-compose.infra.yml ps
```

Các dịch vụ sẽ mở tại các cổng:
- **Keycloak Server**: [http://localhost:8088](http://localhost:8088) (Tài khoản: `admin` / `admin`)
- **PostgreSQL App DB (`datn_main_db`)**: `localhost:5433` (Tài khoản: `postgres` / `123456`)
- **pgAdmin 4**: [http://localhost:5050](http://localhost:5050) (Tài khoản: `admin@admin.com` / `admin`)
- **Redis**: `localhost:6380`
- **Apache Kafka**: `localhost:9092`
- **MinIO S3 Storage**: `localhost:9000` (API) & [http://localhost:9001](http://localhost:9001) (Console UI - User: `minioadmin` / `minioadminpassword`)
- **Mailpit (SMTP dev)**: SMTP `localhost:1025`, xem mail tại [http://localhost:8025](http://localhost:8025). notification-service (:9994, chỉ bind 127.0.0.1) gửi mail qua đây.

---

### Bước 3: Cấu hình / Import Realm cho Keycloak

Sau khi Keycloak chạy, import file [`realm-export_08082026.json`](file:///d:/DATN-2026/infrastructure/keycloak/realm-export_08082026.json) (đã cấu hình sẵn Client ID `datn-backend` và Google Identity Provider):

1. Truy cập [http://localhost:8088](http://localhost:8088) ➔ Đăng nhập với `admin` / `admin`.
2. Menu góc trái bấm chọn dropdown Realm (mặc định là `master`) ➔ Bấm **Create Realm**.
3. Tại phần **Resource file**, bấm **Browse** và chọn file:
   ```text
   d:\DATN-2026\backend\environment\keycloak\realm-export_08082026.json
   ```
4. Bấm nút **Create**. Toàn bộ Roles, Client `datn-backend`, và Google Provider sẽ được nạp hoàn chỉnh.

*(Chi tiết xem thêm tại [`keycloak-setup.md`](file:///d:/DATN-2026/infrastructure/keycloak-setup.md))*

---

## 💻 III. Khởi chạy Ứng dụng (Chạy trực tiếp trên máy qua Maven & NPM)

### 1. Khởi chạy Backend Services:
Mở 3 terminal riêng biệt tại thư mục `backend/`:

- **Terminal 1 - API Gateway (Cổng 8081)**:
  ```powershell
  cd d:\DATN-2026\backend
  ./mvnw spring-boot:run -pl api-gateway
  ```

- **Terminal 2 - Identity Service (Cổng 8082)**:
  ```powershell
  cd d:\DATN-2026\backend
  ./mvnw spring-boot:run -pl identity-service
  ```

- **Terminal 3 - Work Service (Cổng 8083)**:
  ```powershell
  cd d:\DATN-2026\backend
  ./mvnw spring-boot:run -pl work-service
  ```
- **Terminal 4 - Workspace Service (Cổng 8087)**:
  ```powershell
  cd d:\DATN-2026\backend
  ./mvnw spring-boot:run -pl workspace-service
  ```
### 2. Khởi chạy Frontend:
Mở terminal tại thư mục `frontend/`:
```powershell
cd d:\DATN-2026\frontend
npm install
npm run dev
```
Truy cập ứng dụng: **[http://localhost:5173](http://localhost:5173)**
