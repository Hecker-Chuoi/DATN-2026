# Hướng Dẫn & Đặc Tả Điểm Ước Lượng (Story Points) trong DATN-2026

Tài liệu này tổng hợp toàn bộ nghiên cứu về **bản chất, mục đích sử dụng, thang đo và quy tắc nghiệp vụ** của **Story Point (Điểm ước lượng)**, đồng thời đặc tả cách tính năng này được thiết kế và vận hành thực tế trong hệ sinh thái **DATN-2026 (Scrum & Work Management)**.

---

## 1. Bản chất của Story Point là gì?

**Story Point (SP)** là một **đơn vị đo lường tương đối (Relative Estimation)** dùng trong phương pháp luận **Agile/Scrum** nhằm định lượng quy mô của một hạng mục công việc (User Story, Task, Bug).

Story Point **KHÔNG PHẢI** là số giờ hay số ngày làm việc cố định ($1\text{ SP} \neq 1\text{ giờ}$ hay $8\text{ giờ}$). Story Point trả lời cho câu hỏi:
> *"Công việc này có quy mô, độ khó và rủi ro lớn đến mức nào so với các công việc mẫu khác trong dự án?"*

### Tam giác ước lượng (The Estimation Triangle)
Một điểm Story Point được tổng hòa từ 3 yếu tố phi tuyến tính:

```text
                  Độ phức tạp kỹ thuật
                 (Technical Complexity)
                          ▲
                         / \
                        /   \
                       /     \
  Khối lượng công việc ------- Mức độ rủi ro & Bất định
    (Amount of Effort)             (Risk & Uncertainty)
```

1. **Khối lượng công việc (Effort):** Lượng thao tác cơ học cần thực hiện (ví dụ: tạo 20 bảng cơ sở dữ liệu mẫu tốn nhiều công gõ code hơn 1 bảng, dù logic không khó).
2. **Độ phức tạp kỹ thuật (Complexity):** Mức độ hóc búa về mặt tư duy, thuật toán hoặc kiến trúc (ví dụ: thuật toán đồng bộ trạng thái thời gian thực giữa WebSocket và Redis).
3. **Mức độ rủi ro & Bất định (Risk & Uncertainty):** Độ mơ hồ của bài toán, phụ thuộc vào API bên thứ 3 chưa có tài liệu, hoặc sử dụng công nghệ mới mà thành viên trong nhóm chưa có kinh nghiệm.

---

## 2. Story Point được dùng để làm gì?

### 2.1. Lập kế hoạch Sprint chuẩn xác (Sprint Planning)
Khi bắt đầu một chu kỳ Sprint, nhóm phát triển không thể chọn việc một cách cảm tính. Nhóm dựa vào tổng Story Point của các Story để:
- Không nhận quá tải (*Overcommitment*), tránh tình trạng kiệt sức (*Burnout*) hoặc vỡ kế hoạch bàn giao.
- Không nhận quá ít việc, đảm bảo tối ưu hóa năng suất làm việc của toàn đội ngũ.

### 2.2. Đo lường tốc độ phát triển của đội ngũ (Team Velocity)
* **Velocity** là tổng số Story Point mà nhóm hoàn thành đạt chuẩn `DONE` trong một Sprint.
* Khi nhóm trải qua 3–4 Sprint, tốc độ sẽ ổn định (ví dụ: trung bình đạt $28\text{ SP/Sprint}$). 
* Chỉ số này đại diện cho **năng lực thực tế của toàn đội**, là cơ sở khách quan để ra quyết định cam kết phạm vi cho Sprint tiếp theo.

### 2.3. Dự báo lộ trình và ngày phát hành (Release Forecasting)
* Nếu Product Backlog còn tồn đọng **$140\text{ SP}$** và nhóm có vận tốc ổn định **$28\text{ SP/Sprint}$** (chu kỳ 2 tuần/Sprint):
  $$\text{Số Sprint cần thiết} = \frac{140\text{ SP}}{28\text{ SP/Sprint}} = 5\text{ Sprint} \approx 10\text{ tuần (2.5 tháng)}$$
* Quản lý dự án (Project Manager / Scrum Master) có thể đưa ra mốc bàn giao dự kiến cho các bên liên quan dựa trên số liệu thực tế thay vì suy đoán cảm tính.

