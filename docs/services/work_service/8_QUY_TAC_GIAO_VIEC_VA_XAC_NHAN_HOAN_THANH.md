# 📘 QUY TẮC NGHIỆP VỤ: GIAO VIỆC & XÁC NHẬN HOÀN THÀNH ("TẠM HOÀN THÀNH")
## DỰ ÁN: DATN-2026 — WORK SERVICE

> Cập nhật 14/11/2026. Bổ sung cho [7_QUY_TAC_NGHIEP_VU_SPRINT_BOARD_BAO_CAO.md](7_QUY_TAC_NGHIEP_VU_SPRINT_BOARD_BAO_CAO.md).

---

## 1. Bối cảnh

Trước thay đổi này:
- Ai có quyền `ISSUE_TRANSITION` (mặc định **cả Member**) đều chuyển được công việc sang **Hoàn thành**. Điểm đóng góp được ghi ngay lúc đó, không qua Admin.
- **Hủy giao việc không hoạt động**: giao diện gửi `assigneeId: null` nhưng `PUT /items/{id}` hiểu `null` là "không đổi".
- Đổi người thực hiện qua màn chuyển trạng thái (`fields.assigneeId`) **không kiểm tra quyền và không ghi lịch sử**.
- Đánh dấu **Trùng lặp** (liên kết `DUPLICATES`) tự đóng công việc sang DONE, ai làm cũng được.

---

## 2. Giao việc

| Mã | Quy tắc |
| :--- | :--- |
| **BR-W01** | Mọi thành viên có quyền `ISSUE_ASSIGN` (mặc định **Admin dự án và Member**, Viewer thì không) đều được **giao việc** cho mình hoặc người khác và **hủy giao**. Dùng endpoint riêng `PUT /items/{id}/assignee`, chỉ cần `ISSUE_ASSIGN`, không cần `ISSUE_EDIT`. `assigneeId = null` nghĩa là hủy giao. Người được giao phải là thành viên dự án. |
| **BR-W02** | **Mọi thay đổi người thực hiện đều được ghi lịch sử** (trường *Người thực hiện*: tên cũ → tên mới, ai đổi, lúc nào). Hủy giao thì giá trị mới để trống. Đổi người qua màn chuyển trạng thái cũng phải có `ISSUE_ASSIGN` và cũng được ghi lịch sử. |

---

## 3. Xác nhận Hoàn thành

| Mã | Quy tắc |
| :--- | :--- |
| **BR-W03** | **Chỉ Admin dự án mới được XÁC NHẬN Hoàn thành.** Admin dự án = vai trò có quyền `PROJECT_ADMIN` (mặc định ADMINISTRATOR), Lead/người tạo dự án, hoặc Global ADMIN. Người khác chuyển công việc vào nhóm trạng thái **DONE** (menu trạng thái, kéo thả trên Board, đánh dấu Trùng lặp) thì **trạng thái không đổi**: hệ thống tạo một yêu cầu **"Tạm hoàn thành"** chờ Admin duyệt. |
| **BR-W04** | Khi gửi yêu cầu, các ràng buộc vẫn được **kiểm tra ngay** để báo lỗi sớm: việc tiền đề (`BLOCKS`), Sub-task chưa xong, validator của bước chuyển. Lựa chọn *"hoàn thành luôn các việc con"* được **lưu lại** và chỉ áp dụng khi Admin xác nhận. |
| **BR-W05** | Mỗi công việc có **tối đa một** yêu cầu đang chờ. Gửi lại thì yêu cầu cũ bị thay thế (lưu là CANCELLED). |
| **BR-W06** | **Admin xác nhận** → hệ thống chạy lại bước chuyển đã lưu (nếu quy trình đã đổi thì chọn bước tương đương tới đúng trạng thái đích, rồi tới bất kỳ trạng thái DONE nào). Condition kiểu *"chỉ người được giao"* **không** đánh giá lại với Admin vì đã kiểm tra với người gửi; các ràng buộc khác chạy lại. Công việc sang DONE, có `resolvedAt`, **điểm đóng góp ghi cho người thực hiện**. Phạt trễ hạn tính theo **ngày gửi yêu cầu**, không theo ngày Admin duyệt. |
| **BR-W07** | **Admin từ chối** (lý do không bắt buộc; nếu có thì hiện thành bình luận) → công việc giữ nguyên trạng thái. **Người gửi hoặc Admin** được rút lại yêu cầu. Nếu trạng thái công việc bị đổi trước khi duyệt (người dùng chuyển, hoặc cha bị cascade mở lại / tự bắt đầu) thì yêu cầu **tự hủy**. Admin tự chuyển thẳng vào DONE thì yêu cầu đang chờ được tính là **đã xác nhận**. |
| **BR-W08** | Trong lúc chờ, công việc **chưa được tính là Hoàn thành ở bất cứ đâu**: Board, Burndown, Velocity, tiến độ công việc cha, Sổ ghi công. Giao diện hiển thị rõ: nhãn **"Chờ duyệt HT"** trên thẻ Board/Backlog, khung vàng **"Tạm hoàn thành – chờ Admin xác nhận"** trong chi tiết công việc, nút **"Chờ xác nhận HT (n)"** trên Board. Mỗi bước (gửi, xác nhận, từ chối, hủy) ghi một dòng lịch sử trường *Xác nhận hoàn thành*. |

### Vì sao không đổi trạng thái khi "Tạm hoàn thành"?

