# 📋 Tài liệu Kỹ thuật: Project & Task Service – DATN-2026

> **Module:** `project-task-service`  
> **Port:** `8083`  
> **Tech Stack:** Spring Boot 3, Spring Data JPA, PostgreSQL, Spring WebSocket / STOMP, Kafka Producer  
> **Java:** 21

---

## 1. TỔNG QUAN SERVICE

`project-task-service` là dịch vụ lõi (Core Business Service) chịu trách nhiệm toàn bộ về quản lý tiến độ, dự án, công việc và tính năng cộng tác nhóm trong từng Workspace.

```
                      ┌────────────────────────┐
                      │   API Gateway (:8081)  │
                      └───────────┬────────────┘
                                  │ Forward /projects/**, /tasks/**
                                  ▼
                      ┌────────────────────────┐
                      │  Project-Task-Service  │  :8083
                      │   (Core Work Engine)   │
                      └───────────┬────────────┘
                                  │
          ┌───────────────────────┼────────────────────────┐
          ▼                       ▼                        ▼
┌───────────────────┐   ┌───────────────────┐    ┌───────────────────┐
│ PostgreSQL DB     │   │ Kafka Event Pub   │    │ WebSocket Server  │
│ (Projects, Tasks, │   │ (Task Events,     │    │ (Realtime Kanban  │
│  Comments, Logs)  │   │  Activity Stream) │    │  Drag & Drop)     │
└───────────────────┘   └───────────────────┘    └───────────────────┘
```

---

## 2. PHẠM VI TRÁCH NHIỆM & CÁC THỰC THỂ PHỤ TRÁCH

Service quản lý trực tiếp các thực thể dữ liệu sau:
1. **Team & Thành viên**: `teams`, `team_members`
2. **Dự án & Cột mốc**: `projects`, `milestones`
3. **Công việc & Chi tiết**: `tasks`, `task_labels`, `task_label_map`, `task_watchers`, `task_comments`, `task_attachments`, `task_dependencies`, `task_activity_logs`
4. **Cộng đồng & Mẫu**: `templates`, `initiatives`, `initiative_votes`, `project_clones`, `posts`, `tags`, `post_tags`, `post_comments`, `post_votes`

---

## 3. DANH SÁCH REST API CHÍNH

### 3.1. Project & Milestone APIs (`/api/v1/projects`)

| Method | Endpoint | Quyền hạn | Mô tả |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/projects` | `project.create` | Tạo mới dự án trong Workspace |
| `GET` | `/api/v1/projects?workspaceId={id}` | `project.view` | Lấy danh sách dự án của Workspace |
| `GET` | `/api/v1/projects/{projectId}` | `project.view` | Xem chi tiết 1 dự án |
| `PUT` | `/api/v1/projects/{projectId}` | `project.edit` | Cập nhật thông tin dự án |
| `DELETE`| `/api/v1/projects/{projectId}` | `project.delete` | Lưu trữ / Xóa dự án |
| `POST` | `/api/v1/projects/{projectId}/milestones` | `project.edit` | Tạo cột mốc mới cho dự án |
| `GET` | `/api/v1/projects/{projectId}/milestones` | `project.view` | Danh sách cột mốc |

### 3.2. Task & Kanban APIs (`/api/v1/tasks`)

| Method | Endpoint | Quyền hạn | Mô tả |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/tasks` | `task.create` | Tạo task mới (tự sinh mã code theo Project Key) |
| `GET` | `/api/v1/tasks?projectId={id}` | `task.view` | Lấy danh sách task (hỗ trợ phân trang, filter) |
| `GET` | `/api/v1/tasks/board?projectId={id}` | `task.view` | Lấy dữ liệu gom nhóm theo cột Kanban (`todo`, `in_progress`,...) |
| `GET` | `/api/v1/tasks/{taskId}` | `task.view` | Lấy chi tiết task kèm sub-tasks, comments, attachments |
| `PUT` | `/api/v1/tasks/{taskId}` | `task.edit` | Cập nhật nội dung task (kiểm tra `version` Optimistic Lock) |
| `PATCH`| `/api/v1/tasks/{taskId}/move` | `task.edit` | Kéo thả Kanban: đổi status & position thứ tự |
| `DELETE`| `/api/v1/tasks/{taskId}` | `task.delete` | Xóa task |

