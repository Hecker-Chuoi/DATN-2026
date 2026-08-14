# 🏗️ KIẾN TRÚC PHÂN CHIA MICROSERVICES – DATN-2026

> **Mục tiêu thiết kế**: **Gọn gàng – Đủ tính độc lập – Tối ưu tài nguyên máy dev – Dễ bảo trì và phát triển.**  
> Tránh tình trạng phân rã quá nhỏ (Microservice Sprawl) làm tốn RAM, phức tạp hóa giao tiếp mạng và vướng bẫy Distributed Transaction không cần thiết.

---

## 1. NGUYÊN TẮC PHÂN CHIA (BOUNDED CONTEXTS)

Dựa trên toàn bộ 25+ bảng CSDL của hệ thống, chúng ta áp dụng mô hình **Domain-Driven Design (DDD)** để gom các thực thể có mối quan hệ dữ liệu mật thiết (tightly coupled) và cùng vòng đời vào cùng một Bounded Context.

```
                                  ┌──────────────────────┐
                                  │   Frontend (React)   │
                                  └──────────┬───────────┘
                                             │ HTTP / WS
                                             ▼
                                  ┌──────────────────────┐
                                  │  API Gateway (:8081) │
                                  └──────────┬───────────┘
                                             │
      ┌────────────────────┬─────────────────┴───────────────┬────────────────────┐
      ▼                    ▼                                 ▼                    ▼
┌─────────────┐   ┌─────────────────┐             ┌─────────────────────┐  ┌─────────────┐
│  Identity   │   │  Project & Task │             │    Integration &    │  │ AI & Smart  │
│   Service   │   │     Service     │             │ Notification Service│  │  Assistant  │
│   (:8082)   │   │     (:8083)     │             │       (:8084)       │  │   (:8085)   │
└─────────────┘   └─────────────────┘             └─────────────────────┘  └─────────────┘
  Users, Roles,     Projects, Tasks,                GitHub Sync, Mail,       LLM Agents,
  Workspaces,       Milestones, Teams,              Calendar, In-App Push,   Task Auto-Gen,
  Permissions       Comments, Kanban                SSE / Realtime Alerts    RAG Knowledge
```

---

## 2. DANH SÁCH & BẢN ĐỒ CÁC SERVICE ĐỀ XUẤT (4 SERVICE CỐT LÕI + 1 AI SERVICE)

| STT | Service Name | Port | Công nghệ đề xuất | Trách nhiệm chính & Bảng CSDL phụ trách |
| :-: | :--- | :-: | :--- | :--- |
| **0** | **`api-gateway`** | `8081` | Spring Cloud Gateway, WebFlux | • Single Entry Point<br>• JWT Validation, Rate Limiting, CORS, Global Routing |
| **1** | **`identity-service`** | `8082` | Spring Boot 3, Spring Security, JPA | • Quản lý User, Authentication (Local, OAuth2 Google/GitHub)<br>• Quản lý Workspace & phân quyền RBAC đa cấp<br>• **Bảng**: `users`, `workspaces`, `workspace_members`, `roles`, `permissions`, `role_permissions`, `user_roles` |
| **2** | **`project-task-service`** | `8083` | Spring Boot 3, JPA, WebSocket | • Quản lý Team, Project, Milestone, Kanban Board<br>• Task lifecycle, Sub-tasks, Dependencies, Comments, Attachments, Activity Logs<br>• Quản lý Templates, Initiatives & Blog/Post cộng đồng<br>• **Bảng**: `teams`, `team_members`, `projects`, `milestones`, `tasks`, `task_*`, `templates`, `initiatives`, `posts`, `tags`, `post_*` |
| **3** | **`integration-notification-service`** | `8084` | Spring Boot 3, WebFlux/SSE, Kafka | • Gửi và lưu trữ thông báo (In-app, Email, Push)<br>• Tích hợp bên ngoài: GitHub Webhook sync, Google Calendar Sync, Email-to-task<br>• **Bảng**: `notifications`, `notification_settings`, `integrations`, `github_repo_links`, `calendar_syncs`, `email_task_mappings` |
| **4** | **`ai-service`** *(Khuyến nghị)* | `8085` | Python (FastAPI) hoặc Spring AI | • Trợ lý AI hội thoại, sinh task tự động từ yêu cầu/họp<br>• Tóm tắt tiến độ dự án, phân tích rủi ro deadline<br>• **Bảng**: `ai_conversations`, `ai_messages`, `ai_api_keys`, `ai_task_generations` |

