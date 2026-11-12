# Thư Mục Kiểm Thử Thực Nghiệm Kịch Bản Đa Ngành (Scenario Testing)

Thư mục này chứa các tài liệu nghiên cứu, thực nghiệm và đánh giá chất lượng logic nghiệp vụ của hệ thống quản lý dự án **DATN-2026** (đặc biệt là dịch vụ `work-service`).

## Báo Cáo Trọng Tâm
- 📄 **[WORK_SERVICE_MULTI_INDUSTRY_SCENARIO_TEST.md](./WORK_SERVICE_MULTI_INDUSTRY_SCENARIO_TEST.md)**: 
  *Báo cáo thực nghiệm đánh giá nghiệp vụ Work-Service trên 20 ngành nghề khác biệt.*
  - **Mục tiêu:** Kiểm tra xem ngoài tiến độ & đóng góp, tất cả các logic nghiệp vụ hiện tại (Phân cấp thực thể, FSM Workflow Engine, Dependency & Cycle Check, Board Kanban/Scrum, LexoRank, Project Cloning/Templates, Permissions & Roles) đã thực sự tốt cho một dự án quản lý dự án cho nhiều loại ngành nghề hay chưa.
  - **Phương pháp:** Đóng vai 20 người dùng thực tế từ 20 ngành nghề (IT, Xây dựng, F&B, Nông nghiệp, Sản xuất, Marketing, Y tế, Giáo dục, Sự kiện, Luật, E-Commerce, Phim ảnh, Kiến trúc nội thất, NGO cứu trợ, Nghiên cứu R&D, Game Studio, Thời trang, Kiểm toán, Bất động sản, Tuyển dụng HR) và đối chiếu trực tiếp với mã nguồn Java Spring Boot của `work-service`.
  - **Kết quả:** Phân tích chi tiết từng kịch bản, bảng tổng hợp Scorecard, nhận diện 7 điểm nghẽn kiến trúc cốt tử và đề xuất 5 gói giải pháp nâng cấp thành Universal Work OS.
