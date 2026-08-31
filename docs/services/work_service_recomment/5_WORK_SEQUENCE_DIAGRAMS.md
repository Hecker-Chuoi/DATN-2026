# 🔄 TÀI LIỆU SƠ ĐỒ TUẦN TỰ (SEQUENCE DIAGRAMS) CÁC LUỒNG CHÍNH — WORK SERVICE
## DỰ ÁN: DATN-2026 — HỆ THỐNG QUẢN LÝ DỰ ÁN & CÔNG VIỆC DOANH NGHIỆP

> **Module:** `work-service`  
> **Kiến trúc:** Jira-Grade Work Engine, Event-Driven Architecture (Kafka), Realtime WebSocket STOMP, Distributed Locks (Redis).  
> **Tài liệu tham chiếu:** `2_WORK_MAIN_DOC.md`, `3_WORK_IMPLEMENTATION_PLAN.md`, `4_WORK_DATABASE_DESIGN.md`.

---

## 📑 MỤC LỤC
1. [Luồng 1: Khởi tạo Dự án từ Template Mẫu & Dịch chuyển Timeline tự động](#1-luồng-1-khởi-tạo-dự-án-từ-template-mẫu--dịch-chuyển-timeline-tự-động)
2. [Luồng 2: Tạo mới Work Item (Atomic Key Sequence Generator & LexoRank)](#2-luồng-2-tạo-mới-work-item-atomic-key-sequence-generator--lexorank)
3. [Luồng 3: Máy trạng thái Chuyển đổi Trạng thái Issue (Workflow FSM Engine)](#3-luồng-3-máy-trạng-thái-chuyển-đổi-trạng-thái-issue-workflow-fsm-engine)
4. [Luồng 4: Kéo thả Card trên Board & Cập nhật LexoRank (WIP Limits & Optimistic Locking)](#4-luồng-4-kéo-thả-card-trên-board--cập-nhật-lexorank-wip-limits--optimistic-locking)
5. [Luồng 5: Chu kỳ Quản lý Sprint Scrum (Start Sprint, Active & Complete Sprint Snapshot)](#5-luồng-5-chu-kỳ-quản-lý-sprint-scrum-start-sprint-active--complete-sprint-snapshot)
6. [Luồng 6: Thiết lập Liên kết Phụ thuộc & Phát hiện Chu trình (Cycle Detection DFS)](#6-luồng-6-thiết-lập-liên-kết-phụ-thuộc--phát-hiện-chu-trình-cycle-detection-dfs)
7. [Luồng 7: Thực thi Bộ lọc Động & JQL Search Engine (JPA Specification & GIN Index)](#7-luồng-7-thực-thi-bộ-lọc-động--jql-search-engine-jpa-specification--gin-index)
8. [Luồng 8: Động cơ Tự động hóa Nội bộ (Automation Engine: Trigger -> Action)](#8-luồng-8-động-cơ-tự-động-hóa-nội-bộ-automation-engine-trigger---action)

---

## 1. Luồng 1: Khởi tạo Dự án từ Template Mẫu & Dịch chuyển Timeline tự động

```mermaid
sequenceDiagram
    autonumber
    actor User as 👤 Người dùng (Client / Frontend)
    participant Gateway as 🚪 API Gateway (8080)
    participant ProjCtrl as 🎮 ProjectController (8083)
    participant ProjService as ⚙️ ProjectService
    participant CloneEngine as 🧬 DeepCloneTemplateEngine
    participant Repo as 💾 Spring Data Repositories
    participant DB as 🐘 PostgreSQL DB
    participant Outbox as 📤 OutboxService

    User->>Gateway: POST /api/v1/work/projects/from-template<br/>{templateId, name, key, newStartDate}
    Gateway->>Gateway: Validate JWT Token & Extract User Context
    Gateway->>ProjCtrl: Forward Request (User Header injected)
    ProjCtrl->>ProjService: createProjectFromTemplate(cmd)
    
    ProjService->>Repo: Check Key Duplicate (e.g. "SCRUM")
    Repo->>DB: SELECT count(*) FROM projects WHERE key = 'SCRUM'
    DB-->>Repo: 0 (Available)
    
    ProjService->>CloneEngine: cloneFullProject(sourceTemplateId, newProjConfig)
    
    Note over CloneEngine: Tính độ lệch ngày: Δdays = (newStartDate - templateStartDate)
    
    CloneEngine->>DB: 1. Clone Project Record (is_template = FALSE)
    CloneEngine->>DB: 2. Clone Workflows, Transitions & Workflow Schemes
    CloneEngine->>DB: 3. Clone Boards, Columns & Swimlanes
    CloneEngine->>DB: 4. Clone Components & Versions (ReleaseDate + Δdays)
    CloneEngine->>DB: 5. Clone Sprints (StartDate + Δdays, EndDate + Δdays, state = FUTURE)
    CloneEngine->>DB: 6. Clone WorkItems (3-level hierarchy: Epic -> Story/Task/Bug -> Subtask)<br/>(Cập nhật new parent_id, new sprint_id, startDate + Δdays, dueDate + Δdays)
    
    CloneEngine->>DB: 7. Ghi nhật ký project_clones
    CloneEngine->>Outbox: Record OutboxEvent('PROJECT_CREATED_FROM_TEMPLATE')
    Outbox->>DB: INSERT INTO outbox_events
    
    ProjService-->>ProjCtrl: Trả về ProjectResponse chi tiết
    ProjCtrl-->>Gateway: 201 CREATED (Project DTO)
    Gateway-->>User: Hiển thị Dự án mới sẵn sàng làm việc
```

---

## 2. Luồng 2: Tạo mới Work Item (Atomic Key Sequence Generator & LexoRank)

```mermaid
sequenceDiagram
    autonumber
    actor User as 👤 Người dùng (Client)
    participant ItemCtrl as 🎮 WorkItemController
    participant ItemService as ⚙️ WorkItemService
    participant SeqGen as 🔢 AtomicKeySequenceGenerator
    participant LexoService as 📊 LexoRankService
    participant HierarchyVal as 🛡️ StrictHierarchyValidator
    participant Repo as 💾 WorkItemRepository
    participant DB as 🐘 PostgreSQL DB
    participant WS as 📡 WebSocket Broadcaster
    participant Outbox as 📤 OutboxService

    User->>ItemCtrl: POST /api/v1/work/projects/{projectId}/items<br/>{typeId, summary, parentId, priority, storyPoints, customFields}
    ItemCtrl->>ItemService: createWorkItem(projectId, request, userId)
    
    ItemService->>HierarchyVal: validateParentChildHierarchy(typeId, parentId)
    Note over HierarchyVal: Kiểm tra: Epic(1) chỉ nhận Parent=null<br/>Story(0) chỉ nhận Parent=Epic(1)<br/>Subtask(-1) chỉ nhận Parent=Story/Task/Bug(0)
    HierarchyVal-->>ItemService: Hierarchy Valid ✅
    
    ItemService->>SeqGen: generateNextCode(projectId, projectKey)
    Note over SeqGen: Thực thi Atomic Sequence trong PostgreSQL<br/>hoặc Redis INCR: projectKey + "-" + nextVal
    SeqGen-->>ItemService: Sinh mã tuần tự: "SCRUM-105"
    
    ItemService->>LexoService: calculateInitialRank(projectId, sprintId)
    Note over LexoService: Tính LexoRank ở cuối Backlog/Sprint: "0|i0000z:"
    LexoService-->>ItemService: Generated Rank
    
    ItemService->>Repo: save(WorkItem entity)
    Repo->>DB: INSERT INTO work_items (code, summary, rank, custom_fields JSONB...)
    DB-->>Repo: Saved WorkItem (id, code="SCRUM-105", version=0)
    
    ItemService->>DB: Ghi log work_item_changelogs ('CREATED')
    ItemService->>WS: Broadcast Topic: /topic/projects/{projectId}/items ('CREATED')
    ItemService->>Outbox: Record OutboxEvent('WORK_ITEM_CREATED')
    
    ItemService-->>ItemCtrl: Trả về WorkItemDetailResponse
    ItemCtrl-->>User: 201 CREATED (WorkItem DTO)
```

---

## 3. Luồng 3: Máy trạng thái Chuyển đổi Trạng thái Issue (Workflow FSM Engine)

```mermaid
sequenceDiagram
    autonumber
    actor User as 👤 Developer / QA
    participant TransitionCtrl as 🎮 WorkflowTransitionController
    participant FsmService as ⚙️ WorkflowFsmService
    participant GuardValidator as 🛡️ Guard & Validator Engine
    participant PostFuncEngine as ⚡ PostFunctionEngine
    participant ItemRepo as 💾 WorkItemRepository
    participant DB as 🐘 PostgreSQL DB
    participant WS as 📡 WebSocket Broadcaster
    participant Outbox as 📤 OutboxService

    User->>TransitionCtrl: POST /api/v1/work/items/{itemId}/transitions/{transitionId}<br/>{resolution, comment}
    TransitionCtrl->>FsmService: executeTransition(itemId, transitionId, request, userId)
    
    FsmService->>ItemRepo: findByIdForUpdate(itemId) (Pessimistic / Optimistic Lock)
    ItemRepo->>DB: SELECT * FROM work_items WHERE id = ? FOR UPDATE
    DB-->>ItemRepo: WorkItem Record (status = "IN_PROGRESS")
    
    FsmService->>DB: Lấy WorkflowTransition theo transitionId
    DB-->>FsmService: Transition ("Submit for QA Review", toStatus = "IN_REVIEW")
    
    FsmService->>GuardValidator: validateConditionsAndGuards(transition, user, workItem)
    Note over GuardValidator: 1. Kiểm tra Permission (User có quyền TRANSITION_ISSUE)<br/>2. Kiểm tra Validators (Nếu chuyển DONE -> Bắt buộc mọi Subtasks phải DONE)
    GuardValidator-->>FsmService: Validation Passed ✅
    
    FsmService->>PostFuncEngine: executePrePostFunctions(transition, workItem)
    Note over PostFuncEngine: Cập nhật status_id = "IN_REVIEW"<br/>Nếu toStatus.category == DONE -> set resolved_at = NOW()<br/>Set Resolution nếu có truyền lên
    
    FsmService->>ItemRepo: save(updatedWorkItem)
    ItemRepo->>DB: UPDATE work_items SET status_id = ?, resolved_at = ?, version = version + 1
    
    FsmService->>DB: INSERT INTO work_item_changelogs (field="status", old="IN_PROGRESS", new="IN_REVIEW")
    opt Có comment đính kèm
        FsmService->>DB: INSERT INTO work_item_comments (body="Ready for test")
    end
    
    FsmService->>WS: Broadcast Topic: /topic/projects/{projectId}/board<br/>{action: "ITEM_TRANSITIONED", itemId, newStatus: "IN_REVIEW"}
    FsmService->>Outbox: Record OutboxEvent('WORK_ITEM_TRANSITIONED')
    
    FsmService-->>TransitionCtrl: Trả về WorkItemResponse mới
    TransitionCtrl-->>User: 200 OK (Trạng thái đã cập nhật mượt mà)
```

---

## 4. Luồng 4: Kéo thả Card trên Board & Cập nhật LexoRank (WIP Limits & Optimistic Locking)

```mermaid
sequenceDiagram
    autonumber
    actor User as 👤 Người dùng kéo thả Card trên Kanban Board
    participant BoardCtrl as 🎮 BoardController
    participant BoardService as ⚙️ BoardService
    participant WipValidator as 🛡️ BoardWipValidator
    participant LexoService as 📊 LexoRankService
    participant ItemRepo as 💾 WorkItemRepository
    participant DB as 🐘 PostgreSQL DB
    participant WS as 📡 WebSocket Broadcaster

    User->>BoardCtrl: PUT /api/v1/work/boards/{boardId}/items/{itemId}/move<br/>{targetColumnId, prevItemRank, nextItemRank, clientVersion}
    BoardCtrl->>BoardService: moveCardOnBoard(boardId, itemId, request, userId)
    
    BoardService->>WipValidator: validateMaxWipLimit(boardId, targetColumnId)
    Note over WipValidator: Đếm số lượng Cards hiện có tại targetColumnId:<br/>Nếu currentCount + 1 > maxWipLimit -> Ném cảnh báo/Chặn (theo Board Policy)
    WipValidator-->>BoardService: WIP Limit OK ✅
    
    BoardService->>ItemRepo: findById(itemId)
    ItemRepo->>DB: SELECT * FROM work_items WHERE id = ?
    DB-->>ItemRepo: WorkItem (version: 5)
    
    alt clientVersion != workItem.getVersion()
        BoardService-->>BoardCtrl: Ném OptimisticLockingFailureException (409 Conflict)
        BoardCtrl-->>User: 409 CONFLICT: Thẻ đã bị người khác cập nhật trước đó! (Re-sync UI)
    else Version trùng khớp
        BoardService->>LexoService: calculateBetween(prevItemRank, nextItemRank)
        Note over LexoService: Thuật toán Midpoint string: "0|i0000a:" và "0|i0000c:" -> "0|i0000b:"
        LexoService-->>BoardService: New LexoRank ("0|i0000b:")
        
        BoardService->>ItemRepo: updateRankAndStatus(itemId, newRank, targetStatusId, version+1)
        ItemRepo->>DB: UPDATE work_items SET rank = ?, status_id = ?, version = version + 1
        
        BoardService->>WS: Broadcast Topic: /topic/boards/{boardId}/cards<br/>{itemId, newRank, targetColumnId, newVersion: 6}
        BoardService-->>BoardCtrl: 200 OK (Move Successful)
        BoardCtrl-->>User: Thẻ đứng đúng vị trí mới không cần reload toàn trang
    end
```

---

## 5. Luồng 5: Chu kỳ Quản lý Sprint Scrum (Start Sprint, Active & Complete Sprint Snapshot)

```mermaid
sequenceDiagram
    autonumber
    actor ScrumMaster as 👤 Scrum Master / Tech Lead
    participant SprintCtrl as 🎮 SprintController
    participant SprintService as ⚙️ SprintService
    participant SnapshotGen as 📸 SprintSnapshotGenerator
    participant SprintRepo as 💾 SprintRepository
    participant ItemRepo as 💾 WorkItemRepository
    participant DB as 🐘 PostgreSQL DB
    participant Outbox as 📤 OutboxService

    Note over ScrumMaster, DB: GIAI ĐOẠN 1: BẮT ĐẦU SPRINT (START SPRINT)
    ScrumMaster->>SprintCtrl: POST /api/v1/work/sprints/{sprintId}/start<br/>{startDate, endDate, goal}
    SprintCtrl->>SprintService: startSprint(sprintId, request)
    SprintService->>SprintRepo: Check active sprints in project
    Note over SprintService: Kiểm tra: 1 Board Scrum tại một thời điểm chỉ có 1 ACTIVE Sprint
    SprintService->>SprintRepo: updateSprintState(sprintId, 'ACTIVE', startDate, endDate)
    SprintRepo->>DB: UPDATE sprints SET state = 'ACTIVE' WHERE id = ?
    SprintCtrl-->>ScrumMaster: 200 OK (Sprint đã kích hoạt)

    Note over ScrumMaster, DB: GIAI ĐOẠN 2: HOÀN THÀNH SPRINT (COMPLETE SPRINT & SNAPSHOT)
    ScrumMaster->>SprintCtrl: POST /api/v1/work/sprints/{sprintId}/complete<br/>{moveToNextSprintId (hoặc moveToBacklog)}
    SprintCtrl->>SprintService: completeSprint(sprintId, request, userId)
    
    SprintService->>ItemRepo: findBySprintId(sprintId)
    ItemRepo->>DB: SELECT * FROM work_items WHERE sprint_id = ?
    DB-->>ItemRepo: Danh sách Work Items trong Sprint
    
    SprintService->>SnapshotGen: generateFinalSnapshot(sprint, items, userId)
    Note over SnapshotGen: Tính toán số liệu chốt sổ:<br/>- totalStoryPoints & completedStoryPoints<br/>- incompleteItems & completedItems<br/>- Lưu mảng JSONB chi tiết để vẽ Burndown/Velocity vĩnh viễn
    SnapshotGen-->>SprintService: Snapshot Entity
    
    SprintService->>DB: INSERT INTO sprint_report_snapshots (total_sp, completed_sp, snapshot_data JSONB...)
    SprintService->>DB: UPDATE sprints SET state = 'CLOSED', completed_date = NOW()
    
    Note over SprintService: Xử lý công việc dở dang (Incomplete Spillover):
    opt Chuyển sang Next Sprint hoặc Backlog
        SprintService->>ItemRepo: updateIncompleteItems(incompleteItemIds, targetSprintId / null)
        ItemRepo->>DB: UPDATE work_items SET sprint_id = targetSprintId WHERE id IN (...)
    end
    
    SprintService->>Outbox: Record OutboxEvent('SPRINT_COMPLETED')
    SprintService-->>SprintCtrl: Trả về SprintCompleteReportResponse
    SprintCtrl-->>ScrumMaster: 200 OK (Báo cáo tổng kết Sprint & Burndown Chart hoàn tất)
```

---

## 6. Luồng 6: Thiết lập Liên kết Phụ thuộc & Phát hiện Chu trình (Cycle Detection DFS)

```mermaid
sequenceDiagram
    autonumber
    actor User as 👤 Người dùng thiết lập Link Issue
    participant LinkCtrl as 🎮 WorkItemLinkController
    participant LinkService as ⚙️ WorkItemLinkService
    participant CycleDetector as 🔍 DependencyCycleDetector (DFS)
    participant LinkRepo as 💾 WorkItemLinkRepository
    participant DB as 🐘 PostgreSQL DB

    User->>LinkCtrl: POST /api/v1/work/items/{sourceId}/links<br/>{targetItemId: targetId, linkType: "BLOCKS"}
    LinkCtrl->>LinkService: createLink(sourceId, targetId, "BLOCKS", userId)
    
    alt sourceId == targetId
        LinkService-->>LinkCtrl: Ném BadRequestException("Không thể tự liên kết phụ thuộc chính nó!")
        LinkCtrl-->>User: 400 BAD REQUEST
    end
    
    LinkService->>CycleDetector: detectCycle(sourceId, targetId, "BLOCKS")
    Note over CycleDetector: 1. Tải đồ thị có hướng (Directed Graph) các link "BLOCKS" trong Project<br/>2. Chạy thuật toán Depth-First Search (DFS) kiểm tra xem có đường đi ngược từ targetId -> sourceId hay không
    
    alt Phát hiện chu trình (A blocks B -> B blocks C -> C blocks A)
        CycleDetector-->>LinkService: Cycle Detected: [SCRUM-101 -> SCRUM-102 -> SCRUM-103 -> SCRUM-101] ❌
        LinkService-->>LinkCtrl: Ném CyclicDependencyException("Phát hiện chu trình phụ thuộc vòng tròn!")
        LinkCtrl-->>User: 400 BAD REQUEST (Kèm danh sách chuỗi gây deadlock)
    else Hợp lệ không có chu trình
        CycleDetector-->>LinkService: No Cycle Found ✅
        LinkService->>LinkRepo: save(new WorkItemLink(sourceId, targetId, "BLOCKS"))
        LinkRepo->>DB: INSERT INTO work_item_links
        LinkService-->>LinkCtrl: 201 CREATED (Link thành công)
        LinkCtrl-->>User: 201 CREATED
    end
```

---

## 7. Luồng 7: Thực thi Bộ lọc Động & JQL Search Engine (JPA Specification & GIN Index)

```mermaid
sequenceDiagram
    autonumber
    actor User as 👤 Người dùng tìm kiếm trên Search Bar / Filter Panel
    participant SearchCtrl as 🎮 WorkItemSearchController
    participant SearchService as ⚙️ WorkItemSearchService
    participant JqlParser as 🧩 JqlParserEngine
    participant SpecBuilder as 🏗️ WorkItemSpecificationBuilder
    participant ItemRepo as 💾 WorkItemRepository
    participant DB as 🐘 PostgreSQL DB

    User->>SearchCtrl: GET /api/v1/work/projects/{projectId}/items/search<br/>?q=auth&type=BUG&priority=HIGH,HIGHEST&custom_severity=CRITICAL&page=0&size=20&sort=rank,asc
    SearchCtrl->>SearchService: searchWorkItems(projectId, filterParams, pageable)
    
    SearchService->>JqlParser: parseFilterCriteria(filterParams)
    Note over JqlParser: Phân tách tiêu chí chuẩn (keyword, type, priority)<br/>và tiêu chí trường động (custom_fields->>'severity' = 'CRITICAL')
    JqlParser-->>SearchService: Filter Criteria Object
    
    SearchService->>SpecBuilder: buildSpecification(criteria)
    Note over SpecBuilder: Dựng Spring Data JPA Specification:<br/>- Text Search: unaccent(lower(summary)) LIKE %keyword%<br/>- In Criteria: priority IN ('HIGH', 'HIGHEST')<br/>- JSONB GIN Expression: function('jsonb_extract_path_text', custom_fields, 'severity') = 'CRITICAL'
    SpecBuilder-->>SearchService: Specification<WorkItem>
    
    SearchService->>ItemRepo: findAll(spec, pageable)
    ItemRepo->>DB: SELECT * FROM work_items WHERE project_id = ? AND ... <br/>ORDER BY rank ASC LIMIT 20 OFFSET 0
    Note over DB: Tận dụng B-Tree Index (project, rank) & GIN Index (custom_fields)
    DB-->>ItemRepo: Page<WorkItem> (20 items, totalElements = 45)
    
    SearchService-->>SearchCtrl: Trả về PageResponse<WorkItemResponse>
    SearchCtrl-->>User: 200 OK (Hiển thị danh sách kết quả siêu tốc < 50ms)
```

---

## 8. Luồng 8: Động cơ Tự động hóa Nội bộ (Automation Engine: Trigger -> Action)

```mermaid
sequenceDiagram
    autonumber
    participant EventBus as ⚡ Internal Application Event Bus
    participant AutoListener as 👂 AutomationEventListener
    participant RuleEngine as ⚙️ AutomationRuleEngine
    participant ConditionEval as 🔍 ConditionEvaluator
    participant ActionExec as 🚀 ActionExecutor
    participant DB as 🐘 PostgreSQL DB
    participant WS as 📡 WebSocket Broadcaster

    Note over EventBus: Sự kiện phát sinh: Work Item vừa chuyển sang DONE
    EventBus->>AutoListener: onApplicationEvent(WorkItemTransitionedEvent(itemId, newStatus="DONE"))
    
    AutoListener->>RuleEngine: processRulesForTrigger("ISSUE_TRANSITIONED", event)
    RuleEngine->>DB: SELECT * FROM automation_rules WHERE project_id = ? AND is_active = TRUE
    DB-->>RuleEngine: Danh sách Automation Rules đang kích hoạt
    
    loop Duyệt từng Rule phù hợp
        RuleEngine->>ConditionEval: evaluate(rule.conditions_config, event)
        Note over ConditionEval: Ví dụ Rule: "Khi Parent chuyển DONE -> Tự động đóng tất cả Subtasks chưa hoàn thành"
        
        alt Điều kiện thỏa mãn ✅
            ConditionEval-->>RuleEngine: Conditions Match (True)
            RuleEngine->>ActionExec: executeAction(rule.actions_config, event)
            
            Note over ActionExec: Thực thi hành động:<br/>1. Tìm mọi Subtasks của itemId có category != DONE<br/>2. Chuyển status của Subtasks sang DONE<br/>3. Ghi changelog "Auto-closed by Automation Rule"
            ActionExec->>DB: UPDATE work_items SET status_id = 'DONE_STATUS_ID' WHERE parent_id = ?
            ActionExec->>WS: Broadcast /topic/projects/{projectId}/items ('SUBTASKS_AUTO_CLOSED')
        else Điều kiện không thỏa mãn ❌
            ConditionEval-->>RuleEngine: Skip Rule
        end
    end
```
