package com.datn.workservice.service.template;

import com.datn.workservice.entity.*;
import com.datn.workservice.enums.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Dựng một dự án mẫu "Phần mềm Scrum" hợp lệ trong bộ nhớ để test:
 * 4 trạng thái (To Do, In Progress, In Review, Done), 3 loại (Epic/Task/Subtask), 1 workflow,
 * 1 board, 3 vai trò, 2 sprint 14 ngày, 1 epic → 1 task → 1 subtask, 1 link.
 */
final class BlueprintFixtures {

    static final LocalDate ANCHOR = LocalDate.of(2026, 1, 5);

    final Project project;
    final Status todo, inProgress, review, done;
    final WorkItemType epicType, taskType, subtaskType;
    final Workflow workflow;
    final List<WorkflowTransition> transitions = new ArrayList<>();
    final WorkflowScheme scheme;
    final List<WorkflowSchemeMapping> mappings = new ArrayList<>();
    final Board board;
    final List<BoardColumn> columns = new ArrayList<>();
    final List<BoardColumnStatus> columnStatuses = new ArrayList<>();
    final List<ProjectRole> roles = new ArrayList<>();
    final List<PermissionSchemeEntry> permissions = new ArrayList<>();
    final Label label;
    final Component component;
    final Version version;
    final Sprint sprint1, sprint2;
    final WorkItem epic, task, subtask;
    final WorkItemLink link;

    BlueprintFixtures() {
        UUID pid = UUID.randomUUID();
        project = Project.builder().id(pid).workspaceId(UUID.randomUUID()).name("Mẫu Phần mềm").key("TPL")
                .leadUserId(UUID.randomUUID()).createdBy(UUID.randomUUID())
                .projectType(ProjectType.SOFTWARE_SCRUM).isTemplate(true)
                .templateCategory(TemplateCategory.SOFTWARE)
                .startDate(ANCHOR).endDate(ANCHOR.plusDays(60)).build();

        todo = status(pid, "To Do", StatusCategory.TODO);
        inProgress = status(pid, "In Progress", StatusCategory.IN_PROGRESS);
        review = status(pid, "In Review", StatusCategory.IN_PROGRESS);
        done = status(pid, "Done", StatusCategory.DONE);

        epicType = type(pid, "Epic", "EPIC", 1);
        taskType = type(pid, "Task", "TASK", 0);
        subtaskType = type(pid, "Subtask", "SUBTASK", -1);

        workflow = Workflow.builder().id(UUID.randomUUID()).projectId(pid).name("Mẫu Phần mềm Workflow").isDefault(true).build();
        transitions.add(transition(null, todo, "Create"));
        transitions.add(transition(todo, inProgress, "Start"));
        transitions.add(transition(inProgress, review, "Review"));
        transitions.add(transition(review, done, "Approve"));
        transitions.get(3).setValidatorsConfig(List.of(new java.util.HashMap<>(java.util.Map.of(
                "type", "FIELD_REQUIRED", "params", java.util.Map.of("statusId", review.getId().toString())))));

        scheme = WorkflowScheme.builder().id(UUID.randomUUID()).projectId(pid).name("Mẫu Phần mềm Scheme").build();
        for (WorkItemType t : List.of(epicType, taskType, subtaskType)) {
            mappings.add(WorkflowSchemeMapping.builder().id(UUID.randomUUID()).schemeId(scheme.getId())
                    .workItemTypeId(t.getId()).workflowId(workflow.getId()).build());
        }

        board = Board.builder().id(UUID.randomUUID()).projectId(pid).name("Mẫu Phần mềm Board").type(BoardType.SCRUM).build();
        int order = 0;
        for (Status s : List.of(todo, inProgress, review, done)) {
            BoardColumn c = BoardColumn.builder().id(UUID.randomUUID()).boardId(board.getId())
                    .name(s.getName().toUpperCase()).columnOrder(order++).build();
            columns.add(c);
            columnStatuses.add(BoardColumnStatus.builder().columnId(c.getId()).statusId(s.getId()).build());
        }

        for (String name : List.of("ADMINISTRATOR", "MEMBER", "VIEWER")) {
            ProjectRole r = ProjectRole.builder().id(UUID.randomUUID()).projectId(pid).name(name).build();
            roles.add(r);
            permissions.add(PermissionSchemeEntry.builder().id(UUID.randomUUID()).projectId(pid)
                    .roleId(r.getId()).permissionKey(PermissionKey.PROJECT_VIEW.name()).build());
        }

        label = Label.builder().id(UUID.randomUUID()).projectId(pid).name("frontend").build();
        component = Component.builder().id(UUID.randomUUID()).projectId(pid).name("API").leadUserId(UUID.randomUUID()).build();
        version = Version.builder().id(UUID.randomUUID()).projectId(pid).name("v1.0")
                .releaseDate(ANCHOR.plusDays(28)).isReleased(true).build();

        sprint1 = sprint(pid, "Sprint 1", 0, SprintState.CLOSED);
        sprint2 = sprint(pid, "Sprint 2", 14, SprintState.FUTURE);

        epic = item(pid, "TPL-1", epicType, done, null, null);
        task = item(pid, "TPL-2", taskType, review, epic.getId(), sprint1.getId());
        task.setStartDate(ANCHOR.plusDays(1));
        task.setDueDate(ANCHOR.plusDays(10));
        task.setStoryPoints(new BigDecimal("5"));
        task.setAssigneeId(UUID.randomUUID());
        task.setTimeSpentSeconds(3600L);
        task.setOriginalEstimateSeconds(7200L);
        task.setComponentId(component.getId());
        task.setFixVersionId(version.getId());
        task.setResolution(WorkItemResolution.values()[0]);
        subtask = item(pid, "TPL-3", subtaskType, todo, task.getId(), sprint1.getId());

        link = WorkItemLink.builder().id(UUID.randomUUID()).sourceItemId(task.getId()).targetItemId(subtask.getId())
                .linkType(LinkType.BLOCKS).createdBy(UUID.randomUUID()).build();
    }

