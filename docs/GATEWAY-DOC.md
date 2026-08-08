# 📡 Tài liệu API Gateway – DATN-2026

> **Module:** `api-gateway`  
> **Port:** `8081`  
> **Tech Stack:** Spring Cloud Gateway, Spring WebFlux (Reactive), Spring Security, OAuth2 Resource Server  
> **Java:** 21

---

## 1. API Gateway là gì?

API Gateway là **cổng vào duy nhất** của toàn bộ hệ thống backend.  
Mọi request từ Frontend hoặc client đều **bắt buộc phải đi qua Gateway** trước khi đến các microservice.

```
Client (Browser / Postman)
        │
        ▼
┌──────────────────────┐
│      API Gateway     │  :8081
│  (Cổng vào duy nhất) │
└──────────┬───────────┘
           │  Route / Forward
           ▼
┌──────────────────────┐
│   Identity Service   │  :8082
└──────────────────────┘
           │
           ▼
┌──────────────────────┐
│   Keycloak (IAM)     │  :8088
└──────────────────────┘
```

Gateway **không chứa business logic**. Nó chỉ làm:
- Kiểm tra token → cho qua hoặc từ chối
- Điều hướng request đến đúng service
- Gắn thông tin tracing vào mỗi request
- Ghi log mọi request/response

---

## 2. Sáu nhiệm vụ chính

### 2.1 🔀 Routing — Điều hướng request

**File:** `application.yml`

Gateway định nghĩa routing rule:

```yaml
routes:
  - id: identity-service
    uri: http://localhost:8082
    predicates:
      - Path=/identity/**   ← URL client gọi
    filters:
      - StripPrefix=1        ← Cắt bỏ prefix /identity khi forward
```

**Ví dụ:**

| Client gọi Gateway (:8081) | Gateway forward đến |
|---------------------------|---------------------|
| `POST /identity/api/v1/auth/login` | `POST /api/v1/auth/login` → identity-service:8082 |
| `GET /identity/api/v1/users/me` | `GET /api/v1/users/me` → identity-service:8082 |
| `GET /identity/api/v1/admin/users` | `GET /api/v1/admin/users` → identity-service:8082 |

> `StripPrefix=1` → cắt đi 1 phần đầu URL (`/identity`), phần còn lại mới được gửi xuống service.

---

### 2.2 🔐 JWT Authentication — Xác thực token

**File:** `SecurityConfig.java` | `KeycloakJwtConverter.java`

**Nguyên tắc:**

```
Request đến
    │
    ▼
Có phải endpoint PUBLIC?
    ├── CÓ → Cho qua ngay (không cần token)
    └── KHÔNG → Kiểm tra JWT
                    ├── Token hợp lệ → Cho qua, forward xuống service
                    └── Token lỗi   → Trả về 401 Unauthorized (không forward)
```

**Endpoint PUBLIC** (không cần token):
```
/identity/api/v1/auth/**   → đăng ký, đăng nhập
/actuator/health            → kiểm tra sức khỏe service
/actuator/info              → thông tin service
```

**Cơ chế xác thực JWT:**
- Gateway gọi Keycloak tại `http://localhost:8088/realms/datn-realm/protocol/openid-connect/certs`
- Lấy về **JWKS (JSON Web Key Set)** — tập hợp public key
- Dùng public key để **xác minh chữ ký** của token → không cần gọi Keycloak mỗi request
- JWKS được **cache lại**, chỉ refresh khi key thay đổi

**Trích xuất Role từ Keycloak JWT** (class `KeycloakJwtConverter`):

Keycloak lưu roles trong claim `realm_access.roles`:
```json
{
  "realm_access": {
    "roles": ["ADMIN", "USER", "default-roles-datn-realm", "offline_access"]
  }
}
```

Gateway:
1. Đọc `realm_access.roles`
2. Lọc bỏ role nội bộ của Keycloak (`default-roles-*`, `offline_access`, `uma_authorization`)
3. Thêm prefix `ROLE_` cho mỗi role: `ADMIN` → `ROLE_ADMIN`
4. Gắn vào `Authentication` object → downstream service dùng được `@PreAuthorize`

---

### 2.3 🌐 CORS — Cross-Origin Resource Sharing

**File:** `CorsConfig.java` | `application.yml` (globalcors)

Gateway xử lý CORS **tập trung** — các downstream service không cần cấu hình CORS riêng.

**Cấu hình hiện tại:**

