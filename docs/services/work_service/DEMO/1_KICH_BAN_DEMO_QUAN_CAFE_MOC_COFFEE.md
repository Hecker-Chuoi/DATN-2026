# KỊCH BẢN DEMO WORK-SERVICE: DỰ ÁN "MỘC COFFEE", QUÁN CÀ PHÊ KHỞI NGHIỆP

> Phạm vi: chỉ các tính năng **đã có code** trong `work-service` + màn hình dự án ở `frontend`.
> Slide đi kèm: [2_SLIDE_TINH_NANG_WORK_SERVICE.html](2_SLIDE_TINH_NANG_WORK_SERVICE.html).
> Mẫu dùng trong demo: **"Xây dựng Cửa hàng F&B (Nhà hàng - Quán Café)"** (`TPLFNB`, seed ở `V7__seed_default_templates.sql`).

---

## 0. Tổng quan buổi demo

| Mục | Nội dung |
|---|---|
| Câu chuyện | An mở quán **Mộc Coffee** tại nhà riêng, cần lập kế hoạch 60 ngày từ khảo sát đến khai trương và giao việc cho cộng sự. |
| Mạch trình bày | Mỗi tính năng đi theo 3 nhịp: **Nguyên lý** (nói 20–40 giây) → **Demo nhanh** (thao tác) → **Kết quả** (chỉ cho hội đồng thấy). |
| Thời lượng | ~22 phút (bảng thời gian ở mục 3; bản rút gọn 15 phút cũng ở đó). |
| Dữ liệu | Không nhập tay từ đầu: toàn bộ dự án sinh ra từ mẫu F&B, chỉ tùy chỉnh **2 điểm** trước khi tạo. |
| Chuyển trạng thái | Không dùng Bảng tiến độ. Mọi lần chuyển trạng thái làm trong **ô trạng thái** của khung chi tiết công việc: danh sách hiện các bước chuyển hợp lệ dạng *"Tên bước chuyển (➔ Trạng thái đích)"*. |

### Nhân vật (3 tài khoản, cùng một workspace)

| Nhân vật | Vai trò trong dự án | Dùng để chứng minh |
|---|---|---|
| **An**, chủ quán | Project Lead → `ADMINISTRATOR` | Tạo dự án, cấu hình, lập kế hoạch giai đoạn, xem đóng góp cả nhóm |
| **Bình**, barista trưởng | `MEMBER` | Người được giao việc, nhận điểm đóng góp, chỉ xem đóng góp của riêng mình |
| **Chi**, nhà đầu tư góp vốn | `VIEWER` | Chỉ xem, không chuyển trạng thái / tạo việc được |

---

## 1. Chuẩn bị trước buổi demo (checklist)

- [ ] Hạ tầng chạy: `docker compose -f docker-compose.tuananh.yml up -d` (DB, Keycloak, Redis, MinIO, Mailpit…).
- [ ] Service chạy: identity, gateway, **work-service**, workspace-service, notification-service (IntelliJ, profile `tuananh`).
- [ ] Frontend `npm run dev`, đăng nhập được bằng 3 tài khoản An / Bình / Chi.
- [ ] Bình và Chi **đã là thành viên workspace** (mời workspace thuộc workspace-service, không demo).
- [ ] Mở thư viện mẫu, kiểm tra mẫu F&B hiện băng **xanh (hợp lệ)**.
- [ ] Mở sẵn 3 cửa sổ trình duyệt: An (thường), Bình (ẩn danh), Chi (profile khác). Tab thứ 4: Mailpit http://localhost:8025.
- [ ] Chuẩn bị 1 ảnh `mat-bang.jpg` để đính kèm.
- [ ] **Phương án B cho báo cáo:** tạo trước 3–5 ngày một bản "Mộc Coffee (bản chạy thật)" và mỗi ngày chuyển trạng thái vài việc, để Burndown/CFD có hình dạng thật. Demo trực tiếp trong một ngày chỉ có 1 điểm dữ liệu.

