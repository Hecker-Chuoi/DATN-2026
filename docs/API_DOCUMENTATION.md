# API Documentation – Authentication & User Management

Tài liệu này đặc tả các API liên quan đến xác thực (đăng ký, đăng nhập thường, đăng nhập Google, đăng xuất) và quản lý người dùng trong hệ thống **DATN-2026**.

Tất cả các API được gọi thông qua **API Gateway** tại cổng `:8081` (môi trường dev) hoặc qua tên miền chính thức. Gateway sẽ chuyển tiếp yêu cầu đến **identity-service** (cổng `:8082`).

---

## 🔐 1. API Xác thực (Authentication)

### 1.1 Đăng ký tài khoản (Register)
Đăng ký tài khoản mới. Hệ thống sẽ tạo tài khoản trên Keycloak và đồng bộ thông tin sang cơ sở dữ liệu local (`datn_main_db`).

* **URL:** `/identity/api/v1/auth/register`
* **Method:** `POST`
* **Xác thực:** Không yêu cầu (Public)
* **Headers:** 
  - `Content-Type: application/json`

#### Request Body
```json
{
  "username": "devuser",
  "password": "Password123",
  "email": "devuser@example.com",
  "firstName": "Dev",
  "lastName": "User"
}
```

#### Response (Success - `201 Created`)
```json
{
  "success": true,
  "message": "User registered successfully",
  "data": {
    "id": "a1b2c3d4-e5f6-7a8b-9c0d-e1f2a3b4c5d6",
    "keycloakId": "239a1c53-c1cd-4b1e-aa99-81880c83fade",
    "username": "devuser",
    "email": "devuser@example.com",
    "firstName": "Dev",
    "lastName": "User",
    "status": "ACTIVE",
    "roles": ["USER"],
    "createdAt": "2026-08-08T17:15:22.000Z"
  }
}
```

#### Response (Error - `400 Bad Request` - Trùng username/email)
```json
{
  "success": false,
  "message": "Username already exists or Email already exists"
}
```

---

### 1.2 Đăng nhập bằng tài khoản (Login)
Xác thực tài khoản bằng username/password trực tiếp qua Keycloak. Trả về token meta ở Body và thiết lập **httpOnly cookie** chứa Access Token.

* **URL:** `/identity/api/v1/auth/login`
* **Method:** `POST`
* **Xác thực:** Không yêu cầu (Public)
* **Headers:** 
  - `Content-Type: application/json`

#### Request Body
```json
{
  "username": "devuser",
  "password": "Password123"
}
```

#### Response (Success - `200 OK`)
* **Headers:**
  - `Set-Cookie: access_token=eyJhbGciOi...; Max-Age=300; Path=/; HttpOnly; SameSite=Lax`
* **Body:**
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "eyJhbGciOi...",
    "refreshToken": "eyJhbGciOi...",
    "expiresIn": 300,
    "refreshExpiresIn": 1800,
    "tokenType": "Bearer",
    "sessionState": "5cf3e4..."
  }
}
```

#### Response (Error - `401 Unauthorized` - Sai tài khoản/mật khẩu)
```json
{
  "success": false,
  "message": "Invalid username or password"
}
```

---

### 1.3 Đăng xuất (Logout)
Xóa phiên đăng nhập bằng cách thu hồi và xóa cookie `access_token` phía trình duyệt (set Max-Age = 0).

* **URL:** `/identity/api/v1/auth/logout`
* **Method:** `POST`
* **Xác thực:** Không yêu cầu (Public)
* **Headers:** 
  - `Content-Type: application/json`

#### Response (Success - `200 OK`)
* **Headers:**
  - `Set-Cookie: access_token=; Max-Age=0; Path=/; HttpOnly; SameSite=Lax`
* **Body:**
```json
{
  "success": true,
  "message": "Logged out successfully",
  "data": null
}
```

---

### 1.4 Khởi tạo liên kết Google (Google Init)
Lấy URL chuyển hướng đến màn hình đăng nhập Google thông qua Keycloak Identity Broker.

* **URL:** `/identity/api/v1/auth/google/init`
* **Method:** `GET`
* **Xác thực:** Không yêu cầu (Public)
* **Query Parameters:**
  - `redirectUri` (String, optional): URL callback của frontend sau khi đăng nhập xong (Mặc định: `http://localhost:5173/auth/callback`).

