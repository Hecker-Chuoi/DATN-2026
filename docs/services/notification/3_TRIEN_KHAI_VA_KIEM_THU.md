# 3. Triển khai và kiểm thử hệ thống thông báo

> Trạng thái: **đã triển khai** trên nhánh `develop` (14/11/2026). Tài liệu này mô tả phần đã làm thật,
> khác gì so với thiết kế ở [2_KIEN_TRUC_VA_THIET_KE.md](2_KIEN_TRUC_VA_THIET_KE.md), cách chạy, cách kiểm thử
> và cách thêm loại thông báo mới.

## 1. Đối chiếu với thiết kế

| Hạng mục thiết kế | Trạng thái | Ghi chú |
|---|---|---|
| N1: bảng `notifications`, REST API, chuông, trang đầy đủ, `/browse/:code`, `?item=` | Xong | |
| N2: Outbox → Kafka → consumer, sự kiện P0, @nhắc tên | Xong | |
| N3: WebSocket STOMP, đồng bộ nhiều tab | Xong | Hỏi định kỳ 30 giây chỉ chạy khi mất WebSocket |
| N4: job theo lịch, email | Xong | Một mẫu email chung `notification.html` thay vì mỗi loại một mẫu |
| N5: loại P1 / P2, gộp, workspace-service G8–G11 | Xong | Đủ **53/53** loại trong danh mục |
| N5: cài đặt bật / tắt (`notification_preferences`) | Xong (§10.1) | Theo nhóm × kênh, tắt tiếng dự án; một số loại bị khoá |
| Redis làm backplane WebSocket / khoá job | Xong (§10.2), **mặc định tắt** | Bật `app.cluster.redis-enabled=true` khi chạy từ 2 instance; relay outbox vốn đã an toàn nhờ `FOR UPDATE SKIP LOCKED` |

Tên lớp khác thiết kế: `NotificationTemplateRegistry` → `catalog/NotificationCatalog`; `NotificationQueryService` →
`service/NotificationService`; các `*Job` của work-service gộp vào `notification/NotificationJobService` +
`NotificationScheduler`; `AdminJobController` → `controller/NotificationJobController`.

## 2. Luồng chạy

```
work-service / workspace-service
  nghiệp vụ (cùng transaction) ──► outbox_events (PENDING)
                                        │  OutboxRelay (work-service, mỗi giây, FOR UPDATE SKIP LOCKED)
                                        ▼
                         Kafka topic notification-events  (key = projectId / workspaceId)
                                        │  lỗi lặp lại → notification-events.DLT
                                        ▼
notification-service: NotificationEventListener → NotificationIngestService
  dựng nội dung (NotificationCatalog) · chống trùng (dedup_key) · gộp 10 phút · đóng "Cần xử lý"
                                        │ sau khi commit
                     ┌──────────────────┼───────────────────┐
                     ▼                  ▼                   ▼
        bảng notifications    STOMP /user/queue/notifications   email (Mailpit khi dev)
                                        │
                     gateway /notification/ws  ──►  trình duyệt (NotificationProvider)
```

- **Bên phát tính người nhận**, bên nhận dựng nội dung tiếng Việt. Người nhận luôn là Keycloak `sub`;
  workspace-service đổi id của identity sang `sub` trước khi ghi outbox.
- workspace-service không có relay riêng: nó ghi vào bảng `outbox_events` dùng chung, relay của work-service gửi đi.
  **Tắt work-service thì sự kiện của workspace-service cũng nằm chờ** (không mất).

## 3. Bản đồ mã nguồn

| Nơi | Thành phần |
|---|---|
| `common/.../notification` | `NotificationEvent` (hợp đồng sự kiện), `NotificationType` (53 loại + 3 loại nội bộ chỉ dùng để đóng "Cần xử lý"), `RecipientReason` (lý do nhận, mạnh nhất thắng) |
| work-service `notification/` | `NotificationEventPublisher` (dựng + lọc người nhận), `NotificationRecipientResolver`, `MentionParser`, `WorkItemNotifier` (A, B, C, D), `ProjectNotifier` (E, G, H, I), `NotificationJobService` + `NotificationScheduler` (F, E3, E4, E6, C4) |
| work-service khác | `service/OutboxRelay`, `service/OutboxRecorder`, `config/KafkaProducerConfig`, `controller/NotificationJobController` |
| workspace-service | `notification/WorkspaceNotificationPublisher` (G8–G11) |
| notification-service | `catalog/*`, `kafka/*`, `service/NotificationIngestService`, `NotificationService`, `NotificationPushService`, `NotificationMailer`, `controller/NotificationController`, `config/SecurityConfig`, `config/WebSocketConfig`, `ws/StompAuthChannelInterceptor`, `job/NotificationCleanupJob`, `db/migration/V1__notifications.sql` |
| frontend `src/notifications/` | `NotificationContext` (nguồn dữ liệu chung + WebSocket), `notificationSocket`, `NotificationBell`, `NotificationPanel`, `NotificationItem`, `notificationFormat`, `browseLink` |
| frontend khác | `pages/NotificationsPage`, `pages/BrowseItemRedirect`, `components/common/MentionTextarea`, `utils/mentions.jsx`; sửa `App`, `Sidebar`, `WorkspaceLayout`, `ProjectDetailPage`, `WorkItemDetailModal` |