### 2.4. Trực quan hóa tiến độ bằng Báo cáo Agile (Agile Reports)
1. **Burndown Chart (Biểu đồ tiêu hao):**
   - Trục tung thể hiện số Story Point còn lại (`remainingStoryPoints`), trục hoành là các ngày làm việc trong Sprint.
   - Giúp cả đội nhận diện ngay từ giữa Sprint: Đội có đang bám sát đường lý tưởng không? Có bị phình to phạm vi (*Scope Creep*) do thêm việc bất chợt không?
2. **Velocity Chart (Biểu đồ vận tốc):**
   - So sánh giữa điểm cam kết đầu Sprint (`committedStoryPoints`) và điểm thực tế hoàn thành cuối Sprint (`completedStoryPoints`) qua từng chu kỳ.

### 2.5. Tạo sự đồng thuận và làm rõ yêu cầu (Planning Poker)
* Khi thảo luận ước lượng, nếu một Lập trình viên chấm **2 điểm** (cho rằng bài toán đơn giản), nhưng Kỹ sư Kiểm thử (Tester) chấm **8 điểm** (vì có quá nhiều ca kiểm thử phức tạp trên các môi trường khác nhau):
* $\rightarrow$ Sự chênh lệch buộc cả đội phải đối thoại, làm rõ các ngóc ngách kỹ thuật tiềm ẩn **trước khi viết dòng code đầu tiên**.

---

## 3. Tại sao chọn Story Point thay vì Số giờ (Hours)?

| Tiêu chí | Ước lượng bằng Giờ (Hours) | Ước lượng bằng Story Point |
|---|---|---|
| **Chênh lệch trình độ (Senior vs. Junior)** | Senior làm mất 2 giờ, Junior mất 8 giờ $\rightarrow$ Ghi 4 giờ thì sai cho cả hai. | Bản chất bài toán vẫn là **3 điểm**. Độ phức tạp khách quan của bài toán không thay đổi theo người làm. |
| **Áp lực tâm lý & Định luật Parkinson** | Thời gian ước lượng dễ bị biến thành "hạn chót chết". Gây stress khi sắp trễ, hoặc kéo dài công việc cho đủ giờ đã ước lượng. | Giảm áp lực thời gian cơ học, khuyến khích nhóm tập trung vào giá trị bàn giao và chất lượng giải pháp. |
| **Tâm lý học nhận thức con người** | Bộ não con người rất kém khi ước lượng con số tuyệt đối trong tương lai. | Con người cực kỳ giỏi khi so sánh tương đối (dễ dàng phân biệt được *Con Mèo < Con Chó < Con Bò < Con Voi*). |
| **Ranh giới công việc** | Thường chỉ tính thời gian code, bỏ sót thời gian test, review code, họp trao đổi và deploy. | Bao quát toàn bộ chu trình từ khâu bắt đầu tới khi đạt chuẩn nghiệm thu (`Definition of Done`). |

---

## 4. Thang đo ước lượng Fibonacci chuẩn Agile

DATN-2026 áp dụng dãy số **Fibonacci cải tiến**: `1, 2, 3, 5, 8, 13, 20...` để phản ánh quy luật: **Công việc càng lớn thì độ bất định và rủi ro càng tăng theo cấp số nhân.**

```text
 ┌───┐  ┌───┐  ┌───┐  ┌───┐  ┌───┐  ┌────┐
 │ 1 │  │ 2 │  │ 3 │  │ 5 │  │ 8 │  │ 13 │
 └───┘  └───┘  └───┘  └───┘  └───┘  └────┘
  Rất nhỏ  Nhỏ   Vừa    Khá   Lớn   Rất lớn (Cần tách nhỏ)
```

