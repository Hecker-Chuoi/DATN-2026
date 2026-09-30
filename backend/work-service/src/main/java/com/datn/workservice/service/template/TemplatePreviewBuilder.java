package com.datn.workservice.service.template;

import com.datn.workservice.dto.response.TemplatePreviewResponse;
import com.datn.workservice.dto.response.TemplatePreviewResponse.*;
import com.datn.workservice.dto.response.TemplateSummaryResponse;
import com.datn.workservice.entity.*;
import com.datn.workservice.enums.StatusCategory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Chuyển {@link ProjectBlueprint} thành dữ liệu hiển thị (thẻ mẫu và modal xem trước). Hàm thuần.
 */
public final class TemplatePreviewBuilder {

    /** Giới hạn số node cây công việc trả về trong preview để modal không quá nặng. */
    static final int MAX_TREE_NODES = 500;

    private TemplatePreviewBuilder() {
    }

    public static TemplateSummaryResponse summary(ProjectBlueprint bp, long usageCount) {
        Project p = bp.project();
        return TemplateSummaryResponse.builder()
                .id(p.getId())
                .workspaceId(p.getWorkspaceId())
                .teamId(p.getTeamId())
                .name(p.getName())
                .key(p.getKey())
                .description(p.getDescription())
                .projectType(p.getProjectType())
                .templateCategory(p.getTemplateCategory())
                .updatedAt(p.getUpdatedAt())
                .statuses(bp.statuses().stream()
                        .sorted(statusOrder())
                        .map(s -> TemplateSummaryResponse.StatusChip.builder()
                                .name(s.getName()).category(s.getCategory()).colorHex(s.getColorHex()).build())
                        .toList())
                .workItemTypeCount(bp.types().size())
                .sprintCount(bp.sprints().size())
                .workItemCount(bp.workItems().size())
                .epicCount(countEpics(bp))
                .timelineSpanDays(bp.spanDays())
                .usageCount(usageCount)
                .build();
    }

    public static TemplatePreviewResponse preview(ProjectBlueprint bp, long usageCount) {
        Project p = bp.project();
        LocalDate anchor = bp.anchorDate();

        Map<UUID, Status> statuses = index(bp.statuses(), Status::getId);
        Map<UUID, WorkItemType> types = index(bp.types(), WorkItemType::getId);
        Map<UUID, Sprint> sprints = index(bp.sprints(), Sprint::getId);

        return TemplatePreviewResponse.builder()
                .id(p.getId())
                .workspaceId(p.getWorkspaceId())
                .teamId(p.getTeamId())
                .name(p.getName())
                .key(p.getKey())
                .description(p.getDescription())
                .projectType(p.getProjectType())
                .templateCategory(p.getTemplateCategory())
                .usageCount(usageCount)
                .timeline(Timeline.builder()
                        .anchorDate(anchor)
                        .templateEndDate(bp.latestDate())
                        .spanDays(bp.spanDays())
                        .build())
                .stats(stats(bp))
                .statuses(bp.statuses().stream().sorted(statusOrder())
                        .map(s -> StatusItem.builder().id(s.getId()).name(s.getName()).category(s.getCategory())
                                .colorHex(s.getColorHex()).description(s.getDescription()).build())
                        .toList())
                .workItemTypes(bp.types().stream()
                        .map(t -> TypeItem.builder().id(t.getId()).name(t.getName()).code(t.getCode())
                                .iconName(t.getIconName()).colorHex(t.getColorHex())
                                .hierarchyLevel(t.getHierarchyLevel()).isSubtask(t.getIsSubtask()).build())
                        .toList())
                .workflows(workflows(bp, statuses, types))
                .boards(boards(bp, statuses))
                .sprints(bp.sprints().stream().map(s -> sprintItem(s, anchor, bp)).toList())
                .workItems(tree(bp, types, sprints, anchor))
                .workItemsTruncated(bp.workItems().size() > MAX_TREE_NODES)
                .labels(bp.labels().stream()
                        .map(l -> NamedItem.builder().name(l.getName()).colorHex(l.getColorHex()).build())
                        .toList())
                .components(bp.components().stream()
                        .map(c -> NamedItem.builder().name(c.getName()).description(c.getDescription()).build())
                        .toList())
                .versions(bp.versions().stream()
                        .map(v -> VersionItem.builder().name(v.getName())
                                .releaseOffsetDays(offset(anchor, v.getReleaseDate())).build())
                        .toList())
                .customFields(bp.customFields().stream()
                        .map(f -> CustomFieldItem.builder().name(f.getName()).fieldKey(f.getFieldKey())
                                .fieldType(f.getFieldType()).build())
                        .toList())
                .roles(roles(bp))
                .integrity(TemplateIntegrityValidator.validate(bp))
                .build();
    }

