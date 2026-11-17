# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Đồ án tốt nghiệp DATN-2026: hệ thống quản lý công việc kiểu Jira. Tài liệu, commit và giao tiếp đều bằng **tiếng Việt**.

## Cấu trúc monorepo

- `backend/` — Maven multi-module (Java 21, Spring Boot 3.5.4, Spring Cloud 2025.0.0): `common`, `api-gateway` (:8081), `identity-service` (:8082), `work-service` (:8083), `workspace-service` (:8087), `ai-service` (:8084, trợ lý AI dùng Gemini, route `/ai/**`), `notification-service` (:9994, thông báo trong ứng dụng qua Kafka + WebSocket và email SMTP; REST/WebSocket đi qua gateway `/notification/**`, riêng `/internal/**` gửi email nội bộ thì không).
- `frontend/` — React 19 + Vite 8, JS/JSX thuần (không TypeScript, không Tailwind, không thư viện UI/state). Dev server :5173.
- `infrastructure/` (ở root) — toàn bộ docker-compose, Keycloak realm export, hướng dẫn setup. Các file này từng nằm ở `backend/environment/`; sau khi chuyển ra root, `context:` trong compose trỏ tới `../backend` và `../frontend`.
- `docs/` — đặc tả kiến trúc, DB, luồng đăng nhập, kịch bản test (`docs/services/work_service/*`). `community-service`, `integration-notification-service` chỉ tồn tại trong docs, chưa có code. Trợ lý AI: `docs/ai-agent/AI_AGENT.html` (kiến trúc, bảng tool → endpoint → quyền, SSE, cách chạy) và `docs/ai-agent/PLAN.md` (kế hoạch + nhật ký thực thi).

## Lệnh thường dùng

### Backend
Máy hiện tại không có Maven trên PATH và `JAVA_HOME=D:\jdk-17`, nhưng có sẵn JDK 21 của IntelliJ tại `C:\Users\anh.nvt\.jdks\ms-21.0.12` và Maven 3.9.16 do wrapper tải về. Compile/test backend không cần cài gì thêm (đặt JAVA_HOME cho riêng lệnh đó):

```bash
cd backend
export JAVA_HOME="C:\\Users\\anh.nvt\\.jdks\\ms-21.0.12"
/c/Users/anh.nvt/.m2/wrapper/dists/apache-maven-3.9.16/56ba1f9f/bin/mvn.cmd -q -o -pl ai-service -am compile   # -o offline; bỏ -o khi thêm dependency mới
```
`backend/mvnw` hỏng vì `.mvn/` bị gitignore ở root. Cách khác là build qua Docker:

```bash
cd infrastructure
docker compose -f docker-compose.tuananh.yml up -d                       # file CÁ NHÂN: CHỈ hạ tầng (DB, Keycloak 9998, Mailpit, Redis, Kafka, MinIO, pgAdmin); `name: environment` để tái dùng volume cũ
docker compose -f docker-compose.tuananh.yml --profile app up -d --build # thêm identity/gateway/notification trong Docker (mặc định chạy từ IntelliJ)
```
Ba file `docker-compose.yml` / `.app.yml` / `.infra.yml` là bản gốc của nhóm (cổng 808x, image `minio/minio` đã bị gỡ khỏi Docker Hub) — **không đổi cổng/minio**; đã có notification-service + Mailpit (chỉ thêm, không sửa phần cũ).

