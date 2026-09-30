# DỰ ÁN MẪU (PROJECT TEMPLATE) — TÀI LIỆU GIỚI THIỆU

> Phạm vi: `work-service` + màn hình tạo dự án ở `frontend`.
> Tài liệu triển khai chi tiết & CSDL: [2_TEMPLATE_IMPLEMENTATION_DB.md](2_TEMPLATE_IMPLEMENTATION_DB.md).

---

## 1. Bài toán

Mỗi khi khởi động một dự án mới, trưởng nhóm phải dựng lại cùng một bộ khung: trạng thái công việc, workflow chuyển trạng thái, cột board, loại công việc, phân quyền, danh sách sprint và bộ Epic/Story khởi đầu. Nhóm làm phần mềm thường lặp lại gần như y hệt cho mọi dự án web: *Phân tích → Thiết kế → Code → Review → Test → Done*, sprint 2 tuần, các Epic "Xác thực", "Thanh toán", "Triển khai"...

**Dự án mẫu** giải quyết việc đó: dựng bộ khung một lần, sau đó mỗi dự án mới chỉ cần *"chọn mẫu → đặt tên → chọn ngày bắt đầu"*.

**Ví dụ:** nhóm có mẫu **"Dự án phần mềm Scrum"** (6 trạng thái có *In Review*, *Testing*; 4 sprint × 14 ngày; 5 Epic với 30 Story). Khi nhận dự án **"Web bán hàng"** bắt đầu ngày 01/10/2026, trưởng nhóm chọn mẫu đó. Hệ thống sinh ra dự án `SHOP` có đủ 6 trạng thái, workflow, board, 4 sprint được dời lịch sang 01/10 → 26/11, cùng 35 công việc `SHOP-1..SHOP-35` đang ở trạng thái *To Do*, sẵn sàng lập kế hoạch.

---

## 2. Khái niệm

| Khái niệm | Định nghĩa |
|---|---|
| **Dự án mẫu (Template)** | Một `Project` bình thường có `is_template = TRUE`. Nó có đầy đủ hệ sinh thái như dự án thật (trạng thái, workflow, board, sprint, công việc...) nên được chỉnh sửa bằng đúng các màn hình sẵn có. |
| **Bản thiết kế (Blueprint)** | Ảnh chụp toàn bộ cấu hình và dữ liệu của một dự án tại một thời điểm, là thứ được sao chép. |
| **Tạo dự án từ mẫu** | Sao chép sâu blueprint của mẫu sang một dự án mới (`is_template = FALSE`), dời mọi mốc thời gian theo ngày bắt đầu mới và đặt lại tiến độ. |
| **Lưu thành mẫu** | Chiều ngược lại: sao chép một dự án đang chạy thành một mẫu mới. Dự án nguồn giữ nguyên. |
| **Nhân bản dự án** | Sao chép dự án sang dự án khác cùng loại (`clone`). |
| **Mốc gốc (anchor)** | Ngày bắt đầu của mẫu, hoặc ngày sớm nhất trong sprint/công việc/version nếu mẫu không có ngày bắt đầu. Mọi ngày trong mẫu được hiểu là *"cách mốc gốc N ngày"*. |
| **Kiểm tra toàn vẹn** | Bộ quy tắc xác nhận blueprint nhất quán (có trạng thái DONE, transition không trỏ ra ngoài dự án, sprint nằm trong hạn dự án...). Có hai mức: **ERROR** chặn thao tác, **WARNING** chỉ để lưu ý. |

Vì template **là** project, mọi quan hệ trong work-service (trạng thái tùy chỉnh, workflow, sprint, backlog, phân cấp Epic → Story → Subtask, liên kết phụ thuộc, nhãn, component, version, custom field, automation) tự nhiên có mặt trong mẫu mà không phải thiết kế thêm một mô hình dữ liệu song song.

---

## 3. Những gì được sao chép

| Thành phần | Sao chép | Cách xử lý khi tạo dự án từ mẫu |
|---|---|---|
| Trạng thái (kể cả trạng thái tùy chỉnh) | Luôn luôn | Tạo mới, giữ tên, nhóm (TODO/IN_PROGRESS/DONE), màu |
| Loại công việc (Epic, Story, Task, Bug, Subtask, loại tùy chỉnh) | Luôn luôn | Giữ cấp phân cấp (1 / 0 / -1) |
| Workflow + transition | Luôn luôn | Nối lại `from/to` sang trạng thái mới; cấu hình JSON (condition, validator, post-function) được thay UUID |
| Workflow scheme (loại → workflow) | Luôn luôn | Nối lại sang loại và workflow mới |
| Board, cột, WIP limit, ánh xạ cột ↔ trạng thái, swimlane | Luôn luôn | Tên board đổi theo tên dự án mới |
| Vai trò dự án + ma trận quyền | Luôn luôn | Giữ nguyên ma trận; **không** sao chép thành viên |
| Nhãn, component | Luôn luôn | Component lead bị bỏ trống (người đó có thể không thuộc dự án mới) |
| Custom field, automation rule | Luôn luôn | UUID trong cấu hình được thay sang bản mới |
| **Sprint** | Tùy chọn | Dời ngày theo mốc mới, **luôn về `FUTURE`**, giữ tên, mục tiêu và độ dài |
| **Công việc (Backlog + Sprint)** | Tùy chọn | Mã mới `KEY-1..n` theo đúng thứ tự cũ; giữ cây cha-con, sprint, thứ tự LexoRank, story point, ước lượng, nhãn, component, liên kết; ngày được dời |
| Version | Tùy chọn | Ngày phát hành được dời; `is_released = false` |
| Thành viên, bình luận, tệp đính kèm, changelog | **Không** | Là dữ liệu của dự án nguồn, không phải của bản thiết kế |

