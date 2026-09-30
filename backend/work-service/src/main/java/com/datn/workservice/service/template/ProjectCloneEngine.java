package com.datn.workservice.service.template;

import com.datn.workservice.entity.*;
import com.datn.workservice.enums.SprintState;
import com.datn.workservice.enums.StatusCategory;
import com.datn.workservice.repository.*;
import com.datn.workservice.service.AtomicKeySequenceGenerator;
import com.datn.workservice.service.WorkItemAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Engine nhân bản sâu một dự án nguồn ({@link ProjectBlueprint}) sang một dự án đích đã được lưu.
 * <p>
 * Nguyên tắc:
 * <ol>
 *   <li><b>Ánh xạ ID</b>: mọi bản ghi được tạo mới; một bảng {@code idMap} (id nguồn → id đích) dùng chung
 *       cho mọi loại thực thể để nối lại khóa ngoại (status, type, workflow, sprint, parent, component...).
 *       Id không có trong bảng (ví dụ trạng thái/loại toàn cục project_id = NULL) được giữ nguyên.</li>
 *   <li><b>JSON</b>: cấu hình JSONB (condition/validator/post-function, automation, custom field,
 *       custom_fields của work item) được duyệt đệ quy và thay mọi chuỗi UUID có trong {@code idMap}.</li>
 *   <li><b>Thời gian</b>: mọi mốc ngày (sprint, work item, version) được cộng {@code dateOffsetDays},
 *       nên khoảng cách tương đối giữa các mốc được giữ nguyên.</li>
 *   <li><b>Tiến độ được đặt lại</b>: sprint về FUTURE, công việc về trạng thái khởi tạo của workflow,
 *       xóa resolution/timeSpent/assignee; reporter là người thực hiện. Bình luận, tệp đính kèm và
 *       changelog của nguồn không được sao chép.</li>
 * </ol>
 * Engine không tự mở transaction - service gọi nó phải chạy trong {@code @Transactional}.
 */
@Slf4j
@org.springframework.stereotype.Component
@RequiredArgsConstructor
public class ProjectCloneEngine {

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
    private final AtomicKeySequenceGenerator atomicKeySequenceGenerator;
    private final WorkItemAuditService workItemAuditService;

    /**
     * Tùy chọn nhân bản.
     *
     * @param includeSprints   sao chép sprint (luôn ở trạng thái FUTURE)
     * @param includeWorkItems sao chép công việc (kèm cây cha-con, nhãn, component, liên kết)
     * @param includeVersions  sao chép version (ngày phát hành được dời, is_released = false)
     * @param dateOffsetDays   số ngày cộng vào mọi mốc thời gian
     */
    public record Options(boolean includeSprints, boolean includeWorkItems, boolean includeVersions,
                          long dateOffsetDays) {
    }

    /**
     * Nhân bản {@code source} vào {@code target} (target đã có id, chưa có ecosystem).
     *
     * @return số bản ghi đã tạo theo từng loại
     */
    public Map<String, Integer> cloneInto(ProjectBlueprint source, Project target, Options options, UUID actorId) {
        Context ctx = new Context(source, target, options, actorId);

        atomicKeySequenceGenerator.createSequenceForProject(target.getKey());

        cloneStatuses(ctx);
        cloneTypes(ctx);
        cloneWorkflows(ctx);
        cloneSchemes(ctx);
        cloneBoards(ctx);
        cloneRoles(ctx);
        cloneLabels(ctx);
        cloneComponents(ctx);
        if (options.includeVersions()) {
            cloneVersions(ctx);
        }
        cloneCustomFields(ctx);
        cloneAutomationRules(ctx);
        if (options.includeSprints()) {
            cloneSprints(ctx);
        }
        if (options.includeWorkItems()) {
            cloneWorkItems(ctx);
        }

        log.info("Cloned project {} -> {} ({}): {}", source.project().getKey(), target.getKey(), target.getId(), ctx.stats);
        return ctx.stats;
    }

    // ─── CẤU HÌNH ──────────────────────────────────────────────────────────────

    private void cloneStatuses(Context ctx) {
        for (Status s : ctx.source.statuses()) {
            Status copy = statusRepository.save(Status.builder()
                    .projectId(ctx.targetId())
                    .name(s.getName())
                    .category(s.getCategory())
                    .colorHex(s.getColorHex())
                    .description(s.getDescription())
                    .build());
            ctx.map(s.getId(), copy.getId());
            ctx.targetStatuses.add(copy);
        }
        ctx.count("statuses", ctx.source.statuses().size());
    }

