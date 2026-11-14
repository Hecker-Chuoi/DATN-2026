# Danh mục loại thông báo (DATN-2026)

> Thuộc bộ tài liệu `docs/services/notification/`. Bản tương tác (lọc, tìm kiếm, xem trước chuông thông báo): [danh_muc_thong_bao.html](danh_muc_thong_bao.html).
> Kiến trúc, dữ liệu, API và lộ trình: [2_KIEN_TRUC_VA_THIET_KE.md](2_KIEN_TRUC_VA_THIET_KE.md).
> Phạm vi: thông báo **gửi riêng cho từng người** (chuông trong ứng dụng + email) phát sinh trong Không gian làm việc và Dự án. Khác với realtime trong [KE_HOACH_REALTIME_WORK_SERVICE.md](../work_service/realtime/myapp/KE_HOACH_REALTIME_WORK_SERVICE.md): realtime cập nhật màn hình đang mở và không lưu lại.
> Đối chiếu source code nhánh `develop` tới commit `f303aa9` (14/11/2026). Trạng thái: **đã triển khai** (trừ cài đặt bật / tắt từng loại và Redis), xem [3_TRIEN_KHAI_VA_KIEM_THU.md](3_TRIEN_KHAI_VA_KIEM_THU.md).
> Ví dụ nội dung dùng dữ liệu kịch bản demo [Mộc Coffee](../work_service/DEMO/1_KICH_BAN_DEMO_QUAN_CAFE_MOC_COFFEE.md): An là Trưởng dự án, Bình là Thành viên, Chi là Người xem, mã dự án `MOC`.

---

## 0. Tóm tắt

| Chỉ số | Giá trị |
|---|---|
| Tổng số loại | **53**, chia 9 nhóm (A–I) |
| Theo độ ưu tiên triển khai | **15 P0** (bắt buộc cho bản demo) · 22 P1 · 16 P2 |
| Theo nguồn phát sinh | 41 từ **sự kiện nghiệp vụ** · 12 từ **job chạy theo lịch** |
| Theo mức độ | 30 Thông thường · 4 Tích cực · 14 Cảnh báo · 5 Khẩn cấp |
| Có cờ **Cần xử lý** | 6 loại: C1, C4, E3, E6, G1, G8 |
| Gửi kèm email | 7 loại: A1, C1, D1, F1, F3, G1, G8 (F4 tuỳ chọn) |

**15 loại P0:** A1 Được giao việc · A3 Đổi trạng thái · C1 Yêu cầu xác nhận hoàn thành · C2 Được xác nhận · C3 Bị từ chối · D1 Được nhắc tên · D2 Bình luận mới · E1 Chu kỳ bắt đầu · E2 Chu kỳ hoàn tất · E3 Chu kỳ quá hạn · F1 Sắp đến hạn · F2 Đến hạn hôm nay · F3 Quá hạn · G1 Được mời vào dự án · G2 Bị xoá khỏi dự án.

---

## 1. Quy ước

### 1.1 Người nhận

| Ký hiệu | Vai trò | Lấy từ đâu |
|---|---|---|
| **NTH** | Người thực hiện | `work_items.assignee_id` |
| **NT** | Người tạo (người báo cáo) | `work_items.reporter_id` |
| **QL** | Quản lý dự án: Trưởng dự án, người tạo dự án và các vai trò có quyền `PROJECT_ADMIN` (mặc định là Quản trị viên) | `projects.lead_user_id`, `projects.created_by`, `role_assignments` + `permission_scheme_entries`. Cùng tập người với `WorkItemCompletionGate.canConfirmCompletion`, **trừ** Global ADMIN không phải thành viên dự án |
| **TV** | Thành viên dự án | `role_assignments` của dự án |
| **CN** | Người cụ thể của sự kiện | Người được nhắc tên, được mời, bị xoá, người gửi yêu cầu, người mời… |

Mọi `recipient_id` là **Keycloak `sub`** (xem bẫy hai loại userId trong `CLAUDE.md`).

### 1.2 Mức độ (`severity`)

| Mức | Mã | Màu | Dùng khi | Hành vi trên giao diện |
|---|---|---|---|---|
| Thông thường | `INFO` | Xanh dương `#2563EB` | Cập nhật bình thường | Tăng số trên chuông |
| Tích cực | `SUCCESS` | Xanh lá `#16A34A` | Kết quả tốt: được xác nhận, hết bị cản trở | Tăng số trên chuông |
| Cảnh báo | `WARNING` | Cam `#D97706` | Cần chú ý sớm: sắp hạn, bị cản trở, bị từ chối | Tăng số, viền cam |
| Khẩn cấp | `URGENT` | Đỏ `#DC2626` | Đã trễ, hoặc mất quyền | Tăng số + hiện toast |

**Cờ Cần xử lý** (`action_required`, màu tím `#7C3AED`): người nhận phải làm gì đó (xác nhận, chấp nhận lời mời, hoàn tất chu kỳ). Hiện ở tab riêng "Cần xử lý", có toast, và **tự chuyển sang "Đã xử lý"** khi việc đã được giải quyết, kể cả do người khác giải quyết.

### 1.3 Nhóm (`category`) và biểu tượng

Tên biểu tượng theo bộ `lucide-react` mà frontend đang dùng.

