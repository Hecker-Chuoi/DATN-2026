package com.datn.workservice.service;

import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ConflictException;
import com.datn.workservice.dto.request.CompleteSprintRequest;
import com.datn.workservice.dto.request.StartSprintRequest;
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
import com.datn.workservice.service.impl.SprintServiceImpl;
import com.datn.workservice.service.mapper.WorkItemCardMapper;
import com.datn.workservice.service.report.SprintReportSnapshotEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SprintServiceTest {

    @Mock private SprintRepository sprintRepository;
    @Mock private SprintReportSnapshotRepository snapshotRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private WorkItemRepository workItemRepository;
    @Mock private WorkItemTypeRepository workItemTypeRepository;
    @Mock private StatusRepository statusRepository;
    @Mock private WorkItemAuditService auditService;
    @Mock private WorkItemCardMapper cardMapper;
    @Mock private SprintReportSnapshotEngine snapshotEngine;
    @Mock private OutboxRecorder outboxRecorder;
    @Mock private ContributionReportService contributionReportService;

    @Spy private LexoRankService lexoRankService = new LexoRankService();

    @InjectMocks private SprintServiceImpl sprintService;

    private UUID projectId;
    private UUID sprintId;
    private Sprint sprint;
    private Status todoStatus;
    private Status doneStatus;
    private WorkItemType storyType;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        sprintId = UUID.randomUUID();

        sprint = Sprint.builder()
                .id(sprintId)
                .projectId(projectId)
                .name("Sprint 1")
                .state(SprintState.FUTURE)
                .version(0)
                .build();

        todoStatus = Status.builder().id(UUID.randomUUID()).name("To Do")
                .category(StatusCategory.TODO).build();
        doneStatus = Status.builder().id(UUID.randomUUID()).name("Done")
                .category(StatusCategory.DONE).build();
        storyType = WorkItemType.builder().id(UUID.randomUUID()).name("Story")
                .hierarchyLevel(0).isSubtask(false).build();
    }

    private WorkItem item(Status status, String points) {
        return WorkItem.builder()
                .id(UUID.randomUUID())
                .projectId(projectId)
                .sprintId(sprintId)
                .typeId(storyType.getId())
                .statusId(status.getId())
                .code("DATN-" + (int) (Math.random() * 1000))
                .rank("0|i00000:")
                .storyPoints(points == null ? null : new BigDecimal(points))
                .build();
    }

    // ───────────────────────── startSprint ─────────────────────────

    @Test
    @DisplayName("startSprint: từ chối khi Sprint không ở trạng thái FUTURE")
    void startSprint_rejectsNonFutureSprint() {
        sprint.setState(SprintState.ACTIVE);
        when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint));

        assertThatThrownBy(() -> sprintService.startSprint(sprintId, new StartSprintRequest(), UUID.randomUUID()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Dự kiến");

        verify(sprintRepository, never()).save(any());
    }

    @Test
    @DisplayName("startSprint: ném 409 ConflictException khi dự án đã có Sprint đang chạy")
    void startSprint_rejectsWhenAnotherSprintActive() {
        Sprint active = Sprint.builder().id(UUID.randomUUID()).projectId(projectId)
                .name("Sprint 0").state(SprintState.ACTIVE).build();

        when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(new Project()));
        when(sprintRepository.findFirstByProjectIdAndState(projectId, SprintState.ACTIVE))
                .thenReturn(Optional.of(active));

        assertThatThrownBy(() -> sprintService.startSprint(sprintId, new StartSprintRequest(), UUID.randomUUID()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Sprint 0");

        verify(sprintRepository, never()).save(any());
    }

    @Test
    @DisplayName("startSprint: bắt buộc phải có ngày kết thúc")
    void startSprint_requiresEndDate() {
        when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(new Project()));
        when(sprintRepository.findFirstByProjectIdAndState(projectId, SprintState.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> sprintService.startSprint(sprintId, new StartSprintRequest(), UUID.randomUUID()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Ngày kết thúc");
    }

    @Test
    @DisplayName("startSprint: chặn Sprint rỗng trừ khi có cờ allowEmpty")
    void startSprint_rejectsEmptySprint() {
        StartSprintRequest req = StartSprintRequest.builder()
                .endDate(LocalDateTime.now().plusDays(14)).build();

        when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(new Project()));
        when(sprintRepository.findFirstByProjectIdAndState(projectId, SprintState.ACTIVE))
                .thenReturn(Optional.empty());
        when(workItemRepository.countBySprintIdAndDeletedAtIsNull(sprintId)).thenReturn(0L);

        assertThatThrownBy(() -> sprintService.startSprint(sprintId, req, UUID.randomUUID()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("rỗng");

        verify(sprintRepository, never()).save(any());
    }

    @Test
    @DisplayName("startSprint: chuyển sang ACTIVE và ghi sự kiện outbox khi hợp lệ")
    void startSprint_succeeds() {
        StartSprintRequest req = StartSprintRequest.builder()
                .endDate(LocalDateTime.now().plusDays(14)).build();

        when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(new Project()));
        when(sprintRepository.findFirstByProjectIdAndState(projectId, SprintState.ACTIVE))
                .thenReturn(Optional.empty());
        when(workItemRepository.countBySprintIdAndDeletedAtIsNull(sprintId)).thenReturn(3L);
        when(sprintRepository.save(any(Sprint.class))).thenAnswer(inv -> inv.getArgument(0));
        when(workItemRepository.findBySprintIdAndDeletedAtIsNullOrderByRankAsc(sprintId))
                .thenReturn(List.of());
        when(statusRepository.findAll()).thenReturn(List.of(todoStatus, doneStatus));

        sprintService.startSprint(sprintId, req, UUID.randomUUID());

        assertThat(sprint.getState()).isEqualTo(SprintState.ACTIVE);
        assertThat(sprint.getStartDate()).isNotNull();
        verify(outboxRecorder).record(eq("SPRINT"), eq(sprintId), eq("SPRINT_STARTED"), any());
    }

    @Test
    @DisplayName("startSprint: ném 409 khi còn Sprint FUTURE đứng trước trong kế hoạch (BR-S15)")
    void startSprint_rejectsWhenEarlierSprintPlanned() {
        LocalDateTime base = LocalDateTime.now().plusDays(1);
        Sprint phase1 = Sprint.builder().id(UUID.randomUUID()).projectId(projectId)
                .name("Giai đoạn 1").state(SprintState.FUTURE)
                .startDate(base).endDate(base.plusDays(20)).build();
        sprint.setName("Giai đoạn 2");
        sprint.setStartDate(base.plusDays(20));
        sprint.setEndDate(base.plusDays(42));

        when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(new Project()));
        when(sprintRepository.findFirstByProjectIdAndState(projectId, SprintState.ACTIVE))
                .thenReturn(Optional.empty());
        when(sprintRepository.findByProjectIdAndState(projectId, SprintState.FUTURE))
                .thenReturn(List.of(sprint, phase1));

        assertThatThrownBy(() -> sprintService.startSprint(sprintId, new StartSprintRequest(), UUID.randomUUID()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Giai đoạn 1");

        assertThat(sprint.getState()).isEqualTo(SprintState.FUTURE);
        verify(sprintRepository, never()).save(any());
        verify(outboxRecorder, never()).record(any(), any(), any(), any());
    }

    @Test
    @DisplayName("startSprint: Sprint chưa có ngày dự kiến đứng sau Sprint đã có ngày (BR-S15)")
    void startSprint_rejectsUndatedSprintWhenDatedSprintPlanned() {
        Sprint dated = Sprint.builder().id(UUID.randomUUID()).projectId(projectId)
                .name("Sprint có lịch").state(SprintState.FUTURE)
                .startDate(LocalDateTime.now().plusDays(30)).build();

        when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(new Project()));
        when(sprintRepository.findFirstByProjectIdAndState(projectId, SprintState.ACTIVE))
                .thenReturn(Optional.empty());
        when(sprintRepository.findByProjectIdAndState(projectId, SprintState.FUTURE))
                .thenReturn(List.of(sprint, dated));

        assertThatThrownBy(() -> sprintService.startSprint(sprintId, new StartSprintRequest(), UUID.randomUUID()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Sprint có lịch");
    }

    @Test
    @DisplayName("startSprint: cho phép bắt đầu Sprint FUTURE đứng đầu kế hoạch dù còn Sprint sau (BR-S15)")
    void startSprint_allowsFirstSprintInPlan() {
        LocalDateTime base = LocalDateTime.now().plusDays(1);
        sprint.setStartDate(base);
        Sprint later = Sprint.builder().id(UUID.randomUUID()).projectId(projectId)
                .name("Sprint 2").state(SprintState.FUTURE)
                .startDate(base.plusDays(14)).build();
        StartSprintRequest req = StartSprintRequest.builder().endDate(base.plusDays(14)).build();

        when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(new Project()));
        when(sprintRepository.findFirstByProjectIdAndState(projectId, SprintState.ACTIVE))
                .thenReturn(Optional.empty());
        when(sprintRepository.findByProjectIdAndState(projectId, SprintState.FUTURE))
                .thenReturn(List.of(later, sprint));
        when(workItemRepository.countBySprintIdAndDeletedAtIsNull(sprintId)).thenReturn(2L);
        when(sprintRepository.save(any(Sprint.class))).thenAnswer(inv -> inv.getArgument(0));
        when(workItemRepository.findBySprintIdAndDeletedAtIsNullOrderByRankAsc(sprintId))
                .thenReturn(List.of());
        when(statusRepository.findAll()).thenReturn(List.of(todoStatus, doneStatus));

        sprintService.startSprint(sprintId, req, UUID.randomUUID());

        assertThat(sprint.getState()).isEqualTo(SprintState.ACTIVE);
        assertThat(later.getState()).isEqualTo(SprintState.FUTURE);
    }

    // ──────────────────────── completeSprint ────────────────────────

    @Test
    @DisplayName("completeSprint: từ chối khi Sprint chưa chạy")
    void completeSprint_rejectsNonActiveSprint() {
        when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint)); // FUTURE

        assertThatThrownBy(() -> sprintService.completeSprint(sprintId, new CompleteSprintRequest(), UUID.randomUUID()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("đang chạy");

        verifyNoInteractions(snapshotEngine);
    }

    @Test
    @DisplayName("completeSprint: đưa việc chưa xong về Backlog, việc đã DONE giữ nguyên Sprint")
    void completeSprint_movesOnlyIncompleteItems() {
        sprint.setState(SprintState.ACTIVE);
        WorkItem done = item(doneStatus, "5");
        WorkItem open = item(todoStatus, "3");

        stubCompleteSprint(List.of(done, open));

        var result = sprintService.completeSprint(sprintId,
                CompleteSprintRequest.builder().targetSprintIdForIncomplete("BACKLOG").build(),
                UUID.randomUUID());

        // Viec chua xong ve Backlog...
        assertThat(open.getSprintId()).isNull();
        // ...con viec da xong phai o lai de velocity con dung.
        assertThat(done.getSprintId()).isEqualTo(sprintId);

        assertThat(sprint.getState()).isEqualTo(SprintState.CLOSED);
        assertThat(sprint.getCompletedDate()).isNotNull();
        assertThat(result.getCompletedItems()).isEqualTo(1);
        assertThat(result.getIncompleteItems()).isEqualTo(1);
        assertThat(result.getCompletedStoryPoints()).isEqualByComparingTo("5");
        assertThat(result.getIncompleteStoryPoints()).isEqualByComparingTo("3");
    }

    @Test
    @DisplayName("completeSprint: chụp snapshot TRƯỚC khi di chuyển công việc")
    void completeSprint_snapshotsBeforeMoving() {
        sprint.setState(SprintState.ACTIVE);
        WorkItem open = item(todoStatus, "3");
        stubCompleteSprint(List.of(open));

        sprintService.completeSprint(sprintId, new CompleteSprintRequest(), UUID.randomUUID());

        // Neu di chuyen truoc thi snapshot se ghi nhan sai so lieu cua Sprint.
        InOrder inOrder = inOrder(snapshotEngine, workItemRepository);
        inOrder.verify(snapshotEngine).generateFinalSnapshot(any(), anyList(), any(), any(), any(), anyString(), anyList());
        inOrder.verify(workItemRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("completeSprint: từ chối Sprint đích đã đóng")
    void completeSprint_rejectsClosedTargetSprint() {
        sprint.setState(SprintState.ACTIVE);
        UUID targetId = UUID.randomUUID();
        Sprint closed = Sprint.builder().id(targetId).projectId(projectId)
                .name("Sprint 0").state(SprintState.CLOSED).build();

        when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(sprintRepository.findById(targetId)).thenReturn(Optional.of(closed));

        CompleteSprintRequest req = CompleteSprintRequest.builder()
                .targetSprintIdForIncomplete(targetId.toString()).build();

        assertThatThrownBy(() -> sprintService.completeSprint(sprintId, req, UUID.randomUUID()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("đã đóng");
    }

    @Test
    @DisplayName("completeSprint: cascade sprintId xuống Sub-task của việc chưa xong")
    void completeSprint_cascadesToSubtasks() {
        sprint.setState(SprintState.ACTIVE);
        WorkItem parent = item(todoStatus, "5");
        WorkItem subtask = item(doneStatus, null);
        subtask.setParentId(parent.getId());

        stubCompleteSprint(List.of(parent));
        when(workItemRepository.findByParentIdInAndDeletedAtIsNull(List.of(parent.getId())))
                .thenReturn(List.of(subtask));
        when(workItemRepository.findByParentIdInAndDeletedAtIsNull(List.of(subtask.getId())))
                .thenReturn(List.of());

        var result = sprintService.completeSprint(sprintId,
                CompleteSprintRequest.builder().targetSprintIdForIncomplete("BACKLOG").build(),
                UUID.randomUUID());

        // Sub-task DONE van phai di theo cha chua xong, neu khong se mac ket o Sprint da dong.
        assertThat(subtask.getSprintId()).isNull();
        assertThat(result.getCascadedSubtaskCount()).isEqualTo(1);
    }

    private void stubCompleteSprint(List<WorkItem> items) {
        when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(workItemRepository.findBySprintIdAndDeletedAtIsNullOrderByRankAsc(sprintId))
                .thenReturn(items);
        when(statusRepository.findByCategory(StatusCategory.DONE)).thenReturn(List.of(doneStatus));
        when(workItemTypeRepository.findAllById(any())).thenReturn(List.of(storyType));
        when(sprintRepository.save(any(Sprint.class))).thenAnswer(inv -> inv.getArgument(0));
        when(snapshotEngine.generateFinalSnapshot(any(), anyList(), any(), any(), any(), anyString(), anyList()))
                .thenReturn(SprintReportSnapshot.builder().id(UUID.randomUUID()).build());
        lenient().when(workItemRepository.findByProjectIdAndSprintIdIsNullAndDeletedAtIsNullOrderByRankAsc(projectId))
                .thenReturn(List.of());
        lenient().when(workItemRepository.findByParentIdInAndDeletedAtIsNull(anyList()))
                .thenReturn(List.of());
    }
}
