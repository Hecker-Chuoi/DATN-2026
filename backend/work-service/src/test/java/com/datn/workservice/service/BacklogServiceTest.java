package com.datn.workservice.service;

import com.datn.workservice.dto.response.BacklogGroupResponse;
import com.datn.workservice.dto.response.BacklogResponse;
import com.datn.workservice.dto.response.BoardCardResponse;
import com.datn.workservice.entity.Project;
import com.datn.workservice.entity.Sprint;
import com.datn.workservice.entity.SprintReportSnapshot;
import com.datn.workservice.entity.WorkItem;
import com.datn.workservice.enums.SprintState;
import com.datn.workservice.enums.StatusCategory;
import com.datn.workservice.repository.ProjectRepository;
import com.datn.workservice.repository.SprintReportSnapshotRepository;
import com.datn.workservice.repository.SprintRepository;
import com.datn.workservice.repository.StatusRepository;
import com.datn.workservice.repository.WorkItemRepository;
import com.datn.workservice.service.impl.BacklogServiceImpl;
import com.datn.workservice.service.mapper.WorkItemCardMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** BR-B08: giai doan da hoan tat van hien tren man Backlog, tach khoi cac khoi ke hoach. */
@ExtendWith(MockitoExtension.class)
class BacklogServiceTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private SprintRepository sprintRepository;
    @Mock private WorkItemRepository workItemRepository;
    @Mock private StatusRepository statusRepository;
    @Mock private WorkItemCardMapper cardMapper;
    @Mock private SprintService sprintService;
    @Mock private SprintReportSnapshotRepository snapshotRepository;

    @InjectMocks private BacklogServiceImpl backlogService;

    private final UUID projectId = UUID.randomUUID();
    private final LocalDateTime base = LocalDateTime.of(2026, 9, 1, 0, 0);

    private Sprint future;
    private Sprint closedOld;
    private Sprint closedRecent;

    @BeforeEach
    void setUp() {
        future = sprint("Giai đoạn 3", SprintState.FUTURE, base.plusDays(60), null);
        closedOld = sprint("Giai đoạn 1", SprintState.CLOSED, base, base.plusDays(14));
        closedRecent = sprint("Giai đoạn 2", SprintState.CLOSED, base.plusDays(15), base.plusDays(30));

        when(projectRepository.findById(projectId))
                .thenReturn(Optional.of(Project.builder().id(projectId).key("MOC").build()));
        when(sprintRepository.findByProjectIdAndStateIn(eq(projectId), anyCollection()))
                .thenReturn(List.of(future));
        when(sprintRepository.findByProjectIdAndState(projectId, SprintState.CLOSED))
                .thenReturn(List.of(closedOld, closedRecent));
        when(workItemRepository.findBySprintIdInAndDeletedAtIsNull(anyList())).thenReturn(List.of());
        when(workItemRepository.findByProjectIdAndSprintIdIsNullAndDeletedAtIsNullOrderByRankAsc(projectId))
                .thenReturn(List.of());
        when(statusRepository.findByProjectIdOrProjectIdIsNull(projectId)).thenReturn(List.of());

        // Moi Sprint da dong con lai 1 viec DONE; Sprint FUTURE co 1 viec TODO.
        when(cardMapper.mapAll(anyList(), any())).thenReturn(List.of(
                card(future.getId(), StatusCategory.TODO, "0|a"),
                card(closedOld.getId(), StatusCategory.DONE, "0|b"),
                card(closedRecent.getId(), StatusCategory.DONE, "0|c")));

        when(snapshotRepository.findBySprintIdIn(anyCollection())).thenReturn(List.of(
                SprintReportSnapshot.builder().sprintId(closedRecent.getId())
                        .totalItems(5).completedItems(4)
                        .totalStoryPoints(new BigDecimal("13")).completedStoryPoints(new BigDecimal("10"))
                        .build()));
    }

    @Test
    @DisplayName("Giai đoạn đã hoàn tất nằm ở closedSprints (mới nhất trước), không lẫn vào khối kế hoạch")
    void closedSprintsAreKeptSeparately() {
        BacklogResponse res = backlogService.getBacklog(projectId, null);

        assertThat(res.getSprints()).extracting(BacklogGroupResponse::getSprintId)
                .containsExactly(future.getId());
        assertThat(res.getClosedSprints()).extracting(BacklogGroupResponse::getSprintId)
                .containsExactly(closedRecent.getId(), closedOld.getId());

        BacklogGroupResponse recent = res.getClosedSprints().get(0);
        assertThat(recent.getState()).isEqualTo(SprintState.CLOSED);
        assertThat(recent.getItemCount()).isEqualTo(1);
        assertThat(recent.getCompletedDate()).isEqualTo(closedRecent.getCompletedDate());
        assertThat(recent.getCanStart()).isFalse();
        assertThat(recent.getCanComplete()).isFalse();
    }

    @Test
    @DisplayName("Khối đã hoàn tất mang số liệu chốt từ snapshot; thiếu snapshot thì để trống")
    void closedSprintCarriesSnapshotFigures() {
        BacklogResponse res = backlogService.getBacklog(projectId, null);

        BacklogGroupResponse recent = res.getClosedSprints().get(0);
        assertThat(recent.getItemsAtClose()).isEqualTo(5);
        assertThat(recent.getCompletedItemsAtClose()).isEqualTo(4);
        assertThat(recent.getCompletedStoryPointsAtClose()).isEqualByComparingTo("10");

        BacklogGroupResponse old = res.getClosedSprints().get(1);
        assertThat(old.getItemsAtClose()).isNull();
    }

    @Test
    @DisplayName("Giai đoạn đã đóng không làm lệch quyền Bắt đầu của giai đoạn kế tiếp (BR-S15)")
    void closedSprintsDoNotAffectStartable() {
        BacklogResponse res = backlogService.getBacklog(projectId, null);

        assertThat(res.getSprints().get(0).getCanStart()).isTrue();
    }

    private Sprint sprint(String name, SprintState state, LocalDateTime start, LocalDateTime completed) {
        return Sprint.builder()
                .id(UUID.randomUUID())
                .projectId(projectId)
                .name(name)
                .state(state)
                .startDate(start)
                .endDate(start.plusDays(14))
                .completedDate(completed)
                .createdAt(start)
                .build();
    }

    private BoardCardResponse card(UUID sprintId, StatusCategory category, String rank) {
        return BoardCardResponse.builder()
                .id(UUID.randomUUID())
                .sprintId(sprintId)
                .statusCategory(category)
                .storyPoints(BigDecimal.ONE)
                .hierarchyLevel(0)
                .isSubtask(false)
                .rank(rank)
                .build();
    }
}
