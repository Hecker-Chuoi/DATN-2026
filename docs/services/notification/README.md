# Thông báo (Notification): Nghiên cứu & Kế hoạch

Thư mục này trả lời hai câu hỏi:
1. Trong một Không gian làm việc và Dự án, **những trường hợp nào cần thông báo**, gửi cho ai, mức độ nào, dẫn tới đâu?
2. **Dùng công nghệ gì** (Kafka, WebSocket, Redis…) và triển khai ra sao để có chuông thông báo nhiều loại, có đường dẫn, có "Đánh dấu đã đọc" và "Xoá thông báo"?

Đối chiếu source code nhánh `develop` tới commit `f303aa9` (14/11/2026). Trạng thái: **đã triển khai** đủ 53 loại, chuông, trang Thông báo, WebSocket, email và job nhắc hạn; còn thiếu cài đặt bật / tắt từng loại và Redis cho nhiều instance. Chi tiết: [3_TRIEN_KHAI_VA_KIEM_THU.md](3_TRIEN_KHAI_VA_KIEM_THU.md).

## Cấu trúc

| Tài liệu | Nội dung |
|---|---|
| [1_DANH_MUC_LOAI_THONG_BAO.md](1_DANH_MUC_LOAI_THONG_BAO.md) | **53 loại thông báo** trong 9 nhóm: khi nào gửi, điểm gắn trong code, người nhận, mức độ, nội dung mẫu, đường dẫn, kênh, độ ưu tiên. Quy tắc chung (R1–R12) và danh sách thứ **không** đưa vào chuông |
| [2_KIEN_TRUC_VA_THIET_KE.md](2_KIEN_TRUC_VA_THIET_KE.md) | Hiện trạng, so sánh công nghệ, kiến trúc Outbox → Kafka → notification-service → WebSocket, hợp đồng sự kiện, bảng `notifications`, API, giao diện chuông, job theo lịch, bảo mật, lộ trình N1–N5, kịch bản kiểm thử, rủi ro |
| [3_TRIEN_KHAI_VA_KIEM_THU.md](3_TRIEN_KHAI_VA_KIEM_THU.md) | **Phần đã làm thật**: đối chiếu thiết kế, luồng chạy, bản đồ mã nguồn, cấu hình, API / WebSocket, chạy ở máy dev, kết quả kiểm thử, cách thêm loại thông báo mới, lưu ý vận hành |
| [danh_muc_thong_bao.html](danh_muc_thong_bao.html) | **Bản tương tác** của danh mục: lọc theo nhóm / mức độ / ưu tiên / người nhận / nguồn, tìm kiếm, xem dạng thẻ hoặc bảng, và **xem trước chuông thông báo** dùng thử được Đánh dấu đã đọc, Xoá, Hoàn tác, mở đường dẫn |

**Xem bản HTML:** mở trực tiếp bằng trình duyệt; tệp tự chứa, không cần mạng. Phím `T` đổi sáng/tối, phím `/` nhảy tới ô tìm kiếm.

## Trả lời ngắn

**Cần thông báo những gì?** 15 loại P0 nên làm trước:

| Nhóm | Loại P0 |
|---|---|
| Công việc | Được giao việc · Công việc đổi trạng thái |
| Xác nhận hoàn thành | Có yêu cầu chờ xác nhận (gửi Quản lý, **Cần xử lý**) · Được xác nhận · Bị từ chối |
| Bình luận | Được nhắc tên · Có bình luận mới |
| Chu kỳ / Giai đoạn | Bắt đầu · Hoàn tất · **Quá hạn** (gửi Quản lý) |
| Nhắc hạn | Sắp đến hạn · Đến hạn hôm nay · **Quá hạn** |
| Thành viên | Được mời vào dự án · Bị xoá khỏi dự án |

**Dùng công nghệ gì?** Ba thứ ở ba tầng, không phải chọn một:

| Tầng | Chọn |
|---|---|
| Vận chuyển giữa service | **Kafka qua Transactional Outbox** (`OutboxRelay` ở work-service, consumer ở notification-service) |
| Lưu trữ | **PostgreSQL**, bảng `notifications` trong notification-service |
| Đẩy xuống trình duyệt | **WebSocket + STOMP**, kênh `/user/queue/notifications` |
| Phụ trợ | **Redis**: backplane khi chạy nhiều instance, khoá cho job (chưa làm, hiện chạy một instance) |

**Ba yêu cầu của chuông:**
- *Nhiều loại:* `severity` (Thông thường / Tích cực / Cảnh báo / Khẩn cấp) + `category` (9 nhóm) + cờ **Cần xử lý**.
- *Có đường dẫn:* trường `link`; công việc dùng route mới `/browse/{mã}` để mở thẳng chi tiết công việc.
- *Đánh dấu đã đọc / Xoá:* `PATCH /{id} {read}`, `POST /read-all`, `DELETE /{id}` (xoá mềm, có Hoàn tác), đồng bộ giữa các tab qua WebSocket.

## Liên quan

- [realtime/myapp/KE_HOACH_REALTIME_WORK_SERVICE.md](../work_service/realtime/myapp/KE_HOACH_REALTIME_WORK_SERVICE.md): realtime cập nhật màn hình đang mở. Giai đoạn R3 "Sự kiện bền" của tài liệu đó chính là phần Outbox → Kafka ở đây.
- [8_QUY_TAC_GIAO_VIEC_VA_XAC_NHAN_HOAN_THANH.md](../work_service/8_QUY_TAC_GIAO_VIEC_VA_XAC_NHAN_HOAN_THANH.md): luồng "Tạm hoàn thành" (BR-W03…W08); §8 của tài liệu đó ghi chưa có thông báo cho Admin.
- [language/](../language/): thuật ngữ tiếng Việt dùng trong nội dung thông báo.
