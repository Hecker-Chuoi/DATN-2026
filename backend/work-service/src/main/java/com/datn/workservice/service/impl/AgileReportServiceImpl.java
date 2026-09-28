package com.datn.workservice.service.impl;

import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workservice.dto.model.SprintSnapshotData;
import com.datn.workservice.dto.response.BurndownPointResponse;
import com.datn.workservice.dto.response.BurndownResponse;
import com.datn.workservice.dto.response.CfdPointResponse;
import com.datn.workservice.dto.response.CfdResponse;
import com.datn.workservice.dto.response.VelocityPointResponse;
import com.datn.workservice.dto.response.VelocityResponse;
import com.datn.workservice.entity.Sprint;
import com.datn.workservice.entity.SprintReportSnapshot;
import com.datn.workservice.entity.Status;
import com.datn.workservice.entity.WorkItem;
import com.datn.workservice.entity.WorkItemType;
import com.datn.workservice.enums.SprintState;
import com.datn.workservice.enums.StatusCategory;
import com.datn.workservice.repository.ProjectRepository;
import com.datn.workservice.repository.SprintReportSnapshotRepository;
import com.datn.workservice.repository.SprintRepository;
import com.datn.workservice.repository.StatusRepository;
import com.datn.workservice.repository.WorkItemRepository;
import com.datn.workservice.repository.WorkItemTypeRepository;
import com.datn.workservice.service.AgileReportService;
import com.datn.workservice.service.report.BurndownCalculator;
import com.datn.workservice.service.report.WorkItemStateReplayer;
import com.datn.workservice.service.report.WorkItemStateReplayer.ItemStateTimeline;
import com.datn.workservice.service.report.WorkItemStateReplayer.StateEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgileReportServiceImpl implements AgileReportService {

    private static final int DEFAULT_VELOCITY_LIMIT = 6;

    private final SprintRepository sprintRepository;
    private final SprintReportSnapshotRepository snapshotRepository;
    private final ProjectRepository projectRepository;
    private final WorkItemRepository workItemRepository;
    private final WorkItemTypeRepository workItemTypeRepository;
    private final StatusRepository statusRepository;
    private final WorkItemStateReplayer replayer;
    private final BurndownCalculator burndownCalculator;
    private final ObjectMapper objectMapper;

    // ───────────────────────────── Burndown ─────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public BurndownResponse getBurndown(UUID sprintId, Boolean excludeWeekends) {
        Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Sprint: " + sprintId));
        boolean skipWeekends = !Boolean.FALSE.equals(excludeWeekends);

        // 1. Sprint da dong: doc snapshot de so lieu lich su khong doi theo thoi gian.
        if (sprint.getState() == SprintState.CLOSED) {
            SprintSnapshotData data = readSnapshot(sprintId);
            if (data != null && data.getBurndown() != null && !data.getBurndown().isEmpty()) {
                return fromSnapshot(sprint, data, skipWeekends);
            }
            // Sprint dong TRUOC khi co tinh nang bao cao: dung lai tu changelog va noi ro.
            return reconstruct(sprint, skipWeekends, "RECONSTRUCTED_LEGACY");
        }

        // 2. Sprint dang chay / sap toi: dung lai tu changelog.
        return reconstruct(sprint, skipWeekends, "RECONSTRUCTED");
    }

    private BurndownResponse reconstruct(Sprint sprint, boolean excludeWeekends, String source) {
        if (sprint.getEndDate() == null) {
            throw new BadRequestException("Sprint chưa có ngày kết thúc nên chưa thể vẽ Burndown");
        }

        List<WorkItem> all = workItemRepository.findBySprintIdAndDeletedAtIsNullOrderByRankAsc(sprint.getId());
        Map<UUID, WorkItemType> typeById = loadTypes(all);
        // Sub-task khong tinh diem rieng - se dem trung voi Story/Task cha.
        List<WorkItem> items = all.stream()
                .filter(i -> !burndownCalculator.isSubtask(i, typeById))
                .toList();

        Map<UUID, Status> statusById = loadStatusById();
        Map<String, Status> statusByLowerName = loadStatusByName();
        Map<UUID, ItemStateTimeline> timelines = replayer.replay(items, statusById, statusByLowerName);

        LocalDateTime cutoff = sprint.getState() == SprintState.CLOSED
                ? (sprint.getCompletedDate() != null ? sprint.getCompletedDate() : sprint.getEndDate())
                : LocalDateTime.now();

        List<BurndownPointResponse> points =
                burndownCalculator.buildSeries(sprint, items, timelines, cutoff, excludeWeekends);

        BurndownPointResponse last = points.isEmpty() ? null : points.get(points.size() - 1);
        BigDecimal committed = points.isEmpty() ? BigDecimal.ZERO : points.get(0).getScopePoints();

        return BurndownResponse.builder()
                .sprintId(sprint.getId())
                .sprintName(sprint.getName())
                .goal(sprint.getGoal())
                .state(sprint.getState())
                .startDate(sprint.getStartDate())
                .endDate(sprint.getEndDate())
                .completedDate(sprint.getCompletedDate())
                .committedStoryPoints(committed)
                .completedStoryPoints(last == null ? BigDecimal.ZERO : last.getCompletedPoints())
                .remainingStoryPoints(last == null ? BigDecimal.ZERO : last.getRemainingPoints())
                .totalItems(items.size())
                .completedItems(last == null ? 0 : last.getCompletedItems())
                .excludeWeekends(excludeWeekends)
                .source(source)
                .points(points)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private BurndownResponse fromSnapshot(Sprint sprint, SprintSnapshotData data, boolean excludeWeekends) {
        return BurndownResponse.builder()
                .sprintId(sprint.getId())
                .sprintName(data.getSprintName() != null ? data.getSprintName() : sprint.getName())
                .goal(data.getGoal())
                .state(sprint.getState())
                .startDate(data.getStartDate())
                .endDate(data.getEndDate())
                .completedDate(data.getCompletedDate())
                .committedStoryPoints(data.getCommittedStoryPoints())
                .completedStoryPoints(data.getCompletedStoryPoints())
                .remainingStoryPoints(data.getIncompleteStoryPoints())
                .totalItems(data.getTotalItems())
                .completedItems(data.getCompletedItems())
                .excludeWeekends(excludeWeekends)
                .source("SNAPSHOT")
                .points(data.getBurndown())
                .generatedAt(LocalDateTime.now())
                .build();
    }

    // ───────────────────────────── Velocity ─────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public VelocityResponse getVelocity(UUID projectId, Integer limit) {
        requireProject(projectId);
        int take = (limit == null || limit <= 0) ? DEFAULT_VELOCITY_LIMIT : Math.min(limit, 30);

        List<Sprint> closed = sprintRepository
                .findByProjectIdAndStateOrderByCompletedDateDesc(projectId, SprintState.CLOSED)
                .stream().limit(take).toList();

        // Dao nguoc ve thu tu thoi gian tang dan de bieu do doc tu trai sang phai.
        List<Sprint> chronological = new ArrayList<>(closed);
        chronological.sort(Comparator.comparing(
                s -> s.getCompletedDate() == null ? LocalDateTime.MIN : s.getCompletedDate()));

        Map<UUID, SprintReportSnapshot> snapshots = chronological.isEmpty()
                ? Map.of()
                : snapshotRepository.findBySprintIdIn(chronological.stream().map(Sprint::getId).toList())
                    .stream().collect(Collectors.toMap(SprintReportSnapshot::getSprintId, Function.identity()));

        List<VelocityPointResponse> points = new ArrayList<>();
        for (Sprint s : chronological) {
            SprintReportSnapshot snap = snapshots.get(s.getId());
            if (snap != null) {
                points.add(VelocityPointResponse.builder()
                        .sprintId(s.getId()).sprintName(s.getName())
                        .startDate(s.getStartDate()).endDate(s.getEndDate()).completedDate(s.getCompletedDate())
                        .committedStoryPoints(nz(snap.getTotalStoryPoints()))
                        .completedStoryPoints(nz(snap.getCompletedStoryPoints()))
                        .committedItems(snap.getTotalItems())
                        .completedItems(snap.getCompletedItems())
                        .estimated(false)
                        .build());
            } else {
                // Khong co snapshot: tinh song tu cac cong viec CON LAI trong Sprint.
                // Sau khi dong, chi con viec da DONE o lai, nen cam ket bi bao thieu -> danh dau "uoc tinh".
                List<WorkItem> items = workItemRepository.findBySprintIdAndDeletedAtIsNullOrderByRankAsc(s.getId());
                Map<UUID, WorkItemType> typeById = loadTypes(items);
                Map<UUID, Status> statusById = loadStatusById();
                List<WorkItem> scored = items.stream()
                        .filter(i -> !burndownCalculator.isSubtask(i, typeById)).toList();
                List<WorkItem> done = scored.stream()
                        .filter(i -> burndownCalculator.isDone(i, statusById)).toList();

                points.add(VelocityPointResponse.builder()
                        .sprintId(s.getId()).sprintName(s.getName())
                        .startDate(s.getStartDate()).endDate(s.getEndDate()).completedDate(s.getCompletedDate())
                        .committedStoryPoints(burndownCalculator.sumPoints(scored))
                        .completedStoryPoints(burndownCalculator.sumPoints(done))
                        .committedItems(scored.size()).completedItems(done.size())
                        .estimated(true)
                        .build());
            }
        }

        BigDecimal avgVelocity = average(points.stream().map(VelocityPointResponse::getCompletedStoryPoints).toList());
        BigDecimal avgCommit = average(points.stream().map(VelocityPointResponse::getCommittedStoryPoints).toList());
        BigDecimal reliability = avgCommit.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : avgVelocity.multiply(BigDecimal.valueOf(100)).divide(avgCommit, 2, RoundingMode.HALF_UP);

        return VelocityResponse.builder()
                .projectId(projectId)
                .sprints(points)
                .averageVelocity(avgVelocity)
                .averageCommitment(avgCommit)
                .commitmentReliabilityPercent(reliability)
                .sprintCount(points.size())
                .generatedAt(LocalDateTime.now())
                .build();
    }

    // ─────────────────────────────── CFD ───────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public CfdResponse getCfd(UUID projectId, UUID sprintId) {
        requireProject(projectId);

        Sprint sprint = null;
        List<WorkItem> items;
        LocalDate from;
        LocalDate to;

        if (sprintId != null) {
            sprint = sprintRepository.findById(sprintId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Sprint: " + sprintId));
            items = workItemRepository.findBySprintIdAndDeletedAtIsNullOrderByRankAsc(sprintId);
            from = (sprint.getStartDate() != null ? sprint.getStartDate() : sprint.getCreatedAt()).toLocalDate();
            to = sprint.getEndDate() != null ? sprint.getEndDate().toLocalDate() : LocalDate.now();
        } else {
            items = workItemRepository.findActiveByProjectIdOrderByRankAsc(projectId);
            from = LocalDate.now().minusDays(30);
            to = LocalDate.now();
        }
        if (to.isAfter(LocalDate.now())) to = LocalDate.now();

        Map<UUID, WorkItemType> typeById = loadTypes(items);
        List<WorkItem> scored = items.stream()
                .filter(i -> !burndownCalculator.isSubtask(i, typeById)).toList();

        Map<UUID, ItemStateTimeline> timelines =
                replayer.replay(scored, loadStatusById(), loadStatusByName());

        List<CfdPointResponse> points = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            LocalDateTime ts = day.atTime(23, 59, 59);
            Map<String, Integer> counts = new LinkedHashMap<>();
            counts.put(StatusCategory.TODO.name(), 0);
            counts.put(StatusCategory.IN_PROGRESS.name(), 0);
            counts.put(StatusCategory.DONE.name(), 0);

            int total = 0;
            for (WorkItem item : scored) {
                ItemStateTimeline t = timelines.get(item.getId());
                if (t == null) continue;
                StateEvent st = t.valueAt(ts);
                if (st == null) continue;
                String key = (st.category() == null ? StatusCategory.TODO : st.category()).name();
                counts.merge(key, 1, Integer::sum);
                total++;
            }
            points.add(CfdPointResponse.builder().date(day).counts(counts).total(total).build());
        }

        return CfdResponse.builder()
                .projectId(projectId)
                .sprintId(sprintId)
                .buckets(List.of(
                        new CfdResponse.CfdBucket(StatusCategory.DONE.name(), "Hoàn thành", "#ABF5D1", 0),
                        new CfdResponse.CfdBucket(StatusCategory.IN_PROGRESS.name(), "Đang làm", "#B3D4FF", 1),
                        new CfdResponse.CfdBucket(StatusCategory.TODO.name(), "Cần làm", "#DFE1E6", 2)))
                .points(points)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    // ───────────────────────────── helpers ─────────────────────────────

    private SprintSnapshotData readSnapshot(UUID sprintId) {
        return snapshotRepository.findBySprintId(sprintId)
                .map(SprintReportSnapshot::getSnapshotData)
                .map(raw -> {
                    try {
                        return objectMapper.convertValue(raw, SprintSnapshotData.class);
                    } catch (IllegalArgumentException ex) {
                        log.warn("Khong doc duoc snapshot cua Sprint {}: {}", sprintId, ex.getMessage());
                        return null;
                    }
                })
                .orElse(null);
    }

    private void requireProject(UUID projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Không tìm thấy dự án: " + projectId);
        }
    }

    private Map<UUID, WorkItemType> loadTypes(List<WorkItem> items) {
        var typeIds = items.stream().map(WorkItem::getTypeId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (typeIds.isEmpty()) return Map.of();
        return workItemTypeRepository.findAllById(typeIds).stream()
                .collect(Collectors.toMap(WorkItemType::getId, Function.identity(), (a, b) -> a));
    }

    private Map<UUID, Status> loadStatusById() {
        return statusRepository.findAll().stream()
                .collect(Collectors.toMap(Status::getId, Function.identity(), (a, b) -> a));
    }

    private Map<String, Status> loadStatusByName() {
        Map<String, Status> map = new HashMap<>();
        for (Status s : statusRepository.findAll()) {
            if (s.getName() != null) {
                map.putIfAbsent(s.getName().toLowerCase(Locale.ROOT), s);
            }
        }
        return map;
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private BigDecimal average(List<BigDecimal> values) {
        List<BigDecimal> nonNull = values.stream().filter(Objects::nonNull).toList();
        if (nonNull.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = nonNull.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(nonNull.size()), 2, RoundingMode.HALF_UP);
    }
}
