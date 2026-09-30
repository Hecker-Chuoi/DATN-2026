package com.datn.workservice.service.template;

import com.datn.workservice.dto.response.TemplateIntegrityReport;
import com.datn.workservice.entity.*;
import com.datn.workservice.enums.ProjectType;
import com.datn.workservice.enums.SprintState;
import com.datn.workservice.enums.StatusCategory;
import com.datn.workservice.exception.InvalidHierarchyException;
import com.datn.workservice.service.StrictHierarchyValidator;

import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Kiểm tra một {@link ProjectBlueprint} có đủ điều kiện làm dự án mẫu (hoặc là kết quả
 * hợp lệ của việc tạo dự án từ mẫu) hay không. Hàm thuần, không truy cập CSDL.
 * <p>
 * Nhóm ràng buộc:
 * <ul>
 *   <li><b>Trạng thái & Workflow</b>: có đủ nhóm TODO/DONE, transition chỉ trỏ tới trạng thái của chính dự án.</li>
 *   <li><b>Loại công việc & phân cấp</b>: có loại cấp 0; cây cha-con đúng quy tắc 3 cấp.</li>
 *   <li><b>Board</b>: cột chỉ gom trạng thái của dự án; trạng thái không nằm trên cột nào bị cảnh báo.</li>
 *   <li><b>Sprint & thời gian</b>: ngày kết thúc sau ngày bắt đầu, tối đa 8 tuần, nằm trong khung dự án.</li>
 *   <li><b>Phân quyền</b>: đủ 3 vai trò ADMINISTRATOR/MEMBER/VIEWER để đồng bộ thành viên nhóm.</li>
 * </ul>
 */
public final class TemplateIntegrityValidator {

    /** Khớp SprintServiceImpl.MAX_SPRINT_DAYS - sprint dài hơn sẽ không thể bắt đầu. */
    static final long MAX_SPRINT_DAYS = 56;
    static final List<String> REQUIRED_ROLES = List.of("ADMINISTRATOR", "MEMBER", "VIEWER");

    private static final StrictHierarchyValidator HIERARCHY = new StrictHierarchyValidator(null, null);

    private TemplateIntegrityValidator() {
    }

    public static TemplateIntegrityReport validate(ProjectBlueprint bp) {
        TemplateIntegrityReport report = new TemplateIntegrityReport();
        Project project = bp.project();

        Map<UUID, Status> statuses = index(bp.statuses(), Status::getId);
        Map<UUID, WorkItemType> types = index(bp.types(), WorkItemType::getId);
        Map<UUID, Sprint> sprints = index(bp.sprints(), Sprint::getId);
        Map<UUID, WorkItem> items = index(bp.workItems(), WorkItem::getId);

        checkProjectDates(project, report);
        checkStatuses(bp, report);
        checkTypes(bp, report);
        checkWorkflows(bp, statuses, types, report);
        checkBoards(bp, statuses, report);
        checkRoles(bp, report);
        checkSprints(bp, report);
        checkWorkItems(bp, statuses, types, sprints, items, report);
        return report;
    }

    private static void checkProjectDates(Project project, TemplateIntegrityReport report) {
        if (project.getStartDate() != null && project.getEndDate() != null
                && project.getEndDate().isBefore(project.getStartDate())) {
            report.error("PROJECT_DATE_RANGE", "Hạn chót dự án (" + project.getEndDate()
                    + ") sớm hơn ngày bắt đầu (" + project.getStartDate() + ")");
        }
    }

    private static void checkStatuses(ProjectBlueprint bp, TemplateIntegrityReport report) {
        if (bp.statuses().isEmpty()) {
            report.error("NO_STATUS", "Dự án chưa có trạng thái nào");
            return;
        }
        Set<StatusCategory> categories = bp.statuses().stream()
                .map(Status::getCategory).collect(Collectors.toSet());
        if (!categories.contains(StatusCategory.TODO)) {
            report.error("NO_TODO_STATUS", "Thiếu trạng thái nhóm TODO - công việc mới không có trạng thái khởi đầu");
        }
        if (!categories.contains(StatusCategory.DONE)) {
            report.error("NO_DONE_STATUS", "Thiếu trạng thái nhóm DONE - Burndown/Velocity không thể tính hoàn thành");
        }
        if (!categories.contains(StatusCategory.IN_PROGRESS)) {
            report.warning("NO_IN_PROGRESS_STATUS", "Không có trạng thái nhóm IN_PROGRESS");
        }
        Set<String> seen = new HashSet<>();
        for (Status s : bp.statuses()) {
            if (!seen.add(s.getName().trim().toLowerCase())) {
                report.error("DUPLICATE_STATUS", "Trùng tên trạng thái: " + s.getName());
            }
        }
    }

