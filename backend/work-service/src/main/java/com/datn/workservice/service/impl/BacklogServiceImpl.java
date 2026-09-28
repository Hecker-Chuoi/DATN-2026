package com.datn.workservice.service.impl;

import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workservice.dto.request.BacklogFilterRequest;
import com.datn.workservice.dto.response.BacklogGroupResponse;
import com.datn.workservice.dto.response.BacklogResponse;
import com.datn.workservice.dto.response.BoardCardResponse;
import com.datn.workservice.dto.response.EpicOptionResponse;
import com.datn.workservice.dto.response.SprintResponse;
import com.datn.workservice.dto.response.StatusResponse;
import com.datn.workservice.entity.Project;
import com.datn.workservice.entity.Sprint;
import com.datn.workservice.entity.WorkItem;
import com.datn.workservice.enums.SprintState;
import com.datn.workservice.enums.StatusCategory;
import com.datn.workservice.repository.ProjectRepository;
import com.datn.workservice.repository.SprintRepository;
import com.datn.workservice.repository.StatusRepository;
import com.datn.workservice.repository.WorkItemRepository;
import com.datn.workservice.service.BacklogService;
import com.datn.workservice.service.SprintService;
import com.datn.workservice.service.mapper.CardMappingContext;
import com.datn.workservice.service.mapper.WorkItemCardMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BacklogServiceImpl implements BacklogService {

    private final ProjectRepository projectRepository;
    private final SprintRepository sprintRepository;
    private final WorkItemRepository workItemRepository;
    private final StatusRepository statusRepository;
    private final WorkItemCardMapper cardMapper;
    private final SprintService sprintService;

    @Override
    @Transactional(readOnly = true)
    public BacklogResponse getBacklog(UUID projectId, BacklogFilterRequest filter) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dự án: " + projectId));

        // Sprint da dong khong hien tren Backlog - chung thuoc man hinh Bao cao.
        List<Sprint> sprints = sprintRepository
                .findByProjectIdAndStateIn(projectId, EnumSet.of(SprintState.ACTIVE, SprintState.FUTURE))
                .stream()
                .sorted(Comparator
                        .<Sprint, Integer>comparing(s -> s.getState() == SprintState.ACTIVE ? 0 : 1)
                        .thenComparing(s -> s.getStartDate() == null ? LocalDateTime.MAX : s.getStartDate()))
                .toList();
        List<UUID> sprintIds = sprints.stream().map(Sprint::getId).toList();

        // Dung 2 truy van cho toan bo cong viec, roi dung chung mot context anh xa.
        List<WorkItem> sprintItems = sprintIds.isEmpty()
                ? List.of()
                : workItemRepository.findBySprintIdInAndDeletedAtIsNull(sprintIds);
        List<WorkItem> backlogItems =
                workItemRepository.findByProjectIdAndSprintIdIsNullAndDeletedAtIsNullOrderByRankAsc(projectId);

        List<WorkItem> all = new ArrayList<>(sprintItems.size() + backlogItems.size());
        all.addAll(sprintItems);
        all.addAll(backlogItems);

        CardMappingContext ctx = cardMapper.buildContext(all, WorkItemCardMapper.Options.backlog());
        List<BoardCardResponse> cards = cardMapper.mapAll(all, ctx);

        // Sub-task khong dung thanh hang rieng tren Backlog - chung cuon theo cong viec cha.
        List<BoardCardResponse> rows = cards.stream()
                .filter(c -> !Boolean.TRUE.equals(c.getIsSubtask()))
                .filter(c -> matches(c, filter))
                .sorted(Comparator.comparing(BoardCardResponse::getRank,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        Map<UUID, List<BoardCardResponse>> bySprint = rows.stream()
                .filter(c -> c.getSprintId() != null)
                .collect(Collectors.groupingBy(BoardCardResponse::getSprintId,
                        LinkedHashMap::new, Collectors.toList()));
        List<BoardCardResponse> backlogRows = rows.stream()
                .filter(c -> c.getSprintId() == null)
                .toList();

        boolean hasActive = sprints.stream().anyMatch(s -> s.getState() == SprintState.ACTIVE);

        List<BacklogGroupResponse> sprintGroups = sprints.stream()
                .map(s -> toGroup(s, bySprint.getOrDefault(s.getId(), List.of()), hasActive))
                .toList();

        BacklogGroupResponse backlogGroup = toGroup(null, backlogRows, hasActive);

        SprintResponse activeSprint = sprints.stream()
                .filter(s -> s.getState() == SprintState.ACTIVE)
                .findFirst()
                .map(s -> sprintService.getById(s.getId()))
                .orElse(null);

        return BacklogResponse.builder()
                .projectId(projectId)
                .projectKey(project.getKey())
                .activeSprint(activeSprint)
                .sprints(sprintGroups)
                .backlog(backlogGroup)
                .epics(buildEpics(cards))
                .statuses(buildStatuses())
                .totalBacklogItems(backlogRows.size())
                .generatedAt(LocalDateTime.now())
                .build();
    }

    // ───────────────────────────── helpers ─────────────────────────────

    private boolean matches(BoardCardResponse c, BacklogFilterRequest f) {
        if (f == null) return true;

        if (f.getAssigneeId() != null && !f.getAssigneeId().equals(c.getAssigneeId())) {
            return false;
        }
        if (f.getEpicId() != null && !f.getEpicId().equals(c.getEpicId())) {
            return false;
        }
        if (f.getTypeIds() != null && !f.getTypeIds().isEmpty()
                && !f.getTypeIds().contains(c.getTypeId())) {
            return false;
        }
        if (f.getQ() != null && !f.getQ().isBlank()) {
            String needle = f.getQ().trim().toLowerCase(Locale.ROOT);
            String code = c.getCode() == null ? "" : c.getCode().toLowerCase(Locale.ROOT);
            String summary = c.getSummary() == null ? "" : c.getSummary().toLowerCase(Locale.ROOT);
            if (!code.contains(needle) && !summary.contains(needle)) {
                return false;
            }
        }
        return true;
    }

    private BacklogGroupResponse toGroup(Sprint sprint, List<BoardCardResponse> items, boolean projectHasActive) {
        int todo = 0, inProgress = 0, done = 0;
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal todoPts = BigDecimal.ZERO;
        BigDecimal inProgressPts = BigDecimal.ZERO;
        BigDecimal donePts = BigDecimal.ZERO;

        for (BoardCardResponse c : items) {
            StatusCategory cat = c.getStatusCategory() == null ? StatusCategory.TODO : c.getStatusCategory();
            BigDecimal sp = c.getStoryPoints() == null ? BigDecimal.ZERO : c.getStoryPoints();
            total = total.add(sp);
            switch (cat) {
                case DONE -> { done++; donePts = donePts.add(sp); }
                case IN_PROGRESS -> { inProgress++; inProgressPts = inProgressPts.add(sp); }
                default -> { todo++; todoPts = todoPts.add(sp); }
            }
        }

        boolean isBacklog = sprint == null;
        return BacklogGroupResponse.builder()
                .sprintId(isBacklog ? null : sprint.getId())
                .name(isBacklog ? "Backlog" : sprint.getName())
                .goal(isBacklog ? null : sprint.getGoal())
                .state(isBacklog ? null : sprint.getState())
                .startDate(isBacklog ? null : sprint.getStartDate())
                .endDate(isBacklog ? null : sprint.getEndDate())
                .items(items)
                .itemCount(items.size())
                .totalStoryPoints(total)
                .todoStoryPoints(todoPts)
                .inProgressStoryPoints(inProgressPts)
                .doneStoryPoints(donePts)
                .todoCount(todo)
                .inProgressCount(inProgress)
                .doneCount(done)
                .canStart(!isBacklog && sprint.getState() == SprintState.FUTURE
                        && !items.isEmpty() && !projectHasActive)
                .canComplete(!isBacklog && sprint.getState() == SprintState.ACTIVE)
                .build();
    }

    /** Epic suy ra tu chinh cac the da anh xa, khong ton them truy van. */
    private List<EpicOptionResponse> buildEpics(List<BoardCardResponse> cards) {
        Map<UUID, EpicOptionResponse> epics = new LinkedHashMap<>();
        Map<UUID, int[]> counts = new LinkedHashMap<>();
        Map<UUID, BigDecimal[]> points = new LinkedHashMap<>();

        for (BoardCardResponse c : cards) {
            UUID epicId = c.getEpicId();
            if (epicId == null) continue;

            epics.computeIfAbsent(epicId, id -> EpicOptionResponse.builder()
                    .id(id)
                    .code(c.getEpicCode())
                    .summary(c.getEpicSummary())
                    .colorHex(c.getEpicColor())
                    .build());

            counts.computeIfAbsent(epicId, id -> new int[1])[0]++;
            BigDecimal sp = c.getStoryPoints() == null ? BigDecimal.ZERO : c.getStoryPoints();
            BigDecimal[] p = points.computeIfAbsent(epicId, id -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            p[0] = p[0].add(sp);
            if (c.getStatusCategory() == StatusCategory.DONE) {
                p[1] = p[1].add(sp);
            }
        }

        epics.forEach((id, e) -> {
            e.setChildCount(counts.getOrDefault(id, new int[1])[0]);
            BigDecimal[] p = points.getOrDefault(id, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            e.setTotalStoryPoints(p[0]);
            e.setDoneStoryPoints(p[1]);
        });
        return new ArrayList<>(epics.values());
    }

    private List<StatusResponse> buildStatuses() {
        return statusRepository.findAll().stream()
                .sorted(Comparator.comparing(s -> s.getCategory() == null ? 99 : s.getCategory().ordinal()))
                .map(s -> StatusResponse.builder()
                        .id(s.getId())
                        .name(s.getName())
                        .category(s.getCategory())
                        .description(s.getDescription())
                        .colorHex(s.getColorHex())
                        .createdAt(s.getCreatedAt())
                        .build())
                .toList();
    }
}
