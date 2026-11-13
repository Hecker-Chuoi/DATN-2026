package com.datn.workservice.service.mapper;

import com.datn.common.dto.ApiResponse;
import com.datn.workservice.client.IdentityClient;
import com.datn.workservice.client.dto.UserResponse;
import com.datn.workservice.dto.response.BoardCardResponse;
import com.datn.workservice.entity.Label;
import com.datn.workservice.entity.Project;
import com.datn.workservice.entity.Sprint;
import com.datn.workservice.entity.Status;
import com.datn.workservice.entity.WorkItem;
import com.datn.workservice.entity.WorkItemLabelMap;
import com.datn.workservice.entity.WorkItemLink;
import com.datn.workservice.entity.WorkItemType;
import com.datn.workservice.enums.LinkType;
import com.datn.workservice.enums.StatusCategory;
import com.datn.workservice.repository.LabelRepository;
import com.datn.workservice.repository.ProjectRepository;
import com.datn.workservice.repository.SprintRepository;
import com.datn.workservice.repository.StatusRepository;
import com.datn.workservice.enums.CompletionRequestStatus;
import com.datn.workservice.repository.WorkItemCompletionRequestRepository;
import com.datn.workservice.repository.WorkItemLabelMapRepository;
import com.datn.workservice.repository.WorkItemLinkRepository;
import com.datn.workservice.repository.WorkItemRepository;
import com.datn.workservice.repository.WorkItemTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Chuyen WorkItem thanh the hien thi tren Board/Backlog, nap du lieu tra cuu theo LO.
 *
 * So truy van la hang so (~9 truy van + 1 lan goi identity) bat ke co bao nhieu the,
 * thay vi ~8 truy van moi the nhu mapToSummaryResponse.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkItemCardMapper {

    private final StatusRepository statusRepository;
    private final WorkItemTypeRepository workItemTypeRepository;
    private final WorkItemRepository workItemRepository;
    private final WorkItemLinkRepository workItemLinkRepository;
    private final WorkItemLabelMapRepository workItemLabelMapRepository;
    private final LabelRepository labelRepository;
    private final SprintRepository sprintRepository;
    private final ProjectRepository projectRepository;
    private final WorkItemCompletionRequestRepository completionRequestRepository;
    private final IdentityClient identityClient;

    /** Tuy chon de bo bot truy van khi man hinh khong can toi. */
    public record Options(boolean includeLabels, boolean includeAssigneeNames) {
        public static Options board() {
            return new Options(false, true);
        }

        public static Options backlog() {
            return new Options(true, true);
        }
    }

    public CardMappingContext buildContext(List<WorkItem> items, Options options) {
        if (items == null || items.isEmpty()) {
            return CardMappingContext.builder().build();
        }

        // 1. Status la bang toan cuc va rat nho - nap het mot lan, dung lai cho ca replay changelog.
        List<Status> statuses = statusRepository.findAll();
        Map<UUID, Status> statusById = statuses.stream()
                .collect(Collectors.toMap(Status::getId, Function.identity(), (a, b) -> a));
        Map<String, Status> statusByLowerName = new HashMap<>();
        for (Status s : statuses) {
            if (s.getName() != null) {
                statusByLowerName.putIfAbsent(s.getName().toLowerCase(Locale.ROOT), s);
            }
        }

        List<UUID> itemIds = items.stream().map(WorkItem::getId).toList();

        // 2. Cha truc tiep (de hien parentCode va truy nguoc Epic)
        Set<UUID> parentIds = collect(items, WorkItem::getParentId);
        Map<UUID, WorkItem> parentById = new HashMap<>();
        if (!parentIds.isEmpty()) {
            workItemRepository.findAllById(parentIds)
                    .forEach(p -> parentById.put(p.getId(), p));
            // Epic co the la ong (Subtask -> Story -> Epic), nap them mot tang nua.
            Set<UUID> grandParentIds = parentById.values().stream()
                    .map(WorkItem::getParentId)
                    .filter(Objects::nonNull)
                    .filter(id -> !parentById.containsKey(id))
                    .collect(Collectors.toSet());
            if (!grandParentIds.isEmpty()) {
                workItemRepository.findAllById(grandParentIds)
                        .forEach(p -> parentById.put(p.getId(), p));
            }
        }

        // 3. Loai cong viec (nap ca cua the chinh va cua cha/ong de nhan dien duoc Epic)
        Set<UUID> typeIds = collect(items, WorkItem::getTypeId);
        typeIds.addAll(collect(new ArrayList<>(parentById.values()), WorkItem::getTypeId));
        Map<UUID, WorkItemType> typeById = typeIds.isEmpty() ? Collections.emptyMap()
                : workItemTypeRepository.findAllById(typeIds).stream()
                        .collect(Collectors.toMap(WorkItemType::getId, Function.identity(), (a, b) -> a));

        // 4. Cong viec con - phuc vu dem subtask va cuon thoi gian
        Map<UUID, List<WorkItem>> childrenByParentId = workItemRepository
                .findByParentIdInAndDeletedAtIsNull(itemIds).stream()
                .collect(Collectors.groupingBy(WorkItem::getParentId));

        // 5. Cac lien ket BLOCKS tro TOI cac the nay
        Set<UUID> blockedItemIds = resolveBlockedItems(itemIds, statusById);

        // 6. Ten Sprint
        Set<UUID> sprintIds = collect(items, WorkItem::getSprintId);
        Map<UUID, String> sprintNameById = sprintIds.isEmpty() ? Collections.emptyMap()
                : sprintRepository.findAllById(sprintIds).stream()
                        .collect(Collectors.toMap(Sprint::getId, Sprint::getName, (a, b) -> a));

        // 7. Ma du an (thuong chi 1 dong)
        Set<UUID> projectIds = collect(items, WorkItem::getProjectId);
        Map<UUID, String> projectKeyById = projectIds.isEmpty() ? Collections.emptyMap()
                : projectRepository.findAllById(projectIds).stream()
                        .collect(Collectors.toMap(Project::getId, Project::getKey, (a, b) -> a));

        // 8. Nhan (chi Backlog can)
        Map<UUID, List<String>> labelsByItemId = options.includeLabels()
                ? resolveLabels(itemIds)
                : Collections.emptyMap();

        // 9. Ten nguoi duoc giao - loi identity khong duoc lam hong ca man hinh
        Map<UUID, UserResponse> userById = options.includeAssigneeNames()
                ? fetchUsers(collect(items, WorkItem::getAssigneeId))
                : Collections.emptyMap();

        // 10. The dang "Tam hoan thanh" - cho Admin du an xac nhan (BR-W03)
        Set<UUID> completionPendingItemIds = completionRequestRepository
                .findByWorkItemIdInAndStatus(itemIds, CompletionRequestStatus.PENDING).stream()
                .map(r -> r.getWorkItemId())
                .collect(Collectors.toSet());

        return CardMappingContext.builder()
                .statusById(statusById)
                .statusByLowerName(statusByLowerName)
                .typeById(typeById)
                .parentById(parentById)
                .childrenByParentId(childrenByParentId)
                .blockedItemIds(blockedItemIds)
                .sprintNameById(sprintNameById)
                .projectKeyById(projectKeyById)
                .userById(userById)
                .labelsByItemId(labelsByItemId)
                .completionPendingItemIds(completionPendingItemIds)
                .build();
    }

    public List<BoardCardResponse> mapAll(List<WorkItem> items, CardMappingContext ctx) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        return items.stream().map(i -> map(i, ctx)).collect(Collectors.toList());
    }

    public BoardCardResponse map(WorkItem item, CardMappingContext ctx) {
        WorkItemType type = item.getTypeId() == null ? null : ctx.getTypeById().get(item.getTypeId());
        Status status = item.getStatusId() == null ? null : ctx.getStatusById().get(item.getStatusId());
        WorkItem parent = item.getParentId() == null ? null : ctx.getParentById().get(item.getParentId());

        List<WorkItem> children = ctx.getChildrenByParentId()
                .getOrDefault(item.getId(), Collections.emptyList());
        int subtaskDone = (int) children.stream()
                .filter(c -> isDone(c.getStatusId(), ctx))
                .count();

        long totalSpent = nz(item.getTimeSpentSeconds())
                + children.stream().mapToLong(c -> nz(c.getTimeSpentSeconds())).sum();
        long totalRemaining = nz(item.getRemainingEstimateSeconds())
                + children.stream().mapToLong(c -> nz(c.getRemainingEstimateSeconds())).sum();

        WorkItem epic = resolveEpic(item, ctx);
        UserResponse assignee = item.getAssigneeId() == null ? null : ctx.getUserById().get(item.getAssigneeId());

        return BoardCardResponse.builder()
                .id(item.getId())
                .code(item.getCode())
                .summary(item.getSummary())
                .projectId(item.getProjectId())
                .projectKey(ctx.getProjectKeyById().get(item.getProjectId()))
                .typeId(item.getTypeId())
                .typeName(type == null ? null : type.getName())
                .typeCode(type == null ? null : type.getCode())
                .typeColor(type == null ? null : type.getColorHex())
                .typeIcon(type == null ? null : type.getIconName())
                .hierarchyLevel(type == null ? null : type.getHierarchyLevel())
                .isSubtask(type != null && Boolean.TRUE.equals(type.getIsSubtask()))
                .statusId(item.getStatusId())
                .statusName(status == null ? null : status.getName())
                .statusCategory(status == null ? null : status.getCategory())
                .statusColorHex(status == null ? null : status.getColorHex())
                .parentId(item.getParentId())
                .parentCode(parent == null ? null : parent.getCode())
                .parentSummary(parent == null ? null : parent.getSummary())
                .epicId(epic == null ? null : epic.getId())
                .epicCode(epic == null ? null : epic.getCode())
                .epicSummary(epic == null ? null : epic.getSummary())
                .epicColor(epicColor(epic, ctx))
                .sprintId(item.getSprintId())
                .sprintName(item.getSprintId() == null ? null : ctx.getSprintNameById().get(item.getSprintId()))
                .priority(item.getPriority())
                .resolution(item.getResolution())
                .assigneeId(item.getAssigneeId())
                .assigneeName(displayName(assignee, item.getAssigneeId()))
                .assigneeAvatar(null) // UserResponse chua co truong avatar
                .storyPoints(item.getStoryPoints())
                .originalEstimateSeconds(item.getOriginalEstimateSeconds())
                .remainingEstimateSeconds(item.getRemainingEstimateSeconds())
                .timeSpentSeconds(item.getTimeSpentSeconds())
                .totalTimeSpentSeconds(totalSpent)
                .totalRemainingEstimateSeconds(totalRemaining)
                .subtaskCount(children.size())
                .subtaskDoneCount(subtaskDone)
                .isBlocked(ctx.getBlockedItemIds().contains(item.getId()))
                .startDate(item.getStartDate())
                .dueDate(item.getDueDate())
                .isOverdue(isOverdue(item, status))
                .labels(ctx.getLabelsByItemId().get(item.getId()))
                .completionPending(ctx.getCompletionPendingItemIds().contains(item.getId()))
                .rank(item.getRank())
                .version(item.getVersion())
                .build();
    }

    // ───────────────────────── helpers ─────────────────────────

    private Set<UUID> collect(List<WorkItem> items, Function<WorkItem, UUID> getter) {
        return items.stream().map(getter).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private boolean isDone(UUID statusId, CardMappingContext ctx) {
        Status s = statusId == null ? null : ctx.getStatusById().get(statusId);
        return s != null && s.getCategory() == StatusCategory.DONE;
    }

    private long nz(Long v) {
        return v == null ? 0L : v;
    }

    private boolean isOverdue(WorkItem item, Status status) {
        if (item.getDueDate() == null) return false;
        if (status != null && status.getCategory() == StatusCategory.DONE) return false;
        return item.getDueDate().isBefore(LocalDate.now());
    }

    /** Di nguoc parentId toi khi gap loai co hierarchyLevel == 1 (Epic). Toi da 2 buoc. */
    private WorkItem resolveEpic(WorkItem item, CardMappingContext ctx) {
        WorkItem current = item;
        for (int hop = 0; hop < 3 && current != null; hop++) {
            Integer level = ctx.hierarchyLevelOf(current.getTypeId());
            if (level != null && level == 1) {
                return current == item ? null : current; // Epic khong tro toi chinh no
            }
            if (current.getParentId() == null) return null;
            current = ctx.getParentById().get(current.getParentId());
        }
        return null;
    }

    private String epicColor(WorkItem epic, CardMappingContext ctx) {
        if (epic == null || epic.getTypeId() == null) return null;
        WorkItemType t = ctx.getTypeById().get(epic.getTypeId());
        return t == null ? null : t.getColorHex();
    }

    private String displayName(UserResponse user, UUID fallbackId) {
        if (user == null) {
            return fallbackId == null ? null : null;
        }
        String full = user.getFullName();
        if (full != null && !full.isBlank()) return full;
        return user.getUsername();
    }

    private Set<UUID> resolveBlockedItems(List<UUID> itemIds, Map<UUID, Status> statusById) {
        List<WorkItemLink> incoming =
                workItemLinkRepository.findByTargetItemIdInAndLinkType(itemIds, LinkType.BLOCKS);
        if (incoming.isEmpty()) {
            return Collections.emptySet();
        }
        Set<UUID> blockerIds = incoming.stream()
                .map(WorkItemLink::getSourceItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, WorkItem> blockers = workItemRepository.findAllById(blockerIds).stream()
                .collect(Collectors.toMap(WorkItem::getId, Function.identity(), (a, b) -> a));

        Set<UUID> blocked = new HashSet<>();
        for (WorkItemLink link : incoming) {
            WorkItem blocker = blockers.get(link.getSourceItemId());
            if (blocker == null || blocker.getDeletedAt() != null) continue;
            Status s = blocker.getStatusId() == null ? null : statusById.get(blocker.getStatusId());
            if (s == null || s.getCategory() != StatusCategory.DONE) {
                blocked.add(link.getTargetItemId());
            }
        }
        return blocked;
    }

    private Map<UUID, List<String>> resolveLabels(List<UUID> itemIds) {
        List<WorkItemLabelMap> maps = workItemLabelMapRepository.findByWorkItemIdIn(itemIds);
        if (maps.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<UUID> labelIds = maps.stream().map(WorkItemLabelMap::getLabelId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> labelNameById = labelRepository.findAllById(labelIds).stream()
                .collect(Collectors.toMap(Label::getId, Label::getName, (a, b) -> a));

        Map<UUID, List<String>> result = new HashMap<>();
        for (WorkItemLabelMap m : maps) {
            String name = labelNameById.get(m.getLabelId());
            if (name != null) {
                result.computeIfAbsent(m.getWorkItemId(), k -> new ArrayList<>()).add(name);
            }
        }
        return result;
    }

    private Map<UUID, UserResponse> fetchUsers(Set<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            ApiResponse<List<UserResponse>> res = identityClient.getUsersByIds(new ArrayList<>(userIds));
            if (res != null && res.getData() != null) {
                return res.getData().stream()
                        .filter(u -> u.getId() != null)
                        .collect(Collectors.toMap(UserResponse::getId, Function.identity(), (a, b) -> a));
            }
        } catch (Exception e) {
            log.warn("Khong lay duoc thong tin nguoi dung cho the Board: {}", e.getMessage());
        }
        return Collections.emptyMap();
    }
}
