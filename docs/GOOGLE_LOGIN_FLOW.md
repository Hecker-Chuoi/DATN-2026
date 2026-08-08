# Google OAuth2 Login Flow – Keycloak Integration

Tài liệu này mô tả chi tiết luồng đăng nhập bằng Google sử dụng **Keycloak làm Identity Broker** và cách hệ thống đồng bộ hóa tài khoản người dùng vào cơ sở dữ liệu nội bộ (`datn_main_db`).

---

## 🏗️ Sơ đồ tuần tự (Sequence Diagram)

Sơ đồ dưới đây mô tả luồng giao tiếp giữa Trình duyệt, Frontend, API Gateway, Identity Service, Keycloak và Google API:

```mermaid
sequenceDiagram
    autonumber
    actor User as Người dùng
    participant Browser as Trình duyệt (SPA)
    participant Gateway as API Gateway (:8081)
    participant AuthSvc as Identity Service (:8082)
    participant Keycloak as Keycloak (:8088)
    participant Google as Google OAuth2 API

    User->>Browser: Bấm "Continue with Google"
    Browser->>Gateway: GET /identity/api/v1/auth/google/init?redirectUri=...
    Gateway->>AuthSvc: Forward request
    AuthSvc->>AuthSvc: Tạo Keycloak Auth URL (với kc_idp_hint=google)
    AuthSvc-->>Browser: Trả về { authorizationUrl }
    
    Browser->>Keycloak: Redirect tới authorizationUrl
    Note over Keycloak: Nhận diện kc_idp_hint=google
    Keycloak->>Google: Chuyển tiếp tới trang Google Sign-In
    Google-->>User: Hiển thị màn hình chọn tài khoản Google
    
    User->>Google: Chọn tài khoản & Đồng ý cấp quyền
    Google-->>Keycloak: Trả về Authorization Code của Google
    Keycloak->>Google: Đổi code lấy UserInfo (Email, Name...)
    Keycloak->>Keycloak: Tạo/Cập nhật user trong Realm datn-realm
    
    Keycloak-->>Browser: Redirect về http://localhost:5173/auth/callback?code=KEYCLOAK_CODE
    
    Note over Browser: GoogleCallback.jsx kích hoạt
    Browser->>Gateway: POST /identity/api/v1/auth/google/callback { code, redirectUri }
    Gateway->>AuthSvc: Forward request
    
    AuthSvc->>Keycloak: POST /openid-connect/token (Đổi KEYCLOAK_CODE lấy JWT)
    Keycloak-->>AuthSvc: Trả về JWT Token Pair (Access Token, Refresh Token)
    
    AuthSvc->>Keycloak: GET /userinfo (Sử dụng Access Token vừa nhận)
    Keycloak-->>AuthSvc: Trả về UserInfo (sub, email, given_name, family_name)
    
    AuthSvc->>AuthSvc: findOrCreateGoogleUser() (Đồng bộ thông tin vào datn_main_db)
    
    Note over AuthSvc: Tạo Cookie: access_token=JWT; HttpOnly; SameSite=Lax
    AuthSvc-->>Browser: Trả về { TokenResponse } kèm Set-Cookie header
    
    Note over Browser: Lưu token vào state, chuyển về trang / (UsersPage)
    
    Browser->>Gateway: GET /identity/api/v1/admin/users
    Note over Browser: Trình duyệt tự động đính kèm Cookie: access_token
    
    Note over Gateway: TokenRelayFilter đổi Cookie thành Authorization: Bearer JWT
    Gateway->>AuthSvc: GET /api/v1/admin/users (kèm Authorization header)
    Note over AuthSvc: Xác thực JWT & phân quyền ADMIN thành công
    AuthSvc-->>Browser: Trả về dữ liệu danh sách Users (200 OK)
```

---

## 📝 Mô tả chi tiết từng bước

### Bước 1: Khởi động Đăng nhập
Khi người dùng bấm vào nút "Continue with Google", Frontend gửi yêu cầu lên API `/identity/api/v1/auth/google/init`.
- `identity-service` sẽ xây dựng URL dẫn tới Keycloak Auth Endpoint:
  ```
  http://localhost:8088/realms/datn-realm/protocol/openid-connect/auth
    ?client_id=datn-backend
    &response_type=code
    &scope=openid+profile+email
    &redirect_uri=http://localhost:5173/auth/callback
    &kc_idp_hint=google
  ```
- Việc truyền thêm tham số `kc_idp_hint=google` chỉ thị cho Keycloak tự động bỏ qua giao diện đăng nhập của nó để nhảy thẳng sang Google.

### Bước 2: Keycloak làm Identity Broker
Keycloak đóng vai trò là bên trung gian ủy quyền:
1. Chuyển hướng người dùng tới máy chủ OAuth2 của Google.
2. Người dùng đăng nhập tài khoản Google của họ.
3. Google trả về thông tin tài khoản cho Keycloak.
4. Keycloak tự động tạo tài khoản tương ứng bên trong Realm `datn-realm` (nếu đây là lần đăng nhập đầu tiên) và cấp một mã **Authorization Code** riêng của Keycloak gửi lại cho trình duyệt.

### Bước 3: Đổi mã Token và Đồng bộ CSDL (Sync DB)
Trình duyệt SPA bắt được mã code của Keycloak tại URL `/auth/callback?code=...` và gửi POST lên `/identity/api/v1/auth/google/callback`:
1. **Đổi Code**: `identity-service` dùng RestTemplate gửi POST lên `/openid-connect/token` của Keycloak để đổi mã code lấy cặp token JWT.
2. **Lấy UserInfo**: Sử dụng Access Token để gọi endpoint `/userinfo` của Keycloak để lấy thông tin chi tiết (Email, Họ tên, ID Keycloak).
3. **Đồng bộ local DB**: Hàm `UserService.findOrCreateGoogleUser()` được gọi:
   - Nếu `keycloakId` đã tồn tại trong bảng `users`, trả về user hiện tại.
   - Nếu Email đã tồn tại (do trước đó đăng ký thường), thực hiện liên kết tài khoản bằng cách cập nhật `keycloakId` mới.
   - Nếu hoàn toàn mới, tiến hành `INSERT` người dùng mới vào bảng `users` với trạng thái `ACTIVE` và quyền mặc định `USER`.
4. **Thiết lập Cookie**: Backend sinh Cookie `access_token` chứa Access Token JWT dưới dạng **HttpOnly** để gửi về trình duyệt.

### Bước 4: Chuyển tiếp Token qua API Gateway (Token Relay)
Khi gọi các API cần xác thực (ví dụ: `/identity/api/v1/admin/users`):
1. Trình duyệt tự động gửi kèm cookie `access_token` nhờ cấu hình `credentials: 'include'` trên `fetch`.
2. Tại Gateway, **`CookieOrBearerTokenConverter`** đọc cookie để xác thực tại Gateway.
3. Bộ lọc **`TokenRelayFilter`** (Global Filter) kiểm tra và tự động chuyển đổi cookie thành header `Authorization: Bearer <JWT>` để truyền xuống các downstream microservice, giúp bảo vệ an toàn cho các kết nối nội bộ.