**Đặt lại tiến độ** (áp dụng cho mọi công việc được sao chép):

- trạng thái = trạng thái khởi tạo của workflow gắn với loại công việc (đích của transition *Create*), hoặc trạng thái TODO đầu tiên nếu workflow không có transition khởi tạo;
- xóa `resolution`, `resolved_at`, `assignee`; `time_spent = 0`; `remaining = original_estimate`;
- `reporter` = người tạo dự án; mỗi công việc có một dòng changelog *"Đã tạo công việc SHOP-2 từ TPL-2 của mẫu ..."*.

---

## 4. Ràng buộc bảo đảm cho mẫu

Mẫu chỉ dùng được khi blueprint của nó không vi phạm ràng buộc nào ở mức **ERROR**. Hệ thống kiểm tra ở bốn thời điểm:

1. khi đánh dấu một dự án là mẫu (`PUT /projects/{id}` với `isTemplate = true`),
2. khi mở modal Preview (hiển thị cho người dùng, nút *Dùng mẫu này* bị khóa nếu có lỗi),
3. trước khi tạo dự án từ mẫu (kiểm tra nguồn),
4. **sau khi** tạo xong, kiểm tra lại dự án đích. Nếu phát sinh lỗi thì rollback toàn bộ transaction.

| Nhóm | Quy tắc | Mức |
|---|---|---|
| **Trạng thái** | Có ít nhất một trạng thái nhóm TODO (điểm khởi đầu) và một nhóm DONE (Burndown/Velocity tính hoàn thành) | ERROR |
| | Không trùng tên trạng thái trong dự án | ERROR |
| | Không có trạng thái IN_PROGRESS | WARNING |
| **Workflow** | Dự án có workflow; transition và scheme chỉ trỏ tới trạng thái/loại/workflow của chính dự án | ERROR |
| | Trạng thái không có transition nào đi tới; workflow không có transition *Create*; loại chưa gắn workflow | WARNING |
| **Loại & phân cấp** | Có loại cấp 0; không trùng mã loại | ERROR |
| | Cây công việc đúng quy tắc 3 cấp: Epic không có cha, Story/Task/Bug chỉ có cha là Epic, Subtask phải có cha cấp 0 | ERROR |
| **Board** | Cột chỉ gom trạng thái của dự án; WIP min ≤ max | ERROR |
| | Trạng thái không nằm trên cột nào (thẻ sẽ bị ẩn khỏi board) | WARNING |
| **Thời gian** | Hạn dự án ≥ ngày bắt đầu; sprint có ngày kết thúc sau ngày bắt đầu; sprint ≤ 8 tuần (khớp luật `startSprint`); sprint không kết thúc sau hạn dự án; hạn công việc ≥ ngày bắt đầu công việc | ERROR |
| | Sprint bắt đầu trước dự án; hai sprint chồng lấn; công việc có hạn sau hạn dự án | WARNING |
| **Sprint** | Sprint đang ACTIVE/CLOSED trong mẫu (sẽ được đặt về FUTURE) | WARNING |
| | Dự án Kanban nhưng có sprint | WARNING |
| **Phân quyền** | Đủ 3 vai trò `ADMINISTRATOR`, `MEMBER`, `VIEWER` (cần cho việc đồng bộ thành viên nhóm) | ERROR |

Ngoài ra, **dự án mẫu không thể bắt đầu Sprint** (`SprintService.startSprint` từ chối). Mẫu chỉ là bản thiết kế, nên sprint của nó luôn ở trạng thái "Dự kiến".

### Thời gian được dời như thế nào

```
Mẫu:     mốc gốc 05/01 ── Sprint 1 [05/01 → 19/01] ── Sprint 2 [19/01 → 02/02] ── hạn 06/03
                                      │ offset = 01/10 − 05/01 = +269 ngày
Dự án:   bắt đầu 01/10 ── Sprint 1 [01/10 → 15/10] ── Sprint 2 [15/10 → 29/10] ── hạn 30/11
```

Mọi ngày (sprint, hạn công việc, ngày phát hành version, hạn dự án) cùng cộng một offset, nên **độ dài sprint và khoảng cách giữa các mốc được giữ nguyên**. Nếu người dùng tự nhập hạn chót sớm hơn mốc cuối của kế hoạch sau khi dời, việc tạo dự án bị từ chối với thông báo cụ thể (ví dụ *"Sprint 'Sprint 4' kết thúc sau hạn chót dự án"*). Form trên FE tự gợi ý hạn chót = ngày bắt đầu + độ dài timeline của mẫu.