---

## 2. Kịch bản chi tiết

### PHẦN 1: Dự án mẫu và Tùy chỉnh nhanh (3 phút) · An

**Nguyên lý (nói):**
- Dự án mẫu **chính là một Project** có `is_template = true`, nên mang đủ trạng thái, workflow, vai trò, giai đoạn, công việc. Không phải mô hình dữ liệu riêng.
- Tạo từ mẫu = **sao chép sâu** (deep-clone) qua `ProjectCloneEngine`: cấp UUID mới, nối lại mọi quan hệ, **dời toàn bộ ngày** theo ngày bắt đầu mới (giữ nguyên độ dài giai đoạn), đặt lại tiến độ về "Cần làm".
- **Kiểm tra toàn vẹn** chạy 4 lần (đánh dấu mẫu, preview, trước clone, sau clone); có lỗi ERROR thì khóa nút hoặc rollback.
- **Tùy chỉnh nhanh** gửi kèm trong **1 request / 1 transaction**: đổi tên, bỏ bớt giai đoạn/công việc. Bỏ công việc cha thì **con tự bỏ theo** (cascade exclude).

**Demo:**
1. Trang Dự án → **Tạo dự án** → tab **Chọn mẫu dự án** → bấm thẻ **"Xây dựng Cửa hàng F&B"**.
2. Lướt nhanh 4 tab Preview: *Tổng quan* (Gantt mini 3 giai đoạn), *Trạng thái & Quy trình*, *Chu kỳ & Công việc*, *Phân quyền*. Chỉ băng kiểm tra toàn vẹn màu xanh.
3. Tab **Chu kỳ & Công việc**, làm **2 tùy chỉnh**:
   - **Tùy chỉnh 1 (bỏ qua + cascade):** bấm thùng rác ở công việc **"Thương lượng hợp đồng thuê và thủ tục đặt cọc mặt bằng"**.
     *Lời dẫn: "Quán mở tại nhà riêng nên không cần thuê mặt bằng."* → 2 việc con (đàm phán miễn phí thuê, công chứng hợp đồng) tự gạch ngang, ghi **"(Bỏ qua theo cha)"**.
   - **Tùy chỉnh 2 (đổi tên):** bút chì ở **"Chốt danh mục đồ uống chủ lực, công thức chuẩn hóa và tính giá vốn (Costing)"** → đổi thành **"Chốt menu signature: cà phê muối, bạc xỉu, cold brew"**.
4. Banner hiện *"✨ Đã tùy chỉnh: 2 thay đổi"* → bấm **Dùng mẫu này (2 tùy chỉnh)**.
5. Form: Tên **Mộc Coffee**, Mã **MOC**, Ngày bắt đầu **hôm nay** (hạn chót tự gợi ý). Bấm **← Quay lại xem mẫu** một lần để chứng minh tùy chỉnh **không mất**, rồi quay lại form.
6. Bấm **Tạo dự án từ mẫu**.

**Kết quả cần chỉ ra:**
- Toast báo số trạng thái / giai đoạn / công việc đã sao chép; điều hướng vào dự án `MOC`.
- **23 công việc** (26 của mẫu − 3 bị bỏ), mã đánh liên tục `MOC-1..MOC-23`; 3 giai đoạn đã dời lịch theo hôm nay.
- Tên công việc đã đổi xuất hiện đúng; không còn việc "thuê mặt bằng".

<details>
<summary>Bảng mã công việc dự kiến sau khi tạo (đối chiếu lại trên màn hình)</summary>

Cột "Loại" ghi đúng tên hiển thị trên giao diện: Epic = hạng mục lớn, Task = công việc, Subtask = công việc con.

