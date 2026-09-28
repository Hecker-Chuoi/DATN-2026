package com.datn.workservice.service;

import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ConflictException;
import com.datn.common.exception.ForbiddenException;
import com.datn.workservice.dto.request.MoveCardRequest;
import com.datn.workservice.dto.response.AvailableTransitionResponse;
import com.datn.workservice.entity.Board;
import com.datn.workservice.entity.BoardColumn;
import com.datn.workservice.entity.BoardColumnStatus;
import com.datn.workservice.entity.Status;
import com.datn.workservice.entity.WorkItem;
import com.datn.workservice.entity.Workflow;
import com.datn.workservice.entity.WorkflowTransition;
import com.datn.workservice.enums.BoardType;
import com.datn.workservice.enums.StatusCategory;
import com.datn.workservice.repository.BoardColumnRepository;
import com.datn.workservice.repository.BoardColumnStatusRepository;
import com.datn.workservice.repository.BoardRepository;
import com.datn.workservice.repository.BoardSwimlaneRepository;
import com.datn.workservice.repository.ProjectRepository;
import com.datn.workservice.repository.SprintRepository;
import com.datn.workservice.repository.StatusRepository;
import com.datn.workservice.repository.WorkItemRepository;
import com.datn.workservice.repository.WorkflowTransitionRepository;
import com.datn.workservice.service.board.SwimlaneGrouper;
import com.datn.workservice.service.board.WIPLimitGuard;
import com.datn.workservice.service.impl.BoardServiceImpl;
import com.datn.workservice.service.mapper.WorkItemCardMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BoardServiceTest {

    @Mock private BoardRepository boardRepository;
    @Mock private BoardColumnRepository boardColumnRepository;
    @Mock private BoardColumnStatusRepository boardColumnStatusRepository;
    @Mock private BoardSwimlaneRepository boardSwimlaneRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private SprintRepository sprintRepository;
    @Mock private StatusRepository statusRepository;
    @Mock private WorkItemRepository workItemRepository;
    @Mock private WorkflowTransitionRepository workflowTransitionRepository;
    @Mock private WorkflowEngineService workflowEngineService;
    @Mock private WorkItemCardMapper cardMapper;
    @Mock private WIPLimitGuard wipLimitGuard;
    @Mock private SwimlaneGrouper swimlaneGrouper;
    @Mock private SprintService sprintService;

    @Spy private LexoRankService lexoRankService = new LexoRankService();

    @InjectMocks private BoardServiceImpl boardService;

    private UUID boardId, projectId, itemId, todoColId, doneColId;
    private Board board;
    private Status todoStatus, doneStatus;
    private BoardColumn todoColumn, doneColumn;
    private WorkItem item;

    @BeforeEach
    void setUp() {
        boardId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        itemId = UUID.randomUUID();
        todoColId = UUID.randomUUID();
        doneColId = UUID.randomUUID();

        board = Board.builder().id(boardId).projectId(projectId)
                .name("Board").type(BoardType.KANBAN).build();

        todoStatus = Status.builder().id(UUID.randomUUID()).name("To Do")
                .category(StatusCategory.TODO).build();
        doneStatus = Status.builder().id(UUID.randomUUID()).name("Done")
                .category(StatusCategory.DONE).build();

        todoColumn = BoardColumn.builder().id(todoColId).boardId(boardId)
                .name("Cần làm").columnOrder(0).build();
        doneColumn = BoardColumn.builder().id(doneColId).boardId(boardId)
                .name("Hoàn thành").columnOrder(1).build();

        item = WorkItem.builder().id(itemId).projectId(projectId).code("TEST-1")
                .statusId(todoStatus.getId()).rank("0|i00000:").version(3).build();
    }

    private MoveCardRequest req(UUID targetColumnId, Integer clientVersion) {
        return MoveCardRequest.builder()
                .targetColumnId(targetColumnId)
                .clientVersion(clientVersion)
                .prevItemRank("0|i00000:")
                .nextItemRank("0|i0000z:")
                .build();
    }

    @Test
    @DisplayName("moveCard: clientVersion cũ → ConflictException và KHÔNG ghi gì xuống CSDL")
    void moveCard_staleVersionRejectedBeforeAnyWrite() {
        when(boardRepository.findById(boardId)).thenReturn(Optional.of(board));
        when(workItemRepository.findById(itemId)).thenReturn(Optional.of(item)); // version = 3

        assertThatThrownBy(() -> boardService.moveCard(boardId, itemId, req(doneColId, 2), UUID.randomUUID()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("tải lại");

        // Chan truoc moi thay doi: khong ghi, khong chay may trang thai.
        verify(workItemRepository, never()).saveAndFlush(any());
        verifyNoInteractions(workflowEngineService);
        verifyNoInteractions(wipLimitGuard);
    }

    @Test
    @DisplayName("moveCard: sắp xếp trong cùng cột KHÔNG gọi workflow và KHÔNG kiểm WIP")
    void moveCard_sameColumnSkipsWorkflowAndWip() {
        stubColumn(todoColumn, todoStatus);
        when(boardRepository.findById(boardId)).thenReturn(Optional.of(board));
        when(workItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(workItemRepository.saveAndFlush(any(WorkItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(statusRepository.findById(todoStatus.getId())).thenReturn(Optional.of(todoStatus));

        var res = boardService.moveCard(boardId, itemId, req(todoColId, 3), UUID.randomUUID());

        assertThat(res.getStatusChanged()).isFalse();
        assertThat(res.getNewRank()).isNotBlank();
        verifyNoInteractions(workflowEngineService);
        verifyNoInteractions(wipLimitGuard);
    }

    @Test
    @DisplayName("moveCard: đổi cột thì chạy qua workflow chứ không ghi thẳng trạng thái")
    void moveCard_crossColumnGoesThroughWorkflow() {
        UUID transitionId = UUID.randomUUID();
        stubColumn(doneColumn, doneStatus);
        when(boardRepository.findById(boardId)).thenReturn(Optional.of(board));
        when(workItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(workflowEngineService.getAvailableTransitions(itemId, null))
                .thenReturn(List.of(AvailableTransitionResponse.builder()
                        .transitionId(transitionId).name("Quick Done")
                        .toStatusId(doneStatus.getId()).toStatusName("Done")
                        .toStatusCategory(StatusCategory.DONE).build()));

        WorkItem moved = WorkItem.builder().id(itemId).projectId(projectId).code("TEST-1")
                .statusId(doneStatus.getId()).rank("0|i00000:").version(4).build();
        when(workflowEngineService.executeTransition(eq(itemId), eq(transitionId), any(), any()))
                .thenReturn(moved);
        when(workItemRepository.saveAndFlush(any(WorkItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(statusRepository.findById(doneStatus.getId())).thenReturn(Optional.of(doneStatus));

        var res = boardService.moveCard(boardId, itemId, req(doneColId, 3), null);

        assertThat(res.getStatusChanged()).isTrue();
        assertThat(res.getAppliedTransitionId()).isEqualTo(transitionId);
        verify(workflowEngineService).executeTransition(eq(itemId), eq(transitionId), any(), any());
        verify(wipLimitGuard).checkWipLimit(any(), any(), anyList(), any(), anyBoolean());
    }

    @Test
    @DisplayName("moveCard: không có bước chuyển và quy trình cũng không có đường đi → 400")
    void moveCard_noTransitionAtAll_throwsBadRequest() {
        stubColumn(doneColumn, doneStatus);
        when(boardRepository.findById(boardId)).thenReturn(Optional.of(board));
        when(workItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(workflowEngineService.getAvailableTransitions(itemId, null)).thenReturn(List.of());
        when(workflowEngineService.resolveWorkflow(any(), any()))
                .thenReturn(Workflow.builder().id(UUID.randomUUID()).build());
        when(workflowTransitionRepository.findByWorkflowId(any())).thenReturn(List.of());
        when(statusRepository.findById(todoStatus.getId())).thenReturn(Optional.of(todoStatus));

        assertThatThrownBy(() -> boardService.moveCard(boardId, itemId, req(doneColId, 3), null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Không có bước chuyển hợp lệ");
    }

    @Test
    @DisplayName("moveCard: quy trình CÓ đường đi nhưng user không đủ điều kiện → 403")
    void moveCard_transitionExistsButNotPermitted_throwsForbidden() {
        UUID workflowId = UUID.randomUUID();
        stubColumn(doneColumn, doneStatus);
        when(boardRepository.findById(boardId)).thenReturn(Optional.of(board));
        when(workItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(workflowEngineService.getAvailableTransitions(itemId, null)).thenReturn(List.of());
        when(workflowEngineService.resolveWorkflow(any(), any()))
                .thenReturn(Workflow.builder().id(workflowId).build());
        when(workflowTransitionRepository.findByWorkflowId(workflowId))
                .thenReturn(List.of(WorkflowTransition.builder()
                        .id(UUID.randomUUID()).workflowId(workflowId)
                        .fromStatusId(todoStatus.getId()).toStatusId(doneStatus.getId())
                        .name("Quick Done").build()));

        assertThatThrownBy(() -> boardService.moveCard(boardId, itemId, req(doneColId, 3), null))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("không đủ điều kiện");
    }

    @Test
    @DisplayName("moveCard: cột chưa ánh xạ trạng thái nào → 400")
    void moveCard_columnWithoutStatuses_throwsBadRequest() {
        when(boardRepository.findById(boardId)).thenReturn(Optional.of(board));
        when(workItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(boardColumnRepository.findById(doneColId)).thenReturn(Optional.of(doneColumn));
        when(boardColumnStatusRepository.findByColumnId(doneColId)).thenReturn(List.of());

        assertThatThrownBy(() -> boardService.moveCard(boardId, itemId, req(doneColId, 3), null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("chưa được ánh xạ");
    }

    @Test
    @DisplayName("moveCard: công việc khác dự án → 400")
    void moveCard_itemFromAnotherProject_throwsBadRequest() {
        item.setProjectId(UUID.randomUUID());
        when(boardRepository.findById(boardId)).thenReturn(Optional.of(board));
        when(workItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> boardService.moveCard(boardId, itemId, req(doneColId, 3), null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("không thuộc bảng này");
    }

    private void stubColumn(BoardColumn column, Status status) {
        when(boardColumnRepository.findById(column.getId())).thenReturn(Optional.of(column));
        when(boardColumnStatusRepository.findByColumnId(column.getId()))
                .thenReturn(List.of(BoardColumnStatus.builder()
                        .columnId(column.getId()).statusId(status.getId()).build()));
    }
}
