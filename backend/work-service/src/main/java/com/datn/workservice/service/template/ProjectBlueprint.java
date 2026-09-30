package com.datn.workservice.service.template;

import com.datn.workservice.entity.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Ảnh chụp (snapshot) toàn bộ cấu hình và dữ liệu của một dự án - "bản thiết kế" mà
 * dự án mẫu mang theo. Được nạp một lần bởi {@link ProjectBlueprintLoader}, sau đó
 * dùng chung cho: kiểm tra toàn vẹn, xem trước (preview) và nhân bản.
 * <p>
 * Chỉ chứa dữ liệu thuộc chính dự án (project_id = dự án này). Work item đã xóa mềm
 * bị loại, và link/label map/component map chỉ giữ những bản ghi mà cả hai đầu đều
 * nằm trong tập work item còn sống.
 */
public record ProjectBlueprint(
        Project project,
        List<Status> statuses,
        List<WorkItemType> types,
        List<Workflow> workflows,
        List<WorkflowTransition> transitions,
        List<WorkflowScheme> schemes,
        List<WorkflowSchemeMapping> schemeMappings,
        List<Board> boards,
        List<BoardColumn> columns,
        List<BoardColumnStatus> columnStatuses,
        List<BoardSwimlane> swimlanes,
        List<ProjectRole> roles,
        List<PermissionSchemeEntry> permissionEntries,
        List<Label> labels,
        List<Component> components,
        List<Version> versions,
        List<CustomFieldDefinition> customFields,
        List<AutomationRule> automationRules,
        List<Sprint> sprints,
        List<WorkItem> workItems,
        List<WorkItemLabelMap> labelMaps,
        List<WorkItemComponentMap> componentMaps,
        List<WorkItemLink> links
) {

    /**
     * Mốc gốc của timeline: ngày bắt đầu dự án; nếu trống thì ngày sớm nhất
     * trong các sprint, công việc và version. null nếu mẫu không có mốc thời gian nào.
     */
    public LocalDate anchorDate() {
        if (project.getStartDate() != null) {
            return project.getStartDate();
        }
        return datedPoints().min(LocalDate::compareTo).orElse(null);
    }

    /** Mốc muộn nhất: hạn dự án, kết thúc sprint, hạn công việc, ngày phát hành version. */
    public LocalDate latestDate() {
        return Stream.concat(Stream.ofNullable(project.getEndDate()), datedPoints())
                .max(LocalDate::compareTo)
                .orElse(null);
    }

    /** Độ dài timeline tính bằng ngày; null nếu không xác định. */
    public Long spanDays() {
        LocalDate anchor = anchorDate();
        LocalDate latest = latestDate();
        if (anchor == null || latest == null || latest.isBefore(anchor)) {
            return null;
        }
        return ChronoUnit.DAYS.between(anchor, latest);
    }

    private Stream<LocalDate> datedPoints() {
        Stream<LocalDate> sprintDates = sprints.stream()
                .flatMap(s -> Stream.of(s.getStartDate(), s.getEndDate()))
                .filter(Objects::nonNull)
                .map(d -> d.toLocalDate());
        Stream<LocalDate> itemDates = workItems.stream()
                .flatMap(i -> Stream.of(i.getStartDate(), i.getDueDate()))
                .filter(Objects::nonNull);
        Stream<LocalDate> versionDates = versions.stream()
                .map(Version::getReleaseDate)
                .filter(Objects::nonNull);
        return Stream.of(sprintDates, itemDates, versionDates).flatMap(s -> s);
    }
}