#### Response (Success - `200 OK`)
```json
{
  "success": true,
  "message": "OK",
  "data": {
    "authorizationUrl": "http://localhost:8088/realms/datn-realm/protocol/openid-connect/auth?client_id=datn-backend&response_type=code&scope=openid+profile+email&redirect_uri=http%3A%2F%2Flocalhost%3A5173%2Fauth%2Fcallback&kc_idp_hint=google"
  }
}
```

---

### 1.5 Xử lý Callback Google (Google Callback)
Nhận authorization code từ frontend (do Keycloak trả về), thực hiện đổi code lấy JWT token, đồng bộ thông tin user từ Google vào DB local và thiết lập cookie `access_token`.

* **URL:** `/identity/api/v1/auth/google/callback`
* **Method:** `POST`
* **Xác thực:** Không yêu cầu (Public)
* **Headers:** 
  - `Content-Type: application/json`

#### Request Body
```json
{
  "code": "8e3c1d9b-...",
  "redirectUri": "http://localhost:5173/auth/callback"
}
```

#### Response (Success - `200 OK`)
* **Headers:**
  - `Set-Cookie: access_token=eyJhbGciOi...; Max-Age=300; Path=/; HttpOnly; SameSite=Lax`
* **Body:** Giống hệt response body của API **1.2 Đăng nhập (Login)**.

---

## 👤 2. API Quản lý người dùng (User Management)

### 2.1 Lấy thông tin cá nhân (Get My Profile)
Trả về thông tin chi tiết của người dùng hiện tại đang đăng nhập dựa trên token.

* **URL:** `/identity/api/v1/users/me`
* **Method:** `GET`
* **Xác thực:** Yêu cầu đăng nhập (Role: `USER` hoặc `ADMIN`)
* **Headers:**
  - `Cookie: access_token=eyJhbGciOi...` (Trình duyệt tự gửi)

#### Response (Success - `200 OK`)
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": "a1b2c3d4-e5f6-7a8b-9c0d-e1f2a3b4c5d6",
    "keycloakId": "239a1c53-c1cd-4b1e-aa99-81880c83fade",
    "username": "devuser",
    "email": "devuser@example.com",
    "firstName": "Dev",
    "lastName": "User",
    "status": "ACTIVE",
    "roles": ["USER"],
    "createdAt": "2026-08-08T17:15:22.000Z"
  }
}
```

---

### 2.2 Lấy danh sách người dùng (Get All Users)
Trả về danh sách toàn bộ người dùng trong hệ thống.

* **URL:** `/identity/api/v1/admin/users`
* **Method:** `GET`
* **Xác thực:** Yêu cầu quyền Admin (Role: `ADMIN`)
* **Headers:**
  - `Cookie: access_token=eyJhbGciOi...` (Trình duyệt tự gửi)

#### Response (Success - `200 OK`)
```json
{
  "success": true,
  "message": "Users retrieved successfully",
  "data": [
    {
      "id": "a1b2c3d4-e5f6-7a8b-9c0d-e1f2a3b4c5d6",
      "keycloakId": "239a1c53-c1cd-4b1e-aa99-81880c83fade",
      "username": "devuser",
      "email": "devuser@example.com",
      "firstName": "Dev",
      "lastName": "User",
      "status": "ACTIVE",
      "roles": ["USER"],
      "createdAt": "2026-08-08T17:15:22.000Z"
    }
  ]
}
```