    private static Stats stats(ProjectBlueprint bp) {
        BigDecimal points = bp.workItems().stream()
                .map(WorkItem::getStoryPoints)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Stats.builder()
                .statusCount(bp.statuses().size())
                .workItemTypeCount(bp.types().size())
                .workflowCount(bp.workflows().size())
                .transitionCount(bp.transitions().size())
                .boardCount(bp.boards().size())
                .sprintCount(bp.sprints().size())
                .workItemCount(bp.workItems().size())
                .epicCount(countEpics(bp))
                .backlogItemCount(bp.workItems().stream().filter(i -> i.getSprintId() == null).count())
                .linkCount(bp.links().size())
                .totalStoryPoints(points)
                .build();
    }

    private static List<WorkflowItem> workflows(ProjectBlueprint bp, Map<UUID, Status> statuses,
                                                Map<UUID, WorkItemType> types) {
        return bp.workflows().stream().map(w -> WorkflowItem.builder()
                .id(w.getId())
                .name(w.getName())
                .isDefault(w.getIsDefault())
                .appliesToTypes(bp.schemeMappings().stream()
                        .filter(m -> w.getId().equals(m.getWorkflowId()))
                        .map(m -> types.get(m.getWorkItemTypeId()))
                        .filter(Objects::nonNull)
                        .map(WorkItemType::getName)
                        .toList())
                .transitions(bp.transitions().stream()
                        .filter(t -> w.getId().equals(t.getWorkflowId()))
                        .map(t -> TransitionItem.builder()
                                .name(t.getName())
                                .fromStatus(statusName(statuses, t.getFromStatusId()))
                                .toStatus(statusName(statuses, t.getToStatusId()))
                                .build())
                        .toList())
                .build()).toList();
    }

    private static List<BoardItem> boards(ProjectBlueprint bp, Map<UUID, Status> statuses) {
        return bp.boards().stream().map(b -> BoardItem.builder()
                .name(b.getName())
                .type(b.getType())
                .columns(bp.columns().stream()
                        .filter(c -> b.getId().equals(c.getBoardId()))
                        .map(c -> ColumnItem.builder()
                                .name(c.getName())
                                .order(c.getColumnOrder())
                                .minWipLimit(c.getMinWipLimit())
                                .maxWipLimit(c.getMaxWipLimit())
                                .statuses(bp.columnStatuses().stream()
                                        .filter(cs -> c.getId().equals(cs.getColumnId()))
                                        .map(cs -> statusName(statuses, cs.getStatusId()))
                                        .toList())
                                .build())
                        .toList())
                .swimlanes(bp.swimlanes().stream()
                        .filter(s -> b.getId().equals(s.getBoardId()))
                        .map(s -> SwimlaneItem.builder().name(s.getName()).strategy(s.getStrategy()).build())
                        .toList())
                .build()).toList();
    }