| Nhóm | `category` | Biểu tượng | Số loại |
|---|---|---|---|
| A. Công việc | `TASK` | `ClipboardList` | 10 |
| B. Phụ thuộc | `DEPENDENCY` | `Link2` | 2 |
| C. Xác nhận hoàn thành | `APPROVAL` | `BadgeCheck` | 5 |
| D. Bình luận | `COMMENT` | `MessageSquare` | 3 |
| E. Chu kỳ / Giai đoạn | `SPRINT` | `Flag` | 9 |
| F. Nhắc hạn | `REMINDER` | `AlarmClock` | 8 |
| G. Thành viên & quyền | `MEMBER` | `Users` | 11 |
| H. Dự án | `PROJECT` | `FolderKanban` | 3 |
| I. Đóng góp | `CONTRIBUTION` | `Award` | 2 |

### 1.4 Kênh gửi

| Kênh | Áp dụng | Template email |
|---|---|---|
| Trong ứng dụng (chuông) | Mọi loại | — |
| Email | A1, C1, D1, F1, F3, G1, G8; F4 tuỳ chọn | Đã có: `task-assigned`, `comment-mention`, `due-date-reminder`, `invite-member`. Cần thêm: `completion-request` (C1), `task-overdue` (F3, hoặc dùng lại `due-date-reminder`), `project-digest` (F4) |

### 1.5 Đường dẫn

`{ws}`, `{pid}`, `{sid}`, `{commentId}` lần lượt là id không gian làm việc, dự án, chu kỳ, bình luận. Đường dẫn của công việc luôn là `/browse/{mã}`; route này **cần thêm** ở frontend (xem [2_KIEN_TRUC_VA_THIET_KE.md §8.3](2_KIEN_TRUC_VA_THIET_KE.md)).

---

## 2. Danh mục chi tiết

Mỗi nhóm có hai bảng: **Điều kiện** (khi nào gửi, gửi cho ai, gắn vào đâu trong code) và **Hiển thị** (nội dung mẫu và đường dẫn).

### A. Công việc (`TASK`)

**Điều kiện**

| # | Mã | Khi nào | Điểm gắn trong code | Người nhận | Mức | Ưu tiên |
|---|---|---|---|---|---|---|
| A1 | `TASK_ASSIGNED` | Tạo công việc / công việc con có sẵn người thực hiện, hoặc đổi người thực hiện | `WorkItemServiceImpl.createWorkItem`, `createSubtask`, `changeAssignee` (`PUT /items/{id}/assignee`), `updateWorkItem`; `WorkflowEngineServiceImpl.executeTransition` (đổi người qua màn chuyển trạng thái). Mọi đường đều ghi lịch sử `ASSIGNEE` (BR-W02), nên gắn ngay cạnh lời gọi `recordAssigneeChange` / `recordDiff` | NTH mới | Thông thường; **Cảnh báo** nếu ưu tiên Cao/Rất cao hoặc còn ≤ 2 ngày đến hạn | **P0** |
| A2 | `TASK_UNASSIGNED` | Người thực hiện bị đổi sang người khác, hoặc bị huỷ giao (`assigneeId = null`, BR-W01) | Như A1 | NTH cũ | Thông thường | P1 |
| A3 | `TASK_STATUS_CHANGED` | Trạng thái **thật sự** thay đổi (menu trạng thái hoặc kéo thẻ sang cột khác). Thành viên thường kéo vào nhóm Hoàn thành thì trạng thái không đổi (BR-W03): đó là C1, không phải A3 | `WorkflowEngineServiceImpl.executeTransition`, sau bước lưu (`BoardServiceImpl.moveCard` cũng đi qua hàm này) | NT + NTH | Thông thường; **Tích cực** khi vào nhóm Hoàn thành | **P0** |
| A4 | `TASK_PRIORITY_RAISED` | Mức ưu tiên tăng lên Cao hoặc Rất cao | `WorkItemServiceImpl.updateWorkItem` (so với `oldState`) | NTH | Cảnh báo | P1 |
| A5 | `TASK_DUE_DATE_CHANGED` | Hạn chót được đặt mới hoặc thay đổi | `WorkItemServiceImpl.updateWorkItem` (so với `oldState`) | NTH | Thông thường; **Cảnh báo** khi dời sớm hơn | P1 |
| A6 | `TASK_DELETED` | Công việc bị xoá (xoá mềm) | `WorkItemServiceImpl.deleteWorkItem` | NTH + NT | Cảnh báo | P1 |
| A7 | `PARENT_AUTO_REOPENED` | Công việc con chuyển về trạng thái chưa xong trong khi công việc cha đã Hoàn thành; hệ thống tự mở lại công việc cha | `WorkflowEngineServiceImpl.cascadeAncestorStatusTransitions` (quy tắc 1) | NTH + NT của công việc cha | Cảnh báo | P1 |
| A8 | `SUBTASKS_AUTO_RESOLVED` | Công việc cha được hoàn thành kèm lựa chọn "Hoàn thành tất cả công việc con" | `WorkflowEngineServiceImpl.executeTransition`, bước 4b (`resolveAllSubtasks`) | NTH của từng công việc con (gộp 1 thông báo/người) | Thông thường | P2 |
| A9 | `TASK_MARKED_DUPLICATE` | **Admin dự án** tạo liên kết "Trùng lặp với"; công việc nguồn tự hoàn thành với kết quả `DUPLICATE`. Thành viên thường làm việc này thì chỉ tạo yêu cầu Tạm hoàn thành (C1) | `WorkItemLinkServiceImpl.createLink` (quy tắc 3, `DUPLICATES`) | NTH + NT của công việc nguồn | Thông thường | P2 |
| A10 | `TASK_SPRINT_CHANGED` | Một công việc được chuyển sang chu kỳ khác hoặc về Kho việc từ màn chi tiết (công việc con đi theo cha) | `WorkItemServiceImpl.updateWorkItem` (đổi `sprintId`) | NTH | Thông thường | P2 |

