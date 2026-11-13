# Kiến trúc & thiết kế hệ thống thông báo (DATN-2026)

> Thuộc bộ tài liệu `docs/services/notification/`. Danh mục 53 loại thông báo: [1_DANH_MUC_LOAI_THONG_BAO.md](1_DANH_MUC_LOAI_THONG_BAO.md) · bản tương tác: [danh_muc_thong_bao.html](danh_muc_thong_bao.html).
> Liên quan: [KE_HOACH_REALTIME_WORK_SERVICE.md](../work_service/realtime/myapp/KE_HOACH_REALTIME_WORK_SERVICE.md) (giai đoạn R3 "Sự kiện bền" chính là phần vận chuyển của tài liệu này), [8_QUY_TAC_GIAO_VIEC_VA_XAC_NHAN_HOAN_THANH.md](../work_service/8_QUY_TAC_GIAO_VIEC_VA_XAC_NHAN_HOAN_THANH.md).
> Đối chiếu source code nhánh `develop` tới commit `f303aa9` (14/11/2026). Trạng thái: **đề xuất, chưa triển khai.**

---

## 0. Tóm tắt quyết định

| # | Quyết định | Lý do |
|---|---|---|
| D1 | Lưu thông báo trong **PostgreSQL**, bảng `notifications` thuộc **notification-service** | Cần danh sách, trạng thái đã đọc / đã xoá, đếm số chưa đọc. Đây là nguồn dữ liệu chính |
| D2 | Chuyển sự kiện giữa các service bằng **Kafka qua Transactional Outbox** | Đã có sẵn khoảng 80% (container, `spring-kafka`, topic, bảng `outbox_events`). Notification-service tắt rồi bật lại vẫn nhận đủ sự kiện |
| D3 | **Phía phát tự tính danh sách người nhận** và đưa vào sự kiện | Chỉ work-service / workspace-service có đủ dữ liệu (NTH, NT, QL, thành viên). Notification-service không phải gọi ngược |
| D4 | Notification-service **dựng nội dung tiếng Việt, gộp, chống trùng, lưu, đẩy xuống trình duyệt, gửi email** | Một chỗ duy nhất quyết định thông báo trông thế nào |
| D5 | Đẩy xuống trình duyệt bằng **WebSocket + STOMP**, kênh `/user/queue/notifications` | Hiện ngay lập tức; xác thực ở frame `CONNECT` giống kế hoạch realtime |
| D6 | **Redis chỉ làm phụ trợ**: backplane Pub/Sub khi chạy nhiều instance, khoá cho job | Redis Pub/Sub không bền nên không làm đường truyền chính |
| D7 | Job nhắc hạn đặt ở **work-service** (`@Scheduled`) và **cũng ghi vào outbox** | Job nằm ở nơi có dữ liệu; đi chung một đường với sự kiện thường |
| D8 | **Một sự kiện sinh cả thông báo trong ứng dụng lẫn email**; bỏ các lời gọi email rải rác | Tránh lệch nhau giữa chuông và email |
| D9 | Đường dẫn chuẩn của công việc là **`/browse/{mã}`** | Không phụ thuộc chu kỳ hay Board; sửa luôn link email đang hỏng |
| D10 | **Xoá mềm** + "Hoàn tác" 5 giây; dọn hẳn sau 30 ngày | Xoá nhầm vẫn lấy lại được; job không tạo lại thông báo người dùng đã xoá |
| D11 | `recipient_id` luôn là **Keycloak `sub`** | Khớp `jwt.sub` khi đọc API; tránh bẫy hai loại userId |

---

## 1. Hiện trạng (đối chiếu code 14/11/2026)

### 1.1 Đã có

| Thành phần | Hiện trạng | Vị trí |
|---|---|---|
| Kafka | `apache/kafka:3.8.0` (KRaft), 3 partition mặc định. Chỉ quảng bá `PLAINTEXT://localhost:9092` | `infrastructure/docker-compose.infra.yml`, `docker-compose.tuananh.yml` |
| Producer Kafka (work-service) | `spring-kafka`, producer idempotent (`acks=all`), 3 topic `task-events-topic`, `comment-events-topic`, `project-events-topic` | `config/KafkaProducerConfig.java` |
| Outbox | Bảng `outbox_events` (V1) + `OutboxRecorder`. Chỉ `SprintServiceImpl` ghi `SPRINT_STARTED` / `SPRINT_COMPLETED`. **Chưa có relay** | `service/OutboxRecorder.java` |
| notification-service | Cổng 9994. Chỉ có `web` + `mail`; `InternalEmailController`, `EmailService`, `MailTemplateService`; 7 template email. **Không DB, không security, không đi qua gateway** | `backend/notification-service` |
| Email từ work-service | `InvitationMailer` (đang dùng). `TaskNotificationMailer` có 3 hàm (giao việc, nhắc tên, nhắc hạn) nhưng **chưa nơi nào gọi**; link trong mail là `/projects/item/{key}`, route này không có trên frontend | `service/InvitationMailer.java`, `service/TaskNotificationMailer.java` |
| Email từ workspace-service | `InvitationMailer` gửi lời mời | `workspace-service/.../service/InvitationMailer.java` |
| Giao / huỷ giao | `PUT /items/{id}/assignee` (BR-W01), mọi thay đổi đều ghi lịch sử `ASSIGNEE` (BR-W02) | `WorkItemServiceImpl.changeAssignee` |
| Tạm hoàn thành | Gate + service + API xác nhận / từ chối / rút lại / danh sách chờ; FE có `CompletionPendingPanel` và nút "Chờ xác nhận HT (n)". **Chưa thông báo cho Admin** (§8 tài liệu BR-W) | `WorkItemCompletionGate`, `WorkItemCompletionServiceImpl`, `WorkItemCompletionController` |
| Redis | `:6380`, làm cache, có `CacheErrorHandler` chịu lỗi | `config/RedisConfig.java` |
| Gateway | Đã route `/work/ws-work/**` (cho realtime) | `api-gateway/.../application*.yml` |
| Frontend | `Header.jsx` đã import icon `Bell` nhưng chưa hiển thị. Route phẳng `/invitations` đã có. `ProjectReportsPage` đọc `?tab=` và `?sprint=` | `frontend/src` |

