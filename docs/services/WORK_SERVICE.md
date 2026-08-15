# 📋 Tài liệu Kỹ thuật: Work Service – DATN-2026

> **Module:** `work-service` (hoặc `project-task-service`)  
> **Port:** `8083`  
> **Tech Stack:** Spring Boot 3, Spring Data JPA, PostgreSQL, Spring WebSocket / STOMP, Kafka Producer  
> **Java:** 21

---

## 1. TỔNG QUAN SERVICE

`work-service` là dịch vụ lõi quản lý toàn bộ công việc, dự án nội bộ, tiến độ và cộng tác nhóm trong từng Workspace theo mô hình Agile/Kanban (tương tự Linear/Jira).

```
                      ┌────────────────────────┐
                      │   API Gateway (:8081)  │
                      └───────────┬────────────┘
                                  │ Forward /work/**
                                  ▼
                      ┌────────────────────────┐
                      │      Work Service      │  :8083
                      │   (Core Work Engine)   │
                      └───────────┬────────────┘
                                  │
          ┌───────────────────────┼────────────────────────┐
          ▼                       ▼                        ▼
┌───────────────────┐   ┌───────────────────┐    ┌───────────────────┐
│ PostgreSQL DB     │   │ Kafka Event Pub   │    │ WebSocket Server  │
│ (Teams, Projects, │   │ (Task Events,     │    │ (Realtime Kanban  │
│  Tasks, Comments) │   │  Activity Stream) │    │  Drag & Drop)     │
└───────────────────┘   └───────────────────┘    └───────────────────┘
```

---

## 2. DANH SÁCH THỰC THỂ DỮ LIỆU (ENTITIES)

Service quản lý trực tiếp các thực thể trong CSDL:
1. **Cấu trúc Tổ chức**: `teams`, `team_members`
2. **Dự án & Cột mốc**: `projects` *(chứa cờ `is_template = true`)*, `milestones`
3. **Quản trị Tác vụ (Issues/Tasks)**: `tasks`, `task_labels`, `task_label_map`, `task_watchers`, `task_comments`, `task_attachments`, `task_dependencies`, `task_activity_logs`
4. **Nhân bản Dự án / Template**: `project_clones` *(Lưu `source_project_id`, `cloned_project_id`, `cloned_by`)*

---

## 3. DANH SÁCH REST API CHÍNH (`/api/v1/work`)

### 3.1. Team Management (`/api/v1/work/teams`)
| Method | Endpoint | Quyền hạn | Mô tả |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/work/teams` | `team.create` | Tạo nhóm mới trong Workspace |
| `GET` | `/api/v1/work/teams?workspaceId={id}` | `team.view` | Danh sách nhóm trong Workspace |
| `GET` | `/api/v1/work/teams/{teamId}` | `team.view` | Chi tiết nhóm & danh sách thành viên |
| `POST` | `/api/v1/work/teams/{teamId}/members` | `team.manage` | Thêm thành viên vào nhóm |
| `DELETE`| `/api/v1/work/teams/{teamId}/members/{userId}` | `team.manage` | Xóa thành viên khỏi nhóm |

### 3.2. Project & Milestone Management (`/api/v1/work/projects`)
| Method | Endpoint | Quyền hạn | Mô tả |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/work/projects` | `project.create` | Tạo mới dự án |
| `GET` | `/api/v1/work/projects?workspaceId={id}` | `project.view` | Danh sách dự án trong Workspace |
| `GET` | `/api/v1/work/projects/templates?workspaceId={id}` | `project.view` | Lấy danh sách các Dự án Mẫu (`is_template = true`) |
| `GET` | `/api/v1/work/projects/{projectId}` | `project.view` | Xem chi tiết dự án |
| `PUT` | `/api/v1/work/projects/{projectId}` | `project.edit` | Cập nhật dự án |
| `DELETE`| `/api/v1/work/projects/{projectId}` | `project.delete` | Lưu trữ / Xóa dự án |
| `POST` | `/api/v1/work/projects/{projectId}/clone` | `project.create` | **Nhân bản Dự án / Template**: Tạo project mới sao chép toàn bộ Kanban, nhãn và task mẫu |
| `POST` | `/api/v1/work/projects/{projectId}/milestones` | `project.edit` | Tạo cột mốc mới (Milestone / Sprint) |
| `GET` | `/api/v1/work/projects/{projectId}/milestones` | `project.view` | Danh sách cột mốc |

