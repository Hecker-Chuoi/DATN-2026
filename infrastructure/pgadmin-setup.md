# Hướng Dẫn Sử Dụng pgAdmin

### 1. Thông Tin Truy Cập pgAdmin
- **URL:** [http://localhost:5050](http://localhost:5050)
- **Port:** `5050`
- **Email:** `admin@admin.com`
- **Password:** `admin`

---

### 2. Hướng Dẫn Kết Nối Database trong pgAdmin

Sau khi đăng nhập vào pgAdmin, click chuột phải vào **Servers** (ở thanh bên trái) ➔ **Register** ➔ **Server...**:

#### A. Kết nối tới Main DB (`datn_main_db`)
- **Tab `General`:**
  - **Name:** `App DB` (hoặc tên tùy chọn)
- **Tab `Connection`:**
  - **Host name/address:** `app-db`
  - **Port:** `5432`
  - **Maintenance database:** `datn_main_db`
  - **Username:** `postgres`
  - **Password:** `123456`
  - *(Tích chọn "Save password?" để không phải nhập lại)*

#### B. Kết nối tới Keycloak DB (`keycloak`)
- **Tab `General`:**
  - **Name:** `Keycloak DB`
- **Tab `Connection`:**
  - **Host name/address:** `keycloak-db`
  - **Port:** `5432`
  - **Maintenance database:** `keycloak`
  - **Username:** `kc_user`
  - **Password:** `kc_password`
  - *(Tích chọn "Save password?")*