### 1.2 Còn thiếu

| # | Thiếu | Ảnh hưởng |
|---|---|---|
| T1 | Bảng lưu thông báo, API đọc / đánh dấu / xoá | Không có chuông |
| T2 | Relay outbox → Kafka và consumer | Sự kiện nằm `PENDING` mãi |
| T3 | `@EnableScheduling` (không service nào có) | Không có nhắc hạn, chu kỳ quá hạn |
| T4 | WebSocket (`spring-boot-starter-websocket`) | Không đẩy ngay được |
| T5 | URL riêng cho chi tiết công việc (hiện chỉ là modal) | Thông báo và email không dẫn thẳng tới công việc |
| T6 | Tách @nhắc tên trong bình luận | Không làm được D1 |
| T7 | Security cho notification-service, route gateway `/notification/**` | Trình duyệt không gọi được |

---

## 2. Chọn công nghệ

### 2.1 Kafka, WebSocket, Redis nằm ở ba tầng khác nhau

```
 Tầng 1 – Phát & vận chuyển giữa service   Tầng 2 – Lưu trữ        Tầng 3 – Đẩy xuống trình duyệt
 ──────────────────────────────────────    ───────────────────     ─────────────────────────────
 Outbox (Postgres) → Kafka                 Postgres                WebSocket + STOMP
 (bền, đọc lại được, nhiều consumer)       notifications           (/user/queue/notifications)
                                                                   Redis Pub/Sub nếu nhiều instance
```

Không phải chọn một trong ba: **Kafka** lo vận chuyển bền giữa các service, **Postgres** lưu, **WebSocket** đẩy xuống trình duyệt, **Redis** hỗ trợ khi mở rộng.

### 2.2 So sánh cách vận chuyển sự kiện giữa service

| Phương án | Ưu | Nhược | Kết luận |
|---|---|---|---|
| Gọi REST/Feign trực tiếp (cách email đang làm) | Đơn giản, dễ debug | Notification-service sập là mất thông báo; các service gắn chặt vào nhau; gọi trong transaction thì làm chậm nghiệp vụ | Chỉ dùng làm dự phòng khi demo không bật Kafka |
| Spring event trong cùng service (`@TransactionalEventListener`) | Rất nhẹ | Chỉ dùng được trong một service; restart giữa chừng là mất | Dùng **bên trong** work-service để gom sự kiện, không dùng để truyền đi |
| Redis Pub/Sub | Có sẵn, rất nhanh | Gửi một lần rồi thôi: consumer offline là mất | Chỉ làm backplane cho WebSocket |
| Redis Streams | Bền, có consumer group | Phải tự quản lý ack / pending; ít công cụ; đã có Kafka | Không chọn |
| RabbitMQ | Hợp lý cho hàng đợi công việc | Chưa có trong hạ tầng, thêm một thứ phải vận hành | Không chọn |
| **Kafka + Transactional Outbox** | Bền, đọc lại được, giữ thứ tự theo key, thêm consumer mới (email, AI, automation) không phải sửa phía phát; **đã có container, thư viện, topic, bảng outbox** | Tốn RAM; cần relay; trễ thêm khoảng 1 giây do relay quét | **Chọn** |

### 2.3 So sánh cách đẩy xuống trình duyệt

| Phương án | Ưu | Nhược | Kết luận |
|---|---|---|---|
| Hỏi định kỳ (polling) | Dễ nhất, không cần hạ tầng | Trễ tới 30 giây; tốn request | Dùng ở giai đoạn N1 và làm lưới an toàn |
| Server-Sent Events | Một chiều là đủ cho thông báo | `EventSource` không gửi được header `Authorization`; FE đang giữ token trong `localStorage` | Không chọn |
| **WebSocket + STOMP** | Tức thì; `/user/queue/...` gửi riêng từng người; xác thực ở frame `CONNECT`; cùng cách làm với kế hoạch realtime `/ws-work` | Phải xử lý kết nối lại và token hết hạn | **Chọn** |

### 2.4 Vai trò của Redis

| Việc | Khi nào cần |
|---|---|
| Backplane Pub/Sub kênh `notif:push`: mọi instance notification-service nhận rồi đẩy cho các phiên WebSocket mình đang giữ | Chạy từ 2 instance trở lên |
| Khoá job (`SET lock:job:due-reminder NX EX 600`) hoặc dùng ShedLock | work-service chạy từ 2 instance trở lên |
| Cache số chưa đọc `notif:unread:{sub}` | Tuỳ chọn. Với quy mô DATN, `COUNT` có chỉ mục riêng là đủ |

---

## 3. Kiến trúc tổng thể

```
┌──────────────── work-service ─────────────────┐   ┌──────── workspace-service ────────┐
│ ServiceImpl (@Transactional)                   │   │ WorkspaceMemberServiceImpl        │
│   └ NotificationEventPublisher ──┐             │   │   └ NotificationEventPublisher ─┐ │
│ Job 08:00 (nhắc hạn, chu kỳ, tổng hợp) ──┐     │   │                                 │ │
│                                  ▼       ▼     │   │                                 ▼ │
│              outbox_events (cùng transaction)  │   │      outbox_events (cùng transaction)│
│                        │                       │   │                 │                 │
│   OutboxRelay (@Scheduled 1 giây,              │   │   OutboxRelay ──┘                 │
│   FOR UPDATE SKIP LOCKED) ─────┐               │   └───────────────│───────────────────┘
└────────────────────────────────│───────────────┘                   │
                                 ▼                                   ▼
                  Kafka topic "notification-events" (key = projectId / workspaceId)
                                 │
┌──────────────── notification-service ─────────▼────────────────────────────────────────┐
│ @KafkaListener (group "notification-service")                                           │
│  ① bỏ người thao tác, chọn lý do mạnh nhất, dựng nội dung (NotificationTemplateRegistry)│
│  ② INSERT notifications … ON CONFLICT (dedup_key) DO NOTHING  /  gộp theo group_key       │
│  ③ sau commit: STOMP /user/{sub}/queue/notifications  (nhiều instance: Redis Pub/Sub)     │
│  ④ email cho loại có cấu hình (EmailService + template sẵn có)                           │
│ REST /api/v1/notifications/**    WebSocket /ws    Job dọn dẹp 03:00                       │
└──────────────────────────────────────────────────────────────────────────────────────────┘
                                 ▲ qua API Gateway: /notification/api/**, /notification/ws/**
┌──────────────── Trình duyệt ──┴──────────────────────────────────────────────────────────┐
│ Chuông + bảng thông báo · trang /w/:ws/notifications · route /browse/:code                │
└──────────────────────────────────────────────────────────────────────────────────────────┘
```