**Hiển thị**

| # | Nội dung mẫu (tiêu đề · dòng phụ) | Đường dẫn | Kênh |
|---|---|---|---|
| A1 | **An** đã giao cho bạn **MOC-2** · Khảo sát lưu lượng khách và lựa chọn mặt bằng — *Ưu tiên Cao · Hạn chót 20/11/2026* | `/browse/MOC-2` | App + Email |
| A2 | **An** đã giao **MOC-5** cho người khác — *Bạn không còn là người thực hiện công việc này* | `/browse/MOC-5` | App |
| A3 | **Bình** đã chuyển **MOC-2** sang "Đang thực hiện" — *Khảo sát lưu lượng khách và lựa chọn mặt bằng* | `/browse/MOC-2` | App |
| A4 | **An** đã nâng ưu tiên **MOC-12** lên Rất cao — *Chốt menu signature: cà phê muối, bạc xỉu, cold brew* | `/browse/MOC-12` | App |
| A5 | **An** đã dời hạn chót **MOC-5** sớm hơn 5 ngày — *Từ 25/11/2026 sang 20/11/2026* | `/browse/MOC-5` | App |
| A6 | **An** đã xoá **MOC-24** · Khảo sát 5 quán cà phê đối thủ — *Công việc không còn trong dự án Mộc Coffee* | `/w/{ws}/projects/{pid}/backlog` (không trỏ vào công việc đã xoá) | App |
| A7 | **MOC-2** tự mở lại vì công việc con **MOC-3** chuyển sang "Đang thực hiện" — *Do Bình thao tác trên MOC-3* | `/browse/MOC-2` | App |
| A8 | **An** đã hoàn thành **MOC-2** kèm 2 công việc con của bạn — *MOC-3, MOC-4* | `/browse/MOC-2` | App |
| A9 | **MOC-24** được đóng vì trùng lặp với **MOC-2** — *Do An tạo liên kết* | `/browse/MOC-24` | App |
| A10 | **An** đã chuyển **MOC-13** sang Giai đoạn 2 — *Giai đoạn 2 dự kiến bắt đầu 01/12/2026* | `/browse/MOC-13` | App |

### B. Phụ thuộc (`DEPENDENCY`)

**Điều kiện**

| # | Mã | Khi nào | Điểm gắn trong code | Người nhận | Mức | Ưu tiên |
|---|---|---|---|---|---|---|
| B1 | `TASK_BLOCKED` | Tạo liên kết "Cản trở / Chặn tiến độ" (`BLOCKS`): A chặn B | `WorkItemLinkServiceImpl.createLink` (`BLOCKS`) | NTH của công việc bị chặn | Cảnh báo | P1 |
| B2 | `TASK_UNBLOCKED` | Công việc chặn vào nhóm Hoàn thành, liên kết bị xoá, hoặc công việc chặn bị xoá, **và** công việc bị chặn không còn công việc chặn nào chưa xong | `WorkflowEngineServiceImpl.executeTransition`, `WorkItemLinkServiceImpl.deleteLink`, `WorkItemServiceImpl.deleteWorkItem` | NTH của công việc bị chặn | Tích cực | P1 |

**Hiển thị**

| # | Nội dung mẫu | Đường dẫn | Kênh |
|---|---|---|---|
| B1 | **MOC-7** đang bị cản trở bởi **MOC-2** — *Chưa thể chuyển sang "Đang thực hiện" cho tới khi MOC-2 hoàn thành* | `/browse/MOC-7` | App |
| B2 | **MOC-7** đã hết bị cản trở, có thể bắt đầu — *MOC-2 vừa được xác nhận hoàn thành* | `/browse/MOC-7` | App |

### C. Xác nhận hoàn thành (`APPROVAL`)

Luồng "Tạm hoàn thành" theo BR-W03…W08 ([8_QUY_TAC_GIAO_VIEC_VA_XAC_NHAN_HOAN_THANH.md](../work_service/8_QUY_TAC_GIAO_VIEC_VA_XAC_NHAN_HOAN_THANH.md)). Tài liệu đó ghi ở §8 rằng **chưa gửi thông báo cho Admin khi có yêu cầu mới**; nhóm C lấp khoảng trống này. Mọi kết cục của yêu cầu đều đi qua một chỗ duy nhất là `WorkItemCompletionGate.close(request, outcome, actor, note)`, nên C2, C3, C5 và việc đóng C1 đều gắn ở đây.

**Điều kiện**