    private static void checkTypes(ProjectBlueprint bp, TemplateIntegrityReport report) {
        if (bp.types().stream().noneMatch(t -> Integer.valueOf(0).equals(t.getHierarchyLevel()))) {
            report.error("NO_STANDARD_TYPE", "Thiếu loại công việc cấp 0 (Task/Story/Bug)");
        }
        Set<String> codes = new HashSet<>();
        for (WorkItemType t : bp.types()) {
            if (!codes.add(t.getCode().toUpperCase())) {
                report.error("DUPLICATE_TYPE_CODE", "Trùng mã loại công việc: " + t.getCode());
            }
        }
    }

    private static void checkWorkflows(ProjectBlueprint bp, Map<UUID, Status> statuses,
                                       Map<UUID, WorkItemType> types, TemplateIntegrityReport report) {
        if (bp.workflows().isEmpty()) {
            report.error("NO_WORKFLOW", "Dự án chưa có workflow");
            return;
        }
        Map<UUID, Workflow> workflows = index(bp.workflows(), Workflow::getId);
        Set<UUID> reachable = new HashSet<>();

        for (WorkflowTransition t : bp.transitions()) {
            if (t.getFromStatusId() != null && !statuses.containsKey(t.getFromStatusId())) {
                report.error("TRANSITION_FOREIGN_STATUS",
                        "Transition '" + t.getName() + "' xuất phát từ trạng thái không thuộc dự án");
            }
            if (!statuses.containsKey(t.getToStatusId())) {
                report.error("TRANSITION_FOREIGN_STATUS",
                        "Transition '" + t.getName() + "' đi tới trạng thái không thuộc dự án");
            }
            reachable.add(t.getToStatusId());
        }

        for (Workflow w : bp.workflows()) {
            boolean hasCreate = bp.transitions().stream()
                    .anyMatch(t -> w.getId().equals(t.getWorkflowId()) && t.getFromStatusId() == null);
            if (!hasCreate) {
                report.warning("NO_CREATE_TRANSITION", "Workflow '" + w.getName()
                        + "' không có transition khởi tạo - công việc mới sẽ nhận trạng thái TODO đầu tiên");
            }
        }

        for (Status s : bp.statuses()) {
            if (!reachable.contains(s.getId())) {
                report.warning("STATUS_UNREACHABLE",
                        "Trạng thái '" + s.getName() + "' không có transition nào đi tới");
            }
        }

        Set<UUID> mappedTypes = new HashSet<>();
        for (WorkflowSchemeMapping m : bp.schemeMappings()) {
            if (!types.containsKey(m.getWorkItemTypeId())) {
                report.error("SCHEME_FOREIGN_TYPE", "Workflow scheme ánh xạ loại công việc không thuộc dự án");
            }
            if (!workflows.containsKey(m.getWorkflowId())) {
                report.error("SCHEME_FOREIGN_WORKFLOW", "Workflow scheme ánh xạ tới workflow không thuộc dự án");
            }
            mappedTypes.add(m.getWorkItemTypeId());
        }
        for (WorkItemType t : bp.types()) {
            if (!mappedTypes.contains(t.getId())) {
                report.warning("TYPE_UNMAPPED", "Loại '" + t.getName()
                        + "' chưa gắn workflow trong scheme - sẽ dùng workflow mặc định");
            }
        }
    }

    private static void checkBoards(ProjectBlueprint bp, Map<UUID, Status> statuses, TemplateIntegrityReport report) {
        if (bp.boards().isEmpty()) {
            report.warning("NO_BOARD", "Dự án chưa có board");
            return;
        }
        Set<UUID> onBoard = new HashSet<>();
        for (BoardColumnStatus cs : bp.columnStatuses()) {
            if (!statuses.containsKey(cs.getStatusId())) {
                report.error("COLUMN_FOREIGN_STATUS", "Cột board gom trạng thái không thuộc dự án");
            }
            onBoard.add(cs.getStatusId());
        }
        for (BoardColumn c : bp.columns()) {
            if (c.getMinWipLimit() != null && c.getMaxWipLimit() != null
                    && c.getMinWipLimit() > c.getMaxWipLimit()) {
                report.error("WIP_LIMIT_RANGE", "Cột '" + c.getName() + "' có WIP min lớn hơn WIP max");
            }
        }
        for (Status s : bp.statuses()) {
            if (!onBoard.contains(s.getId())) {
                report.warning("STATUS_NOT_ON_BOARD", "Trạng thái '" + s.getName()
                        + "' không nằm trên cột board nào - thẻ ở trạng thái này sẽ bị ẩn");
            }
        }
    }

    private static void checkRoles(ProjectBlueprint bp, TemplateIntegrityReport report) {
        Set<String> names = bp.roles().stream()
                .map(r -> r.getName().toUpperCase()).collect(Collectors.toSet());
        for (String required : REQUIRED_ROLES) {
            if (!names.contains(required)) {
                report.error("MISSING_ROLE", "Thiếu vai trò dự án " + required);
            }
        }
    }

