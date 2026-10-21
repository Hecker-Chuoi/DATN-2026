package com.datn.workservice.service.template;

import com.datn.workservice.entity.*;
import com.datn.workservice.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Nạp {@link ProjectBlueprint} của một dự án từ CSDL.
 * Thứ tự các danh sách được cố định (theo thứ tự tạo / thứ tự hiển thị) để việc nhân bản
 * cho ra kết quả ổn định giữa các lần chạy.
 */
@Component
@RequiredArgsConstructor
public class ProjectBlueprintLoader {

    private final StatusRepository statusRepository;
    private final WorkItemTypeRepository workItemTypeRepository;
    private final WorkflowRepository workflowRepository;
    private final WorkflowTransitionRepository workflowTransitionRepository;
    private final WorkflowSchemeRepository workflowSchemeRepository;
    private final WorkflowSchemeMappingRepository workflowSchemeMappingRepository;
    private final BoardRepository boardRepository;
    private final BoardColumnRepository boardColumnRepository;
    private final BoardColumnStatusRepository boardColumnStatusRepository;
    private final BoardSwimlaneRepository boardSwimlaneRepository;
    private final ProjectRoleRepository projectRoleRepository;
    private final PermissionSchemeEntryRepository permissionSchemeEntryRepository;
    private final LabelRepository labelRepository;
    private final ComponentRepository componentRepository;
    private final VersionRepository versionRepository;
    private final CustomFieldDefinitionRepository customFieldDefinitionRepository;
    private final AutomationRuleRepository automationRuleRepository;
    private final SprintRepository sprintRepository;
    private final WorkItemRepository workItemRepository;
    private final WorkItemLabelMapRepository workItemLabelMapRepository;
    private final WorkItemComponentMapRepository workItemComponentMapRepository;
    private final WorkItemLinkRepository workItemLinkRepository;

    public ProjectBlueprint load(Project project) {
        UUID projectId = project.getId();

        List<Status> statuses = sorted(statusRepository.findByProjectId(projectId),
                Comparator.comparing(Status::getCreatedAt, nullsLast()).thenComparing(Status::getName));
        List<WorkItemType> types = sorted(workItemTypeRepository.findByProjectId(projectId),
                Comparator.comparing(WorkItemType::getHierarchyLevel, Comparator.reverseOrder())
                        .thenComparing(WorkItemType::getCreatedAt, nullsLast()));
        List<Workflow> workflows = sorted(workflowRepository.findByProjectId(projectId),
                Comparator.comparing(Workflow::getCreatedAt, nullsLast()));
        List<WorkflowTransition> transitions = workflows.stream()
                .flatMap(w -> workflowTransitionRepository.findByWorkflowId(w.getId()).stream())
                .toList();
        List<WorkflowScheme> schemes = workflowSchemeRepository.findByProjectId(projectId);
        List<WorkflowSchemeMapping> schemeMappings = schemes.stream()
                .flatMap(s -> workflowSchemeMappingRepository.findBySchemeId(s.getId()).stream())
                .toList();

        List<Board> boards = sorted(boardRepository.findByProjectId(projectId),
                Comparator.comparing(Board::getCreatedAt, nullsLast()));
        List<BoardColumn> columns = boards.stream()
                .flatMap(b -> boardColumnRepository.findByBoardIdOrderByColumnOrderAsc(b.getId()).stream())
                .toList();
        List<BoardColumnStatus> columnStatuses = columns.isEmpty() ? List.of()
                : boardColumnStatusRepository.findByColumnIdIn(columns.stream().map(BoardColumn::getId).toList());
        List<BoardSwimlane> swimlanes = boards.stream()
                .flatMap(b -> boardSwimlaneRepository.findByBoardIdOrderBySwimlaneOrderAsc(b.getId()).stream())
                .toList();

        List<ProjectRole> roles = sorted(projectRoleRepository.findByProjectId(projectId),
                Comparator.comparing(ProjectRole::getCreatedAt, nullsLast()));
        List<PermissionSchemeEntry> permissionEntries = permissionSchemeEntryRepository.findByProjectId(projectId);

        List<Sprint> sprints = sorted(sprintRepository.findByProjectId(projectId),
                Comparator.comparing(Sprint::getStartDate, nullsLast())
                        .thenComparing(Sprint::getCreatedAt, nullsLast()));

        List<WorkItem> workItems = sorted(
                workItemRepository.findByProjectId(projectId).stream()
                        .filter(i -> i.getDeletedAt() == null)
                        .toList(),
                Comparator.comparingLong(ProjectBlueprintLoader::codeNumber)
                        .thenComparing(WorkItem::getCreatedAt, nullsLast()));
        Set<UUID> itemIds = workItems.stream().map(WorkItem::getId).collect(Collectors.toSet());

        List<WorkItemLabelMap> labelMaps = itemIds.isEmpty() ? List.of()
                : workItemLabelMapRepository.findByWorkItemIdIn(itemIds);
        List<WorkItemComponentMap> componentMaps = itemIds.isEmpty() ? List.of()
                : workItemComponentMapRepository.findByWorkItemIdIn(itemIds);
        List<WorkItemLink> links = itemIds.isEmpty() ? List.of()
                : workItemLinkRepository.findBySourceItemIdIn(itemIds).stream()
                        .filter(l -> itemIds.contains(l.getTargetItemId()))
                        .toList();

        return new ProjectBlueprint(
                project, statuses, types, workflows, transitions, schemes, schemeMappings,
                boards, columns, columnStatuses, swimlanes, roles, permissionEntries,
                labelRepository.findByProjectId(projectId),
                componentRepository.findByProjectId(projectId),
                versionRepository.findByProjectId(projectId),
                customFieldDefinitionRepository.findByProjectId(projectId),
                automationRuleRepository.findByProjectId(projectId),
                sprints, workItems, labelMaps, componentMaps, links
        );
    }

    /** Số thứ tự trong mã công việc ("WEB-12" -> 12) để giữ nguyên thứ tự đánh số khi nhân bản. */
    static long codeNumber(WorkItem item) {
        String code = item.getCode();
        if (code == null) return Long.MAX_VALUE;
        int dash = code.lastIndexOf('-');
        try {
            return Long.parseLong(code.substring(dash + 1));
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
    }

    private static <T extends Comparable<? super T>> Comparator<T> nullsLast() {
        return Comparator.nullsLast(Comparator.naturalOrder());
    }

    private static <T> List<T> sorted(List<T> list, Comparator<T> comparator) {
        return list.stream().sorted(comparator).toList();
    }
}
