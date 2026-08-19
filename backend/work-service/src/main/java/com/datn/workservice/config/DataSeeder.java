package com.datn.workservice.config;

import com.datn.workservice.entity.Milestone;
import com.datn.workservice.entity.Project;
import com.datn.workservice.entity.Task;
import com.datn.workservice.entity.TaskLabel;
import com.datn.workservice.enums.MilestoneStatus;
import com.datn.workservice.enums.ProjectStatus;
import com.datn.workservice.enums.TaskPriority;
import com.datn.workservice.enums.TaskStatus;
import com.datn.workservice.enums.TaskType;
import com.datn.workservice.repository.MilestoneRepository;
import com.datn.workservice.repository.ProjectRepository;
import com.datn.workservice.repository.TaskLabelRepository;
import com.datn.workservice.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final ProjectRepository projectRepository;
    private final MilestoneRepository milestoneRepository;
    private final TaskLabelRepository taskLabelRepository;
    private final TaskRepository taskRepository;

    @Override
    @Transactional
    public void run(String... args) {
        List<Project> existingTemplates = projectRepository.findByIsTemplateTrue();

        // Kiểm tra xem đã có template giàu dữ liệu (có subtasks & startDate) hay chưa
        boolean hasRichSubtasks = existingTemplates.stream().anyMatch(p ->
                taskRepository.findByProjectId(p.getId()).stream().anyMatch(t -> t.getParentTaskId() != null && t.getStartDate() != null));

        if (hasRichSubtasks && existingTemplates.size() >= 3) {
            log.info("Rich Templates with Subtasks and Realistic Timelines already seeded. Skipping DataSeeder.");
            return;
        }

        log.info("Starting DataSeeder for Project Templates with Subtasks and Realistic Timelines...");

        // Làm mới dữ liệu mẫu để cập nhật Subtasks và Timeline chuẩn xác
        for (Project old : existingTemplates) {
            taskRepository.deleteAll(taskRepository.findByProjectId(old.getId()));
            milestoneRepository.deleteAll(milestoneRepository.findByProjectId(old.getId()));
            taskLabelRepository.deleteAll(taskLabelRepository.findByProjectId(old.getId()));
            projectRepository.delete(old);
        }

        UUID systemOwnerId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        seedEcommerceTemplate(systemOwnerId);
        seedBlogTemplate(systemOwnerId);
        seedCafeSetupTemplate(systemOwnerId);

        log.info("DataSeeder completed successfully! 3 Rich Templates with Subtasks & Timeline created.");
    }

    private void seedEcommerceTemplate(UUID ownerId) {
        if (projectRepository.findByKey("ECOM").isPresent()) return;

        LocalDate pStart = LocalDate.of(2025, 1, 6);
        LocalDate pEnd = LocalDate.of(2025, 4, 6);

        Project project = projectRepository.save(Project.builder()
                .key("ECOM")
                .name("Mẫu Dự Án Website E-Commerce Toàn Diện")
                .description("Quy trình chuẩn hóa từ khảo sát yêu cầu, thiết kế Figma, phát triển API giỏ hàng/sản phẩm, tích hợp cổng thanh toán VNPay/Momo đến kiểm thử và triển khai.")
                .status(ProjectStatus.ACTIVE)
                .startDate(pStart)
                .endDate(pEnd)
                .createdBy(ownerId)
                .isPublic(true)
                .isTemplate(true)
                .build());

        // Labels
        taskLabelRepository.save(TaskLabel.builder().projectId(project.getId()).name("Frontend").color("#3B82F6").build());
        taskLabelRepository.save(TaskLabel.builder().projectId(project.getId()).name("Backend").color("#10B981").build());
        taskLabelRepository.save(TaskLabel.builder().projectId(project.getId()).name("Payment").color("#F59E0B").build());
        taskLabelRepository.save(TaskLabel.builder().projectId(project.getId()).name("QA/QC").color("#EC4899").build());
        taskLabelRepository.save(TaskLabel.builder().projectId(project.getId()).name("DevOps").color("#8B5CF6").build());

        // Milestones
        Milestone ms1 = milestoneRepository.save(Milestone.builder()
                .projectId(project.getId())
                .name("Phase 1: Khảo sát & Thiết kế Kiến trúc")
                .description("Hoàn thiện thiết kế hệ thống Database và giao diện người dùng UX/UI")
                .dueDate(LocalDateTime.of(2025, 1, 26, 23, 59, 59))
                .status(MilestoneStatus.CLOSED)
                .build());

        Milestone ms2 = milestoneRepository.save(Milestone.builder()
                .projectId(project.getId())
                .name("Phase 2: Core E-Commerce & Cổng Thanh toán")
                .description("Xây dựng Catalog sản phẩm, giỏ hàng, Checkout và kết nối cổng thanh toán")
                .dueDate(LocalDateTime.of(2025, 3, 2, 23, 59, 59))
                .status(MilestoneStatus.IN_PROGRESS)
                .build());

        Milestone ms3 = milestoneRepository.save(Milestone.builder()
                .projectId(project.getId())
                .name("Phase 3: Kiểm thử UAT & Go-Live")
                .description("Kiểm thử bảo mật, tối ưu hiệu năng và triển khai lên môi trường Production")
                .dueDate(LocalDateTime.of(2025, 4, 6, 23, 59, 59))
                .status(MilestoneStatus.OPEN)
                .build());

        // Phase 1 Tasks & Subtasks
        Task t1 = createTask(project.getId(), ms1.getId(), null, "ECOM-1",
                "Thiết kế Database Schema: Users, Products, Orders, Payments",
                "Phân tích quan hệ 1-n, n-n và đánh chỉ mục Index cho bảng sản phẩm và đơn hàng.",
                TaskType.STORY, TaskStatus.DONE, TaskPriority.HIGH, 8,
                LocalDateTime.of(2025, 1, 6, 9, 0), LocalDateTime.of(2025, 1, 15, 18, 0), ownerId, 1000);

        createTask(project.getId(), ms1.getId(), t1.getId(), "ECOM-2",
                "Thiết kế thực thể Users, Roles và bảng phân quyền RBAC",
                "Định nghĩa bảng User, Role, Permission và quan hệ phân quyền nhiều-nhiều.",
                TaskType.TASK, TaskStatus.DONE, TaskPriority.HIGH, 3,
                LocalDateTime.of(2025, 1, 6, 9, 0), LocalDateTime.of(2025, 1, 8, 18, 0), ownerId, 1100);

        createTask(project.getId(), ms1.getId(), t1.getId(), "ECOM-3",
                "Thiết kế bảng Products, Categories và Product Variants (SKU)",
                "Lưu trữ biến thể màu sắc, kích thước, số lượng tồn kho và bảng giá.",
                TaskType.TASK, TaskStatus.DONE, TaskPriority.HIGH, 3,
                LocalDateTime.of(2025, 1, 9, 9, 0), LocalDateTime.of(2025, 1, 12, 18, 0), ownerId, 1200);

        createTask(project.getId(), ms1.getId(), t1.getId(), "ECOM-4",
                "Thiết kế bảng Orders, OrderItems và Transactions thanh toán",
                "Lưu thông tin giao dịch, trạng thái đơn hàng và nhật ký thanh toán.",
                TaskType.TASK, TaskStatus.DONE, TaskPriority.MEDIUM, 2,
                LocalDateTime.of(2025, 1, 13, 9, 0), LocalDateTime.of(2025, 1, 15, 18, 0), ownerId, 1300);

        Task t2 = createTask(project.getId(), ms1.getId(), null, "ECOM-5",
                "Thiết kế Design System Figma & Wireframe Checkout",
                "Xây dựng bộ UI Kit gồm màu sắc, nút bấm, modal giỏ hàng và các bước thanh toán tiện lợi.",
                TaskType.TASK, TaskStatus.DONE, TaskPriority.MEDIUM, 12,
                LocalDateTime.of(2025, 1, 16, 9, 0), LocalDateTime.of(2025, 1, 26, 18, 0), ownerId, 2000);

        createTask(project.getId(), ms1.getId(), t2.getId(), "ECOM-6",
                "Xây dựng bộ Typography, Color Palette và Button Component",
                "Thiết lập chuẩn Design Tokens cho giao diện sáng/tối.",
                TaskType.TASK, TaskStatus.DONE, TaskPriority.LOW, 4,
                LocalDateTime.of(2025, 1, 16, 9, 0), LocalDateTime.of(2025, 1, 20, 18, 0), ownerId, 2100);

        createTask(project.getId(), ms1.getId(), t2.getId(), "ECOM-7",
                "Thiết kế giao diện Modal Giỏ hàng và luồng Checkout 3 bước",
                "Tối ưu trải nghiệm đặt hàng 1-click cho người dùng di động.",
                TaskType.TASK, TaskStatus.DONE, TaskPriority.MEDIUM, 8,
                LocalDateTime.of(2025, 1, 21, 9, 0), LocalDateTime.of(2025, 1, 26, 18, 0), ownerId, 2200);

        // Phase 2 Tasks & Subtasks
        Task t3 = createTask(project.getId(), ms2.getId(), null, "ECOM-8",
                "Xây dựng Product Catalog & Search API với Elasticsearch",
                "Hỗ trợ tìm kiếm full-text search, lọc đa tiêu chí theo danh mục, giá tiền và thương hiệu.",
                TaskType.STORY, TaskStatus.IN_PROGRESS, TaskPriority.HIGH, 16,
                LocalDateTime.of(2025, 1, 27, 9, 0), LocalDateTime.of(2025, 2, 10, 18, 0), ownerId, 3000);

        createTask(project.getId(), ms2.getId(), t3.getId(), "ECOM-9",
                "Viết API CRUD Danh mục & Sản phẩm với JPA Specification",
                "Phân trang, lọc động và sắp xếp linh hoạt theo chuẩn RESTful.",
                TaskType.TASK, TaskStatus.DONE, TaskPriority.HIGH, 8,
                LocalDateTime.of(2025, 1, 27, 9, 0), LocalDateTime.of(2025, 2, 2, 18, 0), ownerId, 3100);

        createTask(project.getId(), ms2.getId(), t3.getId(), "ECOM-10",
                "Đồng bộ dữ liệu sang Elasticsearch Cluster và tạo Index Mapping",
                "Cấu hình bộ phân tích tiếng Việt (vi_analyzer) và tìm kiếm mờ (Fuzzy Search).",
                TaskType.TASK, TaskStatus.IN_PROGRESS, TaskPriority.HIGH, 8,
                LocalDateTime.of(2025, 2, 3, 9, 0), LocalDateTime.of(2025, 2, 10, 18, 0), ownerId, 3200);

        Task t4 = createTask(project.getId(), ms2.getId(), null, "ECOM-11",
                "Tích hợp Cổng thanh toán VNPay & Momo IPN Callback",
                "Tạo URL thanh toán có chữ ký HMAC-SHA512 và xử lý webhook xác nhận thanh toán tự động.",
                TaskType.STORY, TaskStatus.IN_PROGRESS, TaskPriority.URGENT, 20,
                LocalDateTime.of(2025, 2, 11, 9, 0), LocalDateTime.of(2025, 2, 23, 18, 0), ownerId, 4000);

        createTask(project.getId(), ms2.getId(), t4.getId(), "ECOM-12",
                "Tạo URL Redirect thanh toán kèm chữ ký số HMAC-SHA512",
                "Xây dựng payload bảo mật kết nối Sandbox VNPay và Momo Payment Gateway.",
                TaskType.TASK, TaskStatus.IN_PROGRESS, TaskPriority.URGENT, 10,
                LocalDateTime.of(2025, 2, 11, 9, 0), LocalDateTime.of(2025, 2, 16, 18, 0), ownerId, 4100);

        createTask(project.getId(), ms2.getId(), t4.getId(), "ECOM-13",
                "Xử lý IPN Webhook xác nhận giao dịch và cập nhật trạng thái đơn",
                "Bảo vệ chống Replay Attack và đối soát giao dịch tự động.",
                TaskType.TASK, TaskStatus.TODO, TaskPriority.HIGH, 10,
                LocalDateTime.of(2025, 2, 17, 9, 0), LocalDateTime.of(2025, 2, 23, 18, 0), ownerId, 4200);

        createTask(project.getId(), ms2.getId(), null, "ECOM-14",
                "Xây dựng Giỏ hàng Real-time với Redis & LocalStorage",
                "Lưu trữ giỏ hàng tạm thời cho khách vãng lai và đồng bộ vào tài khoản khi đăng nhập.",
                TaskType.STORY, TaskStatus.TODO, TaskPriority.MEDIUM, 10,
                LocalDateTime.of(2025, 2, 17, 9, 0), LocalDateTime.of(2025, 2, 25, 18, 0), ownerId, 5000);

        createTask(project.getId(), ms2.getId(), null, "ECOM-15",
                "Phát triển Trang Quản trị Đơn hàng & Quản lý Kho",
                "Xem danh sách đơn, cập nhật trạng thái giao hàng và cảnh báo khi sản phẩm sắp hết hàng.",
                TaskType.STORY, TaskStatus.TODO, TaskPriority.HIGH, 14,
                LocalDateTime.of(2025, 2, 20, 9, 0), LocalDateTime.of(2025, 3, 2, 18, 0), ownerId, 6000);

        // Phase 3 Tasks & Subtasks
        createTask(project.getId(), ms3.getId(), null, "ECOM-16",
                "Kiểm thử bảo mật luồng thanh toán & Pentest SQLi/XSS",
                "Đảm bảo an toàn thông tin khách hàng, chống giả mạo số tiền đơn hàng (Price Tampering).",
                TaskType.BUG, TaskStatus.TODO, TaskPriority.HIGH, 8,
                LocalDateTime.of(2025, 3, 3, 9, 0), LocalDateTime.of(2025, 3, 20, 18, 0), ownerId, 7000);

        Task t8 = createTask(project.getId(), ms3.getId(), null, "ECOM-17",
                "Triển khai CI/CD Docker Swarm & Thiết lập CDN Cloudflare",
                "Tự động build image khi push git và cấu hình cache hình ảnh sản phẩm qua CDN.",
                TaskType.TASK, TaskStatus.TODO, TaskPriority.MEDIUM, 6,
                LocalDateTime.of(2025, 3, 21, 9, 0), LocalDateTime.of(2025, 4, 6, 18, 0), ownerId, 8000);

        createTask(project.getId(), ms3.getId(), t8.getId(), "ECOM-18",
                "Viết Dockerfile multi-stage cho Frontend & Backend Services",
                "Tối ưu dung lượng Docker Image và thiết lập non-root user.",
                TaskType.TASK, TaskStatus.TODO, TaskPriority.MEDIUM, 3,
                LocalDateTime.of(2025, 3, 21, 9, 0), LocalDateTime.of(2025, 3, 28, 18, 0), ownerId, 8100);

        createTask(project.getId(), ms3.getId(), t8.getId(), "ECOM-19",
                "Cấu hình Cloudflare DNS, SSL Certificate và Caching Rule",
                "Bật HTTP/3, Brotli compression và chống DDoS.",
                TaskType.TASK, TaskStatus.TODO, TaskPriority.LOW, 3,
                LocalDateTime.of(2025, 3, 29, 9, 0), LocalDateTime.of(2025, 4, 6, 18, 0), ownerId, 8200);
    }

    private void seedBlogTemplate(UUID ownerId) {
        if (projectRepository.findByKey("BLOG").isPresent()) return;

        LocalDate pStart = LocalDate.of(2025, 2, 3);
        LocalDate pEnd = LocalDate.of(2025, 4, 15);

        Project project = projectRepository.save(Project.builder()
                .key("BLOG")
                .name("Mẫu Nền Tảng Blog & CMS Tin Tức Chuẩn SEO")
                .description("Hệ thống quản lý nội dung số đa tác giả, trình soạn thảo Markdown/WYSIWYG, tối ưu hóa thẻ Meta SEO Onpage, sitemap động và phân tích lượt xem.")
                .status(ProjectStatus.ACTIVE)
                .startDate(pStart)
                .endDate(pEnd)
                .createdBy(ownerId)
                .isPublic(true)
                .isTemplate(true)
                .build());

        // Labels
        taskLabelRepository.save(TaskLabel.builder().projectId(project.getId()).name("SEO").color("#06B6D4").build());
        taskLabelRepository.save(TaskLabel.builder().projectId(project.getId()).name("Editor").color("#8B5CF6").build());
        taskLabelRepository.save(TaskLabel.builder().projectId(project.getId()).name("UI/UX").color("#EC4899").build());
        taskLabelRepository.save(TaskLabel.builder().projectId(project.getId()).name("Security").color("#EF4444").build());

        // Milestones
        Milestone ms1 = milestoneRepository.save(Milestone.builder()
                .projectId(project.getId())
                .name("Cột mốc 1: CMS & Soạn thảo Markdown")
                .description("Xây dựng bộ công cụ biên tập bài viết và quản trị danh mục")
                .dueDate(LocalDateTime.of(2025, 3, 2, 23, 59, 59))
                .status(MilestoneStatus.IN_PROGRESS)
                .build());

        Milestone ms2 = milestoneRepository.save(Milestone.builder()
                .projectId(project.getId())
                .name("Cột mốc 2: Tối ưu SEO & Độc giả")
                .description("Cấu hình chuẩn SEO Onpage, thẻ Open Graph và cây bình luận")
                .dueDate(LocalDateTime.of(2025, 4, 15, 23, 59, 59))
                .status(MilestoneStatus.OPEN)
                .build());

        // Tasks & Subtasks
        Task t1 = createTask(project.getId(), ms1.getId(), null, "BLOG-1",
                "Tích hợp Trình soạn thảo WYSIWYG & Upload ảnh Cloudinary",
                "Hỗ trợ kéo thả ảnh, chèn code syntax highlighting và tự động resize tối ưu dung lượng.",
                TaskType.STORY, TaskStatus.DONE, TaskPriority.HIGH, 10,
                LocalDateTime.of(2025, 2, 3, 9, 0), LocalDateTime.of(2025, 2, 16, 18, 0), ownerId, 1000);

        createTask(project.getId(), ms1.getId(), t1.getId(), "BLOG-2",
                "Cài đặt thư viện Tiptap / Markdown editor và custom toolbar",
                "Tích hợp Heading, Code Block, Table, Task List và Blockquote.",
                TaskType.TASK, TaskStatus.DONE, TaskPriority.MEDIUM, 5,
                LocalDateTime.of(2025, 2, 3, 9, 0), LocalDateTime.of(2025, 2, 9, 18, 0), ownerId, 1100);

        createTask(project.getId(), ms1.getId(), t1.getId(), "BLOG-3",
                "Tích hợp Cloudinary SDK để upload và nén ảnh tự động",
                "Hỗ trợ định dạng WebP, tự động tạo thumbnail và chống hotlinking.",
                TaskType.TASK, TaskStatus.DONE, TaskPriority.HIGH, 5,
                LocalDateTime.of(2025, 2, 10, 9, 0), LocalDateTime.of(2025, 2, 16, 18, 0), ownerId, 1200);

        createTask(project.getId(), ms1.getId(), null, "BLOG-4",
                "Thiết kế hệ thống Phân loại Danh mục & Tag đám mây",
                "Quản lý cây danh mục phân cấp và hiển thị các chủ đề thịnh hành (Trending Tags).",
                TaskType.TASK, TaskStatus.DONE, TaskPriority.MEDIUM, 6,
                LocalDateTime.of(2025, 2, 17, 9, 0), LocalDateTime.of(2025, 3, 2, 18, 0), ownerId, 2000);

        Task t3 = createTask(project.getId(), ms2.getId(), null, "BLOG-5",
                "Tối ưu hóa SEO Meta Tags động, Schema.org và Open Graph",
                "Tự động sinh thẻ meta description, canonical link và ảnh đại diện khi chia sẻ lên Facebook/Zalo.",
                TaskType.STORY, TaskStatus.IN_PROGRESS, TaskPriority.HIGH, 8,
                LocalDateTime.of(2025, 3, 3, 9, 0), LocalDateTime.of(2025, 3, 16, 18, 0), ownerId, 3000);

        createTask(project.getId(), ms2.getId(), t3.getId(), "BLOG-6",
                "Tạo component SEO động với react-helmet cho trang bài viết",
                "Render thẻ Meta Title, Description, Robots và Canonical Tag.",
                TaskType.TASK, TaskStatus.IN_PROGRESS, TaskPriority.MEDIUM, 4,
                LocalDateTime.of(2025, 3, 3, 9, 0), LocalDateTime.of(2025, 3, 9, 18, 0), ownerId, 3100);

        createTask(project.getId(), ms2.getId(), t3.getId(), "BLOG-7",
                "Sinh cấu trúc JSON-LD Article Schema cho Google Rich Results",
                "Khai báo Author, Publisher, DatePublished và BreadcrumbList.",
                TaskType.TASK, TaskStatus.TODO, TaskPriority.MEDIUM, 4,
                LocalDateTime.of(2025, 3, 10, 9, 0), LocalDateTime.of(2025, 3, 16, 18, 0), ownerId, 3200);

        Task t4 = createTask(project.getId(), ms2.getId(), null, "BLOG-8",
                "Xây dựng Cây bình luận đa cấp & Chống spam reCAPTCHA",
                "Độc giả có thể phản hồi lồng nhau (Reply Thread) và kiểm duyệt comment phản cảm.",
                TaskType.STORY, TaskStatus.TODO, TaskPriority.MEDIUM, 12,
                LocalDateTime.of(2025, 3, 17, 9, 0), LocalDateTime.of(2025, 3, 30, 18, 0), ownerId, 4000);

        createTask(project.getId(), ms2.getId(), t4.getId(), "BLOG-9",
                "Thiết kế API Nested Comments hỗ trợ trả lời phân nhánh",
                "Sử dụng thuật toán CTE hoặc Materialized Path để truy vấn cây bình luận nhanh chóng.",
                TaskType.TASK, TaskStatus.TODO, TaskPriority.MEDIUM, 6,
                LocalDateTime.of(2025, 3, 17, 9, 0), LocalDateTime.of(2025, 3, 23, 18, 0), ownerId, 4100);

        createTask(project.getId(), ms2.getId(), t4.getId(), "BLOG-10",
                "Tích hợp Google reCAPTCHA v3 kiểm tra điểm tin cậy submit",
                "Ngăn chặn bot spam comment và tự động ẩn bình luận nghi vấn.",
                TaskType.TASK, TaskStatus.TODO, TaskPriority.LOW, 6,
                LocalDateTime.of(2025, 3, 24, 9, 0), LocalDateTime.of(2025, 3, 30, 18, 0), ownerId, 4200);

        createTask(project.getId(), ms2.getId(), null, "BLOG-11",
                "Tự động sinh RSS Feed và XML Sitemap theo chuẩn Google",
                "Cập nhật sitemap ngay khi có bài viết mới để Googlebot thu thập dữ liệu nhanh chóng.",
                TaskType.TASK, TaskStatus.TODO, TaskPriority.LOW, 4,
                LocalDateTime.of(2025, 3, 31, 9, 0), LocalDateTime.of(2025, 4, 6, 18, 0), ownerId, 5000);

        createTask(project.getId(), ms2.getId(), null, "BLOG-12",
                "Tích hợp Google Analytics 4 & Bảng theo dõi lượt đọc bài",
                "Thống kê bài viết có lượt xem cao nhất trong tuần, thời gian đọc trung bình.",
                TaskType.STORY, TaskStatus.TODO, TaskPriority.MEDIUM, 8,
                LocalDateTime.of(2025, 4, 7, 9, 0), LocalDateTime.of(2025, 4, 15, 18, 0), ownerId, 6000);
    }

    private void seedCafeSetupTemplate(UUID ownerId) {
        if (projectRepository.findByKey("CAFE").isPresent()) return;

        LocalDate pStart = LocalDate.of(2025, 3, 3);
        LocalDate pEnd = LocalDate.of(2025, 5, 12);

        Project project = projectRepository.save(Project.builder()
                .key("CAFE")
                .name("Mẫu Quy Trình Setup & Thi Công Quán Cà Phê")
                .description("Quy trình thực chiến từ khảo sát mặt bằng, bản vẽ 3D, thi công điện nước 3 pha, lắp đặt quầy Bar đến tuyển dụng Barista và chạy thử nghiệm (Soft Opening).")
                .status(ProjectStatus.ACTIVE)
                .startDate(pStart)
                .endDate(pEnd)
                .createdBy(ownerId)
                .isPublic(true)
                .isTemplate(true)
                .build());

        // Labels
        taskLabelRepository.save(TaskLabel.builder().projectId(project.getId()).name("Mặt bằng").color("#F59E0B").build());
        taskLabelRepository.save(TaskLabel.builder().projectId(project.getId()).name("Thi công").color("#EF4444").build());
        taskLabelRepository.save(TaskLabel.builder().projectId(project.getId()).name("Thiết bị").color("#3B82F6").build());
        taskLabelRepository.save(TaskLabel.builder().projectId(project.getId()).name("Vận hành").color("#10B981").build());

        // Milestones
        Milestone ms1 = milestoneRepository.save(Milestone.builder()
                .projectId(project.getId())
                .name("Giai đoạn 1: Khảo sát & Thiết kế Phối cảnh 3D")
                .description("Đo đạc mặt bằng, lên ý tưởng phong cách thiết kế và dự toán kinh phí")
                .dueDate(LocalDateTime.of(2025, 3, 23, 23, 59, 59))
                .status(MilestoneStatus.CLOSED)
                .build());

        Milestone ms2 = milestoneRepository.save(Milestone.builder()
                .projectId(project.getId())
                .name("Giai đoạn 2: Thi công Cải tạo & Quầy Bar")
                .description("Hoàn thiện phần thô, điện nước công nghiệp và đóng lắp quầy Bar")
                .dueDate(LocalDateTime.of(2025, 4, 13, 23, 59, 59))
                .status(MilestoneStatus.IN_PROGRESS)
                .build());

        Milestone ms3 = milestoneRepository.save(Milestone.builder()
                .projectId(project.getId())
                .name("Giai đoạn 3: Lắp Thiết Bị & Khai Trương Thử")
                .description("Lắp máy pha cà phê, bàn ghế, đào tạo nhân viên và khai trương thử nghiệm")
                .dueDate(LocalDateTime.of(2025, 5, 12, 23, 59, 59))
                .status(MilestoneStatus.OPEN)
                .build());

        // Phase 1 Tasks & Subtasks
        createTask(project.getId(), ms1.getId(), null, "CAFE-1",
                "Khảo sát hiện trạng mặt bằng và đo đạc kích thước thực tế",
                "Kiểm tra vị trí hộp kỹ thuật điện, đường cấp thoát nước và khả năng chịu tải của sàn.",
                TaskType.TASK, TaskStatus.DONE, TaskPriority.HIGH, 4,
                LocalDateTime.of(2025, 3, 3, 9, 0), LocalDateTime.of(2025, 3, 9, 18, 0), ownerId, 1000);

        Task t2 = createTask(project.getId(), ms1.getId(), null, "CAFE-2",
                "Lên bản vẽ thiết kế 3D và bố trí công năng tam giác quầy Bar",
                "Tối ưu khoảng cách di chuyển giữa máy pha cà phê, bồn rửa và tủ bảo quản nguyên liệu.",
                TaskType.STORY, TaskStatus.DONE, TaskPriority.URGENT, 24,
                LocalDateTime.of(2025, 3, 10, 9, 0), LocalDateTime.of(2025, 3, 20, 18, 0), ownerId, 2000);

        createTask(project.getId(), ms1.getId(), t2.getId(), "CAFE-3",
                "Phác thảo layout khu pha chế, bồn rửa và tủ nguyên liệu",
                "Đảm bảo nguyên tắc 1 chiều trong vận hành quầy bar đồ uống.",
                TaskType.TASK, TaskStatus.DONE, TaskPriority.HIGH, 10,
                LocalDateTime.of(2025, 3, 10, 9, 0), LocalDateTime.of(2025, 3, 14, 18, 0), ownerId, 2100);

        createTask(project.getId(), ms1.getId(), t2.getId(), "CAFE-4",
                "Render phối cảnh 3D ánh sáng và vật liệu nội thất",
                "Dựng hình chi tiết tông màu gỗ ấm, đèn rọi ray và quầy thu ngân.",
                TaskType.TASK, TaskStatus.DONE, TaskPriority.URGENT, 14,
                LocalDateTime.of(2025, 3, 15, 9, 0), LocalDateTime.of(2025, 3, 20, 18, 0), ownerId, 2200);

        createTask(project.getId(), ms1.getId(), null, "CAFE-5",
                "Lập bảng dự toán chi phí thi công và danh mục vật tư hoàn thiện",
                "Liệt kê chi phí gạch ốp lát, sơn tường, đèn trang trí và dự phòng phát sinh 10%.",
                TaskType.TASK, TaskStatus.DONE, TaskPriority.HIGH, 8,
                LocalDateTime.of(2025, 3, 17, 9, 0), LocalDateTime.of(2025, 3, 23, 18, 0), ownerId, 3000);

        // Phase 2 Tasks & Subtasks
        Task t4 = createTask(project.getId(), ms2.getId(), null, "CAFE-6",
                "Thi công cải tạo hệ thống điện 3 pha, cấp thoát nước và trần",
                "Kéo dây điện riêng chịu tải cao cho máy pha cà phê và lắp bẫy mỡ cho đường thoát nước.",
                TaskType.TASK, TaskStatus.IN_PROGRESS, TaskPriority.URGENT, 40,
                LocalDateTime.of(2025, 3, 24, 9, 0), LocalDateTime.of(2025, 4, 6, 18, 0), ownerId, 4000);

        createTask(project.getId(), ms2.getId(), t4.getId(), "CAFE-7",
                "Thi công đường ống nước cấp phi 27 và thoát phi 60 kèm bẫy mỡ",
                "Đảm bảo độ dốc thoát nước tốt và chống trào ngược mùi hôi.",
                TaskType.TASK, TaskStatus.DONE, TaskPriority.HIGH, 20,
                LocalDateTime.of(2025, 3, 24, 9, 0), LocalDateTime.of(2025, 3, 30, 18, 0), ownerId, 4100);

        createTask(project.getId(), ms2.getId(), t4.getId(), "CAFE-8",
                "Kéo dây điện 3 pha 6.0mm riêng cho máy pha cà phê công nghiệp",
                "Lắp aptomat chống giật riêng biệt đảm bảo an toàn tải công suất 4500W.",
                TaskType.TASK, TaskStatus.IN_PROGRESS, TaskPriority.URGENT, 20,
                LocalDateTime.of(2025, 3, 31, 9, 0), LocalDateTime.of(2025, 4, 6, 18, 0), ownerId, 4200);

        createTask(project.getId(), ms2.getId(), null, "CAFE-9",
                "Đóng quầy Bar gỗ kết hợp mặt đá Granite và hệ kệ treo",
                "Gia công quầy Bar chống thấm nước, bố trí ổ cắm âm và khay gõ bã cà phê chìm.",
                TaskType.TASK, TaskStatus.IN_PROGRESS, TaskPriority.HIGH, 30,
                LocalDateTime.of(2025, 4, 7, 9, 0), LocalDateTime.of(2025, 4, 13, 18, 0), ownerId, 5000);

        // Phase 3 Tasks & Subtasks
        createTask(project.getId(), ms3.getId(), null, "CAFE-10",
                "Mua sắm & Lắp đặt Máy pha Espresso 2 group & Máy xay hạt",
                "Căn chỉnh áp suất bơm 9 bar, nhiệt độ nước 93°C và thử chiết xuất Espresso tiêu chuẩn.",
                TaskType.TASK, TaskStatus.TODO, TaskPriority.HIGH, 8,
                LocalDateTime.of(2025, 4, 14, 9, 0), LocalDateTime.of(2025, 4, 20, 18, 0), ownerId, 6000);

        createTask(project.getId(), ms3.getId(), null, "CAFE-11",
                "Setup hệ thống âm thanh, ánh sáng decor và cây xanh",
                "Lắp đặt loa âm trần Bluetooth, đèn rọi ray vàng ấm và bố trí cây trầu bà thanh lọc không khí.",
                TaskType.TASK, TaskStatus.TODO, TaskPriority.MEDIUM, 12,
                LocalDateTime.of(2025, 4, 21, 9, 0), LocalDateTime.of(2025, 4, 27, 18, 0), ownerId, 7000);

        Task t8 = createTask(project.getId(), ms3.getId(), null, "CAFE-12",
                "Tuyển dụng, đào tạo Barista & chạy thử Menu (Soft Opening)",
                "Đào tạo kỹ năng bọt sữa Latte Art, quy trình phục vụ và giảm giá 20% tuần chạy thử.",
                TaskType.STORY, TaskStatus.TODO, TaskPriority.HIGH, 20,
                LocalDateTime.of(2025, 4, 28, 9, 0), LocalDateTime.of(2025, 5, 12, 18, 0), ownerId, 8000);

        createTask(project.getId(), ms3.getId(), t8.getId(), "CAFE-13",
                "Đào tạo kỹ thuật định lượng và đánh sữa Cappuccino/Latte tiêu chuẩn",
                "Thực hành canh thời gian chiết xuất 25-30 giây cho shot double espresso.",
                TaskType.TASK, TaskStatus.TODO, TaskPriority.HIGH, 10,
                LocalDateTime.of(2025, 4, 28, 9, 0), LocalDateTime.of(2025, 5, 4, 18, 0), ownerId, 8100);

        createTask(project.getId(), ms3.getId(), t8.getId(), "CAFE-14",
                "Vận hành tuần lễ Soft Opening giảm giá 20% và lấy khảo sát khách",
                "Ghi nhận phản hồi hương vị đồ uống, tốc độ ra món để hoàn thiện quy trình.",
                TaskType.TASK, TaskStatus.TODO, TaskPriority.MEDIUM, 10,
                LocalDateTime.of(2025, 5, 5, 9, 0), LocalDateTime.of(2025, 5, 12, 18, 0), ownerId, 8200);
    }

    private Task createTask(UUID projectId, UUID milestoneId, UUID parentTaskId, String code, String title, String description,
                            TaskType type, TaskStatus status, TaskPriority priority, Integer estimate,
                            LocalDateTime startDate, LocalDateTime dueDate, UUID reporterId, Integer position) {
        return taskRepository.save(Task.builder()
                .projectId(projectId)
                .milestoneId(milestoneId)
                .parentTaskId(parentTaskId)
                .code(code)
                .title(title)
                .description(description)
                .type(type)
                .status(status)
                .priority(priority)
                .estimate(estimate)
                .startDate(startDate)
                .dueDate(dueDate)
                .reporterId(reporterId)
                .position(position)
                .build());
    }
}
