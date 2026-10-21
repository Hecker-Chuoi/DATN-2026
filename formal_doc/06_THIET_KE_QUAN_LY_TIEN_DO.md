# 06 — Thiết kế tương lai: Quản lý tiến độ dự án

> Trạng thái: **Đề xuất thiết kế**. Chưa có mã nguồn. Tài liệu bám theo mô hình dữ liệu hiện có (tài liệu 01) để tái sử dụng tối đa.

---

## Mục lục

1. [Bài toán](#1-bài-toán)
2. [Hiện trạng có thể tận dụng](#2-hiện-trạng-có-thể-tận-dụng)
3. [Phạm vi và các khái niệm mới](#3-phạm-vi-và-các-khái-niệm-mới)
4. [Mô hình dữ liệu](#4-mô-hình-dữ-liệu)
5. [Cách tính tiến độ](#5-cách-tính-tiến-độ)
6. [Sức khỏe dự án](#6-sức-khỏe-dự-án)
7. [Milestone](#7-milestone)
8. [Baseline và độ lệch kế hoạch](#8-baseline-và-độ-lệch-kế-hoạch)
9. [Timeline / Gantt và đường găng](#9-timeline--gantt-và-đường-găng)
10. [Dự báo ngày hoàn thành](#10-dự-báo-ngày-hoàn-thành)
11. [Báo cáo tình trạng định kỳ](#11-báo-cáo-tình-trạng-định-kỳ)
12. [Kiến trúc xử lý](#12-kiến-trúc-xử-lý)
13. [API đề xuất](#13-api-đề-xuất)
14. [Giao diện đề xuất](#14-giao-diện-đề-xuất)
15. [Phân quyền](#15-phân-quyền)
16. [Lộ trình triển khai](#16-lộ-trình-triển-khai)

---

## 1. Bài toán

Hiện hệ thống trả lời tốt câu hỏi *"Sprint này đang thế nào?"* (Burndown), nhưng **không** trả lời được các câu hỏi cấp quản lý:

```mermaid
flowchart LR
    Q1["Dự án đã xong bao nhiêu %?"]
    Q2["Có kịp hạn chót không?"]
    Q3["Mốc nào đang trễ?"]
    Q4["So với kế hoạch ban đầu<br/>đã lệch bao nhiêu?"]
    Q5["Công việc nào trễ sẽ<br/>kéo theo cả dự án?"]
```

## 2. Hiện trạng có thể tận dụng

| Đã có | Dùng cho |
|-------|----------|
| `projects.start_date / end_date` | Khung thời gian dự án |
| `work_items.start_date / due_date / story_points / estimate` | Lập lịch, trọng số |
| `statuses.category` (TODO / IN_PROGRESS / DONE) | Xác định hoàn thành |
| Cây Epic → Task → Subtask | Cộng dồn tiến độ |
| `work_item_links` loại `BLOCKS` | Phụ thuộc cho Gantt |
| `work_item_changelogs` + `WorkItemStateReplayer` | Dựng lại lịch sử |
| `sprint_report_snapshots`, Velocity | Dự báo |
| `versions.release_date` | Mốc phát hành |

**Điều kiện tiên quyết** (từ tài liệu 05): A6 (changelog theo id), D2 (trạng thái dự án), A5 (outbox publisher), E3 (xóa được giá trị trường).

## 3. Phạm vi và các khái niệm mới

```mermaid
flowchart TD
    P["Project"] --> M["Milestone<br/>mốc quan trọng"]
    P --> BL["Baseline<br/>ảnh chụp kế hoạch"]
    P --> SN["Progress Snapshot<br/>số liệu hằng ngày"]
    P --> SU["Status Update<br/>báo cáo định kỳ"]
    M --> WI["Work Items"]
```

| Khái niệm | Định nghĩa |
|-----------|-----------|
| **Milestone** | Một mốc có ngày đích và tập công việc phải xong trước mốc (vd "Khai trương", "Go-live") |
| **Progress** | % hoàn thành tính theo một phương pháp chọn được |
| **Health** | ON_TRACK / AT_RISK / OFF_TRACK, tính tự động và có thể ghi đè tay |
| **Baseline** | Ảnh chụp ngày bắt đầu/kết thúc của mọi công việc tại thời điểm "chốt kế hoạch" |
| **Progress Snapshot** | Một dòng số liệu mỗi ngày mỗi dự án, để vẽ Burn-up dài hạn |
| **Status Update** | Báo cáo do Lead viết định kỳ (tuần), kèm số liệu tự động |

## 4. Mô hình dữ liệu

### 4.1. Trạng thái dự án (bổ sung cột)

```mermaid
erDiagram
    PROJECTS {
        enum status "ACTIVE, ON_HOLD, COMPLETED, ARCHIVED"
        enum progress_method "ITEM_COUNT, STORY_POINTS, ESTIMATE"
        enum health_override "null hoặc giá trị ghi đè"
        date completed_at
    }
```

### 4.2. Milestone

```mermaid
erDiagram
    PROJECTS ||--o{ PROJECT_MILESTONES : "có"
    PROJECT_MILESTONES ||--o{ MILESTONE_ITEMS : "gồm"
    WORK_ITEMS ||--o{ MILESTONE_ITEMS : "thuộc"
    PROJECT_MILESTONES {
        uuid id PK
        uuid project_id FK
        varchar name
        date target_date
        date achieved_at
        enum status "PLANNED, ACHIEVED, MISSED"
        uuid version_id FK "tùy chọn: gắn với Release"
    }
    MILESTONE_ITEMS {
        uuid milestone_id FK
        uuid work_item_id FK
    }
```

### 4.3. Baseline

```mermaid
erDiagram
    PROJECTS ||--o{ PROJECT_BASELINES : "chốt"
    PROJECT_BASELINES ||--o{ BASELINE_ITEMS : "chứa"
    PROJECT_BASELINES {
        uuid id PK
        varchar name "Kế hoạch ban đầu, Re-plan 1"
        uuid created_by
        timestamp created_at
        bool is_active
    }
    BASELINE_ITEMS {
        uuid baseline_id FK
        uuid work_item_id
        date planned_start
        date planned_due
        decimal planned_points
    }
```

### 4.4. Snapshot hằng ngày và báo cáo tình trạng

```mermaid
erDiagram
    PROJECTS ||--o{ PROJECT_PROGRESS_SNAPSHOTS : "mỗi ngày"
    PROJECTS ||--o{ PROJECT_STATUS_UPDATES : "định kỳ"
    PROJECT_PROGRESS_SNAPSHOTS {
        uuid project_id FK
        date snapshot_date "UNIQUE với project_id"
        int total_items
        int done_items
        decimal total_points
        decimal done_points
        int overdue_items
        decimal progress_pct
        enum health
    }
    PROJECT_STATUS_UPDATES {
        uuid id PK
        uuid project_id FK
        uuid author_id
        enum health
        text summary
        text risks
        text next_steps
        jsonb metrics "chụp số liệu lúc viết"
    }
```

## 5. Cách tính tiến độ

### 5.1. Ba phương pháp

| Phương pháp | Công thức | Hợp với |
|-------------|-----------|---------|
| `ITEM_COUNT` | việc DONE / tổng việc | Dự án BUSINESS, không ước lượng |
| `STORY_POINTS` | điểm DONE / tổng điểm | Scrum |
| `ESTIMATE` | giờ ước lượng của việc DONE / tổng giờ ước lượng | Dự án tính theo giờ |

Chỉ tính công việc cấp 0 và cấp 1 (bỏ Subtask khi tính theo điểm để tránh đếm hai lần — đúng như Burndown hiện tại đang làm).

### 5.2. Cộng dồn theo cây

```mermaid
flowchart BT
    S1["Subtask ✅"] --> T1["Task 1<br/>50%"]
    S2["Subtask ⬜"] --> T1
    T1 --> E["Epic<br/>(50% + 100%) / 2 = 75%"]
    T2["Task 2 ✅ 100%"] --> E
    E --> P["Dự án<br/>trung bình có trọng số các Epic"]
```

Quy tắc:
1. Lá (không có con): DONE = 100%, IN_PROGRESS = 50% (tùy chọn) hoặc 0%, TODO = 0%.
2. Nút có con: trung bình có trọng số (trọng số = story points hoặc estimate; thiếu thì = 1).
3. Dự án: cộng dồn tất cả nút gốc.

### 5.3. Tiến độ kỳ vọng theo thời gian

```
expected_pct(t) = (t − start_date) / (end_date − start_date)
schedule_gap    = progress_pct − expected_pct
```

## 6. Sức khỏe dự án

### 6.1. Luật tự động

```mermaid
flowchart TD
    A["Tính chỉ số"] --> B{"Đã quá end_date<br/>và chưa xong?"}
    B -- "Có" --> OFF["🔴 OFF_TRACK"]
    B -- "Không" --> C{"schedule_gap < −20%<br/>hoặc milestone MISSED?"}
    C -- "Có" --> OFF
    C -- "Không" --> D{"schedule_gap < −10%<br/>hoặc overdue > 10% số việc<br/>hoặc dự báo > end_date?"}
    D -- "Có" --> RISK["🟠 AT_RISK"]
    D -- "Không" --> ON["🟢 ON_TRACK"]
```

Các ngưỡng (10%, 20%) lưu ở cấu hình dự án để Lead điều chỉnh.

### 6.2. Ghi đè thủ công

Lead có thể ghi đè (`health_override`) kèm lý do bắt buộc — ví dụ dự án "trễ" theo số liệu nhưng khách hàng đã đồng ý dời hạn. Giao diện luôn hiển thị **cả hai** giá trị (tự động và ghi đè).

## 7. Milestone

### 7.1. Vòng đời

```mermaid
stateDiagram-v2
    [*] --> PLANNED : Tạo
    PLANNED --> ACHIEVED : Mọi việc DONE trước target_date
    PLANNED --> MISSED : Quá target_date còn việc chưa xong
    MISSED --> ACHIEVED : Hoàn thành muộn (ghi nhận trễ N ngày)
    ACHIEVED --> PLANNED : Có việc bị Reopen
```

### 7.2. Quy tắc

- Công việc thêm vào milestone phải cùng dự án.
- Khi công việc trong milestone chuyển trạng thái → tính lại milestone (lắng nghe sự kiện `WORK_ITEM_TRANSITIONED`).
- Milestone có thể gắn với một `Version` để "Release = Milestone".

## 8. Baseline và độ lệch kế hoạch

```mermaid
sequenceDiagram
    actor L as Lead
    participant S as ProgressService
    L->>S: Chốt kế hoạch (tạo baseline)
    S->>S: Chụp start/due/points của mọi việc
    Note over S: Vài tuần sau
    L->>S: Xem độ lệch
    S->>S: So sánh due hiện tại vs planned_due
    S-->>L: Danh sách việc bị dời, tổng số ngày trễ, việc thêm mới (scope creep)
```

| Chỉ số | Ý nghĩa |
|--------|---------|
| Slip (ngày) | `due_hiện_tại − planned_due` |
| Scope added | Việc tạo sau baseline |
| Scope removed | Việc trong baseline đã bị xóa |
| Points drift | Tổng điểm hiện tại − tổng điểm baseline |

## 9. Timeline / Gantt và đường găng

### 9.1. Dữ liệu đầu vào

```mermaid
flowchart LR
    A["start_date / due_date"] --> G["Gantt"]
    B["Liên kết BLOCKS<br/>(A chặn B ⇒ B bắt đầu sau A)"] --> G
    C["Cây Epic → Task"] --> G
    D["Milestone"] --> G
```

### 9.2. Đường găng (Critical Path)

Đồ thị phụ thuộc `BLOCKS` là DAG (đã được `DependencyCycleDetector` đảm bảo không có vòng) → áp dụng thuật toán đường dài nhất:

1. Sắp xếp topo các công việc.
2. Tính *earliest start / finish* đi xuôi, *latest start / finish* đi ngược từ `end_date`.
3. `slack = latest_start − earliest_start`; công việc có `slack = 0` nằm trên đường găng.

Giao diện tô đỏ các công việc trên đường găng: trễ một ngày ở đây = cả dự án trễ một ngày.

### 9.3. Kéo thả trên Gantt

- Kéo thanh → cập nhật `start_date/due_date` (cần `ISSUE_EDIT`).
- Tùy chọn "dời theo" (auto-schedule): dời các việc bị chặn phía sau.

## 10. Dự báo ngày hoàn thành

```mermaid
flowchart LR
    V["Velocity trung bình<br/>3–5 sprint gần nhất"] --> F["Số sprint còn lại =<br/>điểm còn lại / velocity"]
    R["Điểm còn lại"] --> F
    F --> D["Ngày dự kiến =<br/>hôm nay + số sprint × độ dài sprint"]
```

- Dự án Kanban / BUSINESS: dùng **throughput** (số việc xong mỗi tuần) thay velocity.
- Hiển thị khoảng tin cậy: dùng velocity thấp nhất / cao nhất để ra ngày "bi quan / lạc quan".

## 11. Báo cáo tình trạng định kỳ

```mermaid
sequenceDiagram
    participant J as Scheduler (thứ Sáu)
    participant S as ProgressService
    participant N as Notification
    actor L as Lead
    J->>S: Nhắc viết status update
    S->>N: Gửi nhắc Lead
    L->>S: Viết tóm tắt, rủi ro, bước tiếp
    S->>S: Đính kèm số liệu tự động (metrics jsonb)
    S->>N: Gửi cho thành viên dự án + người theo dõi
```

## 12. Kiến trúc xử lý

```mermaid
flowchart LR
    WK["work-service"] -- "outbox:<br/>ITEM_CREATED, ITEM_TRANSITIONED,<br/>ITEM_UPDATED" --> K[["Kafka"]]
    K --> PG["Progress module"]
    CR["Cron 00:05 hằng ngày"] --> PG
    PG --> DB[("progress_snapshots<br/>milestones")]
    PG -- "HEALTH_CHANGED,<br/>MILESTONE_MISSED" --> K
    K --> NT["notification"]
```

| Quyết định | Lý do |
|-----------|-------|
| Bắt đầu như **module** trong work-service, chưa tách service | Cần truy cập trực tiếp cây công việc; giảm độ phức tạp |
| Tính tiến độ **khi có sự kiện** + snapshot **hằng ngày** | Số liệu hiện tại luôn mới; lịch sử rẻ để vẽ |
| Cache `progress_pct` của Epic | Tránh duyệt cả cây mỗi lần mở trang |

## 13. API đề xuất

| Phương thức | Endpoint | Quyền |
|-------------|----------|-------|
| GET | `/projects/{id}/progress` | PROJECT_VIEW |
| GET | `/projects/{id}/progress/history?from=&to=` | PROJECT_VIEW |
| PUT | `/projects/{id}/status` (ACTIVE/ON_HOLD/COMPLETED/ARCHIVED) | PROJECT_ADMIN |
| PUT | `/projects/{id}/health-override` | PROJECT_ADMIN |
| GET/POST | `/projects/{id}/milestones` | VIEW / PROJECT_MANAGE_SCHEDULE |
| PUT/DELETE | `/milestones/{id}`, `/milestones/{id}/items` | PROJECT_MANAGE_SCHEDULE |
| POST | `/projects/{id}/baselines` | PROJECT_MANAGE_SCHEDULE |
| GET | `/projects/{id}/baselines/{bid}/variance` | PROJECT_VIEW |
| GET | `/projects/{id}/timeline` (Gantt + đường găng) | PROJECT_VIEW |
| GET | `/projects/{id}/forecast` | PROJECT_VIEW |
| GET/POST | `/projects/{id}/status-updates` | VIEW / PROJECT_ADMIN |
| GET | `/workspaces/{id}/portfolio` (mọi dự án + health) | WS MEMBER+ |

Quyền mới: `PROJECT_MANAGE_SCHEDULE` (bổ sung vào `PermissionKey`, mặc định cho ADMINISTRATOR).

## 14. Giao diện đề xuất

### 14.1. Bảng điều khiển tiến độ dự án

```
┌─────────────────────────────────────────────────────────────┐
│ WEB · Website bán hàng          🟠 AT_RISK   ON TRACK* (Lead)│
├──────────────┬──────────────┬──────────────┬───────────────┤
│ Hoàn thành   │ Kỳ vọng      │ Quá hạn      │ Dự báo xong   │
│   62%        │   70%        │  4 việc      │ 18/12 (+5 ng) │
├──────────────┴──────────────┴──────────────┴───────────────┤
│ Burn-up (tổng phạm vi vs đã xong theo ngày)                 │
├─────────────────────────────────────────────────────────────┤
│ Milestone: ✅ Thiết kế  ⏳ Beta (12/11)  ⬜ Go-live (01/12)  │
├─────────────────────────────────────────────────────────────┤
│ Epic progress: ██████░░ Catalog 75% │ ███░░░░ Checkout 40%  │
└─────────────────────────────────────────────────────────────┘
```

### 14.2. Portfolio cấp workspace

Bảng mọi dự án: tên · Lead · % · health · hạn chót · milestone kế tiếp — sắp theo health để người quản lý thấy dự án có vấn đề trước.

## 15. Phân quyền

| Hành động | ADMINISTRATOR | MEMBER | VIEWER | WS OWNER/ADMIN |
|-----------|:-:|:-:|:-:|:-:|
| Xem tiến độ, timeline, dự báo | ✅ | ✅ | ✅ | ✅ (portfolio) |
| Quản lý milestone, baseline | ✅ | tùy cấu hình | | |
| Đổi trạng thái dự án, ghi đè health | ✅ | | | |
| Viết status update | ✅ | | | |

## 16. Lộ trình triển khai

```mermaid
flowchart LR
    S1["Bước 1<br/>Trạng thái dự án<br/>+ % tiến độ + Epic rollup"] --> S2["Bước 2<br/>Snapshot hằng ngày<br/>+ Burn-up + Health"]
    S2 --> S3["Bước 3<br/>Milestone<br/>+ Portfolio"]
    S3 --> S4["Bước 4<br/>Timeline/Gantt<br/>+ Đường găng"]
    S4 --> S5["Bước 5<br/>Baseline, Dự báo,<br/>Status update"]
```

| Bước | Giá trị mang lại | Độ khó |
|------|------------------|--------|
| 1 | Thấy ngay % hoàn thành dự án và từng Epic | Thấp |
| 2 | Biết dự án đang tốt hay xấu theo thời gian | Trung bình |
| 3 | Quản lý mốc giao hàng, nhìn toàn bộ tổ chức | Trung bình |
| 4 | Lập lịch trực quan, biết việc nào quyết định hạn chót | Cao |
| 5 | Quản trị dự án chuyên nghiệp (kiểm soát thay đổi phạm vi) | Trung bình |
