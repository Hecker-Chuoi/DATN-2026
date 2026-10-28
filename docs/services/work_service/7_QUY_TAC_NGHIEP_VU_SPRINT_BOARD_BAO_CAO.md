# 📘 MÔ TẢ NGHIỆP VỤ: SPRINT, BACKLOG, BẢNG CÔNG VIỆC & BÁO CÁO AGILE
## DỰ ÁN: DATN-2026 — GIAI ĐOẠN 6 (WORK SERVICE V2)

> **Phân hệ:** `work-service` (Agile Lifecycle & Board Engine)
> **Tài liệu liên quan:**
> - [`1_TONG_QUAN_KIEN_TRUC_WORK_SERVICE.md`](./1_TONG_QUAN_KIEN_TRUC_WORK_SERVICE.md) (Kiến trúc)
> - [`2_THIET_KE_CO_SO_DU_LIEU.md`](./2_THIET_KE_CO_SO_DU_LIEU.md) (CSDL)
> - [`3_SO_DO_TUAN_TU_NGHIEP_VU.md`](./3_SO_DO_TUAN_TU_NGHIEP_VU.md) (Sơ đồ tuần tự)
> - [`4_KE_HOACH_TRIEN_KHAI_CHI_TIET.md`](./4_KE_HOACH_TRIEN_KHAI_CHI_TIET.md) (Lộ trình)
> - [`5_QUY_CHUAN_GIAO_DIEN_FRONTEND.md`](./5_QUY_CHUAN_GIAO_DIEN_FRONTEND.md) (Giao diện)
>
> **Mục đích:** Mô tả **quy tắc nghiệp vụ** (business rules) của Giai đoạn 6 — cái *phải đúng*, độc lập với cách cài đặt. Dùng làm căn cứ nghiệm thu và viết kịch bản kiểm thử.

---

## 1. Phạm vi & Thuật ngữ

| Thuật ngữ | Định nghĩa nghiệp vụ |
| :--- | :--- |
| **Sprint** | Chu kỳ làm việc có thời hạn của nhóm Scrum. Vòng đời `FUTURE → ACTIVE → CLOSED`, **một chiều, không quay lui**. |
| **Backlog** | Tập công việc của dự án **chưa** thuộc Sprint nào (`sprint_id IS NULL`). |
| **Board** | Bảng trực quan hoá công việc theo cột. Board **SCRUM** chỉ hiện việc trong Sprint đang chạy; board **KANBAN** hiện toàn dự án. |
| **Cột (Column)** | Nhóm hiển thị trên Board, ánh xạ tới một hoặc nhiều **Trạng thái**. Bản thân cột không phải là trạng thái. |
| **WIP Limit** | Giới hạn số thẻ được phép nằm trong một cột cùng lúc. |
| **Swimlane** | Phân luồng ngang trên Board (theo Epic / Người thực hiện / Độ ưu tiên). |
| **Story Points (SP)** | Điểm ước lượng độ phức tạp. Chỉ tính ở cấp Story/Task/Bug — **Sub-task không mang điểm riêng**. |
| **Velocity** | Tổng SP *hoàn thành* của một Sprint đã đóng. |
| **Snapshot** | Bản ghi đóng băng số liệu Sprint tại thời điểm đóng, dùng cho báo cáo về sau. |

**Ngoài phạm vi tài liệu này:** quản trị dự án & RBAC (Giai đoạn 3), máy trạng thái workflow (Giai đoạn 4), chi tiết công việc/bình luận/đính kèm (Giai đoạn 5), JQL & tự động hoá (Giai đoạn 8).

---

## 2. Vai trò & Quyền hạn

| Vai trò | Xem Backlog/Board/Báo cáo | Tạo & sửa công việc | Kéo thả trên Board | Quản trị Sprint | Cấu hình Board |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Project Administrator** | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Member** | ✅ | ✅ | ✅ | ❌ | ❌ |
| **Viewer** | ✅ | ❌ | ❌ | ❌ | ❌ |