### 3.1 Một sự kiện đi qua hệ thống: "An giao MOC-2 cho Bình"

| Bước | Ở đâu | Việc xảy ra |
|---|---|---|
| 1 | work-service | `changeAssignee` lưu `assignee_id`, ghi lịch sử `ASSIGNEE`, gọi `notificationEvents.taskAssigned(item, oldAssignee, actor)`: ghi 2 dòng outbox (`TASK_ASSIGNED` cho Bình, `TASK_UNASSIGNED` cho người cũ nếu có) **trong cùng transaction** |
| 2 | work-service | Commit. Nếu nghiệp vụ lỗi thì outbox cũng rollback: không bao giờ báo một thay đổi chưa xảy ra |
| 3 | `OutboxRelay` | Trong ≤ 1 giây, khoá dòng `PENDING`, gửi lên Kafka, đánh dấu `PROCESSED` |
| 4 | notification-service | Nhận sự kiện, bỏ An (người thao tác), dựng tiêu đề "An đã giao cho bạn MOC-2 · …", `INSERT` với `dedup_key = eventId:sub-Bình` |
| 5 | notification-service | Sau commit: đẩy `{kind: "CREATED", notification, unreadCount}` tới `/user/sub-Bình/queue/notifications`; gửi email `task-assigned` |
| 6 | Trình duyệt của Bình | Số trên chuông tăng; mở bảng thấy thông báo; bấm vào thì đánh dấu đã đọc và đi tới `/browse/MOC-2` |

Nếu notification-service đang tắt ở bước 4, sự kiện nằm chờ trong Kafka; bật lên là xử lý tiếp. Nếu Kafka giao trùng, `dedup_key` UNIQUE chặn bản trùng.

---

## 4. Hợp đồng sự kiện

### 4.1 Topic

| Topic | Partition | Key | Ghi chú |
|---|---|---|---|
| `notification-events` (mới) | 3 | `projectId` (sự kiện cấp không gian làm việc dùng `workspaceId`) | Giữ thứ tự trong một dự án. Ba topic cũ giữ nguyên cho sự kiện nghiệp vụ sau này |
| `notification-events.DLT` | 1 | như trên | Sự kiện xử lý lỗi quá 3 lần |

### 4.2 Phong bì

```json
{
  "eventId": "3c1e9a5e-…",
  "type": "TASK_ASSIGNED",
  "occurredAt": "2026-11-14T09:12:03+07:00",
  "source": "work-service",
  "actorId": "keycloak-sub-của-An",
  "workspaceId": "…",
  "projectId": "…",
  "project": { "key": "MOC", "name": "Mộc Coffee" },
  "entity": { "type": "WORK_ITEM", "id": "…", "code": "MOC-2", "title": "Khảo sát lưu lượng khách và lựa chọn mặt bằng" },
  "recipients": [ { "userId": "keycloak-sub-của-Bình", "reason": "NEW_ASSIGNEE" } ],
  "data": { "priority": "HIGH", "dueDate": "2026-11-20", "sprintName": "Giai đoạn 1" },
  "dedupKey": null
}
```

- `eventId` = `outbox_events.id`.
- `recipients[].reason`: `MENTIONED`, `NEW_ASSIGNEE`, `PREVIOUS_ASSIGNEE`, `ASSIGNEE`, `REPORTER`, `PROJECT_MANAGER`, `MEMBER`, `REQUESTER`, `INVITEE`, `INVITER`, `TARGET_USER`. Dùng cho quy tắc R3 (chọn lý do mạnh nhất).
- `data` chỉ chứa id, enum, ngày, số và chuỗi ngắn. Trích bình luận tối đa 200 ký tự (R12).
- `dedupKey`: job theo lịch tự đặt (ví dụ `TASK_DUE_SOON:{itemId}:{dueDate}`); sự kiện thường để `null` và dùng `eventId`.
- Không gửi `actorName`: notification-service tra tên qua identity-service (endpoint nội bộ mà `IdentityClient` của work-service đang dùng), cache 10 phút.

### 4.3 Phía phát: `NotificationEventPublisher` + `NotificationRecipientResolver`

```java
// work-service/notification/NotificationEventPublisher.java (phác thảo)
@Component
@RequiredArgsConstructor
public class NotificationEventPublisher {
    private final OutboxRecorder outbox;
    private final NotificationRecipientResolver recipients;
    private final ProjectRepository projectRepository;

    public void taskAssigned(WorkItem item, UUID oldAssignee, UUID actorId) {
        Project p = projectRepository.findById(item.getProjectId()).orElseThrow();
        if (Boolean.TRUE.equals(p.getIsTemplate())) return;                    // R7
        if (item.getAssigneeId() != null) {
            publish(NotificationType.TASK_ASSIGNED, p, EntityRef.of(item), actorId,
                    List.of(Recipient.of(item.getAssigneeId(), Reason.NEW_ASSIGNEE)),
                    Map.of("priority", item.getPriority(), "dueDate", item.getDueDate()));
        }
        if (oldAssignee != null && !oldAssignee.equals(item.getAssigneeId())) {
            publish(NotificationType.TASK_UNASSIGNED, p, EntityRef.of(item), actorId,
                    List.of(Recipient.of(oldAssignee, Reason.PREVIOUS_ASSIGNEE)), Map.of());
        }
    }

    public void completionRequested(WorkItem item, WorkItemCompletionRequest req) {
        publish(NotificationType.COMPLETION_REQUESTED, /* … */,
                recipients.projectManagers(item.getProjectId()), /* … */);
    }

    private void publish(NotificationType type, Project p, EntityRef entity, UUID actorId,
                         List<Recipient> to, Map<String, Object> data) {
        List<Recipient> filtered = recipients.onlyActiveMembers(p.getId(), to, actorId); // R1, R2
        if (filtered.isEmpty()) return;
        outbox.record("NOTIFICATION", entity.id(), type.name(),
                NotificationEnvelope.of(type, p, entity, actorId, filtered, data).toMap());
    }
}
```