### 3.3. Task & Kanban Management (`/api/v1/work/tasks`)
| Method | Endpoint | Quyền hạn | Mô tả |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/work/tasks` | `task.create` | Tạo task mới (tự sinh mã `code` như `PTMS-101`) |
| `GET` | `/api/v1/work/tasks?projectId={id}` | `task.view` | Lấy danh sách task (phân trang, lọc theo status, priority) |
| `GET` | `/api/v1/work/tasks/board?projectId={id}` | `task.view` | Lấy dữ liệu gom nhóm theo cột Kanban (`todo`, `in_progress`,...) |
| `GET` | `/api/v1/work/tasks/{taskId}` | `task.view` | Xem chi tiết task kèm subtasks, comments, attachments |
| `PUT` | `/api/v1/work/tasks/{taskId}` | `task.edit` | Sửa nội dung task (kiểm tra `version` Optimistic Lock) |
| `PATCH`| `/api/v1/work/tasks/{taskId}/move` | `task.edit` | Kéo thả Kanban: đổi status & position thứ tự |
| `DELETE`| `/api/v1/work/tasks/{taskId}` | `task.delete` | Xóa task |

### 3.4. Task Collaboration & History
| Method | Endpoint | Mô tả |
| :--- | :--- | :--- |
| `POST` | `/api/v1/work/tasks/{taskId}/comments` | Thêm bình luận trao đổi |
| `GET` | `/api/v1/work/tasks/{taskId}/comments` | Cây bình luận thảo luận |
| `POST` | `/api/v1/work/tasks/{taskId}/attachments` | Đính kèm tệp tin |
| `GET` | `/api/v1/work/tasks/{taskId}/activity-logs` | Lịch sử chỉnh sửa / kiểm toán thay đổi |
| `POST` | `/api/v1/work/tasks/{taskId}/dependencies` | Thiết lập phụ thuộc (`blocks`, `relates_to`, `duplicates`) |

---

## 4. CÁC CƠ CHẾ KỸ THUẬT NỔI BẬT & GIẢI PHÁP REALTIME TỐI ĐA (REAL-TIME ARCHITECTURE)

Để đạt được trải nghiệm phản hồi tức thì (Sub-50ms latency), cộng tác đồng thời mượt mà tương tự **Linear**, **Jira**, **Notion** và **Trello**, `work-service` áp dụng 7 cơ chế kỹ thuật cốt lõi sau:

---

### 4.1. Kiến trúc Đồng bộ Realtime Đa người dùng (Real-time Multiplayer Sync)
* **Vấn đề**: Khi nhiều thành viên cùng mở một Kanban Board hoặc Task Detail, mọi thao tác kéo thả, đổi trạng thái, giao việc hay bình luận phải phản ánh tức thì lên màn hình của nhau mà không cần F5.
* **Cách hoạt động**:
  1. Client kết nối WebSocket STOMP tới `/ws-work` và subscribe vào các topic cụ thể:
     - `/topic/project/{projectId}` (lắng nghe sự kiện Kanban Board, thay đổi task).
     - `/topic/task/{taskId}` (lắng nghe bình luận, live typing, đổi thông tin chi tiết).
  2. Khi có mutation (HTTP PATCH / POST), service thực thi DB transaction $\rightarrow$ Bắn message qua WebSocket broker.
  3. Để hỗ trợ scale nhiều instance server, sử dụng **Redis Pub/Sub Backplane**: Khi instance A nhận request, nó publish lên Redis $\rightarrow$ Tất cả các instance khác nhận được và broadcast xuống các WebSocket client kết nối tới chúng.
* **Công nghệ sử dụng**: `Spring WebSocket (STOMP)`, `SockJS`, `Redis Pub/Sub Message Broker`.
* **Tham chiếu**: *Linear Real-time Sync Engine, Figma Multiplayer*.

---

### 4.2. Thuật toán Sắp xếp Vị trí Kéo thả $O(1)$ (Fractional Indexing / Lexorank)
* **Vấn đề**: Trên bảng Kanban, nếu dùng số nguyên (`position = 1, 2, 3...`), khi kéo một card chèn vào giữa 2 card khác thì phải cập nhật lại vị trí của hàng trăm card phía sau $\rightarrow$ Gây nghẽn Database (Deadlock & High I/O).
* **Cách hoạt động**:
  1. Thay vì dùng số nguyên, lưu `position` dưới dạng chuỗi hoặc số thực thập phân có độ chính xác cao (Fractional Indexing / Lexorank).
  2. Khi chèn Task vào giữa Task $A$ (`pos = "a0"`) và Task $B$ (`pos = "a1"`), hệ thống tính toán giá trị trung vị mới là `pos = "a0V"`.
  3. **Chỉ cần UPDATE đúng 1 bản ghi duy nhất** với độ phức tạp $O(1)$, không cần cập nhật bất kỳ bản ghi xung quanh nào.
* **Công nghệ sử dụng**: Thuật toán `Lexorank / Fractional Indexing String Generator`.
* **Tham chiếu**: *Jira Agile Board (Lexorank), Trello Board Ordering, Linear Issue Sort*.

---

### 4.3. Kiểm soát Xung đột Đồng thời & Hòa giải Trạng thái (Optimistic Locking & State Reconciliation)
* **Vấn đề**: Hai người dùng cùng kéo thả hoặc chỉnh sửa một task cùng một lúc dẫn đến hiện tượng ghi đè dữ liệu (Lost Update).
* **Cách hoạt động**:
  1. Bảng `tasks` có cột `@Version private Integer version`.
  2. Frontend áp dụng **Optimistic UI Update** (cập nhật giao diện ngay lập tức trước khi server phản hồi để tạo cảm giác siêu nhanh).
  3. Khi request gửi lên backend:
     - Nếu version khớp: Tăng `version + 1`, commit thành công, broadcast trạng thái mới cho toàn team.
     - Nếu xảy ra xung đột (version cũ hơn): Hibernate ném `OptimisticLockException` $\rightarrow$ Server trả về `409 CONFLICT` kèm authoritative data mới nhất để Frontend tự động hòa giải (Reconcile) giao diện mà không làm mất dữ liệu.
* **Công nghệ sử dụng**: JPA `@Version`, Hibernate Optimistic Locking, React Optimistic State.
* **Tham chiếu**: *Linear Sync Protocol, Google Docs conflict resolution*.

---

### 4.4. Theo dõi Sự hiện diện Trực tuyến Thời gian thực (Multiplayer Live Presence)
* **Vấn đề**: Hiển thị avatar những ai đang cùng xem task này ("Đang xem: An, Bình"), trạng thái đang nhập bình luận ("Minh đang gõ..."), và task nào đang bị ai đó kéo thả.
* **Cách hoạt động**:
  1. Khi người dùng mở một Task Detail, client gửi một frame WebSocket `PRESENCE_JOIN(userId, taskId)`.
  2. Dữ liệu trạng thái hiện diện là **dữ liệu tạm thời (Ephemeral State)**, không lưu vào PostgreSQL mà lưu vào **Redis Hash / Redis Key có TTL (Time-To-Live)** (ví dụ: 10 giây). Client định kỳ gửi heartbeat ping để gia hạn TTL.
  3. Khi user đóng tab hoặc mất mạng, Redis tự động expire key và broadcast sự kiện `PRESENCE_LEAVE` tới các client còn lại.
* **Công nghệ sử dụng**: `Redis In-Memory Key-Value (TTL)`, `WebSocket Ephemeral Events`.
* **Tham chiếu**: *Notion Live Avatars, Figma Co-presence, Linear Live Viewers*.

---

### 4.5. Phát hành Sự kiện Đảm bảo Toàn vẹn (Transactional Outbox Pattern & Kafka Event Streaming)
* **Vấn đề**: Tránh tình trạng "Dual Write": CSDL đã commit nhưng bắn Message lên Kafka thất bại (hoặc ngược lại), làm mất thông báo hoặc sai lệch dữ liệu phân tán.
* **Cách hoạt động**:
  1. Khi tạo Task hoặc đổi Assignee, trong cùng 1 Database Transaction: Lưu `Task` và chèn 1 bản ghi vào bảng `outbox_events (id, aggregate_type, payload, status = PENDING)`.
  2. Một Background Poller (hoặc Debezium CDC) quét bảng `outbox_events` $\rightarrow$ Bắn vào Kafka Topic `task-events-topic` $\rightarrow$ Đánh dấu `PROCESSED`.
  3. `integration-notification-service` consume topic này để gửi Email, Webhook GitHub và Notification mà không làm chậm `work-service`.
* **Công nghệ sử dụng**: `Transactional Outbox Pattern`, `Spring Data JPA Transaction Manager`, `Apache Kafka`.
* **Tham chiếu**: *Uber Microservices Architecture, Shopify Event-driven pipeline*.

---

### 4.6. Bộ sinh Mã Task Tự động Nguyên tử (PostgreSQL Atomic Sequence Key Generator)
* **Vấn đề**: Các task hiển thị mã đẹp như `PTMS-1`, `PTMS-2` theo từng Project Key. Khi có hàng trăm request tạo task đồng thời, không được phép trùng mã hoặc nhảy khoảng trống.
* **Cách hoạt động**:
  1. Sử dụng PostgreSQL Sequence động theo project: `CREATE SEQUENCE IF NOT EXISTS task_seq_PROJECT_KEY`.
  2. Khi tạo task, gọi hàm nguyên tử `SELECT nextval('task_seq_PROJECT_KEY')`.
  3. Cơ chế Lock-free ở mức DB Engine giúp sinh mã tuần tự tuyệt đối mà không cần dùng `@Lock(PESSIMISTIC_WRITE)` làm nghẽn luồng xử lý.
* **Công nghệ sử dụng**: `PostgreSQL Atomic Sequences`, `Native SQL Nextval Query`.
* **Tham chiếu**: *Jira Issue Key Generator, Linear Identifier Sequencer*.

---

### 4.7. Lưu vết Kiểm toán Phi xâm lấn Tự động (Non-intrusive AOP Audit Logging)
* **Vấn đề**: Ghi nhận toàn bộ lịch sử ai đã đổi status từ `TODO` sang `DONE`, đổi deadline, hay đổi người thực hiện mà không làm bẩn code nghiệp vụ ở tầng Service.
* **Cách hoạt động**:
  1. Sử dụng **Spring AOP (Aspect-Oriented Programming)** hoặc Hibernate `EntityListener` bắt sự kiện `@PreUpdate` / `@PostUpdate` của thực thể `Task`.
  2. So sánh state trước (`old_state`) và sau (`new_state`) $\rightarrow$ Tự động trích xuất các trường bị thay đổi dưới dạng `JSONB` diff (ví dụ: `{"status": {"old": "TODO", "new": "IN_PROGRESS"}}`).
  3. Bất đồng bộ (`@Async`) lưu vào bảng `task_activity_logs`.
* **Công nghệ sử dụng**: `Spring AOP`, `Hibernate EntityListener`, `PostgreSQL JSONB`.
* **Tham chiếu**: *GitHub Issue Timeline, Linear Activity History*.

---

## 5. CHI TIẾT TƯƠNG TÁC VỚI REDIS & KAFKA (REDIS & KAFKA INTEGRATION)

Trong kiến trúc của `work-service`, **Redis** và **Kafka** được sử dụng phối hợp nhịp nhàng theo nguyên tắc:
* **Redis**: Phục vụ **Sub-millisecond In-Memory Real-time State & Synchronization** (Dữ liệu tạm thời, độ trễ cực thấp < 2ms).
* **Kafka**: Phục vụ **Persistent Event Streaming & Inter-Service Communication** (Giao tiếp liên microservice bền vững, đảm bảo không mất tin).

```
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                                       WORK-SERVICE                                          │
└───────────────────────────┬─────────────────────────────────────────────┬───────────────────┘
                            │                                             │
      [REAL-TIME / IN-MEMORY / EPHEMERAL]                        [ASYNC / DURABLE / PERSISTENT]
                            │                                             │
                            ▼                                             ▼
               ┌────────────────────────┐                    ┌────────────────────────┐
               │         REDIS          │                    │         KAFKA          │
               ├────────────────────────┤                    ├────────────────────────┤
               │ • WebSocket Cluster    │                    │ • Giao tiếp đa Service │
               │ • Live Presence        │                    │ • Gửi Email / Webhook  │
               │ • Caching Kanban Board │                    │ • Event Sourcing Audit │
               └────────────────────────┘                    └────────────────────────┘