---

## 3. CHI TIẾT TỪNG SERVICE

### 3.1. `identity-service` (Port: 8082)
* **Domain**: Identity & Access Management (IAM), Organization Multi-tenancy.
* **Tại sao gom Workspace vào Identity Service?**
  * Workspace là đơn vị sở hữu dữ liệu (Tenant). Mọi request vào hệ thống đều cần kiểm tra `user` có thuộc `workspace` đó hay không và có quyền `role/permission` gì.
  * Việc để chung `workspaces` và `users` giúp phát hành JWT Token chứa claim `workspace_ids` và `roles` gọn gàng, tránh gọi RPC sang service khác chỉ để check membership.
* **CSDL phụ trách**:
  * `users`
  * `workspaces`, `workspace_members`
  * `roles`, `permissions`, `role_permissions`, `user_roles`

---

### 3.2. `project-task-service` (Port: 8083)
* **Domain**: Core Work Management (Agile, Scrum, Kanban) & Collaboration.
* **Tại sao gom Team, Project, Task, Comment và Blog/Template vào đây?**
  * **Tính toàn vẹn (ACID)**: Khi tạo Task, kiểm tra Milestone, kéo thả Kanban thay đổi `position`, cập nhật `version` (Optimistic Locking) diễn ra liên tục. Đặt chung giúp transaction mượt mà, không bị distributed locks.
  * **Template & Community**: Templates được trích xuất từ Project (`source_project_id`), Initiative được xuất bản từ Project (`project_id`), Project Clones tạo ra Project mới. Đặt chung giúp việc clone dự án diễn ra trong 1 database transaction duy nhất cực kỳ nhanh.
* **CSDL phụ trách**:
  * `teams`, `team_members`
  * `projects`, `milestones`
  * `tasks`, `task_labels`, `task_label_map`, `task_watchers`, `task_comments`, `task_attachments`, `task_dependencies`, `task_activity_logs`
  * `templates`, `initiatives`, `initiative_votes`, `project_clones`
  * `posts`, `tags`, `post_tags`, `post_comments`, `post_votes`

---

### 3.3. `integration-notification-service` (Port: 8084)
* **Domain**: External Integrations & Async Event Processing.
* **Tại sao tách riêng Notification & Integration?**
  * **Hiệu năng**: Các tác vụ gửi email SMTP, gọi GitHub API, đồng bộ Google Calendar là tác vụ I/O chậm (External Network Latency). Tách riêng giúp `project-task-service` không bị nghẽn thread.
  * **Asynchronous Communication**: Lắng nghe sự kiện từ Message Queue (Kafka/RabbitMQ) hoặc Redis Pub/Sub khi có task mới được tạo để bắn In-App Notification (qua Server-Sent Events / WebSocket) và gửi Email.
* **CSDL phụ trách**:
  * `notifications`, `notification_settings`
  * `integrations`, `github_repo_links`, `calendar_syncs`, `email_task_mappings`

---

### 3.4. `ai-service` (Port: 8085)
* **Domain**: Generative AI, Task Automation & NLP.
* **Tại sao nên tách riêng?**
  * AI Service thường có các thư viện hỗ trợ mạnh bằng Python (LangChain, LlamaIndex, OpenAI SDK, FastAPI) hoặc sử dụng Spring AI với các cơ chế Streaming response (`text/event-stream`).
  * Tác vụ gọi LLM (OpenAI, Gemini, Anthropic) tốn thời gian (3-10 giây) và tài nguyên I/O stream, tách riêng giúp không ảnh hưởng đến API xử lý CRUD của người dùng.