| # | Mã | Khi nào | Điểm gắn trong code | Người nhận | Mức | Ưu tiên |
|---|---|---|---|---|---|---|
| C1 | `COMPLETION_REQUESTED` | Thành viên không có quyền xác nhận chuyển công việc vào nhóm Hoàn thành (menu trạng thái, kéo thả Board, đánh dấu Trùng lặp); hệ thống tạo yêu cầu "Tạm hoàn thành" | `WorkItemCompletionGate.submit` (gọi từ `executeTransition`, bước 4d) | QL | Thông thường + **Cần xử lý** | **P0** |
| C2 | `COMPLETION_APPROVED` | Yêu cầu được xác nhận: Admin bấm Xác nhận, hoặc Admin tự chuyển thẳng vào DONE (BR-W07) | `WorkItemCompletionGate.close(… APPROVED …)`, từ `WorkItemCompletionServiceImpl.approve` hoặc `onStatusChanged` | Người gửi yêu cầu (+ NTH nếu khác người) | Tích cực | **P0** |
| C3 | `COMPLETION_REJECTED` | Admin từ chối (lý do không bắt buộc) | `WorkItemCompletionServiceImpl.reject` → `close(… REJECTED …)` | Người gửi yêu cầu | Cảnh báo | **P0** |
| C4 | `COMPLETION_PENDING_REMINDER` | Có yêu cầu `PENDING` quá 48 giờ. Chạy 08:00 hằng ngày, gộp theo dự án | Job mới `CompletionReminderJob` (work-service), đọc như `GET /projects/{projectId}/completion-requests` | QL | Cảnh báo + **Cần xử lý** | P1 |
| C5 | `COMPLETION_CANCELLED` | Admin rút lại yêu cầu của người khác, hoặc yêu cầu **tự huỷ** vì trạng thái công việc bị đổi trước khi duyệt (BR-W07). Nếu người gửi tự rút lại hoặc gửi yêu cầu mới thay thế (BR-W05) thì **không** gửi gì, chỉ đóng C1 của QL | `WorkItemCompletionServiceImpl.cancel`, `WorkItemCompletionGate.onStatusChanged` → `close(… CANCELLED …)` | Người gửi yêu cầu | Thông thường | P2 |

**Hiển thị**

| # | Nội dung mẫu | Đường dẫn | Kênh |
|---|---|---|---|
| C1 | **Bình** báo đã hoàn thành **MOC-2**, chờ bạn xác nhận — *Ghi chú: "Đã khảo sát đủ 3 địa điểm, số liệu trong tệp đính kèm."* | `/browse/MOC-2` (mở khung "Tạm hoàn thành" có nút Xác nhận / Từ chối) | App + Email |
| C2 | **An** đã xác nhận hoàn thành **MOC-2** — *Điểm đóng góp đã được ghi cho người thực hiện* | `/browse/MOC-2` | App |
| C3 | **An** chưa xác nhận hoàn thành **MOC-2** — *Lý do: "Thiếu số liệu khung 17h–19h."* | `/browse/MOC-2` | App |
| C4 | Mộc Coffee có 2 yêu cầu xác nhận hoàn thành chờ quá 2 ngày — *MOC-2, MOC-5* | `/w/{ws}/projects/{pid}/board` (nút "Chờ xác nhận HT (n)" đã có) | App |
| C5 | Yêu cầu xác nhận hoàn thành **MOC-5** đã bị huỷ — *An đã đổi trạng thái sang "Cần làm" trước khi duyệt* | `/browse/MOC-5` | App |

Khi một QL đã xác nhận hoặc từ chối, thông báo C1 của **mọi QL khác** tự chuyển sang "Đã xử lý" để không ai duyệt trùng.

### D. Bình luận (`COMMENT`)

**Điều kiện**

| # | Mã | Khi nào | Điểm gắn trong code | Người nhận | Mức | Ưu tiên |
|---|---|---|---|---|---|---|
| D1 | `COMMENT_MENTION` | Bình luận mới, hoặc bình luận được sửa, có **@nhắc tên**. **Chưa có:** cần ô chọn người trên giao diện, lưu dạng `@[Tên](sub)`, và chỉ cho nhắc người có quyền xem dự án | `WorkItemCommentServiceImpl.addComment`, `updateComment` (chỉ gửi cho người **mới** được nhắc) | Người được nhắc tên | Thông thường | **P0** |
| D2 | `COMMENT_ADDED` | Có bình luận mới trên công việc mình thực hiện hoặc đã tạo | `WorkItemCommentServiceImpl.addComment` | NTH + NT | Thông thường, gộp trong 10 phút | **P0** |
| D3 | `COMMENT_REPLIED` | Có người trả lời (`parentCommentId`) bình luận của mình | `WorkItemCommentServiceImpl.addComment` (có `parentCommentId`) | Tác giả bình luận gốc | Thông thường | P1 |

**Hiển thị**

| # | Nội dung mẫu | Đường dẫn | Kênh |
|---|---|---|---|
| D1 | **Bình** đã nhắc đến bạn trong **MOC-2** — *"@An ưu tiên khung 7h–9h sáng, giờ cao điểm cà phê."* | `/browse/MOC-2?comment={commentId}` | App + Email |
| D2 | **An** và 1 người khác đã bình luận trong **MOC-2** — *"Nhớ chụp ảnh lối thoát hiểm PCCC nhé."* | `/browse/MOC-2?comment={commentId}` | App |
| D3 | **An** đã trả lời bình luận của bạn trong **MOC-2** — *"Đồng ý, chốt khung 7h–9h."* | `/browse/MOC-2?comment={commentId}` | App |