    private void cloneTypes(Context ctx) {
        for (WorkItemType t : ctx.source.types()) {
            WorkItemType copy = workItemTypeRepository.save(WorkItemType.builder()
                    .projectId(ctx.targetId())
                    .name(t.getName())
                    .code(t.getCode())
                    .description(t.getDescription())
                    .iconName(t.getIconName())
                    .colorHex(t.getColorHex())
                    .hierarchyLevel(t.getHierarchyLevel())
                    .isSubtask(t.getIsSubtask())
                    .isSystemDefault(t.getIsSystemDefault())
                    .build());
            ctx.map(t.getId(), copy.getId());
        }
        ctx.count("workItemTypes", ctx.source.types().size());
    }

    private void cloneWorkflows(Context ctx) {
        for (Workflow w : ctx.source.workflows()) {
            Workflow copy = workflowRepository.save(Workflow.builder()
                    .projectId(ctx.targetId())
                    .name(ctx.rename(w.getName()))
                    .description(w.getDescription())
                    .isDefault(w.getIsDefault())
                    .build());
            ctx.map(w.getId(), copy.getId());
        }
        // Transition sao chép sau khi mọi workflow & status đã có id đích
        List<WorkflowTransition> transitions = ctx.source.transitions().stream()
                .map(t -> WorkflowTransition.builder()
                        .workflowId(ctx.id(t.getWorkflowId()))
                        .fromStatusId(ctx.id(t.getFromStatusId()))
                        .toStatusId(ctx.id(t.getToStatusId()))
                        .name(t.getName())
                        .conditionsConfig(ctx.remapJsonList(t.getConditionsConfig()))
                        .validatorsConfig(ctx.remapJsonList(t.getValidatorsConfig()))
                        .postFunctionsConfig(ctx.remapJsonList(t.getPostFunctionsConfig()))
                        .build())
                .toList();
        workflowTransitionRepository.saveAll(transitions);
        ctx.targetTransitions.addAll(transitions);
        ctx.count("workflows", ctx.source.workflows().size());
        ctx.count("transitions", transitions.size());
    }

    private void cloneSchemes(Context ctx) {
        for (WorkflowScheme s : ctx.source.schemes()) {
            WorkflowScheme copy = workflowSchemeRepository.save(WorkflowScheme.builder()
                    .projectId(ctx.targetId())
                    .name(ctx.rename(s.getName()))
                    .description(s.getDescription())
                    .build());
            ctx.map(s.getId(), copy.getId());
        }
        List<WorkflowSchemeMapping> mappings = ctx.source.schemeMappings().stream()
                .map(m -> WorkflowSchemeMapping.builder()
                        .schemeId(ctx.id(m.getSchemeId()))
                        .workItemTypeId(ctx.id(m.getWorkItemTypeId()))
                        .workflowId(ctx.id(m.getWorkflowId()))
                        .build())
                .toList();
        workflowSchemeMappingRepository.saveAll(mappings);
        mappings.forEach(m -> ctx.workflowByType.put(m.getWorkItemTypeId(), m.getWorkflowId()));
        ctx.count("workflowSchemes", ctx.source.schemes().size());
    }

    private void cloneBoards(Context ctx) {
        for (Board b : ctx.source.boards()) {
            Board copy = boardRepository.save(Board.builder()
                    .projectId(ctx.targetId())
                    .name(ctx.rename(b.getName()))
                    .type(b.getType())
                    .filterJql(b.getFilterJql())
                    .build());
            ctx.map(b.getId(), copy.getId());
        }
        for (BoardColumn c : ctx.source.columns()) {
            BoardColumn copy = boardColumnRepository.save(BoardColumn.builder()
                    .boardId(ctx.id(c.getBoardId()))
                    .name(c.getName())
                    .columnOrder(c.getColumnOrder())
                    .minWipLimit(c.getMinWipLimit())
                    .maxWipLimit(c.getMaxWipLimit())
                    .build());
            ctx.map(c.getId(), copy.getId());
        }
        boardColumnStatusRepository.saveAll(ctx.source.columnStatuses().stream()
                .map(cs -> BoardColumnStatus.builder()
                        .columnId(ctx.id(cs.getColumnId()))
                        .statusId(ctx.id(cs.getStatusId()))
                        .build())
                .toList());
        boardSwimlaneRepository.saveAll(ctx.source.swimlanes().stream()
                .map(sl -> BoardSwimlane.builder()
                        .boardId(ctx.id(sl.getBoardId()))
                        .name(sl.getName())
                        .strategy(sl.getStrategy())
                        .customJql(sl.getCustomJql())
                        .swimlaneOrder(sl.getSwimlaneOrder())
                        .build())
                .toList());
        ctx.count("boards", ctx.source.boards().size());
        ctx.count("boardColumns", ctx.source.columns().size());
        ctx.count("swimlanes", ctx.source.swimlanes().size());
    }

