# Trello triển khai Realtime như thế nào: Redis Pub/Sub → RabbitMQ → Kafka

> Thuộc bộ nghiên cứu `docs/services/work_service/realtime/`. Slide đi kèm: [slide_trello_realtime.html](slide_trello_realtime.html).
>
> **Vì sao nghiên cứu Trello?** Trello (thuộc Atlassian) là app Kanban "realtime" kinh điển: kéo thẻ là người khác thấy ngay. Quan trọng hơn, Trello đã **đi qua đúng ba công nghệ mà bạn đang phân vân: Redis, hàng đợi tin nhắn, Kafka**, và công bố lý do.
>
> **Độ tin cậy nguồn:** bài chính thức của Atlassian "Why we chose Kafka for the Trello socket architecture" (Sebastian Mayr, 29/03/2018). Đây là ảnh chụp năm 2018, kiến trúc hiện tại có thể đã khác.

---

## 1. Kết luận nhanh

| Câu hỏi | Trả lời |
|---|---|
| Trello có realtime không? | **Có.** Mọi thay đổi trên board được đẩy qua **WebSocket** tới những ai đang mở board đó. |
| Giao thức socket? | Cực kỳ tối giản: "the only commands we support are subscribing and unsubscribing to a channel". Mỗi lệnh subscribe gồm **loại model** (board, member, organization, card) và **id model**. |
| Kafka/Redis dùng để làm gì? | Là **backplane** (xương sống phân phối) giữa **app server** (nơi ghi dữ liệu) và **các socket server** (nơi giữ kết nối trình duyệt). Không phải thứ trình duyệt nói chuyện trực tiếp. |
| Lịch sử | **Redis Pub/Sub** → **RabbitMQ** (~3 năm, 15 instance) → **Kafka** (2018). |
| Vì sao đổi? | RabbitMQ xử lý **network partition** rất tệ ("from split-brain to complete cluster failure"). Kafka: bộ nhớ giảm ~33%, CPU tăng ~2x, chi phí giảm ~5x, ít sự cố hơn hẳn. |
| Quy mô lúc đó | "2k+ msgs/s + growth". |

---

## 2. Bức tranh kiến trúc

```
                      (ghi dữ liệu)                 (giữ kết nối WebSocket)
 Trình duyệt ──REST──▶ Trello Server ──publish──▶  BACKPLANE  ──▶ Socket server ──WS──▶ Trình duyệt
                                                   (Redis →        (subscribe board/card
                                                    RabbitMQ →      sau khi kiểm quyền)
                                                    Kafka)
```

Điểm cốt lõi: **app server và socket server tách nhau**. App server không cần biết ai đang online; nó chỉ "publish delta của model X". Socket server biết ai đang nghe model nào và chuyển tiếp.

> Ánh xạ sang DATN: app server ≈ `work-service` (các `*ServiceImpl`), socket server ≈ endpoint STOMP `/ws-work`, backplane ≈ **Redis Pub/Sub** (đã có Redis :6380) khi chạy nhiều instance.

---

## 3. Ba thế hệ backplane

### 3.1 Redis Pub/Sub (thế hệ đầu)

- Đơn giản, nhanh, nhưng là **fire-and-forget**: subscriber offline đúng lúc publish thì **mất luôn** tin nhắn, không có lưu trữ hay phát lại.
- Bài viết không nêu chi tiết vì sao bỏ Redis Pub/Sub. Khi so sánh để chọn thay RabbitMQ, **Redis Streams** bị loại vì lúc đó (12/2017) "only available in the unstable branch / unreleased" và không có failover.

### 3.2 RabbitMQ (~3 năm)

- "Our server machines publish messages to the cluster and then our websocket instances pull messages from the queue."
- Cụm **rabbitmq-inbound** (3 node) nhận tin từ app server, rồi plugin *shovel* chia **16 queue** sang **4 cụm rabbitmq-outbound** (mỗi cụm 3 node), tổng **15 instance**.
- Tin nhắn **shard theo model id** qua 16 shard ("sharded by their model id over 16 shards"), nên thứ tự được giữ trong cùng một model.
- Mỗi socket instance chạy 8 process; mỗi process tạo một queue tạm. Khi client gửi lệnh subscribe, process **tạo binding** cho subscription đó vào exchange chung.
- **Vấn đề:**
  - Network partition gây split-brain hoặc sập cả cụm. Khi cụm hỏng, "we have to drop all sockets and force the web clients to reconnect".
  - Khi split-brain, client "may have missed a message but received a later one and has no way of knowing", tức **mất tin mà không biết mình đã mất**.
  - **Reconnect storm:** hàng loạt client kết nối lại kéo theo hàng loạt lệnh tạo/xoá binding, làm RabbitMQ treo. Thêm **jitter** (độ trễ ngẫu nhiên) giúp được phần nào nhưng không giải quyết được partition.
  - Riêng tháng trước khi chuyển đổi đã có **4 sự cố**.

