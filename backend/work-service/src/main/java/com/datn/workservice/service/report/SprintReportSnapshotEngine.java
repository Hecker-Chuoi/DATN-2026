package com.datn.workservice.service.report;

import com.datn.workservice.dto.model.SprintItemSnapshot;
import com.datn.workservice.dto.model.SprintSnapshotData;
import com.datn.workservice.dto.response.BurndownPointResponse;
import com.datn.workservice.entity.Sprint;
import com.datn.workservice.entity.SprintReportSnapshot;
import com.datn.workservice.entity.Status;
import com.datn.workservice.entity.WorkItem;
import com.datn.workservice.entity.WorkItemType;
import com.datn.workservice.repository.SprintReportSnapshotRepository;
import com.datn.workservice.repository.StatusRepository;
import com.datn.workservice.repository.WorkItemTypeRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Chup so lieu cuoi cung cua Sprint khi dong, phuc vu Burndown/Velocity ve sau.
 *
 * Bang sprint_report_snapshots co UNIQUE(sprint_id) nen bat buoc phai UPSERT:
 * insert mu se nem DataIntegrityViolationException neu Sprint duoc dong lai.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SprintReportSnapshotEngine {

    private final SprintReportSnapshotRepository snapshotRepository;
    private final StatusRepository statusRepository;
    private final WorkItemTypeRepository workItemTypeRepository;
    private final ObjectMapper objectMapper;

    /**
     * @param items         toan bo cong viec dang thuoc Sprint (truoc khi di chuyen cai chua xong)
     * @param doneStatusIds tap status thuoc nhom DONE
     * @param burndown      chuoi Burndown da tinh san; co the rong (se dung fallback khi doc bao cao)
     */
    public SprintReportSnapshot generateFinalSnapshot(Sprint sprint,
                                                     List<WorkItem> items,
                                                     Set<UUID> doneStatusIds,
                                                     UUID closedBy,
                                                     UUID targetSprintId,
                                                     String targetSprintName,
                                                     List<BurndownPointResponse> burndown) {

        Map<UUID, Status> statusById = statusRepository.findAll().stream()
                .collect(Collectors.toMap(Status::getId, Function.identity(), (a, b) -> a));
        Map<UUID, WorkItemType> typeById = loadTypes(items);

        List<WorkItem> completed = items.stream()
                .filter(i -> doneStatusIds.contains(i.getStatusId()))
                .toList();
        List<WorkItem> incomplete = items.stream()
                .filter(i -> !doneStatusIds.contains(i.getStatusId()))
                .toList();

        // Sub-task khong tinh diem rieng: diem cua chung da nam o Story/Task cha.
        BigDecimal completedPoints = sumStoryPoints(completed, typeById);
        BigDecimal incompletePoints = sumStoryPoints(incomplete, typeById);
        BigDecimal totalPoints = completedPoints.add(incompletePoints);

        SprintSnapshotData data = SprintSnapshotData.builder()
                .sprintId(sprint.getId())
                .sprintName(sprint.getName())
                .goal(sprint.getGoal())
                .startDate(sprint.getStartDate())
                .endDate(sprint.getEndDate())
                .completedDate(sprint.getCompletedDate())
                .closedAt(LocalDateTime.now())
                .closedBy(closedBy)
                .committedStoryPoints(totalPoints)
                .completedStoryPoints(completedPoints)
                .incompleteStoryPoints(incompletePoints)
                .totalItems(items.size())
                .completedItems(completed.size())
                .incompleteItems(incomplete.size())
                .velocityPoints(completedPoints)
                .excludeWeekends(Boolean.TRUE)
                .burndown(burndown == null ? Collections.emptyList() : burndown)
                .completed(toItemSnapshots(completed, statusById, typeById))
                .incomplete(toItemSnapshots(incomplete, statusById, typeById))
                .movedToSprintId(targetSprintId)
                .movedToSprintName(targetSprintName)
                .build();

        // UPSERT theo rang buoc UNIQUE(sprint_id).
        SprintReportSnapshot snapshot = snapshotRepository.findBySprintId(sprint.getId())
                .orElseGet(() -> SprintReportSnapshot.builder().sprintId(sprint.getId()).build());

        snapshot.setTotalStoryPoints(totalPoints);
        snapshot.setCompletedStoryPoints(completedPoints);
        snapshot.setIncompleteStoryPoints(incompletePoints);
        snapshot.setTotalItems(items.size());
        snapshot.setCompletedItems(completed.size());
        // Cot jsonb duoc anh xa bang Map<String,Object> (dung mau da chay on cua
        // WorkItem.customFields); kieu Object thuan khien Hibernate nem ClassCastException.
        snapshot.setSnapshotData(objectMapper.convertValue(data, new TypeReference<Map<String, Object>>() {}));
        snapshot.setClosedBy(closedBy);

        SprintReportSnapshot saved = snapshotRepository.save(snapshot);
        log.info("Snapshot Sprint {} - {} item, {}/{} story points hoan thanh",
                sprint.getName(), items.size(), completedPoints, totalPoints);
        return saved;
    }

    private Map<UUID, WorkItemType> loadTypes(List<WorkItem> items) {
        Set<UUID> typeIds = items.stream()
                .map(WorkItem::getTypeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (typeIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return workItemTypeRepository.findAllById(typeIds).stream()
                .collect(Collectors.toMap(WorkItemType::getId, Function.identity(), (a, b) -> a));
    }

    private boolean isSubtask(WorkItem item, Map<UUID, WorkItemType> typeById) {
        WorkItemType type = item.getTypeId() == null ? null : typeById.get(item.getTypeId());
        return type != null && Boolean.TRUE.equals(type.getIsSubtask());
    }

    private BigDecimal sumStoryPoints(List<WorkItem> items, Map<UUID, WorkItemType> typeById) {
        return items.stream()
                .filter(i -> !isSubtask(i, typeById))
                .map(WorkItem::getStoryPoints)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<SprintItemSnapshot> toItemSnapshots(List<WorkItem> items,
                                                     Map<UUID, Status> statusById,
                                                     Map<UUID, WorkItemType> typeById) {
        return items.stream().map(i -> {
            Status status = i.getStatusId() == null ? null : statusById.get(i.getStatusId());
            WorkItemType type = i.getTypeId() == null ? null : typeById.get(i.getTypeId());
            return SprintItemSnapshot.builder()
                    .id(i.getId())
                    .code(i.getCode())
                    .summary(i.getSummary())
                    .typeName(type == null ? null : type.getName())
                    .statusName(status == null ? null : status.getName())
                    .statusCategory(status == null ? null : status.getCategory())
                    .storyPoints(i.getStoryPoints())
                    .assigneeId(i.getAssigneeId())
                    .build();
        }).toList();
    }
}