**Quy tắc quyền:**
- **BR-P01** — Quản trị Sprint (tạo/sửa/xoá/bắt đầu/hoàn tất, thêm-bớt công việc) yêu cầu quyền `SPRINT_MANAGE`. Mặc định **chỉ Project Administrator** có quyền này; Member thì không — đúng mô hình Scrum Master của Jira.
- **BR-P02** — Kéo thả thẻ trên Board yêu cầu quyền `ISSUE_TRANSITION`, không phải `SPRINT_MANAGE`.
- **BR-P03** — Cấu hình cột/WIP/swimlane yêu cầu quyền `PROJECT_ADMIN`.
- **BR-P04** — Mọi thao tác trên một Sprint/Board đều được kiểm tra quyền **theo dự án chứa nó**. Truy cập tài nguyên không tồn tại phải trả **404**, không được trả 403 gây hiểu nhầm là "không có quyền".

---

## 3. Nghiệp vụ Sprint

### 3.1. Vòng đời

```
         ┌──────────┐   Bắt đầu   ┌──────────┐   Hoàn tất   ┌──────────┐
         │  FUTURE  │ ──────────▶ │  ACTIVE  │ ───────────▶ │  CLOSED  │
         │ (Dự kiến)│             │(Đang chạy)│             │(Đã đóng) │
         └──────────┘             └──────────┘              └──────────┘
              │                                                   ▲
              └── Xoá được ────────────────────────────────────── ✗ (không thể mở lại)
```

### 3.2. Quy tắc tạo & sửa

| Mã | Quy tắc |
| :--- | :--- |
| **BR-S01** | Sprint mới luôn bắt đầu ở trạng thái `FUTURE`. Tên Sprint là bắt buộc (tối đa 255 ký tự). |
| **BR-S02** | Ngày bắt đầu/kết thúc là **tuỳ chọn** khi tạo; nếu khai báo cả hai thì ngày kết thúc phải **sau** ngày bắt đầu. |
| **BR-S03** | Không được sửa một Sprint đã `CLOSED` — số liệu của nó đã được dùng cho báo cáo. |
| **BR-S04** | **Chỉ xoá được Sprint `FUTURE`.** Sprint đang chạy phải đóng trước; Sprint đã đóng phải giữ lại để lưu vết. |
| **BR-S05** | Khi xoá Sprint, công việc bên trong **trở về Backlog**, không bị xoá theo. |

### 3.3. Quy tắc bắt đầu Sprint

| Mã | Quy tắc |
| :--- | :--- |
| **BR-S10** | Chỉ Sprint `FUTURE` mới được bắt đầu. |
| **BR-S11** | **Mỗi dự án chỉ được có tối đa MỘT Sprint `ACTIVE`.** Vi phạm → lỗi **409 Conflict** kèm tên Sprint đang chạy. Ràng buộc này được chốt cả ở tầng ứng dụng lẫn tầng CSDL để chống chạy đua. |
| **BR-S12** | **Ngày kết thúc là bắt buộc** khi bắt đầu — không có nó thì không vẽ được Burndown. Ngày bắt đầu mặc định là thời điểm hiện tại. |
| **BR-S13** | Không được bắt đầu **Sprint rỗng**, trừ khi người dùng xác nhận rõ ràng (cờ `allowEmpty`). |
| **BR-S14** | Sprint không nên kéo dài quá **8 tuần**. |

### 3.4. Quy tắc hoàn tất Sprint — *phần tinh tế nhất*

| Mã | Quy tắc |
| :--- | :--- |
| **BR-S20** | Chỉ Sprint `ACTIVE` mới được hoàn tất. |
| **BR-S21** | Công việc được phân loại theo **nhóm trạng thái**: `DONE` là hoàn thành, còn lại là chưa xong. Không phân loại theo tên trạng thái. |
| **BR-S22** | **Số liệu phải được chụp TRƯỚC khi di chuyển công việc.** Nếu chuyển trước rồi mới chụp, snapshot sẽ mô tả sai những gì Sprint thực sự đạt được. |
| **BR-S23** | **Công việc đã `DONE` GIỮ NGUYÊN Sprint cũ.** Đây là điều kiện để Velocity còn ý nghĩa: nếu kéo chúng đi, Sprint đã đóng sẽ trông như chẳng làm được gì. |
| **BR-S24** | Công việc **chưa xong** được chuyển sang đích do người dùng chọn: **Backlog** hoặc một Sprint **chưa đóng** khác *trong cùng dự án*. |
| **BR-S25** | Không được chọn đích là Sprint đã `CLOSED`, Sprint khác dự án, hay chính Sprint đang đóng. |
| **BR-S26** | Công việc chuyển đi được **xếp vào cuối** danh sách ở phạm vi đích (tính lại thứ tự), tránh chen ngang thứ tự đang có. |
| **BR-S27** | Sprint chuyển sang `CLOSED` và ghi nhận thời điểm hoàn tất. **Không thể mở lại.** |
| **BR-S28** | Hệ thống phát sinh sự kiện `SPRINT_COMPLETED` để các phân hệ khác (thông báo, realtime) xử lý sau. |