`NotificationRecipientResolver.projectManagers(projectId)` = `lead_user_id` + `created_by` + người có vai trò mang quyền `PROJECT_ADMIN`; dùng chung logic với `WorkItemCompletionGate.canConfirmCompletion` nhưng **không** thêm Global ADMIN.

### 4.4 Relay

```java
@Scheduled(fixedDelay = 1000)
@Transactional
public void relay() {
    // SELECT … FROM outbox_events WHERE status = 'PENDING' ORDER BY created_at LIMIT 100 FOR UPDATE SKIP LOCKED
    for (OutboxEvent e : outboxEventRepository.lockPendingBatch(100)) {
        try {
            kafkaTemplate.send(NOTIFICATION_TOPIC, partitionKey(e), e.getPayload()).get(5, TimeUnit.SECONDS);
            e.setStatus(OutboxStatus.PROCESSED);
            e.setProcessedAt(LocalDateTime.now());
        } catch (Exception ex) {
            e.setRetryCount(e.getRetryCount() + 1);
            if (e.getRetryCount() >= 10) e.setStatus(OutboxStatus.FAILED);
            log.warn("Outbox relay failed for {}: {}", e.getId(), ex.getMessage());
        }
    }
}
```

- `SKIP LOCKED` cho phép nhiều instance cùng chạy relay mà không gửi trùng.
- Gửi đồng bộ (`.get()`) là chấp nhận được với lưu lượng của DATN.
- Job dọn `PROCESSED` cũ hơn 7 ngày. `FAILED` xem trong pgAdmin.
- **Sửa `OutboxRecorder` trước** (rủi ro R-9 của tài liệu realtime): `try/catch` quanh `save()` không nuốt được lỗi vì entity chỉ flush lúc commit. Nên bỏ `try/catch` gây hiểu nhầm và chấp nhận nguyên tắc của Outbox: nghiệp vụ và sự kiện **cùng thành công hoặc cùng thất bại**. Kiểm tra payload (không `null`, kích thước nhỏ) trước khi lưu để lỗi chỉ có thể đến từ DB.

### 4.5 Phía nhận

```java
@KafkaListener(topics = "notification-events", groupId = "notification-service")
public void onEvent(NotificationEnvelope e) {
    ingestService.ingest(e);   // @Transactional: render → gộp/chèn → publish AFTER_COMMIT (WS + email)
}
```

| Bước | Chi tiết |
|---|---|
| Lọc | Bỏ `actorId`; mỗi người chỉ giữ lý do mạnh nhất (R3) |
| Dựng nội dung | `NotificationTemplateRegistry`: mỗi `type` → `category`, `severity` (có thể đổi theo `data`, ví dụ A1 thành Cảnh báo khi ưu tiên cao), `actionRequired`, tiêu đề, dòng phụ, `link` |
| Gộp (R4) | Tìm thông báo chưa đọc cùng `(recipient_id, group_key)` trong 10 phút → cập nhật `title`, `actor_count`, `updated_at` |
| Chèn | `INSERT … ON CONFLICT (dedup_key) DO NOTHING` |
| Đóng "Cần xử lý" (R9) | Sự kiện C2 / C3 / C5 / huỷ lời mời… đặt `action_resolved_at` cho thông báo cùng `entity_id` và loại tương ứng của **mọi** người nhận |
| Đẩy | Sau commit: `SimpMessagingTemplate.convertAndSendToUser(sub, "/queue/notifications", msg)` |
| Email | Loại có cấu hình email: `@Async` gọi `EmailService` với template tương ứng; lỗi chỉ ghi log |
| Lỗi | `DefaultErrorHandler` thử lại 3 lần (cách nhau 2 giây) rồi chuyển vào `notification-events.DLT` |

---

## 5. Mô hình dữ liệu (notification-service)

Dùng chung DB `datn_main_db`, Flyway riêng với bảng lịch sử `flyway_notification_schema_history`, `ddl-auto: validate` (giống cách làm của workspace-service).

```sql
-- V1__notifications.sql
CREATE TABLE notifications (
    id                 UUID PRIMARY KEY,
    recipient_id       UUID          NOT NULL,              -- Keycloak sub
    type               VARCHAR(60)   NOT NULL,              -- TASK_ASSIGNED…
    category           VARCHAR(30)   NOT NULL,              -- TASK, APPROVAL, SPRINT…
    severity           VARCHAR(20)   NOT NULL,              -- INFO, SUCCESS, WARNING, URGENT
    action_required    BOOLEAN       NOT NULL DEFAULT FALSE,
    action_resolved_at TIMESTAMP,
    title              VARCHAR(300)  NOT NULL,              -- đã dựng sẵn, có thể chứa <b>
    body               VARCHAR(1000),
    link               VARCHAR(500),                        -- đường dẫn tương đối của frontend
    workspace_id       UUID,
    project_id         UUID,
    entity_type        VARCHAR(30),                         -- WORK_ITEM, SPRINT, PROJECT, INVITATION…
    entity_id          UUID,
    actor_id           UUID,
    actor_count        INT           NOT NULL DEFAULT 1,    -- gộp: "An và 2 người khác"
    data               JSONB,                               -- mã, tên dự án, giá trị cũ/mới… để dựng lại nếu cần
    group_key          VARCHAR(200),
    dedup_key          VARCHAR(200)  NOT NULL UNIQUE,
    source_event_id    UUID,
    read_at            TIMESTAMP,
    deleted_at         TIMESTAMP,
    created_at         TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at         TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE INDEX idx_notif_list   ON notifications (recipient_id, updated_at DESC, id) WHERE deleted_at IS NULL;
CREATE INDEX idx_notif_unread ON notifications (recipient_id) WHERE read_at IS NULL AND deleted_at IS NULL;
CREATE INDEX idx_notif_group  ON notifications (recipient_id, group_key) WHERE read_at IS NULL AND deleted_at IS NULL;
CREATE INDEX idx_notif_action ON notifications (entity_id, type) WHERE action_required AND action_resolved_at IS NULL;

-- P2: cài đặt bật/tắt theo loại
CREATE TABLE notification_preferences (
    user_id  UUID        NOT NULL,
    type     VARCHAR(60) NOT NULL,
    in_app   BOOLEAN     NOT NULL DEFAULT TRUE,
    email    BOOLEAN     NOT NULL DEFAULT TRUE,
    PRIMARY KEY (user_id, type)
);
```

