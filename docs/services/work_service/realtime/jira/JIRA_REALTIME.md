# Jira Cloud triển khai "Realtime" như thế nào

> Thuộc bộ nghiên cứu `docs/services/work_service/realtime/`. Slide đi kèm: [slide_jira_realtime.html](slide_jira_realtime.html).
> Phạm vi: chỉ các màn hình **bên trong một project** (Board, Backlog, Issue view, Comment...). Không xét Workspace/Site.
>
> **Độ tin cậy nguồn:** phần kiến trúc lấy từ blog kỹ thuật chính thức của Atlassian (2019); phần hành vi Issue view lấy từ thông báo EAP chính thức (2023); phần "những gì chưa realtime" lấy từ tracker công khai `jira.atlassian.com` và diễn đàn cộng đồng. Chỗ nào là **suy luận** của người viết sẽ ghi rõ.

---

## 1. Kết luận nhanh

| Câu hỏi | Trả lời |
|---|---|
| Jira có realtime không? | **Có, nhưng "nửa vời" và có chủ đích.** Jira không đồng bộ dữ liệu như Google Docs. Nó dùng mô hình **"thông báo mỏng + client tự tải lại"** (event-driven invalidation). |
| Trước khi có realtime thì sao? | Board của Jira (Next-Gen) **polling mỗi 1 phút**: "we had to make a request for updated board data every minute from the front-end". Phần lớn các lần gọi không có gì mới → lãng phí tải. |
| Truyền tải bằng gì? | Backend dùng **event bus nội bộ StreamHub** (AWS Kinesis + SQS cho mỗi consumer). Đẩy xuống trình duyệt qua dịch vụ nền tảng **FPS – Frontend PubSub**, phía sau là nhà cung cấp **PubNub** (bên thứ ba, có thể thay). Atlassian **không công bố** là WebSocket hay SSE. |
| Sự kiện chứa gì? | **Chỉ chứa ID** (projectId, issueId, ai gây ra). **Không** chứa nội dung (summary, mô tả, comment). Client nhận xong tự gọi REST API để lấy dữ liệu mới → quyền được kiểm tra lại ở API. |
| Có Kafka/Redis không? | Bài viết không nhắc Kafka/Redis cho luồng này. Event bus là StreamHub (Kinesis). Trello (cùng công ty Atlassian) thì dùng Kafka – xem [../trello/TRELLO_REALTIME.md](../trello/TRELLO_REALTIME.md). |
| Có presence "ai đang xem" không? | **Không có sẵn** trong Jira Cloud. Đây vẫn là đề xuất chưa xử lý (JRACLOUD-80241); thị trường bù bằng app Marketplace ("Who's Looking", "Realtime Issue – Who's viewing"...). |
| Mô tả issue có cùng sửa đồng thời không? | **Không** như Confluence/Linear. Cộng đồng vẫn đề nghị "multiple editing at the same time". |

---

## 2. Bài toán gốc (2019)

Theo bài "Using an event-driven architecture to improve Jira Software responsiveness" (Rogerio Carvalho, Atlassian Engineering, 05/11/2019):

- Board/Backlog của Next-Gen Jira Software lấy cập nhật bằng **polling 1 phút/lần**, bất kể có thay đổi hay không.
- Hậu quả: cộng tác viên phải chờ tới ~1 phút mới thấy thay đổi, hoặc tự F5; rất nhiều request "rỗng".
- Mục tiêu đặt ra: cộng tác "trong vài giây, không phải vài phút".
- Điểm then chốt: Jira **đã sẵn** phát sự kiện issue/project cho các dịch vụ backend khác. Vấn đề chỉ còn là "a way to deliver the right events to the right front-end".

---

## 3. Kiến trúc JiRT (Jira Real-Time)

```
 Jira (monolith)                           Frontend (React)
     │  phát event issue/project               ▲
     ▼                                          │ <Realtime channels events onReceive>
 ┌──────────────┐   at-least-once   ┌──────┐   │
 │  StreamHub   │──────────────────▶│ JiRT │   │
 │ (Kinesis +   │  SQS riêng cho    │      │   │
 │  SQS/consumer)│  từng consumer   └──┬───┘   │
 └──────────────┘                      │ đẩy thông báo mỏng
                                       ▼        │
                              ┌──────────────────┴─┐
                              │ FPS – Frontend PubSub│── kiểm tra quyền khi SUBSCRIBE
                              │ (provider: PubNub)   │   (gọi Permissions service)
                              └──────────────────────┘
```

