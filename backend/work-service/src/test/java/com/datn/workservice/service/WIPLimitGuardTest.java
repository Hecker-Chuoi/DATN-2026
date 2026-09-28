package com.datn.workservice.service;

import com.datn.common.exception.BadRequestException;
import com.datn.workservice.entity.Board;
import com.datn.workservice.entity.BoardColumn;
import com.datn.workservice.enums.BoardType;
import com.datn.workservice.repository.WorkItemRepository;
import com.datn.workservice.service.board.WIPLimitGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WIPLimitGuardTest {

    @Mock private WorkItemRepository workItemRepository;
    @InjectMocks private WIPLimitGuard guard;

    private Board scrumBoard, kanbanBoard;
    private UUID projectId, sprintId;
    private List<UUID> statusIds;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        sprintId = UUID.randomUUID();
        statusIds = List.of(UUID.randomUUID());
        scrumBoard = Board.builder().id(UUID.randomUUID()).projectId(projectId).type(BoardType.SCRUM).build();
        kanbanBoard = Board.builder().id(UUID.randomUUID()).projectId(projectId).type(BoardType.KANBAN).build();
    }

    private BoardColumn column(Integer min, Integer max) {
        return BoardColumn.builder().id(UUID.randomUUID()).name("Đang làm")
                .columnOrder(1).minWipLimit(min).maxWipLimit(max).build();
    }

    @Test
    @DisplayName("Cột đã đạt giới hạn WIP → chặn kéo thêm thẻ vào")
    void blocksWhenAtMax() {
        when(workItemRepository.countBySprintIdAndStatusIdInAndDeletedAtIsNull(sprintId, statusIds))
                .thenReturn(4L);

        assertThatThrownBy(() -> guard.checkWipLimit(scrumBoard, column(null, 4), statusIds, sprintId, false))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("giới hạn WIP tối đa (4)");
    }

    @Test
    @DisplayName("Còn chỗ trống → cho phép")
    void allowsWhenBelowMax() {
        when(workItemRepository.countBySprintIdAndStatusIdInAndDeletedAtIsNull(sprintId, statusIds))
                .thenReturn(2L);

        assertThatCode(() -> guard.checkWipLimit(scrumBoard, column(null, 4), statusIds, sprintId, false))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Cột không đặt giới hạn tối đa → không bao giờ chặn, không cần đếm")
    void neverBlocksWithoutMaxLimit() {
        assertThatCode(() -> guard.checkWipLimit(scrumBoard, column(2, null), statusIds, sprintId, false))
                .doesNotThrowAnyException();

        verify(workItemRepository, never()).countBySprintIdAndStatusIdInAndDeletedAtIsNull(any(), anyCollection());
    }

    @Test
    @DisplayName("Cờ bỏ qua giới hạn cho phép Scrum Master ép vượt WIP")
    void overrideBypassesLimit() {
        when(workItemRepository.countBySprintIdAndStatusIdInAndDeletedAtIsNull(sprintId, statusIds))
                .thenReturn(9L);

        assertThatCode(() -> guard.checkWipLimit(scrumBoard, column(null, 4), statusIds, sprintId, true))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Giới hạn tối thiểu chỉ cảnh báo, không bao giờ chặn")
    void minLimitOnlyWarns() {
        when(workItemRepository.countBySprintIdAndStatusIdInAndDeletedAtIsNull(sprintId, statusIds))
                .thenReturn(1L);

        var status = guard.evaluate(column(3, null), statusIds, projectId, sprintId);

        assertThat(status.isBelowMin()).isTrue();
        assertThat(status.isOverMax()).isFalse();
        assertThatCode(() -> guard.checkWipLimit(scrumBoard, column(3, null), statusIds, sprintId, false))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Bảng SCRUM đếm theo Sprint, bảng KANBAN đếm theo toàn dự án")
    void countsBySprintForScrumAndByProjectForKanban() {
        when(workItemRepository.countBySprintIdAndStatusIdInAndDeletedAtIsNull(sprintId, statusIds))
                .thenReturn(2L);
        guard.evaluate(column(null, 5), statusIds, projectId, sprintId);
        verify(workItemRepository).countBySprintIdAndStatusIdInAndDeletedAtIsNull(sprintId, statusIds);

        when(workItemRepository.countActiveByProjectIdAndStatusIdIn(projectId, statusIds))
                .thenReturn(7L);
        var kanban = guard.evaluate(column(null, 5), statusIds, projectId, null);
        verify(workItemRepository).countActiveByProjectIdAndStatusIdIn(projectId, statusIds);
        assertThat(kanban.currentCount()).isEqualTo(7);
        assertThat(kanban.isOverMax()).isTrue();
    }

    @Test
    @DisplayName("Cột chưa ánh xạ trạng thái → đếm bằng 0, không truy vấn")
    void emptyStatusesCountsZero() {
        var status = guard.evaluate(column(null, 3), List.of(), projectId, sprintId);

        assertThat(status.currentCount()).isZero();
        verifyNoInteractions(workItemRepository);
    }
}