- Sắp xếp theo `updated_at` để thông báo vừa được gộp nổi lên đầu.
- `title` / `body` được dựng **một lần lúc tạo** (ảnh chụp tại thời điểm đó). Đổi tên công việc sau này không làm thông báo cũ thay đổi, giống Jira và GitHub.
- Không cho tắt trong ứng dụng các loại có cờ **Cần xử lý** và G2, G9.

---

## 6. API REST

Gateway thêm route `notification-service`: `Path=/notification/api/**, /notification/ws/**`, `StripPrefix=1`, trỏ tới `:9994`. Phải sửa **cả** `application.yml` và `application-tuananh.yml` (profile `tuananh` ghi đè toàn bộ `routes`). **Không** route `/notification/internal/**`.

| Phương thức | Đường dẫn (qua gateway) | Mô tả |
|---|---|---|
| GET | `/notification/api/v1/notifications?filter=all\|unread\|action&category=&projectId=&cursor=&size=20` | Danh sách, phân trang theo con trỏ `(updated_at, id)` để không trùng khi có thông báo mới chen vào |
| GET | `/notification/api/v1/notifications/unread-count` | `{ "total": 7, "action": 2 }` |
| PATCH | `/notification/api/v1/notifications/{id}` | Body `{ "read": true }` hoặc `{ "read": false }`: đánh dấu đã đọc / chưa đọc |
| POST | `/notification/api/v1/notifications/read-all` | Đánh dấu tất cả đã đọc; `?projectId=` hoặc `?category=` để giới hạn |
| DELETE | `/notification/api/v1/notifications/{id}` | Xoá mềm |
| POST | `/notification/api/v1/notifications/{id}/restore` | Hoàn tác xoá (trong 30 ngày) |
| DELETE | `/notification/api/v1/notifications?filter=read` | Xoá mọi thông báo đã đọc |
| GET / PUT | `/notification/api/v1/notification-preferences` | Cài đặt theo loại (P2) |

Phản hồi danh sách (bọc trong `ApiResponse` như các service khác):

```json
{
  "success": true,
  "data": {
    "items": [{
      "id": "…", "type": "COMPLETION_REQUESTED", "category": "APPROVAL", "severity": "INFO",
      "actionRequired": true, "actionResolved": false,
      "title": "<b>Bình</b> báo đã hoàn thành <b>MOC-2</b>, chờ bạn xác nhận",
      "body": "Ghi chú: \"Đã khảo sát đủ 3 địa điểm…\"",
      "link": "/browse/MOC-2",
      "project": { "id": "…", "key": "MOC", "name": "Mộc Coffee" },
      "actor": { "id": "…", "name": "Bình" }, "actorCount": 1,
      "read": false, "createdAt": "2026-11-14T09:12:04", "updatedAt": "2026-11-14T09:12:04"
    }],
    "nextCursor": "MjAyNi0xMS0xNFQwOToxMjowNHwz…",
    "unreadCount": 7
  }
}
```

**Quy tắc bảo mật:** mọi truy vấn có `WHERE recipient_id = :jwtSub`. Id thuộc người khác trả **404**, không trả 403, để không lộ việc id đó tồn tại. `title` chỉ chứa thẻ `<b>`; frontend lọc thẻ (whitelist) trước khi hiển thị.

---

## 7. WebSocket

| Mục | Giá trị |
|---|---|
| Endpoint | notification-service `/ws` → gateway `/notification/ws` (gateway `permitAll` đường này, xác thực ở frame `CONNECT`) |
| Xác thực | `StompAuthChannelInterceptor`: đọc `Authorization: Bearer …` ở `CONNECT`, `JwtDecoder.decode()`, gán `Principal` = `sub`. Không dùng `?access_token=` vì token lộ vào log |
| Đăng ký | Chỉ chấp nhận `SUBSCRIBE /user/queue/notifications` |
| Heartbeat | 10 giây hai chiều |
| Token hết hạn | `api.js` refresh token thành công thì đóng và mở lại kết nối |
| Kết nối lại | Chờ 3–5 giây có jitter, nhân dần tới 30 giây; nối lại xong thì gọi `unread-count` và tải lại trang đầu |

Thông điệp server gửi xuống:

```json
{ "kind": "CREATED",  "notification": { … }, "unreadCount": 8 }
{ "kind": "UPDATED",  "notification": { … }, "unreadCount": 8 }    // gộp, hoặc "Cần xử lý" đã được giải quyết
{ "kind": "READ",     "ids": ["…"], "unreadCount": 7 }               // đồng bộ giữa các tab
{ "kind": "UNREAD",   "ids": ["…"], "unreadCount": 8 }
{ "kind": "READ_ALL", "unreadCount": 0 }
{ "kind": "DELETED",  "ids": ["…"], "unreadCount": 7 }
{ "kind": "RESTORED", "notification": { … }, "unreadCount": 8 }
```

Trình duyệt sẽ có hai kết nối WebSocket: `/work/ws-work` (realtime dự án) và `/notification/ws` (thông báo). Như vậy vẫn chấp nhận được; nếu muốn gộp làm một thì có thể chuyển STOMP sang broker relay dùng chung ở giai đoạn sau.

---

## 8. Giao diện

### 8.1 Chuông và bảng thông báo

```
                                   ┌──────────────────────────────────────────────────┐
  [🔍 Tìm kiếm…]  [+ Tạo dự án]  🔔7 │ Thông báo                  ✓ Đánh dấu tất cả đã đọc│
                                   │ [Tất cả] [Chưa đọc 7] [Cần xử lý 2]   Nhóm ▾      │
                                   ├──────────────────────────────────────────────────┤
                                   │▌● Bình báo đã hoàn thành MOC-2, chờ bạn xác nhận │
                                   │▌  Ghi chú: "Đã khảo sát đủ 3 địa điểm…"           │
                                   │▌  Mộc Coffee · 5 phút trước   [Cần xử lý]   ✓  🗑 │
                                   │▌● MOC-5 đã quá hạn 3 ngày                         │
                                   │▌  Mộc Coffee · 08:00                         ✓  🗑 │
                                   │   An đã xác nhận hoàn thành MOC-7                 │
                                   │   Mộc Coffee · Hôm qua                       ○  🗑 │
                                   ├──────────────────────────────────────────────────┤
                                   │ Xem tất cả thông báo →                             │
                                   └──────────────────────────────────────────────────┘
```