Một người vừa được nhắc tên vừa là NTH chỉ nhận **D1** (lý do mạnh nhất). Bình luận do hệ thống tự ghi (ghi chú kèm yêu cầu Tạm hoàn thành, lý do từ chối) **không** sinh D2, vì đã có C1/C3.

### E. Chu kỳ / Giai đoạn (`SPRINT`)

Chu kỳ có tên chứa "giai đoạn" thì nội dung gọi là "giai đoạn", còn lại gọi là "chu kỳ", giống hàm `sprintTerm` ở `ProjectBacklogPage.jsx`.

**Điều kiện**

| # | Mã | Khi nào | Điểm gắn trong code | Người nhận | Mức | Ưu tiên |
|---|---|---|---|---|---|---|
| E1 | `SPRINT_STARTED` | Bấm "Bắt đầu chu kỳ / giai đoạn" | `SprintServiceImpl.startSprint` (đã ghi outbox `SPRINT_STARTED`) | TV | Thông thường | **P0** |
| E2 | `SPRINT_COMPLETED` | Bấm "Hoàn tất chu kỳ / giai đoạn". NTH còn công việc chưa xong nhận thêm dòng riêng cho biết công việc được chuyển đi đâu | `SprintServiceImpl.completeSprint` (đã ghi outbox `SPRINT_COMPLETED`) | TV | Thông thường; **Cảnh báo** cho dòng riêng gửi người còn việc dở | **P0** |
| E3 | `SPRINT_OVERDUE` | Chu kỳ đang chạy đã qua ngày kết thúc mà chưa được hoàn tất | Job mới `SprintDeadlineJob` (work-service) | QL | Khẩn cấp + **Cần xử lý** | **P0** |
| E4 | `SPRINT_ENDING_SOON` | Chu kỳ đang chạy kết thúc trong 24 giờ tới | `SprintDeadlineJob` | QL + NTH còn việc chưa xong | Cảnh báo | P1 |
| E5 | `SPRINT_SCOPE_ADDED` | Công việc được thêm vào chu kỳ **đang chạy** (đổi phạm vi giữa chừng) | `SprintServiceImpl.addItemsToSprint` | NTH (gộp 1 thông báo/người) | Thông thường | P1 |
| E6 | `NEXT_SPRINT_DUE` | Không có chu kỳ đang chạy, và chu kỳ đứng đầu kế hoạch (BR-S15) đã tới ngày bắt đầu dự kiến nhưng chưa được bắt đầu | `SprintDeadlineJob` | QL | Cảnh báo + **Cần xử lý** | P1 |
| E7 | `SPRINT_SCOPE_REMOVED` | Công việc bị đưa ra khỏi chu kỳ đang chạy | `SprintServiceImpl.removeItemsFromSprint` | NTH | Thông thường | P2 |
| E8 | `SPRINT_DATES_CHANGED` | Ngày kết thúc của chu kỳ đang chạy bị đổi | `SprintServiceImpl.update` | TV có công việc trong chu kỳ | Thông thường | P2 |
| E9 | `SPRINT_DELETED` | Chu kỳ bị xoá, công việc bên trong trở về Kho việc | `SprintServiceImpl.delete` | NTH của các công việc bị trả về | Thông thường | P2 |

**Hiển thị**

| # | Nội dung mẫu | Đường dẫn | Kênh |
|---|---|---|---|
| E1 | Giai đoạn 1 của Mộc Coffee đã bắt đầu — *14/11 → 28/11/2026 · Bạn có 3 công việc trong giai đoạn này* | `/w/{ws}/projects/{pid}/board` | App |
| E2 | Giai đoạn 1 đã hoàn tất: 5/6 công việc xong — *Riêng bạn: 1 công việc chưa xong được chuyển sang Giai đoạn 2* | `/w/{ws}/projects/{pid}/reports?sprint={sid}` | App |
| E3 | Giai đoạn 1 đã quá ngày kết thúc 2 ngày — *Hãy hoàn tất giai đoạn hoặc dời ngày kết thúc* | `/w/{ws}/projects/{pid}/backlog` | App |
| E4 | Giai đoạn 1 kết thúc vào ngày mai — *Bạn còn 2 công việc chưa xong: MOC-5, MOC-24* | `/w/{ws}/projects/{pid}/board` | App |
| E5 | **An** đã thêm 2 công việc của bạn vào Giai đoạn 1 đang chạy — *MOC-24, MOC-5* | `/w/{ws}/projects/{pid}/board` | App |
| E6 | Đã đến ngày bắt đầu Giai đoạn 2 — *Giai đoạn 1 đã hoàn tất, Giai đoạn 2 đang chờ bạn bấm "Bắt đầu"* | `/w/{ws}/projects/{pid}/backlog` | App |
| E7 | **An** đã đưa **MOC-5** ra khỏi Giai đoạn 1 — *Công việc trở về Kho việc* | `/browse/MOC-5` | App |
| E8 | Giai đoạn 1 đổi ngày kết thúc sang 30/11/2026 — *Do An cập nhật* | `/w/{ws}/projects/{pid}/backlog` | App |
| E9 | **An** đã xoá Giai đoạn 3 — *2 công việc của bạn đã trở về Kho việc* | `/w/{ws}/projects/{pid}/backlog` | App |

### F. Nhắc hạn (`REMINDER`)