| Mã | Công việc | Loại | Giai đoạn | Điểm |
|---|---|---|---|---|
| MOC-1 | Khảo sát vị trí, Thuê mặt bằng & Pháp lý kinh doanh | Epic | – | – |
| MOC-2 | Khảo sát lưu lượng khách (foot traffic) và lựa chọn mặt bằng | Task | GĐ1 | 5 |
| MOC-3 | Đo đếm lượt khách qua lại theo các khung giờ… | Subtask của MOC-2 | GĐ1 | – |
| MOC-4 | Khảo sát chỗ để xe máy, bãi đỗ ô tô và lối thoát hiểm PCCC | Subtask của MOC-2 | GĐ1 | – |
| MOC-5 | Nộp hồ sơ ĐKKD và Giấy chứng nhận Đủ điều kiện ATTP | Task | GĐ1 | 5 |
| MOC-6 | Thiết kế kiến trúc, Thi công nội thất & Biển bảng | Epic | – | – |
| MOC-7 | Thiết kế bản vẽ mặt bằng 2D và phối cảnh 3D | Task | GĐ1 | 8 |
| MOC-8..10 | Thi công phần thô / quầy bar / biển hiệu | Task | GĐ2 | 8 / 5 / 3 |
| MOC-11 | Xây dựng Thực đơn, Định lượng & Nhập trang thiết bị | Epic | – | – |
| MOC-12 | **Chốt menu signature: cà phê muối, bạc xỉu, cold brew** | Task | GĐ2 | 5 |
| MOC-13..15 | Máy pha / Nguyên liệu & bao bì / POS | Task | GĐ2 | 5 / 3 / 3 |
| MOC-16..19 | Epic Tuyển dụng & Đào tạo + 3 Task | Epic/Task | GĐ3 | – / 5 / 8 / 3 |
| MOC-20..23 | Epic Truyền thông & Khai trương + 3 Task | Epic/Task | GĐ3 | – / 5 / 8 / 5 |

</details>

---

### PHẦN 2: Thành viên và Phân quyền (2,5 phút) · An → Bình, Chi

**Nguyên lý:**
- Phân quyền **theo từng dự án, mặc định từ chối** (fail-closed). Nguồn sự thật là 15 quyền + ma trận *vai trò × quyền* của dự án.
- 3 vai trò mặc định: **ADMINISTRATOR** (toàn quyền), **MEMBER** (tạo/sửa/chuyển trạng thái), **VIEWER** (chỉ xem + bình luận). Lead/người tạo có mọi quyền; Global ADMIN bỏ qua kiểm tra.
- Kiểm tra ở **backend** cho mọi API; frontend chỉ ẩn nút cho gọn, không phải lớp bảo vệ.
- Thêm thành viên = **gửi lời mời**, chấp nhận mới được gán vai trò; email gửi qua notification-service.

**Demo:**
1. Tab **Thành viên & Vai trò** → mời **Bình** (vai trò MEMBER) và **Chi** (VIEWER) từ danh sách *Thành viên Workspace* → **Gửi lời mời tham gia**.
2. Mở **Mailpit** → thấy 2 email lời mời.
3. Cửa sổ Bình: biểu tượng lời mời → **Chấp nhận**. Cửa sổ Chi: làm tương tự.
4. Quay lại An: kéo xuống **"Phân quyền chi tiết theo Vai trò"**, chỉ ma trận quyền 3 vai trò.
5. Cửa sổ **Chi**: mở một công việc bất kỳ → ô trạng thái → chọn một bước chuyển → **bị từ chối** (Viewer không có quyền chuyển trạng thái).

**Kết quả:** Bình và Chi xuất hiện trong danh sách thành viên với đúng vai trò; Viewer bị backend chặn khi cố thay đổi.

---

### PHẦN 3: Loại công việc 3 cấp + Workflow (2,5 phút) · An