| Mức điểm | Ý nghĩa quy ước | Ví dụ minh họa thực tế | Thời gian ước chừng tương đối |
|:---:|---|---|---|
| **1** | Việc vụn vặt, cực kỳ rõ ràng, không rủi ro | Sửa lỗi chính tả, đổi màu nút bấm, sửa câu thông báo lỗi. | Vài chục phút – 2 giờ |
| **2** | Việc nhỏ, quy trình chuẩn, ít rủi ro | Thêm một trường dữ liệu đơn giản vào API và cập nhật validate. | Nửa ngày |
| **3** | Việc trung bình - nhỏ, quy trình rõ ràng | Tạo CRUD cơ bản cho một danh mục đơn giản, có unit test. | 1 ngày |
| **5** | Việc trung bình chuẩn, phổ biến nhất trong Sprint | Màn hình Đăng nhập (validate, gọi Keycloak JWT, lưu state, điều hướng). | 2 – 3 ngày |
| **8** | Việc lớn, logic phức tạp hoặc có yếu tố bất định | Xây dựng tính năng Kéo thả Kanban Board có cập nhật LexoRank tức thời. | 4 – 5 ngày |
| **13** | Việc rất lớn, nhiều nhánh rủi ro và phụ thuộc | Tích hợp cổng thanh toán trực tuyến hoặc module Báo cáo động đa chiều. | Gần trọn 1 Sprint |
| **> 13** | ⛔ **QUÁ LỚN (Epic / Giant Story)** | **BẮT BUỘC BẺ NHỎ:** Phân rã thành 2–3 Story nhỏ hơn trước khi đưa vào Sprint. | Vượt quá 1 Sprint |

---

## 5. Đặc Tả Triển Khai trong Hệ Thống DATN-2026

### 5.1. Quy tắc phân tầng phân cấp (Hierarchy Levels)

Theo đặc tả [10_QUY_CHUAN_TRUONG_DU_LIEU_VA_RANG_BUOC_CONG_VIEC.md](../work_type/10_QUY_CHUAN_TRUONG_DU_LIEU_VA_RANG_BUOC_CONG_VIEC.md):

```
Hierarchy Level 1: EPIC
  │  ├── ⛔ KHÓA NHẬP (N/A): Không cho phép nhập Story Point trực tiếp.
  │  └── 🔄 ROLL-UP TỰ ĐỘNG: Backend tự động tính tổng SP từ các Story con trực thuộc.
  │
Hierarchy Level 0: USER STORY / TASK / BUG
  │  ├── ⚪ TÙY CHỌN: Điền số thực từ 0.00 đến 999.99 (khuyến nghị Fibonacci).
  │  └── 🎯 ĐƠN VỊ TÍNH VELOCITY: Chỉ Level 0 được tính vào Velocity và Burndown của Sprint.
  │
Hierarchy Level -1: SUB-TASK
     ├── ⛔ KHÓA NHẬP (N/A): Subtask không tính Story Point trực tiếp (tránh tính trùng lặp hai lần).
     └── ⏱️ THEO DÕI BẰNG GIỜ: Subtask quản lý bằng trường `originalEstimate` và `remainingEstimate` (giờ).
```

### 5.2. Cấu trúc dữ liệu & Ràng buộc Backend

* **Database (PostgreSQL Migration `V1`, `V5`):**
  ```sql
  work_items.story_points DECIMAL(6, 2) NULL;
  ```
* **Validation Annotation (Java 21 / Spring Boot 3):**
  ```java
  @DecimalMin(value = "0.0", message = "Điểm Story Points không thể âm")
  @DecimalMax(value = "999.99", message = "Điểm Story Points tối đa 999.99")
  private BigDecimal storyPoints;
  ```
* **Quy tắc bảo toàn khi chuyển đổi:**
  - Khi tạo hoặc cập nhật Subtask (`hierarchyLevel = -1`): Payload nếu có gửi `storyPoints` thì backend sẽ tự động chuẩn hóa về `null`.
  - Khi tạo hoặc cập nhật Epic (`hierarchyLevel = 1`): Hệ thống tự động tính `totalStoryPoints` và `doneStoryPoints` thông qua câu truy vấn đệ quy/tổng hợp từ các Story con.

### 5.3. Trải nghiệm người dùng trên Giao diện Frontend

1. **Tooltip Hướng dẫn Chuẩn mực (`StoryPointsHelpTooltip.jsx`):**
   - Đặt cạnh ô nhập liệu điểm ước lượng tại modal tạo/sửa việc.
   - Hỗ trợ cả 2 chế độ: Rê chuột để xem nhanh (hover) và Click để ghim xem chi tiết (pin).
   - Giải thích thang Fibonacci và quy tắc Level cha - con.
