# 📂 PHÂN HỆ MÔ HÌNH DỰ ÁN (PROJECT MODELS SPECIFICATION)
## HỆ THỐNG QUẢN LÝ DỰ ÁN & CÔNG VIỆC — DATN-2026

Thư mục này chứa toàn bộ tài liệu nghiên cứu, đặc tả kiến trúc và tài liệu trình chiếu về **2 Mô hình Vận hành Dự án cốt lõi** trong hệ sinh thái DATN-2026:

1. **Mô hình 1: Quản lý theo Chu kỳ, Giai đoạn (`SOFTWARE_SCRUM` / `PHASE_DRIVEN`)**
   - Phù hợp cho các dự án phát triển phần mềm (Agile Scrum), dự án F&B / Chuỗi nhà hàng cà phê, dự án mùa vụ nông nghiệp VietGAP, v.v.
   - Quản lý theo vòng lặp có đích đến: Lập kế hoạch ➔ Thực thi ➔ Nghiệm thu ➔ Đóng giai đoạn ➔ Chuyển giao.
   - Hỗ trợ đầy đủ: Sprint Backlog, Story Points, Biểu đồ Burndown, Vận tốc Velocity và Đóng băng dữ liệu (Snapshot).

2. **Mô hình 2: Quản lý Bảng công việc liên tục (`KANBAN` / `OPERATIONAL`)**
   - Phù hợp cho các hoạt động vận hành thường nhật, bảo trì cơ sở hạ tầng, Helpdesk IT, CSKH, xử lý yêu cầu phát sinh liên tục.
   - Quản lý luồng công việc thời gian thực (Continuous Flow): Cần làm ➔ Đang làm ➔ Hoàn thành, kiểm soát giới hạn làm việc đồng thời (WIP Limits).

---

## 📑 Danh mục Tài liệu trong Thư mục:

- 📄 [`DAC_TA_CAC_MO_HINH_DU_AN.md`](./DAC_TA_CAC_MO_HINH_DU_AN.md): Đặc tả chi tiết kiến trúc, nghiệp vụ 6 bước tự động, cơ chế chuyển giao giai đoạn và triết lý thiết kế "Single Source of Truth".
- 🖥️ [`slide_cac_mo_hinh_du_an.html`](./slide_cac_mo_hinh_du_an.html): Slide trình chiếu tương tác hiện đại bằng HTML/CSS mô phỏng trực quan 2 mô hình vận hành dự án.
