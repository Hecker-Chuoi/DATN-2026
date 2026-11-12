# Realtime trong work-service: Nghiên cứu & Kế hoạch

Thư mục này trả lời ba câu hỏi:
1. Các app quản lý công việc (Jira, Linear, Trello, Asana) **có làm realtime không, làm thế nào**, chỉ xét các màn hình **bên trong một project**.
2. "Realtime" khác gì "Kafka" và "Redis"? Nên dùng cái nào?
3. DATN-2026 nên triển khai realtime cho `work-service` **ra sao, ở đâu, theo thứ tự nào**.

## Cấu trúc

| Thư mục | Tài liệu | Slide |
|---|---|---|
| `jira/` | [JIRA_REALTIME.md](jira/JIRA_REALTIME.md): thông báo mỏng + refetch (JiRT, StreamHub, Frontend PubSub) | [slide_jira_realtime.html](jira/slide_jira_realtime.html) |
| `linear/` | [LINEAR_REALTIME.md](linear/LINEAR_REALTIME.md): local-first sync engine (IndexedDB, Transaction, Delta, lastSyncId) | [slide_linear_realtime.html](linear/slide_linear_realtime.html) |
| `trello/` | [TRELLO_REALTIME.md](trello/TRELLO_REALTIME.md): WebSocket + backplane Redis → RabbitMQ → Kafka | [slide_trello_realtime.html](trello/slide_trello_realtime.html) |
| `asana/` | [ASANA_REALTIME.md](asana/ASANA_REALTIME.md): reactive query (LunaDb, MySQL binlog) | [slide_asana_realtime.html](asana/slide_asana_realtime.html) |
| `myapp/` | [KE_HOACH_REALTIME_WORK_SERVICE.md](myapp/KE_HOACH_REALTIME_WORK_SERVICE.md): **kế hoạch cho DATN**: kiến trúc, UX & chống xung đột (§6), lộ trình R0–R3 | [slide_ke_hoach_realtime.html](myapp/slide_ke_hoach_realtime.html) (23 slide, có 6 ví dụ tương tác; `demo.js`/`demo.css` đi kèm) |
| `_assets/` | `slide.css`, `slide.js` dùng chung cho mọi slide (không phụ thuộc CDN) | — |

**Xem slide:** mở file `.html` bằng trình duyệt (giữ nguyên cấu trúc thư mục vì slide dùng chung `_assets/`).
Phím: `←` `→` chuyển slide · `F` toàn màn hình · `T` sáng/tối · thêm `?all` vào URL để xem hoặc in tất cả slide liền nhau.

## So sánh nhanh

| | Jira | Linear | Trello | Asana | **DATN (đề xuất)** |
|---|---|---|---|---|---|
| Trường phái | Thông báo mỏng + refetch | Bản sao CSDL ở client, đồng bộ delta | Đẩy delta theo model | Reactive query | **Thông báo mỏng (+ patch nhỏ) + refetch** |
| Truyền tải xuống trình duyệt | Frontend PubSub (PubNub) | WebSocket | WebSocket | Không công bố | **WebSocket + STOMP** `/ws-work` |
| Bus/backplane phía server | StreamHub (Kinesis + SQS) | Không công bố | Redis → RabbitMQ → **Kafka** | LunaDb đọc binlog MySQL | Không cần (1 instance) → **Redis Pub/Sub** (nhiều instance) |
| Thứ tự / phát hiện mất | at-least-once, client refetch | `lastSyncId` toàn cục | Shard theo model id | — | **`seq` theo project** + refetch |
| Kiểm quyền | Khi subscribe (FPS) + API | Sync group | Khi subscribe | — | **Khi SUBSCRIBE** (`PROJECT_VIEW`) + API |
| Presence | ❌ (app ngoài) | ✅ | — | — | R2 |
| Co-edit mô tả | ❌ | ✅ | ❌ | — | Không (version + banner) |

## Trả lời ngắn: "Realtime hay Kafka hay Redis?"

Ba thứ này nằm ở **hai tầng khác nhau**, không loại trừ nhau:

- **Tầng đẩy xuống trình duyệt** (bắt buộc nếu muốn "realtime"): WebSocket/STOMP hoặc SSE.
- **Tầng nối giữa các server** (chỉ cần khi chạy nhiều instance): Redis Pub/Sub (nhẹ) hoặc Kafka/RabbitMQ (bền).
- **Kafka** trong DATN phù hợp cho **sự kiện tích hợp bền** (thông báo, automation, AI) qua Transactional Outbox, **không** dùng để vẽ lại UI.

Chi tiết: [trello/TRELLO_REALTIME.md §6](trello/TRELLO_REALTIME.md) và [myapp/KE_HOACH_REALTIME_WORK_SERVICE.md §3](myapp/KE_HOACH_REALTIME_WORK_SERVICE.md).

## Ghi chú về nguồn

- Jira, Trello, Asana: blog kỹ thuật **chính thức** (Atlassian 2018, 2019; Asana 2015–2022) và tracker công khai của Atlassian.
- Linear: bản **dịch ngược** của bên thứ ba (wzhudev), được CTO Linear công khai xác nhận là chính xác. Phía server của Linear không được công bố.
- Các nguồn đều có ngày đăng; kiến trúc hiện tại của các app có thể đã khác. Tài liệu được lập 11/2026.