### 3.5. Quy tắc liên tầng Cha – Con

| Mã | Quy tắc |
| :--- | :--- |
| **BR-S30** | **Sub-task luôn ở cùng Sprint với công việc cha.** Đây là bất biến của hệ thống. |
| **BR-S31** | Khi công việc cha đổi Sprint (thủ công, kéo thả, hoặc do đóng Sprint), **toàn bộ Sub-task con tự động đi theo**. |
| **BR-S32** | Khi đóng Sprint, Sub-task **đã DONE vẫn đi theo cha chưa xong**. Nếu để lại, Sub-task sẽ mắc kẹt ở Sprint đã đóng trong khi cha nó đã sang Sprint mới — phá vỡ BR-S30. |
| **BR-S33** | **Không được kéo Sub-task vào/ra Sprint một cách độc lập.** Giao diện phải chặn thao tác này kèm giải thích. |
| **BR-S34** | Sub-task **không mang Story Points riêng** — điểm đã nằm ở Story/Task cha. Cộng cả hai sẽ đếm trùng. |

### 3.6. Thêm & bớt công việc

| Mã | Quy tắc |
| :--- | :--- |
| **BR-S40** | Không thêm/bớt công việc ở Sprint đã `CLOSED`. |
| **BR-S41** | Công việc phải **thuộc cùng dự án** với Sprint. |
| **BR-S42** | Thao tác **bất biến (idempotent)**: thêm lại công việc đã có trong Sprint không gây lỗi và không đổi gì. |
| **BR-S43** | Mọi thay đổi Sprint đều được ghi nhật ký **hai lớp**: một bản dễ đọc cho người dùng (tên Sprint, hiển thị ở tab Lịch sử) và một bản định danh bền vững (mã Sprint) dành cho báo cáo. Lý do: Sprint có thể **đổi tên**, nên đối chiếu theo tên là không đáng tin. |

---

## 4. Nghiệp vụ Backlog

| Mã | Quy tắc |
| :--- | :--- |
| **BR-B01** | Backlog gồm công việc của dự án **chưa thuộc Sprint nào**. |
| **BR-B02** | Màn hình Backlog hiển thị: Sprint `ACTIVE` (nếu có) → các Sprint `FUTURE` theo ngày bắt đầu → khối Backlog. **Sprint đã đóng không xuất hiện** (chúng thuộc màn hình Báo cáo). |
| **BR-B03** | **Sub-task không đứng thành hàng riêng** trên Backlog — chúng cuộn theo công việc cha (xem BR-S33). |
| **BR-B04** | Thứ tự công việc do người dùng quyết định bằng kéo thả, và **kéo một thẻ chỉ được cập nhật đúng thẻ đó** — không đụng tới các thẻ xung quanh. |
| **BR-B05** | Bộ lọc (tìm kiếm, Epic, người thực hiện, việc của tôi, mới cập nhật) chỉ **thu hẹp hiển thị**, không làm thay đổi dữ liệu. |
| **BR-B06** | Cho phép tạo nhanh công việc ngay trong từng khối; công việc tạo trong khối Sprint nào sẽ thuộc Sprint đó. |
| **BR-B07** | Toàn bộ màn hình được nạp trong **một lần gọi duy nhất** để không nhảy giao diện khi tải. |

---

## 5. Nghiệp vụ Bảng công việc (Board)

### 5.1. Phạm vi hiển thị

| Mã | Quy tắc |
| :--- | :--- |
| **BR-K01** | Board **SCRUM** chỉ hiển thị công việc thuộc **Sprint đang chạy**. Không có Sprint nào chạy → bảng trống kèm lời mời sang Backlog. |
| **BR-K02** | Board **KANBAN** hiển thị công việc toàn dự án, không phụ thuộc Sprint. |
| **BR-K03** | Mỗi cột ánh xạ tới một hoặc nhiều trạng thái. **Một trạng thái không được thuộc hai cột** — nếu không, hệ thống không biết thả thẻ vào đâu. |
| **BR-K04** | Công việc có trạng thái **chưa được gán cột nào** phải được cảnh báo rõ cho người dùng, tuyệt đối không âm thầm biến mất khỏi bảng. |

