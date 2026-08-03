# API Gateway — Walkthrough

## Tổng quan

API Gateway đã được xây dựng hoàn chỉnh với các thành phần sau:

| Package | File | Chức năng |
|---|---|---|
| `config` | [CorsConfig.java](file:///c:/Users/anh.nvt/Desktop/finalProject/DATN-2026/backend/api-gateway/src/main/java/com/datn/api_gateway/config/CorsConfig.java) | CORS tập trung |
| `security` | [SecurityConfig.java](file:///c:/Users/anh.nvt/Desktop/finalProject/DATN-2026/backend/api-gateway/src/main/java/com/datn/api_gateway/security/SecurityConfig.java) | Reactive security chain (JWT) |
| `security` | [KeycloakJwtConverter.java](file:///c:/Users/anh.nvt/Desktop/finalProject/DATN-2026/backend/api-gateway/src/main/java/com/datn/api_gateway/security/KeycloakJwtConverter.java) | Extract Keycloak realm roles |
| `filter` | [CorrelationIdFilter.java](file:///c:/Users/anh.nvt/Desktop/finalProject/DATN-2026/backend/api-gateway/src/main/java/com/datn/api_gateway/filter/CorrelationIdFilter.java) | Sinh/forward X-Correlation-Id |
| `filter` | [LoggingFilter.java](file:///c:/Users/anh.nvt/Desktop/finalProject/DATN-2026/backend/api-gateway/src/main/java/com/datn/api_gateway/filter/LoggingFilter.java) | Log request/response info |
| `filter` | [RequestTimeFilter.java](file:///c:/Users/anh.nvt/Desktop/finalProject/DATN-2026/backend/api-gateway/src/main/java/com/datn/api_gateway/filter/RequestTimeFilter.java) | Theo dõi execution time |
| `exception` | [GlobalExceptionHandler.java](file:///c:/Users/anh.nvt/Desktop/finalProject/DATN-2026/backend/api-gateway/src/main/java/com/datn/api_gateway/exception/GlobalExceptionHandler.java) | JSON error responses (401/403/500) |
| `dto` | [ErrorResponse.java](file:///c:/Users/anh.nvt/Desktop/finalProject/DATN-2026/backend/api-gateway/src/main/java/com/datn/api_gateway/dto/ErrorResponse.java) | Error response DTO |
| `constant` | [GatewayConstant.java](file:///c:/Users/anh.nvt/Desktop/finalProject/DATN-2026/backend/api-gateway/src/main/java/com/datn/api_gateway/constant/GatewayConstant.java) | Shared constants |
| — | [ApiGatewayApplication.java](file:///c:/Users/anh.nvt/Desktop/finalProject/DATN-2026/backend/api-gateway/src/main/java/com/datn/api_gateway/ApiGatewayApplication.java) | Entry point |
| — | [application.yml](file:///c:/Users/anh.nvt/Desktop/finalProject/DATN-2026/backend/api-gateway/src/main/resources/application.yml) | Routes, JWT, CORS, Actuator |
| — | [pom.xml](file:///c:/Users/anh.nvt/Desktop/finalProject/DATN-2026/backend/api-gateway/pom.xml) | Dependencies (WebFlux, Security, OAuth2) |

**Modified:**
| File | Thay đổi |
|---|---|
| [parent pom.xml](file:///c:/Users/anh.nvt/Desktop/finalProject/DATN-2026/backend/pom.xml) | Uncomment `<module>api-gateway</module>` |

---

## Luồng hoạt động chi tiết

### Sơ đồ tổng quan

```mermaid
sequenceDiagram
    participant C as Client/Frontend
    participant GW as API Gateway :8080
    participant SC as SecurityChain
    participant DS as Identity Service :8082
    participant KC as Keycloak :8088

    Note over GW,KC: Startup: Gateway tải JWK Set từ Keycloak (chỉ 1 lần, cache tự động)
    
    C->>GW: POST /api/identity/v1/auth/login
    
    Note over GW: ① CorrelationIdFilter
    GW->>GW: Sinh UUID X-Correlation-Id

    Note over GW: ② LoggingFilter  
    GW->>GW: Log [GATEWAY-IN] POST /api/identity/v1/auth/login

    Note over GW: ③ RequestTimeFilter
    GW->>GW: Ghi nhận startTime
    
    Note over GW: ④ SecurityChain
    GW->>SC: Check path matching
    SC-->>GW: /api/identity/v1/auth/** → PUBLIC (permitAll)
    
    Note over GW: ⑤ Route Matching + StripPrefix
    GW->>GW: Match route identity-service
    GW->>GW: StripPrefix=1: bỏ /identity
    GW->>GW: Path: /api/v1/auth/login
    
    Note over GW: ⑥ Forward Headers
    GW->>DS: POST /api/v1/auth/login + X-Correlation-Id
    
    DS->>KC: Exchange credentials for JWT
    KC-->>DS: JWT Token
    DS-->>GW: 200 OK + TokenResponse
    
    Note over GW: ⑦ Post-Filters (ngược lại)
    GW->>GW: RequestTimeFilter: tính executionTime
    GW->>GW: LoggingFilter: log [GATEWAY-OUT] 200 | 42ms
    GW->>GW: CorrelationIdFilter: thêm X-Correlation-Id vào response

    GW-->>C: 200 OK + TokenResponse + X-Correlation-Id
```

---

### Chi tiết từng bước

#### 1️⃣ Client gửi Request → Gateway (Port 8080)

```
POST http://localhost:8080/api/identity/v1/auth/login
Content-Type: application/json

{
  "username": "john",
  "password": "secret123"
}
```

Client luôn gửi request tới Gateway (`port 8080`), KHÔNG bao giờ gửi trực tiếp tới identity-service (`port 8082`).

---

#### 2️⃣ CorrelationIdFilter (Order: HIGHEST_PRECEDENCE)

**Chạy ĐẦU TIÊN.**

- Kiểm tra header `X-Correlation-Id` trong request
- Nếu **không có** → sinh UUID mới: `550e8400-e29b-41d4-a716-446655440000`
- Nếu **có rồi** → giữ nguyên giá trị
- **Mutate request**: thêm header `X-Correlation-Id` vào request sẽ forward xuống downstream
- **Lưu vào Exchange Attributes**: để các filter khác truy cập

```
Request Headers (sau khi mutate):
X-Correlation-Id: 550e8400-e29b-41d4-a716-446655440000
```

---

#### 3️⃣ LoggingFilter (Order: HIGHEST_PRECEDENCE + 1)

**Chạy thứ HAI.**

- Đọc Correlation ID từ Exchange Attributes
- Log thông tin request:

```
[GATEWAY-IN]  [550e8400-...] POST /api/identity/v1/auth/login | IP: 192.168.1.100
```

- Ghi nhận `startTime` để tính execution time sau

---

#### 4️⃣ RequestTimeFilter (Order: HIGHEST_PRECEDENCE + 2)

**Chạy thứ BA.**

- Lưu `System.currentTimeMillis()` vào Exchange Attribute `requestStartTime`
- Nếu request mất > 3000ms → log WARNING `[SLOW-REQUEST]`

---

#### 5️⃣ SecurityWebFilterChain — Authentication Check

**Đây là bước quyết định PASS hay REJECT.**

```java
.pathMatchers("/api/identity/v1/auth/**").permitAll()    // ← Match!
.anyExchange().authenticated()
```

- Path `/api/identity/v1/auth/login` khớp với pattern `permitAll()` → **PASS** (không cần JWT)
- Nếu path KHÔNG phải public (ví dụ `/api/identity/v1/users/me`):
  1. Kiểm tra header `Authorization: Bearer <token>`
  2. Validate JWT bằng **JWK Set đã cache** từ Keycloak (KHÔNG gọi Keycloak mỗi request)
  3. Nếu JWT hợp lệ → `KeycloakJwtConverter` extract roles → **PASS**
  4. Nếu JWT không hợp lệ/hết hạn → `GlobalExceptionHandler` trả 401

---

#### 6️⃣ Route Matching + StripPrefix

```yaml
routes:
  - id: identity-service
    uri: http://localhost:8082
    predicates:
      - Path=/api/identity/**
    filters:
      - StripPrefix=1
```

- Gateway match route `identity-service` vì path bắt đầu `/api/identity/`
- `StripPrefix=1` bỏ **1 segment** sau `/api/`:

```
Trước: /api/identity/v1/auth/login
            ^^^^^^^^ → bỏ segment "identity"
Sau:   /api/v1/auth/login
```

- Forward request tới `http://localhost:8082/api/v1/auth/login`

---

#### 7️⃣ Forward Headers

Gateway tự động forward các headers sau xuống downstream:

| Header | Giá trị | Ghi chú |
|---|---|---|
| `Authorization` | `Bearer eyJhbG...` | Forward nguyên bản, KHÔNG sửa |
| `X-Correlation-Id` | `550e8400-...` | Được thêm bởi CorrelationIdFilter |
| `Content-Type` | `application/json` | Forward nguyên bản |
| `Host` | `localhost:8082` | Gateway thay đổi Host header |

---

#### 8️⃣ Downstream Service Xử Lý

Identity Service nhận request `POST /api/v1/auth/login` và xử lý:
1. `AuthController.login()` nhận credentials
2. `KeycloakService.login()` exchange credentials với Keycloak
3. Trả về `TokenResponse` chứa `access_token`, `refresh_token`

---

#### 9️⃣ Post-Filters (Chạy ngược lại)

Sau khi downstream trả response, các filter chạy **ngược thứ tự**:

1. **RequestTimeFilter**: Tính `executionTime = now - startTime`
2. **LoggingFilter**: Log response info
   ```
   [GATEWAY-OUT] [550e8400-...] Status: 200 | Time: 42ms
   ```
3. **CorrelationIdFilter**: Thêm `X-Correlation-Id` vào response headers

---

#### 🔟 Response trả về Client

```json
HTTP/1.1 200 OK
X-Correlation-Id: 550e8400-e29b-41d4-a716-446655440000
Content-Type: application/json

{
  "success": true,
  "message": "Login successful",
  "data": {
    "access_token": "eyJhbGciOiJSUzI1NiIs...",
    "refresh_token": "eyJhbGciOiJIUzI1NiIs...",
    "expires_in": 300,
    "refresh_expires_in": 1800,
    "token_type": "Bearer"
  },
  "timestamp": "2026-08-03T15:00:00"
}
```

---

## Luồng cho Protected Endpoint (có JWT)

```mermaid
sequenceDiagram
    participant C as Client
    participant GW as Gateway :8080
    participant SC as SecurityChain
    participant JC as KeycloakJwtConverter
    participant DS as Identity Service :8082

    C->>GW: GET /api/identity/v1/users/me<br/>Authorization: Bearer eyJ...

    Note over GW: Filters 1-3 chạy giống nhau

    GW->>SC: Check path matching
    SC->>SC: /api/identity/v1/users/me → NOT public
    SC->>SC: Require authenticated()
    
    SC->>SC: Extract Bearer token from header
    SC->>SC: Validate JWT signature (cached JWK Set)
    SC->>SC: Check issuer, expiry, audience
    
    SC->>JC: Convert JWT → Authentication
    JC->>JC: Extract realm_access.roles
    JC->>JC: Map ["ADMIN","USER"] → [ROLE_ADMIN, ROLE_USER]
    JC-->>SC: JwtAuthenticationToken with authorities
    
    SC-->>GW: ✅ Authenticated

    Note over GW: Route match + StripPrefix
    GW->>DS: GET /api/v1/users/me<br/>Authorization: Bearer eyJ...<br/>X-Correlation-Id: xxx
    
    Note over DS: @PreAuthorize("hasAnyRole('USER','ADMIN')")
    DS-->>GW: 200 OK + UserResponse
    GW-->>C: 200 OK + UserResponse
```

---

## Luồng khi JWT không hợp lệ (Error Flow)

```mermaid
sequenceDiagram
    participant C as Client
    participant GW as Gateway :8080
    participant SC as SecurityChain
    participant EH as GlobalExceptionHandler

    C->>GW: GET /api/identity/v1/users/me<br/>Authorization: Bearer INVALID_TOKEN

    Note over GW: Filters 1-3 chạy bình thường

    GW->>SC: Check path → NOT public
    SC->>SC: Validate JWT signature → FAIL!
    SC-->>GW: Throw InvalidBearerTokenException

    GW->>EH: Handle exception
    EH->>EH: Map to 401 UNAUTHORIZED
    EH->>EH: Build ErrorResponse JSON

    GW-->>C: 401 Unauthorized

    Note over C: Response body:
    Note over C: {"success":false,"status":401,<br/>"message":"Authentication failed: invalid or expired JWT token",<br/>"path":"/api/identity/v1/users/me",<br/>"timestamp":"2026-08-03T15:00:00"}
```

---

## Filter Execution Order

```mermaid
graph TD
    A["① CorrelationIdFilter<br/>Order: HIGHEST_PRECEDENCE<br/>Pre: Sinh/check X-Correlation-Id"] --> B
    B["② LoggingFilter<br/>Order: HIGHEST_PRECEDENCE + 1<br/>Pre: Log request info"] --> C
    C["③ RequestTimeFilter<br/>Order: HIGHEST_PRECEDENCE + 2<br/>Pre: Record start time"] --> D
    D["④ SecurityWebFilterChain<br/>JWT validation / Public path check"] --> E
    E["⑤ Route Matching<br/>StripPrefix + Forward"] --> F
    F["⑥ Downstream Service"]
    
    F --> G["③ RequestTimeFilter<br/>Post: Calculate execution time"]
    G --> H["② LoggingFilter<br/>Post: Log status + time"]
    H --> I["① CorrelationIdFilter<br/>Post: Add header to response"]
    I --> J["Response → Client"]
    
    style A fill:#4CAF50,color:#fff
    style B fill:#2196F3,color:#fff
    style C fill:#FF9800,color:#fff
    style D fill:#f44336,color:#fff
    style E fill:#9C27B0,color:#fff
    style F fill:#607D8B,color:#fff
```

---

## Cách thêm Service mới

Khi cần route tới service mới (ví dụ `order-service` chạy ở port `8083`):

### 1. Thêm route vào [application.yml](file:///c:/Users/anh.nvt/Desktop/finalProject/DATN-2026/backend/api-gateway/src/main/resources/application.yml):

```yaml
# ── Order Service ──────────────────────────────────
- id: order-service
  uri: http://localhost:8083
  predicates:
    - Path=/api/order/**
  filters:
    - StripPrefix=1
```

### 2. Thêm public endpoints (nếu có) vào [SecurityConfig.java](file:///c:/Users/anh.nvt/Desktop/finalProject/DATN-2026/backend/api-gateway/src/main/java/com/datn/api_gateway/security/SecurityConfig.java):

```java
private static final String[] PUBLIC_ENDPOINTS = {
    "/api/identity/v1/auth/**",
    "/api/order/v1/public/**",     // ← thêm dòng này
    "/actuator/health",
    "/actuator/info"
};
```

### 3. Không cần sửa gì thêm!

- Filters tự động áp dụng cho route mới
- JWT validation tự động cho protected endpoints
- Correlation ID tự động forward
- CORS tự động xử lý

---

## Verification Checklist

| Check | Status |
|---|---|
| Không có JPA, Entity, Repository, Database | ✅ |
| Không có Business Logic | ✅ |
| Không gọi Keycloak mỗi request (dùng JWKS cache) | ✅ |
| Không kiểm tra Role nghiệp vụ (chỉ Authentication) | ✅ |
| Constructor Injection (không Field Injection) | ✅ |
| JavaDoc cho class public | ✅ |
| Comment giải thích trong mỗi filter | ✅ |
| Log bằng Slf4j | ✅ |
| SOLID principles | ✅ |
| Reactive API (WebFlux) | ✅ |