**Nguyên lý:**
- **Loại công việc 3 cấp**: Cấp 1 **hạng mục lớn** (không có cha) → Cấp 0 **công việc** (đứng riêng hoặc thuộc hạng mục lớn) → Cấp −1 **công việc con** (bắt buộc thuộc một công việc). Hệ thống chặn mọi cây sai: công việc con gắn thẳng vào hạng mục lớn, công việc con lồng trong công việc con. *(Trên giao diện 3 loại này tên là Epic / Task / Subtask.)*
- **Trạng thái** tùy chỉnh theo dự án, mỗi trạng thái thuộc 1 trong 3 nhóm, báo cáo dựa vào nhóm chứ không dựa vào tên. Với mẫu F&B:

  | Nhóm | Trạng thái |
  |---|---|
  | TODO (chưa làm) | Cần làm |
  | IN PROGRESS (đang làm) | Đang thực hiện, Nghiệm thu / Đánh giá |
  | DONE (đã xong) | Hoàn thành |

- **Workflow = máy trạng thái hữu hạn**: chỉ đi được theo các *bước chuyển* đã khai báo. Mỗi bước chuyển gắn được **điều kiện** (ai được làm, ví dụ chỉ người được giao), **kiểm tra** (dữ liệu phải đủ, ví dụ việc con phải xong), **hậu xử lý** (ghi nhật ký).

**Demo:**
1. Tab **Cấu hình Dự án** → **Sơ đồ Quy trình & Trạng thái**: chỉ 4 trạng thái *Cần làm → Đang thực hiện → Nghiệm thu / Đánh giá → Hoàn thành* và các mũi tên (*Yêu cầu chỉnh sửa*, *Hoàn tất trực tiếp*, *Mở lại công việc*).
   *Nhấn mạnh: **không có** mũi tên Đang thực hiện → Hoàn thành, nên mọi việc đang làm phải qua Nghiệm thu.*
2. Mở sửa bước chuyển **"Gửi nghiệm thu"** → chỉ ô **"Chỉ người được giao việc mới được chuyển"** để giới thiệu điều kiện → **đóng, không lưu** (giữ quy trình gốc cho các phần sau).
3. Tab **Phân bổ Loại Công việc**: chỉ 3 loại với cấp 1 / 0 / −1 và workflow đang áp dụng.

**Kết quả:** Hội đồng thấy quy trình là cấu hình của từng dự án, không phải code cứng.

---

### PHẦN 4: Kho việc và Lập kế hoạch giai đoạn (3 phút) · An

**Nguyên lý:**
- Backlog = các việc chưa vào giai đoạn; giai đoạn (sprint) là khung thời gian có mục tiêu.
- Thứ tự ưu tiên lưu bằng **LexoRank** (chuỗi xếp hạng): kéo thả chỉ cập nhật **1 dòng**, không phải đánh số lại cả danh sách.
- Luật giai đoạn: chỉ **1 giai đoạn chạy** mỗi lúc; các giai đoạn **bắt đầu lần lượt** theo ngày dự kiến (chỉ giai đoạn đứng đầu mới bấm được *Bắt đầu*); bắt buộc ngày kết thúc (cho Burndown); tối đa **8 tuần**; giai đoạn đã đóng thì khóa; **dự án mẫu không được bắt đầu giai đoạn**.
- Điểm ước lượng theo thang **Fibonacci 1-2-3-5-8-13**: số càng lớn việc càng nặng và càng khó ước lượng chính xác.

**Demo:**
1. Tab **Kho việc & Kế hoạch**: 3 giai đoạn đều ở trạng thái *Dự kiến*. Chỉ nút **Bắt đầu giai đoạn** của Giai đoạn 1 sáng; rê chuột lên nút đang khóa của Giai đoạn 2 → tooltip *"Cần bắt đầu 'Giai đoạn 1: …' trước"*.
2. Tạo nhanh trong Backlog (inline): công việc **"Khảo sát 5 quán cà phê đối thủ trong bán kính 500m"**, chọn **3 điểm**, cha = hạng mục lớn **MOC-1** → được mã **MOC-24**.
3. Kéo **MOC-24** vào **Giai đoạn 1**; kéo đổi thứ tự lên đầu (LexoRank).
4. Bấm **Bắt đầu giai đoạn** ở Giai đoạn 1.
5. *(Tùy chọn, 15 giây)* Rê chuột lên nút **Bắt đầu giai đoạn** của Giai đoạn 2 → vẫn khóa, giờ tooltip báo *đang có "Giai đoạn 1…" chạy, hãy hoàn tất trước*.