## 4. Cấu hình

| Service | Thuộc tính | Mặc định | Ý nghĩa |
|---|---|---|---|
| work-service | `app.outbox.relay-enabled` | `true` | Bật relay outbox → Kafka |
| | `app.outbox.relay-interval-ms` | `1000` | Chu kỳ quét outbox |
| | `app.notifications.jobs-enabled` | `true` | Bật job nhắc hạn theo lịch |
| | `app.notifications.jobs-cron` | `0 0 8 * * *` | Giờ chạy job (múi giờ `Asia/Ho_Chi_Minh`) |
| notification-service | `app.notifications.kafka-enabled` | `true` | Bật consumer |
| | `app.notifications.group-window-minutes` | `10` | Cửa sổ gộp thông báo chưa đọc (R4) |
| | `app.notifications.purge-deleted-after-days` | `30` | Xoá hẳn thông báo đã xoá mềm (hết hạn hoàn tác) |
| | `app.notifications.purge-read-after-days` | `90` | Xoá hẳn thông báo đã đọc lâu ngày |
| | `app.frontend-url` | `http://localhost:5173` | Gốc đường dẫn trong email; cũng là Origin được phép mở WebSocket |
| | `app.services.identity.url` | `http://localhost:8082` | Lấy tên / email người dùng |
| | `spring.kafka.consumer.properties.session.timeout.ms` | `10000` | Instance mới nhận partition nhanh sau khi service bị tắt đột ngột |
| | `app.notifications.cleanup-cron` | `0 0 3 * * *` | Giờ chạy job dọn dẹp |
| | `app.cluster.redis-enabled` (env `APP_CLUSTER_REDIS_ENABLED`) | `false` | Chạy nhiều instance: đẩy WebSocket qua Redis Pub/Sub, khoá job dọn dẹp |
| | `spring.data.redis.host` / `port` (env `SPRING_DATA_REDIS_HOST` / `PORT`) | `localhost` / `6380` | Chỉ dùng khi bật chế độ cụm (kết nối lười) |
| work-service | `app.cluster.redis-enabled` | `false` | Chạy nhiều instance: khoá job nhắc hạn 08:00 (Redis :6380 có sẵn) |

Flyway của notification-service dùng bảng lịch sử riêng `flyway_notification_schema_history`, `ddl-auto: validate`;
thêm migration mới dùng **V2** trở đi.

## 5. API và WebSocket (qua gateway)

| Phương thức | Đường dẫn `/notification/api/v1/notifications…` | Mô tả |
|---|---|---|
| GET | `?filter=all\|unread\|action&category=&projectId=&cursor=&size=` | Danh sách, phân trang theo con trỏ, kèm `unreadCount`, `actionCount` |
| GET | `/unread-count` | `{ total, action }` |
| PATCH | `/{id}` body `{ "read": true\|false }` | Đánh dấu đã đọc / chưa đọc |
| POST | `/read-all?filter=&category=&projectId=` | Đọc tất cả theo bộ lọc |
| DELETE | `/{id}` | Xoá mềm (hoàn tác được trong 30 ngày) |
| POST | `/{id}/restore` | Hoàn tác xoá |
| DELETE | `?filter=read` | Xoá mọi thông báo đã đọc |
| GET | `/preferences` | 9 nhóm (`inApp`, `email`, `inAppLocked`, `emailAvailable`, `alwaysInApp`) + dự án đang tắt tiếng |
| PUT | `/preferences` body `{"categories":[{"category":"COMMENT","inApp":false}]}` | Lưu cài đặt; trường bỏ trống = giữ nguyên |
| PUT / DELETE | `/preferences/projects/{projectId}/mute` body `{"projectName","until"}` | Tắt / bật lại thông báo một dự án (`until` null = đến khi bật lại) |