### 5.2. Kéo thả

| Mã | Quy tắc |
| :--- | :--- |
| **BR-K10** | Kéo thẻ **trong cùng một cột** chỉ đổi thứ tự — không đổi trạng thái, không kiểm tra WIP, không ghi nhật ký trạng thái. |
| **BR-K11** | Kéo thẻ **sang cột khác** tương đương một **bước chuyển trạng thái** và **phải đi qua máy trạng thái workflow**. Không được ghi thẳng trạng thái: làm vậy sẽ bỏ qua mọi ràng buộc (việc tiền đề, Sub-task chưa xong), bỏ ghi nhật ký, và làm hỏng dữ liệu nguồn của Burndown. |
| **BR-K12** | Nếu **không có bước chuyển hợp lệ** từ trạng thái hiện tại sang cột đích → từ chối và giải thích. Phân biệt rõ hai trường hợp: *quy trình không có đường đi* (lỗi 400) và *người dùng không đủ điều kiện* (lỗi 403). |
| **BR-K13** | Giao diện phải **vô hiệu hoá trước** các cột không thể tới được, thay vì để người dùng kéo rồi mới báo lỗi. |
| **BR-K14** | Thẻ phải di chuyển **tức thì trên giao diện** rồi mới đồng bộ với máy chủ. Nếu máy chủ từ chối, thẻ **trượt về đúng vị trí cũ** kèm thông báo. |
| **BR-K15** | Nếu thẻ vừa bị người khác thay đổi (xung đột phiên bản) → lỗi **409**, thẻ trượt về và bảng tự tải lại. |

### 5.3. Giới hạn WIP

| Mã | Quy tắc |
| :--- | :--- |
| **BR-K20** | Cột có **WIP tối đa** sẽ **chặn** việc kéo thêm thẻ vào khi đã đạt giới hạn. |
| **BR-K21** | Chỉ chặn khi thẻ **đi vào** cột. Sắp xếp trong cột, hoặc kéo thẻ **ra khỏi** cột đang đầy, luôn được phép — nếu không người dùng sẽ bị kẹt. |
| **BR-K22** | **WIP tối thiểu** chỉ là **cảnh báo** (dấu hiệu nghẽn luồng), không bao giờ chặn. |
| **BR-K23** | Cho phép người có quyền **cố tình vượt** giới hạn WIP khi cần, và thao tác đó được ghi nhận. |
| **BR-K24** | Bảng luôn trả về số đếm hiện tại của từng cột để giao diện hiển thị `(hiện tại / tối đa)` và tô cảnh báo, **không cần thử kéo mới biết**. |

### 5.4. Swimlane

| Mã | Quy tắc |
| :--- | :--- |
| **BR-K30** | Tại một thời điểm chỉ áp dụng **một** chiến lược phân luồng. |
| **BR-K31** | Hỗ trợ: theo **Epic**, theo **Người thực hiện**, theo **Độ ưu tiên**. Không cấu hình gì → một luồng duy nhất. |
| **BR-K32** | Luôn có luồng cuối cho phần "không thuộc nhóm nào" (chưa có Epic / chưa phân công). |
| **BR-K33** | Phân luồng theo Độ ưu tiên hiển thị **đủ mọi mức**, kể cả mức đang rỗng. |
| **BR-K34** | Chiến lược phân luồng chưa được hỗ trợ **không được làm hỏng bảng** — hạ cấp về một luồng và báo "chưa hỗ trợ". |

---

## 6. Nghiệp vụ Báo cáo Agile

