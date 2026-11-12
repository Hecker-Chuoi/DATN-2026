# Kế hoạch triển khai Realtime cho work-service (DATN-2026)

> Thuộc bộ tài liệu `docs/services/work_service/realtime/`. Slide đi kèm (có **6 ví dụ tương tác**): [slide_ke_hoach_realtime.html](slide_ke_hoach_realtime.html).
> Dựa trên nghiên cứu: [Jira](../jira/JIRA_REALTIME.md) · [Linear](../linear/LINEAR_REALTIME.md) · [Trello](../trello/TRELLO_REALTIME.md) · [Asana](../asana/ASANA_REALTIME.md), cùng các nguồn về UX và chống xung đột ở §6.1.
> Phạm vi: **chỉ bên trong một Project**. Người dùng thao tác chủ yếu bằng **nút bấm** trong modal chi tiết công việc (đổi trạng thái, giao việc, độ ưu tiên, hạn chót…), không phải kéo thả.
> Trạng thái: **đề xuất, chưa triển khai.** Khớp với "Bước 8.3: STOMP WebSocket & Presence" trong [4_KE_HOACH_TRIEN_KHAI_CHI_TIET.md](../../4_KE_HOACH_TRIEN_KHAI_CHI_TIET.md).

---

## 0. Tóm tắt quyết định

| # | Quyết định | Lý do |
|---|---|---|
| D1 | **Thông báo mỏng + client tải lại** như Jira. Không làm sync engine kiểu Linear. | Work-service có nhiều quy tắc phía server (workflow guard, WIP, cascade, hierarchy). |
| D2 | Truyền tải bằng **WebSocket + STOMP** tại `/ws-work`. | Cần hai chiều cho presence. Gateway đã có route `/work/ws-work/**`. |
| D3 | **Một topic cho mỗi project** `/topic/projects/{projectId}`. Client tự lọc theo `itemId`. | Giống "kênh theo tài nguyên" của Jira và Trello. |
| D4 | Xác thực JWT ở frame STOMP `CONNECT`, **kiểm `PROJECT_VIEW` khi `SUBSCRIBE`**. | Cả Jira, Trello và Linear đều kiểm khi subscribe. |
| D5 | Chỉ phát sự kiện **sau khi commit** (`@TransactionalEventListener(AFTER_COMMIT)`). | Không bao giờ báo một thay đổi đã bị rollback. |
| D6 | **`seq` tăng dần theo project** + **`originClientId` theo tab** + **`version`** trong mọi sự kiện. | Mượn từ Linear: client phát hiện hụt sự kiện, bỏ qua sự kiện của chính tab mình, bỏ qua dữ liệu cũ. |
| D7 | **Không dùng Kafka để vẽ lại UI.** Một instance thì dùng broker in-memory, nhiều instance thì Redis Pub/Sub. Kafka (qua Outbox) dành cho sự kiện tích hợp bền. | Bài học Trello: Kafka chỉ cần khi lưu lượng tới hàng nghìn tin/giây. |
| **D8** | **Sửa theo từng trường + giá trị cũ (`expected`)**: khác trường thì cả hai đều được lưu, cùng trường thì 409 kèm "ai đổi, lúc nào". | Figma (LWW theo thuộc tính) + tinh thần HTTP `If-Match`. Jira không phát hiện xung đột và bị than phiền nhiều năm. |
| **D9** | **Không thay đổi thứ dưới tay người dùng**: menu hay ô nhập đang mở thì hoãn áp dữ liệu, chỉ hiện dòng báo. | Nguyên tắc dịch chuyển bố cục của web.dev (CLS), rào chắn "đệm khi đang thao tác" của Jira. |
| **D10** | **Thang thông báo 5 mức**. Không dùng modal cho xung đột thường. **Không bao giờ làm mất chữ đang gõ.** | Hướng dẫn của NN/g về mức độ thông báo. |
| **D11** | Thao tác tạo (bình luận, việc con, liên kết, tệp): **khoá nút + `Idempotency-Key`**. | Cách làm của Stripe, chống tạo trùng khi bấm đúp hoặc gửi lại. |

> **Điểm mấu chốt:** realtime chỉ giúp *nhìn thấy sớm hơn*. Thứ chặn ghi đè là **D8**, thứ chống giật là **D9 + D10**. Cả ba phải làm cùng nhau. D8, D10 và D11 **làm được ngay ở R0**, trước khi có WebSocket.

---

## 1. Hiện trạng (đối chiếu từ source code, 11/2026)

### 1.1 Hạ tầng

| Thành phần | Hiện trạng | Vị trí |
|---|---|---|
| Thư viện WebSocket | **Chưa có** `spring-boot-starter-websocket` | `backend/work-service/pom.xml` |
| Route gateway `/work/ws-work/**` | **Đã có** ở cả hai profile. Gateway là bản reactive nên proxy được WebSocket | `api-gateway/.../application.yml`, `application-tuananh.yml` |
| Bảo mật gateway | Mọi path trừ `/identity/api/v1/auth/**` đều bắt JWT. **Trình duyệt không gửi được header `Authorization` khi mở WebSocket**, nên handshake sẽ trả 401 | `api-gateway/.../SecurityConfig.java` |
| Proxy dev Vite / nginx | `/work` chưa có `ws: true`; nginx **thiếu `/work/`** | `frontend/vite.config.js`, `frontend/nginx.conf` |
| Kafka + Outbox | Có bean producer và 3 topic, bảng `outbox_events` (V1). **Chỉ ghi** `SPRINT_STARTED/COMPLETED`, **chưa có relay** | `KafkaProducerConfig`, `OutboxRecorder`, `SprintServiceImpl` |
| Redis | :6380, dùng làm cache, có `CacheErrorHandler` chịu lỗi | `config/RedisConfig.java` |
| Lịch sử thay đổi | `work_item_changelogs` lưu `fieldName` (`ASSIGNEE`, `PRIORITY`, `STATUS`…), `changedBy`, `changedAt`. Dùng được để trả lời "ai đổi, lúc nào" khi có xung đột | `entity/WorkItemChangelog.java`, `WorkItemAuditServiceImpl` |
| `version` | `WorkItem` có `@Version`; `WorkItemDetailResponse`, `BoardCardResponse`, `WorkItemResponse` đều trả `version` | các DTO response |

### 1.2 Rủi ro UX và dữ liệu (có sẵn hôm nay, realtime sẽ khuếch đại)

