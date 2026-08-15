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
        if (projectRepository.findByIsTemplateTrue().size() >= 3) {
            log.info("Templates already seeded. Skipping DataSeeder.");
            return;
        }

        log.info("Starting DataSeeder for Project Templates...");

        UUID systemOwnerId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        seedEcommerceTemplate(systemOwnerId);
        seedBlogTemplate(systemOwnerId);
        seedCafeSetupTemplate(systemOwnerId);

        log.info("DataSeeder completed successfully! 3 Rich Templates created.");
    }

    private void seedEcommerceTemplate(UUID ownerId) {
        if (projectRepository.findByKey("ECOM").isPresent()) return;

        Project project = projectRepository.save(Project.builder()
                .key("ECOM")
                .name("Mẫu Dự Án Website E-Commerce Toàn Diện")
                .description("Quy trình chuẩn hóa từ khảo sát yêu cầu, thiết kế Figma, phát triển API giỏ hàng/sản phẩm, tích hợp cổng thanh toán VNPay/Momo đến kiểm thử và triển khai.")
                .status(ProjectStatus.ACTIVE)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(3))
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
                .dueDate(LocalDateTime.now().plusWeeks(2))
                .status(MilestoneStatus.CLOSED)
                .build());

        Milestone ms2 = milestoneRepository.save(Milestone.builder()
                .projectId(project.getId())
                .name("Phase 2: Core E-Commerce & Cổng Thanh toán")
                .description("Xây dựng Catalog sản phẩm, giỏ hàng, Checkout và kết nối cổng thanh toán")
                .dueDate(LocalDateTime.now().plusWeeks(6))
                .status(MilestoneStatus.IN_PROGRESS)
                .build());

        Milestone ms3 = milestoneRepository.save(Milestone.builder()
                .projectId(project.getId())
                .name("Phase 3: Kiểm thử UAT & Go-Live")
                .description("Kiểm thử bảo mật, tối ưu hiệu năng và triển khai lên môi trường Production")
                .dueDate(LocalDateTime.now().plusWeeks(10))
                .status(MilestoneStatus.OPEN)
                .build());

        // Tasks
        createTask(project.getId(), ms1.getId(), "ECOM-1", "Thiết kế Database Schema: Users, Products, Orders, Payments",
                "Phân tích quan hệ 1-n, n-n và đánh chỉ mục Index cho bảng sản phẩm và đơn hàng.", TaskType.STORY, TaskStatus.DONE, TaskPriority.HIGH, 8, ownerId, 1000);

        createTask(project.getId(), ms1.getId(), "ECOM-2", "Thiết kế Design System Figma & Wireframe Checkout",
                "Xây dựng bộ UI Kit gồm màu sắc, nút bấm, modal giỏ hàng và các bước thanh toán tiện lợi.", TaskType.TASK, TaskStatus.DONE, TaskPriority.MEDIUM, 12, ownerId, 2000);

        createTask(project.getId(), ms2.getId(), "ECOM-3", "Xây dựng Product Catalog & Search API với Elasticsearch",
                "Hỗ trợ tìm kiếm full-text search, lọc đa tiêu chí theo danh mục, giá tiền và thương hiệu.", TaskType.STORY, TaskStatus.IN_PROGRESS, TaskPriority.HIGH, 16, ownerId, 3000);

        createTask(project.getId(), ms2.getId(), "ECOM-4", "Tích hợp Cổng thanh toán VNPay & Momo IPN Callback",
                "Tạo URL thanh toán có chữ ký HMAC-SHA512 và xử lý webhook xác nhận thanh toán tự động.", TaskType.STORY, TaskStatus.IN_PROGRESS, TaskPriority.URGENT, 20, ownerId, 4000);

        createTask(project.getId(), ms2.getId(), "ECOM-5", "Xây dựng Giỏ hàng Real-time với Redis & LocalStorage",
                "Lưu trữ giỏ hàng tạm thời cho khách vãng lai và đồng bộ vào tài khoản khi đăng nhập.", TaskType.STORY, TaskStatus.TODO, TaskPriority.MEDIUM, 10, ownerId, 5000);

        createTask(project.getId(), ms2.getId(), "ECOM-6", "Phát triển Trang Quản trị Đơn hàng & Quản lý Kho",
                "Xem danh sách đơn, cập nhật trạng thái giao hàng và cảnh báo khi sản phẩm sắp hết hàng.", TaskType.STORY, TaskStatus.TODO, TaskPriority.HIGH, 14, ownerId, 6000);

        createTask(project.getId(), ms3.getId(), "ECOM-7", "Kiểm thử bảo mật luồng thanh toán & Pentest SQLi/XSS",
                "Đảm bảo an toàn thông tin khách hàng, chống giả mạo số tiền đơn hàng (Price Tampering).", TaskType.BUG, TaskStatus.TODO, TaskPriority.HIGH, 8, ownerId, 7000);

        createTask(project.getId(), ms3.getId(), "ECOM-8", "Triển khai CI/CD Docker Swarm & Thiết lập CDN Cloudflare",
                "Tự động build image khi push git và cấu hình cache hình ảnh sản phẩm qua CDN.", TaskType.TASK, TaskStatus.TODO, TaskPriority.MEDIUM, 6, ownerId, 8000);
    }

    private void seedBlogTemplate(UUID ownerId) {
        if (projectRepository.findByKey("BLOG").isPresent()) return;

        Project project = projectRepository.save(Project.builder()
                .key("BLOG")
                .name("Mẫu Nền Tảng Blog & CMS Tin Tức Chuẩn SEO")
                .description("Hệ thống quản lý nội dung số đa tác giả, trình soạn thảo Markdown/WYSIWYG, tối ưu hóa thẻ Meta SEO Onpage, sitemap động và phân tích lượt xem.")
                .status(ProjectStatus.ACTIVE)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(2))
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
                .dueDate(LocalDateTime.now().plusWeeks(3))
                .status(MilestoneStatus.IN_PROGRESS)
                .build());

        Milestone ms2 = milestoneRepository.save(Milestone.builder()
                .projectId(project.getId())
                .name("Cột mốc 2: Tối ưu SEO & Độc giả")
                .description("Cấu hình chuẩn SEO Onpage, thẻ Open Graph và cây bình luận")
                .dueDate(LocalDateTime.now().plusWeeks(6))
                .status(MilestoneStatus.OPEN)
                .build());

        // Tasks
        createTask(project.getId(), ms1.getId(), "BLOG-1", "Tích hợp Trình soạn thảo WYSIWYG & Upload ảnh Cloudinary",
                "Hỗ trợ kéo thả ảnh, chèn code syntax highlighting và tự động resize tối ưu dung lượng.", TaskType.STORY, TaskStatus.DONE, TaskPriority.HIGH, 10, ownerId, 1000);

        createTask(project.getId(), ms1.getId(), "BLOG-2", "Thiết kế hệ thống Phân loại Danh mục & Tag đám mây",
                "Quản lý cây danh mục phân cấp và hiển thị các chủ đề thịnh hành (Trending Tags).", TaskType.TASK, TaskStatus.DONE, TaskPriority.MEDIUM, 6, ownerId, 2000);

        createTask(project.getId(), ms2.getId(), "BLOG-3", "Tối ưu hóa SEO Meta Tags động, Schema.org và Open Graph",
                "Tự động sinh thẻ meta description, canonical link và ảnh đại diện khi chia sẻ lên Facebook/Zalo.", TaskType.STORY, TaskStatus.IN_PROGRESS, TaskPriority.HIGH, 8, ownerId, 3000);

        createTask(project.getId(), ms2.getId(), "BLOG-4", "Xây dựng Cây bình luận đa cấp & Chống spam reCAPTCHA",
                "Độc giả có thể phản hồi lồng nhau (Reply Thread) và kiểm duyệt comment phản cảm.", TaskType.STORY, TaskStatus.TODO, TaskPriority.MEDIUM, 12, ownerId, 4000);

        createTask(project.getId(), ms2.getId(), "BLOG-5", "Tự động sinh RSS Feed và XML Sitemap theo chuẩn Google",
                "Cập nhật sitemap ngay khi có bài viết mới để Googlebot thu thập dữ liệu nhanh chóng.", TaskType.TASK, TaskStatus.TODO, TaskPriority.LOW, 4, ownerId, 5000);

        createTask(project.getId(), ms2.getId(), "BLOG-6", "Tích hợp Google Analytics 4 & Bảng theo dõi lượt đọc bài",
                "Thống kê bài viết có lượt xem cao nhất trong tuần, thời gian đọc trung bình.", TaskType.STORY, TaskStatus.TODO, TaskPriority.MEDIUM, 8, ownerId, 6000);
    }

    private void seedCafeSetupTemplate(UUID ownerId) {
        if (projectRepository.findByKey("CAFE").isPresent()) return;

        Project project = projectRepository.save(Project.builder()
                .key("CAFE")
                .name("Mẫu Quy Trình Setup & Thi Công Quán Cà Phê")
                .description("Quy trình thực chiến từ khảo sát mặt bằng, bản vẽ 3D, thi công điện nước 3 pha, lắp đặt quầy Bar đến tuyển dụng Barista và chạy thử nghiệm (Soft Opening).")
                .status(ProjectStatus.ACTIVE)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(2))
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
                .dueDate(LocalDateTime.now().plusWeeks(2))
                .status(MilestoneStatus.CLOSED)
                .build());

        Milestone ms2 = milestoneRepository.save(Milestone.builder()
                .projectId(project.getId())
                .name("Giai đoạn 2: Thi công Cải tạo & Quầy Bar")
                .description("Hoàn thiện phần thô, điện nước công nghiệp và đóng lắp quầy Bar")
                .dueDate(LocalDateTime.now().plusWeeks(5))
                .status(MilestoneStatus.IN_PROGRESS)
                .build());

        Milestone ms3 = milestoneRepository.save(Milestone.builder()
                .projectId(project.getId())
                .name("Giai đoạn 3: Lắp Thiết Bị & Khai Trương Thử")
                .description("Lắp máy pha cà phê, bàn ghế, đào tạo nhân viên và khai trương thử nghiệm")
                .dueDate(LocalDateTime.now().plusWeeks(8))
                .status(MilestoneStatus.OPEN)
                .build());

        // Tasks
        createTask(project.getId(), ms1.getId(), "CAFE-1", "Khảo sát hiện trạng mặt bằng và đo đạc kích thước thực tế",
                "Kiểm tra vị trí hộp kỹ thuật điện, đường cấp thoát nước và khả năng chịu tải của sàn.", TaskType.TASK, TaskStatus.DONE, TaskPriority.HIGH, 4, ownerId, 1000);

        createTask(project.getId(), ms1.getId(), "CAFE-2", "Lên bản vẽ thiết kế 3D và bố trí công năng tam giác quầy Bar",
                "Tối ưu khoảng cách di chuyển giữa máy pha cà phê, bồn rửa và tủ bảo quản nguyên liệu.", TaskType.STORY, TaskStatus.DONE, TaskPriority.URGENT, 24, ownerId, 2000);

        createTask(project.getId(), ms1.getId(), "CAFE-3", "Lập bảng dự toán chi phí thi công và danh mục vật tư hoàn thiện",
                "Liệt kê chi phí gạch ốp lát, sơn tường, đèn trang trí và dự phòng phát sinh 10%.", TaskType.TASK, TaskStatus.DONE, TaskPriority.HIGH, 8, ownerId, 3000);

        createTask(project.getId(), ms2.getId(), "CAFE-4", "Thi công cải tạo hệ thống điện 3 pha, cấp thoát nước và trần",
                "Kéo dây điện riêng chịu tải cao cho máy pha cà phê và lắp bẫy mỡ cho đường thoát nước.", TaskType.TASK, TaskStatus.IN_PROGRESS, TaskPriority.URGENT, 40, ownerId, 4000);

        createTask(project.getId(), ms2.getId(), "CAFE-5", "Đóng quầy Bar gỗ kết hợp mặt đá Granite và hệ kệ treo",
                "Gia công quầy Bar chống thấm nước, bố trí ổ cắm âm và khay gõ bã cà phê chìm.", TaskType.TASK, TaskStatus.IN_PROGRESS, TaskPriority.HIGH, 30, ownerId, 5000);

        createTask(project.getId(), ms3.getId(), "CAFE-6", "Mua sắm & Lắp đặt Máy pha Espresso 2 group & Máy xay hạt",
                "Căn chỉnh áp suất bơm 9 bar, nhiệt độ nước 93°C và thử chiết xuất Espresso tiêu chuẩn.", TaskType.TASK, TaskStatus.TODO, TaskPriority.HIGH, 8, ownerId, 6000);

        createTask(project.getId(), ms3.getId(), "CAFE-7", "Setup hệ thống âm thanh, ánh sáng decor và cây xanh",
                "Lắp đặt loa âm trần Bluetooth, đèn rọi ray vàng ấm và bố trí cây trầu bà thanh lọc không khí.", TaskType.TASK, TaskStatus.TODO, TaskPriority.MEDIUM, 12, ownerId, 7000);

        createTask(project.getId(), ms3.getId(), "CAFE-8", "Tuyển dụng, đào tạo Barista & chạy thử Menu (Soft Opening)",
                "Đào tạo kỹ năng bọt sữa Latte Art, quy trình phục vụ và giảm giá 20% tuần chạy thử.", TaskType.STORY, TaskStatus.TODO, TaskPriority.HIGH, 20, ownerId, 8000);
    }

    private void createTask(UUID projectId, UUID milestoneId, String code, String title, String description,
                            TaskType type, TaskStatus status, TaskPriority priority, Integer estimate, UUID reporterId, Integer position) {
        taskRepository.save(Task.builder()
                .projectId(projectId)
                .milestoneId(milestoneId)
                .code(code)
                .title(title)
                .description(description)
                .type(type)
                .status(status)
                .priority(priority)
                .estimate(estimate)
                .reporterId(reporterId)
                .position(position)
                .build());
    }
}