WebSocket: `ws(s)://<host>/notification/ws`, token gửi trong header của frame STOMP `CONNECT`
(không đặt trên URL), đăng ký `/user/queue/notifications`. Thông điệp: `CREATED`, `UPDATED`, `READ` / `UNREAD` (kèm `ids`),
`DELETED` (kèm `ids`), `RESTORED`; mọi thông điệp kèm `unreadCount`, `actionCount`.
`/notification/internal/**` (email nội bộ cho identity-service) **cố ý không route** qua gateway.

## 6. Chạy ở máy dev

1. Hạ tầng Docker phải có Postgres, Kafka, Mailpit (compose `infrastructure/`).
2. `.\start-dev.ps1`: script **cài module `common` trước** rồi mới mở các service. Nếu chạy tay
   `mvnw spring-boot:run -pl <module>`, phải `mvnw install -pl common -DskipTests` trước, nếu không service lỗi
   `NoClassDefFoundError: com/datn/common/notification/...` (lấy bản `common` cũ trong `~/.m2`).
3. Frontend `npm run dev`: Vite proxy `/notification/` (có dấu `/` cuối để không nuốt route giao diện `/notifications`, bật `ws`).
4. **Mở giao diện bằng `http://localhost:5173`.** `api-gateway/config/CorsConfig.java` chỉ cho Origin `localhost:5173` và
   `localhost:3000`; mở bằng `127.0.0.1` hoặc cổng khác thì mọi request ghi (POST/PATCH/DELETE) và **cả WebSocket** bị 403.
5. Docker: notification-service trong container đọc `kafka:9092`; Kafka của compose hiện chỉ quảng bá `localhost:9092`,
   cần thêm listener nội bộ thì container mới nhận được sự kiện (chi tiết §6.2). Chạy service trên máy (`start-dev.ps1`) thì không bị.

### 6.1. Deploy tất cả trên một server

Đã thử gateway thật với header nginx gửi khi deploy (request ghi và WebSocket có token hợp lệ):

| Cách truy cập | Gateway như hiện tại | Thêm `server.forward-headers-strategy: framework` |
|---|---|---|
| Dev `http://localhost:5173` | Qua | Qua |
| `http://<domain hoặc IP>` cổng 80 | Qua | Qua |
| `https://<domain>` cổng 443 | **403** (cả WebSocket) | Qua |
| `http://<domain>:8080` (cổng khác 80) | **403** (cả WebSocket) | Qua (nginx gửi thêm `X-Forwarded-Port`) |

Lý do: trình duyệt gửi `Origin` thật, còn gateway nhận request từ nginx qua `http`, cổng 8081 nên tưởng là gọi chéo
nguồn và đối chiếu với danh sách cứng trong `CorsConfig` (chỉ `localhost:5173`, `localhost:3000`).

**Đã sửa:**
- `api-gateway/application.yml`: `server.forward-headers-strategy: framework`. Chỉ an toàn khi cổng 8081 của gateway
  **không mở ra Internet**, mọi request đều đi qua nginx.
- `frontend/nginx.conf`: gửi `X-Forwarded-Proto` và `X-Forwarded-Host` (= `$http_host`, giữ cả cổng khi Docker map
  8080→80). Nếu phía trước còn proxy / CDN làm HTTPS (Cloudflare, nginx của máy chủ) thì giữ nguyên giá trị nó gửi.

Đã thử qua nginx thật (`nginx:alpine` với chính file này) tới gateway thật: `http://domain`, `http://domain:8080`,
`https://domain` (proxy phía trước) và WebSocket qua HTTPS đều qua; Origin lạ (`https://evil.com`) vẫn bị 403;
dev qua Vite (`localhost:5173`) không đổi.

Khi deploy vẫn phải đặt cho notification-service `APP_FRONTEND_URL` = địa chỉ thật người dùng mở (là Origin được phép
mở WebSocket và gốc link trong email).

### 6.2. Kafka khi service chạy trong Docker

Kafka trả cho client địa chỉ nó "quảng bá" (`KAFKA_ADVERTISED_LISTENERS`). Hiện chỉ có `localhost:9092`: service
trên máy host dùng tốt, nhưng container khác nhận về `localhost` (chính nó) nên lỗi
`Connection to node 1 (localhost/127.0.0.1:9092) could not be established`. Cách sửa đã thử trên một Kafka riêng
(container khác gửi và nhận được; máy host vẫn dùng `localhost:9092` như cũ):