| Thành phần | Hành vi | API |
|---|---|---|
| Chuông | Số chưa đọc (quá 99 thì hiện "99+"); chấm tím nếu có Cần xử lý | `unread-count` khi tải trang, sau đó qua WebSocket |
| Dải màu bên trái | Theo `severity` | — |
| Chấm xanh + chữ đậm | Chưa đọc | — |
| Bấm vào thông báo | Đánh dấu đã đọc rồi điều hướng tới `link` | `PATCH {read:true}` |
| Nút ✓ / ○ | Đổi qua lại đã đọc / chưa đọc, không điều hướng | `PATCH {read:true\|false}` |
| Nút 🗑 | Xoá mềm, toast "Đã xoá thông báo · Hoàn tác" trong 5 giây | `DELETE /{id}`, `POST /{id}/restore` |
| "Đánh dấu tất cả đã đọc" | Theo tab / bộ lọc hiện tại | `POST /read-all` |
| Tab "Cần xử lý" | Chỉ thông báo có cờ, chưa được giải quyết | `?filter=action` |
| Thông báo đã giải quyết | Mờ đi, nhãn "Đã xử lý" thay cho "Cần xử lý" | — |
| Toast khi có thông báo mới | **Chỉ** cho `URGENT` và Cần xử lý; tối đa một toast mỗi 5 giây | — |

### 8.2 Trang đầy đủ `/w/:workspaceId/notifications`

Như bảng thông báo nhưng có thêm lọc theo dự án và nhóm, chọn nhiều để đánh dấu đã đọc hoặc xoá, "Xoá tất cả đã đọc", và cuộn vô hạn.

### 8.3 Đường dẫn (deep link)

| Việc cần làm | Chi tiết |
|---|---|
| Route mới `/browse/:code` | Gọi `GET /work/api/v1/work/items/code/{code}` (đã có) để lấy `projectId`, lấy `workspaceId` của dự án, rồi `navigate(…/projects/{pid}/board?item={code}, {replace:true})`. Công việc không nằm trong chu kỳ đang chạy thì dùng `/backlog?item={code}` |
| `ProjectDetailPage` | Đọc `?item=` để mở `WorkItemDetailModal`; đóng modal thì xoá tham số |
| `WorkItemDetailModal` | `?comment={id}`: cuộn tới bình luận và tô sáng 2 giây |
| Lỗi 404 / 403 | Toast "Công việc không còn tồn tại hoặc bạn không còn quyền xem"; thông báo vẫn được đánh dấu đã đọc |
| Email | Sửa `TaskNotificationMailer` (hoặc template mới) dùng `{frontend-url}/browse/{key}` thay cho `/projects/item/{key}` |
| Bộ lọc mới ở Kho việc | `?filter=overdue` (F4); không bắt buộc cho P0 |

### 8.4 File frontend thay đổi

| File | Thay đổi |
|---|---|
| `src/api.js` | Hằng `NOTIF_BASE = '/notification/api/v1/notifications'`, đối tượng `notificationApi` |
| `vite.config.js` | Proxy `/notification` (`ws: true`) |
| `nginx.conf` | `location /notification/` có `Upgrade` / `Connection` |
| `components/notifications/NotificationBell.jsx`, `NotificationPanel.jsx`, `NotificationItem.jsx` (mới) | Chuông, bảng, từng dòng |
| `hooks/useNotifications.js`, `realtime/notificationSocket.js` (mới) | Trạng thái, phân trang, WebSocket, đồng bộ nhiều tab |
| `pages/NotificationsPage.jsx`, `pages/BrowseItemRedirect.jsx` (mới) | Trang đầy đủ, chuyển hướng `/browse/:code` |
| `App.jsx` | Route `/browse/:code`, `/w/:workspaceId/notifications` |
| `layout` của `WorkspaceLayout` / `Header.jsx` | Gắn chuông |
| `pages/ProjectDetailPage.jsx`, `components/items/WorkItemDetailModal.jsx` | `?item=`, `?comment=` |
| Ô soạn bình luận | Chọn người khi gõ `@`, lưu `@[Tên](sub)` |

---

## 9. Job theo lịch

Bật `@EnableScheduling` ở work-service và notification-service. Mọi job dùng `ZoneId.of("Asia/Ho_Chi_Minh")` và ghi sự kiện qua **outbox** kèm `dedupKey`.

| Job | Service | Lịch | Sinh loại |
|---|---|---|---|
| `DueDateReminderJob` | work-service | `0 0 8 * * *` | F1, F2, F3, F5, F6 |
| `SprintDeadlineJob` | work-service | `0 0 8 * * *` (E3 có thể thêm mỗi giờ) | E3, E4, E6 |
| `CompletionReminderJob` | work-service | `0 0 8 * * *` | C4 |
| `ProjectDigestJob` | work-service | `0 5 8 * * *` | F4, F7, F8 |
| `OutboxRelay` | work-service, workspace-service | mỗi 1 giây | — |
| `OutboxCleanupJob` | work-service, workspace-service | `0 30 3 * * *` | Xoá `PROCESSED` cũ hơn 7 ngày |
| `NotificationCleanupJob` | notification-service | `0 0 3 * * *` | Xoá hẳn thông báo đã xoá mềm quá 30 ngày và đã đọc quá 90 ngày |

Truy vấn mẫu cho F1:

```sql
SELECT wi.id, wi.code, wi.summary, wi.assignee_id, wi.due_date, wi.project_id
FROM work_items wi
JOIN statuses s ON s.id = wi.status_id
JOIN projects p ON p.id = wi.project_id
WHERE wi.deleted_at IS NULL AND p.deleted_at IS NULL AND p.is_template = FALSE
  AND wi.assignee_id IS NOT NULL
  AND s.category <> 'DONE'
  AND wi.due_date = :tomorrow
  AND NOT EXISTS (SELECT 1 FROM work_item_completion_requests r
                  WHERE r.work_item_id = wi.id AND r.status = 'PENDING');   -- R11
```