    /** Sao chép vai trò và ma trận quyền; KHÔNG sao chép thành viên (role_assignments). */
    private void cloneRoles(Context ctx) {
        for (ProjectRole r : ctx.source.roles()) {
            ProjectRole copy = projectRoleRepository.save(ProjectRole.builder()
                    .projectId(ctx.targetId())
                    .name(r.getName())
                    .description(r.getDescription())
                    .build());
            ctx.map(r.getId(), copy.getId());
        }
        List<PermissionSchemeEntry> entries = ctx.source.permissionEntries().stream()
                .filter(e -> ctx.idMap.containsKey(e.getRoleId()))
                .map(e -> PermissionSchemeEntry.builder()
                        .projectId(ctx.targetId())
                        .roleId(ctx.id(e.getRoleId()))
                        .permissionKey(e.getPermissionKey())
                        .build())
                .toList();
        permissionSchemeEntryRepository.saveAll(entries);
        ctx.count("roles", ctx.source.roles().size());
        ctx.count("permissionEntries", entries.size());
    }

    private void cloneLabels(Context ctx) {
        for (Label l : ctx.source.labels()) {
            Label copy = labelRepository.save(Label.builder()
                    .projectId(ctx.targetId())
                    .name(l.getName())
                    .colorHex(l.getColorHex())
                    .build());
            ctx.map(l.getId(), copy.getId());
        }
        ctx.count("labels", ctx.source.labels().size());
    }

    /** Component lead không được giữ vì người đó có thể không phải thành viên dự án mới. */
    private void cloneComponents(Context ctx) {
        for (Component c : ctx.source.components()) {
            Component copy = componentRepository.save(Component.builder()
                    .projectId(ctx.targetId())
                    .name(c.getName())
                    .description(c.getDescription())
                    .build());
            ctx.map(c.getId(), copy.getId());
        }
        ctx.count("components", ctx.source.components().size());
    }

    private void cloneVersions(Context ctx) {
        for (Version v : ctx.source.versions()) {
            Version copy = versionRepository.save(Version.builder()
                    .projectId(ctx.targetId())
                    .name(v.getName())
                    .description(v.getDescription())
                    .releaseDate(ctx.shift(v.getReleaseDate()))
                    .isReleased(false)
                    .isArchived(false)
                    .build());
            ctx.map(v.getId(), copy.getId());
        }
        ctx.count("versions", ctx.source.versions().size());
    }

    private void cloneCustomFields(Context ctx) {
        for (CustomFieldDefinition f : ctx.source.customFields()) {
            UUID[] applicable = f.getApplicableTypeIds() == null ? null
                    : Arrays.stream(f.getApplicableTypeIds()).map(ctx::id).toArray(UUID[]::new);
            CustomFieldDefinition copy = customFieldDefinitionRepository.save(CustomFieldDefinition.builder()
                    .projectId(ctx.targetId())
                    .fieldKey(f.getFieldKey())
                    .name(f.getName())
                    .fieldType(f.getFieldType())
                    .options(ctx.remapJson(f.getOptions()))
                    .validationRules(ctx.remapJson(f.getValidationRules()))
                    .applicableTypeIds(applicable)
                    .build());
            ctx.map(f.getId(), copy.getId());
        }
        ctx.count("customFields", ctx.source.customFields().size());
    }

    private void cloneAutomationRules(Context ctx) {
        List<AutomationRule> rules = ctx.source.automationRules().stream()
                .map(r -> AutomationRule.builder()
                        .projectId(ctx.targetId())
                        .name(r.getName())
                        .triggerType(r.getTriggerType())
                        .conditionsConfig(ctx.remapJson(r.getConditionsConfig()))
                        .actionsConfig(ctx.remapJson(r.getActionsConfig()))
                        .isActive(r.getIsActive())
                        .build())
                .toList();
        automationRuleRepository.saveAll(rules);
        ctx.count("automationRules", rules.size());
    }

    // ─── SPRINT & CÔNG VIỆC ─────────────────────────────────────────────────────

    /** Mọi sprint về FUTURE, giữ nguyên độ dài và khoảng cách giữa các sprint. */
    private void cloneSprints(Context ctx) {
        for (Sprint s : ctx.source.sprints()) {
            Sprint copy = sprintRepository.save(Sprint.builder()
                    .projectId(ctx.targetId())
                    .name(s.getName())
                    .goal(s.getGoal())
                    .state(SprintState.FUTURE)
                    .startDate(ctx.shift(s.getStartDate()))
                    .endDate(ctx.shift(s.getEndDate()))
                    .completedDate(null)
                    .build());
            ctx.map(s.getId(), copy.getId());
        }
        ctx.count("sprints", ctx.source.sprints().size());
    }

