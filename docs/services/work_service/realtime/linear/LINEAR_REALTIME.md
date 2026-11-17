# Linear triển khai Realtime như thế nào: Sync Engine (local-first)

> Thuộc bộ nghiên cứu `docs/services/work_service/realtime/`. Slide đi kèm: [slide_linear_realtime.html](slide_linear_realtime.html).
>
> **Độ tin cậy nguồn:** Linear **không công bố tài liệu nội bộ** về sync engine. Nguồn chi tiết nhất là bản **dịch ngược (reverse-engineering)** của wzhudev trên GitHub, làm từ mã frontend Linear giai đoạn 2024–đầu 2025. CTO Linear, Tuomas Artman, đã xác nhận công khai bản này là "a pretty awesome (and correct) write-up of our sync engine". Phía **server** (CSDL, cách sinh sync id, hạ tầng pub/sub) **không được công bố**. Chỗ nào là suy luận sẽ ghi rõ.

---

## 1. Kết luận nhanh

| Câu hỏi | Trả lời |
|---|---|
| Linear có realtime không? | **Có, toàn diện nhất trong các app được khảo sát.** Mọi đối tượng (Issue, Comment, Cycle, Project, WorkflowState...) đều được đồng bộ, không riêng board. |
| Cách tiếp cận? | **Local-first sync engine**: trình duyệt giữ **bản sao CSDL** trong IndexedDB và bộ nhớ (MobX). UI đọc/ghi vào bản sao cục bộ → **tức thì**. Thay đổi được gửi lên server dưới dạng **Transaction**; server phát **Delta packet** qua **WebSocket** cho mọi client liên quan. |
| Thứ tự và mất gói? | Mỗi thay đổi có **`syncId` tăng đơn điệu** toàn hệ thống. Client nhớ `lastSyncId`; thấy bị **hụt** thì tải các delta còn thiếu. |
| Xung đột? | **Last-Writer-Wins** (theo tóm tắt của bản dịch ngược), áp cho `UpdateTransaction`. Server là bên quyết định thứ tự. Không dùng CRDT cho dữ liệu dạng bản ghi. |
| Quyền? | **Sync group** (theo user, team, role). Client không nhận được delta của team mà mình không có quyền. |
| Co-edit mô tả? | **Có** (changelog 06/12/2023): nhiều người sửa issue description đồng thời, có con trỏ của người đang sửa hoặc xem. |
| Có dùng Kafka/Redis không? | Không công bố. Bản dịch ngược chỉ thấy phía client (WebSocket, REST `/sync/*`, GraphQL mutation). |

---

## 2. Bức tranh tổng thể

```
┌─────────────────────────── Trình duyệt ───────────────────────────┐
│  React UI (observer)  ◀── MobX observable ──  Object Pool (bộ nhớ)│
│        │ issue.assignee = u ; issue.save()          ▲             │
│        ▼                                            │ applyDelta  │
│  TransactionQueue ──batch──▶ GraphQL mutation       │             │
│        │ (lưu tạm IndexedDB __transactions)         │             │
│        ▼                                            │             │
│  IndexedDB: linear_<hash> (mỗi model 1 object store, _meta.lastSyncId)
└────────│──────────────────────────────────────────▲───────────────┘
         │ HTTPS: /sync/bootstrap, /sync/batch,     │ WebSocket: Delta packets
         ▼        GraphQL mutations                 │ (mỗi action có syncId)
┌──────────────────────────────── Server Linear ────┴───────────────┐
│  Ghi CSDL → cấp syncId tăng dần → phát delta theo sync group       │
└────────────────────────────────────────────────────────────────────┘
```

---

## 3. Năm khối chính

### 3.1 Model & ModelRegistry

- Mỗi thực thể (Issue, Team, Comment...) là một **Model** kế thừa lớp `Model`, khoá bằng UUID.
- Decorator ghi metadata vào `ModelRegistry`: `@ClientModel("Issue")`, `@Property()`, `@Reference(...)`, `@OneToMany()`.
- **Chiến lược tải** (`loadStrategy`): `instant` (mặc định, tải lúc khởi động), `lazy`, `partial`, `explicitlyRequested`, `local`.
- **Quan sát được (observable):** getter/setter được bọc bằng MobX. Gán `issue.title = "x"` thì component `observer` tự render lại, đồng thời setter ghi lại giá trị cũ/mới để sinh Transaction.
- `__schemaHash` dùng để quyết định khi nào cần migrate IndexedDB.

