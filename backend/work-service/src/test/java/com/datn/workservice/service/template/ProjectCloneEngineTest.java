package com.datn.workservice.service.template;

import com.datn.workservice.entity.*;
import com.datn.workservice.enums.SprintState;
import com.datn.workservice.repository.*;
import com.datn.workservice.service.AtomicKeySequenceGenerator;
import com.datn.workservice.service.WorkItemAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Kiểm tra engine nhân bản: mọi khóa ngoại được nối lại sang bản ghi mới, thời gian được dời,
 * tiến độ được đặt lại. Repository là mock "ghi nhớ" - save() gán UUID mới và lưu lại bản ghi.
 */
class ProjectCloneEngineTest {

    private final Map<Class<?>, List<Object>> saved = new HashMap<>();
    private final AtomicInteger codeSeq = new AtomicInteger();

    private AtomicKeySequenceGenerator keyGenerator;
    private WorkItemAuditService auditService;
    private ProjectCloneEngine engine;

    private BlueprintFixtures f;
    private Project target;

    @BeforeEach
    void setUp() {
        keyGenerator = mock(AtomicKeySequenceGenerator.class);
        when(keyGenerator.generateNextCode(anyString()))
                .thenAnswer(inv -> inv.getArgument(0) + "-" + codeSeq.incrementAndGet());
        auditService = mock(WorkItemAuditService.class);

        engine = new ProjectCloneEngine(
                repo(StatusRepository.class), repo(WorkItemTypeRepository.class), repo(WorkflowRepository.class),
                repo(WorkflowTransitionRepository.class), repo(WorkflowSchemeRepository.class),
                repo(WorkflowSchemeMappingRepository.class), repo(BoardRepository.class),
                repo(BoardColumnRepository.class), repo(BoardColumnStatusRepository.class),
                repo(BoardSwimlaneRepository.class), repo(ProjectRoleRepository.class),
                repo(PermissionSchemeEntryRepository.class), repo(LabelRepository.class),
                repo(ComponentRepository.class), repo(VersionRepository.class),
                repo(CustomFieldDefinitionRepository.class), repo(AutomationRuleRepository.class),
                repo(SprintRepository.class), repo(WorkItemRepository.class),
                repo(WorkItemLabelMapRepository.class), repo(WorkItemComponentMapRepository.class),
                repo(WorkItemLinkRepository.class), keyGenerator, auditService);

        f = new BlueprintFixtures();
        target = Project.builder().id(UUID.randomUUID()).name("Web bán hàng").key("SHOP")
                .workspaceId(f.project.getWorkspaceId()).build();
    }