2. **Hiển thị trên Thẻ Backlog & Sprint Accordion (`SprintAccordion.jsx`):**
   - Header mỗi Sprint hiển thị tổng hợp 3 "viên thuốc" (Point Chips) màu sắc trực quan:
     - ⚪ **Xám (`#DFE1E6`):** Điểm cần làm (`todoStoryPoints`).
     - 🔵 **Xanh dương (`#DEEBFF`):** Điểm đang làm (`inProgressStoryPoints`).
     - 🟢 **Xanh lá (`#E3FCEF`):** Điểm hoàn thành (`doneStoryPoints`).
3. **Hiển thị trên Thẻ Kanban Board (`BoardCard.jsx`):**
   - Góc phải hiển thị huy hiệu tròn chứa điểm số để lập trình viên nắm được độ nặng của thẻ.
4. **Trang Báo cáo Dự án (`ProjectReportsPage.jsx`):**
   - `BurndownChart`: Vẽ biến thiên `remainingStoryPoints` theo trục thời gian thực.
   - `VelocityChart`: Cột kép thể hiện `committed` vs `completed` qua các Sprint đã đóng.

---

## 6. Hướng Dẫn Thực Hành Ước Lượng cho Nhóm Dự Án

### Bước 1: Thiết lập Story chuẩn mốc (Baseline Anchor)
- Cả nhóm chọn ra một việc đơn giản, thân thuộc làm mốc **2 điểm** (ví dụ: *"Đổi mật khẩu người dùng"*).
- Mọi User Story sau đó sẽ được mang ra "so sánh trọng lượng" với Story mốc này.

### Bước 2: Phiên ước lượng (Planning Poker Session)
1. Product Owner đọc to User Story và tiêu chí chấp nhận (*Acceptance Criteria*).
2. Lập trình viên và Tester cùng đặt câu hỏi làm rõ.
3. Mỗi người bí mật chọn một quân bài điểm số (hoặc giơ tay cùng lúc).
4. Nếu điểm số đồng thuận: Ghi nhận điểm số đó vào trường `storyPoints`.
5. Nếu điểm số chênh lệch lớn (ví dụ: người chấm 2, người chấm 8):
   - Người chấm thấp nhất và cao nhất lần lượt chia sẻ góc nhìn/rủi ro.
   - Cả nhóm bầu chọn lại lần 2 để chốt con số cuối cùng.

### Bước 3: Đóng Sprint và Xử lý việc dở dang
- Khi đóng Sprint (`state = CLOSED`):
  - Các công việc đạt trạng thái `DONE` sẽ được đóng băng điểm số vào `sprint_report_snapshots`.
  - Các công việc dở dang (`IN_PROGRESS` hoặc `TODO`) được di chuyển sang Sprint tiếp theo hoặc trả về Backlog.
  - **Lưu ý:** Story dở dang không được chia nửa điểm (không có chuyện làm được 50% của 8 điểm là ghi nhận 4 điểm). Nguyên tắc Agile: **Chỉ ghi nhận điểm khi việc đã DONE hoàn toàn**.

---

## 7. Các Sai Lầm Phổ Biến Cần Tránh (Anti-patterns)

> [!CAUTION]
> 1. **Quy đổi cứng điểm sang giờ:**
>    - *Sai lầm:* Quy định cứng ngắc "$1\text{ SP} = 4\text{ giờ}$". Điều này triệt tiêu hoàn toàn lợi ích của ước lượng tương đối, đưa nhóm quay trở lại nhược điểm của phương pháp Waterfall truyền thống.
> 2. **So sánh Velocity giữa các đội ngũ khác nhau:**
>    - Velocity là chỉ số **nội bộ** của riêng một nhóm dựa trên thước đo chủ quan của nhóm đó. Nhóm A làm 40 SP/Sprint không có nghĩa là làm việc hiệu quả gấp đôi Nhóm B làm 20 SP/Sprint.
> 3. **Bắt buộc chấm điểm cho Subtask:**
>    - Subtask chỉ là các bước kỹ thuật nhỏ để hoàn thành Story. Chấm điểm cho Subtask sẽ gây ra lỗi cộng trùng lặp điểm vào báo cáo.
> 4. **Tăng điểm Story chỉ để "làm đẹp" báo cáo:**
>    - Thổi phồng điểm số để tạo cảm giác vận tốc tăng ảo là hành vi tự đánh lừa chất lượng dự án.