Tất cả chạy theo lịch lúc **08:00 giờ Việt Nam** (`Asia/Ho_Chi_Minh`). Bỏ qua: công việc ở nhóm Hoàn thành, công việc đang **chờ xác nhận hoàn thành** (BR-W08: người thực hiện đã báo xong; phạt trễ hạn tính theo ngày gửi yêu cầu, nên nhắc tiếp là sai), dự án mẫu, dự án hoặc công việc đã xoá.

**Điều kiện**

| # | Mã | Khi nào | Điểm gắn trong code | Người nhận | Mức | Ưu tiên |
|---|---|---|---|---|---|---|
| F1 | `TASK_DUE_SOON` | Hạn chót là **ngày mai**; thêm mốc **trước 3 ngày** nếu ưu tiên Cao/Rất cao | Job mới `DueDateReminderJob` (work-service) | NTH | Cảnh báo | **P0** |
| F2 | `TASK_DUE_TODAY` | Hạn chót là hôm nay | `DueDateReminderJob` | NTH | Cảnh báo | **P0** |
| F3 | `TASK_OVERDUE` | Đã qua hạn chót. Chỉ gửi vào ngày quá hạn thứ **1, 3 và 7**; sau đó chỉ còn trong tổng hợp F4 | `DueDateReminderJob` | NTH | Khẩn cấp | **P0** |
| F4 | `PROJECT_DAILY_DIGEST` | 08:00 mỗi ngày, chỉ gửi khi có ít nhất một con số khác 0 | Job mới `ProjectDigestJob` (work-service) | QL | Cảnh báo | P1 |
| F5 | `TASK_START_TODAY` | Ngày bắt đầu là hôm nay mà công việc vẫn ở nhóm Cần làm | `DueDateReminderJob` | NTH | Thông thường | P2 |
| F6 | `TASK_STALE` | Công việc ở nhóm Đang thực hiện quá 5 ngày không có thay đổi | `DueDateReminderJob` | NTH | Thông thường | P2 |
| F7 | `PROJECT_ENDING_SOON` | Ngày kết thúc dự án (`projects.end_date`) còn 7 ngày mà vẫn còn công việc chưa xong | `ProjectDigestJob` | QL | Cảnh báo | P2 |
| F8 | `PROJECT_OVERDUE` | Đã qua ngày kết thúc dự án mà còn công việc chưa xong (gửi ngày 1, 3, 7) | `ProjectDigestJob` | QL | Khẩn cấp | P2 |

**Hiển thị**

| # | Nội dung mẫu | Đường dẫn | Kênh |
|---|---|---|---|
| F1 | **MOC-5** đến hạn vào ngày mai (20/11) — *Nộp hồ sơ ĐKKD và Giấy chứng nhận Đủ điều kiện ATTP* | `/browse/MOC-5` | App + Email |
| F2 | **MOC-5** đến hạn hôm nay — *Nộp hồ sơ ĐKKD và Giấy chứng nhận Đủ điều kiện ATTP* | `/browse/MOC-5` | App |
| F3 | **MOC-5** đã quá hạn 3 ngày — *Hạn chót 20/11/2026* | `/browse/MOC-5` | App + Email (ngày thứ 1) |
| F4 | Mộc Coffee hôm nay: 2 quá hạn · 1 đến hạn · 1 chờ xác nhận — *3 công việc trong Giai đoạn 1 chưa có người thực hiện* | `/w/{ws}/projects/{pid}/backlog?filter=overdue` (**cần thêm** bộ lọc "Quá hạn") | App (+ Email tuỳ chọn) |
| F5 | Hôm nay là ngày bắt đầu **MOC-7** — *Thiết kế bản vẽ mặt bằng 2D và phối cảnh 3D* | `/browse/MOC-7` | App |
| F6 | **MOC-7** đã 6 ngày chưa được cập nhật — *Đang ở trạng thái "Đang thực hiện"* | `/browse/MOC-7` | App |
| F7 | Mộc Coffee kết thúc sau 7 ngày (21/12/2026) — *Còn 8 công việc chưa hoàn thành* | `/w/{ws}/projects/{pid}/backlog` | App |
| F8 | Mộc Coffee đã quá ngày kết thúc 3 ngày — *Còn 4 công việc chưa hoàn thành* | `/w/{ws}/projects/{pid}/backlog` | App |

### G. Thành viên & quyền (`MEMBER`)

**Điều kiện**

