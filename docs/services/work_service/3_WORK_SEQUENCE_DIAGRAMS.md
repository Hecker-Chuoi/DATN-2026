# 🔄 SƠ ĐỒ TUẦN TỰ CÁC LUỒNG NGHIỆP VỤ (SEQUENCE DIAGRAMS) — WORK SERVICE (V2)
## DỰ ÁN: DATN-2026 — HỆ THỐNG QUẢN LÝ DỰ ÁN & CÔNG VIỆC DOANH NGHIỆP

> **Module:** `work-service` (Port: 8083)  
> **Kiến trúc:** Jira-Grade Work Engine, Event-Driven Architecture, Realtime WebSocket STOMP, Transactional Outbox.  
> **Tài liệu tham chiếu:** [`1_WORK_OVERVIEW_REFACTOR.md`](file:///d:/DATN-2026/docs/services/work_service_new/1_WORK_OVERVIEW_REFACTOR.md), [`2_WORK_DATABASE_DESIGN.md`](file:///d:/DATN-2026/docs/services/work_service_new/2_WORK_DATABASE_DESIGN.md), [`4_WORK_IMPLEMENTATION_PLAN.md`](file:///d:/DATN-2026/docs/services/work_service_new/4_WORK_IMPLEMENTATION_PLAN.md).

---

## 📑 MỤC LỤC
1. [Luồng 1: Khởi tạo Dự án từ Template & Dịch chuyển Timeline (Two-Pass Shifting)](#1-luồng-1-khởi-tạo-dự-án-từ-template--dịch-chuyển-timeline-two-pass-shifting)
2. [Luồng 2: Tạo Work Item (Strict 3-Level Hierarchy, Atomic Code Gen & LexoRank)](#2-luồng-2-tạo-work-item-strict-3-level-hierarchy-atomic-code-gen--lexorank)
3. [Luồng 3: Máy trạng thái FSM Chuyển đổi Trạng thái Issue (Hooks & Post-functions)](#3-luồng-3-máy-trạng-thái-fsm-chuyển-đổi-trạng-thái-issue-hooks--post-functions)
4. [Luồng 4: Kéo thả Card trên Kanban Board (LexoRank O(1) & WIP Limit Guard)](#4-luồng-4-kéo-thả-card-trên-kanban-board-lexorank-o1--wip-limit-guard)
5. [Luồng 5: Vòng đời Quản trị Sprint (Start Sprint, Active & Complete Sprint Snapshot)](#5-luồng-5-vòng-đời-quản-trị-sprint-start-sprint-active--complete-sprint-snapshot)
6. [Luồng 6: Thiết lập Liên kết Phụ thuộc & Phát hiện Chu trình (DFS Cycle Check)](#6-luồng-6-thiết-lập-liên-kết-phụ-thuộc--phát-hiện-chu-trình-dfs-cycle-check)
7. [Luồng 7: Audit Trail Tự động & Đồng bộ Realtime (WebSocket & Outbox Kafka)](#7-luồng-7-audit-trail-tự-động--đồng-bộ-realtime-websocket--outbox-kafka)

---

## 1. Luồng 1: Khởi tạo Dự án từ Template & Dịch chuyển Timeline (Two-Pass Shifting)

Khi người dùng tạo một dự án mới từ một Project mẫu (Template), hệ thống thực hiện nhân bản sâu toàn bộ cấu hình, quy trình và các công việc mẫu, đồng thời tự động tính toán dịch chuyển ngày bắt đầu/kết thúc (timeline shifting) chính xác.

```mermaid
sequenceDiagram
    autonumber
    actor User as 👤 Người dùng (Frontend)
    participant Gateway as 🚪 API Gateway (8081)
    participant ProjCtrl as 🎮 ProjectController
    participant ProjService as ⚙️ ProjectService
    participant CloneEngine as 🧬 DeepCloneTemplateEngine
    participant Repo as 💾 Spring Data Repositories
    participant DB as 🐘 PostgreSQL 16
    participant Outbox as 📤 OutboxService

    User->>Gateway: POST /api/v1/work/projects/from-template<br/>{templateId, name, key, newStartDate, targetEndDate}
    Gateway->>Gateway: Xác thực JWT Token & Inject Header (X-User-Id, Roles)
    Gateway->>ProjCtrl: Forward Request
    ProjCtrl->>ProjService: createProjectFromTemplate(request, userId)

    ProjService->>Repo: Kiểm tra trùng mã Dự án (Key: "DATN")
    Repo->>DB: SELECT count(*) FROM projects WHERE key = 'DATN'
    DB-->>Repo: 0 (Hợp lệ)

    ProjService->>CloneEngine: executeDeepClone(sourceTemplateId, newConfig)
    
    Note over CloneEngine: 1. Tính toán độ lệch thời gian: Δdays = (newStartDate - templateStartDate)
    
    CloneEngine->>DB: 2. INSERT INTO projects (name, key, is_template = FALSE, current_issue_sequence = 0...)
    CloneEngine->>DB: 3. Clone Workflows, Transitions & Workflow Schemes
    CloneEngine->>DB: 4. Clone Boards, Columns & Swimlanes
    CloneEngine->>DB: 5. Clone Components & Versions (ReleaseDate = ReleaseDate + Δdays)
    CloneEngine->>DB: 6. Clone Sprints (StartDate + Δdays, EndDate + Δdays, state = 'FUTURE')
    
    Note over CloneEngine: 7. Clone WorkItems theo 2 lượt (Two-pass):<br/>Pass 1: Copy Epics & Standard Items (StartDate + Δdays, DueDate + Δdays)<br/>Pass 2: Copy Subtasks & Map new parent_id, new sprint_id tương ứng
    
    CloneEngine->>DB: Batch INSERT work_items & mappings
    CloneEngine->>DB: INSERT INTO project_clones (source_id, cloned_id, cloned_by)

    CloneEngine->>Outbox: recordEvent("PROJECT_CREATED", projectPayload)
    Outbox->>DB: INSERT INTO outbox_events

    ProjService-->>ProjCtrl: Trả về ApiResponse(ProjectDetailResponse)
    ProjCtrl-->>Gateway: 201 CREATED
    Gateway-->>User: Hiển thị Dự án mới sẵn sàng làm việc
```

---

## 2. Luồng 2: Tạo Work Item (Strict 3-Level Hierarchy, Atomic Code Gen & LexoRank)

Quản lý chặt chẽ phân cấp công việc (Epic $\rightarrow$ Story/Task/Bug $\rightarrow$ Subtask), tự động sinh mã nguyên tử (ví dụ `DATN-101`) và gán vị trí sắp xếp LexoRank ban đầu.

```mermaid
sequenceDiagram
    autonumber
    actor User as 👤 Người dùng
    participant ItemCtrl as 🎮 WorkItemController
    participant ItemService as ⚙️ WorkItemService
    participant HierarchyVal as 🛡️ StrictHierarchyValidator
    participant SeqGen as 🔢 AtomicKeySequenceGenerator
    participant LexoService as 📊 LexoRankService
    participant Repo as 💾 WorkItemRepository
    participant DB as 🐘 PostgreSQL 16
    participant WS as 📡 WebSocket Broadcaster
    participant Outbox as 📤 OutboxService

    User->>ItemCtrl: POST /api/v1/work/projects/{projectId}/items<br/>{typeId, summary, parentId, sprintId, priority, storyPoints, customFields}
    ItemCtrl->>ItemService: createWorkItem(projectId, request, userId)

    ItemService->>HierarchyVal: validateHierarchy(typeId, parentId)
    Note over HierarchyVal: Kiểm tra tính hợp lệ:<br/>• Level 1 (Epic): parentId BẮT BUỘC = NULL<br/>• Level 0 (Story/Task/Bug): parentId = NULL hoặc = Epic ID (Level 1)<br/>• Level -1 (Sub-task): parentId BẮT BUỘC = Level 0 ID
    HierarchyVal-->>ItemService: Hierarchy Validated ✅

    ItemService->>SeqGen: generateNextCode(projectId, projectKey)
    Note over SeqGen: Thực thi Atomic Sequence trong PostgreSQL:<br/>UPDATE projects SET current_issue_sequence = current_issue_sequence + 1<br/>WHERE id = :projectId RETURNING current_issue_sequence;
    SeqGen-->>ItemService: Sinh mã nguyên tử: "DATN-105"

    ItemService->>LexoService: calculateInitialRank(projectId, sprintId)
    Note over LexoService: Sinh LexoRank ở cuối Backlog / Sprint: "0|i0000z:"
    LexoService-->>ItemService: Rank String

    ItemService->>Repo: save(WorkItem entity)
    Repo->>DB: INSERT INTO work_items (code="DATN-105", summary, rank, custom_fields JSONB, version=0...)
    DB-->>Repo: Saved WorkItem Entity

    ItemService->>WS: broadcastToProject(projectId, "/topic/project/" + projectId + "/items", "CREATED", itemDto)
    ItemService->>Outbox: recordEvent("WORK_ITEM_CREATED", itemPayload)
    Outbox->>DB: INSERT INTO outbox_events

    ItemService-->>ItemCtrl: WorkItemResponse DTO
    ItemCtrl-->>User: 201 CREATED (Mã DATN-105)
```

---

## 3. Luồng 3: Máy trạng thái FSM Chuyển đổi Trạng thái Issue (Hooks & Post-functions)

Khi thực hiện chuyển đổi trạng thái (ví dụ từ *In Progress* sang *Done* hoặc *Code Review*), toàn bộ điều kiện (Conditions), kiểm tra tính hợp lệ (Validators) và hành động tự động (Post-functions) được thực thi nghiêm ngặt.

```mermaid
sequenceDiagram
    autonumber
    actor User as 👤 Người dùng / Developer
    participant Ctrl as 🎮 WorkItemTransitionController
    participant FSM as ⚙️ WorkflowEngine
    participant PermGuard as 🛡️ PermissionGuard
    participant ValEngine as 🔍 TransitionValidatorEngine
    participant Repo as 💾 WorkItemRepository
    participant DB as 🐘 PostgreSQL 16
    participant PostFn as 🚀 PostFunctionRunner
    participant WS as 📡 WebSocket Broadcaster

    User->>Ctrl: POST /api/v1/work/items/{itemId}/transitions/{transitionId}<br/>{resolution, timeSpentSeconds, comment}
    Ctrl->>FSM: executeTransition(itemId, transitionId, payload, userId)

    FSM->>Repo: findById(itemId)
    Repo->>DB: SELECT * FROM work_items WHERE id = :itemId
    DB-->>Repo: WorkItem (status_id = "IN_PROGRESS", version = 2)

    FSM->>PermGuard: checkConditions(transition.conditionsJson, userId, workItem)
    Note over PermGuard: Kiểm tra: User có quyền TRANSITION_ISSUE?<br/>User có phải là Assignee hoặc Project Admin?
    PermGuard-->>FSM: Conditions Passed ✅

    FSM->>ValEngine: checkValidators(transition.validatorsJson, workItem, payload)
    Note over ValEngine: Kiểm tra Validators nghiệp vụ:<br/>1. Tất cả Sub-tasks đã ở trạng thái DONE chưa?<br/>2. Đã nhập thông tin bắt buộc (Resolution / Time Spent) chưa?
    ValEngine-->>FSM: Validators Passed ✅

    Note over FSM: State Mutation: Cập nhật status_id = to_status_id,<br/>resolution_date = NOW(), version = version + 1
    FSM->>Repo: saveAndFlush(workItem)
    Repo->>DB: UPDATE work_items SET status_id = :toStatusId, version = 3 WHERE id = :itemId AND version = 2
    DB-->>Repo: Success (Optimistic Lock OK)

    FSM->>PostFn: executePostFunctions(transition.postFunctionsJson, workItem, payload, userId)
    Note over PostFn: Thực thi các Hook tự động hóa:<br/>1. Ghi nhật ký work_item_changelogs (status: IN_PROGRESS -> DONE)<br/>2. Thêm bình luận nếu có (work_item_comments)<br/>3. Ghi OutboxEvent('WORK_ITEM_TRANSITIONED')
    PostFn->>DB: INSERT changelog, comment, outbox_event

    FSM->>WS: broadcastIssueUpdated(projectId, workItemDto)
    FSM-->>Ctrl: TransitionExecutionResult (Success)
    Ctrl-->>User: 200 OK (Trạng thái mới: DONE)
```

---

## 4. Luồng 4: Kéo thả Card trên Kanban Board (LexoRank O(1) & WIP Limit Guard)

Khi người dùng kéo thả card giữa các cột hoặc thay đổi vị trí trong cùng một cột, hệ thống tính toán LexoRank tức thì mà không cần cập nhật các card xung quanh, đồng thời kiểm tra giới hạn WIP Limits.

```mermaid
sequenceDiagram
    autonumber
    actor User as 👤 Người dùng (Kéo thả thẻ)
    participant BoardCtrl as 🎮 BoardController
    participant BoardService as ⚙️ BoardService
    participant WIPGuard as 🛑 WIPLimitGuard
    participant LexoService as 📊 LexoRankService
    participant Repo as 💾 WorkItemRepository
    participant DB as 🐘 PostgreSQL 16
    participant WS as 📡 WebSocket Broadcaster

    User->>BoardCtrl: PUT /api/v1/work/boards/{boardId}/items/{itemId}/move<br/>{targetColumnId, prevItemRank: "0|i0000a:", nextItemRank: "0|i0000c:", clientVersion: 3}
    BoardCtrl->>BoardService: moveCardOnBoard(boardId, itemId, moveRequest, userId)

    BoardService->>WIPGuard: checkWIPLimit(targetColumnId)
    Note over WIPGuard: SELECT count(*) FROM work_items WHERE column_id = :targetColumnId<br/>Nếu count >= max_wip ➔ Ném lỗi WIP_LIMIT_EXCEEDED (hoặc cảnh báo)
    WIPGuard-->>BoardService: WIP Limit OK ✅

    BoardService->>LexoService: calculateBetween("0|i0000a:", "0|i0000c:")
    Note over LexoService: Thuật toán O(1) sinh chuỗi ký tự trung gian:<br/>"0|i0000b:" (Không cần cập nhật bất kỳ thẻ nào khác!)
    LexoService-->>BoardService: newRank = "0|i0000b:"

    BoardService->>Repo: findById(itemId)
    Repo->>DB: SELECT * FROM work_items WHERE id = :itemId
    DB-->>Repo: WorkItem (version = 3)

    Note over BoardService: Cập nhật status_id (nếu đổi cột), rank = "0|i0000b:", version = 4
    BoardService->>Repo: save(workItem)
    Repo->>DB: UPDATE work_items SET status_id = :newStatusId, rank = '0|i0000b:', version = 4 WHERE id = :itemId AND version = 3
    DB-->>Repo: 1 Row Updated ✅

    BoardService->>WS: broadcastCardMoved(boardId, {itemId, targetColumnId, newRank, version: 4})
    BoardService-->>BoardCtrl: MoveCardResponse (newRank)
    BoardCtrl-->>User: 200 OK (Thẻ đã yên vị mượt mà)
```

---

## 5. Luồng 5: Vòng đời Quản trị Sprint (Start Sprint, Active & Complete Sprint Snapshot)

Quy trình quản lý Sprint Scrum chuẩn: Kích hoạt Sprint, theo dõi tiến độ và khi Đóng Sprint (Complete Sprint), tự động chụp Snapshot số liệu báo cáo và chuyển các Issue chưa hoàn thành (Incomplete Issues) sang Backlog hoặc Sprint tiếp theo.

```mermaid
sequenceDiagram
    autonumber
    actor ScrumMaster as 👤 Scrum Master
    participant SprintCtrl as 🎮 SprintController
    participant SprintService as ⚙️ SprintService
    participant Repo as 💾 SprintRepository
    participant ItemRepo as 💾 WorkItemRepository
    participant SnapEngine as 📸 SprintReportSnapshotEngine
    participant DB as 🐘 PostgreSQL 16
    participant WS as 📡 WebSocket Broadcaster
    participant Outbox as 📤 OutboxService

    ScrumMaster->>SprintCtrl: POST /api/v1/work/sprints/{sprintId}/complete<br/>{targetSprintIdForIncomplete: "NEXT_SPRINT_ID" hoặc "BACKLOG"}
    SprintCtrl->>SprintService: completeSprint(sprintId, request, userId)

    SprintService->>Repo: findById(sprintId)
    Repo->>DB: SELECT * FROM sprints WHERE id = :sprintId
    DB-->>Repo: Sprint (state = 'ACTIVE')

    Note over SprintService: 1. Phân loại Issues trong Sprint:<br/>• Completed Issues (Status category = DONE)<br/>• Incomplete Issues (Status category != DONE)

    SprintService->>SnapEngine: generateFinalSnapshot(sprintId)
    Note over SnapEngine: Tính toán chỉ số báo cáo Agile:<br/>• Total Story Points, Completed Story Points<br/>• Burndown Data Points (Điểm lý thuyết vs Thực tế)<br/>• Velocity Points
    SnapEngine->>DB: INSERT INTO sprint_report_snapshots (sprint_id, total_sp, completed_sp, burndown_data_json)

    Note over SprintService: 2. Xử lý các Incomplete Issues:<br/>Cập nhật sprint_id = targetSprintId (hoặc NULL nếu về Backlog)
    SprintService->>ItemRepo: moveIncompleteIssues(sprintId, targetSprintId)
    ItemRepo->>DB: UPDATE work_items SET sprint_id = :targetSprintId WHERE sprint_id = :sprintId AND status_id NOT IN (DONE_STATUSES)

    Note over SprintService: 3. Đổi trạng thái Sprint: state = 'CLOSED', completed_at = NOW()
    SprintService->>Repo: save(sprint)
    Repo->>DB: UPDATE sprints SET state = 'CLOSED', completed_at = NOW() WHERE id = :sprintId

    SprintService->>Outbox: recordEvent("SPRINT_COMPLETED", snapshotPayload)
    Outbox->>DB: INSERT INTO outbox_events

    SprintService->>WS: broadcastSprintCompleted(projectId, sprintId, targetSprintId)
    SprintService-->>SprintCtrl: SprintCompletionResponse (Chỉ số hoàn thành)
    SprintCtrl-->>ScrumMaster: 200 OK (Sprint đã đóng & Báo cáo đã lưu vết)
```

---

## 6. Luồng 6: Thiết lập Liên kết Phụ thuộc & Phát hiện Chu trình (DFS Cycle Check)

Khi tạo liên kết phụ thuộc dạng `BLOCKS` (Công việc A chặn công việc B), hệ thống chạy thuật toán Depth-First Search (DFS) để phát hiện và ngăn chặn chu trình phụ thuộc vòng (Deadlock: $A \rightarrow B \rightarrow C \rightarrow A$).

```mermaid
sequenceDiagram
    autonumber
    actor User as 👤 Người dùng
    participant LinkCtrl as 🎮 WorkItemLinkController
    participant LinkService as ⚙️ WorkItemLinkService
    participant Detector as 🔄 DependencyCycleDetector
    participant Repo as 💾 WorkItemLinkRepository
    participant DB as 🐘 PostgreSQL 16

    User->>LinkCtrl: POST /api/v1/work/items/{sourceId}/links<br/>{targetId: "ISSUE_B_ID", linkType: "BLOCKS"}
    LinkCtrl->>LinkService: createLink(sourceId, targetId, linkType, userId)

    LinkService->>Repo: findAllBlockingLinksInProject(projectId)
    Repo->>DB: SELECT source_id, target_id FROM work_item_links WHERE link_type = 'BLOCKS'
    DB-->>Repo: Danh sách các cạnh đồ thị hiện tại

    LinkService->>Detector: checkCycle(graphEdges, newEdge: sourceId -> targetId)
    Note over Detector: Xây dựng đồ thị có hướng (Directed Graph)<br/>Chạy thuật toán DFS / Tarjan từ targetId tìm đường về sourceId
    
    alt Phát hiện Chu trình (Loop Detected: A -> B -> C -> A)
        Detector-->>LinkService: Cycle Detected! ❌ (Path: B -> C -> A)
        LinkService-->>LinkCtrl: Ném ngoại lệ DependencyCycleException("Phát hiện phụ thuộc vòng!")
        LinkCtrl-->>User: 400 BAD REQUEST (Chi tiết chu trình gây lỗi)
    else Hợp lệ (Không có vòng lặp)
        Detector-->>LinkService: No Cycle Found ✅
        LinkService->>Repo: save(new WorkItemLink)
        Repo->>DB: INSERT INTO work_item_links (source_id, target_id, link_type)
        DB-->>Repo: Saved Link
        LinkService-->>LinkCtrl: WorkItemLinkResponse
        LinkCtrl-->>User: 201 CREATED (Đã thiết lập liên kết BLOCKS)
    end
```

---

## 7. Luồng 7: Audit Trail Tự động & Đồng bộ Realtime (WebSocket & Outbox Kafka)

Mọi thay đổi trên Work Item được bắt diff tự động qua JPA EntityListener, ghi vào bảng `work_item_changelogs`, phát thông báo tức thì qua WebSocket STOMP tới tất cả client đang mở trang và ghi nhận vào `outbox_events` cho Kafka.

```mermaid
sequenceDiagram
    autonumber
    actor UserA as 👤 Người dùng A (Sửa tiêu đề)
    actor UserB as 👥 Người dùng B (Đang xem Board)
    participant Ctrl as 🎮 WorkItemController
    participant Service as ⚙️ WorkItemService
    participant Listener as 👂 WorkItemEntityListener
    participant DB as 🐘 PostgreSQL 16
    participant WSBroker as 📡 Spring WebSocket STOMP
    participant OutboxWorker as ⏰ OutboxScheduledWorker
    participant Kafka as 📨 Apache Kafka (Topic: work-events)

    UserA->>Ctrl: PATCH /api/v1/work/items/{itemId}<br/>{summary: "Giao diện mới chuẩn Jira"}
    Ctrl->>Service: updateWorkItem(itemId, patchDto, userAId)

    Service->>DB: UPDATE work_items SET summary = 'Giao diện mới...', updated_at = NOW() WHERE id = :itemId
    
    Note over Listener: @PostUpdate trigger kích hoạt:<br/>So sánh old_state vs new_state ➔ Phát hiện field 'summary' bị thay đổi
    Listener->>DB: INSERT INTO work_item_changelogs (work_item_id, author_id, field_name, old_value, new_value)
    Listener->>DB: INSERT INTO outbox_events (event_type: 'WORK_ITEM_UPDATED', payload)

    Service->>WSBroker: convertAndSend("/topic/project/" + projectId, eventPayload)
    WSBroker-->>UserB: Push Realtime Update (Tiêu đề trên thẻ cập nhật tức thì!)

    Service-->>Ctrl: Updated WorkItem DTO
    Ctrl-->>UserA: 200 OK

    Note over OutboxWorker: Background Worker chạy mỗi 500ms (Transactional Outbox):
    OutboxWorker->>DB: SELECT * FROM outbox_events WHERE status = 'PENDING' LIMIT 50 FOR UPDATE SKIP LOCKED
    OutboxWorker->>Kafka: send("work-events", event.payload)
    Kafka-->>OutboxWorker: ACK Received
    OutboxWorker->>DB: UPDATE outbox_events SET status = 'PUBLISHED', published_at = NOW() WHERE id = :eventId
```

---
> **Tiếp theo:** Vui lòng tham khảo kế hoạch triển khai chi tiết từng bước tại [`4_WORK_IMPLEMENTATION_PLAN.md`](file:///d:/DATN-2026/docs/services/work_service_new/4_WORK_IMPLEMENTATION_PLAN.md).