| Setting | Value |
|---------|-------|
| Allowed Origins | `http://localhost:3000`, `http://localhost:5173` |
| Allowed Methods | `GET, POST, PUT, DELETE, PATCH, OPTIONS` |
| Allowed Headers | `*` (tất cả) |
| Exposed Headers | `X-Correlation-Id` |
| Allow Credentials | `true` (hỗ trợ cookie, Authorization header) |
| Max Age (cache) | `3600 giây` (preflight được cache 1 giờ) |

**Preflight request** (OPTIONS):  
Browser tự động gửi OPTIONS trước POST/PUT có Authorization → Gateway trả về `200 OK` kèm CORS headers mà không cần forward xuống service.

---

### 2.4 🏷️ Correlation ID — Truy vết request

**File:** `CorrelationIdFilter.java`  
**Constant:** `X-Correlation-Id` (header name)

**Vấn đề giải quyết:**  
Trong hệ thống microservice, 1 request từ client có thể đi qua nhiều service. Nếu có lỗi, rất khó biết log nào thuộc về request nào.

**Giải pháp — Correlation ID:**
- Mỗi request được gắn 1 ID duy nhất (UUID)
- ID này được truyền xuống tất cả downstream service qua header `X-Correlation-Id`
- Mỗi service dùng ID này khi ghi log → tất cả log của 1 request có cùng ID

**Luồng xử lý:**

```
Request đến Gateway
    │
    ▼
Đã có header X-Correlation-Id?
    ├── CÓ  → Giữ nguyên ID đó
    └── KHÔNG → Tạo UUID mới: "a1b2c3d4-..."
                    │
                    ▼
            Gắn ID vào:
            - Request header → forward xuống identity-service
            - Response header → client nhìn thấy để debug
            - Exchange attributes → LoggingFilter và RequestTimeFilter đọc được
```

**Ví dụ response header:**
```
X-Correlation-Id: a1b2c3d4-e5f6-7890-abcd-ef1234567890
```

---

### 2.5 📋 Logging — Ghi log request/response

**File:** `LoggingFilter.java` | `RequestTimeFilter.java`

**LoggingFilter** ghi 2 dòng log cho mỗi request:

```
# Request vào
[GATEWAY-IN]  [a1b2c3d4] POST /identity/api/v1/auth/login | IP: 127.0.0.1

# Response ra
[GATEWAY-OUT] [a1b2c3d4] Status: 200 | Time: 145ms
```

**RequestTimeFilter** theo dõi thời gian xử lý:
- Ghi thời điểm bắt đầu khi request vào
- Tính thời gian sau khi nhận response từ service
- Nếu request mất > **3000ms** → ghi thêm WARN log:

```
[SLOW-REQUEST] [a1b2c3d4] POST /identity/api/v1/auth/login took 3500ms (threshold: 3000ms)
```

**Cách xác định IP client:**  
Ưu tiên đọc header `X-Forwarded-For` (khi đứng sau Nginx/proxy), fallback về địa chỉ kết nối trực tiếp.

---

### 2.6 ❌ Error Handling — Xử lý lỗi tập trung

**File:** `GlobalExceptionHandler.java` | `ErrorResponse.java`

Mọi lỗi xảy ra tại Gateway đều được chuyển thành JSON thống nhất:

```json
{
  "success": false,
  "status": 401,
  "message": "Authentication failed: invalid or expired JWT token",
  "path": "/identity/api/v1/users/me",
  "timestamp": "2026-08-06T17:00:00"
}
```

**Bảng xử lý lỗi:**

| Exception | HTTP Status | Message |
|-----------|-------------|---------|
| `InvalidBearerTokenException` | **401** | Token không hợp lệ hoặc hết hạn |
| `AuthenticationCredentialsNotFoundException` | **401** | Thiếu JWT token |
| `AuthenticationException` (khác) | **401** | Authentication required |
| `AccessDeniedException` | **403** | Không đủ quyền |
| `ResponseStatusException` | _(theo status)_ | Downstream trả về |
| `Exception` (catch-all) | **500** | Lỗi không xác định |

---

## 3. Cấu trúc package