| # | Rủi ro | Bằng chứng | Hệ quả |
|---|---|---|---|
| R-1 | **Ghi đè âm thầm** khi hai người sửa cùng trường | `WorkItemServiceImpl.updateWorkItem` không nhận `expected`/`version` từ client. Mọi nút trong modal đều gọi `workItemApi.update` | Người sau thắng, không ai được báo (ví dụ 2 trong slide) |
| R-2 | **Modal nháy trắng và mất chữ đang gõ** khi tải lại | `fetchDetail()` gọi `setLoading(true)`, mà dòng 793 thay **cả modal** bằng spinner; đồng thời `setSummaryDraft`/`setDescriptionDraft` ghi đè bản nháp | Nếu realtime gọi lại hàm này thì modal nháy liên tục và mất chữ (ví dụ 4). Hiện bấm chuyển trạng thái cũng đã nháy một lần vì `handleExecuteTransition` gọi `fetchDetail` |
| R-3 | **Menu trạng thái bị cũ** | `item.availableTransitions` chỉ tính lúc mở modal (dòng 2072). Server chặn đúng (`WorkflowEngineServiceImpl` dòng 140) nhưng FE chỉ toast lỗi, **không tải lại** | A bấm lại vẫn lỗi mà không hiểu vì sao (ví dụ 3) |
| R-4 | **Bước chuyển "từ mọi trạng thái"** (`fromStatusId = null`) không có kiểm tra nào | `WorkflowEngineServiceImpl.executeTransition` bước 2 | Hai người cùng chuyển thì người sau thắng |
| R-5 | **Bỏ giao việc không lưu được** | Javadoc `UpdateWorkItemRequest` ghi "can be set to null to unassign", nhưng service chỉ xử lý `if (request.getAssigneeId() != null …)` | Chọn "Chưa giao" trên UI thì server bỏ qua |
| R-6 | **Bấm đúp tạo trùng** | `handleAddComment`, `handleAddReply`, `handleAddSubtask` không khoá nút khi đang gửi | Hai bình luận hoặc hai việc con giống hệt (ví dụ 5) |
| R-7 | **Kết quả trả về sai thứ tự đè dữ liệu mới** | Mọi handler `setItem(res.data)` vô điều kiện | Khi có realtime, một lần tải lại về muộn làm giá trị vừa chọn "nhảy ngược" (ví dụ 6) |
| R-8 | **Toast thành công cho mọi thao tác** | "Đã cập nhật độ ưu tiên", "Đã cập nhật hạn chót"… | Gây ồn. Khi thêm realtime, càng dễ dồn thông báo |
| R-9 | `OutboxRecorder` "nuốt lỗi" không hiệu quả | `try/catch` quanh `save()`, nhưng entity UUID chỉ flush lúc commit | Lỗi outbox vẫn làm rollback nghiệp vụ (liên quan R3) |

---

## 2. Những phần nào trong Project cần Realtime

| # | Màn hình / chức năng | Ưu tiên | Jira | Linear | Cách làm ở DATN |
|---|---|---|---|---|---|
| 1 | **Modal chi tiết: các trường** (trạng thái, người thực hiện, độ ưu tiên, hạn chót, điểm, sprint, tiêu đề, mô tả) | **P0** | ✅ | ✅ | Áp theo từng trường, theo thang thông báo §6.3 |
| 2 | **Modal chi tiết: bình luận, tệp, liên kết, lịch sử** | **P0** | ⚠️ | ✅ | Bỏ polling 5 giây, nghe `COMMENT_*`, `ATTACHMENT_*`, `LINK_*` theo `itemId` |
| 3 | **Board / Backlog** (thường nằm phía sau modal) | **P0** | ✅ (refetch) | ✅ | Tải lại im lặng có debounce 300 ms, giữ vị trí cuộn, nháy viền thẻ vừa đổi |
| 4 | **Sprint**: tạo, bắt đầu, hoàn thành | **P0** | ✅ | ✅ | `SPRINT_*`: Board/Backlog tải lại, banner "Sprint X vừa bắt đầu" |
| 5 | **Thu hồi quyền** khi bị xoá khỏi project | **P0 (bảo mật)** | — | sync group | `ACCESS_REVOKED`, đóng session, chuyển trang 403 |
| 6 | **Presence**: "đang xem", "đang sửa mô tả" | P1 | ❌ | ✅ | Giảm xung đột trước khi xảy ra (§6.9) |
| 7 | **Cấu hình project** thay đổi (workflow, cột, loại công việc) | P1 | — | ✅ | `PROJECT_CONFIG_CHANGED`: banner + tải lại bản đồ transition |
| 8 | **Thành viên/vai trò** | P2 | — | ✅ | `MEMBER_CHANGED`: tải lại danh sách và `permissions/my` |
| 9 | **Báo cáo** Burndown/Velocity/CFD | **—** | ❌ | — | Tính khi mở trang |
| 10 | Nhiều người cùng gõ một mô tả (con trỏ, CRDT) | **—** | ❌ | ✅ | Không làm. Thay bằng phát hiện xung đột, so sánh 2 bản và presence "đang sửa" |
| 11 | Thông báo chuông | ngoài phạm vi | — | — | Theo user. Để R3/Kafka |

---

## 3. Kiến trúc

```
┌──────────────── Trình duyệt (React) ─────────────────┐
│ REST: PATCH /items/{id}/fields   STOMP over WebSocket  │
│ (X-Client-Id, Idempotency-Key)   SUB /topic/projects/{id}│
└──────┬──────────────────────────────▲──────────────────┘
┌──────▼────── Vite proxy / nginx (ws: true, Upgrade) ──┴──────┐
┌──────▼──────────── API Gateway (WebFlux) ──────────────┴───────┐
│ /work/api/** → JWT bắt buộc   /work/ws-work/** → permitAll      │
└──────┬──────────────────────────────▲──────────────────────────┘
┌──────▼──────────────────── work-service ─────────────┴──────────────────────┐
│ Controller → *ServiceImpl (@Transactional)                                   │
│   • updateFields: khoá dòng, so expected, ghi, audit, publish(ITEM_UPDATED)  │
│                 ▼                                                            │
│        [COMMIT] ──▶ RealtimeBroadcaster (AFTER_COMMIT) ── seq = INCR ──▶     │
│              RealtimeTransport ──R1──▶ SimpMessagingTemplate (simple broker) │
│                         └──────R2──▶ Redis Pub/Sub ─▶ mọi instance           │
│ StompAuthChannelInterceptor: CONNECT (JWT) · SUBSCRIBE (PROJECT_VIEW)        │
│ PresenceService (R2) · IdempotencyService (R2) · OutboxRelay → Kafka (R3)    │
└──────────────────────────────────────────────────────────────────────────────┘
```

- **Vì sao WebSocket/STOMP:** hai chiều (heartbeat presence), nhiều kênh trên một kết nối, `ChannelInterceptor` kiểm quyền khi SUBSCRIBE, `/user/queue/...` để gửi riêng cho một người. SSE phải tự làm những thứ này, còn polling thì chậm.
- **Vì sao không dùng Kafka cho UI:** UI luôn lấy lại dữ liệu từ DB nên không cần bền. Muốn đẩy xuống trình duyệt thì vẫn phải có WebSocket. Đi qua Outbox → Kafka thì phải chịu thêm độ trễ của poller.
- **Nhiều instance:** dùng Redis Pub/Sub làm backplane (đã có Redis). Kiểu "gửi rồi quên" vẫn chấp nhận được nhờ có `seq` (§5).

---

## 4. Hợp đồng sự kiện

### 4.1 Kênh

| Destination | Hướng | Quyền | Nội dung |
|---|---|---|---|
| `/topic/projects/{projectId}` | server → client | `PROJECT_VIEW` hoặc Global ADMIN | Mọi sự kiện nghiệp vụ |
| `/topic/projects/{projectId}/presence` | server → client | như trên | Ai đang xem / đang sửa (R2) |
| `/user/queue/project-access` | server → một user | chính user đó | `ACCESS_REVOKED` |
| `/app/presence.heartbeat` | client → server | `PROJECT_VIEW` | `{ projectId, itemId?, editingField? }` (R2) |