    ProjectBlueprint blueprint() {
        return new ProjectBlueprint(project,
                new ArrayList<>(List.of(todo, inProgress, review, done)),
                new ArrayList<>(List.of(epicType, taskType, subtaskType)),
                new ArrayList<>(List.of(workflow)), transitions,
                new ArrayList<>(List.of(scheme)), mappings,
                new ArrayList<>(List.of(board)), columns, columnStatuses, new ArrayList<>(),
                roles, permissions,
                new ArrayList<>(List.of(label)), new ArrayList<>(List.of(component)), new ArrayList<>(List.of(version)),
                new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(List.of(sprint1, sprint2)),
                new ArrayList<>(List.of(epic, task, subtask)),
                new ArrayList<>(List.of(WorkItemLabelMap.builder().workItemId(task.getId()).labelId(label.getId()).build())),
                new ArrayList<>(),
                new ArrayList<>(List.of(link)));
    }

    private static Status status(UUID pid, String name, StatusCategory category) {
        return Status.builder().id(UUID.randomUUID()).projectId(pid).name(name).category(category).build();
    }

    private static WorkItemType type(UUID pid, String name, String code, int level) {
        return WorkItemType.builder().id(UUID.randomUUID()).projectId(pid).name(name).code(code)
                .hierarchyLevel(level).isSubtask(level == -1).isSystemDefault(true).build();
    }

    private WorkflowTransition transition(Status from, Status to, String name) {
        return WorkflowTransition.builder().id(UUID.randomUUID()).workflowId(workflow.getId())
                .fromStatusId(from == null ? null : from.getId()).toStatusId(to.getId()).name(name).build();
    }

    private static Sprint sprint(UUID pid, String name, int startOffset, SprintState state) {
        LocalDateTime start = ANCHOR.plusDays(startOffset).atTime(9, 0);
        return Sprint.builder().id(UUID.randomUUID()).projectId(pid).name(name).state(state)
                .startDate(start).endDate(start.plusDays(14))
                .completedDate(state == SprintState.CLOSED ? start.plusDays(14) : null).build();
    }

    private static WorkItem item(UUID pid, String code, WorkItemType type, Status status, UUID parentId, UUID sprintId) {
        return WorkItem.builder().id(UUID.randomUUID()).projectId(pid).code(code).summary("Việc " + code)
                .typeId(type.getId()).statusId(status.getId()).parentId(parentId).sprintId(sprintId)
                .priority(WorkItemPriority.MEDIUM).reporterId(UUID.randomUUID()).rank("0|i0000" + code.charAt(4) + ":")
                .build();
    }
}