    private static SprintItem sprintItem(Sprint s, LocalDate anchor, ProjectBlueprint bp) {
        List<WorkItem> items = bp.workItems().stream().filter(i -> s.getId().equals(i.getSprintId())).toList();
        Long duration = s.getStartDate() != null && s.getEndDate() != null
                ? ChronoUnit.DAYS.between(s.getStartDate().toLocalDate(), s.getEndDate().toLocalDate())
                : null;
        return SprintItem.builder()
                .id(s.getId())
                .name(s.getName())
                .goal(s.getGoal())
                .startOffsetDays(s.getStartDate() == null ? null : offset(anchor, s.getStartDate().toLocalDate()))
                .durationDays(duration)
                .itemCount(items.size())
                .storyPoints(items.stream().map(WorkItem::getStoryPoints).filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .build();
    }

    /** Cây Epic → Story/Task/Bug → Subtask, anh em sắp theo LexoRank. */
    private static List<TreeNode> tree(ProjectBlueprint bp, Map<UUID, WorkItemType> types,
                                       Map<UUID, Sprint> sprints, LocalDate anchor) {
        List<WorkItem> items = bp.workItems().stream()
                .limit(MAX_TREE_NODES)
                .sorted(Comparator.comparing(WorkItem::getRank, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        Map<UUID, TreeNode> nodes = new LinkedHashMap<>();
        for (WorkItem i : items) {
            WorkItemType type = types.get(i.getTypeId());
            Sprint sprint = i.getSprintId() == null ? null : sprints.get(i.getSprintId());
            nodes.put(i.getId(), TreeNode.builder()
                    .id(i.getId())
                    .code(i.getCode())
                    .summary(i.getSummary())
                    .typeName(type == null ? null : type.getName())
                    .typeIcon(type == null ? null : type.getIconName())
                    .typeColor(type == null ? null : type.getColorHex())
                    .hierarchyLevel(type == null ? null : type.getHierarchyLevel())
                    .priority(i.getPriority())
                    .storyPoints(i.getStoryPoints())
                    .sprintName(sprint == null ? null : sprint.getName())
                    .dueOffsetDays(offset(anchor, i.getDueDate()))
                    .build());
        }
        List<TreeNode> roots = new ArrayList<>();
        for (WorkItem i : items) {
            TreeNode node = nodes.get(i.getId());
            TreeNode parent = i.getParentId() == null ? null : nodes.get(i.getParentId());
            if (parent != null) {
                parent.getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        // Epic lên đầu, sau đó các việc độc lập
        roots.sort(Comparator.comparing(n -> n.getHierarchyLevel() == null ? 0 : -n.getHierarchyLevel()));
        return roots;
    }

    private static List<RoleItem> roles(ProjectBlueprint bp) {
        Map<UUID, List<String>> keysByRole = bp.permissionEntries().stream()
                .collect(Collectors.groupingBy(PermissionSchemeEntry::getRoleId, LinkedHashMap::new,
                        Collectors.mapping(PermissionSchemeEntry::getPermissionKey, Collectors.toList())));
        return bp.roles().stream()
                .map(r -> RoleItem.builder()
                        .name(r.getName())
                        .description(r.getDescription())
                        .permissions(keysByRole.getOrDefault(r.getId(), List.of()).stream().sorted().toList())
                        .build())
                .toList();
    }

    private static long countEpics(ProjectBlueprint bp) {
        Set<UUID> epicTypes = bp.types().stream()
                .filter(t -> Integer.valueOf(1).equals(t.getHierarchyLevel()))
                .map(WorkItemType::getId)
                .collect(Collectors.toSet());
        return bp.workItems().stream().filter(i -> epicTypes.contains(i.getTypeId())).count();
    }

    /** TODO → IN_PROGRESS → DONE, giữ thứ tự tạo trong cùng nhóm. */
    private static Comparator<Status> statusOrder() {
        return Comparator.comparing((Status s) -> s.getCategory() == null ? StatusCategory.TODO : s.getCategory());
    }

    private static String statusName(Map<UUID, Status> statuses, UUID id) {
        if (id == null) return null;
        Status s = statuses.get(id);
        return s == null ? "(ngoài dự án)" : s.getName();
    }

    private static Long offset(LocalDate anchor, LocalDate date) {
        return anchor == null || date == null ? null : ChronoUnit.DAYS.between(anchor, date);
    }

    private static <T> Map<UUID, T> index(List<T> list, Function<T, UUID> id) {
        Map<UUID, T> map = new HashMap<>();
        list.forEach(t -> map.put(id.apply(t), t));
        return map;
    }
}