### 4.2 Phong bì

```json
{
  "seq": 1287,
  "projectId": "6f1c…",
  "type": "ITEM_UPDATED",
  "entityId": "a91e…",
  "itemId": "a91e…",
  "version": 7,
  "actorId": "keycloak-sub-của-người-gây-ra",
  "originClientId": "tab-uuid-của-người-gây-ra",
  "occurredAt": "2026-11-12T10:21:04.120Z",
  "changedFields": ["assigneeId"],
  "patch": { "assigneeId": "…", "version": 7 }
}
```

- `patch` **chỉ chứa ID, enum và số**. Không gửi mô tả hay nội dung bình luận; client cần thì gọi API có kiểm quyền (bài học Jira).
- `changedFields` cho client biết **đúng ô nào** cần nháy hoặc cần giữ (§6.8).
- `version` là version của công việc **sau** thay đổi; client dùng nó để bỏ qua dữ liệu cũ.

### 4.3 Danh mục sự kiện và điểm gắn trong code

| `type` | Phát từ | `patch` / ghi chú |
|---|---|---|
| `ITEM_CREATED` | `WorkItemServiceImpl.createWorkItem`, `createSubtask` | `statusId, sprintId, parentId` |
| `ITEM_UPDATED` | `WorkItemServiceImpl.updateFields` (mới), `updateWorkItem` | các trường ngắn đã đổi; `changedFields` lấy từ diff của `recordDiff` |
| `ITEM_RANKED` | `WorkItemServiceImpl.updateRank` | `rank` |
| `ITEM_MOVED` | `BoardServiceImpl.moveCard` | `statusId, columnId, rank` |
| `ITEM_TRANSITIONED` | `WorkflowEngineServiceImpl.executeTransition`, kể cả các **công việc cha** bị cập nhật dây chuyền trong `cascadeAncestorStatusTransitions` | `statusId, resolution` |
| `ITEM_DELETED` | `WorkItemServiceImpl.deleteWorkItem` | Modal đang mở chuyển sang mức 4 (chỉ đọc) |
| `SPRINT_CREATED/UPDATED/DELETED/STARTED/COMPLETED` | `SprintServiceImpl.create/update/delete/startSprint/completeSprint` | `sprintId, state` |
| `SPRINT_ITEMS_CHANGED` | `SprintServiceImpl.addItemsToSprint/removeItemsFromSprint` | **một** sự kiện kèm `itemIds` |
| `COMMENT_CREATED/UPDATED/DELETED` | `WorkItemCommentServiceImpl.addComment/updateComment/deleteComment` | `commentId` |
| `ATTACHMENT_ADDED/REMOVED` | `WorkItemAttachmentServiceImpl.uploadAttachment/deleteAttachment` | `attachmentId` |
| `LINK_ADDED/REMOVED` | `WorkItemLinkServiceImpl.createLink/deleteLink` | gửi cho **cả hai** công việc |
| `PROJECT_CONFIG_CHANGED` | `BoardServiceImpl` (cấu hình board), `WorkflowServiceImpl`, `WorkItemTypeServiceImpl` | `scope: BOARD/WORKFLOW/TYPES` |
| `MEMBER_CHANGED` | `ProjectMemberServiceImpl.addMemberToProject/updateMemberRole/removeMemberFromProject`, `InvitationServiceImpl.accept` | `userId` |
| `ACCESS_REVOKED` (gửi riêng) | `removeMemberFromProject`, `updateMemberRole` (khi mất `PROJECT_VIEW`) | `projectId` |
| `PROJECT_UPDATED/DELETED` | `ProjectServiceImpl.updateProject/deleteProject` | — |

---

## 5. Thứ tự, mất gói, trùng gói

| Tình huống | Cách xử lý |
|---|---|
| `seq` nhảy hơn 1 | Đã hụt sự kiện, tải lại màn hình hiện tại |
| `seq` ≤ `lastSeq` | Trùng, bỏ qua |
| `seq = -1` (Redis lỗi) | Tải lại |
| `version` ≤ version đang hiển thị | Dữ liệu cũ, bỏ qua (**ví dụ 6**) |
| Kết nối lại | Chờ ngẫu nhiên (backoff có jitter: 3–5 giây, nhân dần, tối đa 30 giây) rồi tải lại toàn màn hình |
| Mất mà không có sự kiện sau | Tải lại khi tab được focus lại (`visibilitychange`) và định kỳ 2–5 phút khi đang mở Board |

Mất hay trùng một sự kiện chỉ làm **chậm**, không làm **sai**, vì client luôn lấy lại dữ liệu từ DB. Realtime chạy sau commit và được bọc `try/catch`, nên không bao giờ làm hỏng nghiệp vụ.

---

## 6. UX & chống xung đột

### 6.1 Nghiên cứu: các sản phẩm xử lý "nhiều người cùng sửa" thế nào

| Nguồn | Cách làm | Hệ quả / bài học |
|---|---|---|
| **Jira Cloud** | Không khoá, **không phát hiện** xung đột, người lưu sau thắng. Theo báo cáo người dùng, form Sửa có thể xoá trắng trường người khác vừa nhập. | Than phiền kéo dài: JRACLOUD-6146 (khoá issue), JRACLOUD-37142 (cảnh báo khi đồng thời sửa) vẫn ở trạng thái "Gathering Interest". **Không phát hiện thì người dùng mất niềm tin.** |
| **Linear** | Server quyết định thứ tự, người ghi sau thắng (LWW) cho Update. Cập nhật tức thì. | Mượt, không hộp thoại, nhưng bị đè thì không được báo. |
| **Figma** (Evan Wallace, 16/10/2019) | LWW **theo từng thuộc tính** của từng đối tượng; khác thuộc tính thì không xung đột. Thay đổi cục bộ áp ngay, không chờ server. Client **bỏ qua thay đổi từ server cho thuộc tính mình đang chờ xác nhận**: "discard incoming changes from the server that conflict with unacknowledged property changes". Nếu không làm vậy, "older acknowledged values temporarily overwrite newer unacknowledged ones" và màn hình nhấp nháy. | Hai quy tắc DATN mượn nguyên: **theo từng trường** và **ưu tiên thay đổi đang chờ của mình**. |
| **HTTP, RFC 9110** | Ghi có điều kiện `If-Match`: tài nguyên đã đổi thì trả `412 Precondition Failed`. Đây là cơ chế chuẩn chống "lost update". | DATN áp dụng **ở mức từng trường** (`expected`), để khác trường không bị chặn oan. |
| **Stripe API** | Header `Idempotency-Key`: gửi lại cùng key thì nhận lại kết quả lần đầu, không thực hiện lại. | Áp dụng cho các thao tác **tạo** (bình luận, việc con, liên kết). |
| **web.dev (CLS)** | Dịch chuyển bố cục chỉ được coi là "được mong đợi" trong **500 ms sau một thao tác rời rạc** (bấm, gõ phím) của chính người dùng. | Thay đổi do **người khác** mà làm xê dịch thứ người dùng đang nhắm tới là dịch chuyển ngoài ý muốn, nên phải hoãn (D9). |
| **Nielsen Norman Group** | Modal chỉ dành cho tình huống nghiêm trọng cần quyết định. Thông báo cần ít tương tác thì dùng toast/banner. Lỗi phải ở lại tại chỗ, không chỉ là toast tự tắt. | Thang thông báo 5 mức (§6.3). Hộp xung đột đặt **ngay tại ô**. |