| Thành phần | Vai trò | Ghi chú |
|---|---|---|
| **StreamHub** | Event bus nội bộ cho giao tiếp service-to-service theo EDA. "guarantees at-least-once delivery of events". | Bên trong dùng AWS Kinesis; mỗi consumer tự có một hàng đợi SQS. JiRT chỉ là **một consumer mới** → không phải sửa Jira hay StreamHub. |
| **JiRT** | Đọc sự kiện từ StreamHub, trích project/board/issue, đẩy thông báo vào kênh của client. | Là service mới, mỏng. |
| **FPS** (Frontend PubSub) | Dịch vụ nền tảng sẵn có: (1) che nhà cung cấp truyền tải ("We are currently using PubNub for this"); (2) **kiểm tra quyền lúc đăng ký kênh** qua Permissions service. | Tài nguyên được đăng ký là "a Jira project or board". |
| **`<Realtime>`** (React) | Component bọc client FPS, tải bất đồng bộ để không phình bundle. Props: `channels`, `events`, `onJoin`, `onReceive`, `onLeave`. | Các team Issue view, Roadmap cũng dùng lại. |

### 3.1 Định danh kênh và loại sự kiện

- **ARI** (Atlassian Resource Identifier) – định danh tài nguyên = **tên kênh**:
  `ari:cloud:<resource_owner>:<cloud_id>:<resource_type>/<resource_id>` (ví dụ ARI của một project).
- **AVI** (Atlassian Event Identifier) – loại sự kiện client muốn nghe:
  `avi:jira:created:issue`, `avi:jira:transitioned:issue`, `avi:jira:deleted:project`...
- Client tự chọn AVI muốn nhận → frontend đổi danh sách sự kiện **không cần sửa backend**.

> Ánh xạ sang DATN: ARI ≈ `/topic/projects/{projectId}`; AVI ≈ trường `type` trong phong bì sự kiện (`ITEM_MOVED`, `COMMENT_CREATED`...).

### 3.2 Payload "mỏng" (thin event)

```json
{
  "channels": ["ari:cloud:jira:<cloudId>:project/10001"],
  "type": "avi:jira:transitioned:issue",
  "payload": { "projectId": 10001, "issueId": 20042, "atlassianId": "<user-gây-ra>" }
}
```

Lý do chọn payload mỏng (nguyên văn ý trong bài): quyền có thể **thay đổi giữa lúc đăng ký kênh và lúc nhận sự kiện**, nên sự kiện **không chứa "user-generated content"**. Khi cần nội dung, client gọi API bình thường – nơi quyền của người đang đăng nhập được kiểm tra lại.

`atlassianId` dùng để client **bỏ qua sự kiện do chính mình gây ra**.

---

## 4. Client xử lý sự kiện thế nào (phần đáng học nhất)

| Kỹ thuật | Mô tả | Vì sao |
|---|---|---|
| **Bỏ qua sự kiện của chính mình** | So `atlassianId` với user hiện tại. | Tránh tải lại thừa ngay sau khi chính mình kéo thẻ. (Bài viết thừa nhận: user mở 2 tab sẽ cần thêm tracing header để phân biệt.) |
| **Đệm khi đang thao tác** | Khi có request API đang chạy **hoặc đang kéo thả**, sự kiện được giữ lại, phát sau. | Không làm "giật" thẻ dưới tay người dùng. |
| **Xử lý từng cái một** | Sự kiện đệm được xử lý tuần tự; sự kiện đến **trong lúc đang xử lý thì bị bỏ** (vì lần tải lại kế tiếp sẽ bao luôn). | Bảo đảm chỉ **một** request làm mới chạy tại một thời điểm. |
| **Chọn làm mới một phần hay toàn bộ** | Payload đủ để client quyết định: `created:issue` có thể chỉ tải 1 issue; sự kiện khác có thể tải lại toàn bộ. | Linh hoạt. |
| **Board/Backlog: luôn tải lại toàn bộ** | Team Next-Gen chọn cách đơn giản: **mọi** sự kiện → đánh dấu board cũ → tải lại. | Board phụ thuộc nhiều quy tắc (cột, swimlane, filter) → vá cục bộ dễ sai; tải lại = đúng tuyệt đối. |
| **Issue view cần ít rào chắn hơn** | Phạm vi chỉ một issue. | Ít sự kiện, ít va chạm. |

> **Bài học trực tiếp cho DATN:** cơ chế "đệm khi kéo thả + gộp nhiều sự kiện thành một lần refetch" chính là thứ `useBoardData.reload({ silent: true })` hiện có đang thiếu.

---

## 5. Issue view realtime (2023 → GA)

Theo thông báo EAP chính thức trên Atlassian Developer Community (04/05/2023):

- "Issue view real time updates" trước đó **đã chạy cho các site không cài app Connect/Forge**; tới 2023 mở cho mọi site.
- Mỗi khi issue view được cập nhật, Jira phát sự kiện phía client `JIRA_ISSUE_CHANGED` để app Forge/Connect cập nhật UI của chúng:
  `events.on('JIRA_ISSUE_CHANGED', (data) => {...})`.
- Đây là **sự kiện thuần client-side** ("purely a client-side feature") phân phối qua event bus của trang (`AP.events` / `@forge/bridge`), **không phải webhook**. Webhook/backend event là hệ thống khác.
- Giới hạn được nêu: khi **chính người đang xem** sửa issue thì view **không** được làm mới (giả định người đó đã thấy thay đổi).
- Atlassian đóng EAP 31/05/2023 và chuyển sang GA (không công bố ngày cụ thể).