    @Test
    @DisplayName("Sao chép đủ cấu hình và nối lại mọi khóa ngoại sang dự án đích")
    void clonesConfigurationWithRemappedIds() {
        Map<String, Integer> stats = engine.cloneInto(f.blueprint(), target,
                new ProjectCloneEngine.Options(true, true, true, 0), UUID.randomUUID());

        assertThat(stats).containsEntry("statuses", 4).containsEntry("workItemTypes", 3)
                .containsEntry("transitions", 4).containsEntry("sprints", 2).containsEntry("workItems", 3)
                .containsEntry("links", 1).containsEntry("roles", 3);
        verify(keyGenerator).createSequenceForProject("SHOP");

        Set<UUID> newStatusIds = ids(Status.class);
        assertThat(all(Status.class, Status.class)).allMatch(s -> target.getId().equals(s.getProjectId()));
        assertThat(all(WorkflowTransition.class, WorkflowTransition.class)).allSatisfy(t -> {
            assertThat(newStatusIds).contains(t.getToStatusId());
            if (t.getFromStatusId() != null) assertThat(newStatusIds).contains(t.getFromStatusId());
        });
        assertThat(all(BoardColumnStatus.class, BoardColumnStatus.class))
                .allMatch(cs -> newStatusIds.contains(cs.getStatusId()) && ids(BoardColumn.class).contains(cs.getColumnId()));
        assertThat(all(WorkflowSchemeMapping.class, WorkflowSchemeMapping.class))
                .allMatch(m -> ids(WorkItemType.class).contains(m.getWorkItemTypeId())
                        && ids(Workflow.class).contains(m.getWorkflowId()));

        // Tên board/workflow đổi theo dự án mới
        assertThat(all(Board.class, Board.class)).extracting(Board::getName).containsExactly("Web bán hàng Board");

        // UUID trong JSON validator được ánh xạ sang trạng thái mới
        WorkflowTransition approve = all(WorkflowTransition.class, WorkflowTransition.class).stream()
                .filter(t -> t.getName().equals("Approve")).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) approve.getValidatorsConfig().get(0).get("params");
        assertThat(newStatusIds).contains(UUID.fromString((String) params.get("statusId")));
    }

    @Test
    @DisplayName("Công việc: cây cha-con, sprint, link nối lại; tiến độ được đặt lại")
    void clonesWorkItemsAndResetsProgress() {
        UUID actor = UUID.randomUUID();
        engine.cloneInto(f.blueprint(), target, new ProjectCloneEngine.Options(true, true, true, 0), actor);

        List<WorkItem> items = all(WorkItem.class, WorkItem.class);
        assertThat(items).extracting(WorkItem::getCode).containsExactly("SHOP-1", "SHOP-2", "SHOP-3");
        WorkItem epic = items.get(0), task = items.get(1), subtask = items.get(2);

        assertThat(epic.getParentId()).isNull();
        assertThat(task.getParentId()).isEqualTo(epic.getId());
        assertThat(subtask.getParentId()).isEqualTo(task.getId());
        assertThat(ids(Sprint.class)).contains(task.getSprintId());

        // Mọi công việc về trạng thái khởi tạo của workflow (đích của transition Create = "To Do")
        UUID newTodo = all(Status.class, Status.class).stream()
                .filter(s -> s.getName().equals("To Do")).findFirst().orElseThrow().getId();
        assertThat(items).allMatch(i -> newTodo.equals(i.getStatusId()));

        assertThat(task.getAssigneeId()).isNull();
        assertThat(task.getReporterId()).isEqualTo(actor);
        assertThat(task.getResolution()).isNull();
        assertThat(task.getTimeSpentSeconds()).isZero();
        assertThat(task.getRemainingEstimateSeconds()).isEqualTo(7200L);
        assertThat(ids(Component.class)).contains(task.getComponentId());
        assertThat(ids(Version.class)).contains(task.getFixVersionId());

        WorkItemLink link = all(WorkItemLink.class, WorkItemLink.class).get(0);
        assertThat(link.getSourceItemId()).isEqualTo(task.getId());
        assertThat(link.getTargetItemId()).isEqualTo(subtask.getId());
        assertThat(all(WorkItemLabelMap.class, WorkItemLabelMap.class))
                .singleElement().satisfies(m -> assertThat(m.getWorkItemId()).isEqualTo(task.getId()));

        verify(auditService, times(3)).recordCustomEvent(any(), eq("SUMMARY"), isNull(), anyString(), eq(actor));
    }

    @Test
    @DisplayName("Thời gian: sprint/công việc/version dời đúng offset, sprint về FUTURE, version chưa phát hành")
    void shiftsTimelineAndResetsSprints() {
        engine.cloneInto(f.blueprint(), target, new ProjectCloneEngine.Options(true, true, true, 100), UUID.randomUUID());

        List<Sprint> sprints = all(Sprint.class, Sprint.class);
        assertThat(sprints).allMatch(s -> s.getState() == SprintState.FUTURE && s.getCompletedDate() == null);
        assertThat(sprints.get(0).getStartDate()).isEqualTo(f.sprint1.getStartDate().plusDays(100));
        assertThat(sprints.get(1).getEndDate()).isEqualTo(f.sprint2.getEndDate().plusDays(100));

        WorkItem task = all(WorkItem.class, WorkItem.class).get(1);
        assertThat(task.getDueDate()).isEqualTo(f.task.getDueDate().plusDays(100));

        Version v = all(Version.class, Version.class).get(0);
        assertThat(v.getReleaseDate()).isEqualTo(f.version.getReleaseDate().plusDays(100));
        assertThat(v.getIsReleased()).isFalse();
    }

    @Test
    @DisplayName("Bỏ sprint: công việc về backlog; bỏ công việc: không tạo work item")
    void optionalParts() {
        engine.cloneInto(f.blueprint(), target, new ProjectCloneEngine.Options(false, true, false, 0), UUID.randomUUID());
        assertThat(all(Sprint.class, Sprint.class)).isEmpty();
        assertThat(all(Version.class, Version.class)).isEmpty();
        assertThat(all(WorkItem.class, WorkItem.class)).allMatch(i -> i.getSprintId() == null && i.getFixVersionId() == null);

        saved.clear();
        engine.cloneInto(f.blueprint(), target, new ProjectCloneEngine.Options(true, false, true, 0), UUID.randomUUID());
        assertThat(all(WorkItem.class, WorkItem.class)).isEmpty();
        assertThat(all(Sprint.class, Sprint.class)).hasSize(2);
    }

    @Test
    @DisplayName("Áp dụng tùy chỉnh: đổi tên status/type/sprint/task và loại bỏ sprint/task (kèm cascade subtask)")
    void appliesCustomizationsCorrectly() {
        UUID actor = UUID.randomUUID();

        var customizations = com.datn.workservice.dto.request.TemplateCustomizationsRequest.builder()
                .renamedStatuses(Map.of(f.todo.getId(), "Cần làm", f.done.getId(), "Hoàn tất"))
                .renamedTypes(Map.of(f.epicType.getId(), "Sáng kiến"))
                .excludedSprintIds(List.of(f.sprint2.getId()))
                .renamedSprints(Map.of(f.sprint1.getId(), "Sprint Khởi động"))
                .excludedWorkItemIds(List.of(f.task.getId())) // task bị loại trừ -> subtask con cũng phải tự động bị loại trừ
                .renamedWorkItems(Map.of(f.epic.getId(), "Epic Tùy chỉnh"))
                .build();

        var options = new ProjectCloneEngine.Options(true, true, true, 0, customizations);
        Map<String, Integer> stats = engine.cloneInto(f.blueprint(), target, options, actor);

        // Status: 4 cái, tên To Do -> Cần làm, Done -> Hoàn tất
        assertThat(all(Status.class, Status.class)).extracting(Status::getName)
                .contains("Cần làm", "Hoàn tất", "In Progress", "In Review");

        // Type: Epic -> Sáng kiến
        assertThat(all(WorkItemType.class, WorkItemType.class)).extracting(WorkItemType::getName)
                .contains("Sáng kiến", "Task", "Subtask");

        // Sprint: sprint2 bị loại trừ -> chỉ còn 1 sprint, tên là "Sprint Khởi động"
        assertThat(stats).containsEntry("sprints", 1);
        assertThat(all(Sprint.class, Sprint.class)).extracting(Sprint::getName)
                .containsExactly("Sprint Khởi động");

        // WorkItem: task bị loại trừ -> subtask con của nó cũng bị loại trừ -> chỉ còn lại epic!
        assertThat(stats).containsEntry("workItems", 1);
        assertThat(all(WorkItem.class, WorkItem.class)).extracting(WorkItem::getSummary)
                .containsExactly("Epic Tùy chỉnh");
    }

    // ─── Mock repository ghi nhớ ────────────────────────────────────────────

    private <R> R repo(Class<R> type) {
        Answer<Object> answer = inv -> {
            String name = inv.getMethod().getName();
            if (name.equals("save")) {
                return remember(inv.getArgument(0));
            }
            if (name.equals("saveAll")) {
                List<Object> out = new ArrayList<>();
                for (Object o : (Iterable<?>) inv.getArgument(0)) out.add(remember(o));
                return out;
            }
            return RETURNS_DEFAULTS.answer(inv);
        };
        return mock(type, answer);
    }

    private Object remember(Object entity) throws Exception {
        try {
            Method getId = entity.getClass().getMethod("getId");
            if (getId.invoke(entity) == null) {
                entity.getClass().getMethod("setId", UUID.class).invoke(entity, UUID.randomUUID());
            }
        } catch (NoSuchMethodException ignored) {
            // bảng nối không có id riêng
        }
        saved.computeIfAbsent(entity.getClass(), k -> new ArrayList<>());
        List<Object> list = saved.get(entity.getClass());
        if (list.stream().noneMatch(o -> o == entity)) list.add(entity);
        return entity;
    }

    private <T> List<T> all(Class<?> key, Class<T> type) {
        return saved.getOrDefault(key, List.of()).stream().map(type::cast).toList();
    }

    private Set<UUID> ids(Class<?> type) {
        Set<UUID> ids = new HashSet<>();
        for (Object o : saved.getOrDefault(type, List.of())) {
            try {
                ids.add((UUID) o.getClass().getMethod("getId").invoke(o));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return ids;
    }
}