**Tổng hợp cho DATN:** lấy độ mượt của Figma/Linear (khác trường thì không cản trở), cộng với khả năng phát hiện của HTTP `If-Match` (cùng trường thì báo rõ), tránh đúng điểm yếu của Jira.

### 6.2 Bảy nguyên tắc

| # | Nguyên tắc | Cụ thể | Nguồn |
|---|---|---|---|
| ① | **Từng trường** | Khác trường thì cả hai được lưu. Cùng trường thì phát hiện bằng `expected` | Figma, RFC 9110 |
| ② | **Phản hồi ngay** | Chọn giá trị là hiện ngay (cập nhật lạc quan). Chỉ hiện vòng xoay nếu chờ quá 300 ms, để phản hồi nhanh không bị nháy | Linear, Figma |
| ③ | **Không đổi dưới tay** | Menu hoặc ô nhập đang mở thì hoãn áp dữ liệu và chỉ hiện dòng báo. Áp khi đóng | web.dev CLS, Jira |
| ④ | **Thay đổi của mình được ưu tiên** | Trường đang gửi thì tạm giữ giá trị server tới cho đến khi có kết quả | Figma |
| ⑤ | **Mới thắng cũ** | Dữ liệu có `version` nhỏ hơn bản đang hiển thị thì bỏ qua | Linear `lastSyncId` |
| ⑥ | **Báo đúng mức** | Thang 5 mức. Không modal cho xung đột thường. **Không bao giờ làm mất chữ** | NN/g |
| ⑦ | **Tạo đúng một lần** | Khoá nút khi đang gửi + `Idempotency-Key` | Stripe |

### 6.3 Thang thông báo 5 mức

| Mức | Khi nào | Hình thức | Ví dụ |
|---|---|---|---|
| **0 · Im lặng** | Thay đổi ở chỗ người dùng không nhìn | Board/Backlog phía sau modal tự tải lại, giữ vị trí cuộn | — |
| **1 · Nháy + ghi tên** | Trường đang hiển thị bị người khác đổi | Nền vàng 1,5 giây + "B · vừa xong" (hiện 10 giây rồi mờ dần) | Ví dụ 1 |
| **2 · Báo tại chỗ** | Trường đang mở menu hoặc đang sửa bị người khác đổi | Một dòng trong menu, khoá các lựa chọn cũ / dải phía trên ô sửa. **Không đóng menu, không đổi nội dung** | Ví dụ 3, 4 |
| **3 · Hộp xung đột tại ô** | Lưu của mình bị 409 | Ngay dưới ô: ai đổi, lúc nào, giá trị nào + 2–3 lựa chọn | Ví dụ 2, 4 |
| **4 · Banner khoá** | Công việc bị xoá, mất quyền | Chuyển modal sang chỉ đọc kèm banner, hoặc chuyển tới trang 403 | — |

**Quy tắc toast:**
- Chỉ dùng toast cho **lỗi** trong thao tác của chính mình, kèm thông báo tại chỗ.
- **Bỏ** các toast "Đã cập nhật…" khi đổi một trường, vì ô vừa đổi đã là phản hồi (R-8).
- **Không** toast cho thay đổi của người khác. Riêng sự kiện sprint (bắt đầu/hoàn thành) dùng banner trên Board.

### 6.4 Bảng quyết định theo loại thao tác

| Thao tác | Hiển thị | Chống xung đột | Khi xung đột (UX) |
|---|---|---|---|
| **Chuyển trạng thái** | Chờ server, vì có thể chạy workflow guard hoặc mở modal "hoàn thành việc con" | Server đã kiểm "từ trạng thái"; thêm `expectedStatusId` để phủ cả bước chuyển "từ mọi trạng thái" (R-4) | 409: modal tải lại im lặng, menu làm mới, hiện "B vừa chuyển sang Đang làm (10:21)" |
| **Chọn giá trị**: người thực hiện, độ ưu tiên, hạn chót, ngày bắt đầu, điểm | Hiện ngay (lạc quan), vòng xoay nhỏ | `PATCH /fields` kèm `expected` của **đúng trường đó** | Quay về giá trị server, hộp tại ô: **[Vẫn đổi thành X]** (`force`) **[Giữ Y]** |
| **Sprint** của công việc | Hiện ngay | `expectedSprintId` trong API sprint (vì có cascade sang việc con) | Như trên |
| **Văn bản**: tiêu đề, mô tả | Soạn trong bản nháp riêng, **không bao giờ bị ghi đè từ server** | `expected` = bản gốc lúc bắt đầu sửa; presence "đang sửa" (R2) | So sánh hai bản: **[Gộp & lưu]** **[Dùng bản mới]** **[Sao chép bản của tôi]** |
| **Bình luận mới** | Thêm vào cuối danh sách | Chỉ thêm mới nên không xung đột. Khoá nút + `Idempotency-Key` | Người đang đọc giữa danh sách thì hiện nút "N bình luận mới ↓", không tự cuộn |
| **Sửa/xoá bình luận của mình** | Chờ server | `expected` = nội dung cũ; xoá thứ đã bị xoá thì coi như thành công | "Bình luận đã bị xoá" |
| **Tệp, liên kết, việc con** | Chờ server | Khoá nút + `Idempotency-Key`; xoá lặp lại không báo lỗi | Không có xung đột thật |
| **Công việc bị xoá** khi đang mở | — | Sự kiện `ITEM_DELETED` | Mức 4: chỉ đọc + banner "Công việc đã bị B xoá" |

### 6.5 Giao thức sửa theo trường (server)

**Request** (endpoint mới, giữ `PUT /items/{id}` cũ để tương thích):

```http
PATCH /api/v1/work/items/{id}/fields
X-Client-Id: 3f9c…
Content-Type: application/json

{
  "changes":  { "assigneeId": "minh-sub" },
  "expected": { "assigneeId": "nam-sub" },
  "force": false
}
```

- `changes` là **map**, nên phân biệt được "không gửi" với "đặt rỗng": `{"assigneeId": null}` nghĩa là bỏ giao việc. Cách này sửa luôn R-5.
- Danh sách trường được phép (whitelist): `summary`, `description`, `priority`, `assigneeId` (cần `ISSUE_ASSIGN`), `storyPoints`, `dueDate`, `startDate`, `originalEstimateSeconds`, `remainingEstimateSeconds`, `componentId`, `fixVersionId`. Các trường cần validator riêng (`typeId`, `parentId`, `sprintId`) vẫn đi qua API cũ hoặc API sprint, có thêm `expected` tương ứng.

**Phản hồi xung đột:**