```yaml
kafka:
  networks: [app-net]          # docker-compose.infra.yml: kafka đang ở mạng mặc định, các service ở app-net
  environment:
    KAFKA_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093,INTERNAL://:29092
    KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092,INTERNAL://kafka:29092
    KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,INTERNAL:PLAINTEXT
# service trong container: SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:29092
```

`docker-compose.yml` (bản gộp của nhóm) hiện **không có** service `kafka`; work-service và workspace-service chưa có trong
compose nào. Container Kafka đang chạy được tạo với project `environment`, nên áp dụng bằng
`docker compose -p environment -f docker-compose.infra.yml up -d kafka` (dữ liệu nằm trong volume, không mất).

Chạy job bằng tay (cần token có realm role `ADMIN`):

```
POST /work/api/v1/work/admin/notification-jobs/{job}/run[?projectId=…]
job = all | due-reminders | sprint-deadlines | completion-reminders | project-digest
```

## 7. Kiểm thử đã chạy

| Mức | Nội dung | Kết quả |
|---|---|---|
| Unit | notification-service 148 test, work-service 286 test, workspace-service 46 test | Đạt; mỗi commit build và test được độc lập |
| Tích hợp notification-service | Kafka + Postgres thật: bảo mật REST / WebSocket, chống trùng, gộp, đóng "Cần xử lý", đẩy WebSocket, REST đọc / xoá / hoàn tác / đọc tất cả, R6, DLT (33 kiểm tra) | Đạt, cả gọi thẳng lẫn qua gateway |
| Tích hợp work-service | Job theo lịch trên dự án mẫu (đúng số, đúng người, chạy lại không trùng), thao tác thật (giao việc, nhắc tên, từ chối xác nhận, xoá việc), outbox PROCESSED | Đạt |
| Giao diện end-to-end | Edge headless qua gateway và đủ 5 service, 3 người dùng (52 kiểm tra, mục 7.1) | Đạt |
| Giao diện cài đặt | Bật / tắt theo nhóm, loại bị khoá vẫn tới, tắt trong ứng dụng nhưng bật email (bản ẩn, không gửi trùng khi job chạy lại), tắt / bật tiếng dự án từ trang dự án và màn cài đặt, lối tắt từ bảng nổi (24 kiểm tra) | Đạt |
| Nhiều instance | 2 notification-service + Redis: trình duyệt nối vào instance 19995 nhận đủ 12/12 thông báo kể cả phần instance 19994 xử lý; đối chứng tắt Redis chỉ nhận phần của 19995; job dọn dẹp mỗi lượt chỉ chạy một instance | Đạt |

### 7.1. Kịch bản giao diện

1. Chuông lên số ngay khi job chạy (WebSocket), badge ở sidebar khớp; đọc / chưa đọc đồng bộ với server; tab "Chưa đọc" bỏ thông báo vừa đọc.
2. Bấm thông báo → `/w/{ws}/projects/{id}/backlog?item=…`, chi tiết công việc tự mở; đóng thì bỏ `?item`.
3. Trang Thông báo: lọc nhóm, xoá + Hoàn tác, đọc tất cả, xoá mọi thông báo đã đọc (có hộp xác nhận), đường dẫn phẳng `/notifications`.
4. Trưởng dự án nhận yêu cầu xác nhận: toast hiện ngay, tab "Cần xử lý"; từ chối yêu cầu qua API thật → thông báo tự chuyển "Đã xử lý", số "Cần xử lý" về 0.
5. Gõ `@bì` gợi ý "Bình Trần" (không có chính mình), Enter chèn tên, server lưu `@[Bình Trần](sub)`.
6. Người được nhắc bấm thông báo → mở đúng công việc, chuyển tab Bình luận, cuộn tới và làm nổi bình luận, tên mình tô cam.
7. `/browse/MÃ-KHÔNG-CÓ` báo không tìm thấy; tắt notification-service → hiện "Đang kết nối lại", bật lại → tự nối và nhận thông báo mới; chưa đăng nhập mở `/browse/…` → về `/login` và nhớ đích; không có lỗi JavaScript.

## 8. Thêm một loại thông báo mới