### 3.3 Kafka (2018)

- Socket server chia hai vai:
  - **master process:** "subscribed to the whole topic and receives all delta updates and does its filtering locally", tức đọc **toàn bộ** topic rồi tự lọc.
  - **client process:** giữ socket của người dùng và xác thực. Nhận lệnh subscribe thì **kiểm tra quyền** rồi báo master; master lưu model id vào map và chỉ chuyển delta của model đó.
- Kafka đáp ứng yêu cầu "In-Order message delivery (per shard)".
- **Kết quả:** bộ nhớ giảm ~33%, CPU tăng ~2x (vì lọc cục bộ), chi phí giảm ~5x ("A lot less!"), có thêm metric consumer lag. Tháng đầu chỉ có 1 sự cố nhỏ (giới hạn file descriptor khi nâng cấp).

---

## 4. Bảo mật và kiểm tra quyền

- "When a client receives a subscribe request, it checks permissions and then forwards the request to the master."
- Quyền được kiểm **tại thời điểm subscribe**, giống Jira (FPS) và Linear (sync group).

---

## 5. Phần nào trong một board của Trello là realtime

| Chức năng | Trello | Ghi chú |
|---|---|---|
| Kéo thẻ giữa list, sắp xếp | ✅ | Kênh model `board` |
| Sửa tiêu đề/nhãn/thành viên của thẻ | ✅ | Kênh model `card` / `board` |
| Comment, checklist trong thẻ | ✅ | Thuộc model `card` |
| Thành viên board thay đổi | ✅ | Kênh model `member` / `organization` |
| Phát lại khi mất kết nối | Kết nối lại rồi **tải lại** phần bị lỡ | Bài viết không mô tả replay theo offset Kafka |

---

## 6. Trả lời câu hỏi "Realtime hay Kafka hay Redis?"

Ba thứ này **không thay thế nhau**, chúng nằm ở **hai tầng khác nhau**:

| Tầng | Câu hỏi nó trả lời | Công nghệ |
|---|---|---|
| **Tầng đẩy xuống trình duyệt** | Làm sao server "gõ cửa" trình duyệt? | **WebSocket** (STOMP, Socket.IO), **SSE**, hoặc dịch vụ như PubNub |
| **Tầng backplane giữa các server** | Server A ghi dữ liệu, làm sao server B (đang giữ socket) biết? | **Redis Pub/Sub** (nhẹ, mất thì thôi), **RabbitMQ**, **Kafka** (bền, có thứ tự, phát lại được) |

- **Chỉ có 1 instance work-service** thì **không cần backplane**: service gọi thẳng `SimpMessagingTemplate` là đủ.
- **Nhiều instance** thì cần backplane. Ở quy mô DATN, **Redis Pub/Sub là đủ** (đã có Redis). Trello chỉ cần Kafka khi đạt hàng nghìn tin/giây và cần chịu được partition.
- **Kafka** trong DATN hợp hơn cho **sự kiện tích hợp bền vững** (thông báo in-app, automation, AI, báo cáo), tức những consumer **không được phép mất sự kiện**, chứ không phải để vẽ lại board.

---

## 7. Bài học cho DATN

1. **Tách "nơi ghi" và "nơi giữ socket"** bằng một interface (`RealtimeBroadcaster`): hôm nay gửi thẳng, mai đổi sang Redis Pub/Sub mà không sửa service nghiệp vụ.
2. **Kênh theo model**: Trello theo board/card, DATN theo `project` (và lọc theo `itemId` ở client).
3. **Giao thức tối giản:** chỉ SUBSCRIBE/UNSUBSCRIBE, kiểm quyền khi SUBSCRIBE.
4. **Shard hoặc khoá theo id để giữ thứ tự:** nếu sau này đẩy qua Kafka, dùng `projectId` làm message key.
5. **Kết nối lại phải có jitter** (backoff ngẫu nhiên) để tránh reconnect storm, và **kết nối lại xong thì refetch** vì không có gì bảo đảm không lỡ tin.
6. **Mất tin mà không biết** là rủi ro có thật, nên cần **số thứ tự (seq)** để client tự phát hiện (ý tưởng mượn từ Linear).

---

## Nguồn

- Atlassian – *Why we chose Kafka for the Trello socket architecture* (Sebastian Mayr, 29/03/2018): https://www.atlassian.com/blog/how-we-build/why-we-chose-kafka
- Arpit Bhayani – *Trello's Migration from RabbitMQ to Kafka for Real-time WebSocket Updates* (tóm tắt thứ cấp): https://arpitbhayani.me/videos/trello-rabbitmq-kafka-migration-websockets/