```json
HTTP/1.1 409 Conflict
{
  "success": false,
  "code": "FIELD_CONFLICT",
  "message": "Người thực hiện vừa được thay đổi bởi người khác",
  "data": {
    "conflicts": [{
      "field": "assigneeId", "expected": "nam-sub", "actual": "lan-sub",
      "changedBy": "a-sub", "changedAt": "2026-11-12T10:21:04"
    }],
    "item": { "...": "WorkItemDetailResponse mới nhất", "version": 6 }
  }
}
```

**Thuật toán (phác thảo):**

```java
@Transactional
public WorkItemDetailResponse updateFields(UUID itemId, FieldPatchRequest req, UUID userId) {
    // Khoá dòng: hai request cùng lúc sẽ được xếp hàng, nên request sau
    // luôn so expected với giá trị request trước VỪA ghi (không lọt cả hai).
    WorkItem item = workItemRepository.findByIdForUpdate(itemId)       // @Lock(PESSIMISTIC_WRITE)
            .orElseThrow(() -> new ResourceNotFoundException("…"));

    if (!req.force()) {
        List<FieldConflict> conflicts = new ArrayList<>();
        req.expected().forEach((field, expected) -> {
            Object actual = WorkItemFields.read(item, field);
            if (!WorkItemFields.sameValue(actual, expected)) {        // chuẩn hoá UUID/ngày/BigDecimal
                WorkItemChangelog last = changelogRepository
                        .findTopByWorkItemIdAndFieldNameOrderByChangedAtDesc(itemId, WorkItemFields.auditName(field))
                        .orElse(null);                                 // "ASSIGNEE", "PRIORITY"…
                conflicts.add(FieldConflict.of(field, expected, actual, last));
            }
        });
        if (!conflicts.isEmpty()) {
            throw new FieldConflictException(conflicts, toDetail(item)); // → 409 + data
        }
    }

    WorkItem before = snapshot(item);
    req.changes().forEach((field, value) -> WorkItemFields.write(item, field, value, projectAccessService));
    WorkItem saved = workItemRepository.saveAndFlush(item);            // flush để version tăng ngay
    List<String> changed = workItemAuditService.recordDiff(before, saved, userId);
    projectEventPublisher.publish(saved.getProjectId(), ITEM_UPDATED, saved.getId(), saved.getId(),
            WorkItemFields.shortPatch(saved, changed), changed);
    return toDetail(saved);
}
```

- **Tất cả hoặc không:** một trường xung đột thì không lưu trường nào. Giao diện bấm nút gửi từng trường một, nên quy tắc này hầu như không ảnh hưởng.
- `force: true` **chỉ** được gửi khi người dùng bấm "Vẫn đổi…". Lịch sử thay đổi vẫn ghi đầy đủ nên luôn truy vết được.
- `@Version` vẫn giữ làm lưới an toàn cuối cùng (`ObjectOptimisticLockingFailureException` → 409).
- `GlobalExceptionHandler` thêm handler cho `FieldConflictException`, trả `ApiResponse` **có `data`** (handler `ConflictException` hiện chỉ trả `message`).

### 6.6 Chuyển trạng thái

- `ExecuteTransitionRequest` thêm `expectedStatusId`. Trong `executeTransition`, ngay sau bước 1: nếu `expectedStatusId != null && !expectedStatusId.equals(workItem.getStatusId())` thì trả 409 kèm `changedBy` và `changedAt` lấy từ changelog `STATUS`.
- FE gửi `expectedStatusId = item.statusId` đang hiển thị. Khi nhận 400 hoặc 409 thì **tải lại im lặng** và hiện thông báo mức 2/3 thay vì chỉ toast (R-3).

### 6.7 Chống tạo trùng

| Lớp | R0 | R2 |
|---|---|---|
| Frontend | Cờ `submitting` cho từng form; nút hiển thị "Đang gửi…" và bị `disabled` | Sinh `Idempotency-Key = crypto.randomUUID()` **một lần cho mỗi lần bấm**; gửi lại (retry) thì dùng cùng key |
| Backend | — | `IdempotencyService`: `SET idem:{sub}:{key} PENDING NX EX 600`. Đã có thì trả lại kết quả cũ (lưu id bản ghi đã tạo), hoặc 409 "đang xử lý" nếu vẫn `PENDING`. Áp dụng cho `POST` comment, subtask, link, attachment |

### 6.8 Mô hình trạng thái phía client

Mỗi trường trong modal có **ba lớp**:

| Lớp | Ý nghĩa | Ai ghi |
|---|---|---|
| `server[f]` | Giá trị đã xác nhận, kèm `server.version` | Response của mình, hoặc tải lại im lặng |
| `pending[f]` | Giá trị mình vừa chọn, đang chờ server | Khi bấm chọn |
| `editing[f]` / `menuOpen[f]` | Đang mở menu hoặc ô sửa (kèm `draft[f]`) | Khi mở hoặc đóng |

```js
// Hiển thị: đang sửa → đang gửi → server
const shown = (f) => (editing[f] ? draft[f] : f in pending ? pending[f] : server[f]);

// Khi bấm chọn giá trị (②)
async function setField(f, value) {
  const expected = server[f];
  pending[f] = value;                                   // hiện ngay
  try {
    const res = await workItemApi.patchFields(id, { changes: { [f]: value }, expected: { [f]: expected } });
    applyIfNewer(res.data);                             // ⑤
  } catch (err) {
    if (err.status === 409) { applyIfNewer(err.data.item); showConflict(f, err.data.conflicts[0], value); } // mức 3
    else showInlineError(f, err.message);
  } finally {
    delete pending[f];
    flushDeferred(f);                                   // áp phần đã hoãn (nếu có)
  }
}

// Khi nhận sự kiện realtime
async function onRemoteEvent(e) {
  if (e.originClientId === TAB_ID) return;              // của chính tab mình
  if (e.version <= server.version) return;              // ⑤ dữ liệu cũ
  const item = await fetchSilent(id);                   // KHÔNG setLoading, KHÔNG đụng draft
  for (const f of e.changedFields) {
    if (f in pending) defer(f, item, e);                // ④ Figma: giữ giá trị của mình
    else if (editing[f] || menuOpen[f]) { defer(f, item, e); hintAt(f, e.actorId); } // ③ mức 2
    else { server[f] = item[f]; flash(f, e.actorId); }  // mức 1
  }
  server.version = Math.max(server.version, item.version);
}

const applyIfNewer = (next) => setItem((prev) => (!prev || next.version >= prev.version ? next : prev));
```

Thay đổi cụ thể trong `WorkItemDetailModal.jsx`:
- `fetchDetail` (dùng khi mở modal) giữ nguyên. Thêm `fetchSilent`: không `setLoading`, không `setSummaryDraft`/`setDescriptionDraft` khi đang sửa (R-2).
- Thay mọi `setItem(res.data)` bằng `applyIfNewer(res.data)` (R-7).
- `handleExecuteTransition`: gửi `expectedStatusId`; bắt 400/409 để tải lại im lặng và hiện thông báo mức 2/3 (R-3).
- `handleUpdatePriority`, `handleUpdateAssignee`, `handleUpdateDueDate`, `handleUpdateStoryPoints`, `handleSaveSummary`, `handleSaveDescription` đổi sang `setField(...)`.
- Thêm cờ `submitting` cho `handleAddComment`, `handleAddReply`, `handleAddSubtask` (R-6).
- Bỏ toast thành công cho thay đổi trường (R-8).

