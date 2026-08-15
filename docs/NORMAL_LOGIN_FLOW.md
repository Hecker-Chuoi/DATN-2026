# Normal Login Flow – Username & Password Authentication

Tài liệu này mô tả chi tiết luồng xác thực bằng **Tài khoản & Mật khẩu** trực tiếp (Direct Access Grant / Resource Owner Password Credentials Flow) thông qua Keycloak và cơ chế bảo mật Cookie của hệ thống **DATN-2026**.

---

## 🏗️ Sơ đồ tuần tự (Sequence Diagram)

Sơ đồ dưới đây mô tả luồng xác thực trực tiếp từ màn hình đăng nhập:

```mermaid
sequenceDiagram
    autonumber
    actor User as Người dùng
    participant Browser as Trình duyệt (SPA)
    participant Gateway as API Gateway (:8081)
    participant AuthSvc as Identity Service (:8082)
    participant Keycloak as Keycloak (:8088)

    User->>Browser: Nhập Username/Password & bấm "Sign In"
    Browser->>Gateway: POST /identity/api/v1/auth/login { username, password }
    Gateway->>AuthSvc: Forward request
    
    AuthSvc->>Keycloak: POST /openid-connect/token (Direct Grant)<br/>kèm client_id & client_secret (Confidential Client)
    
    alt Thông tin tài khoản chính xác (200 OK)
        Keycloak-->>AuthSvc: Trả về JWT Token Pair (Access Token, Refresh Token)
        Note over AuthSvc: Tạo Cookie: access_token=JWT; HttpOnly; SameSite=Lax; Path=/
        AuthSvc-->>Browser: Trả về { TokenResponse } kèm Set-Cookie header
        Note over Browser: Đăng nhập thành công, lưu token vào state
    else Sai mật khẩu hoặc tài khoản (401 Unauthorized)
        Keycloak-->>AuthSvc: Trả về 401 Unauthorized
        AuthSvc->>AuthSvc: Ném BadCredentialsException
        AuthSvc-->>Browser: Trả về { success: false, message: "Invalid username or password" }
        Note over Browser: Hiển thị thông báo lỗi lên màn hình Login
    end

    Note over Browser: Người dùng truy cập trang Dashboard
    Browser->>Gateway: GET /identity/api/v1/admin/users
    Note over Browser: Trình duyệt tự động đính kèm Cookie: access_token
    
    Note over Gateway: TokenRelayFilter đổi Cookie thành Authorization: Bearer JWT
    Gateway->>AuthSvc: GET /api/v1/admin/users (kèm Authorization header)
    Note over AuthSvc: Xác thực JWT & phân quyền ADMIN thành công
    AuthSvc-->>Browser: Trả về dữ liệu danh sách Users (200 OK)
```

---

## 📝 Mô tả chi tiết từng bước

### Bước 1: Người dùng nhập thông tin đăng nhập
Người dùng điền `username` và `password` vào form đăng nhập trên giao diện Web và nhấp nút "Sign In". Trình duyệt SPA gửi yêu cầu HTTP POST lên API `/identity/api/v1/auth/login`.

### Bước 2: Xác thực trực tiếp với Keycloak (Direct Access Grant)
Hệ thống sử dụng cơ chế **Direct Access Grant** (Resource Owner Password Credentials) của Keycloak:
1. `identity-service` tiếp nhận thông tin và gửi yêu cầu POST đến endpoint `/protocol/openid-connect/token` của Keycloak.
2. Vì `datn-backend` là một **Confidential Client**, yêu cầu này bắt buộc phải gửi kèm theo tham số `client_secret` (được cấu hình trong `application.yaml` từ tab Credentials trên Keycloak admin):
   ```
   POST http://localhost:8088/realms/datn-realm/protocol/openid-connect/token
   Content-Type: application-x-www-form-urlencoded
   
   grant_type=password
   client_id=datn-backend
   client_secret=aHyLbQ4knDq8VdIr5...
   username=devuser
   password=Password123
   ```
3. Keycloak xác thực cặp thông tin đăng nhập trong Realm `datn-realm`:
   - **Thành công**: Trả về Access Token (JWT), Refresh Token và thời gian hết hạn (`expires_in`).
   - **Thất bại**: Trả về mã lỗi `401 Unauthorized` kèm theo thông điệp `invalid_grant`.

### Bước 3: Đóng gói Token và Thiết lập Cookie bảo mật
Khi Keycloak xác thực thành công:
1. `identity-service` đóng gói Access Token thành đối tượng **`ResponseCookie`**:
   - `HttpOnly=true`: Ngăn chặn JavaScript truy cập (chống đánh cắp token qua XSS).
   - `Secure=false`: Cho phép chạy thử nghiệm trên HTTP ở `localhost` (sẽ được set `true` khi lên môi trường HTTPS production).
   - `SameSite=Lax`: Cho phép trình duyệt gửi cookie trên các chuyển hướng thông thường để tích hợp SSO.
   - `Path=/`: Cookie có hiệu lực trên toàn bộ các đường dẫn của domain.
2. Thiết lập header `Set-Cookie: access_token=...` trực tiếp trên phản hồi `ResponseEntity` để trình duyệt tự động lưu trữ.

### Bước 4: Đăng xuất và huỷ Cookie
Khi người dùng bấm vào nút **Sign out** (Đăng xuất):
1. Trình duyệt gửi yêu cầu POST lên `/identity/api/v1/auth/logout`.
2. Backend sinh một cookie có tên `access_token` mới nhưng có thuộc tính `Max-Age=0` và gửi về qua header `Set-Cookie`.
3. Trình duyệt nhận được chỉ thị hết hạn lập tức và **xoá bỏ** cookie này khỏi bộ nhớ của thiết bị.
4. Trình duyệt chuyển hướng người dùng về màn hình đăng nhập.