| # | Mã | Khi nào | Điểm gắn trong code | Người nhận | Mức | Ưu tiên |
|---|---|---|---|---|---|---|
| G1 | `PROJECT_INVITATION_RECEIVED` | Được mời vào dự án. Hiện chỉ có email (`InvitationMailer`) | `InvitationServiceImpl.inviteToProject` | Người được mời | Thông thường + **Cần xử lý** | **P0** |
| G2 | `PROJECT_MEMBER_REMOVED` | Bị xoá khỏi dự án | `ProjectMemberServiceImpl.removeMemberFromProject` | Người bị xoá | Khẩn cấp | **P0** |
| G3 | `MEMBER_REMOVED_OPEN_ITEMS` | Người bị xoá vẫn đang là NTH của công việc chưa xong (code hiện tại **không** tự huỷ giao) | `ProjectMemberServiceImpl.removeMemberFromProject` | QL | Cảnh báo | P1 |
| G4 | `PROJECT_INVITATION_ANSWERED` | Người được mời chấp nhận hoặc từ chối | `InvitationServiceImpl.accept`, `decline` | Người mời (`invitedBy`) + Trưởng dự án | Tích cực; Thông thường khi bị từ chối | P1 |
| G5 | `PROJECT_MEMBER_ADDED` | Được thêm thẳng vào dự án (không qua lời mời) | `ProjectMemberServiceImpl.addMemberToProject` | Người được thêm | Thông thường | P1 |
| G6 | `PROJECT_ROLE_CHANGED` | Vai trò dự án của mình bị đổi | `ProjectMemberServiceImpl.updateMemberRole` | Người bị đổi vai trò | Thông thường; **Cảnh báo** khi bị hạ quyền | P1 |
| G7 | `PROJECT_LEAD_CHANGED` | Trưởng dự án (`leadUserId`) được thay | `ProjectServiceImpl.updateProject` | Trưởng dự án mới + cũ | Thông thường | P1 |
| G8 | `WORKSPACE_INVITATION_RECEIVED` | Được mời vào không gian làm việc. Hiện chỉ có email | workspace-service: `WorkspaceMemberServiceImpl.invite` | Người được mời | Thông thường + **Cần xử lý** | P1 |
| G9 | `WORKSPACE_MEMBER_REMOVED` | Bị xoá khỏi không gian làm việc | `WorkspaceMemberServiceImpl.removeMember` | Người bị xoá | Khẩn cấp | P1 |
| G10 | `WORKSPACE_ROLE_CHANGED` | Vai trò trong không gian làm việc bị đổi | `WorkspaceMemberServiceImpl.updateMemberRole` | Người bị đổi vai trò | Thông thường | P2 |
| G11 | `WORKSPACE_INVITATION_ANSWERED` | Lời mời vào không gian làm việc được chấp nhận hoặc từ chối | `WorkspaceMemberServiceImpl.acceptInvitation`, `declineInvitation` | Người mời | Thông thường | P2 |

**Hiển thị**

| # | Nội dung mẫu | Đường dẫn | Kênh |
|---|---|---|---|
| G1 | **An** mời bạn tham gia dự án Mộc Coffee — *Vai trò: Thành viên* | `/invitations` | App + Email |
| G2 | Bạn không còn là thành viên dự án Mộc Coffee — *Do An thực hiện* | `/w/{ws}/projects` (**không** trỏ vào dự án vừa mất quyền) | App |
| G3 | Bình rời Mộc Coffee, còn 2 công việc chưa xong đứng tên — *MOC-5, MOC-24: hãy giao lại cho người khác* | `/w/{ws}/projects/{pid}/backlog` | App |
| G4 | **Bình** đã chấp nhận lời mời vào Mộc Coffee — *Vai trò: Thành viên* | `/w/{ws}/projects/{pid}/members` | App |
| G5 | **An** đã thêm bạn vào dự án Mộc Coffee — *Vai trò: Người xem* | `/w/{ws}/projects/{pid}` | App |
| G6 | Vai trò của bạn trong Mộc Coffee đổi sang Quản trị viên — *Do An thực hiện* | `/w/{ws}/projects/{pid}` | App |
| G7 | Bạn trở thành Trưởng dự án Mộc Coffee — *Thay cho An* | `/w/{ws}/projects/{pid}` | App |
| G8 | **An** mời bạn vào không gian làm việc Mộc Group — *Vai trò: Thành viên* | `/invitations` | App + Email |
| G9 | Bạn không còn là thành viên không gian làm việc Mộc Group — *Do An thực hiện* | `/workspaces` | App |
| G10 | Vai trò của bạn trong Mộc Group đổi sang Quản trị viên — *Do An thực hiện* | `/w/{ws}/members` | App |
| G11 | **Chi** đã chấp nhận lời mời vào Mộc Group — *Vai trò: Thành viên* | `/w/{ws}/members` | App |

G2 và G9 là hai loại duy nhất được gửi cho người **vừa mất quyền** xem.

### H. Dự án (`PROJECT`)

**Điều kiện**

| # | Mã | Khi nào | Điểm gắn trong code | Người nhận | Mức | Ưu tiên |
|---|---|---|---|---|---|---|
| H1 | `PROJECT_ARCHIVED` | Dự án bị lưu trữ (xoá mềm) | `ProjectServiceImpl.deleteProject` | TV | Cảnh báo | P1 |
| H2 | `PROJECT_RESTORED` | Dự án được khôi phục | `ProjectServiceImpl.restoreProject` | TV | Thông thường | P2 |
| H3 | `PROJECT_SCHEDULE_CHANGED` | Ngày bắt đầu hoặc kết thúc dự án thay đổi | `ProjectServiceImpl.updateProject` | TV | Thông thường | P2 |

**Hiển thị**

| # | Nội dung mẫu | Đường dẫn | Kênh |
|---|---|---|---|
| H1 | **An** đã lưu trữ dự án Mộc Coffee — *Có thể khôi phục lại dự án nếu cần* | `/w/{ws}/projects` | App |
| H2 | **An** đã khôi phục dự án Mộc Coffee — *Dự án hoạt động trở lại* | `/w/{ws}/projects/{pid}` | App |
| H3 | Mộc Coffee đổi ngày kết thúc sang 15/12/2026 — *Do An cập nhật* | `/w/{ws}/projects/{pid}` | App |