### 3.2 Bootstrapping (khởi động)

| Kiểu | Khi nào | Endpoint |
|---|---|---|
| `full` | Chưa có dữ liệu, `lastSyncId` rỗng, hoặc schema đổi | `GET /sync/bootstrap?type=full&onlyModels=...`, trả về JSON-lines (mỗi dòng một model) và dòng cuối `_metadata_` gồm `lastSyncId`, `subscribedSyncGroups`, `returnedModelsCount` |
| `partial` | Tải dần các model `partial` (ví dụ Issue của một team) | `/sync/bootstrap?type=partial&firstSyncId=...&syncGroups=...` |
| `local` | Đã có dữ liệu cục bộ đủ mới | Đọc từ IndexedDB, rồi bắt kịp bằng delta |

Thứ tự: mở IndexedDB và migrate → bootstrap → ghi vào IndexedDB → nạp các model `instant` vào bộ nhớ → **mở WebSocket**.

**Lazy loading:** model không phải `instant` chỉ tải khi được truy cập (ví dụ mở issue thì mới tải comment). Các yêu cầu tải lẻ được gom qua `BatchModelLoader` thành `POST /sync/batch`.

> Bản dịch ngược ghi nhận Linear về sau đã tách bootstrap thành nhiều request cache được. Theo tóm tắt của bên thứ ba, bài nói "Scaling the Linear Sync Engine" (06/2023) giải thích việc chuyển sang tải bất đồng bộ khi "giữ mọi model trong bộ nhớ" không còn khả thi.

### 3.3 Transaction (đường ghi)

- Gán thuộc tính thì **model trong bộ nhớ đổi ngay** (optimistic). `save()` sinh Transaction.
- Loại: `CreationTransaction`, `UpdatingTransaction`, `DeletionTransaction`, `ArchivalTransaction`, `UnarchiveTransaction`, `LocalTransaction`.
- `TransactionQueue`: transaction tạo trong cùng một microtask được gom chung `batchIndex`, rồi `TransactionExecutor` gộp thành **một GraphQL mutation** có alias (`o1`, `o2`...).
- Mỗi transaction được **lưu tạm vào IndexedDB** khi xếp hàng và xoá khi server chấp nhận. Mất mạng hoặc đóng tab thì mở lại sẽ **gửi lại** (hỗ trợ offline). Tác giả cảnh báo: "some transactions are not idempotent".
- Server trả về `lastSyncId` của thay đổi; transaction **chờ tới khi nhận delta có syncId đó** mới coi là hoàn tất (`completedButUnsyncedTransactions`).
- Lỗi thì `rollback()` hoàn tác thay đổi cục bộ.
- **Undo/Redo:** mỗi transaction có `undoTransaction()`, undo chỉ là một transaction mới đi qua đúng luồng trên.

### 3.4 Delta packet (đường đọc realtime)

- Server phát delta qua WebSocket **cho mọi client liên quan, kể cả client vừa gây ra thay đổi** (để xác nhận).
- Mỗi action trong delta có `syncId` riêng; loại action: `I` (insert), `U` (update), `A` (archive), `D` (delete), `C`, `G`, `S`, `V`...
- **Phát hiện hụt:** "If the client's `lastSyncId` is smaller than the server's, it indicates that the client is out of sync", khi đó client tải các packet bị thiếu.
- Áp delta tuần tự dưới khoá `updateLock.runExclusive` (không bao giờ áp hai delta song song).
- Delta **có thể khác** transaction gốc vì server thêm hiệu ứng phụ (ví dụ sinh lịch sử). Client lấy theo delta, tức server là nguồn sự thật.
- Transaction `Update` đang chờ được **rebase** lên delta mới tới.

### 3.5 Quyền: Sync group