### 3.3. Task Extension APIs (Comments, Labels, Attachments, Activity)

| Method | Endpoint | Mô tả |
| :--- | :--- | :--- |
| `POST` | `/api/v1/tasks/{taskId}/comments` | Thêm bình luận vào task |
| `GET` | `/api/v1/tasks/{taskId}/comments` | Lấy cây bình luận thảo luận |
| `POST` | `/api/v1/tasks/{taskId}/attachments` | Đính kèm file vào task |
| `GET` | `/api/v1/tasks/{taskId}/activity-logs` | Lấy lịch sử chỉnh sửa / kiểm toán task |
| `POST` | `/api/v1/tasks/{taskId}/dependencies` | Thiết lập quan hệ phụ thuộc (`blocks`, `relates_to`) |

### 3.4. Templates & Community APIs (`/api/v1/community`)

| Method | Endpoint | Mô tả |
| :--- | :--- | :--- |
| `POST` | `/api/v1/community/templates` | Xuất bản cấu trúc dự án thành Template |
| `GET` | `/api/v1/community/templates` | Khám phá kho Template mẫu |
| `POST` | `/api/v1/community/initiatives` | Đăng tải Sáng kiến / Dự án cộng đồng |
| `POST` | `/api/v1/community/initiatives/{id}/clone` | Nhân bản sáng kiến/mẫu thành dự án mới |
| `GET` | `/api/v1/community/posts` | Danh sách bài viết blog / chia sẻ kinh nghiệm |

---

## 4. TÍNH NĂNG ĐẶC BIỆT & GIẢI PHÁP KỸ THUẬT

### 4.1. Tự động sinh mã Task Code theo Project Key
* Mỗi task có trường `code` hiển thị dạng `KEY-NUMBER` (Ví dụ: `PTMS-1`, `PTMS-2`).
* Sử dụng PostgreSQL Sequence hoặc Atomic Increment theo `project_id` để đảm bảo không bị trùng mã task ngay cả khi có nhiều request tạo cùng lúc.

### 4.2. Kéo thả Kanban & Optimistic Locking
* Khi người dùng kéo thả thẻ task trên bảng Kanban từ cột này sang cột khác, request gửi lên kèm theo:
  * `status`: Trạng thái mới.
  * `position`: Vị trí mới trong cột.
  * `version`: Version hiện tại trên frontend.
* Nếu trong lúc kéo thả có người khác vừa sửa task, Hibernate sẽ ném ngoại lệ `OptimisticLockException` $\rightarrow$ Service trả về mã `409 CONFLICT` yêu cầu Frontend tải lại dữ liệu mới nhất.

### 4.3. Lưu vết Audit Log tự động (Activity Log)
* Mọi hành động sửa `status`, `assignee`, `due_date`, `priority` đều được tự động intercept qua Spring AOP hoặc Hibernate Envers/EntityListener để ghi 1 bản ghi vào `task_activity_logs` với `old_value` và `new_value` dạng `JSONB`.

### 4.4. Event-Driven Notification & Realtime Collaboration
* Khi có sự kiện quan trọng (Ví dụ: `TaskAssignedEvent`, `CommentCreatedEvent`), service bắn message lên Kafka Topic: `task-events-topic`.
* Đồng thời publish qua WebSocket Topic `/topic/project/{projectId}` để tất cả thành viên đang mở bảng Kanban tự động cập nhật thẻ task tức thời mà không cần F5.

---

## 5. CẤU TRÚC THƯ MỤC SOURCE CODE ĐỀ XUẤT

```
backend/project-task-service/
├── pom.xml
└── src/main/java/com/datn/projecttask/
    ├── ProjectTaskServiceApplication.java
    ├── config/           # WebSocket, Security, Kafka Config
    ├── controller/       # ProjectController, TaskController, CommentController,...
    ├── dto/              # Request/Response DTOs
    ├── entity/           # JPA Entities (Project, Task, Milestone, Comment,...)
    ├── enums/            # TaskStatus, TaskPriority, TaskType,...
    ├── event/            # Kafka Event Producers & Domain Events
    ├── exception/        # Custom Exceptions & GlobalExceptionHandler
    ├── repository/       # Spring Data JPA Repositories
    ├── service/          # Business Logic Interfaces
    └── service/impl/     # Implementation Classes
```
