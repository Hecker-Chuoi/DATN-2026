# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Đồ án tốt nghiệp DATN-2026: hệ thống quản lý công việc kiểu Jira. Tài liệu, commit và giao tiếp đều bằng **tiếng Việt**.

## Cấu trúc monorepo

- `backend/` — Maven multi-module (Java 21, Spring Boot 3.5.4, Spring Cloud 2025.0.0): `common`, `api-gateway` (:8081), `identity-service` (:8082), `work-service` (:8083), `notification-service` (:9994, chỉ gửi email qua SMTP, không đi qua gateway).
- `frontend/` — React 19 + Vite 8, JS/JSX thuần (không TypeScript, không Tailwind, không thư viện UI/state). Dev server :5173.
- `infrastructure/` (ở root) — toàn bộ docker-compose, Keycloak realm export, hướng dẫn setup. Các file này từng nằm ở `backend/environment/`; sau khi chuyển ra root, `context:` trong compose trỏ tới `../backend` và `../frontend`.
- `docs/` — đặc tả kiến trúc, DB, luồng đăng nhập, kịch bản test (`docs/services/work_service/*`). `community-service`, `integration-notification-service`, `ai-service` chỉ tồn tại trong docs, chưa có code.

## Lệnh thường dùng

### Backend
Máy hiện tại **không có Maven và chỉ có JDK 17** (`JAVA_HOME=D:\jdk-17`), trong khi dự án cần JDK 21. `backend/mvnw` cũng hỏng vì `.mvn/` bị gitignore ở root. Vì vậy build/test backend chỉ chắc chắn chạy được qua Docker:

```bash
cd infrastructure
docker compose -f docker-compose.tuananh.yml up -d --build   # file CÁ NHÂN: cổng 999x, MinIO bitnamilegacy, Mailpit, notification-service, redis, kafka
```
Ba file `docker-compose.yml` / `.app.yml` / `.infra.yml` là bản gốc của nhóm (cổng 808x, image `minio/minio` đã bị gỡ khỏi Docker Hub, chưa có notification-service) — **không sửa**.

**Cổng dùng chung (808x) vs cá nhân (999x):** mọi `application.yml`, `vite.config.js`, `nginx.conf`, realm export dùng cổng 808x của nhóm. Máy này bị trùng cổng 808x nên dùng lớp phủ cá nhân, không sửa file chung:
- Backend: profile `tuananh` (`application-tuananh.y*ml` trong 3 service) → gateway 9991, identity 9992, work 9993, Keycloak 9998. Chạy từ IDE với `-Dspring.profiles.active=tuananh`; `docker-compose.tuananh.yml` đã set `SPRING_PROFILES_ACTIVE`.
- Frontend: `frontend/.env.local` (gitignored) đặt `VITE_GATEWAY_URL` và `VITE_KEYCLOAK_URL`; `vite.config.js` và `App.jsx` đọc hai biến này, mặc định 8081/8088.
- Keycloak Admin Console cá nhân: http://localhost:9998 (nhóm: 8088). Google Console cần có cả hai redirect URI.
Dockerfile mỗi service dùng build context là `backend/` (compose khai báo `context: ..`) và phải COPY đủ **5 pom** (common, identity, gateway, work, notification) + `common/src` vì parent pom liệt kê mọi module. Thêm module mới = sửa cả 4 Dockerfile còn lại.