**Kết quả:** Giai đoạn 1 chuyển *Đang chạy*, gồm MOC-2, 3, 4, 5, 7, 24 (21 điểm).

---

### PHẦN 5: Chi tiết công việc (2,5 phút) · An

**Nguyên lý:**
- Mỗi công việc có: người thực hiện, độ ưu tiên, điểm, hạn chót, thành phần, theo dõi thời gian (ước lượng / còn lại).
- **Bình luận**, **tệp đính kèm** (lưu trên MinIO), **lịch sử thay đổi** (ghi mọi lần đổi trường/trạng thái: ai, lúc nào, cũ → mới).
- Quyền theo sở hữu: chỉ sửa/xóa **bình luận, tệp của chính mình**.

**Demo:**
1. Mở **MOC-2**: giao cho **Bình**, độ ưu tiên Cao. Chỉ 2 việc con MOC-3, MOC-4 trong khối việc con.
2. Giao **MOC-5** cho Bình, **MOC-24** cho An.
3. Tab **Bình luận** của MOC-2: *"Ưu tiên khung 7h–9h sáng, giờ cao điểm cà phê."*
4. Tab **Tệp đính kèm**: tải `mat-bang.jpg`.
5. Tab **Lịch sử**: chỉ các dòng vừa thay đổi (người thực hiện, ưu tiên…).

**Kết quả:** Thông tin cập nhật ngay; lịch sử ghi đủ.

---

### PHẦN 6: Liên kết công việc (2,5 phút) · An

**Nguyên lý: 4 loại liên kết và tác dụng**

| Loại | Tác dụng |
|---|---|
| **Cản trở** | Việc bị cản trở **không được bắt đầu hay hoàn thành** cho đến khi việc cản trở xong. Hiện nhãn đỏ "đang bị cản trở"; cảnh báo nếu việc cản trở có hạn chót muộn hơn ngày bắt đầu / hạn chót của việc này. |
| **Gây ra** | Ghi nguyên nhân → hệ quả để truy vết (sự cố này gây ra việc kia). Không chặn chuyển trạng thái. |
| **Trùng lặp** | Đánh dấu việc bị trùng → việc đó **tự chuyển Hoàn thành** với kết quả "Trùng lặp", có ghi lịch sử. |
| **Liên quan** | Chỉ để tham chiếu qua lại, không ràng buộc. |

Luật chung:
- Cản trở và Gây ra **không được tạo vòng lặp** (A → B → A): hệ thống từ chối ngay và chỉ ra vòng lặp.
- Không liên kết với chính nó, với cha/con ruột (đã có quan hệ cha–con), với việc đã xóa; không tạo trùng một liên kết đã có. Cản trở không nối thẳng hạng mục lớn với công việc con (lệch cấp).
- Mỗi liên kết ghi vào lịch sử của **cả hai** việc.

**Demo:**
1. Mở **MOC-2** → **Liên kết** → kiểu *Cản trở / Chặn tiến độ* → chọn **MOC-7** (Thiết kế 2D/3D).
   *Lời dẫn: "Chưa chốt mặt bằng thì chưa thể vẽ thiết kế."*
2. Mở **MOC-7**: banner đỏ **"CÔNG VIỆC ĐANG BỊ CẢN TRỞ"** liệt kê MOC-2.
3. Vẫn ở MOC-7 → ô trạng thái → **Bắt đầu triển khai** → báo lỗi *"Không thể chuyển sang trạng thái 'Đang thực hiện' do công việc đang bị chặn bởi: MOC-2 - …"*.
4. Thử ngược lại: từ MOC-7 tạo liên kết *Cản trở* tới MOC-2 → bị từ chối *"Phát hiện chu trình phụ thuộc vòng lặp (Deadlock): …"*.

