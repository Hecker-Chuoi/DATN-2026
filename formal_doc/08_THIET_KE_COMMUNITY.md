# 08 — Thiết kế tương lai: Community

> Trạng thái: **Đề xuất thiết kế**. Hiện chưa có mã nguồn (`community-service` mới chỉ được nhắc tên trong kế hoạch dự án).
> Cảm hứng: **Figma Community** (publish file → người khác Duplicate/Remix), **GitHub** (public repo, star, fork), **Notion Template Gallery**.

---

## Mục lục

1. [Tầm nhìn](#1-tầm-nhìn)
2. [Khái niệm cốt lõi](#2-khái-niệm-cốt-lõi)
3. [Nguyên tắc thiết kế quan trọng nhất: Publish là ảnh chụp](#3-nguyên-tắc-thiết-kế-quan-trọng-nhất-publish-là-ảnh-chụp)
4. [Kiến trúc tổng thể](#4-kiến-trúc-tổng-thể)
5. [Mô hình dữ liệu](#5-mô-hình-dữ-liệu)
6. [Luồng Publish](#6-luồng-publish)
7. [Làm sạch dữ liệu trước khi công khai](#7-làm-sạch-dữ-liệu-trước-khi-công-khai)
8. [Luồng Remix (Clone về workspace của tôi)](#8-luồng-remix-clone-về-workspace-của-tôi)
9. [Phiên bản và cập nhật](#9-phiên-bản-và-cập-nhật)
10. [Bài đăng (Post)](#10-bài-đăng-post)
11. [Tag và khám phá](#11-tag-và-khám-phá)
12. [Tương tác xã hội](#12-tương-tác-xã-hội)
13. [Trang hồ sơ người sáng tạo](#13-trang-hồ-sơ-người-sáng-tạo)
14. [Kiểm duyệt](#14-kiểm-duyệt)
15. [Phân quyền](#15-phân-quyền)
16. [API đề xuất](#16-api-đề-xuất)
17. [Giao diện đề xuất](#17-giao-diện-đề-xuất)
18. [Rủi ro và cách giảm thiểu](#18-rủi-ro-và-cách-giảm-thiểu)
19. [Lộ trình triển khai](#19-lộ-trình-triển-khai)

---

## 1. Tầm nhìn

Biến mỗi dự án tốt thành **tài sản dùng lại được cho cả cộng đồng**: một người lập kế hoạch mở quán café rất kỹ, publish lên Community; hàng trăm người khác bấm **Remix** để có ngay một dự án đầy đủ workflow, board, sprint, công việc mẫu trong workspace của mình.

```mermaid
flowchart LR
    A["Người tạo<br/>làm dự án tốt"] -- "Publish" --> C(("Community"))
    C -- "Khám phá / tìm theo tag" --> B["Người dùng khác"]
    B -- "Remix" --> D["Dự án mới<br/>trong workspace của B"]
    B -- "Like · Bình luận · Theo dõi" --> A
```

Giá trị cho đồ án: tận dụng gần như nguyên vẹn **engine clone + blueprint** đã có (tài liệu 04), chỉ mở rộng phạm vi từ "trong workspace" ra "toàn hệ thống".

## 2. Khái niệm cốt lõi

| Khái niệm | Mô tả | Tương đương Figma |
|-----------|-------|-------------------|
| **Visibility** | Mức hiển thị của dự án: `PRIVATE`, `WORKSPACE`, `PUBLIC`, `UNLISTED` | Share settings |
| **Listing** | Trang công khai của một dự án đã publish | Community file page |
| **Listing Version** | Một ảnh chụp bất biến của blueprint tại thời điểm publish | File version |
| **Remix** | Tạo dự án mới trong workspace của tôi từ một listing | Duplicate |
| **Post** | Bài viết (chia sẻ kinh nghiệm, hỏi đáp, giới thiệu) có thể nhúng listing | Community post |
| **Tag** | Nhãn chủ đề toàn hệ thống (`#fnb`, `#scrum`, `#ecommerce`) | Tags |
| **Creator** | Hồ sơ công khai của người publish | Creator profile |

```mermaid
flowchart TD
    P["Project (riêng tư)"] -- "publish" --> L["Listing"]
    L --> V1["Version 1"]
    L --> V2["Version 2"]
    V2 -- "remix" --> R["Project mới"]
    PO["Post"] -. "nhúng" .-> L
    L --> T["Tags"]
```

## 3. Nguyên tắc thiết kế quan trọng nhất: Publish là ảnh chụp

**Không** cho người khác đọc trực tiếp dự án đang sống. Publish = chụp blueprint thành **JSON bất biến**, đã làm sạch, lưu ở community-service.

```mermaid
flowchart LR
    subgraph Sai["❌ Cách sai"]
        P1["Project đang sống"] -- "đọc trực tiếp" --> X["Người ngoài"]
    end
    subgraph Dung["✅ Cách đúng"]
        P2["Project đang sống"] -- "chụp + làm sạch" --> J["Blueprint JSON v1<br/>(bất biến)"]
        J -- "đọc / remix" --> Y["Người ngoài"]
    end
```

| Lý do | |
|-------|---|
| Bảo mật | Không thể vô tình lộ tên khách hàng, email, bình luận nội bộ thêm vào **sau** khi publish |
| Ổn định | Người remix luôn nhận đúng nội dung họ đã xem trước |
| Hiệu năng | Đọc một JSON thay vì join ~20 bảng |
| Tách service | community-service không cần truy cập DB của work-service |
| Phiên bản | Mỗi lần cập nhật là một version mới, có changelog |

Hệ quả kỹ thuật: cần bổ sung **tuần tự hóa / giải tuần tự hóa** `ProjectBlueprint ↔ JSON` và cho `ProjectCloneEngine` nhận blueprint từ JSON (hiện chỉ nhận từ DB). Đây cũng là đề xuất G1 trong tài liệu 05.

## 4. Kiến trúc tổng thể

```mermaid
flowchart LR
    FE["Frontend<br/>/community"] --> GW["Gateway"]
    GW --> CS["community-service<br/>(mới)"]
    GW --> WK["work-service"]
    CS -- "internal: export blueprint" --> WK
    CS -- "internal: import blueprint" --> WK
    CS --> CDB[("community DB<br/>listings, posts, tags...")]
    CS --> MN[("MinIO<br/>ảnh bìa, blueprint.json")]
    CS --> SE[("Tìm kiếm<br/>PostgreSQL FTS → OpenSearch")]
```

| Thành phần | Trách nhiệm |
|-----------|-------------|
| community-service | Listing, version, post, tag, like, follow, bình luận, kiểm duyệt, tìm kiếm |
| work-service (bổ sung) | `GET /internal/projects/{id}/blueprint` (xuất), `POST /internal/blueprints/instantiate` (nhập) |
| workspace-service | Kiểm tra người remix là MEMBER+ của workspace đích |
| identity-service | Thông tin hồ sơ công khai (tên, ảnh) |

## 5. Mô hình dữ liệu

### 5.1. Listing và phiên bản

```mermaid
erDiagram
    COMMUNITY_LISTINGS ||--o{ LISTING_VERSIONS : "có"
    COMMUNITY_LISTINGS {
        uuid id PK
        uuid source_project_id "project gốc ở work-service"
        uuid source_workspace_id
        uuid creator_id
        varchar slug UK
        varchar title
        text description
        varchar cover_image_url
        enum visibility "PUBLIC, UNLISTED"
        enum status "DRAFT, PUBLISHED, HIDDEN, REMOVED"
        enum license "FREE_REMIX, ATTRIBUTION, NO_REMIX"
        uuid current_version_id
        int remix_count
        int like_count
        int view_count
    }
    LISTING_VERSIONS {
        uuid id PK
        uuid listing_id FK
        int version_no
        text release_note
        varchar blueprint_url "MinIO"
        jsonb summary "số trạng thái, việc, sprint..."
        timestamp published_at
    }
```

### 5.2. Tag

```mermaid
erDiagram
    TAGS ||--o{ LISTING_TAGS : ""
    COMMUNITY_LISTINGS ||--o{ LISTING_TAGS : ""
    TAGS ||--o{ POST_TAGS : ""
    COMMUNITY_POSTS ||--o{ POST_TAGS : ""
    TAGS {
        uuid id PK
        varchar slug UK "fnb, scrum, ecommerce"
        varchar name
        enum kind "INDUSTRY, METHOD, TOPIC"
        int usage_count
    }
```

### 5.3. Bài đăng và bình luận

```mermaid
erDiagram
    COMMUNITY_POSTS ||--o{ POST_COMMENTS : "có"
    POST_COMMENTS |o--o{ POST_COMMENTS : "trả lời"
    COMMUNITY_POSTS |o--o{ POST_LISTING_EMBEDS : "nhúng"
    COMMUNITY_POSTS {
        uuid id PK
        uuid author_id
        varchar title
        text body_markdown
        enum type "SHOWCASE, GUIDE, QUESTION, DISCUSSION"
        enum status "PUBLISHED, HIDDEN, REMOVED"
        int like_count
        int comment_count
    }
    POST_LISTING_EMBEDS {
        uuid post_id FK
        uuid listing_id FK
    }
```

### 5.4. Tương tác và nguồn gốc remix

```mermaid
erDiagram
    REACTIONS {
        uuid user_id
        enum target_type "LISTING, POST, COMMENT"
        uuid target_id
        enum kind "LIKE, BOOKMARK"
    }
    FOLLOWS {
        uuid follower_id
        enum target_type "USER, TAG"
        uuid target_id
    }
    REMIX_RECORDS {
        uuid id PK
        uuid listing_id
        uuid listing_version_id
        uuid remixed_by
        uuid target_workspace_id
        uuid target_project_id
        timestamp remixed_at
    }
    CONTENT_REPORTS {
        uuid id PK
        uuid reporter_id
        enum target_type
        uuid target_id
        enum reason "SPAM, SENSITIVE_DATA, OFFENSIVE, COPYRIGHT"
        enum status "OPEN, ACTIONED, DISMISSED"
    }
```

## 6. Luồng Publish

### 6.1. Trình tự

```mermaid
sequenceDiagram
    actor A as Quản trị dự án
    participant FE as Frontend
    participant CS as community-service
    participant WK as work-service
    participant MN as MinIO
    A->>FE: "Publish to Community"
    FE->>CS: POST /listings/draft {projectId}
    CS->>WK: GET /internal/projects/{id}/blueprint?sanitize=true
    WK->>WK: Kiểm tra PROJECT_ADMIN + TemplateIntegrityValidator
    WK-->>CS: Blueprint JSON đã làm sạch + báo cáo làm sạch
    CS-->>FE: Bản xem trước + danh sách dữ liệu bị loại
    A->>FE: Nhập tiêu đề, mô tả, ảnh bìa, tag, license, xác nhận
    FE->>CS: POST /listings/{id}/publish
    CS->>MN: Lưu blueprint_v1.json
    CS->>CS: status = PUBLISHED, index tìm kiếm
```

### 6.2. Điều kiện publish

```mermaid
flowchart TD
    A["Yêu cầu publish"] --> B{"Có PROJECT_ADMIN?"}
    B -- "Không" --> X["403"]
    B -- "Có" --> C{"Workspace cho phép<br/>publish ra ngoài?"}
    C -- "Không" --> X2["403: chính sách workspace"]
    C -- "Có" --> D{"Blueprint hợp lệ?<br/>(validator tài liệu 04)"}
    D -- "Không" --> X3["400 + danh sách lỗi"]
    D -- "Có" --> E{"Email đã xác thực<br/>và tài khoản > N ngày?"}
    E -- "Không" --> X4["403: chống spam"]
    E -- "Có" --> OK["Cho phép"]
```

Chính sách workspace mới: `workspaces.allow_public_publish` (mặc định `false`, chỉ OWNER bật được).

## 7. Làm sạch dữ liệu trước khi công khai

| Dữ liệu | Hành động |
|---------|-----------|
| Thành viên, role_assignments | Loại bỏ |
| `assignee_id`, `reporter_id`, `lead_user_id`, `component.lead_user_id` | Loại bỏ |
| Bình luận, tệp đính kèm, changelog, worklog | Loại bỏ |
| Email / số điện thoại / URL trong `summary`, `description` | Phát hiện bằng regex → **cảnh báo**, người publish tự sửa hoặc xác nhận |
| `custom_fields` kiểu `USER` | Loại bỏ giá trị |
| Ngày tuyệt đối | Chuyển thành **ngày tương đối** (ngày thứ N kể từ mốc) |
| Mã công việc `WEB-12` | Thay bằng chỉ số `#12` |
| Trạng thái tiến độ (DONE, sprint CLOSED) | Đặt lại như khi clone (tài liệu 04 – mục 6.5) |
| Tùy chọn của người publish | Có kèm công việc? kèm sprint? (giống `includeWorkItems`, `includeSprints`) |

```mermaid
flowchart LR
    BP["Blueprint gốc"] --> S1["Bỏ người & hoạt động"]
    S1 --> S2["Quét thông tin nhạy cảm"]
    S2 --> S3["Ngày → tương đối"]
    S3 --> S4["Đặt lại tiến độ"]
    S4 --> OUT["Blueprint công khai"]
```

## 8. Luồng Remix (Clone về workspace của tôi)

```mermaid
sequenceDiagram
    actor U as Người dùng
    participant CS as community-service
    participant WS as workspace-service
    participant WK as work-service
    U->>CS: POST /listings/{id}/remix {workspaceId, teamId, name, key, startDate}
    CS->>CS: license ≠ NO_REMIX?
    CS->>WS: U là MEMBER+ của workspace?
    CS->>CS: Tải blueprint của version hiện tại
    CS->>WK: POST /internal/blueprints/instantiate (blueprint, target, actor=U)
    WK->>WK: Quy tắc tạo dự án (trưởng nhóm) + CloneEngine + validator
    WK-->>CS: projectId mới
    CS->>CS: Ghi remix_records, remix_count++
    CS-->>U: Mở dự án mới
```

- Dự án mới có thông tin nguồn gốc: *"Remixed from **Mở quán café 90 ngày** v3 by @an"* (lưu `source_listing_id`, `source_version_id` trong `projects` hoặc `project_clones` với `clone_type = FROM_COMMUNITY`).
- Ngày tương đối được neo vào `startDate` người dùng chọn — đúng cơ chế dời ngày hiện có.

## 9. Phiên bản và cập nhật

```mermaid
stateDiagram-v2
    [*] --> DRAFT : Tạo bản nháp
    DRAFT --> PUBLISHED : Publish v1
    PUBLISHED --> PUBLISHED : Publish v2, v3...
    PUBLISHED --> HIDDEN : Người tạo ẩn
    HIDDEN --> PUBLISHED : Hiện lại
    PUBLISHED --> REMOVED : Kiểm duyệt gỡ
    HIDDEN --> REMOVED
```

| Quy tắc | |
|---------|---|
| Mỗi lần "Cập nhật listing" tạo version mới; version cũ vẫn giữ | Người đã remix không bị ảnh hưởng |
| Người theo dõi listing được thông báo khi có version mới | |
| Dự án đã remix **không** tự cập nhật theo nguồn (khác GitHub fork) | Tránh ghi đè dữ liệu thật của người dùng |
| Xóa project gốc không làm mất listing | Listing chỉ phụ thuộc blueprint đã lưu |

## 10. Bài đăng (Post)

| Loại | Mục đích | Ví dụ |
|------|----------|-------|
| `SHOWCASE` | Giới thiệu một listing | "Quy trình 12 tuần mở quán café của mình" |
| `GUIDE` | Hướng dẫn, kinh nghiệm | "Cách chia Epic cho dự án thương mại điện tử" |
| `QUESTION` | Hỏi đáp | "Nên dùng Scrum hay Kanban cho team 3 người?" |
| `DISCUSSION` | Thảo luận chung | |

- Nội dung Markdown, nhúng được listing dạng thẻ (ảnh bìa + số liệu + nút Remix).
- `QUESTION` có thể đánh dấu một bình luận là "câu trả lời được chấp nhận".

## 11. Tag và khám phá

### 11.1. Ba loại tag

| Loại | Ví dụ |
|------|-------|
| INDUSTRY (ngành) | `#fnb`, `#ecommerce`, `#agriculture`, `#education` |
| METHOD (phương pháp) | `#scrum`, `#kanban`, `#waterfall` |
| TOPIC (chủ đề) | `#mvp`, `#marketing-campaign`, `#event` |

Tag do quản trị hệ thống quản lý danh mục; người dùng chọn tối đa 5 tag / listing. `TemplateCategory` hiện có được ánh xạ sẵn sang tag INDUSTRY.

### 11.2. Các cách khám phá

```mermaid
flowchart TD
    H["Trang Community"] --> T1["Thịnh hành"]
    H --> T2["Mới nhất"]
    H --> T3["Theo tag"]
    H --> T4["Tìm kiếm toàn văn"]
    H --> T5["Từ người tôi theo dõi"]
    H --> T6["Gợi ý theo workspace của tôi"]
```

### 11.3. Điểm thịnh hành

```
trending = (remix × 5 + like × 2 + comment × 1 + view × 0.05) / (giờ_kể_từ_publish + 2)^1.5
```

Tính lại mỗi giờ bằng job; lưu vào cột `trending_score` có chỉ mục.

## 12. Tương tác xã hội

| Tương tác | Đối tượng | Ghi chú |
|-----------|-----------|---------|
| Like | Listing, Post, Comment | Một người một lần |
| Bookmark | Listing, Post | Danh sách "Đã lưu" riêng tư |
| Bình luận | Listing, Post | Trả lời lồng 1 cấp, @mention |
| Theo dõi | User, Tag | Bảng tin cá nhân hóa |
| Remix | Listing | Đếm công khai |

Mọi tương tác phát sự kiện → notification-service gửi thông báo trong ứng dụng / email tổng hợp hằng tuần.

## 13. Trang hồ sơ người sáng tạo

```
┌─────────────────────────────────────────────┐
│ 🧑 An Nguyễn  @an        [Theo dõi]          │
│ 12 listing · 1.2k remix · 340 người theo dõi │
├─────────────────────────────────────────────┤
│ Listing   Bài đăng   Đã thích                │
│ ┌────────┐ ┌────────┐ ┌────────┐             │
│ │ Café   │ │ Shop   │ │ Event  │             │
│ │ 320 ⟳  │ │ 210 ⟳  │ │ 95 ⟳   │             │
│ └────────┘ └────────┘ └────────┘             │
└─────────────────────────────────────────────┘
```

Cần thêm ở identity-service: `display_name`, `avatar_url`, `bio`, `public_handle` (duy nhất), cờ `is_public_profile`.

## 14. Kiểm duyệt

```mermaid
flowchart TD
    R["Người dùng báo cáo"] --> Q["Hàng đợi kiểm duyệt"]
    A["Tự động: quét nhạy cảm,<br/>từ khóa cấm, tần suất đăng"] --> Q
    Q --> M{"Kiểm duyệt viên"}
    M -- "Vi phạm" --> H["Ẩn / Gỡ + thông báo người tạo"]
    M -- "Không vi phạm" --> D["Bỏ qua"]
    H --> AP["Người tạo kháng nghị"]
```

| Biện pháp chống spam | |
|---------------------|---|
| Giới hạn tần suất | ≤ 3 listing / ngày, ≤ 10 bài / ngày / người |
| Tài khoản mới | Phải xác thực email và đủ 7 ngày tuổi |
| Ẩn tự động | ≥ 5 báo cáo khác nhau → tạm ẩn chờ duyệt |
| Vai trò mới | `COMMUNITY_MODERATOR` (realm role Keycloak) |

## 15. Phân quyền

| Hành động | Khách (chưa đăng nhập) | Người dùng | Quản trị dự án nguồn | Người tạo listing | Moderator |
|-----------|:-:|:-:|:-:|:-:|:-:|
| Xem listing / post công khai | ✅ | ✅ | ✅ | ✅ | ✅ |
| Like, bình luận, theo dõi, báo cáo | | ✅ | ✅ | ✅ | ✅ |
| Remix | | ✅ (MEMBER+ workspace đích) | ✅ | ✅ | ✅ |
| Publish listing | | | ✅ (+ chính sách WS) | | |
| Cập nhật version, ẩn listing | | | | ✅ | |
| Gỡ nội dung | | | | | ✅ |

## 16. API đề xuất

Tiền tố `/community/api/v1`.

| Nhóm | Endpoint |
|------|----------|
| Listing | `POST /listings/draft`, `POST /listings/{id}/publish`, `GET /listings?tag=&q=&sort=trending\|new`, `GET /listings/{slug}`, `PUT /listings/{id}`, `POST /listings/{id}/versions`, `POST /listings/{id}/hide` |
| Xem trước | `GET /listings/{id}/versions/{v}/preview` |
| Remix | `POST /listings/{id}/remix` |
| Post | `POST/GET /posts`, `GET/PUT/DELETE /posts/{id}` |
| Bình luận | `GET/POST /{listings\|posts}/{id}/comments` |
| Tương tác | `POST/DELETE /reactions`, `POST/DELETE /follows` |
| Tag | `GET /tags`, `GET /tags/{slug}` |
| Hồ sơ | `GET /creators/{handle}`, `GET /me/feed`, `GET /me/bookmarks` |
| Kiểm duyệt | `POST /reports`, `GET /moderation/queue`, `POST /moderation/{id}/action` |

Bổ sung ở work-service (nội bộ, có xác thực service-to-service — tài liệu 05 – B2):
- `GET /internal/projects/{id}/blueprint?sanitize=true`
- `POST /internal/blueprints/instantiate`

## 17. Giao diện đề xuất

### 17.1. Trang listing

```
┌───────────────────────────────────────────────────────────────┐
│ [Ảnh bìa]                                                     │
│ Mở quán café trong 90 ngày                     [ ⟳ Remix ]    │
│ by @an · v3 · cập nhật 2 ngày trước   ♥ 210  ⟳ 320  💬 18      │
│ #fnb  #scrum  #mvp                                            │
├───────────────────────────────────────────────────────────────┤
│ Tổng quan │ Workflow │ Board │ Công việc │ Sprint │ Phiên bản  │
│                                                               │
│  (tái sử dụng TemplatePreviewModal hiện có)                   │
├───────────────────────────────────────────────────────────────┤
│ Bình luận                                                     │
└───────────────────────────────────────────────────────────────┘
```

### 17.2. Lối vào trong ứng dụng

- Trong `CreateProjectModal`: tab mới **"Từ Community"** bên cạnh "Mẫu của workspace".
- Trong trang dự án: nút **"Publish to Community"** (cạnh "Lưu thành mẫu").
- Sidebar: mục **Community**.

## 18. Rủi ro và cách giảm thiểu

| Rủi ro | Mức | Giảm thiểu |
|--------|-----|-----------|
| Lộ dữ liệu nội bộ công ty | 🔴 | Ảnh chụp đã làm sạch; chính sách workspace mặc định tắt; màn hình xem trước bắt buộc |
| Spam, nội dung xấu | 🟠 | Giới hạn tần suất, báo cáo, moderator |
| Blueprint cũ không tương thích khi schema đổi | 🟠 | Lưu `schema_version` trong JSON; bộ chuyển đổi theo phiên bản |
| Remix tạo hàng loạt dự án rác | 🟡 | Giới hạn remix / giờ; dự án remix vẫn chịu quy tắc "trưởng nhóm mới tạo được" |
| Tranh chấp bản quyền | 🟡 | Trường `license`, ghi nguồn bắt buộc, quy trình báo cáo COPYRIGHT |

## 19. Lộ trình triển khai

```mermaid
flowchart LR
    S0["Tiền đề<br/>Blueprint ↔ JSON<br/>Auth nội bộ (B2)"] --> S1["Bước 1<br/>Publish + Listing<br/>+ Remix"]
    S1 --> S2["Bước 2<br/>Tag + Tìm kiếm<br/>+ Like/Bookmark"]
    S2 --> S3["Bước 3<br/>Post + Bình luận<br/>+ Hồ sơ + Follow"]
    S3 --> S4["Bước 4<br/>Version, Trending,<br/>Kiểm duyệt"]
```

| Bước | Kết quả demo được | Độ khó |
|------|-------------------|--------|
| 1 | Publish một dự án, người khác remix về workspace của họ | Trung bình (tái sử dụng clone engine) |
| 2 | Duyệt Community theo tag, tìm kiếm | Thấp |
| 3 | Mạng xã hội thu nhỏ quanh dự án | Trung bình |
| 4 | Vận hành bền vững ở quy mô lớn | Trung bình |

> Gợi ý cho phạm vi đồ án: **Bước 1 + 2** đã đủ tạo điểm nhấn khác biệt so với các hệ thống quản lý tác vụ thông thường.
