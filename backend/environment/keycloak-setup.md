# Hướng dẫn Cài đặt & Cấu hình Keycloak (datn-realm)

Tài liệu này hướng dẫn cách cấu hình Keycloak bằng file export có sẵn: [`realm-export_08082026.json`](file:///d:/DATN-2026/backend/environment/keycloak/realm-export_08082026.json).

---

## Thông tin môi trường Keycloak

- **URL Admin Console**: [http://localhost:9998](http://localhost:9998)
- **Tài khoản quản trị Keycloak (Master Realm)**:
  - **Username**: `admin`
  - **Password**: `admin`
- **Tên Realm dự án**: `datn-realm`
- **File cấu hình Realm**: `backend/environment/keycloak/realm-export_08082026.json`

---

## Cách 1: Import Realm qua giao diện Keycloak Admin Console (Khuyên dùng)

Nếu container Keycloak đã chạy (`docker compose -f docker-compose.infra.yml up -d`), bạn có thể import trực tiếp qua giao diện:

1. Mở trình duyệt truy cập: **[http://localhost:9998](http://localhost:9998)**
2. Đăng nhập với tài khoản:
   - **Username**: `admin`
   - **Password**: `admin`
3. Ở góc trên cùng bên trái màn hình, nhấn vào dropdown chọn Realm (mặc định đang là `master`) ➔ Chọn **Create Realm**.
4. Tại ô **Resource file**, bấm **Browse** và tìm chọn file:
   ```text
   d:\DATN-2026\backend\environment\keycloak\realm-export_08082026.json
   ```
5. Keycloak sẽ tự động nhận diện Realm name là **`datn-realm`**.
6. Nhấn nút **Create**. Toàn bộ Roles, Clients, và cấu hình sẽ được import thành công.

---

## Các cấu hình quan trọng trong `datn-realm`

### 1. Client `datn-backend` (Dùng cho Backend Spring Boot)
- **Client ID**: `datn-backend`
- **Client authentication**: `On` (Confidential)
- **Valid redirect URIs**:
  - `http://localhost:5173/*`
  - `http://localhost:3000/*`
  - `http://localhost:9991/*`
- **Web Origins**: `*` hoặc `http://localhost:5173`, `http://localhost:3000`
- **Client Secret**: Kiểm tra tại tab **Credentials** ➔ `pBchukVams8pHwBHnPvzTY86e3ou2Ym7` *(trùng với file `application.yaml` của `identity-service`)*.

---

### 2. Cấu hình Đăng nhập Google (Google Identity Provider)

Để tính năng đăng nhập Google hoạt động:

1. Vào Keycloak Admin ➔ chọn realm **`datn-realm`**.
2. Menu trái chọn **Identity Providers** ➔ Chọn **Google** (hoặc bấm Add provider ➔ Google nếu chưa có).
3. Copy đường dẫn **Redirect URI** hiển thị trên Keycloak:
   ```text
   http://localhost:9998/realms/datn-realm/broker/google/endpoint
   ```
4. Vào [Google Cloud Console](https://console.cloud.google.com/) ➔ **APIs & Services** ➔ **Credentials**:
   - Thêm Redirect URI trên vào danh sách **Authorized redirect URIs**.
   - Copy **Client ID** và **Client Secret** từ Google Cloud Console.
5. Dán Client ID & Client Secret vào trang cấu hình Google Provider trên Keycloak ➔ Bấm **Save**.

---

### 3. Tạo User thử nghiệm trong Keycloak

1. Vào **`datn-realm`** ➔ Chọn mục **Users** ở menu bên trái.
2. Bấm **Add user**:
   - **Username**: `testuser`
   - **Email**: `testuser@example.com`
   - **Email verified**: Bật `Yes`
   - Bấm **Create**.
3. Chuyển sang tab **Credentials** của user:
   - Bấm **Set password**
   - Nhập mật khẩu (ví dụ: `123456`)
   - Tắt mục **Temporary** (để không bắt đổi mật khẩu khi đăng nhập lần đầu)
   - Bấm **Save**.
4. Chuyển sang tab **Role mapping**:
   - Bấm **Assign role** ➔ Chọn role **`USER`** hoặc **`ADMIN`** ➔ Bấm **Assign**.