---

## 6. Những gì Jira **chưa** realtime (bằng chứng công khai)

| Phần | Tình trạng | Nguồn |
|---|---|---|
| Backlog tự làm mới khi người khác sửa | Đề xuất **JRACLOUD-87370** "Add automatic refresh on backlog update" (tạo 06/02/2017), trạng thái *Gathering Interest*, 90 vote. Người dùng mô tả: "on every backlog update all of them must reload their browsers". Liên kết với JRACLOUD-85662 (board tự làm mới). | jira.atlassian.com |
| Comment mới hiện ngay | Cộng đồng báo comment mới **không xuất hiện** tới khi tải lại trang; có yêu cầu tính năng JSWCLOUD-12807. | community.atlassian.com |
| Presence "ai đang xem/sửa issue" | Đề xuất **JRACLOUD-80241** chưa giải quyết. Confluence đã có avatar người xem, Jira chưa. Bù bằng app Marketplace. | jira.atlassian.com, marketplace |
| Sửa mô tả đồng thời | Không có co-editing kiểu Confluence; cộng đồng vẫn yêu cầu. | community.atlassian.com |

> Lưu ý: Jira Cloud cập nhật liên tục, một số mục có thể đã thay đổi sau thời điểm các nguồn trên. Khi bảo vệ đồ án, nên trình bày là "theo nguồn công khai tại thời điểm nghiên cứu (11/2026)".

---

## 7. Bảng tổng hợp: phần nào trong project của Jira có realtime

| Màn hình / chức năng trong project | Jira | Cơ chế |
|---|---|---|
| Board (kéo thẻ, đổi trạng thái, thẻ mới) | ✅ (Next-Gen/team-managed, từ 2019) | Thin event → **tải lại toàn board**, có đệm khi kéo |
| Backlog (xếp hạng, thêm vào sprint) | ⚠️ Không nhất quán – vẫn có đề xuất mở từ 2017 | Polling / F5 |
| Issue view – các trường | ✅ (EAP 2023 → GA) | Cập nhật nền + sự kiện `JIRA_ISSUE_CHANGED` cho app |
| Issue view – comment | ⚠️ Theo báo cáo người dùng: chưa tự hiện | — |
| Presence (ai đang xem) | ❌ | App bên thứ ba |
| Co-edit mô tả | ❌ | — |
| Báo cáo (Burndown, Velocity) | ❌ (tính khi mở trang) | — |
| Thông báo (chuông) | ✅ nhưng là hệ thống riêng (user-scoped), ngoài phạm vi project | — |

---

## 8. Bài học áp dụng cho DATN

1. **Không cần "đồng bộ dữ liệu" kiểu Linear** để đạt trải nghiệm tốt: Jira – sản phẩm lớn nhất thị trường – chỉ dùng thông báo mỏng + refetch cho Board.
2. **Tách 2 tầng**: event bus backend (StreamHub ≈ Kafka/Outbox của ta) và tầng đẩy xuống trình duyệt (FPS ≈ STOMP/WebSocket). Tầng đẩy chỉ là một consumer.
3. **Kiểm tra quyền khi SUBSCRIBE** + **payload không chứa nội dung nhạy cảm** + **lấy nội dung qua API có kiểm quyền**.
4. Phía client: **bỏ qua sự kiện của chính mình, đệm khi đang kéo/đang gọi API, gộp sự kiện, một refetch tại một thời điểm**.
5. Chấp nhận **at-least-once / có thể mất**: vì client luôn refetch từ nguồn sự thật nên trùng hay mất một sự kiện không làm sai dữ liệu – chỉ làm chậm.

---

## Nguồn

- Atlassian Engineering – *Using an event-driven architecture to improve Jira Software responsiveness* (05/11/2019): https://www.atlassian.com/blog/atlassian-engineering/using-an-event-driven-architecture-to-improve-jira-software-responsiveness
- Atlassian Developer Community – *Real time issue events (EAP)* (04/05/2023): https://community.developer.atlassian.com/t/real-time-issue-events-eap/69066
- JRACLOUD-87370 – *Add automatic refresh on backlog update*: https://jira.atlassian.com/browse/JRACLOUD-87370
- JRACLOUD-80241 – chỉ báo ai đang xem/sửa issue: https://jira.atlassian.com/browse/JRACLOUD-80241
- Cộng đồng – *Real-time comment updates in Jira Cloud issue view*: https://community.atlassian.com/forums/Jira-questions/Real-time-comment-updates-in-Jira-Cloud-issue-view/qaq-p/3192757
- Cộng đồng – *Jira Description Real-time collaborative editing*: https://community.atlassian.com/forums/Jira-questions/Jira-Description-Real-time-collaborative-editing/qaa-p/2122725
- Marketplace – Realtime Issue (Who's viewing): https://marketplace.atlassian.com/apps/1235818
