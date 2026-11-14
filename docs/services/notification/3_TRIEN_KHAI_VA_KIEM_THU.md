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
| N5: cài đặt bật / tắt từng loại (`notification_preferences`) | **Chưa làm** | Mọi người nhận mọi loại theo danh mục |
| Redis làm backplane WebSocket / khoá job | **Chưa làm** | Chạy một instance mỗi service; relay outbox đã an toàn nhiều instance nhờ `FOR UPDATE SKIP LOCKED`, job theo lịch thì chưa |

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
   cần thêm listener nội bộ thì container mới nhận được sự kiện.

Chạy job bằng tay (cần token có realm role `ADMIN`):

```
POST /work/api/v1/work/admin/notification-jobs/{job}/run[?projectId=…]
job = all | due-reminders | sprint-deadlines | completion-reminders | project-digest
```

## 7. Kiểm thử đã chạy

| Mức | Nội dung | Kết quả |
|---|---|---|
| Unit | notification-service 113 test, work-service 274 test, workspace-service 46 test | Đạt; mỗi commit build và test được độc lập |
| Tích hợp notification-service | Kafka + Postgres thật: bảo mật REST / WebSocket, chống trùng, gộp, đóng "Cần xử lý", đẩy WebSocket, REST đọc / xoá / hoàn tác / đọc tất cả, R6, DLT (33 kiểm tra) | Đạt, cả gọi thẳng lẫn qua gateway |
| Tích hợp work-service | Job theo lịch trên dự án mẫu (đúng số, đúng người, chạy lại không trùng), thao tác thật (giao việc, nhắc tên, từ chối xác nhận, xoá việc), outbox PROCESSED | Đạt |
| Giao diện end-to-end | Edge headless qua gateway và đủ 5 service, 3 người dùng (52 kiểm tra, mục 7.1) | Đạt |

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
