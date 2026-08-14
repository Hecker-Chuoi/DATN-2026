# Hướng dẫn khởi chạy Môi trường Hạ tầng (Infrastructure)

Thư mục này chứa các file cấu hình Docker Compose được tách biệt:

- **[`docker-compose.infra.yml`](file:///d:/DATN-2026/backend/environment/docker-compose.infra.yml)**: Chứa DB (PostgreSQL cho App & Keycloak) và Keycloak Server.
- **[`docker-compose.app.yml`](file:///d:/DATN-2026/backend/environment/docker-compose.app.yml)**: Chứa các container cho Backend và Frontend (nếu muốn đóng gói Docker toàn bộ).

---

## Hướng dẫn chạy nhanh (Chạy Docker Hạ tầng + Chạy chay BE & FE)

### 1. Bật Hạ tầng (Database & Keycloak)
```powershell
cd d:\DATN-2026\backend\environment
docker compose -f docker-compose.infra.yml up -d
```

### 2. Chạy Backend (Identity Service & API Gateway)
Mở IDE hoặc terminal tại `backend/`:
```powershell
cd d:\DATN-2026\backend
# Terminal 1:
./mvnw spring-boot:run -pl identity-service

# Terminal 2:
./mvnw spring-boot:run -pl api-gateway
```

### 3. Chạy Frontend
```powershell
cd d:\DATN-2026\frontend
npm install
npm run dev
```
Truy cập: `http://localhost:5173`