```

---

### 5.1. Các kịch bản sử dụng REDIS trong `work-service`

| Kịch bản | Data Structure & Key Format | TTL (Thời gian sống) | Mô tả chi tiết |
| :--- | :--- | :---: | :--- |
| **1. WebSocket Pub/Sub Backplane** | `Channel: work:events` | Không (Stream) | Khi chạy nhiều instance `work-service`, Redis làm cầu nối phát tán sự kiện kéo thả Kanban sang tất cả instance server để broadcast xuống Client. |
| **2. Live Presence (Ai đang xem task)** | `Set: presence:task:{taskId}`<br>Lưu danh sách `userId` | `10s` (Gia hạn qua heartbeat) | Client gửi heartbeat định kỳ mỗi 5s. Khi user đóng tab, key tự hết hạn sau 10s $\rightarrow$ Không tốn tài nguyên DB. |
| **3. Live Typing (Đang gõ bình luận)** | `String: typing:task:{taskId}:{userId}` | `3s` | Bật/tắt chỉ báo *"Nguyễn Văn A đang gõ bình luận..."* tức thì. |
| **4. Cache Bảng Kanban (Read Acceleration)** | `String (JSON): cache:board:project:{projectId}` | `5 phút`<br>*(Cache-Aside)* | Giảm 90% truy vấn `JOIN` phức tạp khi tải Board. **Invalidation Strategy**: Tự động xóa cache khi có bất kỳ thay đổi nào (`Task Created/Moved/Deleted`). |

---

### 5.2. Các kịch bản sử dụng KAFKA trong `work-service`

`work-service` đóng vai trò là **Kafka Producer** chính, phát các Domain Events có cấu trúc lên Broker:

| Kafka Topic | Event Type | Payload Data (JSON) | Service tiếp nhận (Consumer) & Hành động |
| :--- | :--- | :--- | :--- |
| **`task-events-topic`** | `TASK_ASSIGNED` | `{ taskId, projectId, assigneeId, assignerId, title, dueDate }` | `integration-notification-service` $\rightarrow$ Gửi email thông báo & Push in-app alert cho người được giao việc. |
| **`task-events-topic`** | `TASK_STATUS_CHANGED` | `{ taskId, projectId, oldStatus, newStatus, actorId, code }` | • `integration-notification-service` $\rightarrow$ Báo cho Task Watchers.<br>• `ai-service` $\rightarrow$ Cập nhật tiến độ Sprint tổng quan. |
| **`task-events-topic`** | `TASK_DUE_APPROACHING` | `{ taskId, title, assigneeId, dueDate, hoursLeft }` | `integration-notification-service` $\rightarrow$ Gửi email nhắc nhở deadline sắp hết hạn (trước 24h / 2h). |
| **`comment-events-topic`**| `COMMENT_CREATED` | `{ commentId, taskId, authorId, mentionedUserIds: [...], snippet }` | `integration-notification-service` $\rightarrow$ Bắn thông báo cho người được `@mention` trong bình luận. |
| **`project-events-topic`**| `MILESTONE_COMPLETED` | `{ milestoneId, projectId, completedTasksCount, timestamp }` | `ai-service` $\rightarrow$ Tự động sinh báo cáo tóm tắt kết quả cột mốc bàn giao. |

---

### 5.3. Cấu hình & Lộ trình triển khai (Pragmatic Roadmap)

```yaml
# Ví dụ cấu hình Kafka & Redis trong application.yaml
spring:
  # ─── Redis Caching & Pub/Sub ───
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 2000ms

  # ─── Apache Kafka Producer ───
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      properties:
        enable.idempotence: true # Chống gửi trùng tin nhắn
        acks: all