### 6.9 Presence: ngăn xung đột từ trước khi xảy ra (R2)

- Client gửi `/app/presence.heartbeat` mỗi **20 giây** khi mở modal, kèm `{ projectId, itemId, editingField? }`. Mở ô sửa mô tả thì gửi ngay một nhịp có `editingField: "description"`.
- Server lưu Redis `ZADD rt:presence:{projectId}:{itemId} <epochMillis> <sub>|<field>`. `@Scheduled` mỗi 30 giây dọn các mục cũ hơn 60 giây; `SessionDisconnectEvent` thì xoá ngay. Tập người thay đổi thì phát `PRESENCE_CHANGED`.
- UI: chồng avatar "Đang xem" ở đầu modal; ô đang được người khác sửa có nhãn **"A đang sửa mô tả…"** (khoá mềm, không chặn). Người thứ hai thấy nhãn này thường sẽ chờ, nên phần lớn xung đột văn bản không bao giờ xảy ra. Jira không có tính năng này (JRACLOUD-80241) và phải dùng app bên ngoài.

### 6.10 Sáu ví dụ tương tác (trong slide)

Mỗi ví dụ có các chế độ **Hiện tại / Realtime làm ẩu / Đề xuất** và chạy được từng bước. Có thể mở thẳng một bước bằng URL, ví dụ `slide_ke_hoach_realtime.html?mode=new&step=3#16`.

| Ví dụ (slide) | Tình huống | Hiện tại / làm ẩu | Đề xuất |
|---|---|---|---|
| 1 (15) | A đổi Độ ưu tiên, B đổi Người thực hiện | Dữ liệu không mất, nhưng mỗi người nhìn dữ liệu cũ; giá trị tự nhảy mà không rõ ai đổi | Cả hai được lưu; ô của người kia nháy + "· vừa xong" |
| 2 (16) | A và B cùng giao lại một công việc | Người sau ghi đè âm thầm | 409 tại ô, có tên người đổi và giờ đổi; "Vẫn giao" là ghi đè có chủ đích |
| 3 (17) | Đang mở menu trạng thái thì người khác đổi | Hiện tại: 400 không giải thích. Làm ẩu: modal nháy, lựa chọn đổi dưới con trỏ, **bấm nhầm** | Menu giữ nguyên + dòng báo; đóng menu mới áp |
| 4 (18) | Đang sửa mô tả thì người khác lưu | Hiện tại: mất bản của B. Làm ẩu: **mất chữ A đang gõ** | Presence "đang sửa", dải báo, so sánh 2 bản, gộp rồi lưu |
| 5 (19) | Bấm đúp Gửi, người kia đang đọc dở | 2 bình luận trùng, danh sách bị đẩy | Khoá nút + Idempotency-Key, nút "1 bình luận mới ↓" |
| 6 (20) | Kết quả trả về sai thứ tự | Giá trị vừa chọn "nhảy ngược" | So version, bỏ dữ liệu cũ |

---

## 7. Bảo mật

| Lớp | Biện pháp |
|---|---|
| Handshake qua gateway | Thêm `"/work/ws-work/**"` vào `PUBLIC_ENDPOINTS`; xác thực ở **frame CONNECT**. Không dùng `?access_token=` vì token sẽ lộ vào log |
| work-service `SecurityConfig` | `permitAll` cho `/ws-work/**` |
| CONNECT | `StompAuthChannelInterceptor` đọc header `Authorization: Bearer …`, dùng `JwtDecoder.decode()` rồi `accessor.setUser(keycloakJwtAuthenticationConverter.convert(jwt))` |
| SUBSCRIBE | Chỉ chấp nhận `^/topic/projects/{uuid}(/presence)?$` hoặc `/user/queue/**`; gọi `projectMemberService.hasPermission(projectId, sub, PROJECT_VIEW)`; `ROLE_ADMIN` được bỏ qua kiểm tra. Trong luồng STOMP không có `SecurityContextHolder`, nên đọc quyền từ `Principal`. Kiểm luôn `exp` của token |
| Thu hồi quyền | `ACCESS_REVOKED` tới `/user/{sub}/queue/project-access`, chủ động đóng session (giữ map session ↔ subscription qua `SessionSubscribeEvent`/`SessionDisconnectEvent`) |
| Token hết hạn giữa phiên | Client đóng rồi mở lại kết nối mỗi khi `api.js` refresh token thành công |
| Không lộ dữ liệu | `patch` chỉ chứa ID/enum/số. `FieldConflict.actual` chỉ trả cho người có quyền sửa: endpoint `PATCH /items/{id}/fields` gắn `@RequireTaskPermission(PermissionKey.ISSUE_EDIT)` giống `PUT /items/{id}` hiện tại |

---

## 8. Phác thảo mã nguồn (realtime)

```xml
<!-- backend/work-service/pom.xml -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```

```java
// config/WebSocketConfig.java
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-work").setAllowedOriginPatterns(frontendUrl, "http://localhost:*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        ThreadPoolTaskScheduler heartbeat = new ThreadPoolTaskScheduler();
        heartbeat.setPoolSize(1);
        heartbeat.setThreadNamePrefix("ws-heartbeat-");
        heartbeat.initialize();
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{10_000, 10_000}).setTaskScheduler(heartbeat);
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
```

```java
// realtime/StompAuthChannelInterceptor.java (rút gọn)
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {
    private static final Pattern PROJECT_TOPIC = Pattern.compile("^/topic/projects/([0-9a-fA-F-]{36})(/presence)?$");
    private final JwtDecoder jwtDecoder;
    private final Converter<Jwt, AbstractAuthenticationToken> keycloakJwtAuthenticationConverter;
    private final ProjectMemberService projectMemberService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor acc = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (acc == null || acc.getCommand() == null) return message;
        switch (acc.getCommand()) {
            case CONNECT -> {
                String header = acc.getFirstNativeHeader("Authorization");
                if (header == null || !header.startsWith("Bearer ")) throw new AccessDeniedException("Thiếu token");
                acc.setUser(keycloakJwtAuthenticationConverter.convert(jwtDecoder.decode(header.substring(7))));
            }
            case SUBSCRIBE -> {
                String dest = Objects.toString(acc.getDestination(), "");
                if (dest.startsWith("/user/queue/")) return message;
                Matcher m = PROJECT_TOPIC.matcher(dest);
                if (!m.matches()) throw new AccessDeniedException("Kênh không hợp lệ");
                assertCanView((JwtAuthenticationToken) acc.getUser(), UUID.fromString(m.group(1)));
            }
            default -> { /* SEND: chỉ /app/presence.* (R2) */ }
        }
        return message;
    }

    private void assertCanView(JwtAuthenticationToken auth, UUID projectId) {
        if (auth == null || auth.getToken().getExpiresAt().isBefore(Instant.now())) throw new AccessDeniedException("Phiên đã hết hạn");
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        UUID userId = UUID.fromString(auth.getToken().getSubject());   // Keycloak sub
        if (!admin && !projectMemberService.hasPermission(projectId, userId, PermissionKey.PROJECT_VIEW)) {
            throw new AccessDeniedException("Không có quyền xem dự án");
        }
    }
}
```