Nếu cho công việc vào cột DONE rồi gắn cờ "chưa duyệt", mọi nơi đang đếm DONE (Burndown, Velocity, tiến độ cha, guard việc tiền đề, Sổ ghi công, hoàn tất Sprint) đều phải sửa để loại trừ cờ này — dễ sót và làm sai báo cáo. Giữ nguyên trạng thái thì toàn bộ báo cáo đúng sẵn, và lúc Admin xác nhận, công việc đi qua đúng một bước chuyển workflow như bình thường.

---

## 4. Luồng

```
Member chọn "Hoàn thành" / kéo vào cột Hoàn thành
        │
        ▼
WorkflowEngine: kiểm tra Condition, việc tiền đề, Sub-task, validator
        │
        ├── người dùng là Admin dự án ──► đổi trạng thái DONE + ghi công (như cũ)
        │
        └── không phải Admin ──► lưu yêu cầu PENDING (work_item_completion_requests)
                                   trạng thái giữ nguyên, ghi lịch sử "Tạm hoàn thành"
                                          │
              ┌───────────────────────────┼──────────────────────────────┐
              ▼                           ▼                              ▼
   Admin "Xác nhận"             Admin "Từ chối" (+ lý do)     Người gửi "Rút lại" /
   → chạy lại bước chuyển       → REJECTED, trạng thái         trạng thái đổi trước khi duyệt
   → DONE, ghi công (APPROVED)    giữ nguyên                   → CANCELLED
```

---

## 5. API

| Phương thức | Đường dẫn | Quyền | Mô tả |
| :--- | :--- | :--- | :--- |
| PUT | `/api/v1/work/items/{id}/assignee` | `ISSUE_ASSIGN` | Giao (`{assigneeId}`) / hủy giao (`{assigneeId: null}`) |
| POST | `/api/v1/work/work-items/{id}/transitions/{transitionId}` | `ISSUE_TRANSITION` | Như cũ; người không phải Admin chuyển vào DONE nhận về `completionApprovalRequested: true` |
| GET | `/api/v1/work/projects/{projectId}/completion-requests` | `PROJECT_VIEW` | Các yêu cầu đang chờ của dự án |
| GET | `/api/v1/work/items/{id}/completion-requests` | `PROJECT_VIEW` | Lịch sử yêu cầu của một công việc |
| POST | `/api/v1/work/items/{id}/completion/approve` | `PROJECT_ADMIN` | Xác nhận Hoàn thành |
| POST | `/api/v1/work/items/{id}/completion/reject` | `PROJECT_ADMIN` | Từ chối, body `{reason}` (không bắt buộc) |
| DELETE | `/api/v1/work/items/{id}/completion` | Người gửi hoặc Admin | Rút lại yêu cầu |

Trường mới trong response: `AvailableTransitionResponse.requiresApproval`, `WorkItemResponse.completionPending`, `WorkItemDetailResponse.completionPending / completionRequest / canConfirmCompletion`, `BoardCardResponse.completionPending`, `MoveCardResponse.completionPending`.

**Dữ liệu:** bảng `work_item_completion_requests` (Flyway `V10__work_item_completion_requests.sql`), chỉ mục duy nhất một `PENDING` cho mỗi công việc. Bảng giữ toàn bộ lịch sử, không xóa bản ghi. Không thuộc dữ liệu nhân bản của dự án mẫu (yêu cầu đang chờ không được sao chép).

---

## 6. Thông điệp

| Tình huống | Mã HTTP | Thông điệp |
| :--- | :---: | :--- |
| Member chuyển vào DONE | 200 | *Đã gửi yêu cầu xác nhận hoàn thành. Công việc ở trạng thái Tạm hoàn thành cho tới khi Admin dự án duyệt.* |
| Không phải Admin gọi xác nhận / từ chối | 403 | *Chỉ Admin dự án mới được xác nhận hoặc từ chối hoàn thành công việc* |
| Người khác rút lại yêu cầu | 403 | *Chỉ người gửi yêu cầu hoặc Admin dự án mới được hủy yêu cầu này* |
| Không có yêu cầu đang chờ | 400 | *Công việc không có yêu cầu xác nhận hoàn thành nào đang chờ* |
| Quy trình không còn đường tới DONE | 400 | *Quy trình hiện tại không có bước chuyển tới trạng thái Hoàn thành từ trạng thái của công việc. Hãy từ chối yêu cầu hoặc điều chỉnh quy trình.* |
| Giao cho người ngoài dự án | 400 | *Người được giao phải là thành viên của dự án* |

---

## 7. Kiểm thử

Unit test: `WorkflowEngineServiceTest` (7 ca mới: Member chỉ tạo yêu cầu, hoãn hoàn thành việc con, guard Sub-task vẫn chặn, Admin hoàn thành + ghi công theo ngày gửi, cờ `requiresApproval`, xác nhận bỏ qua Condition, đổi người qua màn chuyển trạng thái), `WorkItemCompletionServiceTest`, `WorkItemCompletionGateTest`, `BoardServiceTest` (kéo vào cột Hoàn thành).

---

## 8. Điểm cần quyết định

1. **Mở lại (Reopen) công việc đã Hoàn thành** vẫn theo quyền `ISSUE_TRANSITION` như cũ (Member làm được, điểm bị trừ lại). Nếu muốn chỉ Admin mới được mở lại thì cần thêm một quy tắc riêng.
2. **Viewer** không giao việc được (không có `ISSUE_ASSIGN`). Muốn mọi người kể cả Viewer đều giao được thì bật `ISSUE_ASSIGN` cho vai trò Viewer trong cấu hình phân quyền dự án.
3. **Chưa gửi thông báo** (email/thông báo trong ứng dụng) cho Admin khi có yêu cầu mới; Admin xem qua nút "Chờ xác nhận HT (n)" trên Board.