**Kết quả:** Hệ thống hiểu và bảo vệ thứ tự công việc. MOC-7 sẽ tự hết bị cản trở khi MOC-2 xong (Phần 7, bước 3).

> ⚠ **Không bấm thử liên kết "Trùng lặp" trên dự án demo.** Code hiện chọn trạng thái "Hoàn thành" đầu tiên tìm được trong **toàn bộ DB** (`statusRepository.findByCategory(DONE)` trong `WorkItemLinkServiceImpl`), không lọc theo dự án, nên việc bị trùng có thể nhận trạng thái của dự án khác. Chỉ nói nguyên lý.

---

### PHẦN 7: Ghi nhận đóng góp (4 phút) · An, Bình

**Nguyên lý (khớp slide "Ghi nhận đóng góp"):**

1. **Ghi điểm thế nào**
   - Việc chuyển sang **Hoàn thành** → người **đang được giao** nhận điểm của việc.
   - Việc có việc con → việc cha **0 điểm**, điểm tính ở việc con (không tính 2 lần).
   - Việc xong mà chưa giao cho ai → không ai nhận, hệ thống cảnh báo.
   - Sổ ghi công **chỉ ghi thêm**: mỗi lần cộng/trừ là 1 dòng mới, không sửa, không xóa dòng cũ, nên truy vết được.
2. **Khi mở lại việc**
   - Việc đã Hoàn thành bị mở lại → ghi 1 dòng **trừ đúng số điểm** đã cộng lần trước, trừ vào người đã nhận.
   - Hoàn thành lại → cộng lại, trễ hạn tính theo **ngày hoàn thành mới**.
   - Xóa việc đã có điểm → trừ hết phần điểm đã cộng.
3. **Khi chuyển việc cho người khác**
   - Việc **chưa xong**: chưa ai có điểm; ai đang được giao **lúc hoàn thành** thì người đó nhận.
   - Việc **đã xong** rồi mới đổi người: điểm **vẫn ở người cũ**.
   - Sau đó nếu mở lại: người cũ bị trừ; hoàn thành lại thì **người mới** nhận.
4. **Công thức**
   - **Điểm nhận = Điểm của việc × (100% − Phạt trễ hạn)**, phạt **10% mỗi ngày trễ, tối đa 50%**.
   - Đúng hạn 100% · trễ 1 ngày 90% · trễ 2 ngày 80% · trễ từ 5 ngày 50%.

Nói thêm nếu còn thời gian: Trưởng dự án xem cả nhóm, thành viên chỉ xem phần của mình; đóng giai đoạn thì số liệu được chốt lại. Điều chỉnh điểm tay (tối đa ±8, bắt buộc lý do) đã có API nhưng **nút trên giao diện đang ẩn**, không demo.

**Demo** (mọi lần chuyển trạng thái: mở việc → ô trạng thái → chọn bước chuyển):
1. **Trễ hạn:** mở **MOC-5** (giao Bình, 5 điểm) → đặt **Hạn chót = hôm kia** → chọn **Hoàn tất trực tiếp (➔ Hoàn thành)**.
2. **Đúng hạn:** mở **MOC-24** (giao An, 3 điểm, còn hạn) → lần lượt **Bắt đầu triển khai** → **Gửi nghiệm thu** → **Nghiệm thu đạt chuẩn**.
3. *(Tùy chọn, 20 giây: việc cha 0 điểm)* Mở **MOC-2** (giao Bình) → **Hoàn tất trực tiếp** → vì MOC-3, MOC-4 chưa xong nên hệ thống hỏi → **Hoàn thành tất cả công việc con**. Mở lại MOC-7: hết nhãn "đang bị cản trở".
4. Tab **Đóng góp**: Bình **4.0** (MOC-5 *"Trễ 2 ngày (-20%)"*, MOC-2 được 0 điểm), An **3.0** (*"Đúng hạn"*). Nếu làm bước 3: có cảnh báo *việc hoàn thành chưa gán người* (MOC-3, MOC-4).
5. **Chuyển việc cho người khác + mở lại** (MOC-24):
   1. Đổi người thực hiện MOC-24 từ An sang **Bình** → tab Đóng góp **không đổi**: 3 điểm vẫn của An.
   2. Chọn **Mở lại công việc** → bấm **Sổ ghi công**: dòng **−3 "Bị mở lại"** trừ vào **An**.
   3. Chọn **Gửi nghiệm thu** → **Nghiệm thu đạt chuẩn** → dòng **+3 "Hoàn thành lại (Đúng hạn)"** ghi cho **Bình**.