* **CSDL phụ trách**:
  * `ai_conversations`, `ai_messages`
  * `ai_api_keys`
  * `ai_task_generations`

---

## 4. CƠ CHẾ GIAO TIẾP GIỮA CÁC SERVICE (INTER-SERVICE COMMUNICATION)

```mermaid
flowchart TD
    Client([Client Browser / Mobile]) -->|REST / WS| Gateway[API Gateway :8081]
    
    Gateway -->|/identity/**| Identity[Identity Service :8082]
    Gateway -->|/projects/**, /tasks/**| ProjectTask[Project & Task Service :8083]
    Gateway -->|/notifications/**, /integrations/**| Notif[Integration & Notification :8084]
    Gateway -->|/ai/**| AI[AI Service :8085]
    
    ProjectTask -.->|1. Fire Event (TaskCreated / TaskAssigned)| Queue[(Message Broker: Kafka / Redis)]
    Queue -.->|2. Consume Event| Notif
    
    AI -.->|REST / OpenFeign (Fetch Project Context)| ProjectTask
    Notif -.->|Push Realtime Notification| Client
```

1. **Đồng bộ (Synchronous - REST / OpenFeign)**:
   - Sử dụng khi cần dữ liệu tức thời, ví dụ: AI Service cần lấy thông tin dự án hiện tại từ `project-task-service` để làm ngữ cảnh prompt (RAG).
2. **Bất đồng bộ (Asynchronous - Event-driven với Kafka / RabbitMQ / Redis Streams)**:
   - `project-task-service` khi có sự kiện (Assign Task, Add Comment, Due Date Warning) chỉ cần publish event lên Broker.
   - `integration-notification-service` consume event để gửi mail, ghi log thông báo và đẩy SSE xuống browser.

---

## 5. BẢNG ROUTING CONFIGURATION TẠI GATEWAY

| Route ID | Path Predicate | Target Service URI | Mô tả |
| :--- | :--- | :--- | :--- |
| `identity-service` | `/identity/**` | `http://localhost:8082` | Đăng nhập, đăng ký, profile, quản lý workspace & thành viên |
| `project-task-service` | `/projects/**`, `/tasks/**`, `/teams/**`, `/community/**` | `http://localhost:8083` | Quản lý dự án, công việc, kanban board, blog, templates |
| `notification-service` | `/notifications/**`, `/integrations/**` | `http://localhost:8084` | Lấy danh sách thông báo, cấu hình webhook, sync calendar |
| `ai-service` | `/ai/**` | `http://localhost:8085` | Chatbot, sinh task tự động, gợi ý tiến độ |

---

## 6. ĐÁNH GIÁ LỢI ÍCH CỦA PHƯƠNG ÁN

✅ **Tiết kiệm RAM & CPU máy phát triển**: Chỉ chạy 3-4 service Spring Boot thay vì 8-10 service. Máy 8GB - 16GB RAM hoàn toàn chạy mượt mà kèm Docker và Frontend.  
✅ **Tránh phân mảnh dữ liệu (Data Fragmentation)**: Các bảng liên quan chặt chẽ như `tasks` và `task_comments`, `projects` và `milestones` nằm chung CSDL nên truy vấn JOIN nhanh, không phải viết logic tổng hợp DTO phức tạp qua mạng.  
✅ **Đầy đủ tính chất Microservices**: Độc lập deploy, độc lập scale (ví dụ Task Service chịu tải nhiều có thể scale 3 instance, Identity scale 2 instance), có API Gateway định tuyến tập trung.  
✅ **Dễ bảo vệ đồ án tốt nghiệp**: Cấu trúc kiến trúc rõ ràng, có căn cứ thiết kế chuẩn DDD Bounded Context, thuyết trình chuyên nghiệp.