Email dev: compose infra có **Mailpit** (SMTP :1025, xem mail tại http://localhost:8025). SMTP thật cấu hình qua env `MAIL_HOST/PORT/USERNAME/PASSWORD/SMTP_AUTH/STARTTLS/FROM` của notification-service.

Nếu có JDK 21 + Maven, chạy từ `backend/`:
```bash
mvn -pl work-service -am test                                        # test một module
mvn -pl work-service test -Dtest=ProjectPermissionGuardTest           # một class
mvn -pl work-service test -Dtest=LexoRankServiceTest#tenMethod        # một method
mvn -q -o validate -pl identity-service,api-gateway,work-service -am  # kiểm tra pom nhanh
```
Test backend là unit thuần (JUnit 5, Mockito, AssertJ, MockMvc standalone), không cần DB/Keycloak. Gần như toàn bộ test nằm ở `work-service/src/test`.

### Frontend
Cần Node `^20.19` hoặc `>=22.12` (Vite 8 dùng `util.styleText`); Node 20.11 sẽ lỗi ngay khi `npm run dev`.
```bash
cd frontend
npm install
npm run dev      # vite, proxy /identity và /work -> http://127.0.0.1:8081 (gateway)
npm run build
npm run lint     # eslint flat config
```
Không có test frontend.

## Kiến trúc

### Luồng request
Frontend → API Gateway (:8081) → service. Gateway (`api-gateway/src/main/resources/application.yml`) route `/identity/api/**` → :8082 và `/work/api/**`, `/work/ws-work/**` → :8083, đều `StripPrefix=1`. Frontend dùng hai hằng `AUTH_BASE = /identity/api/v1/auth` và `WORK_BASE = /work/api/v1/work` trong `frontend/src/api.js`.

### Xác thực
- Keycloak :8088, realm `datn-realm`, client `datn-backend`, realm role `ADMIN`/`USER`/`USER_CREATE`. Cả 3 service là OAuth2 resource server với cùng issuer.
- Gateway nhận JWT từ header `Authorization` **hoặc** cookie httpOnly `access_token` (`CookieOrBearerTokenConverter`), rồi `TokenRelayFilter` chuyển thành Bearer cho downstream.
- Frontend (`src/api.js`) giữ hai bộ token trong `localStorage` (`user_*` và `admin_*`), chọn theo path `/admin`; interceptor tự refresh khi 401, bóc wrapper `["java.util.ArrayList", [...]]` của Jackson, và tự decode JWT (không dùng `keycloak-js`).
- `identity-service` mở `/internal/**` không cần token để `work-service` gọi qua Feign (`work-service/.../client/IdentityClient.java`). `identity-service` gọi `notification-service` bằng RestTemplate (`client/NotificationClient.java`, fail-safe: lỗi mail chỉ log). Kafka/outbox/WebSocket mới có bean/entity, chưa có code publish.
- **Xác thực email**: đăng ký tạo Keycloak user `enabled=false` + `users.status=INACTIVE`, gửi link `{app.frontend-url}/verify-email?token=` (bảng `verification_tokens`, `AccountVerificationService`). Login bị chặn 403 khi INACTIVE (`UserService.assertEmailVerified`). Quên mật khẩu: `/auth/forgot-password` → link `/reset-password?token=` (30 phút) → `/auth/reset-password`. User Google không qua bước này.
- **Lời mời**: `POST /teams/{id}/members` và `POST /projects/{id}/members` không thêm trực tiếp mà tạo `invitations` (V4); người được mời chấp nhận qua `/invitations/{id}/accept` mới có `team_members`/`role_assignments`.

### Hai loại userId — bẫy lớn nhất
`identity-service` có bảng `users` với `id` riêng, còn **mọi bảng của `work-service` dùng Keycloak `sub`** làm `user_id` (`SecurityUtils.getCurrentUserId()`). Khi nhận id từ identity phải quy đổi qua `ProjectMemberServiceImpl.canonicalUserId()` trước khi ghi. Migration `V2__authz_hardening.sql` đã rewrite dữ liệu cũ sang chuẩn này. Khi debug quyền/membership không khớp, kiểm tra điểm này trước.

### Mô hình phân quyền work-service (project-scoped, fail-closed)
- Không có entity Permission; nguồn sự thật là enum `PermissionKey`. Mỗi project có 3 `ProjectRole` mặc định (ADMINISTRATOR/MEMBER/VIEWER) + `PermissionSchemeEntry` (project × role × key) + `RoleAssignment` (project × user × role). Seed ở `ProjectServiceImpl` khi tạo project; `syncTeamMembersToProject()` map `TeamRole` LEADER→ADMINISTRATOR, VIEWER→VIEWER, còn lại MEMBER.
- Quyết định cuối ở `ProjectMemberServiceImpl.hasPermission()`: lead/creator được mọi quyền; không có `RoleAssignment` → từ chối; `PROJECT_VIEW` cho mọi member; ADMINISTRATOR có tất cả; còn lại tra scheme.
- Ba đường enforce: (1) `@RequireProjectPermission` / `@RequireTaskPermission` do aspect `ProjectPermissionGuard` xử lý — resolve projectId theo thứ tự `@PathVariable("projectId")` → tham số UUID có tên chứa "project" → DTO có `getProjectId()`; không resolve được thì từ chối. Cần `maven-compiler-plugin` `<parameters>true</parameters>` để đọc tên tham số. (2) `@PreAuthorize("@teamSecurity.isMember(#teamId)")` cho Team. (3) `ProjectAccessService.assert*()` trong service layer cho các quyền có phạm vi sở hữu (`COMMENT_EDIT_OWN`, `ATTACHMENT_DELETE_OWN`…).
- Realm role `ADMIN` (global admin) bypass toàn bộ kiểm tra project.

### Dữ liệu
- Một DB Postgres `datn_main_db` (:5433) dùng chung cho identity và work; cả hai đều `ddl-auto: update`. work-service còn chạy Flyway (`work-service/src/main/resources/db/migration`, bảng lịch sử `flyway_work_schema_history`). Thêm migration mới phải chọn version chưa dùng.
- work-service dùng JPA Specification (`specification/*`) cho filter động, Redis :6380, MinIO :9000 (bucket `work-attachments`).

## Trạng thái hiện tại cần biết trước khi sửa
- Migration hiện có V1, V2 (`authz_hardening`), V3 (`add_project_id_to_statuses`), V4 (`invitations`); thêm mới dùng V5 trở đi. identity-service không dùng Flyway (`ddl-auto: update` tự tạo `verification_tokens`).
- Chỉ trưởng nhóm (owner/LEADER của team) hoặc Global ADMIN tạo được dự án; dự án không gắn team chỉ ADMIN tạo được. Xóa team cần `teamSecurity.isLeader` (không còn `isOwner`).
- `work-service` **chưa có trong bất kỳ docker-compose nào**, và compose chỉ set env `SPRING_CLOUD_GATEWAY_ROUTES_0_*` (identity) nên gateway trong container mất route `/work/**`. `frontend/nginx.conf` cũng chỉ proxy `/identity/`, thiếu `/work/`.
- `frontend/src/api/workServiceApi.js` là bản trùng lặp của `src/api.js`, không ai import; dùng `src/api.js`.
- Client secret `datn-backend` phải khớp giữa `identity-service/application.yaml`, compose và realm export (`infrastructure/keycloak/realm-export_08082026.json`). Import realm: `POST http://localhost:8088/admin/realms` với token admin-cli của realm master.

## Quy tắc làm việc với Claude Code
- **Không tự chạy lệnh can thiệp sâu vào hệ thống.** Với các lệnh cài đặt hoặc thay đổi máy (winget/choco/msi, cài hay gỡ Node/JDK/Maven, sửa PATH hoặc biến môi trường, sửa registry, xóa volume/image Docker, `docker system prune`, `git push --force`, `git reset --hard`, xóa DB), chỉ đưa lệnh và hướng dẫn để người dùng tự chạy. Được phép tự chạy các thao tác trong phạm vi repo và container dev: sửa file, `npm install`, `docker compose up/down`, chạy test, đọc log.