6. Chuyển sub-tab **Biểu đồ trực quan**; bấm vào tên Bình để mở **chi tiết thành viên** (điểm theo tuần, danh sách việc).
7. Bấm **Xuất file Excel** (Excel đọc đúng tiếng Việt).
8. Cửa sổ **Bình** → tab Đóng góp: chỉ thấy số liệu của chính mình.

**Kết quả: sổ ghi công cuối phần**

| Việc | Người | Điểm | Ghi chú |
|---|---|---|---|
| MOC-5 | Bình | +4.0 | Trễ 2 ngày (-20%) |
| MOC-2 | Bình | 0 | Đúng hạn (việc có việc con → 0 điểm; chỉ có nếu làm bước 3) |
| MOC-24 | An | +3.0 | Đúng hạn |
| MOC-24 | An | −3.0 | Bị mở lại |
| MOC-24 | Bình | +3.0 | Hoàn thành lại (Đúng hạn) |

Tổng: **Bình 7.0, An 0**. Điểm minh bạch, truy vết được từng dòng.

> Nếu ô "Hạn chót" không cho chọn ngày quá khứ: chuẩn bị sẵn từ hôm trước một việc có hạn đã qua.

---

### PHẦN 8: Báo cáo Agile (2 phút) · An

**Nguyên lý:**
- **Burndown**: điểm còn lại theo ngày so với đường lý tưởng; dựng lại trạng thái từng ngày bằng cách **phát lại lịch sử thay đổi**, có đánh dấu **thay đổi phạm vi** khi thêm/bớt việc giữa chừng.
- **Velocity**: điểm cam kết và điểm hoàn thành của các giai đoạn đã đóng.
- **CFD (Cumulative Flow)**: số việc theo từng nhóm trạng thái qua thời gian, thấy được chỗ ùn ứ.
- Đóng giai đoạn thì **chốt số liệu**, báo cáo cũ không bị đổi khi dữ liệu sau này thay đổi.

**Demo:** Tab **Báo cáo** → chọn Giai đoạn 1 → lần lượt Burndown, CFD, Velocity.
*Dữ liệu một ngày quá mỏng, và Velocity cần giai đoạn đã đóng → chuyển sang dự án Phương án B (đã chạy vài ngày, có giai đoạn đã đóng).*

---

## 3. Bảng thời gian

| # | Phần | Phút | Tài khoản |
|---|---|---|---|
| 1 | Dự án mẫu + Tùy chỉnh nhanh | 3 | An |
| 2 | Thành viên & Phân quyền | 2,5 | An, Bình, Chi |
| 3 | Loại công việc 3 cấp + Workflow | 2,5 | An |
| 4 | Kho việc & Lập kế hoạch giai đoạn | 3 | An |
| 5 | Chi tiết công việc | 2,5 | An |
| 6 | Liên kết công việc | 2,5 | An |
| 7 | Ghi nhận đóng góp | 4 | An, Bình |
| 8 | Báo cáo Agile | 2 | An |
| | **Tổng** | **~22** | |

Nếu bị giới hạn 15 phút: giữ Phần 1, 3, 6, 7; các phần còn lại chỉ nói nguyên lý trên slide.