**Cổng dùng chung (808x) vs cá nhân (999x):** mọi `application.yml`, `vite.config.js`, `nginx.conf`, realm export dùng cổng 808x của nhóm. Máy này bị trùng cổng 808x nên dùng lớp phủ cá nhân, không sửa file chung:
- Backend: profile `tuananh` (`application-tuananh.y*ml` trong 5 service) → gateway 9991, identity 9992, work 9993, ai 9995, workspace 9997, notification 9994 (không cần profile), Keycloak 9998. Profile này ghi đè TOÀN BỘ `routes` của gateway: thêm service mới phải thêm route vào cả hai file. Chạy từ IDE với `-Dspring.profiles.active=tuananh`; `docker-compose.tuananh.yml` đã set `SPRING_PROFILES_ACTIVE`.
- Trợ lý AI cần 2 biến môi trường (không commit): `GEMINI_API_KEY` (ai-service) và `AI_TOOLS_API_KEY` (cùng giá trị ở ai-service **và** work-service; work-service dùng nó để nhận lời gọi `/internal/ai-tools/**` từ ai-service). Thiếu `GEMINI_API_KEY` thì app vẫn boot, chỉ lượt chat báo lỗi.
- Frontend: `frontend/.env.local` (gitignored) đặt `VITE_GATEWAY_URL` và `VITE_KEYCLOAK_URL`; `vite.config.js` và `App.jsx` đọc hai biến này, mặc định 8081/8088.
- Keycloak Admin Console cá nhân: http://localhost:9998 (nhóm: 8088). Google Console cần có cả hai redirect URI.
Dockerfile mỗi service dùng build context là `backend/` (compose khai báo `context: ..`) và phải COPY đủ **7 pom** (common, identity, gateway, work, workspace, notification, ai) + `common/src` vì parent pom liệt kê mọi module. Thêm module mới = sửa cả 5 Dockerfile còn lại (quên là `mvn` trong image fail ngay: `Child module ... does not exist`).

