package com.datn.workservice.service.template;

import com.datn.workservice.dto.response.TemplateIntegrityReport;
import com.datn.workservice.entity.*;
import com.datn.workservice.enums.*;
import com.datn.workservice.repository.*;
import com.datn.workservice.service.AtomicKeySequenceGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Khởi tạo dữ liệu mẫu chuẩn (Seed Default Templates) cho hệ thống Work Management.
 * <p>
 * Cung cấp 3 mẫu chuẩn nghiệp vụ và cấu trúc kỹ thuật:
 * 1. Xây dựng Cửa hàng F&B (Nhà hàng - Quán Café) - [BUSINESS]
 * 2. Xây dựng Website Bán hàng E-Commerce - [SOFTWARE]
 * 3. Kế hoạch Vận hành Mùa vụ Cây ăn quả (Nông nghiệp) - [OPERATIONS]
 * <p>
 * Mỗi mẫu bao gồm đầy đủ hệ sinh thái:
 * - Trạng thái & Workflow chuyển trạng thái chuẩn
 * - Board Kanban/Scrum với các cột ánh xạ 100% trạng thái
 * - Sprints / Milestones theo từng giai đoạn (FUTURE)
 * - Cây công việc 3 cấp: Epic (1) -> Task (0) -> Subtask (-1)
 * - Nhãn (Labels) và Phân hệ (Components)
 * - Phân quyền dự án (ADMINISTRATOR, MEMBER, VIEWER)
 * - PostgreSQL Atomic Key Sequence
 * - Đảm bảo kiểm tra toàn vẹn qua TemplateIntegrityValidator với 0 ERROR.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateDataSeeder {

    public static final UUID DEFAULT_WORKSPACE_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    public static final UUID DEFAULT_CREATOR_ID = UUID.fromString("d457bd54-287f-40a0-ab65-9a4f04dd3606");

    public static final String KEY_FNB = "TPLFNB";
    public static final String KEY_WEB = "TPLWEB";
    public static final String KEY_AGRI = "TPLAGRI";

    private final ProjectRepository projectRepository;
    private final StatusRepository statusRepository;
    private final WorkItemTypeRepository workItemTypeRepository;
    private final WorkflowRepository workflowRepository;
    private final WorkflowTransitionRepository workflowTransitionRepository;
    private final WorkflowSchemeRepository workflowSchemeRepository;
    private final WorkflowSchemeMappingRepository workflowSchemeMappingRepository;
    private final BoardRepository boardRepository;
    private final BoardColumnRepository boardColumnRepository;
    private final BoardColumnStatusRepository boardColumnStatusRepository;
    private final ProjectRoleRepository projectRoleRepository;
    private final PermissionSchemeEntryRepository permissionSchemeEntryRepository;
    private final ComponentRepository componentRepository;
    private final LabelRepository labelRepository;
    private final SprintRepository sprintRepository;
    private final WorkItemRepository workItemRepository;
    private final WorkItemLabelMapRepository workItemLabelMapRepository;
    private final WorkItemComponentMapRepository workItemComponentMapRepository;
    private final AtomicKeySequenceGenerator atomicKeySequenceGenerator;
    private final ProjectBlueprintLoader blueprintLoader;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Tự động kiểm tra và seed 3 mẫu mặc định cho Default Workspace nếu chưa có.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void seedDefaultTemplates() {
        UUID creatorId = resolveCreatorId();
        seedTemplatesForWorkspace(DEFAULT_WORKSPACE_ID, creatorId);
    }

    /**
     * Khởi tạo 3 mẫu dự án cho một workspace cụ thể.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void seedTemplatesForWorkspace(UUID workspaceId, UUID creatorId) {
        log.info("Bắt đầu kiểm tra và khởi tạo các Dự án mẫu cho workspace: {}", workspaceId);

        if (!projectRepository.existsByKey(KEY_FNB)) {
            seedFnbTemplate(workspaceId, creatorId);
        } else {
            log.info("Dự án mẫu F&B ({}) đã tồn tại.", KEY_FNB);
        }

        if (!projectRepository.existsByKey(KEY_WEB)) {
            seedEcommerceTemplate(workspaceId, creatorId);
        } else {
            log.info("Dự án mẫu Website Bán hàng ({}) đã tồn tại.", KEY_WEB);
        }

        if (!projectRepository.existsByKey(KEY_AGRI)) {
            seedAgriTemplate(workspaceId, creatorId);
        } else {
            log.info("Dự án mẫu Mùa vụ Nông nghiệp ({}) đã tồn tại.", KEY_AGRI);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 1. MẪU DỰ ÁN 1: XÂY DỰNG CỬA HÀNG F&B (NHÀ HÀNG - QUÁN CAFÉ)
    // ─────────────────────────────────────────────────────────────────────────────
    private void seedFnbTemplate(UUID workspaceId, UUID creatorId) {
        log.info("Đang khởi tạo mẫu dự án: Xây dựng Cửa hàng F&B ({})", KEY_FNB);
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusDays(90);

        atomicKeySequenceGenerator.createSequenceForProject(KEY_FNB);

        Project project = projectRepository.save(Project.builder()
                .workspaceId(workspaceId)
                .name("Xây dựng Cửa hàng F&B (Nhà hàng - Quán Café)")
                .key(KEY_FNB)
                .description("Mẫu dự án chuẩn hóa quy trình mở mới một điểm kinh doanh F&B: từ khảo sát thuê mặt bằng, thiết kế thi công nội thất, mua sắm máy móc quầy bar, tuyển dụng đào tạo nhân sự, hoàn thiện giấy phép ATTP đến chạy thử (Soft Opening) và khai trương.")
                .leadUserId(creatorId)
                .createdBy(creatorId)
                .defaultAssigneeType(DefaultAssigneeType.PROJECT_LEADER)
                .projectType(ProjectType.SOFTWARE_SCRUM)
                .isTemplate(true)
                .templateCategory(TemplateCategory.BUSINESS)
                .startDate(start)
                .endDate(end)
                .build());

        seedRolesAndPermissions(project);

        // Work Item Types
        Map<String, WorkItemType> types = seedStandardTypes(project);
        WorkItemType epicType = types.get("EPIC");
        WorkItemType taskType = types.get("TASK");
        WorkItemType subtaskType = types.get("SUBTASK");

        // Statuses
        Status sTodo = createStatus(project, "Cần làm", StatusCategory.TODO, "#64748B", "Hạng mục công việc chuẩn bị thực hiện");
        Status sProg = createStatus(project, "Đang thực hiện", StatusCategory.IN_PROGRESS, "#3B82F6", "Đang triển khai thi công, mua sắm hoặc tuyển dụng");
        Status sReview = createStatus(project, "Nghiệm thu / Đánh giá", StatusCategory.IN_PROGRESS, "#F59E0B", "Kiểm tra chất lượng món, thẩm định PCCC, nghiệm thu công trình");
        Status sDone = createStatus(project, "Hoàn thành", StatusCategory.DONE, "#10B981", "Đã hoàn tất và nghiệm thu đạt chuẩn");

        // Workflow & Transitions
        Workflow workflow = workflowRepository.save(Workflow.builder()
                .projectId(project.getId())
                .name("Quy trình Mở mới Cửa hàng F&B")
                .description("Quy trình chuẩn kiểm soát từng giai đoạn từ khảo sát đến khai trương")
                .isDefault(true)
                .build());

        workflowTransitionRepository.saveAll(List.of(
                createTransition(workflow, null, sTodo, "Create"),
                createTransition(workflow, sTodo, sProg, "Bắt đầu triển khai"),
                createTransition(workflow, sProg, sReview, "Gửi nghiệm thu"),
                createTransition(workflow, sReview, sDone, "Nghiệm thu đạt chuẩn"),
                createTransition(workflow, sReview, sProg, "Yêu cầu chỉnh sửa"),
                createTransition(workflow, sTodo, sDone, "Hoàn tất trực tiếp"),
                createTransition(workflow, sDone, sProg, "Mở lại công việc")
        ));

        bindWorkflowToTypes(project, workflow, List.of(epicType, taskType, subtaskType));

        // Board & Columns
        Board board = boardRepository.save(Board.builder()
                .projectId(project.getId())
                .name("Bảng Tiến độ Setup Cửa hàng F&B")
                .type(BoardType.SCRUM)
                .build());

        createColumn(board, "CHUẨN BỊ", 0, sTodo);
        createColumn(board, "ĐANG TRIỂN KHAI", 1, sProg);
        createColumn(board, "NGHIỆM THU", 2, sReview);
        createColumn(board, "HOÀN THÀNH", 3, sDone);

        // Components & Labels
        Component cMatBang = createComponent(project, "Mặt bằng & Pháp lý", "Khảo sát, hợp đồng thuê và giấy phép kinh doanh, PCCC, ATTP");
        Component cThiCong = createComponent(project, "Thiết kế & Thi công", "Bản vẽ 2D/3D, cải tạo điện nước, nội thất quầy bar, biển bảng");
        Component cMayMoc = createComponent(project, "Thiết bị & Dụng cụ", "Máy pha cà phê, máy xay, tủ lạnh công nghiệp, hệ thống POS");
        Component cMenu = createComponent(project, "Thực đơn & Nguyên liệu", "Công thức đồ uống/món ăn, tính giá vốn costing, nhà cung cấp");
        Component cNhanSu = createComponent(project, "Nhân sự & Đào tạo", "Tuyển dụng quản lý, pha chế, thu ngân, đào tạo quy trình SOP");
        Component cMarketing = createComponent(project, "Marketing & Khai trương", "Truyền thông quảng bá, tuần lễ Soft Opening và Grand Opening");

        Label lbMatBang = createLabel(project, "mat-bang", "#3B82F6");
        Label lbNoiThat = createLabel(project, "noi-that", "#8B5CF6");
        Label lbTuyenDung = createLabel(project, "tuyen-dung", "#EC4899");
        Label lbPhaChe = createLabel(project, "pha-che", "#F59E0B");
        Label lbPhapLy = createLabel(project, "phap-ly", "#EF4444");
        Label lbKhaiTruong = createLabel(project, "khai-truong", "#10B981");

        // Sprints (3 Giai đoạn Milestone)
        Sprint sp1 = createSprint(project, "Giai đoạn 1: Pháp lý, Mặt bằng & Thiết kế Concept",
                "Chốt mặt bằng kinh doanh, ký hợp đồng thuê và hoàn thiện bản vẽ 3D", start, start.plusDays(20));
        Sprint sp2 = createSprint(project, "Giai đoạn 2: Thi công nội thất, Máy móc & Menu",
                "Hoàn tất thi công thô/hoàn thiện, lắp đặt quầy bar và mua sắm thiết bị", start.plusDays(20), start.plusDays(42));
        Sprint sp3 = createSprint(project, "Giai đoạn 3: Tuyển dụng, Đào tạo & Khai trương",
                "Đào tạo nhân sự thực chiến, tuần lễ chạy thử Soft Opening và Grand Opening", start.plusDays(42), end);

        int seq = 1;
        // Epic 1
        WorkItem epic1 = saveWorkItem(project, KEY_FNB, seq++, epicType, sTodo, null, null,
                "Khảo sát vị trí, Thuê mặt bằng & Pháp lý kinh doanh",
                "Nghiên cứu thị trường khu vực, đàm phán hợp đồng thuê mặt bằng và hoàn thiện các thủ tục pháp lý thành lập hộ kinh doanh / ATTP.",
                WorkItemPriority.HIGH, null, null, cMatBang, start.plusDays(1), start.plusDays(20), creatorId);

        WorkItem t1_1 = saveWorkItem(project, KEY_FNB, seq++, taskType, sTodo, epic1.getId(), sp1.getId(),
                "Khảo sát lưu lượng khách (foot traffic) và lựa chọn mặt bằng",
                "Đánh giá tiềm năng khách hàng mục tiêu quanh bán kính 1km và kiểm tra hạ tầng giao thông vỉa hè.",
                WorkItemPriority.HIGH, new BigDecimal("5"), 8 * 3600L, cMatBang, start.plusDays(1), start.plusDays(6), creatorId);
        bindLabels(t1_1, List.of(lbMatBang));

        WorkItem st1_1_1 = saveWorkItem(project, KEY_FNB, seq++, subtaskType, sTodo, t1_1.getId(), sp1.getId(),
                "Đo đếm lượt khách qua lại theo các khung giờ sáng, trưa, tối trong 3 ngày",
                "Ghi nhận số lượng người đi bộ, phương tiện xe cộ qua lại cửa hàng.",
                WorkItemPriority.MEDIUM, null, 4 * 3600L, cMatBang, start.plusDays(1), start.plusDays(3), creatorId);

        WorkItem st1_1_2 = saveWorkItem(project, KEY_FNB, seq++, subtaskType, sTodo, t1_1.getId(), sp1.getId(),
                "Khảo sát chỗ để xe máy, bãi đỗ ô tô và lối thoát hiểm PCCC",
                "Đảm bảo sức chứa tối thiểu 20 xe máy cho khách và an toàn thoát hiểm.",
                WorkItemPriority.MEDIUM, null, 4 * 3600L, cMatBang, start.plusDays(3), start.plusDays(6), creatorId);

        WorkItem t1_2 = saveWorkItem(project, KEY_FNB, seq++, taskType, sTodo, epic1.getId(), sp1.getId(),
                "Thương lượng hợp đồng thuê và thủ tục đặt cọc mặt bằng",
                "Đàm phán giá thuê, chu kỳ tăng giá, thời gian sửa chữa miễn phí và điều khoản bồi thường.",
                WorkItemPriority.HIGH, new BigDecimal("5"), 6 * 3600L, cMatBang, start.plusDays(7), start.plusDays(12), creatorId);
        bindLabels(t1_2, List.of(lbMatBang, lbPhapLy));

        WorkItem st1_2_1 = saveWorkItem(project, KEY_FNB, seq++, subtaskType, sTodo, t1_2.getId(), sp1.getId(),
                "Đàm phán thời gian miễn phí tiền thuê để sửa chữa cải tạo (Fit-out)",
                "Đề xuất chủ nhà miễn phí 20 - 30 ngày trong thời gian đập phá, sửa chữa thô.",
                WorkItemPriority.MEDIUM, null, 3 * 3600L, cMatBang, start.plusDays(7), start.plusDays(9), creatorId);

        WorkItem st1_2_2 = saveWorkItem(project, KEY_FNB, seq++, subtaskType, sTodo, t1_2.getId(), sp1.getId(),
                "Rà soát điều khoản bồi thường, gia hạn và tiến hành công chứng hợp đồng",
                "Luật sư hoặc chuyên gia rà soát rủi ro pháp lý trước khi ký chính thức.",
                WorkItemPriority.HIGH, null, 3 * 3600L, cMatBang, start.plusDays(10), start.plusDays(12), creatorId);

        WorkItem t1_3 = saveWorkItem(project, KEY_FNB, seq++, taskType, sTodo, epic1.getId(), sp1.getId(),
                "Nộp hồ sơ ĐKKD và Giấy chứng nhận Đủ điều kiện An toàn thực phẩm",
                "Hoàn thành các thủ tục xin phép cơ quan chức năng địa phương.",
                WorkItemPriority.HIGH, new BigDecimal("5"), 10 * 3600L, cMatBang, start.plusDays(13), start.plusDays(19), creatorId);
        bindLabels(t1_3, List.of(lbPhapLy));

        // Epic 2
        WorkItem epic2 = saveWorkItem(project, KEY_FNB, seq++, epicType, sTodo, null, null,
                "Thiết kế kiến trúc, Thi công nội thất & Biển bảng nhận diện",
                "Thiết kế phối cảnh 3D không gian, cải tạo phần thô cấp thoát nước, thi công quầy bar và lắp đặt biển hiệu quảng cáo.",
                WorkItemPriority.HIGH, null, null, cThiCong, start.plusDays(10), start.plusDays(42), creatorId);

        WorkItem t2_1 = saveWorkItem(project, KEY_FNB, seq++, taskType, sTodo, epic2.getId(), sp1.getId(),
                "Thiết kế bản vẽ mặt bằng 2D và phối cảnh 3D nội ngoại thất",
                "Thống nhất phong cách decor, bố trí ánh sáng, luồng di chuyển của nhân viên và khách.",
                WorkItemPriority.HIGH, new BigDecimal("8"), 16 * 3600L, cThiCong, start.plusDays(10), start.plusDays(18), creatorId);
        bindLabels(t2_1, List.of(lbNoiThat));

        WorkItem t2_2 = saveWorkItem(project, KEY_FNB, seq++, taskType, sTodo, epic2.getId(), sp2.getId(),
                "Thi công cải tạo phần thô: cấp thoát nước quầy bar, điện 3 pha và hệ thống thông gió",
                "Đảm bảo đường nước thải có bẫy mỡ, đường điện chịu tải cho máy pha cà phê công suất cao.",
                WorkItemPriority.HIGH, new BigDecimal("8"), 24 * 3600L, cThiCong, start.plusDays(21), start.plusDays(29), creatorId);
        bindLabels(t2_2, List.of(lbNoiThat));

        WorkItem t2_3 = saveWorkItem(project, KEY_FNB, seq++, taskType, sTodo, epic2.getId(), sp2.getId(),
                "Sản xuất và lắp đặt quầy bar chuyên dụng, bàn ghế và đèn chiếu sáng",
                "Lắp đặt mặt đá quầy bar, bồn rửa âm, tủ kệ đựng syrup và bàn ghế chuẩn kích thước nhân trắc học.",
                WorkItemPriority.MEDIUM, new BigDecimal("5"), 16 * 3600L, cThiCong, start.plusDays(30), start.plusDays(37), creatorId);
        bindLabels(t2_3, List.of(lbNoiThat));

        WorkItem t2_4 = saveWorkItem(project, KEY_FNB, seq++, taskType, sTodo, epic2.getId(), sp2.getId(),
                "Thi công lắp đặt biển hiệu quảng cáo mặt tiền, biển vẫy LED và menu board",
                "Gia công chữ nổi phát sáng mặt tiền, biển vẫy hai mặt và bảng thực đơn điện tử.",
                WorkItemPriority.MEDIUM, new BigDecimal("3"), 8 * 3600L, cThiCong, start.plusDays(38), start.plusDays(41), creatorId);
        bindLabels(t2_4, List.of(lbNoiThat));

        // Epic 3
        WorkItem epic3 = saveWorkItem(project, KEY_FNB, seq++, epicType, sTodo, null, null,
                "Xây dựng Thực đơn (Menu), Định lượng & Nhập trang thiết bị",
                "Chuẩn hóa công thức đồ uống/món ăn, tính giá vốn costing, mua sắm máy móc quầy bar và tìm kiếm nhà cung cấp nguyên vật liệu.",
                WorkItemPriority.HIGH, null, null, cMenu, start.plusDays(22), start.plusDays(42), creatorId);

        WorkItem t3_1 = saveWorkItem(project, KEY_FNB, seq++, taskType, sTodo, epic3.getId(), sp2.getId(),
                "Chốt danh mục đồ uống chủ lực, công thức chuẩn hóa và tính giá vốn (Costing)",
                "Xác định món signature, xây dựng định lượng chi tiết (recipe gram/ml) để tối ưu tỷ lệ Food Cost dưới 30%.",
                WorkItemPriority.HIGH, new BigDecimal("5"), 12 * 3600L, cMenu, start.plusDays(22), start.plusDays(27), creatorId);
        bindLabels(t3_1, List.of(lbPhaChe));

        WorkItem t3_2 = saveWorkItem(project, KEY_FNB, seq++, taskType, sTodo, epic3.getId(), sp2.getId(),
                "Mua sắm máy pha cà phê công nghiệp, máy xay, máy làm đá và tủ mát trưng bày",
                "Chọn nhà phân phối chính hãng bảo hành tận nơi, lắp đặt và hiệu chỉnh áp suất chiết xuất espresso.",
                WorkItemPriority.HIGH, new BigDecimal("5"), 8 * 3600L, cMayMoc, start.plusDays(28), start.plusDays(34), creatorId);
        bindLabels(t3_2, List.of(lbPhaChe));

        WorkItem t3_3 = saveWorkItem(project, KEY_FNB, seq++, taskType, sTodo, epic3.getId(), sp2.getId(),
                "Tìm kiếm và ký hợp đồng cung cấp nguyên vật liệu tươi, cà phê hạt, trà và bao bì in thương hiệu",
                "So sánh báo giá 3 nhà cung ứng uy tín, đặt in ly cốc giấy, ly nhựa và túi mang đi có logo quán.",
                WorkItemPriority.MEDIUM, new BigDecimal("3"), 8 * 3600L, cMenu, start.plusDays(35), start.plusDays(40), creatorId);
        bindLabels(t3_3, List.of(lbPhaChe));

        WorkItem t3_4 = saveWorkItem(project, KEY_FNB, seq++, taskType, sTodo, epic3.getId(), sp2.getId(),
                "Cài đặt hệ thống phần mềm quản lý bán hàng POS, máy in hóa đơn và mã QR thanh toán",
                "Nhập thực đơn lên hệ thống POS, cấu hình phân quyền nhân viên thu ngân và liên kết tài khoản ngân hàng.",
                WorkItemPriority.MEDIUM, new BigDecimal("3"), 6 * 3600L, cMayMoc, start.plusDays(39), start.plusDays(41), creatorId);

        // Epic 4
        WorkItem epic4 = saveWorkItem(project, KEY_FNB, seq++, epicType, sTodo, null, null,
                "Tuyển dụng, Đào tạo nhân sự & Chuẩn hóa quy trình phục vụ (SOP)",
                "Đăng tuyển nhân sự các vị trí quầy bar, phục vụ, thu ngân; tổ chức đào tạo nội bộ kỹ năng pha chế và quy trình ứng xử với khách.",
                WorkItemPriority.HIGH, null, null, cNhanSu, start.plusDays(42), start.plusDays(55), creatorId);

        WorkItem t4_1 = saveWorkItem(project, KEY_FNB, seq++, taskType, sTodo, epic4.getId(), sp3.getId(),
                "Đăng tin và tổ chức phỏng vấn tuyển dụng Quản lý cửa hàng, Barista, Thu ngân và Phục vụ",
                "Lên tiêu chuẩn nhân sự, tổ chức phỏng vấn trực tiếp và chọn lựa các ứng viên có thái độ phù hợp.",
                WorkItemPriority.HIGH, new BigDecimal("5"), 14 * 3600L, cNhanSu, start.plusDays(42), start.plusDays(47), creatorId);
        bindLabels(t4_1, List.of(lbTuyenDung));

        WorkItem t4_2 = saveWorkItem(project, KEY_FNB, seq++, taskType, sTodo, epic4.getId(), sp3.getId(),
                "Tổ chức đào tạo nội bộ: kỹ năng pha chế theo công thức chuẩn, quy trình order và văn hóa phục vụ",
                "Đào tạo kỹ năng chào đón khách, xử lý khiếu nại, kỹ thuật đánh sữa latte art và tốc độ ra đồ.",
                WorkItemPriority.HIGH, new BigDecimal("8"), 20 * 3600L, cNhanSu, start.plusDays(48), start.plusDays(52), creatorId);
        bindLabels(t4_2, List.of(lbTuyenDung, lbPhaChe));

        WorkItem t4_3 = saveWorkItem(project, KEY_FNB, seq++, taskType, sTodo, epic4.getId(), sp3.getId(),
                "Kiểm tra tay nghề thực tế (Dry-run), đánh giá sát hạch và phát đồng phục làm việc",
                "Tổ chức buổi thi tay nghề blind test đồ uống trước toàn thể ban quản lý và cấp phát đồng phục, tạp dề.",
                WorkItemPriority.MEDIUM, new BigDecimal("3"), 6 * 3600L, cNhanSu, start.plusDays(53), start.plusDays(55), creatorId);
        bindLabels(t4_3, List.of(lbTuyenDung));

        // Epic 5
        WorkItem epic5 = saveWorkItem(project, KEY_FNB, seq++, epicType, sTodo, null, null,
                "Truyền thông quảng bá, Vận hành thử nghiệm (Soft Opening) & Khai trương",
                "Triển khai chiến dịch truyền thông đa kênh, tổ chức tuần lễ Soft Opening đón khách mời thử nghiệm và ngày hội Grand Opening chính thức.",
                WorkItemPriority.HIGH, null, null, cMarketing, start.plusDays(44), end, creatorId);

        WorkItem t5_1 = saveWorkItem(project, KEY_FNB, seq++, taskType, sTodo, epic5.getId(), sp3.getId(),
                "Triển khai chiến dịch truyền thông: chụp ảnh menu, chạy quảng cáo Fanpage và phát voucher",
                "Tạo dựng hình ảnh bắt mắt, liên kết với các Food Reviewer địa phương và chuẩn bị quà tặng mở màn.",
                WorkItemPriority.HIGH, new BigDecimal("5"), 12 * 3600L, cMarketing, start.plusDays(44), start.plusDays(53), creatorId);
        bindLabels(t5_1, List.of(lbKhaiTruong));

        WorkItem t5_2 = saveWorkItem(project, KEY_FNB, seq++, taskType, sTodo, epic5.getId(), sp3.getId(),
                "Tổ chức tuần lễ chạy thử (Soft Opening) đón khách thân thiết để kiểm tra tốc độ ra đồ",
                "Phục vụ 50% công suất thực tế, ghi nhận ý kiến phản hồi về chất lượng đồ uống và dịch vụ để hoàn thiện.",
                WorkItemPriority.HIGH, new BigDecimal("8"), 16 * 3600L, cMarketing, start.plusDays(54), start.plusDays(57), creatorId);
        bindLabels(t5_2, List.of(lbKhaiTruong));

        WorkItem t5_3 = saveWorkItem(project, KEY_FNB, seq++, taskType, sTodo, epic5.getId(), sp3.getId(),
                "Tổ chức ngày khai trương chính thức (Grand Opening): bố trí hoa tươi, chương trình giảm giá",
                "Chương trình khuyến mãi Mua 1 Tặng 1, âm nhạc sôi động, đón tiếp khách hàng và vận hành 100% công suất.",
                WorkItemPriority.HIGH, new BigDecimal("5"), 12 * 3600L, cMarketing, start.plusDays(58), end, creatorId);
        bindLabels(t5_3, List.of(lbKhaiTruong));

        updateSequenceValue(KEY_FNB, seq - 1);
        validateBlueprint(project);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 2. MẪU DỰ ÁN 2: XÂY DỰNG WEBSITE BÁN HÀNG E-COMMERCE
    // ─────────────────────────────────────────────────────────────────────────────
    private void seedEcommerceTemplate(UUID workspaceId, UUID creatorId) {
        log.info("Đang khởi tạo mẫu dự án: Xây dựng Website Bán hàng E-Commerce ({})", KEY_WEB);
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusDays(90);

        atomicKeySequenceGenerator.createSequenceForProject(KEY_WEB);

        Project project = projectRepository.save(Project.builder()
                .workspaceId(workspaceId)
                .name("Xây dựng Website Bán hàng E-Commerce")
                .key(KEY_WEB)
                .description("Bản mẫu quy trình phát triển website thương mại điện tử chuyên nghiệp theo phương pháp Agile/Scrum: từ phân tích đặc tả, thiết kế UI/UX Figma, phát triển Catalog, Giỏ hàng, Cổng thanh toán trực tuyến đến kiểm thử QA và Golive.")
                .leadUserId(creatorId)
                .createdBy(creatorId)
                .defaultAssigneeType(DefaultAssigneeType.PROJECT_LEADER)
                .projectType(ProjectType.SOFTWARE_SCRUM)
                .isTemplate(true)
                .templateCategory(TemplateCategory.SOFTWARE)
                .startDate(start)
                .endDate(end)
                .build());

        seedRolesAndPermissions(project);

        Map<String, WorkItemType> types = seedStandardTypes(project);
        WorkItemType epicType = types.get("EPIC");
        WorkItemType taskType = types.get("TASK");
        WorkItemType subtaskType = types.get("SUBTASK");

        // Statuses
        Status sTodo = createStatus(project, "To Do", StatusCategory.TODO, "#64748B", "Hạng mục công việc đang chờ thực hiện");
        Status sProg = createStatus(project, "In Progress", StatusCategory.IN_PROGRESS, "#3B82F6", "Đang trong quá trình phát triển mã nguồn");
        Status sReview = createStatus(project, "Code Review", StatusCategory.IN_PROGRESS, "#8B5CF6", "Đang rà soát mã nguồn Pull Request và kiểm tra tiêu chuẩn clean code");
        Status sQa = createStatus(project, "Testing / QA", StatusCategory.IN_PROGRESS, "#F59E0B", "Đang kiểm thử chức năng, tích hợp và kiểm tra hiệu năng trên Staging");
        Status sDone = createStatus(project, "Done", StatusCategory.DONE, "#10B981", "Đã hoàn thành kiểm thử và triển khai thành công");

        // Workflow
        Workflow workflow = workflowRepository.save(Workflow.builder()
                .projectId(project.getId())
                .name("Quy trình Scrum Phát triển Phần mềm E-Commerce")
                .description("Luồng chuẩn kiểm soát chất lượng qua Code Review và QA trước khi Done")
                .isDefault(true)
                .build());

        workflowTransitionRepository.saveAll(List.of(
                createTransition(workflow, null, sTodo, "Create"),
                createTransition(workflow, sTodo, sProg, "Start Progress"),
                createTransition(workflow, sProg, sReview, "Submit PR / Review"),
                createTransition(workflow, sReview, sQa, "Pass Review -> Testing"),
                createTransition(workflow, sReview, sProg, "Request Changes"),
                createTransition(workflow, sQa, sDone, "QA Approved -> Done"),
                createTransition(workflow, sQa, sProg, "Bug Found / Re-open Code"),
                createTransition(workflow, sDone, sProg, "Reopen Item"),
                createTransition(workflow, sTodo, sDone, "Quick Done")
        ));

        bindWorkflowToTypes(project, workflow, List.of(epicType, taskType, subtaskType));

        // Board & Columns
        Board board = boardRepository.save(Board.builder()
                .projectId(project.getId())
                .name("Bảng Scrum E-Commerce")
                .type(BoardType.SCRUM)
                .build());

        createColumn(board, "BACKLOG & TO DO", 0, sTodo);
        createColumn(board, "IN PROGRESS", 1, sProg);
        createColumn(board, "CODE REVIEW", 2, sReview);
        createColumn(board, "TESTING / QA", 3, sQa);
        createColumn(board, "DONE", 4, sDone);

        // Components & Labels
        Component cBackend = createComponent(project, "Backend API", "Dịch vụ lõi Spring Boot, xử lý dữ liệu và logic thanh toán");
        Component cFrontend = createComponent(project, "Frontend Storefront", "Giao diện khách hàng mua sắm React / Next.js tối ưu trải nghiệm");
        Component cAdmin = createComponent(project, "Admin Portal", "Hệ thống quản trị danh mục sản phẩm, đơn hàng và kho bãi");
        Component cPayment = createComponent(project, "Payment & Integration", "Tích hợp cổng thanh toán VNPay, Momo, ZaloPay và đối tác vận chuyển");
        Component cDevops = createComponent(project, "DevOps & Security", "Hạ tầng Docker, CI/CD, chứng chỉ SSL và bảo mật dữ liệu");
        Component cDesign = createComponent(project, "UI/UX Design", "Hệ thống Design System, wireframe và prototype Figma");

        Label lbFrontend = createLabel(project, "frontend", "#3B82F6");
        Label lbBackend = createLabel(project, "backend", "#10B981");
        Label lbSecurity = createLabel(project, "security", "#EF4444");
        Label lbPayment = createLabel(project, "payment", "#F59E0B");
        Label lbDesign = createLabel(project, "ui-ux", "#8B5CF6");
        Label lbApi = createLabel(project, "api", "#06B6D4");

        // 4 Sprints (Mỗi Sprint 14 ngày)
        Sprint sp1 = createSprint(project, "Sprint 1: Phân tích yêu cầu, Kiến trúc CSDL & UI/UX Design",
                "Đặc tả yêu cầu chức năng SRS, thiết kế UI Figma và dựng khung mã nguồn hệ thống", start, start.plusDays(14));
        Sprint sp2 = createSprint(project, "Sprint 2: Quản lý Sản phẩm, Danh mục & Xác thực Người dùng",
                "Phát triển Catalog sản phẩm đa biến thể, bộ lọc tìm kiếm và xác thực bảo mật tài khoản", start.plusDays(14), start.plusDays(28));
        Sprint sp3 = createSprint(project, "Sprint 3: Giỏ hàng, Đặt hàng & Cổng thanh toán trực tuyến",
                "Hoàn thiện flow mua sắm từ Giỏ hàng, Áp dụng Voucher đến Thanh toán VNPay/Momo", start.plusDays(28), start.plusDays(42));
        Sprint sp4 = createSprint(project, "Sprint 4: Trang Quản trị Admin, Kiểm thử bảo mật & Go-Live",
                "Dashboard quản trị doanh thu, kiểm thử tải hiệu năng và triển khai Production", start.plusDays(42), end);

        int seq = 1;
        // Epic 1
        WorkItem epic1 = saveWorkItem(project, KEY_WEB, seq++, epicType, sTodo, null, null,
                "Phân tích yêu cầu, Thiết kế kiến trúc & UI/UX Design Hệ thống",
                "Khảo sát yêu cầu nghiệp vụ, hoàn thiện tài liệu SRS, thiết kế Prototype Figma và thiết lập kiến trúc cơ sở dữ liệu PostgreSQL.",
                WorkItemPriority.HIGH, null, null, cDesign, start.plusDays(1), start.plusDays(14), creatorId);

        WorkItem t1_1 = saveWorkItem(project, KEY_WEB, seq++, taskType, sTodo, epic1.getId(), sp1.getId(),
                "Khảo sát yêu cầu nghiệp vụ và viết tài liệu đặc tả chức năng (SRS)",
                "Làm rõ các luồng đặt hàng, xử lý tồn kho, chính sách hoàn trả và ma trận phân quyền người dùng.",
                WorkItemPriority.HIGH, new BigDecimal("5"), 16 * 3600L, cBackend, start.plusDays(1), start.plusDays(5), creatorId);

        WorkItem t1_2 = saveWorkItem(project, KEY_WEB, seq++, taskType, sTodo, epic1.getId(), sp1.getId(),
                "Thiết kế Wireframe và Prototype giao diện người dùng trên Figma",
                "Xây dựng Design System đồng bộ, thiết kế phiên bản Desktop và Mobile-first đáp ứng tính thẩm mỹ cao.",
                WorkItemPriority.HIGH, new BigDecimal("8"), 24 * 3600L, cDesign, start.plusDays(4), start.plusDays(12), creatorId);
        bindLabels(t1_2, List.of(lbDesign));

        WorkItem st1_2_1 = saveWorkItem(project, KEY_WEB, seq++, subtaskType, sTodo, t1_2.getId(), sp1.getId(),
                "Thiết kế luồng trải nghiệm Home, Danh mục và Trang chi tiết sản phẩm",
                "Thiết kế bố cục sản phẩm, gallery ảnh zoom, bảng chọn size/màu và khối đánh giá khách hàng.",
                WorkItemPriority.MEDIUM, null, 12 * 3600L, cDesign, start.plusDays(4), start.plusDays(8), creatorId);

        WorkItem st1_2_2 = saveWorkItem(project, KEY_WEB, seq++, subtaskType, sTodo, t1_2.getId(), sp1.getId(),
                "Thiết kế luồng thanh toán Checkout 1 bước tối ưu chuyển đổi trên Mobile",
                "Tối giản các bước điền thông tin giao hàng, chọn phương thức thanh toán và xem lại đơn hàng.",
                WorkItemPriority.HIGH, null, 12 * 3600L, cDesign, start.plusDays(8), start.plusDays(12), creatorId);

        WorkItem t1_3 = saveWorkItem(project, KEY_WEB, seq++, taskType, sTodo, epic1.getId(), sp1.getId(),
                "Thiết kế mô hình CSDL quan hệ PostgreSQL và dựng khung mã nguồn Boilerplate",
                "Thiết kế ERD chuẩn hóa 3NF, index tối ưu truy vấn sản phẩm và khởi tạo repository backend/frontend.",
                WorkItemPriority.HIGH, new BigDecimal("5"), 16 * 3600L, cBackend, start.plusDays(6), start.plusDays(13), creatorId);
        bindLabels(t1_3, List.of(lbBackend));

        // Epic 2
        WorkItem epic2 = saveWorkItem(project, KEY_WEB, seq++, epicType, sTodo, null, null,
                "Phát triển Danh mục sản phẩm (Product Catalog) & Module Thành viên",
                "Xây dựng API quản lý sản phẩm biến thể, bộ lọc tìm kiếm full-text search và module xác thực bảo mật tài khoản người dùng.",
                WorkItemPriority.HIGH, null, null, cBackend, start.plusDays(14), start.plusDays(28), creatorId);

        WorkItem t2_1 = saveWorkItem(project, KEY_WEB, seq++, taskType, sTodo, epic2.getId(), sp2.getId(),
                "Phát triển RESTful API Quản lý danh mục đa cấp và sản phẩm có biến thể (Màu, Size)",
                "Hỗ trợ phân cấp danh mục cha - con vô hạn cấp, quản lý SKU, giá bán, giá khuyến mãi và số lượng tồn kho.",
                WorkItemPriority.HIGH, new BigDecimal("8"), 24 * 3600L, cBackend, start.plusDays(14), start.plusDays(21), creatorId);
        bindLabels(t2_1, List.of(lbBackend, lbApi));

        WorkItem t2_2 = saveWorkItem(project, KEY_WEB, seq++, taskType, sTodo, epic2.getId(), sp2.getId(),
                "Xây dựng chức năng Tìm kiếm full-text search và Bộ lọc động sản phẩm",
                "Lọc sản phẩm theo khoảng giá, danh mục, thương hiệu, đánh giá sao kết hợp sắp xếp đa tiêu chí.",
                WorkItemPriority.MEDIUM, new BigDecimal("5"), 16 * 3600L, cFrontend, start.plusDays(20), start.plusDays(26), creatorId);
        bindLabels(t2_2, List.of(lbFrontend));

        WorkItem t2_3 = saveWorkItem(project, KEY_WEB, seq++, taskType, sTodo, epic2.getId(), sp2.getId(),
                "Module Xác thực tài khoản: Đăng ký, Đăng nhập OTP/Google OAuth2 và Quản lý Profile",
                "Bảo mật mật khẩu Bcrypt, phát hành JWT token, làm mới refreshToken và quản lý sổ địa chỉ giao hàng.",
                WorkItemPriority.HIGH, new BigDecimal("5"), 18 * 3600L, cBackend, start.plusDays(15), start.plusDays(23), creatorId);
        bindLabels(t2_3, List.of(lbBackend, lbSecurity));

        // Epic 3
        WorkItem epic3 = saveWorkItem(project, KEY_WEB, seq++, epicType, sTodo, null, null,
                "Giỏ hàng, Đặt hàng & Tích hợp Cổng thanh toán trực tuyến",
                "Phát triển logic giỏ hàng lưu trữ Redis, mã giảm giá Coupon, checkout và tích hợp cổng thanh toán VNPay, Momo, thẻ ngân hàng.",
                WorkItemPriority.HIGH, null, null, cPayment, start.plusDays(28), start.plusDays(42), creatorId);

        WorkItem t3_1 = saveWorkItem(project, KEY_WEB, seq++, taskType, sTodo, epic3.getId(), sp3.getId(),
                "Phát triển Giỏ hàng lưu trữ session/Redis và tự động đồng bộ khi khách đăng nhập",
                "Cho phép khách vãng lai thêm hàng vào giỏ, tự động merge giỏ hàng khi người dùng đăng nhập tài khoản.",
                WorkItemPriority.MEDIUM, new BigDecimal("5"), 16 * 3600L, cFrontend, start.plusDays(28), start.plusDays(33), creatorId);
        bindLabels(t3_1, List.of(lbFrontend, lbBackend));

        WorkItem t3_2 = saveWorkItem(project, KEY_WEB, seq++, taskType, sTodo, epic3.getId(), sp3.getId(),
                "Xây dựng module Mã giảm giá (Coupon), Tính phí vận chuyển và Khuyến mãi flash sale",
                "Kiểm tra điều kiện áp dụng mã, giới hạn số lần sử dụng và tính toán phí ship tự động theo địa chỉ.",
                WorkItemPriority.HIGH, new BigDecimal("8"), 20 * 3600L, cBackend, start.plusDays(32), start.plusDays(38), creatorId);
        bindLabels(t3_2, List.of(lbBackend));

        WorkItem t3_3 = saveWorkItem(project, KEY_WEB, seq++, taskType, sTodo, epic3.getId(), sp3.getId(),
                "Tích hợp cổng thanh toán trực tuyến: VNPay, Momo, ZaloPay và thẻ tín dụng Visa/Mastercard",
                "Kết nối sandbox cổng thanh toán, xử lý callback redirect và kiểm tra tính toàn vẹn giao dịch.",
                WorkItemPriority.HIGH, new BigDecimal("8"), 24 * 3600L, cPayment, start.plusDays(34), start.plusDays(41), creatorId);
        bindLabels(t3_3, List.of(lbPayment, lbSecurity));

        WorkItem st3_3_1 = saveWorkItem(project, KEY_WEB, seq++, subtaskType, sTodo, t3_3.getId(), sp3.getId(),
                "Tích hợp API tạo mã QR thanh toán động và redirect trang thanh toán VNPay",
                "Tạo URL thanh toán có chữ ký HMAC-SHA512 kèm mã đơn hàng duy nhất.",
                WorkItemPriority.HIGH, null, 12 * 3600L, cPayment, start.plusDays(34), start.plusDays(37), creatorId);

        WorkItem st3_3_2 = saveWorkItem(project, KEY_WEB, seq++, subtaskType, sTodo, t3_3.getId(), sp3.getId(),
                "Cài đặt Webhook (IPN) xử lý kết quả thanh toán bất đồng bộ và kiểm tra chữ ký số",
                "Cập nhật trạng thái đơn hàng sang ĐÃ THANH TOÁN ngay khi nhận được tín hiệu IPN hợp lệ.",
                WorkItemPriority.HIGH, null, 12 * 3600L, cPayment, start.plusDays(38), start.plusDays(41), creatorId);

        WorkItem t3_4 = saveWorkItem(project, KEY_WEB, seq++, taskType, sTodo, epic3.getId(), sp3.getId(),
                "Hệ thống thông báo tự động gửi email/SMS xác nhận đơn hàng thành công kèm hóa đơn điện tử",
                "Thiết kế template email HTML chuyên nghiệp chứa chi tiết món hàng, giá tiền và link theo dõi đơn.",
                WorkItemPriority.LOW, new BigDecimal("3"), 8 * 3600L, cBackend, start.plusDays(37), start.plusDays(41), creatorId);
        bindLabels(t3_4, List.of(lbBackend));

        // Epic 4
        WorkItem epic4 = saveWorkItem(project, KEY_WEB, seq++, epicType, sTodo, null, null,
                "Hệ thống Quản trị CMS (Admin Portal) & Quản lý Đơn hàng",
                "Xây dựng trang Dashboard quản trị cho nhân viên bán hàng: theo dõi đơn mới, đóng gói, in vận đơn và biểu đồ báo cáo tài chính.",
                WorkItemPriority.HIGH, null, null, cAdmin, start.plusDays(42), start.plusDays(51), creatorId);

        WorkItem t4_1 = saveWorkItem(project, KEY_WEB, seq++, taskType, sTodo, epic4.getId(), sp4.getId(),
                "Xây dựng Dashboard biểu đồ thống kê doanh thu, tỷ lệ chuyển đổi đơn hàng và tồn kho sản phẩm",
                "Biểu đồ trực quan hóa dữ liệu theo ngày, tháng, quý; cảnh báo sản phẩm sắp hết hàng trong kho.",
                WorkItemPriority.HIGH, new BigDecimal("8"), 20 * 3600L, cAdmin, start.plusDays(42), start.plusDays(48), creatorId);
        bindLabels(t4_1, List.of(lbFrontend, lbBackend));

        WorkItem t4_2 = saveWorkItem(project, KEY_WEB, seq++, taskType, sTodo, epic4.getId(), sp4.getId(),
                "Chức năng Quản lý đơn hàng: Tiếp nhận, Đóng gói, In vận đơn và Bàn giao đơn vị vận chuyển",
                "Quy trình xử lý đơn hàng chuẩn 5 bước giúp kho xuất hàng nhanh chóng, không bị sót đơn.",
                WorkItemPriority.MEDIUM, new BigDecimal("5"), 16 * 3600L, cAdmin, start.plusDays(45), start.plusDays(51), creatorId);
        bindLabels(t4_2, List.of(lbBackend));

        // Epic 5
        WorkItem epic5 = saveWorkItem(project, KEY_WEB, seq++, epicType, sTodo, null, null,
                "Kiểm thử chất lượng (QA), Tối ưu hiệu năng & Triển khai Production",
                "Kiểm thử bảo mật chống SQLi/XSS, stress test mô phỏng tải cao, tối ưu điểm Google Core Web Vitals và thiết lập CI/CD triển khai hệ thống.",
                WorkItemPriority.HIGH, null, null, cDevops, start.plusDays(48), end, creatorId);

        WorkItem t5_1 = saveWorkItem(project, KEY_WEB, seq++, taskType, sTodo, epic5.getId(), sp4.getId(),
                "Kiểm thử tải (Load Testing) mô phỏng 10,000 người dùng đồng thời trong sự kiện Sale bằng JMeter",
                "Xác định điểm nghẽn cổ chai database pool, thời gian phản hồi API và tối ưu cấu hình Redis cache.",
                WorkItemPriority.HIGH, new BigDecimal("5"), 14 * 3600L, cDevops, start.plusDays(48), start.plusDays(53), creatorId);
        bindLabels(t5_1, List.of(lbSecurity));

        WorkItem t5_2 = saveWorkItem(project, KEY_WEB, seq++, taskType, sTodo, epic5.getId(), sp4.getId(),
                "Tối ưu Core Web Vitals (LCP < 2.5s, CLS < 0.1), SEO Onpage và nén ảnh định dạng WebP",
                "Đạt điểm PageSpeed Insights trên 90 điểm cho cả Mobile và Desktop, tối ưu thẻ meta OpenGraph mạng xã hội.",
                WorkItemPriority.MEDIUM, new BigDecimal("5"), 12 * 3600L, cFrontend, start.plusDays(50), start.plusDays(54), creatorId);
        bindLabels(t5_2, List.of(lbFrontend));

        WorkItem t5_3 = saveWorkItem(project, KEY_WEB, seq++, taskType, sTodo, epic5.getId(), sp4.getId(),
                "Thiết lập pipeline CI/CD tự động build, test và triển khai lên môi trường Production Docker",
                "Tự động chạy bộ test suite khi merge Pull Request vào nhánh main và rolling deploy không downtime.",
                WorkItemPriority.HIGH, new BigDecimal("5"), 14 * 3600L, cDevops, start.plusDays(52), end, creatorId);
        bindLabels(t5_3, List.of(lbBackend));

        updateSequenceValue(KEY_WEB, seq - 1);
        validateBlueprint(project);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 3. MẪU DỰ ÁN 3: KẾ HOẠCH VẬN HÀNH MÙA VỤ CÂY ĂN QUẢ (NÔNG NGHIỆP)
    // ─────────────────────────────────────────────────────────────────────────────
    private void seedAgriTemplate(UUID workspaceId, UUID creatorId) {
        log.info("Đang khởi tạo mẫu dự án: Mùa vụ Cây ăn quả Nông nghiệp ({})", KEY_AGRI);
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusDays(90);

        atomicKeySequenceGenerator.createSequenceForProject(KEY_AGRI);

        Project project = projectRepository.save(Project.builder()
                .workspaceId(workspaceId)
                .name("Kế hoạch Vận hành Mùa vụ Cây ăn quả (Nông nghiệp)")
                .key(KEY_AGRI)
                .description("Mẫu kế hoạch quản lý toàn bộ chu kỳ 1 mùa vụ thu hoạch cây ăn quả (Sầu riêng, Xoài, Bưởi, Cam...) theo tiêu chuẩn VietGAP: từ phục hồi cây sau vụ trước, tỉa cành tạo tán, xử lý xiết nước kích hoa, nuôi quả, bao trái ngừa dịch hại đến thu hái, phân loại và xuất bán.")
                .leadUserId(creatorId)
                .createdBy(creatorId)
                .defaultAssigneeType(DefaultAssigneeType.PROJECT_LEADER)
                .projectType(ProjectType.SOFTWARE_SCRUM)
                .isTemplate(true)
                .templateCategory(TemplateCategory.OPERATIONS)
                .startDate(start)
                .endDate(end)
                .build());

        seedRolesAndPermissions(project);

        Map<String, WorkItemType> types = seedStandardTypes(project);
        WorkItemType epicType = types.get("EPIC");
        WorkItemType taskType = types.get("TASK");
        WorkItemType subtaskType = types.get("SUBTASK");

        // Statuses
        Status sTodo = createStatus(project, "Lập kế hoạch", StatusCategory.TODO, "#64748B", "Lên lịch kỹ thuật canh tác và dự trù vật tư nông nghiệp");
        Status sProg = createStatus(project, "Đang canh tác", StatusCategory.IN_PROGRESS, "#3B82F6", "Đang triển khai bón phân, tưới nước, tỉa cành, kích hoa hoặc bao trái");
        Status sReview = createStatus(project, "Kiểm tra kỹ thuật", StatusCategory.IN_PROGRESS, "#F59E0B", "Đo chỉ số pH đất, kiểm tra sâu bệnh hại, đo độ ngọt Brix và test dư lượng");
        Status sDone = createStatus(project, "Đạt chuẩn / Đã thu hoạch", StatusCategory.DONE, "#10B981", "Nghiệm thu đạt chuẩn kỹ thuật VietGAP và xuất bán hoàn tất");

        // Workflow
        Workflow workflow = workflowRepository.save(Workflow.builder()
                .projectId(project.getId())
                .name("Quy trình Quản lý Mùa vụ Nông nghiệp VietGAP")
                .description("Quy trình kiểm soát nghiêm ngặt từng giai đoạn sinh trưởng và an toàn thực phẩm")
                .isDefault(true)
                .build());

        workflowTransitionRepository.saveAll(List.of(
                createTransition(workflow, null, sTodo, "Create"),
                createTransition(workflow, sTodo, sProg, "Bắt đầu canh tác"),
                createTransition(workflow, sProg, sReview, "Yêu cầu kiểm tra kỹ thuật"),
                createTransition(workflow, sReview, sDone, "Nghiệm thu đạt chuẩn"),
                createTransition(workflow, sReview, sProg, "Xử lý bổ sung / Chăm sóc lại"),
                createTransition(workflow, sTodo, sDone, "Nghiệm thu trực tiếp"),
                createTransition(workflow, sDone, sProg, "Mở lại kiểm tra")
        ));

        bindWorkflowToTypes(project, workflow, List.of(epicType, taskType, subtaskType));

        // Board & Columns
        Board board = boardRepository.save(Board.builder()
                .projectId(project.getId())
                .name("Bảng Quản lý Mùa vụ Nông nghiệp")
                .type(BoardType.SCRUM)
                .build());

        createColumn(board, "KẾ HOẠCH NÔNG VỤ", 0, sTodo);
        createColumn(board, "ĐANG THỰC HIỆN", 1, sProg);
        createColumn(board, "KIỂM ĐỊNH KỸ THUẬT", 2, sReview);
        createColumn(board, "HOÀN THÀNH", 3, sDone);

        // Components & Labels
        Component cDatNuoc = createComponent(project, "Đất & Nước tưới", "Xử lý độ pH đất, hệ thống tưới nhỏ giọt tự động, nguồn nước sạch");
        Component cDinhDuong = createComponent(project, "Phân bón & Dinh dưỡng", "Phân hữu cơ vi sinh, NPK định kỳ và phân bón lá nuôi quả");
        Component cBvtv = createComponent(project, "Bảo vệ thực vật & Sâu bệnh", "Chế phẩm sinh học phòng trừ nấm khuẩn, sâu đục thân, rệp sáp");
        Component cKichHoa = createComponent(project, "Tỉa cành & Kích hoa", "Cắt tỉa cành tạo tán, xiết nước và kỹ thuật phân hóa mầm hoa");
        Component cNuoiTrai = createComponent(project, "Nuôi quả & Bao trái", "Tỉa quả non, bao trái chống ruồi vàng, kiểm tra độ ngọt Brix");
        Component cThuHoach = createComponent(project, "Thu hoạch & Tiêu thụ", "Kiểm tra dư lượng thuốc BVTV, hái quả, đóng gói dán tem QR");

        Label lbPhanBon = createLabel(project, "phan-bon", "#10B981");
        Label lbKichHoa = createLabel(project, "kich-hoa", "#F59E0B");
        Label lbBaoTrai = createLabel(project, "bao-trai", "#8B5CF6");
        Label lbVietgap = createLabel(project, "vietgap", "#3B82F6");
        Label lbSauBenh = createLabel(project, "sau-benh", "#EF4444");
        Label lbThuHoach = createLabel(project, "thu-hoach", "#EC4899");

        // Sprints (3 Giai đoạn Mùa vụ)
        Sprint sp1 = createSprint(project, "Giai đoạn 1: Cải tạo đất, Phục hồi cây & Xử lý phân hóa mầm hoa",
                "Phân tích mẫu đất, bón lót phân vi sinh, cắt tỉa cành và kích thích ra hoa đồng loạt", start, start.plusDays(18));
        Sprint sp2 = createSprint(project, "Giai đoạn 2: Nuôi hoa, Thụ phấn & Chăm sóc giai đoạn đậu quả non",
                "Bổ sung Canxi-Bo hỗ trợ hạt phấn sống, phun phòng bọ trĩ và tỉa định hình quả non", start.plusDays(18), start.plusDays(36));
        Sprint sp3 = createSprint(project, "Giai đoạn 3: Nuôi lớn quả, Bao trái chống ruồi đục & Thu hoạch xuất bán",
                "Bao bọc túi chuyên dụng toàn vườn, bón kali tăng ngọt và thu hoạch xuất bán siêu thị", start.plusDays(36), end);

        int seq = 1;
        // Epic 1
        WorkItem epic1 = saveWorkItem(project, KEY_AGRI, seq++, epicType, sTodo, null, null,
                "Cải tạo đất vườn, Hệ thống tưới & Chuẩn bị vật tư nông nghiệp",
                "Đo độ pH đất, khử chua bằng vôi bột, bảo dưỡng hệ thống béc tưới nhỏ giọt tự động và dự trù phân bón hữu cơ sinh học.",
                WorkItemPriority.HIGH, null, null, cDatNuoc, start.plusDays(1), start.plusDays(18), creatorId);

        WorkItem t1_1 = saveWorkItem(project, KEY_AGRI, seq++, taskType, sTodo, epic1.getId(), sp1.getId(),
                "Đo nồng độ pH đất, kiểm tra độ ẩm và rải vôi bột khử trùng toàn vườn",
                "Đo pH tại các điểm rễ cây, xử lý đất chua đạt ngưỡng lý tưởng 5.5 - 6.5 để rễ hấp thu tối đa dưỡng chất.",
                WorkItemPriority.HIGH, new BigDecimal("3"), 8 * 3600L, cDatNuoc, start.plusDays(1), start.plusDays(6), creatorId);
        bindLabels(t1_1, List.of(lbVietgap));

        WorkItem st1_1_1 = saveWorkItem(project, KEY_AGRI, seq++, subtaskType, sTodo, t1_1.getId(), sp1.getId(),
                "Lấy mẫu đất tại 5 điểm đối xứng trong vườn để đo chỉ số pH bằng máy điện tử",
                "Lấy mẫu ở tầng đất 10 - 20cm, ghi nhận vào sổ nhật ký canh tác điện tử.",
                WorkItemPriority.MEDIUM, null, 4 * 3600L, cDatNuoc, start.plusDays(1), start.plusDays(3), creatorId);

        WorkItem st1_1_2 = saveWorkItem(project, KEY_AGRI, seq++, subtaskType, sTodo, t1_1.getId(), sp1.getId(),
                "Rải vôi bột khử chua, cân bằng pH và diệt trừ mầm nấm rễ Phytophthora",
                "Rải đều quanh tán cây theo định lượng 2kg vôi/gốc, tưới đẫm nước sau khi rải.",
                WorkItemPriority.HIGH, null, 4 * 3600L, cDatNuoc, start.plusDays(3), start.plusDays(6), creatorId);

        WorkItem t1_2 = saveWorkItem(project, KEY_AGRI, seq++, taskType, sTodo, epic1.getId(), sp1.getId(),
                "Bảo dưỡng, vệ sinh hệ thống đường ống tưới nhỏ giọt và béc phun tự động",
                "Sục rửa cặn lắng đường ống dẫn nước chính, kiểm tra áp lực bơm và thay thế các béc phun hỏng.",
                WorkItemPriority.MEDIUM, new BigDecimal("3"), 8 * 3600L, cDatNuoc, start.plusDays(5), start.plusDays(10), creatorId);

        WorkItem t1_3 = saveWorkItem(project, KEY_AGRI, seq++, taskType, sTodo, epic1.getId(), sp1.getId(),
                "Nhập kho phân chuồng hoai mục ủ nấm Trichoderma, phân hữu cơ vi sinh và chế phẩm sinh học",
                "Tập kết vật tư nông nghiệp đạt chuẩn kiểm định chất lượng, bảo quản trong kho khô ráo.",
                WorkItemPriority.HIGH, new BigDecimal("5"), 12 * 3600L, cDinhDuong, start.plusDays(8), start.plusDays(15), creatorId);
        bindLabels(t1_3, List.of(lbPhanBon));

        // Epic 2
        WorkItem epic2 = saveWorkItem(project, KEY_AGRI, seq++, epicType, sTodo, null, null,
                "Cắt tỉa cành, Kích thích ra hoa đồng loạt & Quản lý thụ phấn",
                "Cắt tỉa cành tăm, kỹ thuật xiết nước kích mầm hoa, phun vi lượng Canxi - Bo tăng tỷ lệ đậu quả và phòng bọ trĩ phá hoa.",
                WorkItemPriority.HIGH, null, null, cKichHoa, start.plusDays(12), start.plusDays(36), creatorId);

        WorkItem t2_1 = saveWorkItem(project, KEY_AGRI, seq++, taskType, sTodo, epic2.getId(), sp1.getId(),
                "Cắt tỉa cành tăm, cành sâu bệnh, cành vượt trong tán để vườn thông thoáng đón ánh sáng",
                "Tạo độ thông thoáng cho cây quang hợp, triệt tiêu nơi trú ngụ của rệp sáp và nấm hồng.",
                WorkItemPriority.HIGH, new BigDecimal("5"), 16 * 3600L, cKichHoa, start.plusDays(12), start.plusDays(17), creatorId);
        bindLabels(t2_1, List.of(lbKichHoa));

        WorkItem t2_2 = saveWorkItem(project, KEY_AGRI, seq++, taskType, sTodo, epic2.getId(), sp2.getId(),
                "Áp dụng quy trình kỹ thuật xiết nước (cắt nước) và phun phân bón lá lân cao để kích hoa",
                "Cắt nước trong 10 - 15 ngày kích cây chuyển hóa sinh sản tạo mầm hoa (mắt cua).",
                WorkItemPriority.HIGH, new BigDecimal("8"), 16 * 3600L, cKichHoa, start.plusDays(18), start.plusDays(24), creatorId);
        bindLabels(t2_2, List.of(lbKichHoa, lbPhanBon));

        WorkItem t2_3 = saveWorkItem(project, KEY_AGRI, seq++, taskType, sTodo, epic2.getId(), sp2.getId(),
                "Phun bổ sung vi lượng Canxi - Bo để tăng sức sống hạt phấn và hỗ trợ thụ phấn đậu quả",
                "Phun định kỳ 2 lần trước khi hoa nở rộ 7 ngày giúp cuống hoa dẻo dai, chống rụng hoa sinh lý.",
                WorkItemPriority.HIGH, new BigDecimal("5"), 10 * 3600L, cDinhDuong, start.plusDays(25), start.plusDays(30), creatorId);
        bindLabels(t2_3, List.of(lbPhanBon));

        WorkItem t2_4 = saveWorkItem(project, KEY_AGRI, seq++, taskType, sTodo, epic2.getId(), sp2.getId(),
                "Kiểm tra và phun phòng ngừa bọ trĩ, rệp sáp tấn công cuống hoa trong giai đoạn nhú mắt cua",
                "Sử dụng dầu khoáng nông nghiệp hoặc hoạt chất sinh học an toàn cho ong thụ phấn.",
                WorkItemPriority.MEDIUM, new BigDecimal("5"), 12 * 3600L, cBvtv, start.plusDays(28), start.plusDays(35), creatorId);
        bindLabels(t2_4, List.of(lbSauBenh));

        // Epic 3
        WorkItem epic3 = saveWorkItem(project, KEY_AGRI, seq++, epicType, sTodo, null, null,
                "Chăm sóc quả non, Tỉa định quả & Bao bọc quả ngừa sâu bệnh",
                "Tuyển chọn những chùm quả khỏe nhất, tỉa bỏ quả méo mó dị tật và tiến hành bọc túi vải chuyên dụng cách ly ruồi vàng đục quả.",
                WorkItemPriority.HIGH, null, null, cNuoiTrai, start.plusDays(36), start.plusDays(48), creatorId);

        WorkItem t3_1 = saveWorkItem(project, KEY_AGRI, seq++, taskType, sTodo, epic3.getId(), sp3.getId(),
                "Tỉa bỏ quả non dị tật, quả méo mó, chỉ giữ lại số lượng chùm quả cân đối với sức cây",
                "Mỗi chùm chỉ để từ 1 - 2 quả tròn đều, giúp cây tập trung dinh dưỡng nuôi quả đạt Size Loại 1.",
                WorkItemPriority.HIGH, new BigDecimal("5"), 14 * 3600L, cNuoiTrai, start.plusDays(36), start.plusDays(41), creatorId);
        bindLabels(t3_1, List.of(lbBaoTrai));

        WorkItem t3_2 = saveWorkItem(project, KEY_AGRI, seq++, taskType, sTodo, epic3.getId(), sp3.getId(),
                "Phun chế phẩm sinh học gốc thảo mộc làm sạch bề mặt quả trước khi tiến hành bao bọc trái",
                "Rửa sạch trứng sâu và bào tử nấm trên bề mặt vỏ quả trước khi đóng kín túi bao.",
                WorkItemPriority.MEDIUM, new BigDecimal("3"), 6 * 3600L, cBvtv, start.plusDays(40), start.plusDays(43), creatorId);
        bindLabels(t3_2, List.of(lbSauBenh));

        WorkItem t3_3 = saveWorkItem(project, KEY_AGRI, seq++, taskType, sTodo, epic3.getId(), sp3.getId(),
                "Tiến hành bọc túi chuyên dụng (túi vải không dệt 2 lớp) phòng ngừa ruồi vàng đục quả",
                "Bọc kín từng quả hoặc chùm quả, cố định miệng túi bằng dây rút nhôm chuyên dụng.",
                WorkItemPriority.HIGH, new BigDecimal("8"), 24 * 3600L, cNuoiTrai, start.plusDays(42), start.plusDays(48), creatorId);
        bindLabels(t3_3, List.of(lbBaoTrai, lbVietgap));

        WorkItem st3_3_1 = saveWorkItem(project, KEY_AGRI, seq++, subtaskType, sTodo, t3_3.getId(), sp3.getId(),
                "Chuẩn bị túi bao trái đạt chuẩn VietGAP và dụng cụ thang nhôm chuyên dụng",
                "Kiểm tra số lượng 5,000 túi bọc và kiểm tra độ an toàn của thang nhôm trước khi trèo.",
                WorkItemPriority.MEDIUM, null, 8 * 3600L, cNuoiTrai, start.plusDays(42), start.plusDays(44), creatorId);

        WorkItem st3_3_2 = saveWorkItem(project, KEY_AGRI, seq++, subtaskType, sTodo, t3_3.getId(), sp3.getId(),
                "Thực hiện bao bọc toàn bộ các cây ăn trái trong vườn theo đúng kỹ thuật",
                "Chia đội nhân công phụ trách từng lô cây, đảm bảo không bỏ sót quả trên cành cao.",
                WorkItemPriority.HIGH, null, 16 * 3600L, cNuoiTrai, start.plusDays(44), start.plusDays(48), creatorId);

        // Epic 4
        WorkItem epic4 = saveWorkItem(project, KEY_AGRI, seq++, epicType, sTodo, null, null,
                "Quản lý dinh dưỡng giai đoạn nuôi quả lớn & Tăng độ ngọt (Brix)",
                "Bón phân cân đối hàm lượng Kali cao giúp trái đậm vị ngọt thơm, điều tiết nước tưới tránh nứt quả và kiểm tra chỉ số đường Brix định kỳ.",
                WorkItemPriority.HIGH, null, null, cDinhDuong, start.plusDays(45), start.plusDays(54), creatorId);

        WorkItem t4_1 = saveWorkItem(project, KEY_AGRI, seq++, taskType, sTodo, epic4.getId(), sp3.getId(),
                "Bón phân NPK cân đối kết hợp phân hữu cơ Kali cao để nuôi cơm trái và tích lũy đường",
                "Bón theo hình chiếu tán cây, kết hợp xới nhẹ và tưới nước giữ ẩm để cây hấp thu chậm.",
                WorkItemPriority.HIGH, new BigDecimal("5"), 10 * 3600L, cDinhDuong, start.plusDays(45), start.plusDays(50), creatorId);
        bindLabels(t4_1, List.of(lbPhanBon));

        WorkItem t4_2 = saveWorkItem(project, KEY_AGRI, seq++, taskType, sTodo, epic4.getId(), sp3.getId(),
                "Điều tiết độ ẩm và lượng nước tưới hợp lý, tránh ngập úng gây hiện tượng nứt vỏ",
                "Duy trì tưới nhẹ vào sáng sớm, giảm 50% lượng nước trước ngày thu hoạch 10 ngày để trái đậm vị.",
                WorkItemPriority.MEDIUM, new BigDecimal("3"), 8 * 3600L, cDatNuoc, start.plusDays(48), start.plusDays(53), creatorId);

        WorkItem t4_3 = saveWorkItem(project, KEY_AGRI, seq++, taskType, sTodo, epic4.getId(), sp3.getId(),
                "Lấy mẫu quả đo chỉ số độ ngọt Brix bằng khúc xạ kế định kỳ hàng tuần để chốt ngày thu hoạch",
                "Test độ ngọt đạt chuẩn tối thiểu > 14 độ Brix đối với quả chín cây trước khi phê duyệt thu hoạch.",
                WorkItemPriority.HIGH, new BigDecimal("3"), 6 * 3600L, cNuoiTrai, start.plusDays(51), start.plusDays(54), creatorId);
        bindLabels(t4_3, List.of(lbVietgap));

        // Epic 5
        WorkItem epic5 = saveWorkItem(project, KEY_AGRI, seq++, epicType, sTodo, null, null,
                "Thu hoạch đúng độ chín, Sơ chế phân loại & Tiêu thụ xuất bán",
                "Cách ly thuốc BVTV đảm bảo dư lượng bằng 0, tổ chức thu hái quả bằng kéo chuyên dụng, đóng thùng dán tem QR truy xuất nguồn gốc và giao siêu thị.",
                WorkItemPriority.HIGH, null, null, cThuHoach, start.plusDays(52), end, creatorId);

        WorkItem t5_1 = saveWorkItem(project, KEY_AGRI, seq++, taskType, sTodo, epic5.getId(), sp3.getId(),
                "Kiểm tra thời gian cách ly phân bón và thuốc BVTV, đảm bảo dư lượng bằng 0 đạt chuẩn VietGAP",
                "Gửi mẫu trái đến trung tâm kiểm định độc lập kiểm tra dư lượng kim loại nặng và thuốc BVTV.",
                WorkItemPriority.HIGH, new BigDecimal("5"), 8 * 3600L, cThuHoach, start.plusDays(52), start.plusDays(54), creatorId);
        bindLabels(t5_1, List.of(lbVietgap));

        WorkItem t5_2 = saveWorkItem(project, KEY_AGRI, seq++, taskType, sTodo, epic5.getId(), sp3.getId(),
                "Tổ chức thu hái quả bằng kéo chuyên dụng vào sáng sớm, xếp vào sọt lót xốp",
                "Cắt chừa cuống 2 - 3cm, đặt nhẹ nhàng tránh dập vỏ gây thối hỏng trong quá trình vận chuyển.",
                WorkItemPriority.HIGH, new BigDecimal("8"), 16 * 3600L, cThuHoach, start.plusDays(54), start.plusDays(55), creatorId);
        bindLabels(t5_2, List.of(lbThuHoach));

        WorkItem t5_3 = saveWorkItem(project, KEY_AGRI, seq++, taskType, sTodo, epic5.getId(), sp3.getId(),
                "Sơ chế làm sạch, phân loại Size quả Loại 1 và Loại 2, đóng thùng carton và dán tem QR",
                "Lau sạch bụi phấn, dán tem QR truy xuất nguồn gốc lô nông trại và đóng hộp chuẩn quy cách bảo quản.",
                WorkItemPriority.MEDIUM, new BigDecimal("5"), 12 * 3600L, cThuHoach, start.plusDays(55), start.plusDays(56), creatorId);
        bindLabels(t5_3, List.of(lbThuHoach, lbVietgap));

        WorkItem t5_4 = saveWorkItem(project, KEY_AGRI, seq++, taskType, sTodo, epic5.getId(), sp3.getId(),
                "Bàn giao lô hàng cho chuỗi cửa hàng hoa quả sạch / siêu thị đối tác và quyết toán mùa vụ",
                "Ký biên bản nghiệm thu giao nhận số lượng, trọng lượng và tổng kết hạch toán chi phí - doanh thu mùa vụ.",
                WorkItemPriority.HIGH, new BigDecimal("3"), 6 * 3600L, cThuHoach, start.plusDays(56), end, creatorId);
        bindLabels(t5_4, List.of(lbThuHoach));

        updateSequenceValue(KEY_AGRI, seq - 1);
        validateBlueprint(project);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // TIỆN ÍCH DỰNG BLUEPRINT CHUẨN CODE & DB
    // ─────────────────────────────────────────────────────────────────────────────

    private UUID resolveCreatorId() {
        try {
            List<UUID> userIds = jdbcTemplate.query(
                    "SELECT created_by FROM workspaces WHERE id = ? LIMIT 1",
                    (rs, rowNum) -> (UUID) rs.getObject("created_by"),
                    DEFAULT_WORKSPACE_ID
            );
            if (!userIds.isEmpty() && userIds.get(0) != null) {
                return userIds.get(0);
            }
        } catch (Exception e) {
            log.warn("Không tra cứu được creator_id của workspace mặc định: {}", e.getMessage());
        }
        return DEFAULT_CREATOR_ID;
    }

    private void seedRolesAndPermissions(Project project) {
        ProjectRole adminRole = projectRoleRepository.save(ProjectRole.builder()
                .projectId(project.getId())
                .name("ADMINISTRATOR")
                .description("Toàn quyền quản trị cấu hình, phân quyền, workflow và công việc trong dự án")
                .build());

        ProjectRole memberRole = projectRoleRepository.save(ProjectRole.builder()
                .projectId(project.getId())
                .name("MEMBER")
                .description("Thành viên chính: tạo, sửa, gán người và chuyển trạng thái công việc")
                .build());

        ProjectRole viewerRole = projectRoleRepository.save(ProjectRole.builder()
                .projectId(project.getId())
                .name("VIEWER")
                .description("Thành viên xem: theo dõi tiến độ và tham gia bình luận trao đổi")
                .build());

        List<PermissionSchemeEntry> entries = new ArrayList<>();
        for (PermissionKey pk : PermissionKey.values()) {
            entries.add(PermissionSchemeEntry.builder().projectId(project.getId()).roleId(adminRole.getId()).permissionKey(pk.name()).build());
        }

        List<PermissionKey> memberKeys = List.of(
                PermissionKey.PROJECT_VIEW, PermissionKey.ISSUE_CREATE, PermissionKey.ISSUE_EDIT,
                PermissionKey.ISSUE_TRANSITION, PermissionKey.ISSUE_ASSIGN, PermissionKey.COMMENT_CREATE,
                PermissionKey.COMMENT_EDIT_OWN, PermissionKey.COMMENT_DELETE_OWN, PermissionKey.ATTACHMENT_DELETE_OWN
        );
        for (PermissionKey pk : memberKeys) {
            entries.add(PermissionSchemeEntry.builder().projectId(project.getId()).roleId(memberRole.getId()).permissionKey(pk.name()).build());
        }

        List<PermissionKey> viewerKeys = List.of(
                PermissionKey.PROJECT_VIEW, PermissionKey.COMMENT_CREATE,
                PermissionKey.COMMENT_EDIT_OWN, PermissionKey.COMMENT_DELETE_OWN
        );
        for (PermissionKey pk : viewerKeys) {
            entries.add(PermissionSchemeEntry.builder().projectId(project.getId()).roleId(viewerRole.getId()).permissionKey(pk.name()).build());
        }

        permissionSchemeEntryRepository.saveAll(entries);
    }

    private Map<String, WorkItemType> seedStandardTypes(Project project) {
        WorkItemType epic = workItemTypeRepository.save(WorkItemType.builder()
                .projectId(project.getId()).name("Epic").code("EPIC")
                .description("Hạng mục lớn bao quát nhiều công việc").iconName("Zap").colorHex("#9333EA")
                .hierarchyLevel(1).isSubtask(false).isSystemDefault(true).build());

        WorkItemType task = workItemTypeRepository.save(WorkItemType.builder()
                .projectId(project.getId()).name("Task").code("TASK")
                .description("Công việc tiêu chuẩn").iconName("CheckSquare").colorHex("#3B82F6")
                .hierarchyLevel(0).isSubtask(false).isSystemDefault(true).build());

        WorkItemType subtask = workItemTypeRepository.save(WorkItemType.builder()
                .projectId(project.getId()).name("Subtask").code("SUBTASK")
                .description("Công việc con phụ thuộc của Task").iconName("GitCommit").colorHex("#06B6D4")
                .hierarchyLevel(-1).isSubtask(true).isSystemDefault(true).build());

        return Map.of("EPIC", epic, "TASK", task, "SUBTASK", subtask);
    }

    private Status createStatus(Project project, String name, StatusCategory category, String colorHex, String description) {
        return statusRepository.save(Status.builder()
                .projectId(project.getId())
                .name(name)
                .category(category)
                .colorHex(colorHex)
                .description(description)
                .build());
    }

    private WorkflowTransition createTransition(Workflow workflow, Status from, Status to, String name) {
        return WorkflowTransition.builder()
                .workflowId(workflow.getId())
                .fromStatusId(from != null ? from.getId() : null)
                .toStatusId(to.getId())
                .name(name)
                .build();
    }

    private void bindWorkflowToTypes(Project project, Workflow workflow, List<WorkItemType> types) {
        WorkflowScheme scheme = workflowSchemeRepository.save(WorkflowScheme.builder()
                .projectId(project.getId())
                .name(project.getName() + " Workflow Scheme")
                .description("Gắn các loại công việc vào quy trình chuẩn")
                .build());

        List<WorkflowSchemeMapping> mappings = types.stream()
                .map(t -> WorkflowSchemeMapping.builder()
                        .schemeId(scheme.getId())
                        .workItemTypeId(t.getId())
                        .workflowId(workflow.getId())
                        .build())
                .toList();
        workflowSchemeMappingRepository.saveAll(mappings);
    }

    private void createColumn(Board board, String name, int order, Status status) {
        BoardColumn col = boardColumnRepository.save(BoardColumn.builder()
                .boardId(board.getId())
                .name(name)
                .columnOrder(order)
                .build());

        boardColumnStatusRepository.save(BoardColumnStatus.builder()
                .columnId(col.getId())
                .statusId(status.getId())
                .build());
    }

    private Component createComponent(Project project, String name, String desc) {
        return componentRepository.save(Component.builder()
                .projectId(project.getId())
                .name(name)
                .description(desc)
                .build());
    }

    private Label createLabel(Project project, String name, String colorHex) {
        return labelRepository.save(Label.builder()
                .projectId(project.getId())
                .name(name)
                .colorHex(colorHex)
                .build());
    }

    private Sprint createSprint(Project project, String name, String goal, LocalDate start, LocalDate end) {
        return sprintRepository.save(Sprint.builder()
                .projectId(project.getId())
                .name(name)
                .goal(goal)
                .state(SprintState.FUTURE)
                .startDate(start.atStartOfDay())
                .endDate(end.atTime(23, 59, 59))
                .build());
    }

    private WorkItem saveWorkItem(Project project, String keyPrefix, int codeNumber, WorkItemType type, Status status,
                                 UUID parentId, UUID sprintId, String summary, String description,
                                 WorkItemPriority priority, BigDecimal storyPoints, Long estimateSeconds,
                                 Component component, LocalDate startDate, LocalDate dueDate, UUID creatorId) {
        String code = keyPrefix + "-" + codeNumber;
        String rank = String.format("0|i%05d:", codeNumber);

        WorkItem item = workItemRepository.save(WorkItem.builder()
                .projectId(project.getId())
                .typeId(type.getId())
                .statusId(status.getId())
                .parentId(parentId)
                .sprintId(sprintId)
                .code(code)
                .summary(summary)
                .description(description)
                .priority(priority)
                .reporterId(creatorId)
                .assigneeId(null)
                .storyPoints(storyPoints)
                .originalEstimateSeconds(estimateSeconds != null ? estimateSeconds : 0L)
                .remainingEstimateSeconds(estimateSeconds != null ? estimateSeconds : 0L)
                .timeSpentSeconds(0L)
                .rank(rank)
                .componentId(component != null ? component.getId() : null)
                .startDate(startDate)
                .dueDate(dueDate)
                .version(0)
                .build());

        if (component != null) {
            workItemComponentMapRepository.save(WorkItemComponentMap.builder()
                    .workItemId(item.getId())
                    .componentId(component.getId())
                    .build());
        }

        return item;
    }

    private void bindLabels(WorkItem item, List<Label> labels) {
        if (labels == null || labels.isEmpty()) return;
        List<WorkItemLabelMap> maps = labels.stream()
                .map(lb -> WorkItemLabelMap.builder().workItemId(item.getId()).labelId(lb.getId()).build())
                .toList();
        workItemLabelMapRepository.saveAll(maps);
    }

    private void updateSequenceValue(String key, int currentCount) {
        String seqName = "work_item_seq_" + key.trim().toLowerCase();
        try {
            jdbcTemplate.execute("SELECT setval('" + seqName + "', " + currentCount + ", true)");
            log.info("Cập nhật sequence '{}' đạt giá trị: {}", seqName, currentCount);
        } catch (Exception e) {
            log.warn("Không thể cập nhật sequence {}: {}", seqName, e.getMessage());
        }
    }

    private void validateBlueprint(Project project) {
        ProjectBlueprint bp = blueprintLoader.load(project);
        TemplateIntegrityReport report = TemplateIntegrityValidator.validate(bp);
        if (!report.isValid()) {
            log.error("Template {} ({}) vi phạm tính toàn vẹn: {}",
                    project.getName(), project.getKey(), report.getErrors());
            throw new IllegalStateException("Template " + project.getKey() + " không hợp lệ: " + report.getErrors());
        }
        log.info("Xác thực mẫu {} ({}) thành công! Errors: 0, Warnings: {}",
                project.getName(), project.getKey(), report.getWarnings().size());
    }
}