```java
// realtime/ProjectEventPublisher.java + RealtimeBroadcaster.java
@Component
@RequiredArgsConstructor
public class ProjectEventPublisher {
    private final ApplicationEventPublisher publisher;
    public void publish(UUID projectId, RealtimeEventType type, UUID entityId, UUID itemId,
                        Map<String, Object> patch, List<String> changedFields) {
        publisher.publishEvent(new ProjectRealtimeEvent(projectId, type, entityId, itemId, patch, changedFields,
                SecurityUtils.getCurrentUserIdOptional().orElse(null), currentClientId()));
    }
    private static String currentClientId() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes sra
                ? sra.getRequest().getHeader("X-Client-Id") : null;
    }
}

@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeBroadcaster {
    private final RealtimeTransport transport;     // R1: SimpTransport · R2: RedisBackplaneTransport
    private final StringRedisTemplate redis;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onProjectEvent(ProjectRealtimeEvent e) {
        try {
            transport.send("/topic/projects/" + e.projectId(), RealtimeEnvelope.of(nextSeq(e.projectId()), e));
        } catch (Exception ex) {
            log.warn("Realtime publish failed for {} {}: {}", e.type(), e.entityId(), ex.getMessage());
        }
    }
    private long nextSeq(UUID projectId) {
        try { Long v = redis.opsForValue().increment("rt:seq:" + projectId); return v == null ? -1 : v; }
        catch (Exception ex) { return -1; }
    }
}
```

```js
// frontend/src/realtime/realtimeClient.js: một kết nối dùng chung
import { Client } from '@stomp/stompjs';
import { userTokenStore } from '../api.js';

export const TAB_ID = crypto.randomUUID();   // api.js gắn vào header X-Client-Id

export const realtime = new Client({
  brokerURL: `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/work/ws-work`,
  heartbeatIncoming: 10000,
  heartbeatOutgoing: 10000,
  reconnectDelay: 3000 + Math.floor(Math.random() * 2000),   // jitter
  beforeConnect: () => { realtime.connectHeaders = { Authorization: `Bearer ${userTokenStore.get()}` }; },
});
```

```js
// vite.config.js
'/work': { target: gateway, changeOrigin: true, ws: true },
```

```nginx
# frontend/nginx.conf (đồng thời sửa thiếu /work/)
location /work/ {
    proxy_pass http://api-gateway:8081;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
    proxy_read_timeout 3600s;
}
```

---

## 9. Danh sách file sẽ thay đổi

| Khu vực | File | Thay đổi | GĐ |
|---|---|---|---|
| work-service | `controller/WorkItemController.java` | + `PATCH /items/{id}/fields` | R0 |
| | `dto/request/FieldPatchRequest.java`, `dto/response/FieldConflict.java`, `exception/FieldConflictException.java` (mới) | Giao thức theo trường | R0 |
| | `service/WorkItemFields.java` (mới) | Whitelist trường: đọc, ghi, so sánh chuẩn hoá, tên audit | R0 |
| | `WorkItemServiceImpl.updateFields` (mới) | Khoá dòng, so expected, 409 | R0 |
| | `repository/WorkItemRepository` | `findByIdForUpdate` (`@Lock(PESSIMISTIC_WRITE)`) | R0 |
| | `repository/WorkItemChangelogRepository` | `findTopByWorkItemIdAndFieldNameOrderByChangedAtDesc` | R0 |
| | `dto/request/ExecuteTransitionRequest` + `WorkflowEngineServiceImpl` | `expectedStatusId` | R0 |
| | `exception/GlobalExceptionHandler` | Handler cho `FieldConflictException` (409 kèm `data`) | R0 |
| | `pom.xml`, `config/WebSocketConfig.java`, `realtime/*` (interceptor, publisher, broadcaster, transport, session registry) | Realtime lõi | R1 |
| | `config/SecurityConfig.java` | `permitAll("/ws-work/**")` | R1 |
| | 12 `*ServiceImpl` ở §4.3 | Gọi `projectEventPublisher.publish(...)` | R1 |
| | `realtime/PresenceService.java`, `realtime/IdempotencyService.java`, `realtime/RedisBackplaneTransport.java` | Presence, chống trùng, backplane | R2 |
| | `service/OutboxRelay.java` | Outbox gửi Kafka | R3 |
| api-gateway | `security/SecurityConfig.java` | + `/work/ws-work/**` | R1 |
| frontend | `src/api.js` | `X-Client-Id`; `workItemApi.patchFields`; giữ `data` của lỗi 409 trong object lỗi | R0 |
| | `components/items/WorkItemDetailModal.jsx` | `fetchSilent`, `applyIfNewer`, `setField`, hộp xung đột tại ô, cờ `submitting`, bỏ toast thành công (§6.8) | R0 |
| | `src/realtime/realtimeClient.js`, `useProjectRealtime.js` (mới); `package.json` + `@stomp/stompjs` | Kết nối, `seq`, resync | R1 |
| | `WorkItemDetailModal.jsx` | Bỏ polling 5 giây, `onRemoteEvent`, hoãn khi menu/ô sửa mở | R1 |
| | `hooks/useBoardData.js`, `hooks/useProjectBacklog.js` | Tải lại im lặng có debounce, nháy viền thẻ | R1 |
| | `pages/ProjectDetailPage.jsx` | `ACCESS_REVOKED`, `PROJECT_CONFIG_CHANGED`, chỉ báo "Mất kết nối realtime" | R1 |
| | `vite.config.js`, `nginx.conf` | `ws: true`; `location /work/` | R1 |
| | Modal: avatar "Đang xem", nhãn "đang sửa"; nút "N bình luận mới ↓" | | R2 |
| Tài liệu | `CLAUDE.md`, `docs/AUTHORIZATION.md`, `docs/API_DOCUMENTATION.md` | Ghi chú `/work/ws-work/**` public ở gateway; endpoint `PATCH /fields` | R0–R1 |

**Không cần migration Flyway** cho R0–R2 (khoá dòng dùng bảng sẵn có; `seq`, presence và idempotency nằm trong Redis).

---

## 10. Lộ trình

| Giai đoạn | Nội dung | Ước lượng | Tiêu chí hoàn thành |
|---|---|---|---|
| **R0: An toàn dữ liệu & UX** (không cần WebSocket) | `PATCH /fields` + `expected` + 409 kèm "ai/lúc nào"; `expectedStatusId`; bỏ giao việc bằng `null`; `fetchSilent` + `applyIfNewer`; hộp xung đột tại ô; cờ `submitting`; 400/409 thì tự tải lại; bỏ toast thành công | 1,5–2 ngày | U1, U2, U4 (phần lưu), U5, U6 đạt dù chưa có realtime |
| **R1: Realtime lõi** | STOMP, xác thực, kiểm quyền, publisher AFTER_COMMIT, `seq`; modal áp theo trường (mức 1–2), hoãn khi đang mở; Board/Backlog tải lại im lặng; Sprint; `ACCESS_REVOKED`; gateway, Vite, nginx | 3–4 ngày | U1–U4, T1, T4–T7, T9, T10 đạt |
| **R2: Trải nghiệm** | Presence "đang xem/đang sửa"; `Idempotency-Key` phía server; nút "N bình luận mới"; Redis backplane | 2–3 ngày | U7, T11, T12 đạt |
| **R3: Sự kiện bền** | `OutboxRelay` gửi Kafka; consumer mẫu (thông báo in-app / automation) | 2–3 ngày | Tắt Kafka, thao tác, bật lại thì sự kiện vẫn tới |