---

## 5. Ai được làm gì

| Thao tác | Điều kiện |
|---|---|
| Xem thư viện mẫu, Preview | Là thành viên (bất kỳ vai trò, kể cả GUEST) của workspace chứa mẫu; hoặc là thành viên của chính mẫu; hoặc Global ADMIN |
| Tạo dự án từ mẫu | Dùng được mẫu, **và** có vai trò ≥ MEMBER trong workspace đích, **và** thỏa quy tắc tạo dự án hiện hành: trưởng nhóm (owner/LEADER) của nhóm được chọn, hoặc Global ADMIN (dự án không gắn nhóm chỉ ADMIN tạo được) |
| Lưu dự án thành mẫu | Có quyền `PROJECT_ADMIN` trên dự án nguồn **và** thỏa quy tắc tạo dự án với nhóm của mẫu |
| Nhân bản dự án | `PROJECT_ADMIN` trên dự án nguồn + quy tắc tạo dự án |
| Sửa mẫu | Như sửa một dự án thường (theo ma trận quyền của mẫu) |

Dự án sinh ra từ mẫu có thành viên ban đầu là: Project Lead và người tạo (ADMINISTRATOR), cộng thành viên nhóm (nếu gắn nhóm) theo luật sẵn có LEADER → ADMINISTRATOR, VIEWER → VIEWER, còn lại → MEMBER.

---

## 6. Trải nghiệm người dùng (Frontend)

### 6.1 Tạo dự án từ mẫu

1. Ở trang danh sách dự án, bấm **Tạo dự án**. Modal có hai tab: **Dự án trống** (luồng cũ) và **Chọn mẫu dự án**.
2. Tab *Chọn mẫu dự án* hiển thị **thư viện mẫu** của workspace dưới dạng lưới thẻ. Mỗi thẻ gồm phân loại (Phần mềm, Marketing...), mô hình (Scrum/Kanban), luồng trạng thái thu gọn, số loại công việc / sprint / công việc, độ dài timeline và số dự án đã dùng mẫu. Có ô tìm kiếm và bộ lọc theo phân loại.
3. **Bấm vào một thẻ để mở modal Preview**, gồm các tab:
   - *Tổng quan*: thống kê, biểu đồ dòng thời gian của sprint (Gantt thu nhỏ theo "Ngày N"), nhãn, component, version, trường tùy chỉnh;
   - *Trạng thái & Quy trình*: danh sách trạng thái theo nhóm màu, loại công việc theo cấp, từng workflow với các transition `Từ → Đến`;
   - *Board*: các cột, WIP limit, trạng thái gom trong mỗi cột, swimlane;
   - *Sprint & Công việc*: bảng sprint (bắt đầu tương đối, độ dài, số việc, story point) và cây Epic → Story → Subtask có thể mở/thu;
   - *Phân quyền*: vai trò và quyền của từng vai trò;
   - phía trên cùng là **băng kết quả kiểm tra toàn vẹn**: xanh (hợp lệ), đỏ (lỗi, khóa nút dùng mẫu), vàng (lưu ý, bấm để xem).
4. Bấm **Dùng mẫu này** để quay lại form. Form hiển thị thẻ mẫu đã chọn (có nút *Xem trước* và *Đổi mẫu*). Người dùng nhập tên, mã, nhóm, ngày bắt đầu; hạn chót được gợi ý tự động. Ba tùy chọn *Sprint*, *Công việc* và *Version* có thể bỏ chọn.
5. Bấm **Tạo dự án từ mẫu**. Toast báo số trạng thái, sprint và công việc đã sao chép, rồi điều hướng tới dự án mới.

### 6.2 Tạo và quản lý mẫu

- **Từ đầu:** tab *Dự án trống*, tích *"Đặt làm Dự án mẫu"*. Sau đó cấu hình mẫu như một dự án thường (thêm trạng thái, workflow, sprint, Epic...).
- **Từ dự án có sẵn:** trong trang dự án, mở menu **⋯ → Lưu thành dự án mẫu**, nhập tên, mã, phân loại và chọn có kèm sprint/công việc/version hay không. Modal hiển thị trước các lưu ý toàn vẹn của dự án nguồn.
- Dự án mẫu có huy hiệu **MẪU** cạnh mã dự án.

---

## 7. Ngoài phạm vi / hướng mở rộng

- **Mẫu hệ thống dùng chung giữa các workspace**: hiện mẫu thuộc về một workspace (`projects.workspace_id NOT NULL`). Có thể mở rộng bằng cột `template_visibility` (WORKSPACE / GLOBAL).
- **Gán người mặc định**: có thể thêm tùy chọn giữ người được giao nếu người đó là thành viên dự án mới.
- **Đồng bộ ngược**: dự án đã sinh ra không tự cập nhật khi mẫu thay đổi. `project_clones` đã lưu nguồn gốc để làm tính năng *"so sánh với mẫu"* về sau.
- **Hard delete mẫu**: `project_clones.source_project_id` đang `ON DELETE CASCADE`. Vì dự án chỉ bị xóa mềm nên lịch sử vẫn còn.
