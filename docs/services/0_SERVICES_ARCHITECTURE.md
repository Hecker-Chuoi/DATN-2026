# 🏗️ KIẾN TRÚC PHÂN CHIA MICROSERVICES – DATN-2026

> **Mục tiêu thiết kế**: **Gọn gàng – Tách biệt ranh giới Private / Public – Chuẩn Domain-Driven Design (DDD) – Dễ bảo trì và bảo vệ Đồ án Tốt nghiệp.**

---

## 1. NGUYÊN TẮC PHÂN CHIA HỆ THỐNG (BOUNDED CONTEXTS)

Hệ thống được chia thành **5 Microservices chuyên biệt** + **1 API Gateway**, phân định rõ ràng giữa:
1. **Quản trị Định danh & Tổ chức (IAM & Multi-tenancy)**: `identity-service`
2. **Không gian Làm việc & Dự án Nội bộ (Private Work Management)**: `work-service`
3. **Không gian Chia sẻ Cộng đồng & Mẫu Dự án (Public Community & Marketplace)**: `community-service`
4. **Tích hợp Bên thứ ba & Thông báo Realtime (Integrations & Notifications)**: `integration-notification-service`
5. **Trợ lý Trí tuệ Nhân tạo (AI Agent & Smart Assistant)**: `ai-service`

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
      ┌────────────────────┬─────────────────┼─────────────────┬────────────────────┐
      ▼                    ▼                 ▼                 ▼                    ▼
┌─────────────┐   ┌─────────────────┐ ┌─────────────┐ ┌─────────────────────┐  ┌─────────────┐
│  Identity   │   │      Work       │ │  Community  │ │    Integration &    │  │ AI & Smart  │
│   Service   │   │     Service     │ │   Service   │ │ Notification Service│  │  Assistant  │
│   (:8082)   │   │     (:8083)     │ │   (:8085)   │ │       (:8084)       │  │   (:8086)   │
└─────────────┘   └─────────────────┘ └─────────────┘ └─────────────────────┘  └─────────────┘
  Users, Roles,     Teams, Projects,    Initiatives,    GitHub Sync, Mail,       LLM Agents,
  Workspaces,       Milestones,         Posts, Tags,    Calendar, In-App Push,   Task Auto-Gen,
  Permissions       Tasks, Kanban       Templates       SSE / Realtime Alerts    RAG Knowledge