    private void cloneWorkItems(Context ctx) {
        List<WorkItem> sourceItems = ctx.source.workItems();
        if (sourceItems.isEmpty()) {
            ctx.count("workItems", 0);
            return;
        }
        UUID fallbackStatus = fallbackInitialStatus(ctx);

        // Lượt 1: tạo công việc (chưa nối cha) theo đúng thứ tự mã của nguồn
        List<WorkItem> copies = new ArrayList<>(sourceItems.size());
        for (WorkItem src : sourceItems) {
            UUID typeId = ctx.id(src.getTypeId());
            Long original = src.getOriginalEstimateSeconds() == null ? 0L : src.getOriginalEstimateSeconds();
            copies.add(WorkItem.builder()
                    .projectId(ctx.targetId())
                    .typeId(typeId)
                    .statusId(initialStatusFor(ctx, typeId, fallbackStatus))
                    .sprintId(ctx.options.includeSprints() ? ctx.idOrNull(src.getSprintId()) : null)
                    .code(atomicKeySequenceGenerator.generateNextCode(ctx.target.getKey()))
                    .summary(src.getSummary())
                    .description(src.getDescription())
                    .priority(src.getPriority())
                    .resolution(null)
                    .reporterId(ctx.actorId)
                    .assigneeId(null)
                    .storyPoints(src.getStoryPoints())
                    .originalEstimateSeconds(original)
                    .remainingEstimateSeconds(original)
                    .timeSpentSeconds(0L)
                    .rank(src.getRank())
                    .componentId(ctx.idOrNull(src.getComponentId()))
                    .fixVersionId(ctx.options.includeVersions() ? ctx.idOrNull(src.getFixVersionId()) : null)
                    .customFields(castMap(ctx.remapJson(src.getCustomFields())))
                    .startDate(ctx.shift(src.getStartDate()))
                    .dueDate(ctx.shift(src.getDueDate()))
                    .resolvedAt(null)
                    .version(0)
                    .build());
        }
        List<WorkItem> saved = workItemRepository.saveAll(copies);
        for (int i = 0; i < sourceItems.size(); i++) {
            ctx.map(sourceItems.get(i).getId(), saved.get(i).getId());
        }

        // Lượt 2: nối lại cây cha-con (cha luôn nằm trong tập nguồn - validator đã đảm bảo)
        List<WorkItem> withParent = new ArrayList<>();
        for (int i = 0; i < sourceItems.size(); i++) {
            UUID parent = ctx.idOrNull(sourceItems.get(i).getParentId());
            if (parent != null) {
                saved.get(i).setParentId(parent);
                withParent.add(saved.get(i));
            }
        }
        workItemRepository.saveAll(withParent);

        List<WorkItemLabelMap> labelMaps = ctx.source.labelMaps().stream()
                .filter(m -> ctx.idMap.containsKey(m.getLabelId()))
                .map(m -> WorkItemLabelMap.builder()
                        .workItemId(ctx.id(m.getWorkItemId()))
                        .labelId(ctx.id(m.getLabelId()))
                        .build())
                .toList();
        workItemLabelMapRepository.saveAll(labelMaps);

        List<WorkItemComponentMap> componentMaps = ctx.source.componentMaps().stream()
                .filter(m -> ctx.idMap.containsKey(m.getComponentId()))
                .map(m -> WorkItemComponentMap.builder()
                        .workItemId(ctx.id(m.getWorkItemId()))
                        .componentId(ctx.id(m.getComponentId()))
                        .build())
                .toList();
        workItemComponentMapRepository.saveAll(componentMaps);

        List<WorkItemLink> links = ctx.source.links().stream()
                .map(l -> WorkItemLink.builder()
                        .sourceItemId(ctx.id(l.getSourceItemId()))
                        .targetItemId(ctx.id(l.getTargetItemId()))
                        .linkType(l.getLinkType())
                        .createdBy(ctx.actorId)
                        .build())
                .toList();
        workItemLinkRepository.saveAll(links);

        String origin = ctx.source.project().getName() + " (" + ctx.source.project().getKey() + ")";
        for (int i = 0; i < saved.size(); i++) {
            WorkItem item = saved.get(i);
            workItemAuditService.recordCustomEvent(item.getId(), "SUMMARY", null,
                    "Đã tạo công việc " + item.getCode() + " từ " + sourceItems.get(i).getCode()
                            + " của mẫu " + origin, ctx.actorId);
        }

        ctx.count("workItems", saved.size());
        ctx.count("links", links.size());
        ctx.count("labelMaps", labelMaps.size());
        ctx.count("componentMaps", componentMaps.size());
    }