**Để demo khi bảo vệ:** thêm `POST /work/api/v1/admin/jobs/{jobName}/run` (chỉ Global ADMIN) để chạy job ngay, không phải chờ tới 08:00. Chạy lại nhiều lần cũng không sinh trùng nhờ `dedup_key`.

Khi chạy nhiều instance: dùng ShedLock (bảng `shedlock`) hoặc khoá Redis để mỗi job chỉ chạy ở một instance.

---

## 10. Bảo mật

| Lớp | Biện pháp |
|---|---|
| notification-service | Thêm `spring-boot-starter-oauth2-resource-server`, cùng issuer Keycloak `datn-realm`; `/api/**` bắt JWT; `/internal/**` chỉ cho gọi nội bộ (gateway không route; trong Docker không publish cổng 9994 ra ngoài) |
| Gateway | Thêm `/notification/ws/**` vào `PUBLIC_ENDPOINTS` (xác thực ở `CONNECT`); `/notification/api/**` vẫn bắt JWT như các route khác |
| Dữ liệu | Lọc theo `recipient_id = jwt.sub`; id của người khác trả 404 |
| WebSocket | Chỉ cho đăng ký kênh của chính mình; kiểm `exp` của token |
| Nội dung | Sự kiện không chứa mô tả hay toàn văn bình luận (R12); `title` chỉ cho phép thẻ `<b>` |
| Email | Chỉ gửi tới email của chính người nhận, tra qua identity-service |

---

## 11. Lộ trình

| Giai đoạn | Nội dung | Ước lượng | Tiêu chí hoàn thành |
|---|---|---|---|
| **N1: Lưu trữ, API, giao diện** | notification-service: JPA + Flyway `V1__notifications`, resource server, REST API; route gateway (cả 2 file). FE: chuông, bảng, trang đầy đủ, `/browse/:code`, `?item=`; hỏi số chưa đọc mỗi 30 giây | 2–3 ngày | Đọc / đánh dấu chưa đọc / xoá / hoàn tác / mở link chạy được với dữ liệu mẫu |
| **N2: Sự kiện P0** | Sửa `OutboxRecorder`; `NotificationEventPublisher`, `RecipientResolver`; `OutboxRelay` → Kafka; consumer + `NotificationTemplateRegistry`; gắn 11 loại P0 dạng sự kiện (A1, A3, C1–C3, D1, D2, E1, E2, G1, G2); tách @nhắc tên (backend + ô chọn người khi gõ `@`) cho D1 | 3–4 ngày | NT7: tắt notification-service, giao việc, bật lại thì thông báo vẫn tới |
| **N3: Đẩy ngay** | WebSocket STOMP + xác thực `CONNECT`; đồng bộ nhiều tab; bỏ hỏi định kỳ (giữ làm lưới an toàn khi mất kết nối) | 1–2 ngày | Chuông nhảy số trong dưới 1 giây |
| **N4: Lịch & email** | `@EnableScheduling`; `DueDateReminderJob`, `SprintDeadlineJob` (F1–F3, E3); endpoint chạy job thủ công; email cho A1, C1, D1, F1, F3, G1 | 2 ngày | NT8 đạt; mail hiện trong Mailpit |
| **N5: Hoàn thiện** | Loại P1 / P2; gộp thông báo; workspace-service (G8–G11); cài đặt bật/tắt | 3–4 ngày | Toàn bộ kịch bản §12 đạt |

---

## 12. Kịch bản kiểm thử

Hai trình duyệt (hoặc một cửa sổ ẩn danh), dự án Mộc Coffee: An (Trưởng dự án), Bình (Thành viên), Chi (Người xem).

| Mã | Kịch bản | Kỳ vọng |
|---|---|---|
| NT1 | An giao MOC-2 cho Bình | Chuông của Bình +1 trong ≤ 1 giây, đúng nội dung; bấm vào mở MOC-2; An không nhận gì |
| NT2 | Bình kéo MOC-2 sang Hoàn thành | An nhận C1 có nhãn Cần xử lý; An bấm Xác nhận → Bình nhận C2, C1 của An chuyển "Đã xử lý" |
| NT3 | An từ chối kèm lý do | Bình nhận C3 có lý do; không có thêm D2 cho bình luận lý do |
| NT4 | Bình bình luận "@An …" trên MOC-2 do An tạo | An chỉ nhận D1, không nhận thêm D2 |
| NT5 | An và Chi lần lượt bình luận trong MOC-2 trong vòng 10 phút | Bình nhận **một** thông báo "An và 1 người khác đã bình luận…" |
| NT6 | Đánh dấu đã đọc ở tab 1 | Badge ở tab 2 giảm ngay |
| NT7 | Tắt notification-service, An giao việc cho Bình, bật lại | Bình vẫn nhận thông báo, không trùng |
| NT8 | Đặt hạn MOC-5 là ngày mai, chạy `DueDateReminderJob` bằng endpoint thủ công, chạy thêm lần nữa | Bình nhận F1 + email trong Mailpit; lần chạy thứ hai không sinh thêm |
| NT9 | Bình xoá F1 rồi bấm Hoàn tác; xoá lần nữa rồi chạy lại job | Hoàn tác thì thông báo trở lại; job chạy lại không tạo lại thông báo đã xoá |
| NT10 | Gọi `PATCH` với id thông báo của người khác | 404 |
| NT11 | Xoá Bình khỏi dự án khi Bình còn MOC-5 | Bình nhận G2, link về danh sách dự án; An nhận G3 |
| NT12 | Mở link của công việc đã bị xoá | Toast "không còn tồn tại"; thông báo vẫn được đánh dấu đã đọc |
| NT13 | Dự án mẫu có công việc hạn ngày mai | Job không nhắc |
| NT14 | Bình đã gửi yêu cầu Tạm hoàn thành cho MOC-5 có hạn hôm nay | Bình không nhận F2; An nhận C4 nếu yêu cầu chờ quá 48 giờ |