1. `common`: thêm giá trị vào `NotificationType` (đặt đúng nhóm).
2. notification-service `NotificationCatalog`: `def(TYPE, CATEGORY, SEVERITY, c -> Rendered.of(tiêu đề, dòng phụ, link))`,
   thêm `.action()` / `.resolves(...)` / `.group(...)` / `.email(...)` nếu cần; thêm test vào `NotificationCatalogTest`
   (test kiểm tra mọi loại không nội bộ đều có định nghĩa).
3. Bên phát: gọi `publisher.event(TYPE, project).item(...).to(userId, reason).actor(actorId).publish()` **trong cùng transaction**
   với nghiệp vụ (thường đặt trong `WorkItemNotifier` / `ProjectNotifier`).
4. Đường đổi trạng thái công việc mới: ngoài `completionGate.onStatusChanged(...)` (xem `CLAUDE.md`) nhớ gọi notifier ở cùng chỗ.
5. Cập nhật [1_DANH_MUC_LOAI_THONG_BAO.md](1_DANH_MUC_LOAI_THONG_BAO.md) và [danh_muc_thong_bao.html](danh_muc_thong_bao.html).

## 9. Lưu ý vận hành

- Dòng outbox lỗi dữ liệu quá 10 lần chuyển `FAILED` (không tự gửi lại); Kafka sập thì relay chỉ lùi dần tối đa 30 giây, không tính là lỗi.
- Sự kiện lỗi (VD: JSON hỏng) không chặn hàng đợi: thử lại 3 lần cách nhau 2 giây rồi chuyển sang topic `notification-events.DLT`.
- Job theo lịch chạy lại trong ngày không tạo trùng (mỗi mốc nhắc có `dedupKey` riêng); thông báo người dùng đã xoá không hiện lại (R6).
- Tên người thao tác lấy từ identity-service; identity không chạy thì nội dung ghi "Một thành viên" thay vì tên.

## 10. Cài đặt bật / tắt và chạy nhiều instance (đã làm)

Đã triển khai theo kế hoạch dưới đây, với các điểm khác:
- **Tắt "trong ứng dụng" nhưng vẫn bật email**: vẫn lưu một bản ẩn (đánh dấu đã xoá) để `dedup_key` chặn gửi email trùng khi
  Kafka giao lại hoặc job chạy lại; bản ẩn không hiện, không đếm, không đẩy WebSocket.
- **Nút tắt tiếng dự án** là biểu tượng chuông cạnh nút "Yêu thích" ở đầu trang dự án (menu "…" chỉ hiện với quản trị dự án).
- **Khoá job** tự viết gọn (`cluster/ClusterJobLock`: `SET NX PX` + script Lua nhả khoá đúng chủ) thay vì thêm thư viện ShedLock;
  Redis lỗi thì job vẫn chạy (an toàn vì đều chạy lại được). **Job dọn outbox 03:30 không khoá**: chỉ là một câu `DELETE`
  có điều kiện, hai instance cùng chạy cũng vô hại.
- Thêm loại mới mà phải luôn hiện trong chuông: bổ sung vào `ALWAYS_IN_APP_LABELS` của `NotificationPreferenceService`.

### 10.1. Cài đặt bật / tắt thông báo

**Người dùng chỉnh gì.** Theo **nhóm** (9 nhóm, không bắt chỉnh từng loại trong 53 loại) × **kênh** (Trong ứng dụng,
Email), cộng **tắt tiếng một dự án**. Chưa có dòng cài đặt nào thì theo mặc định của danh mục (trong ứng dụng: bật hết;
email: 7 loại đang gửi).

**Không cho tắt** (khoá, hiện biểu tượng ổ khoá):
- Trong ứng dụng của 6 loại "Cần xử lý" (C1, C4, E3, E6, G1, G8): tắt đi thì người duyệt không biết có việc chờ.
- Trong ứng dụng của nhóm Thành viên & quyền (bị xoá khỏi dự án / không gian, đổi vai trò): liên quan quyền truy cập.
- Được nhắc tên (D1) và được giao việc (A1): chỉ cho tắt email, vẫn hiện trong chuông.

**Tắt tiếng dự án** bỏ mọi thông báo của dự án đó (nhắc hạn, đổi trạng thái, bình luận ở việc mình theo dõi, tổng hợp…)
**trừ** thông báo gửi đích danh, tức lý do nhận `MENTIONED`, `NEW_ASSIGNEE`, `TARGET_USER`, `REQUESTER`, `INVITEE`, và các
loại bị khoá ở trên.

**Dữ liệu** (notification-service, Flyway `V2__notification_preferences.sql`; thực tế thêm cột `project_name` để màn cài đặt hiện tên dự án):

