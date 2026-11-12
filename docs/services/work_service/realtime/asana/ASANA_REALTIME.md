# Asana triển khai Realtime như thế nào: Reactive Query (LunaDb)

> Thuộc bộ nghiên cứu `docs/services/work_service/realtime/`. Slide đi kèm: [slide_asana_realtime.html](slide_asana_realtime.html).
>
> **Vì sao nghiên cứu Asana?** Asana đại diện cho trường phái thứ ba, khác cả Jira lẫn Linear: client **đăng ký một câu truy vấn**, server **tự đẩy kết quả mới** mỗi khi dữ liệu nền đổi.
>
> **Độ tin cậy nguồn:** blog kỹ thuật chính thức của Asana (2015, 2021, 2022). Asana **không công bố** giao thức truyền tải (WebSocket hay long-polling), cấu trúc cache phía client, hay cách xử lý optimistic write. Những phần đó **không được khẳng định** trong tài liệu này.

---

## 1. Kết luận nhanh

| Câu hỏi | Trả lời |
|---|---|
| Asana có realtime không? | **Có, và là "mặc định" của toàn app** từ 2015. |
| Ý tưởng | **Reactive query:** "Clients issue queries for graphs of related data and business logic calculations (similar to GraphQL), automatically subscribing to any updates to those query results." |
| Ai đẩy? | **LunaDb** (viết bằng Scala) "watches for changes in the underlying databases and pushes query updates down to all subscribed clients". |
| Phát hiện thay đổi DB bằng gì? | Gắn với **MySQL binlog**: "LunaDb is heavily tied to physical binlog files and offsets" (tức là CDC, Change Data Capture). |
| Ghi đi đường nào? | "Writes are also routed through LunaDb, executing in our Node-based LunaServer backend application framework." |
| Kafka/Redis? | Có **2 cache phân tán**: LunaDb và **LunaServer Redis**. Không nhắc Kafka trong luồng này. |

---

## 2. Cơ chế Reactive Query

```
 Client ──"đăng ký truy vấn: task của project P, kèm assignee, subtasks…"──▶ LunaDb
    ▲                                                                       │
    │  kết quả mới (push)                                                   │ ghi nhớ: truy vấn Q phụ thuộc
    │                                                                       │ những "index" nào
    └──────────────────────────────── LunaDb ◀── đọc binlog MySQL ──────────┘
                                         │ thấy index I đổi → các truy vấn dùng I bị vô hiệu
                                         ▼
                                  chạy lại / tính phần thay đổi → đẩy xuống client đăng ký
```

- So với GraphQL Subscriptions, Relay, Falcor: bài nói 2015 tự so sánh với Netflix Falcor và Facebook Relay/GraphQL.
- Để biết **truy vấn nào bị ảnh hưởng** bởi một lần ghi, Asana **phi chuẩn hoá** dữ liệu thành các **bảng index vật lý**: "We denormalize this data into physical index tables to make it easier for LunaDb to determine which cached data to invalidate." Hệ quả: "a write to a single object value can cascade to many physical indices".
- Dữ liệu được shard trên "well over a hundred physical MySQL instances" theo customer ID.
- Asana hầu như **không dùng read replica**, vì replica làm phức tạp các giao dịch tương tác với tính nhất quán cuối cùng (eventual consistency) của LunaDb.

---

## 3. Điểm mạnh và cái giá

| Ưu | Nhược |
|---|---|
| Lập trình viên frontend chỉ việc **khai báo dữ liệu cần**, realtime "tự có" cho mọi màn hình | Phải xây hẳn một **CSDL trung gian/cache** biết theo dõi phụ thuộc truy vấn (LunaDb) |
| Không phải viết tay "sự kiện X thì cập nhật màn hình Y" | Gắn chặt với binlog nên **nâng cấp DB khó**: "Low-downtime database engine upgrades are time-consuming right now" |
| Logic nghiệp vụ vẫn ở server (khác Linear) | Phải phi chuẩn hoá dữ liệu thành index để tính được việc vô hiệu hoá |

---

## 4. Phần nào trong project của Asana là realtime

Theo mô tả "app-wide reactive data-loading", **mọi màn hình đọc dữ liệu qua reactive query đều realtime**: danh sách task, board, chi tiết task, subtask, comment (story), trường tuỳ chỉnh. Asana không công bố danh sách ngoại lệ. Năm 2022 họ vẫn đang "bringing reactive data-loading to mobile clients", tức mobile chưa hoàn toàn reactive.

---

## 5. Bài học cho DATN: "reactive query của người nghèo"

DATN không thể xây LunaDb, nhưng có thể **mô phỏng tinh thần** của nó với chi phí rất nhỏ:

| Asana | DATN (đề xuất) |
|---|---|
| Client đăng ký **truy vấn** | Client đăng ký **topic theo project** (`/topic/projects/{id}`) |
| LunaDb biết truy vấn nào phụ thuộc index nào | Client biết màn hình nào phụ thuộc **loại sự kiện** nào (bảng ánh xạ trong `myapp/`) |
| Server chạy lại truy vấn và đẩy kết quả | Client **tự chạy lại API** của màn hình đó (refetch, gộp sự kiện, debounce) |
| Phát hiện thay đổi bằng binlog (CDC) | Phát hiện thay đổi **trong service** (domain event, `@TransactionalEventListener(AFTER_COMMIT)`). CDC kiểu Debezium là quá nặng cho đồ án |

Tức là: **"sự kiện mỏng + bản đồ màn hình ↔ loại sự kiện + refetch"**, cũng chính là kết luận rút ra từ Jira.

---

## Nguồn

- Asana – *Asana's Tech Stack* (28/02/2022): https://asana.com/inside-asana/asana-tech-stack
- Asana – *Asana tech talk: Reactive queries* (09/10/2015): https://asana.com/inside-asana/asana-tech-talk-reactive-queries
- Asana – *Scaling Scala* (02/03/2021): https://asana.com/inside-asana/scaling-scala
- Asana – *Database architecture: sharding & scaling*: https://asana.com/inside-asana/database-architecture-sharding-scaling