    /**
     * Trạng thái khởi tạo của một loại công việc: đích của transition "Create" (from = NULL)
     * trong workflow gắn với loại đó; nếu không có thì trạng thái TODO đầu tiên.
     */
    private UUID initialStatusFor(Context ctx, UUID typeId, UUID fallback) {
        UUID workflowId = ctx.workflowByType.get(typeId);
        if (workflowId != null) {
            for (WorkflowTransition t : ctx.targetTransitions) {
                if (workflowId.equals(t.getWorkflowId()) && t.getFromStatusId() == null) {
                    return t.getToStatusId();
                }
            }
        }
        return fallback;
    }

    private UUID fallbackInitialStatus(Context ctx) {
        return ctx.targetStatuses.stream()
                .filter(s -> s.getCategory() == StatusCategory.TODO)
                .findFirst()
                .or(() -> ctx.targetStatuses.stream().findFirst())
                .map(Status::getId)
                .orElseThrow(() -> new IllegalStateException("Dự án nguồn không có trạng thái nào để gán cho công việc"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }

    // ─── NGỮ CẢNH ─────────────────────────────────────────────────────────────

    /** Trạng thái của một lần nhân bản: bảng ánh xạ id, dữ liệu đích đã tạo, thống kê. */
    static final class Context {
        final ProjectBlueprint source;
        final Project target;
        final Options options;
        final UUID actorId;
        final Map<UUID, UUID> idMap = new HashMap<>();
        final Map<UUID, UUID> workflowByType = new HashMap<>();
        final List<Status> targetStatuses = new ArrayList<>();
        final List<WorkflowTransition> targetTransitions = new ArrayList<>();
        final Map<String, Integer> stats = new LinkedHashMap<>();

        Context(ProjectBlueprint source, Project target, Options options, UUID actorId) {
            this.source = source;
            this.target = target;
            this.options = options;
            this.actorId = actorId;
        }

        UUID targetId() {
            return target.getId();
        }

        void map(UUID sourceId, UUID targetId) {
            idMap.put(sourceId, targetId);
        }

        /** Id đích nếu có ánh xạ; ngược lại giữ nguyên (tham chiếu toàn cục). */
        UUID id(UUID sourceId) {
            return sourceId == null ? null : idMap.getOrDefault(sourceId, sourceId);
        }

        /** Id đích nếu có ánh xạ; ngược lại null (tham chiếu tới dữ liệu không được sao chép). */
        UUID idOrNull(UUID sourceId) {
            return sourceId == null ? null : idMap.get(sourceId);
        }

        void count(String key, int value) {
            stats.put(key, value);
        }

        LocalDate shift(LocalDate date) {
            return date == null ? null : date.plusDays(options.dateOffsetDays());
        }

        LocalDateTime shift(LocalDateTime dateTime) {
            return dateTime == null ? null : dateTime.plusDays(options.dateOffsetDays());
        }

        /** "Mẫu Web Board" -> "Shop Online Board" khi tên bắt đầu bằng tên dự án nguồn. */
        String rename(String name) {
            String sourceName = source.project().getName();
            if (name != null && sourceName != null && name.startsWith(sourceName)) {
                return target.getName() + name.substring(sourceName.length());
            }
            return name;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> remapJsonList(List<Map<String, Object>> list) {
            return list == null ? null : (List<Map<String, Object>>) remapJson(list);
        }

        /** Sao chép sâu cấu trúc JSON, thay mọi UUID (dạng chuỗi hoặc UUID) có trong idMap. */
        Object remapJson(Object node) {
            if (node == null) {
                return null;
            }
            if (node instanceof Map<?, ?> map) {
                Map<String, Object> copy = new LinkedHashMap<>();
                map.forEach((k, v) -> copy.put(String.valueOf(k), remapJson(v)));
                return copy;
            }
            if (node instanceof Collection<?> list) {
                List<Object> copy = new ArrayList<>(list.size());
                list.forEach(v -> copy.add(remapJson(v)));
                return copy;
            }
            if (node instanceof UUID uuid) {
                return id(uuid);
            }
            if (node instanceof String s && s.length() == 36) {
                try {
                    UUID mapped = idMap.get(UUID.fromString(s));
                    return mapped != null ? mapped.toString() : s;
                } catch (IllegalArgumentException ignored) {
                    return s;
                }
            }
            return node;
        }
    }
}