```

* **Giai đoạn 1 (Development & Unit Testing)**: 
  * Phát triển các luồng CRUD cốt lõi, WebSocket in-memory broker để chạy nhanh, nhẹ máy.
* **Giai đoạn 2 (Integration & Enterprise Ready)**: 
  * Kết nối Redis (chạy container Docker) cho Live Presence và Cache.
  * Kết nối Kafka để kích hoạt luồng bắn Notification liên dịch vụ hoàn chỉnh trước khi nghiệm thu/bảo vệ đồ án.

---

## 6. CẤU TRÚC THƯ MỤC SOURCE CODE (`work-service`)

```
backend/work-service/
├── pom.xml
└── src/main/java/com/datn/workservice/
    ├── WorkServiceApplication.java
    ├── config/           # WebSocketConfig, SecurityConfig, RedisConfig, KafkaProducerConfig
    ├── controller/       # TeamController, ProjectController, TaskController, CommentController
    ├── dto/
    │   ├── request/      # CreateTaskRequest, MoveTaskPositionRequest, AddCommentRequest,...
    │   └── response/     # TaskResponse, KanbanBoardResponse, ProjectDetailResponse,...
    ├── entity/           # Team, Project, Milestone, Task, Comment, Attachment, ActivityLog,...
    ├── enums/            # ProjectStatus, TaskStatus, TaskPriority, TaskType, DependencyType
    ├── event/            # TaskAssignedEvent, TaskStatusChangedEvent, OutboxEventPublisher
    ├── exception/        # ResourceNotFoundException, OptimisticLockConflictException,...
    ├── repository/       # TeamRepository, ProjectRepository, TaskRepository,...
    ├── service/          # TeamService, ProjectService, TaskService, KanbanRealtimeService
    └── service/impl/     # Implementation Classes & Redis/WebSocket Integration
```