| Mã | Quy tắc |
| :--- | :--- |
| **BR-R01** | Sprint **đã đóng** đọc số liệu từ **snapshot** đã chụp — số liệu lịch sử không được thay đổi theo thời gian. |
| **BR-R02** | Sprint **đang chạy** dựng biểu đồ bằng cách **phát lại nhật ký thay đổi**, cho phép xem Burndown ngay giữa Sprint. |
| **BR-R03** | Mỗi Sprint có **đúng một** snapshot. Đóng lại Sprint phải **ghi đè**, không tạo bản trùng. |
| **BR-R04** | **Burndown**: trục ngang là ngày trong Sprint, trục dọc là SP còn lại. Gồm đường **lý thuyết** (giảm đều về 0), đường **thực tế** (bậc thang), và vùng **thay đổi phạm vi**. |
| **BR-R05** | Đường lý thuyết tính theo **ngày làm việc**; cuối tuần đi ngang. |
| **BR-R06** | **Sub-task bị loại khỏi mọi phép tính điểm** (BR-S34). |
| **BR-R07** | Công việc **được thêm vào giữa Sprint** chỉ tính từ thời điểm nó thực sự thuộc Sprint, và được đánh dấu là *thay đổi phạm vi*. |
| **BR-R08** | **Velocity** liệt kê N Sprint đã đóng gần nhất, so sánh **cam kết** với **hoàn thành**. |
| **BR-R09** | **CFD** hiển thị số lượng công việc cộng dồn theo từng nhóm trạng thái qua thời gian. |
| **BR-R10** | Sprint đóng **trước khi** có tính năng báo cáo vẫn phải xem được — dựng lại từ nhật ký và **nói rõ** đây là số liệu tái dựng, không phải snapshot gốc. |

---

## 7. Quy tắc dữ liệu xuyên suốt

| Mã | Quy tắc |
| :--- | :--- |
| **BR-X01** | **Thứ tự công việc** dùng cơ chế xếp hạng cho phép chèn giữa hai thẻ bất kỳ mà chỉ ghi đúng một bản ghi — không đánh lại số thứ tự cả danh sách. |
| **BR-X02** | **Chống ghi đè khi đồng thời**: mỗi lần sửa đều kèm số phiên bản. Phiên bản lệch → từ chối (409) và yêu cầu tải lại, thay vì âm thầm đè mất thay đổi của người khác. |
| **BR-X03** | Công việc xoá mềm **không xuất hiện** ở Backlog, Board, số đếm WIP hay báo cáo. |
| **BR-X04** | Mọi thông báo lỗi hướng tới người dùng phải bằng **tiếng Việt**, nói rõ *cái gì sai* và *làm gì tiếp theo*. |
| **BR-X05** | Sự kiện nghiệp vụ được ghi cùng giao dịch dữ liệu. **Lỗi ghi sự kiện không được làm hỏng nghiệp vụ chính.** |

---

## 8. Ma trận nghiệm thu & Trạng thái triển khai

| Nhóm | Quy tắc | Trạng thái | Ghi chú |
| :--- | :--- | :---: | :--- |
| Quyền | BR-P01 → BR-P04 | 🟢 Đã xong | Đã kiểm thử thật: truy cập Sprint trả 200, Sprint không tồn tại trả 404 |
| Sprint — tạo/sửa/xoá | BR-S01 → BR-S05 | 🟢 Đã xong | Có unit test |
| Sprint — bắt đầu | BR-S10 → BR-S14 | 🟢 Đã xong | Đã kiểm thử thật quy tắc 409 một-Sprint-ACTIVE và chặn Sprint rỗng |
| Sprint — hoàn tất | BR-S20 → BR-S28 | 🟢 Đã xong | Đã kiểm thử thật: việc DONE ở lại, việc dở sang Sprint 2, snapshot ghi đúng |
| Sprint — liên tầng | BR-S30 → BR-S34 | 🟢 Đã xong | Có unit test cascade Sub-task |
| Sprint — thêm/bớt việc | BR-S40 → BR-S43 | 🟢 Đã xong | Đã kiểm thử thật nhật ký hai lớp |
| Backlog | BR-B01 → BR-B07 | 🟢 Đã xong | API 1-lần-gọi đã kiểm thử thật; màn hình Backlog chạy được trên trình duyệt (tạo → bắt đầu Sprint) |
| Board — hiển thị | BR-K01 → BR-K04 | 🟢 Đã xong | Bảng SCRUM lọc theo Sprint đang chạy; cảnh báo trạng thái chưa gán cột |
| Board — kéo thả | BR-K10 → BR-K15 | 🟢 Đã xong | Đã kiểm thử thật: đổi cột đi qua workflow (changelog STATUS), sắp xếp cùng cột bỏ qua workflow, phiên bản cũ → 409 |
| Board — WIP | BR-K20 → BR-K24 | 🟢 Đã xong | Đã kiểm thử thật chặn ở 1/1 và cờ bỏ qua giới hạn |
| Board — Swimlane | BR-K30 → BR-K34 | 🟢 Đã xong | Có unit test cho cả 4 chiến lược, CUSTOM_JQL hạ cấp không lỗi |
| Báo cáo | BR-R01 → BR-R10 | 🟢 Đã xong | Burndown / Velocity / CFD đã kiểm thử thật; Sprint đang chạy dựng lại từ changelog, Sprint đã đóng đọc snapshot |
| Dữ liệu xuyên suốt | BR-X01 → BR-X05 | 🟢 Đã xong | Xếp hạng, chống ghi đè 409, xoá mềm, sự kiện outbox |