---

## 11. Kịch bản kiểm thử

Hai trình duyệt (hoặc một cửa sổ ẩn danh), hai user khác nhau, cùng là thành viên một project. Dùng DevTools → Network → throttling "Slow 3G" cho các ca về thời gian.

| Mã | Kịch bản | Kỳ vọng |
|---|---|---|
| U1 | A đổi Độ ưu tiên, B đổi Người thực hiện cùng công việc | Cả hai được lưu; mỗi bên thấy ô kia nháy + tên người đổi; không popup, không spinner toàn modal |
| U2 | A và B cùng đổi Người thực hiện | Người sau nhận hộp xung đột tại ô (tên và giờ đổi); "Vẫn đổi" thì lưu, người kia thấy nháy |
| U3 | A đang mở menu Trạng thái thì B chuyển trạng thái | Menu không đổi dưới con trỏ, có dòng báo; đóng menu mới cập nhật; không thể bấm nhầm |
| U4 | A đang gõ mô tả thì B lưu mô tả | Chữ của A còn nguyên; có dải báo; khi A lưu thấy so sánh 2 bản |
| U5 | Bấm Gửi bình luận 2 lần trên Slow 3G | Chỉ có 1 bình luận |
| U6 | Response cũ về sau response mới (Slow 3G + thao tác liên tiếp) | Màn hình giữ giá trị mới |
| U7 | A mở sửa mô tả | B thấy "A đang sửa mô tả…" trong ≤ 1 giây; A đóng thì nhãn mất trong ≤ 60 giây |
| U8 | Chọn "Chưa giao" | Server lưu `assigneeId = null` |
| T1 | A đổi trạng thái | Board của B cập nhật trong ≤ 1 giây, không spinner, giữ vị trí cuộn |
| T4 | Xoá B khỏi project | B chuyển trang 403; subscribe lại nhận ERROR |
| T5 | Ngắt mạng B 30 giây | B tự kết nối lại (có jitter) và tải lại |
| T6 | Dừng work-service | FE hiện "Mất kết nối realtime"; work-service lên lại thì tự nối |
| T7 | Thao tác bị rollback (WIP, workflow guard) | B không nhận sự kiện |
| T9 | A hoàn thành sprint | Board/Backlog của B đổi, có banner |
| T10 | User ngoài project tự SUBSCRIBE | ERROR "Không có quyền xem dự án" |
| T12 | 2 instance work-service | Vẫn nhận chéo qua Redis backplane |

**Unit test** (JUnit 5 + Mockito, đúng phong cách `work-service/src/test`):
- `WorkItemFieldsTest`: so sánh chuẩn hoá (UUID dạng chuỗi, `BigDecimal` 3 và 3.0, ngày, `null`); whitelist từ chối trường lạ.
- `WorkItemServiceImplUpdateFieldsTest`: expected khớp thì lưu và publish; lệch thì `FieldConflictException` kèm `changedBy` từ changelog; `force` thì bỏ qua kiểm tra; `null` thì bỏ giao việc; không có quyền `ISSUE_ASSIGN` thì 403.
- `WorkflowEngineServiceImplTest`: `expectedStatusId` lệch thì 409, kể cả với bước chuyển "từ mọi trạng thái".
- `StompAuthChannelInterceptorTest`, `RealtimeBroadcasterTest`, `ProjectEventPublisherTest`, `IdempotencyServiceTest`.

---

## 12. Rủi ro và lưu ý

| Rủi ro | Giảm thiểu |
|---|---|
| Khoá dòng (`PESSIMISTIC_WRITE`) gây chờ | Transaction rất ngắn (một dòng, vài trường); chỉ dùng cho `updateFields`. `@Version` vẫn là lưới an toàn |
| So sánh `expected` sai kiểu (UUID/chuỗi, `BigDecimal`, múi giờ) | Một chỗ chuẩn hoá duy nhất `WorkItemFields.sameValue` + unit test |
| Người dùng lạm dụng "Vẫn đổi" | Đây là quyết định có chủ đích; lịch sử thay đổi vẫn ghi đầy đủ và người kia được báo mức 1 |
| Quên gọi `publish` ở service mới | Bảng §4.3 là checklist; lưới an toàn tải lại khi focus và định kỳ |
| Dây chuyền (sprint sang việc con, con sang cha) phát nhiều sự kiện | Phát một sự kiện tổng; FE debounce |
| Hai bộ token user/admin trong `api.js` | Realtime chỉ cho cổng user `/w/...` |
| Bẫy hai loại userId | `actorId`, `changedBy` và kiểm quyền luôn dùng Keycloak `sub` |
| work-service chưa có trong docker-compose, nginx thiếu `/work/` | Sửa nginx ở R1; dev chạy bằng IDE và Vite proxy |

---

## Nguồn (phần UX & chống xung đột)

- Figma – *How Figma's multiplayer technology works* (Evan Wallace, 16/10/2019): https://www.figma.com/blog/how-figmas-multiplayer-technology-works/
- Liveblocks – *Understanding sync engines: how Figma, Linear and Google Docs work*: https://www.liveblocks.io/blog/understanding-sync-engines-how-figma-linear-and-google-docs-work
- Atlassian Community – *If one person updates at the same time will Jira overwrite?*: https://community.atlassian.com/t5/Jira-questions/If-one-person-updates-at-the-same-time-will-Jira-overwrite/qaq-p/437550
- Atlassian Community – *Jira issue edits being overwritten, how can this be prevented?*: https://community.atlassian.com/t5/Jira-questions/Jira-issue-edits-being-overwritten-how-can-this-be-prevented/qaq-p/2120415
- JRACLOUD-6146 (khoá issue khi sửa): https://jira.atlassian.com/browse/JRACLOUD-6146 · JRACLOUD-37142 (cảnh báo đồng thời sửa): https://jira.atlassian.com/browse/JRACLOUD-37142 · JRACLOUD-80241 (ai đang xem): https://jira.atlassian.com/browse/JRACLOUD-80241
- HTTP Semantics, RFC 9110 – `If-Match`, `412 Precondition Failed`: https://www.rfc-editor.org/rfc/rfc9110 · tóm tắt: https://http.dev/if-match
- Stripe – *Idempotent requests*: https://docs.stripe.com/api/idempotent_requests · *Designing robust and predictable APIs with idempotency*: https://stripe.com/blog/idempotency
- web.dev – *Cumulative Layout Shift (CLS)*: https://web.dev/articles/cls
- Nielsen Norman Group – *Indicators, Validations, and Notifications*: https://www.nngroup.com/articles/indicators-validations-notifications/ · *Error-Message Guidelines*: https://www.nngroup.com/articles/error-message-guidelines/