    private static void checkSprints(ProjectBlueprint bp, TemplateIntegrityReport report) {
        Project project = bp.project();
        if (project.getProjectType() == ProjectType.SOFTWARE_KANBAN && !bp.sprints().isEmpty()) {
            report.warning("KANBAN_WITH_SPRINTS", "Dự án Kanban nhưng mẫu có Sprint");
        }

        List<Sprint> dated = new ArrayList<>();
        for (Sprint s : bp.sprints()) {
            if (s.getState() != SprintState.FUTURE) {
                report.warning("SPRINT_NOT_FUTURE", "Sprint '" + s.getName() + "' đang ở trạng thái "
                        + s.getState() + " - khi tạo dự án sẽ được đặt lại thành FUTURE");
            }
            if (s.getStartDate() == null || s.getEndDate() == null) {
                continue;
            }
            if (!s.getEndDate().isAfter(s.getStartDate())) {
                report.error("SPRINT_DATE_RANGE", "Sprint '" + s.getName() + "' có ngày kết thúc không sau ngày bắt đầu");
                continue;
            }
            if (Duration.between(s.getStartDate(), s.getEndDate()).toDays() > MAX_SPRINT_DAYS) {
                report.error("SPRINT_TOO_LONG", "Sprint '" + s.getName() + "' dài hơn 8 tuần - sẽ không thể bắt đầu");
            }
            if (project.getStartDate() != null && s.getStartDate().toLocalDate().isBefore(project.getStartDate())) {
                report.warning("SPRINT_BEFORE_PROJECT", "Sprint '" + s.getName() + "' bắt đầu trước ngày bắt đầu dự án");
            }
            if (project.getEndDate() != null && s.getEndDate().toLocalDate().isAfter(project.getEndDate())) {
                report.error("SPRINT_AFTER_PROJECT", "Sprint '" + s.getName() + "' kết thúc sau hạn chót dự án");
            }
            dated.add(s);
        }

        dated.sort(Comparator.comparing(Sprint::getStartDate));
        for (int i = 1; i < dated.size(); i++) {
            Sprint prev = dated.get(i - 1);
            Sprint cur = dated.get(i);
            if (cur.getStartDate().isBefore(prev.getEndDate())) {
                report.warning("SPRINT_OVERLAP", "Sprint '" + prev.getName() + "' và '" + cur.getName() + "' chồng lấn thời gian");
            }
        }
    }

    private static void checkWorkItems(ProjectBlueprint bp, Map<UUID, Status> statuses, Map<UUID, WorkItemType> types,
                                       Map<UUID, Sprint> sprints, Map<UUID, WorkItem> items,
                                       TemplateIntegrityReport report) {
        Project project = bp.project();
        for (WorkItem item : bp.workItems()) {
            String code = item.getCode();
            if (!statuses.containsKey(item.getStatusId())) {
                report.error("ITEM_FOREIGN_STATUS", code + ": trạng thái không thuộc dự án");
            }
            WorkItemType type = types.get(item.getTypeId());
            if (type == null) {
                report.error("ITEM_FOREIGN_TYPE", code + ": loại công việc không thuộc dự án");
            }
            if (item.getSprintId() != null && !sprints.containsKey(item.getSprintId())) {
                report.error("ITEM_FOREIGN_SPRINT", code + ": sprint không thuộc dự án");
            }
            if (type != null) {
                WorkItem parent = item.getParentId() == null ? null : items.get(item.getParentId());
                if (item.getParentId() != null && parent == null) {
                    report.error("ITEM_FOREIGN_PARENT", code + ": công việc cha không thuộc dự án hoặc đã bị xóa");
                } else {
                    WorkItemType parentType = parent == null ? null : types.get(parent.getTypeId());
                    try {
                        HIERARCHY.validateHierarchyLevels(
                                parentType == null ? null : parentType.getHierarchyLevel(), type.getHierarchyLevel());
                    } catch (InvalidHierarchyException e) {
                        report.error("ITEM_HIERARCHY", code + ": sai phân cấp - " + e.getMessage());
                    }
                }
            }
            if (item.getStartDate() != null && item.getDueDate() != null
                    && item.getDueDate().isBefore(item.getStartDate())) {
                report.error("ITEM_DATE_RANGE", code + ": hạn chót sớm hơn ngày bắt đầu");
            }
            if (project.getEndDate() != null && item.getDueDate() != null
                    && item.getDueDate().isAfter(project.getEndDate())) {
                report.warning("ITEM_AFTER_PROJECT", code + ": hạn chót sau hạn chót dự án");
            }
        }
    }

    private static <T> Map<UUID, T> index(List<T> list, Function<T, UUID> id) {
        Map<UUID, T> map = new HashMap<>();
        for (T t : list) {
            map.put(id.apply(t), t);
        }
        return map;
    }
}