---

## 4. Sự cố thường gặp khi demo

| Hiện tượng | Xử lý nhanh |
|---|---|
| Ô trạng thái không có bước chuyển mong muốn | Việc đang ở trạng thái không có mũi tên đó (ví dụ Đang thực hiện → Hoàn thành). Đi đúng theo sơ đồ: Gửi nghiệm thu → Nghiệm thu đạt chuẩn. |
| Không thấy email lời mời | Mở Mailpit http://localhost:8025; gửi mail là bất đồng bộ, chờ 2–3 giây. |
| Nút "Bắt đầu giai đoạn" bị khóa | Rê chuột xem lý do: đang có giai đoạn khác chạy; **chưa tới lượt** (phải bắt đầu giai đoạn đứng trên trước; muốn đổi thứ tự thì bấm bút chì sửa ngày bắt đầu dự kiến; giai đoạn đứng trên rỗng và không cần nữa thì bấm thùng rác để xóa); hoặc giai đoạn chưa có việc. |
| Burndown/CFD gần như phẳng | Bình thường khi demo trong 1 ngày → dùng dự án Phương án B. |
| 401 liên tục | Token hết hạn → đăng nhập lại (interceptor tự refresh, nhưng refresh token cũng có hạn). |
| Mã công việc khác bảng dự kiến | Gọi theo **tên** công việc; bảng mã chỉ để tham khảo. |
| Lỡ tạo liên kết "Trùng lặp" | Việc bị trùng có thể mang trạng thái của dự án khác (lỗi đã nêu ở Phần 6). Xóa liên kết không tự sửa trạng thái; bỏ qua việc đó và tiếp tục. |

---

## 5. Câu hỏi hội đồng có thể hỏi

| Câu hỏi | Ý trả lời |
|---|---|
| Tạo dự án từ mẫu mà lỗi giữa chừng thì sao? | Toàn bộ chạy trong 1 transaction; kiểm tra toàn vẹn sau clone, lỗi thì rollback sạch. |
| Sao không cho thêm/xóa trạng thái trong lúc tùy chỉnh nhanh? | Trạng thái gắn với bước chuyển và cột hiển thị; thêm/xóa dễ gãy workflow. Đổi tên thì an toàn 100%. |
| Frontend ẩn nút thì có đủ bảo mật không? | Không dựa vào FE. Backend kiểm tra mọi API; không xác định được dự án thì **từ chối**. |
| Vì sao dùng LexoRank? | Kéo thả chỉ ghi 1 dòng thay vì đánh số lại N dòng; giảm xung đột khi nhiều người cùng sắp xếp. |
| Điểm đóng góp có bị sửa lén được không? | Sổ chỉ ghi thêm, mỗi thay đổi là một dòng có người thực hiện + thời điểm; điều chỉnh tay (API) giới hạn ±8 và bắt buộc lý do. |
| Đổi người thực hiện sau khi việc đã xong thì điểm tính cho ai? | Vẫn cho người đã hoàn thành. Chỉ khi việc bị mở lại thì người đó mới bị trừ, và người được giao lúc hoàn thành lại sẽ nhận điểm. |
| Vì sao việc cha có việc con lại 0 điểm? | Điểm đã nằm ở các việc con; cộng thêm cho cha là tính 2 lần cho cùng một khối lượng. |
| Sao không bấm bắt đầu Giai đoạn 3 trước được? | Giai đoạn là các chặng tuần tự: chỉ giai đoạn đứng đầu kế hoạch (ngày bắt đầu dự kiến sớm nhất) mới bắt đầu được. Backend cũng chặn (409), không chỉ khóa nút. Kế hoạch đổi thì sửa ngày dự kiến để đổi thứ tự. |
| Burndown tính thế nào khi thêm việc giữa giai đoạn? | Phát lại lịch sử thay đổi theo ngày; việc thêm/bớt sau khi bắt đầu được đánh dấu là thay đổi phạm vi. |