```
api-gateway/
└── src/main/java/com/datn/api_gateway/
    │
    ├── ApiGatewayApplication.java          ← Entry point, @SpringBootApplication
    │
    ├── config/
    │   └── CorsConfig.java                 ← Bean CorsWebFilter, cấu hình CORS
    │
    ├── security/
    │   ├── SecurityConfig.java             ← Filter chain: public endpoints, JWT validation
    │   └── KeycloakJwtConverter.java       ← Đọc realm_access.roles từ JWT
    │
    ├── filter/                             ← Global filters (áp dụng cho mọi request)
    │   ├── CorrelationIdFilter.java        ← Order: HIGHEST (chạy đầu tiên)
    │   ├── LoggingFilter.java              ← Order: HIGHEST + 1
    │   └── RequestTimeFilter.java          ← Order: HIGHEST + 2
    │
    ├── exception/
    │   └── GlobalExceptionHandler.java     ← @Order(-1), bắt mọi exception tại Gateway
    │
    ├── dto/
    │   └── ErrorResponse.java              ← Cấu trúc JSON khi có lỗi
    │
    └── constant/
        └── GatewayConstant.java            ← Hằng số: tên header, attribute key
```

---

## 4. Thứ tự xử lý một request

```
Request đến :8081
        │
        ▼
[1] CorrelationIdFilter       → Gắn X-Correlation-Id
        │
        ▼
[2] LoggingFilter             → Log "[GATEWAY-IN] ..."
        │
        ▼
[3] RequestTimeFilter         → Ghi startTime
        │
        ▼
[4] CorsWebFilter             → Xử lý CORS headers / Preflight OPTIONS
        │
        ▼
[5] SecurityWebFilterChain    → Kiểm tra JWT
        │
        ├── Token lỗi → GlobalExceptionHandler → Trả 401/403 JSON ───────►
        │
        └── Token OK / Public endpoint
                │
                ▼
[6] Spring Cloud Gateway Router
        │
        └── Route matching: /identity/** ?
                │
                ├── Match → StripPrefix → Forward → identity-service:8082
                │
                └── No match → 404
                        │
                        ▼
              [7] Response về từ identity-service
                        │
                        ▼
              [8] RequestTimeFilter.post  → Log slow request nếu > 3000ms
                        │
                        ▼
              [9] LoggingFilter.post      → Log "[GATEWAY-OUT] Status: 200"
                        │
                        ▼
              [10] Gắn X-Correlation-Id vào Response header
                        │
                        ▼
                  Response trả về Client
```

---

## 5. Cấu hình (application.yml)

```yaml
server:
  port: 8081

spring:
  cloud:
    gateway:
      routes:
        - id: identity-service
          uri: http://localhost:8082
          predicates:
            - Path=/identity/**
          filters:
            - StripPrefix=1

  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: http://localhost:8088/realms/datn-realm/protocol/openid-connect/certs
          issuer-uri: http://localhost:8088/realms/datn-realm

management:
  endpoints:
    web:
      exposure:
        include: health, info
```

---

## 6. Dependencies (pom.xml)

| Dependency | Mục đích |
|-----------|----------|
| `spring-cloud-starter-gateway` | Core: routing, filters, predicates (WebFlux) |
| `spring-boot-starter-security` | Reactive security filter chain |
| `spring-boot-starter-oauth2-resource-server` | Validate JWT qua JWKS URI |
| `spring-boot-starter-actuator` | `/actuator/health`, `/actuator/info` |
| `lombok` | Giảm boilerplate code |

---

## 7. Các endpoint Gateway expose

| Endpoint | Method | Auth | Chức năng |
|----------|--------|------|-----------|
| `/identity/api/v1/auth/register` | POST | ❌ Public | Đăng ký user mới |
| `/identity/api/v1/auth/login` | POST | ❌ Public | Đăng nhập, nhận JWT |
| `/identity/api/v1/users/me` | GET | ✅ JWT | Xem profile bản thân |
| `/identity/api/v1/admin/users` | GET | ✅ JWT + ADMIN | Danh sách tất cả users |
| `/identity/api/v1/admin/users/{id}/roles` | PUT | ✅ JWT + ADMIN | Cập nhật role user |
| `/actuator/health` | GET | ❌ Public | Health check Gateway |
| `/actuator/info` | GET | ❌ Public | Thông tin Gateway |

> **Lưu ý quan trọng:** Gateway chỉ validate **JWT có hợp lệ không**. Việc kiểm tra **role** (`ADMIN`, `USER`) được thực hiện bởi **identity-service** thông qua `@PreAuthorize`.

---

## 8. Những điều Gateway KHÔNG làm

- ❌ Không xử lý business logic (đăng ký, đăng nhập, quản lý user)
- ❌ Không kết nối database
- ❌ Không gọi trực tiếp Keycloak Admin API
- ❌ Không lưu session (stateless hoàn toàn)
- ❌ Không kiểm tra role cụ thể — chỉ kiểm tra token hợp lệ hay không