> **Cập nhật 28/09/2026:** toàn bộ Giai đoạn 6 (6.1 Sprint & Backlog, 6.2 Board Engine, 6.3 Báo cáo Agile) đã triển khai xong cả backend lẫn giao diện. 147 unit test đạt, các luồng chính đã kiểm thử thật trên PostgreSQL và trình duyệt.

---

## 9. Thông điệp lỗi chuẩn

| Tình huống | Mã HTTP | Thông điệp |
| :--- | :---: | :--- |
| Bắt đầu Sprint khi đã có Sprint chạy | 409 | *Dự án đã có Sprint đang chạy: '<tên>'. Hãy đóng Sprint đó trước khi bắt đầu Sprint mới.* |
| Bắt đầu Sprint rỗng | 400 | *Không thể bắt đầu một Sprint rỗng. Hãy kéo công việc vào Sprint trước.* |
| Bắt đầu Sprint thiếu ngày kết thúc | 400 | *Ngày kết thúc Sprint là bắt buộc để theo dõi tiến độ Burndown.* |
| Hoàn tất Sprint chưa chạy | 400 | *Chỉ Sprint đang chạy mới có thể đóng.* |
| Chọn Sprint đích đã đóng | 400 | *Không thể chuyển công việc sang một Sprint đã đóng.* |
| Xoá Sprint đang chạy / đã đóng | 400 | *Chỉ có thể xoá Sprint chưa bắt đầu.* |
| Kéo Sub-task riêng lẻ | 400 | *Sub-task đi theo Sprint của công việc cha.* |
| Vượt giới hạn WIP | 400 | *Cột '<tên>' đã đạt giới hạn WIP tối đa (<n>). Hãy hoàn thành công việc đang dở trước khi kéo thêm thẻ.* |
| Cột chưa ánh xạ trạng thái | 400 | *Cột '<tên>' chưa được ánh xạ với trạng thái nào.* |
| Không có bước chuyển hợp lệ | 400 | *Không có bước chuyển hợp lệ từ '<trạng thái>' sang cột '<tên cột>'.* |
| Không đủ điều kiện chuyển | 403 | *Bạn không đủ điều kiện để chuyển '<mã>' sang cột '<tên cột>'.* |
| Xung đột phiên bản | 409 | *Dữ liệu đã được người khác cập nhật. Vui lòng tải lại và thử lại.* |
| Không tìm thấy Sprint/Board | 404 | *Không tìm thấy Sprint: <id>* |

---

## 10. Giả định & Điểm cần quyết định

1. **Sprint không mở lại được.** Jira cho phép; ở đây thì không, vì snapshot đã chốt. Nếu nghiệp vụ cần, phải bổ sung quy tắc xử lý snapshot cũ.
2. **Quản trị Sprint mặc định chỉ dành cho Project Administrator.** Nếu muốn có vai trò *Scrum Master* riêng thì cần thêm vai trò mới, không nên nới quyền của Member.
3. **Một dự án một Sprint ACTIVE.** Mô hình đa nhóm (nhiều nhóm chạy Sprint song song trong một dự án) chưa được hỗ trợ.
4. **Board chưa gắn với Sprint cụ thể.** Board SCRUM luôn lấy Sprint đang chạy; muốn xem Sprint khác phải chỉ định rõ khi truy vấn.
5. **Phân luồng theo truy vấn tuỳ biến (JQL)** phụ thuộc Giai đoạn 8, hiện hạ cấp về một luồng.