```

---

## 2. DANH SÁCH & BẢN ĐỒ CÁC SERVICE

| STT | Service Name | Port | Công nghệ | Trách nhiệm chính & Bảng CSDL phụ trách |
| :-: | :--- | :-: | :--- | :--- |
| **0** | **`api-gateway`** | `8081` | Spring Cloud Gateway, WebFlux | • Single Entry Point, JWT Verification (Keycloak JWKS)<br>• Global CORS, Rate Limiting, Request Logging, Routing |
| **1** | **`identity-service`** | `8082` | Spring Boot 3, Keycloak, JPA | • Xác thực người dùng (Local, Google/GitHub OAuth2 qua Keycloak)<br>• Quản lý Workspace & phân quyền RBAC đa cấp (Workspace/Team/Project)<br>• **Bảng**: `users`, `workspaces`, `workspace_members`, `roles`, `permissions`, `role_permissions`, `user_roles` |
| **2** | **`work-service`** | `8083` | Spring Boot 3, JPA, WebSocket | • Core Work Management: Team, Dự án (kèm Dự án Mẫu `is_template = true`), Cột mốc (Milestones)<br>• Agile/Kanban Board, Issues/Tasks lifecycle, Subtasks đa cấp<br>• Task Comments, Attachments, Dependencies, Activity Audit Logs<br>• Lịch sử nhân bản Dự án / Template (`project_clones`)<br>• **Bảng**: `teams`, `team_members`, `projects`, `milestones`, `tasks`, `task_labels`, `task_label_map`, `task_watchers`, `task_comments`, `task_attachments`, `task_dependencies`, `task_activity_logs`, `project_clones` |
| **3** | **`community-service`** | `8085` | Spring Boot 3, JPA, Redis | • Quản lý Sáng kiến cộng đồng (Initiatives, Upvotes/Downvotes)<br>• Blog, Bài viết chia sẻ kinh nghiệm kỹ thuật (Posts, Tags, Post Comments, Post Votes)<br>• **Bảng**: `initiatives`, `initiative_votes`, `posts`, `tags`, `post_tags`, `post_comments`, `post_votes` |
| **4** | **`integration-notification-service`** | `8084` | Spring Boot 3, WebFlux/SSE, Kafka | • Gửi thông báo đa kênh: In-App (SSE/WebSocket), Email SMTP, Push<br>• Tích hợp bên ngoài: GitHub Webhook sync, Google Calendar sync, Email-to-task<br>• **Bảng**: `notifications`, `notification_settings`, `integrations`, `github_repo_links`, `calendar_syncs`, `email_task_mappings` |
| **5** | **`ai-service`** | `8086` | Spring AI hoặc Python (FastAPI) | • Trợ lý AI hội thoại ngữ cảnh, tóm tắt tiến độ, dự đoán deadline<br>• Tự động bóc tách và sinh task từ biên bản họp / prompt<br>• **Bảng**: `ai_conversations`, `ai_messages`, `ai_api_keys`, `ai_task_generations` |

---

## 3. CHI TIẾT TỪNG BOUNDED CONTEXT

### 3.1. `identity-service` (Port: 8082)
* **Domain**: Identity & Access Management (IAM), Multi-tenant Organization.
* **Vai trò**:
  * Đóng vai trò là cầu nối với Keycloak IAM Server để quản lý credentials và phát hành Access/Refresh Token.
  * Lưu trữ hồ sơ người dùng (`users`), quản lý các không gian làm việc (`workspaces`) và thành viên tham gia (`workspace_members`).
  * Thực thi ma trận phân quyền động RBAC theo phạm vi (`scope_type = workspace / team / project`).
* **Bảng CSDL phụ trách**:
  * `users`
  * `workspaces`, `workspace_members`
  * `roles`, `permissions`, `role_permissions`, `user_roles`

---

### 3.2. `work-service` (Port: 8083)
* **Domain**: Core Work Management Engine (Private / Internal).
* **Vai trò**:
  * Phục vụ các hoạt động lập kế hoạch và thực thi công việc nội bộ của từng Workspace.
  * Quản lý Team, Projects, Milestones/Sprints.
  * Xử lý vòng đời Task/Issue (Todo, In Progress, In Review, Done) trên bảng Kanban với cơ chế Optimistic Locking (`version`) chống ghi đè dữ liệu.
  * Cung cấp cây Sub-tasks phân cấp, quản lý ràng buộc phụ thuộc (`blocks`, `relates_to`), trao đổi bình luận nội bộ, tệp đính kèm và tự động ghi vết thay đổi (`task_activity_logs`).
  * Bắn sự kiện realtime qua WebSocket STOMP topic `/topic/project/{projectId}` khi thẻ task được kéo thả.
* **Bảng CSDL phụ trách**:
  * `teams`, `team_members`
  * `projects`, `milestones`
  * `tasks`, `task_labels`, `task_label_map`, `task_watchers`, `task_comments`, `task_attachments`, `task_dependencies`, `task_activity_logs`

---

### 3.3. `community-service` (Port: 8085)
* **Domain**: Public Collaboration, Knowledge Sharing & Marketplace.
* **Vai trò**:
  * Môi trường mở giúp các lập trình viên/nhóm chia sẻ các sáng kiến (`initiatives`), quy trình mẫu (`templates`) và bài viết blog (`posts`).
  * Quản lý tương tác cộng đồng: Upvote/Downvote, bình luận thảo luận, gắn thẻ bài viết (`tags`).
  * **Tính năng Nhân bản (Template / Initiative Cloning)**: Khi người dùng bấm *"Sử dụng Template"* hoặc *"Nhân bản Sáng kiến này"*, service này sẽ gửi request sang `work-service` để tự động tạo một Project mới hoàn chỉnh trong Workspace của họ.
* **Bảng CSDL phụ trách**:
  * `initiatives`, `initiative_votes`
  * `posts`, `tags`, `post_tags`, `post_comments`, `post_votes`
  * `templates`, `project_clones`

---

### 3.4. `integration-notification-service` (Port: 8084)
* **Domain**: Third-party Integrations & Multi-channel Alerts.
* **Vai trò**:
  * Tách biệt các tác vụ I/O mạng chậm (gọi GitHub API, gửi mail SMTP, đồng bộ Google Calendar).
  * Lắng nghe các sự kiện từ Message Broker (Kafka/RabbitMQ) như `TaskAssignedEvent`, `DeadlineApproachingEvent`, `CommentMentionedEvent` để gửi thông báo In-app và Email.
* **Bảng CSDL phụ trách**:
  * `notifications`, `notification_settings`
  * `integrations`, `github_repo_links`, `calendar_syncs`, `email_task_mappings`

---

### 3.5. `ai-service` (Port: 8086)
* **Domain**: AI Agent, LLM Prompting & NLP Workflow.
* **Vai trò**:
  * Kết nối tới các nhà cung cấp mô hình ngôn ngữ lớn (OpenAI GPT-4o, Google Gemini, Anthropic Claude) sử dụng API Key mã hóa của người dùng hoặc hệ thống.
  * Hỗ trợ streaming response (`text/event-stream`), sinh danh sách task con từ mô tả tính năng hoặc biên bản cuộc họp.
* **Bảng CSDL phụ trách**:
  * `ai_conversations`, `ai_messages`
  * `ai_api_keys`
  * `ai_task_generations`

---

## 4. MA TRẬN ROUTING TẠI API GATEWAY

| Route ID | Path Predicate | Target Service URI | Mô tả |
| :--- | :--- | :--- | :--- |
| `identity-service` | `/identity/**` | `http://localhost:8082` | Đăng nhập, đăng ký, profile, quản lý workspace & thành viên |
| `work-service` | `/work/**` | `http://localhost:8083` | Quản lý team, dự án, milestones, task, kanban board, audit logs |
| `community-service` | `/community/**` | `http://localhost:8085` | Sáng kiến, bài viết blog, template mẫu, bình chọn, clone dự án |
| `notification-service` | `/notifications/**`, `/integrations/**` | `http://localhost:8084` | Lấy thông báo, cấu hình kênh, sync Google Calendar, GitHub Webhooks |
| `ai-service` | `/ai/**` | `http://localhost:8086` | Chatbot AI trợ lý, sinh task tự động |

---

## 5. CƠ CHẾ GIAO TIẾP LIÊN DỊCH VỤ (INTER-SERVICE COMMUNICATION)

```mermaid
flowchart TD
    Client([Client Browser / Mobile]) -->|REST / WS| Gateway[API Gateway :8081]
    
    Gateway -->|/identity/**| Identity[Identity Service :8082]
    Gateway -->|/work/**| Work[Work Service :8083]
    Gateway -->|/community/**| Community[Community Service :8085]
    Gateway -->|/notifications/**| Notif[Integration & Notification :8084]
    Gateway -->|/ai/**| AI[AI Service :8086]
    
    Community -.->|REST OpenFeign: Clone Project from Template| Work
    Work -.->|1. Publish Event: TaskAssigned / CommentCreated| Broker[(Message Broker: Kafka / Redis)]
    Broker -.->|2. Consume Event & Push Notification| Notif
    AI -.->|REST: Fetch Project & Task Context for RAG| Work
    
    Notif -.->|Push Realtime Alerts (SSE / WS)| Client
```

1. **Đồng bộ (Synchronous - REST via Spring Cloud OpenFeign)**:
   - Sử dụng cho các tác vụ cần kết quả tức thì: `community-service` gọi `work-service` để khởi tạo cấu trúc dự án từ mẫu; `ai-service` gọi `work-service` để lấy ngữ cảnh backlog phục vụ prompt.
2. **Bất đồng bộ (Asynchronous - Event-driven via Kafka)**:
   - Các sự kiện thay đổi dữ liệu (`TaskCreated`, `TaskStatusChanged`, `TaskAssigned`) được `work-service` publish lên Kafka Topic. `notification-service` consume để gửi email và đẩy thông báo In-App.
