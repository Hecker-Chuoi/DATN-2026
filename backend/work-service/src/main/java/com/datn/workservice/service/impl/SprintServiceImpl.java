package com.datn.workservice.service.impl;

import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ConflictException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workservice.dto.request.CompleteSprintRequest;
import com.datn.workservice.dto.request.CreateSprintRequest;
import com.datn.workservice.dto.request.StartSprintRequest;
import com.datn.workservice.dto.request.UpdateSprintRequest;
import com.datn.workservice.dto.response.BoardCardResponse;
import com.datn.workservice.dto.response.SprintCompletionResponse;
import com.datn.workservice.dto.response.SprintResponse;
import com.datn.workservice.entity.Project;
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
import com.datn.workservice.service.LexoRankService;
import com.datn.workservice.service.OutboxRecorder;
import com.datn.workservice.service.SprintService;
import com.datn.workservice.service.WorkItemAuditService;
import com.datn.workservice.service.mapper.CardMappingContext;
import com.datn.workservice.service.mapper.WorkItemCardMapper;
import com.datn.workservice.dto.response.BurndownPointResponse;
import com.datn.workservice.service.report.BurndownCalculator;
import com.datn.workservice.service.report.SprintReportSnapshotEngine;
import com.datn.workservice.service.report.WorkItemStateReplayer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SprintServiceImpl implements SprintService {

    /** Jira canh bao khi Sprint dai bat thuong; 8 tuan la nguong hop ly. */
    private static final long MAX_SPRINT_DAYS = 56;

    private final SprintRepository sprintRepository;
    private final SprintReportSnapshotRepository snapshotRepository;
    private final ProjectRepository projectRepository;
    private final WorkItemRepository workItemRepository;
    private final WorkItemTypeRepository workItemTypeRepository;
    private final StatusRepository statusRepository;
    private final LexoRankService lexoRankService;
    private final WorkItemAuditService auditService;
    private final WorkItemCardMapper cardMapper;
    private final SprintReportSnapshotEngine snapshotEngine;
    private final WorkItemStateReplayer stateReplayer;
    private final BurndownCalculator burndownCalculator;
    private final OutboxRecorder outboxRecorder;
    private final com.datn.workservice.service.ContributionReportService contributionReportService;

    // ───────────────────────────── CRUD ─────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<SprintResponse> listByProject(UUID projectId, Set<SprintState> states) {
        requireProject(projectId);
        List<Sprint> sprints = (states == null || states.isEmpty())
                ? sprintRepository.findByProjectId(projectId)
                : sprintRepository.findByProjectIdAndStateIn(projectId, states);

        boolean hasActive = sprints.stream().anyMatch(s -> s.getState() == SprintState.ACTIVE);
        List<Sprint> ordered = sprints.stream().sorted(sprintOrder()).toList();

        Map<UUID, List<WorkItem>> itemsBySprint = loadItemsBySprint(
                ordered.stream().map(Sprint::getId).toList());
        Map<UUID, Status> statusById = loadStatusMap();
        Map<UUID, WorkItemType> typeById = loadTypeMap(itemsBySprint.values().stream()
                .flatMap(List::stream).toList());
        Set<UUID> withSnapshot = loadSnapshotSprintIds(ordered);

        return ordered.stream()
                .map(s -> toResponse(s, itemsBySprint.getOrDefault(s.getId(), List.of()),
                        statusById, typeById, hasActive, withSnapshot.contains(s.getId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SprintResponse getById(UUID sprintId) {
        Sprint sprint = requireSprint(sprintId);
        List<WorkItem> items = workItemRepository.findBySprintIdAndDeletedAtIsNullOrderByRankAsc(sprintId);
        boolean hasActive = sprintRepository
                .findFirstByProjectIdAndState(sprint.getProjectId(), SprintState.ACTIVE).isPresent();
        return toResponse(sprint, items, loadStatusMap(), loadTypeMap(items), hasActive,
                snapshotRepository.existsBySprintId(sprintId));
    }

    @Override
    @Transactional
    public SprintResponse create(UUID projectId, CreateSprintRequest request, UUID userId) {
        requireProject(projectId);
        validateDateRange(request.getStartDate(), request.getEndDate());

        Sprint sprint = Sprint.builder()
                .projectId(projectId)
                .name(request.getName().trim())
                .goal(request.getGoal())
                .state(SprintState.FUTURE)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .build();
        sprint = sprintRepository.save(sprint);
        log.info("Tao Sprint '{}' cho du an {}", sprint.getName(), projectId);

        return toResponse(sprint, List.of(), loadStatusMap(), Map.of(),
                sprintRepository.findFirstByProjectIdAndState(projectId, SprintState.ACTIVE).isPresent(),
                false);
    }

    @Override
    @Transactional
    public SprintResponse update(UUID sprintId, UpdateSprintRequest request, UUID userId) {
        Sprint sprint = requireSprint(sprintId);
        if (sprint.getState() == SprintState.CLOSED) {
            throw new BadRequestException("Không thể chỉnh sửa một Sprint đã đóng");
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            sprint.setName(request.getName().trim());
        }
        if (request.getGoal() != null) {
            sprint.setGoal(request.getGoal());
        }
        LocalDateTime start = request.getStartDate() != null ? request.getStartDate() : sprint.getStartDate();
        LocalDateTime end = request.getEndDate() != null ? request.getEndDate() : sprint.getEndDate();
        validateDateRange(start, end);
        sprint.setStartDate(start);
        sprint.setEndDate(end);

        sprint = sprintRepository.save(sprint);

        List<WorkItem> items = workItemRepository.findBySprintIdAndDeletedAtIsNullOrderByRankAsc(sprintId);
        return toResponse(sprint, items, loadStatusMap(), loadTypeMap(items), true,
                snapshotRepository.existsBySprintId(sprintId));
    }

    @Override
    @Transactional
    public void delete(UUID sprintId, UUID userId) {
        Sprint sprint = requireSprint(sprintId);
        if (sprint.getState() != SprintState.FUTURE) {
            throw new BadRequestException(
                    "Chỉ có thể xoá Sprint chưa bắt đầu. Sprint đang chạy cần được đóng, Sprint đã đóng phải giữ lại để lưu vết báo cáo.");
        }

        // Cong viec ben trong tro ve Backlog thay vi bi xoa theo.
        List<WorkItem> items = workItemRepository.findBySprintIdAndDeletedAtIsNullOrderByRankAsc(sprintId);
        for (WorkItem item : items) {
            item.setSprintId(null);
            auditService.recordCustomEvent(item.getId(), "SPRINT", sprint.getName(), null, userId);
            auditService.recordCustomEvent(item.getId(), "SPRINT_SCOPE", sprintId.toString(), null, userId);
        }
        workItemRepository.saveAll(items);
        sprintRepository.delete(sprint);
        log.info("Xoa Sprint '{}', tra {} cong viec ve Backlog", sprint.getName(), items.size());
    }

    // ─────────────────────────── Lifecycle ───────────────────────────

    @Override
    @Transactional
    public SprintResponse startSprint(UUID sprintId, StartSprintRequest request, UUID userId) {
        Sprint sprint = requireSprint(sprintId);

        if (sprint.getState() != SprintState.FUTURE) {
            throw new BadRequestException(
                    "Chỉ có thể bắt đầu Sprint đang ở trạng thái Dự kiến (hiện tại: " + sprint.getState() + ")");
        }
        Project project = requireProject(sprint.getProjectId());
        if (project.getDeletedAt() != null) {
            throw new BadRequestException("Dự án đã bị lưu trữ");
        }
        // Du an mau chi la ban thiet ke: sprint cua mau luon o FUTURE de nhan ban ra du an that.
        if (Boolean.TRUE.equals(project.getIsTemplate())) {
            throw new BadRequestException("Không thể bắt đầu Sprint trong dự án mẫu. Hãy tạo dự án từ mẫu rồi bắt đầu Sprint ở dự án đó.");
        }

        // Moi du an chi mot Sprint ACTIVE. Unique index o V2 chan not truong hop chay dua.
        sprintRepository.findFirstByProjectIdAndState(sprint.getProjectId(), SprintState.ACTIVE)
                .ifPresent(active -> {
                    throw new ConflictException("Dự án đã có Sprint đang chạy: '" + active.getName()
                            + "'. Hãy đóng Sprint đó trước khi bắt đầu Sprint mới.");
                });

        LocalDateTime start = firstNonNull(request == null ? null : request.getStartDate(),
                sprint.getStartDate(), LocalDateTime.now());
        LocalDateTime end = firstNonNull(request == null ? null : request.getEndDate(), sprint.getEndDate());
        if (end == null) {
            throw new BadRequestException("Ngày kết thúc Sprint là bắt buộc để theo dõi tiến độ Burndown");
        }
        if (!end.isAfter(start)) {
            throw new BadRequestException("Ngày kết thúc phải sau ngày bắt đầu");
        }
        if (Duration.between(start, end).toDays() > MAX_SPRINT_DAYS) {
            throw new BadRequestException("Sprint không nên kéo dài quá 8 tuần");
        }

        boolean allowEmpty = request != null && Boolean.TRUE.equals(request.getAllowEmpty());
        long itemCount = workItemRepository.countBySprintIdAndDeletedAtIsNull(sprintId);
        if (!allowEmpty && itemCount == 0) {
            throw new BadRequestException(
                    "Không thể bắt đầu một Sprint rỗng. Hãy kéo công việc vào Sprint trước.");
        }

        sprint.setState(SprintState.ACTIVE);
        sprint.setStartDate(start);
        sprint.setEndDate(end);
        if (request != null && request.getGoal() != null && !request.getGoal().isBlank()) {
            sprint.setGoal(request.getGoal());
        }
        sprint = sprintRepository.save(sprint);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", sprint.getProjectId());
        payload.put("sprintId", sprint.getId());
        payload.put("name", sprint.getName());
        payload.put("goal", sprint.getGoal());
        payload.put("startDate", sprint.getStartDate());
        payload.put("endDate", sprint.getEndDate());
        payload.put("itemCount", itemCount);
        payload.put("startedBy", userId);
        outboxRecorder.record("SPRINT", sprint.getId(), "SPRINT_STARTED", payload);

        log.info("Bat dau Sprint '{}' ({} cong viec)", sprint.getName(), itemCount);

        List<WorkItem> items = workItemRepository.findBySprintIdAndDeletedAtIsNullOrderByRankAsc(sprintId);
        return toResponse(sprint, items, loadStatusMap(), loadTypeMap(items), true, false);
    }

    @Override
    @Transactional
    public SprintCompletionResponse completeSprint(UUID sprintId, CompleteSprintRequest request, UUID userId) {
        Sprint sprint = requireSprint(sprintId);
        if (sprint.getState() != SprintState.ACTIVE) {
            throw new BadRequestException("Chỉ Sprint đang chạy mới có thể đóng (hiện tại: "
                    + sprint.getState() + ")");
        }

        // 1. Xac dinh noi chuyen cong viec chua hoan thanh
        UUID targetSprintId = null;
        String targetSprintName = "Backlog";
        String raw = request == null ? null : request.getTargetSprintIdForIncomplete();
        if (raw != null && !raw.isBlank() && !"BACKLOG".equalsIgnoreCase(raw.trim())) {
            UUID parsed;
            try {
                parsed = UUID.fromString(raw.trim());
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("Sprint đích không hợp lệ: " + raw);
            }
            Sprint target = sprintRepository.findById(parsed)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Sprint đích: " + raw));
            if (!Objects.equals(target.getProjectId(), sprint.getProjectId())) {
                throw new BadRequestException("Sprint đích không thuộc dự án này");
            }
            if (Objects.equals(target.getId(), sprintId)) {
                throw new BadRequestException("Sprint đích trùng với Sprint đang đóng");
            }
            if (target.getState() == SprintState.CLOSED) {
                throw new BadRequestException("Không thể chuyển công việc sang một Sprint đã đóng");
            }
            targetSprintId = target.getId();
            targetSprintName = target.getName();
        }

        // 2. Phan loai theo nhom trang thai DONE
        List<WorkItem> items = workItemRepository.findBySprintIdAndDeletedAtIsNullOrderByRankAsc(sprintId);
        Set<UUID> doneStatusIds = statusRepository.findByCategory(StatusCategory.DONE).stream()
                .map(Status::getId).collect(Collectors.toSet());

        List<WorkItem> completed = items.stream()
                .filter(i -> doneStatusIds.contains(i.getStatusId())).toList();
        List<WorkItem> incomplete = items.stream()
                .filter(i -> !doneStatusIds.contains(i.getStatusId())).toList();

        // 3. Chup snapshot TRUOC khi di chuyen - so lieu phai mo ta Sprint dung luc no ket thuc.
        SprintReportSnapshot snapshot = snapshotEngine.generateFinalSnapshot(
                sprint, items, doneStatusIds, userId, targetSprintId, targetSprintName,
                buildBurndownSeries(sprint, items));

        // 4. Chuyen cong viec chua xong. Cong viec DONE GIU NGUYEN sprintId de velocity con dung.
        int cascaded = moveItemsToSprint(incomplete, targetSprintId, targetSprintName,
                sprint.getName(), userId);

        // 5. Dong Sprint
        sprint.setState(SprintState.CLOSED);
        sprint.setCompletedDate(LocalDateTime.now());
        sprint = sprintRepository.save(sprint);

        Map<UUID, WorkItemType> typeById = loadTypeMap(items);
        BigDecimal completedPoints = sumPoints(completed, typeById);
        BigDecimal incompletePoints = sumPoints(incomplete, typeById);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", sprint.getProjectId());
        payload.put("sprintId", sprint.getId());
        payload.put("name", sprint.getName());
        payload.put("completedItems", completed.size());
        payload.put("incompleteItems", incomplete.size());
        payload.put("completedStoryPoints", completedPoints);
        payload.put("movedToSprintId", targetSprintId);
        payload.put("closedBy", userId);
        outboxRecorder.record("SPRINT", sprint.getId(), "SPRINT_COMPLETED", payload);

        // Đóng băng số liệu đóng góp của Sprint vào snapshot
        try {
            contributionReportService.freezeSprintContributionSnapshot(sprint.getId(), sprint.getProjectId());
        } catch (Exception e) {
            log.warn("Không thể đóng băng snapshot đóng góp cho Sprint '{}': {}", sprint.getId(), e.getMessage());
        }

        log.info("Dong Sprint '{}': {} xong / {} chua xong -> {}",
                sprint.getName(), completed.size(), incomplete.size(), targetSprintName);

        return SprintCompletionResponse.builder()
                .sprintId(sprint.getId())
                .sprintName(sprint.getName())
                .state(sprint.getState())
                .completedDate(sprint.getCompletedDate())
                .completedItems(completed.size())
                .incompleteItems(incomplete.size())
                .completedStoryPoints(completedPoints)
                .incompleteStoryPoints(incompletePoints)
                .totalStoryPoints(completedPoints.add(incompletePoints))
                .movedToSprintId(targetSprintId)
                .movedToSprintName(targetSprintName)
                .movedItemCount(incomplete.size())
                .cascadedSubtaskCount(cascaded)
                .snapshotId(snapshot == null ? null : snapshot.getId())
                .build();
    }

    /**
     * Dung chuoi Burndown de dong bang vao snapshot.
     *
     * Loi o day khong duoc lam hong viec dong Sprint: bao cao co the dung lai tu changelog
     * sau nay, con Sprint thi phai dong duoc.
     */
    private List<BurndownPointResponse> buildBurndownSeries(Sprint sprint, List<WorkItem> items) {
        try {
            if (sprint.getEndDate() == null) {
                return List.of();
            }
            Map<UUID, WorkItemType> typeById = loadTypeMap(items);
            List<WorkItem> scored = items.stream()
                    .filter(i -> !burndownCalculator.isSubtask(i, typeById))
                    .toList();
            if (scored.isEmpty()) {
                return List.of();
            }
            Map<UUID, Status> statusById = loadStatusMap();
            Map<String, Status> statusByName = new java.util.HashMap<>();
            for (Status st : statusById.values()) {
                if (st.getName() != null) {
                    statusByName.putIfAbsent(st.getName().toLowerCase(java.util.Locale.ROOT), st);
                }
            }
            var timelines = stateReplayer.replay(scored, statusById, statusByName);
            return burndownCalculator.buildSeries(sprint, scored, timelines, LocalDateTime.now(), true);
        } catch (Exception ex) {
            log.warn("Khong dung duoc chuoi Burndown cho Sprint {}: {}", sprint.getName(), ex.getMessage());
            return List.of();
        }
    }

    // ─────────────────────── Them / bot cong viec ───────────────────────

    @Override
    @Transactional
    public List<BoardCardResponse> addItemsToSprint(UUID sprintId, List<UUID> itemIds, UUID userId) {
        Sprint sprint = requireSprint(sprintId);
        if (sprint.getState() == SprintState.CLOSED) {
            throw new BadRequestException("Không thể thêm công việc vào Sprint đã đóng");
        }
        List<WorkItem> items = loadItemsOrThrow(itemIds);

        List<WorkItem> changed = new ArrayList<>();
        for (WorkItem item : items) {
            if (!Objects.equals(item.getProjectId(), sprint.getProjectId())) {
                throw new BadRequestException("Công việc " + item.getCode() + " không thuộc dự án của Sprint này");
            }
            if (item.getDeletedAt() != null) continue;
            if (Objects.equals(item.getSprintId(), sprintId)) continue; // idempotent
            changed.add(item);
        }
        moveItemsToSprint(changed, sprintId, sprint.getName(), null, userId);
        return toCards(changed);
    }

    @Override
    @Transactional
    public List<BoardCardResponse> removeItemsFromSprint(UUID sprintId, List<UUID> itemIds, UUID userId) {
        Sprint sprint = requireSprint(sprintId);
        if (sprint.getState() == SprintState.CLOSED) {
            throw new BadRequestException("Không thể thay đổi công việc của Sprint đã đóng");
        }
        List<WorkItem> items = loadItemsOrThrow(itemIds);

        List<WorkItem> changed = new ArrayList<>();
        for (WorkItem item : items) {
            if (item.getDeletedAt() != null) continue;
            if (!Objects.equals(item.getSprintId(), sprintId)) {
                throw new BadRequestException("Công việc " + item.getCode() + " không thuộc Sprint này");
            }
            changed.add(item);
        }
        moveItemsToSprint(changed, null, "Backlog", sprint.getName(), userId);
        return toCards(changed);
    }

    // ───────────────────────────── helpers ─────────────────────────────

    /**
     * Doi sprintId cho danh sach cong viec, tinh lai rank o pham vi dich va lan truyen
     * xuong cong viec con de giu bat bien "Sub-task luon cung Sprint voi cha".
     *
     * @return so cong viec con bi keo theo
     */
    private int moveItemsToSprint(List<WorkItem> items, UUID targetSprintId,
                                  String targetSprintName, String fromSprintName, UUID userId) {
        if (items.isEmpty()) return 0;

        UUID projectId = items.get(0).getProjectId();
        String lastRank = lastRankInScope(projectId, targetSprintId);

        for (WorkItem item : items) {
            // Phai doc TRUOC khi ghi de, de con biet Sprint cu la gi.
            UUID previousSprintId = item.getSprintId();
            String oldName = fromSprintName != null ? fromSprintName : resolveSprintName(previousSprintId);

            item.setSprintId(targetSprintId);
            lastRank = (lastRank == null)
                    ? lexoRankService.calculateInitialRank()
                    : lexoRankService.calculateAfter(lastRank);
            item.setRank(lastRank);

            // Ghi hai dong: "SPRINT" (ten, hien tren tab Lich su) va "SPRINT_SCOPE" (UUID, cho bao cao).
            auditService.recordCustomEvent(item.getId(), "SPRINT", oldName, targetSprintName, userId);
            auditService.recordCustomEvent(item.getId(), "SPRINT_SCOPE",
                    idOrNull(previousSprintId), idOrNull(targetSprintId), userId);
        }
        workItemRepository.saveAll(items);

        return cascadeToDescendants(items, targetSprintId, targetSprintName, userId);
    }

    /**
     * Keo cong viec con theo cha. Chay toi da 2 luot vi he thong chi co 3 tang
     * (Epic -> Story/Task -> Sub-task).
     */
    private int cascadeToDescendants(List<WorkItem> parents, UUID targetSprintId,
                                     String targetSprintName, UUID userId) {
        int total = 0;
        List<UUID> frontier = parents.stream().map(WorkItem::getId).toList();

        for (int depth = 0; depth < 2 && !frontier.isEmpty(); depth++) {
            List<WorkItem> children = workItemRepository.findByParentIdInAndDeletedAtIsNull(frontier);
            List<WorkItem> toUpdate = children.stream()
                    .filter(c -> !Objects.equals(c.getSprintId(), targetSprintId))
                    .toList();
            for (WorkItem child : toUpdate) {
                String oldName = resolveSprintName(child.getSprintId());
                UUID previous = child.getSprintId();
                child.setSprintId(targetSprintId);
                auditService.recordCustomEvent(child.getId(), "SPRINT", oldName, targetSprintName, userId);
                auditService.recordCustomEvent(child.getId(), "SPRINT_SCOPE",
                        idOrNull(previous), idOrNull(targetSprintId), userId);
            }
            if (!toUpdate.isEmpty()) {
                workItemRepository.saveAll(toUpdate);
                total += toUpdate.size();
            }
            frontier = children.stream().map(WorkItem::getId).toList();
        }
        return total;
    }

    private String idOrNull(UUID id) {
        return id == null ? null : id.toString();
    }

    private String lastRankInScope(UUID projectId, UUID sprintId) {
        List<WorkItem> scope = sprintId != null
                ? workItemRepository.findBySprintIdAndDeletedAtIsNullOrderByRankAsc(sprintId)
                : workItemRepository.findByProjectIdAndSprintIdIsNullAndDeletedAtIsNullOrderByRankAsc(projectId);
        return scope.isEmpty() ? null : scope.get(scope.size() - 1).getRank();
    }

    private List<WorkItem> loadItemsOrThrow(List<UUID> itemIds) {
        List<WorkItem> items = workItemRepository.findAllById(itemIds);
        if (items.size() != itemIds.size()) {
            Set<UUID> found = items.stream().map(WorkItem::getId).collect(Collectors.toSet());
            List<UUID> missing = itemIds.stream().filter(id -> !found.contains(id)).toList();
            throw new ResourceNotFoundException("Không tìm thấy công việc: " + missing);
        }
        return items;
    }

    private List<BoardCardResponse> toCards(List<WorkItem> items) {
        if (items.isEmpty()) return List.of();
        CardMappingContext ctx = cardMapper.buildContext(items, WorkItemCardMapper.Options.backlog());
        return cardMapper.mapAll(items, ctx);
    }

    private Project requireProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dự án: " + projectId));
    }

    private Sprint requireSprint(UUID sprintId) {
        return sprintRepository.findById(sprintId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Sprint: " + sprintId));
    }

    private void validateDateRange(LocalDateTime start, LocalDateTime end) {
        if (start != null && end != null && !end.isAfter(start)) {
            throw new BadRequestException("Ngày kết thúc phải sau ngày bắt đầu");
        }
    }

    private String resolveSprintName(UUID sprintId) {
        if (sprintId == null) return "Backlog";
        return sprintRepository.findById(sprintId).map(Sprint::getName).orElse("Backlog");
    }

    @SafeVarargs
    private <T> T firstNonNull(T... values) {
        for (T v : values) {
            if (v != null) return v;
        }
        return null;
    }

    private Map<UUID, Status> loadStatusMap() {
        return statusRepository.findAll().stream()
                .collect(Collectors.toMap(Status::getId, Function.identity(), (a, b) -> a));
    }

    private Map<UUID, WorkItemType> loadTypeMap(List<WorkItem> items) {
        Set<UUID> typeIds = items.stream().map(WorkItem::getTypeId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (typeIds.isEmpty()) return Collections.emptyMap();
        return workItemTypeRepository.findAllById(typeIds).stream()
                .collect(Collectors.toMap(WorkItemType::getId, Function.identity(), (a, b) -> a));
    }

    private Map<UUID, List<WorkItem>> loadItemsBySprint(List<UUID> sprintIds) {
        if (sprintIds.isEmpty()) return Collections.emptyMap();
        return workItemRepository.findBySprintIdInAndDeletedAtIsNull(sprintIds).stream()
                .collect(Collectors.groupingBy(WorkItem::getSprintId));
    }

    private Set<UUID> loadSnapshotSprintIds(List<Sprint> sprints) {
        List<UUID> closedIds = sprints.stream()
                .filter(s -> s.getState() == SprintState.CLOSED)
                .map(Sprint::getId).toList();
        if (closedIds.isEmpty()) return Collections.emptySet();
        return snapshotRepository.findBySprintIdIn(closedIds).stream()
                .map(SprintReportSnapshot::getSprintId).collect(Collectors.toSet());
    }

    /** Sub-task khong cong diem rieng: diem cua chung da nam o Story/Task cha. */
    private BigDecimal sumPoints(List<WorkItem> items, Map<UUID, WorkItemType> typeById) {
        return items.stream()
                .filter(i -> {
                    WorkItemType t = i.getTypeId() == null ? null : typeById.get(i.getTypeId());
                    return t == null || !Boolean.TRUE.equals(t.getIsSubtask());
                })
                .map(WorkItem::getStoryPoints)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private java.util.Comparator<Sprint> sprintOrder() {
        // ACTIVE truoc, roi FUTURE theo ngay bat dau, cuoi cung CLOSED moi nhat len truoc.
        Map<SprintState, Integer> weight = Map.of(
                SprintState.ACTIVE, 0, SprintState.FUTURE, 1, SprintState.CLOSED, 2);
        return java.util.Comparator
                .<Sprint, Integer>comparing(s -> weight.getOrDefault(s.getState(), 3))
                .thenComparing(s -> s.getStartDate() == null ? LocalDateTime.MAX : s.getStartDate());
    }

    private SprintResponse toResponse(Sprint sprint, List<WorkItem> items,
                                      Map<UUID, Status> statusById,
                                      Map<UUID, WorkItemType> typeById,
                                      boolean projectHasActiveSprint,
                                      boolean hasSnapshot) {
        int todo = 0, inProgress = 0, done = 0;
        BigDecimal totalPoints = BigDecimal.ZERO;
        BigDecimal donePoints = BigDecimal.ZERO;

        for (WorkItem item : items) {
            Status st = item.getStatusId() == null ? null : statusById.get(item.getStatusId());
            StatusCategory cat = st == null ? StatusCategory.TODO : st.getCategory();
            if (cat == StatusCategory.DONE) done++;
            else if (cat == StatusCategory.IN_PROGRESS) inProgress++;
            else todo++;

            WorkItemType t = item.getTypeId() == null ? null : typeById.get(item.getTypeId());
            boolean isSub = t != null && Boolean.TRUE.equals(t.getIsSubtask());
            BigDecimal sp = item.getStoryPoints();
            if (!isSub && sp != null) {
                totalPoints = totalPoints.add(sp);
                if (cat == StatusCategory.DONE) donePoints = donePoints.add(sp);
            }
        }

        Integer daysRemaining = null;
        Boolean overdue = null;
        if (sprint.getEndDate() != null) {
            long d = java.time.temporal.ChronoUnit.DAYS.between(
                    LocalDate.now(), sprint.getEndDate().toLocalDate());
            daysRemaining = (int) d;
            overdue = sprint.getState() == SprintState.ACTIVE && d < 0;
        }

        boolean canStart = sprint.getState() == SprintState.FUTURE
                && !items.isEmpty()
                && !projectHasActiveSprint;

        return SprintResponse.builder()
                .id(sprint.getId())
                .projectId(sprint.getProjectId())
                .name(sprint.getName())
                .goal(sprint.getGoal())
                .state(sprint.getState())
                .startDate(sprint.getStartDate())
                .endDate(sprint.getEndDate())
                .completedDate(sprint.getCompletedDate())
                .totalItems(items.size())
                .completedItems(done)
                .totalStoryPoints(totalPoints)
                .completedStoryPoints(donePoints)
                .remainingStoryPoints(totalPoints.subtract(donePoints))
                .todoItems(todo)
                .inProgressItems(inProgress)
                .doneItems(done)
                .daysRemaining(daysRemaining)
                .isOverdue(overdue)
                .hasSnapshot(hasSnapshot)
                .canStart(canStart)
                .canComplete(sprint.getState() == SprintState.ACTIVE)
                .version(sprint.getVersion())
                .createdAt(sprint.getCreatedAt())
                .updatedAt(sprint.getUpdatedAt())
                .build();
    }
}