- `subscribedSyncGroups` (về sau là `userSyncGroups` và `teamSyncGroup`) gồm UUID của user, team, role.
- "you cannot access issues or receive delta packets from workspaces or teams to which you lack proper permissions."
- `lastSyncId` là bộ đếm **chung toàn hệ thống**, nên client thấy syncId "nhảy cóc" là bình thường (các thay đổi đó thuộc nhóm khác).

---

## 4. Phần nào trong một "Team/Project" của Linear là realtime

| Màn hình / chức năng | Linear | Cơ chế |
|---|---|---|
| Danh sách issue / Board / Backlog | ✅ | Delta `U` vào Object Pool, MobX render lại đúng thẻ bị đổi (không refetch) |
| Kéo thẻ, sắp xếp | ✅ | Optimistic + Transaction + delta xác nhận |
| Chi tiết issue (mọi trường) | ✅ | Cùng cơ chế |
| Comment | ✅ | Model `Comment`, lazy-load khi mở issue, sau đó nhận delta |
| Mô tả issue (co-edit) | ✅ (từ 12/2023) | Con trỏ người đang sửa/xem; "changes are saved and synced to everyone in realtime". Thuật toán cụ thể không công bố. |
| Cycle (≈ Sprint), Project, WorkflowState | ✅ | Đều là Model |
| Offline | ✅ | IndexedDB + hàng đợi transaction gửi lại |
| Undo/Redo | ✅ | Transaction đảo |

---

## 5. Cái giá của cách làm Linear

| Ưu | Nhược / chi phí |
|---|---|
| UI tức thì (đọc/ghi cục bộ), cảm giác "native" | Phải viết lại **toàn bộ tầng dữ liệu frontend** (ORM phía client, IndexedDB, migrate schema) |
| Không cần refetch, băng thông thấp sau bootstrap | Bootstrap lớn; phải đầu tư partial/lazy loading khi dữ liệu phình |
| Offline, undo/redo "miễn phí" | Logic quyền bị nhân đôi (server lọc theo sync group, client phải hiểu) |
| Thứ tự toàn cục rõ ràng (syncId) | Server phải cấp syncId tăng đơn điệu cho **mọi** thay đổi, dễ thành điểm nghẽn |
| | Mọi rule nghiệp vụ (workflow guard, WIP, cascade...) phải xử lý được kiểu "optimistic rồi rollback" |

> **Đánh giá cho DATN** (ý kiến người viết): work-service có nhiều rule phía server (workflow transition guard, WIP limit, cascade sprint/subtask, hierarchy validator). Đi theo Linear đồng nghĩa với chép các rule đó xuống client hoặc chấp nhận rollback thường xuyên. **Không phù hợp** với phạm vi đồ án. Nhưng có **4 ý tưởng nên mượn** (mục 6).

---

## 6. Ý tưởng nên mượn cho DATN

1. **Số thứ tự tăng đơn điệu + phát hiện hụt:** không cần toàn cục như Linear, chỉ cần **theo project** (`seq` per project). Client thấy `seq` nhảy quá 1 thì refetch.
2. **Server phát lại cả cho client gây ra thay đổi** (kèm `clientMutationId`) để client xác nhận hoặc bỏ qua. Đây là cách xác định "sự kiện của chính mình" chính xác hơn so `userId` (Jira tự nhận là chưa xử lý được trường hợp hai tab).
3. **Sync group ≈ topic theo project**, kiểm quyền khi subscribe.
4. **LWW theo từng trường + version:** `UpdateWorkItemRequest` của DATN đã là cập nhật một phần (trường `null` thì bỏ qua). Thêm `clientVersion` để phát hiện ghi đè, còn trường khác nhau thì để LWW.

---

## Nguồn

- wzhudev – *reverse-linear-sync-engine* (README): https://github.com/wzhudev/reverse-linear-sync-engine
- Linear – *Scaling the Linear Sync Engine* (Tuomas Artman, 29/06/2023): https://linear.app/now/scaling-the-linear-sync-engine
- Linear changelog – *Editor improvements / Inline comments* (06/12/2023): https://linear.app/changelog/2023-12-06-editor-improvements
- Thảo luận Hacker News về bản dịch ngược: https://hn.jakechampion.name/item/44123131