Email dev: compose infra có **Mailpit** (SMTP :1025, xem mail tại http://localhost:8025). SMTP thật cấu hình qua env `MAIL_HOST/PORT/USERNAME/PASSWORD/SMTP_AUTH/STARTTLS/FROM` của notification-service.

Nếu có JDK 21 + Maven, chạy từ `backend/`:
```bash
mvn -pl work-service -am test                                        # test một module
mvn -pl work-service test -Dtest=ProjectPermissionGuardTest           # một class
mvn -pl work-service test -Dtest=LexoRankServiceTest#tenMethod        # một method
mvn -q -o validate -pl identity-service,api-gateway,work-service -am  # kiểm tra pom nhanh
```
Test backend là unit thuần (JUnit 5, Mockito, AssertJ, MockMvc standalone), không cần DB/Keycloak. Test nằm ở `work-service`, `notification-service` và `workspace-service` (`src/test`).

### Frontend
Cần Node `^20.19` hoặc `>=22.12` (Vite 8 dùng `util.styleText`); Node 20.11 sẽ lỗi ngay khi `npm run dev`.
```bash
cd frontend
npm install
npm run dev      # vite, proxy /identity, /work, /workspace, /notification/ (có ws) -> http://127.0.0.1:8081 (gateway)
npm run build
npm run lint     # eslint flat config
```
Không có test frontend.

## Kiến trúc

### Luồng request
Frontend → API Gateway (:8081) → service. Gateway (`api-gateway/src/main/resources/application.yml`) route `/identity/api/**` → :8082, `/work/api/**`, `/work/ws-work/**` → :8083, `/workspace/api/**` → :8087 và `/notification/api/**`, `/notification/ws/**` → :9994, đều `StripPrefix=1`. Frontend dùng các hằng `AUTH_BASE = /identity/api/v1/auth`, `WORK_BASE = /work/api/v1/work`, `NOTIF_BASE = /notification/api/v1/notifications` trong `frontend/src/api.js`. `api-gateway/config/CorsConfig.java` chỉ cho Origin `localhost:5173` và `localhost:3000`: mở frontend bằng `127.0.0.1` hoặc cổng khác thì request ghi và cả WebSocket bị 403.

### Xác thực
- Keycloak :8088, realm `datn-realm`, client `datn-backend`, realm role `ADMIN`/`USER`/`USER_CREATE`. Cả 3 service là OAuth2 resource server với cùng issuer.
- Gateway nhận JWT từ header `Authorization` **hoặc** cookie httpOnly `access_token` (`CookieOrBearerTokenConverter`), rồi `TokenRelayFilter` chuyển thành Bearer cho downstream.
- Frontend (`src/api.js`) giữ hai bộ token trong `localStorage` (`user_*` và `admin_*`), chọn theo path `/admin`; interceptor tự refresh khi 401, bóc wrapper `["java.util.ArrayList", [...]]` của Jackson, và tự decode JWT (không dùng `keycloak-js`).
- `identity-service` mở `/internal/**` không cần token để `work-service` gọi qua Feign (`work-service/.../client/IdentityClient.java`). `identity-service` gọi `notification-service` bằng RestTemplate (`client/NotificationClient.java`, fail-safe: lỗi mail chỉ log) cho email xác thực / đặt lại mật khẩu. Thông báo trong ứng dụng: xem mục **Thông báo** bên dưới.
- **Xác thực email**: đăng ký tạo Keycloak user `enabled=false` + `users.status=INACTIVE`, gửi link `{app.frontend-url}/verify-email?token=` (bảng `verification_tokens`, `AccountVerificationService`). Login bị chặn 403 khi INACTIVE (`UserService.assertEmailVerified`). Quên mật khẩu: `/auth/forgot-password` → link `/reset-password?token=` (30 phút) → `/auth/reset-password`. User Google không qua bước này.
- **Lời mời**: `POST /projects/{id}/members` (work-service, bảng `invitations` V4, accept tại `/invitations/{id}/accept`) và `POST /workspaces/{id}/members` (workspace-service, bảng `workspace_invitations` V4, accept tại `/api/v1/workspace-invitations/{id}/accept`, ADMIN xem lời mời chờ tại `/workspaces/{id}/members/invitations`) đều KHÔNG thêm trực tiếp mà tạo lời mời; chấp nhận mới có `role_assignments`/`workspace_members`. Lời mời phát thông báo G1 (dự án) / G8 (workspace) qua outbox; notification-service hiện trong chuông và gửi email, link `/invitations` → route phẳng `/invitations` redirect vào `/w/:id/invitations`. Frontend gộp 2 loại (WORKSPACE/PROJECT) ở `WorkspaceLayout.fetchInvitations`; `WorkspaceListPage` hiện lời mời workspace cho user chưa có workspace nào.

### Thông báo
- Luồng: nghiệp vụ ghi `outbox_events` **cùng transaction** → `OutboxRelay` (work-service, mỗi giây, `FOR UPDATE SKIP LOCKED`; cũng gửi hộ sự kiện workspace-service ghi vào bảng dùng chung) → Kafka `notification-events` → notification-service lưu bảng `notifications`, đẩy STOMP `/user/queue/notifications`, gửi email. Lỗi lặp lại → `notification-events.DLT`.
- Hợp đồng sự kiện ở `common/.../notification` (`NotificationEvent`, `NotificationType` 53 loại, `RecipientReason`). Bên phát tính người nhận (Keycloak `sub`), bên nhận dựng nội dung tiếng Việt ở `catalog/NotificationCatalog`. Phát từ `WorkItemNotifier` / `ProjectNotifier` (work-service), `WorkspaceNotificationPublisher` (workspace-service); job nhắc hạn 08:00 ở `NotificationJobService` (chạy tay: `POST /api/v1/work/admin/notification-jobs/{job}/run`, cần realm role ADMIN).
- Frontend: `src/notifications/` (`NotificationProvider` đặt trong `AuthedShell` của `App.jsx`), trang `/w/:id/notifications`, link công việc dạng `/browse/{mã}[?comment=]` → `?item=` của trang dự án. Bình luận nhắc tên lưu dạng `@[Tên](keycloak-sub)` (`MentionTextarea`, `MentionParser`).
- Cài đặt bật / tắt theo nhóm × kênh và tắt tiếng dự án áp ở `NotificationPreferenceService` (một số loại bị khoá luôn hiện). Chạy từ 2 instance thì bật `app.cluster.redis-enabled=true` (đẩy WebSocket qua Redis Pub/Sub, khoá job `cluster/ClusterJobLock`); mặc định tắt, một instance không cần Redis.
- Thêm loại mới / đường đổi trạng thái mới: xem `docs/services/notification/3_TRIEN_KHAI_VA_KIEM_THU.md` §8.
- Có class mới trong `common` thì service chạy bằng `spring-boot:run -pl <module>` phải `install -pl common` trước (`start-dev.ps1` đã làm), nếu không lỗi `NoClassDefFoundError`. Đóng gói fat jar nhớ `clean package ... -am`.

### User id = Keycloak `sub` (toàn hệ thống) — bẫy lớn nhất
- **Mọi bảng nghiệp vụ dùng Keycloak `sub`** làm user id: work-service (V2 `authz_hardening`), workspace-service (V6 `unify_user_id_keycloak_sub`, trước đó lưu `users.id` nên từng lẫn hai loại), notification-service, sự kiện outbox. Người gọi lấy thẳng từ `sub` của JWT: `SecurityUtils.getCurrentUserId()` (work), `CurrentUserResolver` (workspace), `NotificationController.me()` (notification) — không gọi identity.
- `users.id` (id riêng của identity-service) **chỉ dùng bên trong identity-service**; `users.keycloak_id` nối sang `sub`. API của identity trả `id` = `users.id` kèm `keycloakId`: id nhận từ identity (tìm người dùng, `/users/me`) phải đổi sang `keycloakId` trước khi ghi / so sánh — `ProjectMemberServiceImpl.canonicalUserId()`, `IdentityUsers.indexByAnyId` (tra tên hàng loạt), frontend dùng `currentUser.keycloakId` hoặc `getUserKeycloakId()` (không dùng `currentUser.id`).
- Rủi ro: identity ghi đè `keycloak_id` khi email khớp tài khoản Keycloak khác → dữ liệu gắn `sub` cũ (xem `docs/AUTHORIZATION.md` §5.2). Debug quyền / membership không khớp thì kiểm tra điểm này trước.

### Mô hình phân quyền work-service (project-scoped, fail-closed)
- Không có entity Permission; nguồn sự thật là enum `PermissionKey`. Mỗi project có 3 `ProjectRole` mặc định (ADMINISTRATOR/MEMBER/VIEWER) + `PermissionSchemeEntry` (project × role × key) + `RoleAssignment` (project × user × role). Seed ở `ProjectServiceImpl` khi tạo project: người tạo hoặc Project Lead được tự động gán vai trò ADMINISTRATOR; các thành viên khác được mời và phân quyền theo RoleAssignment.
- Quyết định cuối ở `ProjectMemberServiceImpl.hasPermission()`: lead/creator được mọi quyền; không có `RoleAssignment` → từ chối; `PROJECT_VIEW` cho mọi member; ADMINISTRATOR có tất cả; còn lại tra scheme.
- Ba đường enforce: (1) `@RequireProjectPermission` / `@RequireTaskPermission` do aspect `ProjectPermissionGuard` xử lý — resolve projectId theo thứ tự `@PathVariable("projectId")` → tham số UUID có tên chứa "project" → DTO có `getProjectId()`; không resolve được thì từ chối. Cần `maven-compiler-plugin` `<parameters>true</parameters>` để đọc tên tham số. (2) `@RequireWorkspaceAccess` bảo vệ ở cấp độ workspace. (3) `ProjectAccessService.assert*()` trong service layer cho các quyền có phạm vi sở hữu (`COMMENT_EDIT_OWN`, `ATTACHMENT_DELETE_OWN`…).
- Realm role `ADMIN` (global admin) bypass toàn bộ kiểm tra project.

### Dữ liệu
- Một DB Postgres `datn_main_db` (:5433) dùng chung cho identity, work, workspace và notification; identity và work dùng `ddl-auto: update`. Bảng `outbox_events` dùng chung cho work-service và workspace-service. work-service còn chạy Flyway (`work-service/src/main/resources/db/migration`, bảng lịch sử `flyway_work_schema_history`). Thêm migration mới phải chọn version chưa dùng.
- work-service dùng JPA Specification (`specification/*`) cho filter động, Redis :6380, MinIO :9000 (bucket `work-attachments`).

## Trạng thái hiện tại cần biết trước khi sửa
- Migration work-service: V1, V2 (`authz_hardening`), V3 (`add_project_id_to_statuses`), V4 (`invitations`), V5 (`phase6_sprint_board_agile`), V6 (`project_templates`), V7 (`seed_default_templates`), V8 (`drop_team_and_restructure_projects`), V9 (`contribution_management`), V10 (`work_item_completion_requests`); thêm mới dùng V11 trở đi.
- **"Tạm hoàn thành"**: chỉ Admin dự án (`PROJECT_ADMIN`) mới đưa công việc vào nhóm DONE; người khác chỉ tạo yêu cầu chờ duyệt (`WorkItemCompletionGate`, chặn ngay trong `WorkflowEngineServiceImpl.executeTransition`). Mọi đường đổi trạng thái mới phải gọi `completionGate.onStatusChanged(...)`. Giao/hủy giao qua `PUT /items/{id}/assignee` (luôn ghi lịch sử). Chi tiết: `docs/services/work_service/8_QUY_TAC_GIAO_VIEC_VA_XAC_NHAN_HOAN_THANH.md`. workspace-service (Flyway riêng `flyway_workspace_schema_history`, `ddl-auto: validate`): V1–V4 (`workspace_invitations`), V5 (`drop_teams_fk`), V6 (`unify_user_id_keycloak_sub`); thêm mới dùng V7. ai-service (Flyway riêng `flyway_ai_schema_history`, `ddl-auto: validate`): V1 (`ai_agent_core`, 6 bảng `ai_*`); thêm mới dùng V2. identity-service không dùng Flyway (`ddl-auto: update` tự tạo `verification_tokens`). notification-service: Flyway riêng `flyway_notification_schema_history`, `ddl-auto: validate`, V1 (`notifications`), V2 (`notification_preferences`, `notification_project_mutes`); thêm mới dùng V3.
- **Trợ lý AI** (`backend/ai-service`, package `com.datn.aiservice`): vòng lặp agent ở `agent/AgentOrchestrator`; tool của work-service khai báo trong `resources/agent-tools.yml` (một lớp `RemoteAgentTool` chung, gọi `POST /internal/ai-tools/{path}` kèm `X-API-Key` + `X-User-Id`); phía work-service là `controller/internal/InternalAiToolController` + `service/ai/AiToolService` (kiểm `hasPermission(projectId, userId, key)` cho từng tool) + `security/AiToolApiKeyFilter` (dựng Jwt giả chỉ có `sub`, nên Global ADMIN không bypass khi đi qua AI – chủ ý). Thêm tool mới = thêm mục trong yml + một method/endpoint ở work-service. Frontend: `components/ai/*`, `hooks/useAiChat.js`, `context/AiChatContext.jsx`, `aiApi` trong `src/api.js` (SSE đọc bằng `fetch`).
- **Dự án mẫu** = `Project` có `is_template=true`; tạo dự án từ mẫu / lưu thành mẫu / nhân bản đều đi qua `service/template/ProjectCloneEngine` (deep-clone + ánh xạ ID + dời ngày) và `TemplateIntegrityValidator`. Thêm bảng con mới của project thì phải bổ sung vào `ProjectBlueprint`, loader và engine, nếu không mẫu sẽ bỏ sót bảng đó. Chi tiết: `docs/services/work_service/template/`.
- Thành viên Workspace (vai trò MEMBER, ADMIN, OWNER) hoặc Global ADMIN đều có thể tạo dự án trong Workspace đó. Đã loại bỏ hoàn toàn tầng trung gian Team (Flyway V8), kiến trúc phẳng 2 cấp gồm Workspace -> Projects -> Tasks.
- `work-service` **chưa có trong bất kỳ docker-compose nào**, và compose chỉ set env `SPRING_CLOUD_GATEWAY_ROUTES_0_*` (identity) nên gateway trong container mất route `/work/**` và `/ai/**`. `frontend/nginx.conf` đã proxy `/identity/`, `/work/`, `/workspace/`, `/notification/` (có nâng cấp WebSocket), `/ai/`. Kafka có 2 listener: `localhost:9092` cho service chạy trên máy, `kafka:29092` cho container trong mạng `app-net`; dữ liệu ở volume `kafka_data` (`KAFKA_LOG_DIRS`, trước đây nằm trong container nên tạo lại là mất). Container hạ tầng đang chạy thuộc project `environment`: áp dụng thay đổi bằng `docker compose -p environment -f docker-compose.infra.yml up -d <service>`. `ai-service` có trong `docker-compose.tuananh.yml` (profile `app`, trỏ work-service về `host.docker.internal:9993`).
- `frontend/src/api/workServiceApi.js` là bản trùng lặp của `src/api.js`, không ai import; dùng `src/api.js`.
- Client secret `datn-backend` phải khớp giữa `identity-service/application.yaml`, compose và realm export (`infrastructure/keycloak/realm-export_08082026.json`). Import realm: `POST http://localhost:8088/admin/realms` với token admin-cli của realm master.

## Quy tắc làm việc với Claude Code
- **Không tự chạy lệnh can thiệp sâu vào hệ thống.** Với các lệnh cài đặt hoặc thay đổi máy (winget/choco/msi, cài hay gỡ Node/JDK/Maven, sửa PATH hoặc biến môi trường, sửa registry, xóa volume/image Docker, `docker system prune`, `git push --force`, `git reset --hard`, xóa DB), chỉ đưa lệnh và hướng dẫn để người dùng tự chạy. Được phép tự chạy các thao tác trong phạm vi repo và container dev: sửa file, `npm install`, `docker compose up/down`, chạy test, đọc log.