**Unit test** (JUnit 5 + Mockito, đúng phong cách `work-service/src/test`):
- `NotificationRecipientResolverTest`: QL gồm Lead, người tạo, vai trò có `PROJECT_ADMIN`; bỏ người thao tác; bỏ người không còn là thành viên.
- `NotificationEventPublisherTest`: dự án mẫu không phát; A1 + A2 khi đổi người; C1 gửi đúng QL.
- `OutboxRelayTest`: gửi thành công thì `PROCESSED`; lỗi thì tăng `retryCount`, quá 10 lần thì `FAILED`.
- `NotificationIngestServiceTest`: chống trùng, chọn lý do mạnh nhất, gộp trong 10 phút, đóng Cần xử lý.
- `DueDateReminderJobTest`: mốc 1/3/7 ngày, bỏ công việc đang chờ xác nhận, `dedupKey` ổn định.
- `NotificationControllerTest` (MockMvc standalone): 404 với id của người khác, `read-all` theo bộ lọc, xoá mềm và hoàn tác.

---

## 13. Rủi ro và lưu ý

| Rủi ro | Giảm thiểu |
|---|---|
| **Hai loại userId**: workspace-service dùng `users.id` của identity-service (`CurrentUserResolver`), còn work-service dùng Keycloak `sub` | Sự kiện từ workspace-service phải đổi sang `sub` trước khi ghi outbox |
| `OutboxRecorder` nuốt lỗi không có tác dụng (R-9) | Sửa ở đầu N2 (§4.4) |
| Kafka chỉ quảng bá `localhost:9092`: service chạy **trong Docker** không kết nối được | Thêm listener nội bộ `INTERNAL://kafka:29092` khi chạy notification-service bằng Docker; chạy từ IntelliJ thì không ảnh hưởng |
| Gateway profile `tuananh` ghi đè toàn bộ `routes` | Thêm route `notification-service` vào cả `application.yml` và `application-tuananh.yml` |
| notification-service cần thêm cấu hình DB và Kafka trong compose | Bổ sung biến môi trường `SPRING_DATASOURCE_*`, `SPRING_KAFKA_BOOTSTRAP_SERVERS` |
| Chuông quá ồn khiến người dùng bỏ qua | R3, R4, R5, R10; danh sách "không đưa vào chuông" (tài liệu 1 §4); cài đặt bật/tắt ở N5 |
| Lệch múi giờ khi so ngày | Luôn dùng `Asia/Ho_Chi_Minh` trong job và khi hiển thị |
| Quên phát sự kiện ở chức năng mới | Danh mục ở tài liệu 1 là checklist; khi thêm đường đổi trạng thái mới, `CLAUDE.md` đã yêu cầu gọi `completionGate.onStatusChanged(...)`, nên gắn phát thông báo ở cùng chỗ |
| Email lặp với `InvitationMailer` / `TaskNotificationMailer` | Khi N4 xong: bỏ `TaskNotificationMailer`, chuyển `InvitationMailer` sang phát sự kiện |
| `CLAUDE.md` đang ghi notification-service "chỉ gửi email, không qua gateway" | Cập nhật sau khi xong N1 |

---

## Phụ lục: danh sách file thay đổi

| Khu vực | File | Thay đổi | GĐ |
|---|---|---|---|
| notification-service | `pom.xml` | + `data-jpa`, `postgresql`, `flyway-core`, `oauth2-resource-server`, `spring-kafka`, `websocket` | N1–N3 |
| | `db/migration/V1__notifications.sql` | Bảng `notifications` (+ `notification_preferences` ở N5) | N1 |
| | `entity/Notification.java`, `repository/NotificationRepository.java` | Truy vấn danh sách theo con trỏ, đếm, đánh dấu, xoá mềm | N1 |
| | `controller/NotificationController.java`, `service/NotificationQueryService.java` | REST §6 | N1 |
| | `config/SecurityConfig.java` | Resource server; `/internal/**` nội bộ | N1 |
| | `kafka/NotificationEventListener.java`, `service/NotificationIngestService.java`, `template/NotificationTemplateRegistry.java` | Nhận, dựng nội dung, gộp, chống trùng | N2 |
| | `config/WebSocketConfig.java`, `ws/StompAuthChannelInterceptor.java` | WebSocket §7 | N3 |
| | `job/NotificationCleanupJob.java`, `templates/completion-request.html`, `task-overdue.html` | Dọn dẹp, email mới | N4 |
| work-service | `notification/NotificationEventPublisher.java`, `NotificationRecipientResolver.java`, `NotificationType.java` | Phía phát | N2 |
| | `service/OutboxRecorder.java`, `service/OutboxRelay.java` (mới), `repository/OutboxEventRepository.java` | Sửa R-9; relay; `lockPendingBatch` | N2 |
| | `config/KafkaProducerConfig.java` | + topic `notification-events`, `.DLT` | N2 |
| | `WorkItemServiceImpl`, `WorkflowEngineServiceImpl`, `WorkItemCompletionGate`, `WorkItemCompletionServiceImpl`, `WorkItemCommentServiceImpl`, `WorkItemLinkServiceImpl`, `SprintServiceImpl`, `ProjectMemberServiceImpl`, `InvitationServiceImpl`, `ProjectServiceImpl`, `ContributionReportServiceImpl` | Gọi publisher theo danh mục | N2, N5 |
| | `WorkServiceApplication.java` (+ `@EnableScheduling`), `job/*Job.java`, `controller/AdminJobController.java` | Job theo lịch | N4 |
| workspace-service | `WorkspaceMemberServiceImpl`, outbox + relay | G8–G11 | N5 |
| api-gateway | `application.yml`, `application-tuananh.yml`, `security/SecurityConfig.java` | Route `/notification/**`; `/notification/ws/**` public | N1, N3 |
| frontend | Xem §8.4 | | N1–N5 |
| infrastructure | `docker-compose*.yml` | Biến DB / Kafka cho notification-service; listener Kafka nội bộ | N2 |
| Tài liệu | `CLAUDE.md`, `docs/API_DOCUMENTATION.md`, `docs/services/0_SERVICES_ARCHITECTURE.md` | Cập nhật vai trò mới của notification-service | N1 |