```sql
notification_preferences (user_id UUID, category VARCHAR(32), in_app BOOLEAN, email BOOLEAN, updated_at TIMESTAMPTZ,
                          PRIMARY KEY (user_id, category))
notification_project_mutes (user_id UUID, project_id UUID, muted_until TIMESTAMPTZ NULL, created_at TIMESTAMPTZ,
                            PRIMARY KEY (user_id, project_id))   -- muted_until NULL = tắt đến khi bật lại
```

**Áp dụng ở đâu.** Chỉ ở `NotificationIngestService`, ngay trước khi tạo / gộp thông báo cho từng người nhận: tra một lần
cài đặt của mọi người nhận trong sự kiện (`findAllByUserIdIn`), tính "trong ứng dụng" và "email" thực tế theo danh mục +
cài đặt + khoá + tắt tiếng dự án. Tắt trong ứng dụng thì không lưu; tắt email thì không gửi mail. Bên phát (work-service,
workspace-service) không đổi gì. Việc đóng "Cần xử lý" (R9) vẫn chạy bình thường.

**API** `/api/v1/notifications/preferences`: `GET` (9 nhóm, mỗi nhóm `inApp`, `email`, `locked`, kèm danh sách dự án đang tắt
tiếng), `PUT` (lưu cả bảng), `PUT` / `DELETE /projects/{projectId}/mute`.

**Giao diện.** Tab "Cài đặt" trên trang Thông báo (và nút bánh răng ở bảng nổi): bảng 9 dòng × 2 công tắc, dòng bị khoá có
chú thích lý do. Menu "…" ở đầu trang dự án thêm "Tắt thông báo dự án này" / "Bật lại thông báo".

**Kiểm thử.** Unit cho phần tính cài đặt thực tế (mặc định, đã tắt, bị khoá, tắt tiếng dự án với từng lý do nhận); test
ingest (tắt trong ứng dụng thì không lưu, tắt email thì không gửi); test controller; E2E: tắt nhóm Bình luận → bình luận mới
không vào chuông nhưng nhắc tên vẫn vào; tắt tiếng dự án → nhắc hạn của dự án không vào, được giao việc vẫn vào.

### 10.2. Redis khi chạy nhiều instance (chỉ cần khi scale ngang)

Deploy một server, mỗi service một instance thì **chưa cần**. Khi chạy từ 2 instance trở lên sẽ gặp hai vấn đề:

1. **WebSocket lệch instance.** Broker STOMP hiện là bộ nhớ trong của từng instance. Kafka giao mỗi sự kiện cho đúng một
   instance (consumer group), nhưng trình duyệt có thể đang nối WebSocket vào instance khác, nên không nhận được đẩy ngay
   (chỉ thấy khi tải lại hoặc khi hỏi định kỳ).
   **Cách làm:** `NotificationPushService` sau khi commit không gửi thẳng mà publish `{recipientId, message}` lên kênh Redis
   `notif:push`; mọi instance đăng ký kênh này (`RedisMessageListenerContainer`) và gọi `convertAndSendToUser` tại chỗ
   (instance không giữ phiên của người đó thì lệnh này không làm gì). Bật bằng `app.notifications.push-mode: local | redis`,
   mặc định `local`. Phương án khác là STOMP broker relay sang RabbitMQ (chuẩn của Spring) nhưng phải thêm RabbitMQ vào hạ
   tầng; Redis đã có sẵn (:6380, work-service đang dùng) nên chọn Redis. Không cần sticky session vì dùng WebSocket thuần
   (không SockJS).
2. **Job chạy trùng.** `@Scheduled` chạy trên mọi instance: job nhắc hạn 08:00 (đã có `dedupKey` nên không trùng thông báo,
   nhưng tốn gấp đôi truy vấn và sự kiện), dọn outbox 03:30, dọn thông báo 03:00.
   **Cách làm:** ShedLock (`@SchedulerLock(name = …, lockAtMostFor = "10m")`) với provider Redis. `OutboxRelay` không cần vì
   đã dùng `FOR UPDATE SKIP LOCKED`.

**Kiểm thử.** Chạy 2 instance notification-service (9994, 9995) sau gateway có cân bằng tải: trình duyệt nối vào instance A,
dừng consumer của A để sự kiện rơi vào B, thông báo vẫn đẩy tới ngay. Chạy 2 instance work-service: log cho thấy job 08:00 chỉ
chạy một lần.
