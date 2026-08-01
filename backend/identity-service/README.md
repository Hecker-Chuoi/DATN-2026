# Identity Service – Hướng dẫn Setup & Test

> **Stack:** Spring Boot 3 · Keycloak 24.0.1 · PostgreSQL 15 · Docker Compose

---

## Mục lục

1. [Yêu cầu hệ thống](#1-yêu-cầu-hệ-thống)
2. [Khởi động môi trường Docker](#2-khởi-động-môi-trường-docker)
3. [Cấu hình Keycloak – Tạo Realm](#3-cấu-hình-keycloak--tạo-realm-datn-realm)
4. [Cấu hình Keycloak – Tạo Roles](#4-cấu-hình-keycloak--tạo-realm-roles)
5. [Cấu hình Keycloak – Tạo Client](#5-cấu-hình-keycloak--tạo-client-datn-backend)
6. [Khởi chạy Identity Service](#6-khởi-chạy-identity-service)
7. [Test API – Đăng ký & Đăng nhập](#7-test-api--đăng-ký--đăng-nhập)
8. [Test API – Lấy Profile User](#8-test-api--lấy-profile-user-authenticated)
9. [Test API – Admin: Tạo Admin User](#9-test-api--admin-tạo-admin-user)
10. [Test API – Admin: List & Update Roles](#10-test-api--admin-list--update-roles)
11. [Test Negative Cases](#11-test-negative-cases-kiểm-tra-bảo-mật)
12. [Verify Database](#12-verify-database)
13. [Tổng hợp tất cả API](#13-tổng-hợp-tất-cả-api)

---

## 1. Yêu cầu hệ thống

| Công cụ | Phiên bản |
|---------|-----------|
| Docker Desktop | ≥ 4.x |
| Java JDK | 21 |
| Maven | 3.9+ |
| Postman / curl | Bất kỳ |

**Cổng sử dụng:**

| Service | Cổng |
|---------|------|
| Keycloak Admin Console | `http://localhost:8080` |
| Identity Service API | `http://localhost:8081` |
| PostgreSQL (app-db) | `localhost:5432` |

---

## 2. Khởi động môi trường Docker

```powershell
cd backend\environment
docker compose up -d
```

**Kiểm tra tất cả container đang chạy:**

```powershell
docker ps
```

Bạn phải thấy đủ 3 container: `keycloak`, `keycloak-db`, `app-db`.

**Chờ Keycloak sẵn sàng** (khoảng 30–60 giây), sau đó verify:

```powershell
curl http://localhost:8080/realms/master
```

> ✅ Nếu trả về JSON có `"realm": "master"` là Keycloak đã sẵn sàng.

---

## 3. Cấu hình Keycloak – Tạo Realm `datn-realm`

### Bước 3.1 – Truy cập Keycloak Admin Console

1. Mở trình duyệt → truy cập: **`http://localhost:8080`**
2. Click **"Administration Console"**
3. Đăng nhập với:
   - **Username:** `admin`
   - **Password:** `admin`

### Bước 3.2 – Tạo Realm mới

1. Nhìn góc **trên bên trái**, hover/click vào chữ **"master"**
2. Click **"Create realm"**
3. Điền thông tin:

   | Trường | Giá trị |
   |--------|---------|
   | **Realm name** | `datn-realm` |
   | **Enabled** | `ON` |

4. Click **"Create"**

5. ✅ **Xác nhận:** Menu góc trên trái đã chuyển sang hiển thị `datn-realm`

---

## 4. Cấu hình Keycloak – Tạo Realm Roles

> ⚠️ Đảm bảo bạn đang ở realm **`datn-realm`** (xem góc trên bên trái).

### Bước 4.1 – Mở trang Realm Roles

1. Menu trái → Click **"Realm roles"**
2. Click **"Create role"** (nút góc trên phải)

### Bước 4.2 – Tạo role `USER`

| Trường | Giá trị |
|--------|---------|
| **Role name** | `USER` |
| **Description** | Người dùng thông thường |

→ Click **"Save"**

### Bước 4.3 – Tạo role `ADMIN`

→ Click **"Create role"** lần nữa

| Trường | Giá trị |
|--------|---------|
| **Role name** | `ADMIN` |
| **Description** | Quản trị viên hệ thống |

→ Click **"Save"**

### Bước 4.4 – Tạo role `MANAGER`

→ Click **"Create role"** lần nữa

| Trường | Giá trị |
|--------|---------|
| **Role name** | `MANAGER` |
| **Description** | Quản lý dự án |

→ Click **"Save"**

### ✅ Xác nhận

Vào **"Realm roles"** phải thấy (ít nhất) 3 roles mới được tạo:
```
ADMIN
MANAGER
USER
```

---

## 5. Cấu hình Keycloak – Tạo Client `datn-backend`

### Bước 5.1 – Mở trang Clients

1. Menu trái → Click **"Clients"**
2. Click **"Create client"** (nút góc trên phải)

### Bước 5.2 – General Settings (Tab 1)

| Trường | Giá trị |
|--------|---------|
| **Client type** | `OpenID Connect` |
| **Client ID** | `datn-backend` |
| **Name** | `DATN Backend Service` |

→ Click **"Next"**

### Bước 5.3 – Capability Config (Tab 2)

> ⚠️ **CỰC KỲ QUAN TRỌNG** – Cấu hình đúng các toggle sau:

| Setting | Giá trị |
|---------|---------|
| **Client authentication** | `OFF` (public client) |
| **Authorization** | `OFF` |
| **Standard flow** | ✅ `ON` |
| **Direct access grants** | ✅ `ON` ← **BẮT BUỘC PHẢI BẬT** |
| **Implicit flow** | `OFF` |
| **Service accounts roles** | `OFF` |

> 💡 **Lý do:** `Direct access grants = ON` mới cho phép login bằng `username/password` trực tiếp (`grant_type=password`). Đây là cơ chế mà `KeycloakService.login()` đang sử dụng.

→ Click **"Next"**

### Bước 5.4 – Login Settings (Tab 3)

| Trường | Giá trị |
|--------|---------|
| **Valid redirect URIs** | `http://localhost:8081/*` |
| **Valid post logout redirect URIs** | `http://localhost:8081/*` |
| **Web origins** | `*` |

→ Click **"Save"**

### ✅ Xác nhận Client

1. Vào **"Clients"** → click vào `datn-backend`
2. Tab **"Settings"**
3. Kiểm tra: **Direct access grants → Enabled = ON**

---

## 6. Khởi chạy Identity Service

### Bước 6.1 – Chạy service

```powershell
cd backend\identity-service
mvn spring-boot:run
```

Chờ log console hiển thị dòng:
```
Started IdentityServiceApplication in X.XXX seconds
```

### Bước 6.2 – Kiểm tra health

```powershell
curl http://localhost:8081/actuator/health
```

**Kết quả mong đợi:**
```json
{"status":"UP"}
```

---

## 7. Test API – Đăng ký & Đăng nhập

### 📌 Test 7.1 – Đăng ký user thông thường

```
POST http://localhost:8081/api/v1/auth/register
Content-Type: application/json
```

**Request Body:**
```json
{
  "username": "nguyenvan",
  "password": "Password123",
  "email": "nguyenvan@test.com",
  "firstName": "Van",
  "lastName": "Nguyen"
}
```

**Kết quả mong đợi – HTTP `201 Created`:**
```json
{
  "success": true,
  "message": "User registered successfully",
  "data": {
    "id": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
    "keycloakId": "yyyyyyyy-yyyy-yyyy-yyyy-yyyyyyyyyyyy",
    "username": "nguyenvan",
    "email": "nguyenvan@test.com",
    "firstName": "Van",
    "lastName": "Nguyen",
    "roles": ["USER"],
    "status": "ACTIVE",
    "createdAt": "2026-07-31T...",
    "updatedAt": "2026-07-31T..."
  }
}
```

**Verify trong Keycloak sau khi đăng ký:**
1. Keycloak Admin → realm `datn-realm` → **"Users"**
2. Tìm user `nguyenvan` → click vào
3. Tab **"Role mappings"** → phải thấy role `USER` đã được assign

---

### 📌 Test 7.2 – Đăng nhập (lấy JWT Token)

```
POST http://localhost:8081/api/v1/auth/login
Content-Type: application/json
```

**Request Body:**
```json
{
  "username": "nguyenvan",
  "password": "Password123"
}
```

**Kết quả mong đợi – HTTP `200 OK`:**
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expires_in": 300,
    "refresh_expires_in": 1800,
    "token_type": "Bearer",
    "session_state": "..."
  }
}
```

> 💾 **Lưu lại `access_token`** – sử dụng cho các bước tiếp theo.
> Token có hiệu lực **5 phút**. Nếu hết hạn, login lại để lấy token mới.

---

## 8. Test API – Lấy Profile User (Authenticated)

### 📌 Test 8.1 – Lấy thông tin user hiện tại

```
GET http://localhost:8081/api/v1/users/me
Authorization: Bearer <access_token_từ_bước_7.2>
```

**Kết quả mong đợi – HTTP `200 OK`:**
```json
{
  "success": true,
  "data": {
    "id": "xxxxxxxx-...",
    "keycloakId": "yyyyyyyy-...",
    "username": "nguyenvan",
    "email": "nguyenvan@test.com",
    "firstName": "Van",
    "lastName": "Nguyen",
    "roles": ["USER"],
    "status": "ACTIVE"
  }
}
```

---

## 9. Test API – Admin: Tạo Admin User

### Bước 9.1 – Đăng ký user sẽ làm admin

```
POST http://localhost:8081/api/v1/auth/register
Content-Type: application/json
```

```json
{
  "username": "adminuser",
  "password": "Admin123",
  "email": "admin@test.com",
  "firstName": "Admin",
  "lastName": "User"
}
```

> ✅ User `adminuser` được tạo với role mặc định `USER`.

### Bước 9.2 – Assign role ADMIN trong Keycloak

> Thực hiện trong **Keycloak Admin Console**:

1. Vào realm `datn-realm` → **"Users"**
2. Tìm và click vào **`adminuser`**
3. Click tab **"Role mappings"**
4. Click nút **"Assign role"**
5. Ở bộ lọc dropdown: chọn **"Filter by realm roles"**
6. Tích chọn role **`ADMIN`**
7. Click **"Assign"**

✅ **Xác nhận:** Tab "Role mappings" của `adminuser` có cả `USER` và `ADMIN`.

### Bước 9.3 – Đăng nhập với adminuser (lấy ADMIN TOKEN)

```
POST http://localhost:8081/api/v1/auth/login
Content-Type: application/json
```

```json
{
  "username": "adminuser",
  "password": "Admin123"
}
```

> 💾 Lưu lại `access_token` của admin → gọi là **`ADMIN_TOKEN`**

---

## 10. Test API – Admin: List & Update Roles

### 📌 Test 10.1 – Lấy danh sách tất cả users (ADMIN only)

```
GET http://localhost:8081/api/v1/admin/users
Authorization: Bearer <ADMIN_TOKEN>
```

**Kết quả mong đợi – HTTP `200 OK`:**
```json
{
  "success": true,
  "message": "Users retrieved successfully",
  "data": [
    {
      "id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
      "username": "nguyenvan",
      "email": "nguyenvan@test.com",
      "roles": ["USER"],
      "status": "ACTIVE"
    },
    {
      "id": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
      "username": "adminuser",
      "email": "admin@test.com",
      "roles": ["USER"],
      "status": "ACTIVE"
    }
  ]
}
```

> 📋 **Lưu lại `id` (UUID)** của `nguyenvan` để dùng ở bước tiếp theo.
> Ví dụ: `aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa`

---

### 📌 Test 10.2 – Cập nhật roles cho user (ADMIN only)

> Thay `{userId}` bằng UUID của `nguyenvan` lấy từ bước trên.

```
PUT http://localhost:8081/api/v1/admin/users/{userId}/roles
Authorization: Bearer <ADMIN_TOKEN>
Content-Type: application/json
```

```json
{
  "roles": ["USER", "MANAGER"]
}
```

**Kết quả mong đợi – HTTP `200 OK`:**
```json
{
  "success": true,
  "message": "User roles updated successfully",
  "data": {
    "username": "nguyenvan",
    "roles": ["USER", "MANAGER"],
    "status": "ACTIVE"
  }
}
```

**Verify trong Keycloak:**
1. Keycloak Admin → Users → `nguyenvan` → tab "Role mappings"
2. ✅ Phải thấy cả `USER` và `MANAGER` đều được assign

---

## 11. Test Negative Cases (Kiểm tra bảo mật)

### 📌 Test 11.1 – Gọi protected endpoint không có token

```
GET http://localhost:8081/api/v1/users/me
```
*(không có header Authorization)*

**Kỳ vọng → HTTP `401 Unauthorized`**

---

### 📌 Test 11.2 – USER thường gọi endpoint ADMIN

Dùng token của `nguyenvan` (chỉ có role `USER`):

```
GET http://localhost:8081/api/v1/admin/users
Authorization: Bearer <access_token_của_nguyenvan>
```

**Kỳ vọng → HTTP `403 Forbidden`**

---

### 📌 Test 11.3 – Đăng ký trùng username

```
POST http://localhost:8081/api/v1/auth/register
Content-Type: application/json
```

```json
{
  "username": "nguyenvan",
  "password": "AnotherPass123",
  "email": "another@test.com",
  "firstName": "Another",
  "lastName": "User"
}
```

**Kỳ vọng → HTTP `4xx` với message:**
```json
{
  "success": false,
  "message": "Username 'nguyenvan' is already taken"
}
```

---

### 📌 Test 11.4 – Đăng nhập sai password

```
POST http://localhost:8081/api/v1/auth/login
Content-Type: application/json
```

```json
{
  "username": "nguyenvan",
  "password": "SaiPassword999"
}
```

**Kỳ vọng → HTTP `4xx` với message:**
```json
{
  "success": false,
  "message": "Invalid credentials"
}
```

---

### 📌 Test 11.5 – Validation: password quá ngắn

```json
{
  "username": "test123",
  "password": "abc",
  "email": "test@test.com"
}
```

**Kỳ vọng → HTTP `400 Bad Request`**
*(password phải ≥ 6 ký tự)*

---

## 12. Verify Database

### Kết nối vào PostgreSQL container

```powershell
docker exec -it app-db psql -U postgres -d datn_main_db
```

### Xem danh sách users

```sql
SELECT id, keycloak_id, username, email, status, created_at
FROM users
ORDER BY created_at;
```

### Xem roles của từng user

```sql
SELECT u.username, ur.role
FROM users u
JOIN user_roles ur ON u.id = ur.user_id
ORDER BY u.username, ur.role;
```

**Kết quả mong đợi sau tất cả test:**

```
 username  |  role
-----------+---------
 adminuser | USER
 nguyenvan | MANAGER
 nguyenvan | USER
```

### Thoát psql

```sql
\q
```

---

## 13. Tổng hợp tất cả API

| # | Method | Endpoint | Auth | Role yêu cầu |
|---|--------|----------|------|--------------|
| 1 | `POST` | `/api/v1/auth/register` | ❌ | – |
| 2 | `POST` | `/api/v1/auth/login` | ❌ | – |
| 3 | `GET` | `/api/v1/users/me` | ✅ JWT | USER hoặc ADMIN |
| 4 | `GET` | `/api/v1/admin/users` | ✅ JWT | **ADMIN only** |
| 5 | `PUT` | `/api/v1/admin/users/{id}/roles` | ✅ JWT | **ADMIN only** |
| 6 | `GET` | `/actuator/health` | ❌ | – |

---

## ⚠️ Lưu ý quan trọng

| Vấn đề | Giải pháp |
|--------|-----------|
| Nhận lỗi `401` khi gọi API | Token đã hết hạn (5 phút). Login lại để lấy token mới |
| Assign role trong Keycloak nhưng vẫn bị `403` | Phải **login lại** để JWT mới chứa role được cập nhật |
| Keycloak chưa sẵn sàng | Chờ thêm 30–60 giây sau `docker compose up -d` |
| Identity service lỗi khi start | Kiểm tra Keycloak đã chạy chưa (service phụ thuộc Keycloak) |

---

## Thứ tự thực hiện tóm tắt

```
Bước 1  →  docker compose up -d
Bước 2  →  Tạo realm: datn-realm
Bước 3  →  Tạo roles: USER, ADMIN, MANAGER
Bước 4  →  Tạo client: datn-backend (Direct Access Grants = ON)
Bước 5  →  mvn spring-boot:run
Bước 6  →  POST /register  (tạo nguyenvan)
Bước 7  →  POST /login     (lấy access_token)
Bước 8  →  GET  /users/me  (dùng access_token)
Bước 9  →  POST /register  (tạo adminuser)
Bước 10 →  [Keycloak] Assign ADMIN role cho adminuser
Bước 11 →  POST /login     (login adminuser → lấy ADMIN_TOKEN)
Bước 12 →  GET  /admin/users      (dùng ADMIN_TOKEN)
Bước 13 →  PUT  /admin/users/{id}/roles (dùng ADMIN_TOKEN)
Bước 14 →  Test negative cases
Bước 15 →  Verify database
```