### I. Đóng góp (`CONTRIBUTION`)

**Điều kiện**

| # | Mã | Khi nào | Điểm gắn trong code | Người nhận | Mức | Ưu tiên |
|---|---|---|---|---|---|---|
| I1 | `CONTRIBUTION_ADJUSTED` | QL điều chỉnh điểm thủ công (tối đa ±8, bắt buộc có lý do). API đã có, nút trên giao diện đang ẩn | `ContributionReportServiceImpl.manualAdjustment` | Thành viên được điều chỉnh | Tích cực khi cộng; **Cảnh báo** khi trừ | P1 |
| I2 | `CONTRIBUTION_FROZEN` | Chu kỳ hoàn tất, hệ thống chốt số liệu đóng góp | `ContributionReportServiceImpl.freezeSprintContributionSnapshot` | TV | Thông thường | P2 |

**Hiển thị**

| # | Nội dung mẫu | Đường dẫn | Kênh |
|---|---|---|---|
| I1 | **An** đã cộng 3 điểm đóng góp cho bạn — *Lý do: "Hỗ trợ khảo sát ngoài giờ."* | `/w/{ws}/projects/{pid}/reports?tab=contributions` | App |
| I2 | Bảng đóng góp Giai đoạn 1 đã được chốt — *Bạn được ghi nhận 13 điểm* | `/w/{ws}/projects/{pid}/reports?tab=contributions&sprint={sid}` | App |

---

## 3. Quy tắc chung

| # | Quy tắc | Chi tiết |
|---|---|---|
| R1 | **Không gửi cho chính người thao tác** | Bỏ `actorId` khỏi danh sách người nhận. |
| R2 | **Chỉ gửi cho người còn quyền xem dự án** | Phía phát chỉ chọn người đang có `role_assignments`. Ngoại lệ: G2, G9. |
| R3 | **Một sự kiện, một người, một thông báo** | Chọn lý do mạnh nhất: được nhắc tên > được giao > người thực hiện > người tạo > thành viên. |
| R4 | **Gộp thông báo** | Đã có thông báo chưa đọc cùng `group_key` trong 10 phút thì cập nhật thông báo đó ("An và 2 người khác đã bình luận…") và đẩy lên đầu, không tạo mới. Áp dụng: A3, A10, D2, E5, E7. |
| R5 | **Thao tác hàng loạt** | Hoàn tất chu kỳ, thêm nhiều việc vào chu kỳ, hoàn thành dây chuyền việc con, xoá chu kỳ: mỗi người nhận đúng một thông báo. |
| R6 | **Job chạy lại không trùng** | `dedup_key` UNIQUE, ví dụ `TASK_DUE_SOON:{itemId}:{dueDate}:{recipient}`. Đổi hạn chót thì sinh khoá mới nên vẫn nhắc đúng. |
| R7 | **Bỏ qua dữ liệu không hoạt động** | Dự án mẫu (`is_template = true`), dự án hoặc công việc đã xoá (`deleted_at`). |
| R8 | **Dùng đúng thuật ngữ giao diện** | "chu kỳ" / "giai đoạn" theo `sprintTerm`; "Kho việc" thay cho Backlog; "Trưởng dự án" thay cho Lead (xem `docs/services/language/`). |
| R9 | **Cần xử lý tự đóng** | `action_resolved_at` được đặt khi việc đã xong ở nơi khác: QL khác đã duyệt/từ chối, yêu cầu bị rút lại hoặc thay thế, lời mời bị huỷ hoặc đã trả lời, chu kỳ đã hoàn tất (E3), giai đoạn đã bắt đầu (E6). |
| R10 | **Không nhắc mỗi ngày** | Quá hạn chỉ nhắc vào ngày thứ 1, 3, 7; sau đó chỉ còn trong F4. |
| R11 | **Không nhắc khi đang chờ xác nhận** | Công việc có yêu cầu Tạm hoàn thành `PENDING` không nhận F1–F3, F6; QL sẽ nhận C4. |
| R12 | **Không lộ nội dung** | Sự kiện chỉ chứa id, mã, tiêu đề ngắn. Trích bình luận tối đa 200 ký tự, chỉ cho người có quyền xem. |

---

## 4. Những gì không đưa vào chuông

| Thay đổi | Lý do | Xử lý thay thế |
|---|---|---|
| Kéo đổi thứ tự (rank), kéo thẻ trong cùng cột | Rất nhiều, không cần ai hành động | Realtime: Board tải lại im lặng |
| Sửa tiêu đề, mô tả, điểm ước lượng, nhãn, thành phần | Gây ồn | Realtime: ô nháy + lịch sử thay đổi |
| Thêm / xoá tệp đính kèm; liên kết "Liên quan tới", "Gây ra" | Ít giá trị | Realtime |
| Cấu hình Board, quy trình, trạng thái, loại công việc | Ảnh hưởng chung, không theo người | Realtime `PROJECT_CONFIG_CHANGED` (banner) |
| Bình luận do hệ thống ghi kèm yêu cầu / lý do từ chối | Đã có C1, C3 | — |
| Mọi hành động của chính mình | — | — |

Sau này nếu có tính năng **Theo dõi (watch)** công việc, người theo dõi sẽ được thêm vào người nhận của A3, D2 và có thể chọn nhận cả các thay đổi trong bảng này.
