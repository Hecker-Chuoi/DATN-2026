package com.datn.workservice.service.board;

import com.datn.common.exception.BadRequestException;
import com.datn.workservice.entity.Board;
import com.datn.workservice.entity.BoardColumn;
import com.datn.workservice.enums.BoardType;
import com.datn.workservice.repository.WorkItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.UUID;

/**
 * Kiem soat gioi han WIP cua cot.
 *
 * Luu y quan trong: bang work_items KHONG co cot column_id, nen so the trong mot cot
 * phai dem gian tiep qua tap trang thai ma cot do anh xa (board_column_statuses).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WIPLimitGuard {

    private final WorkItemRepository workItemRepository;

    public record WipStatus(int currentCount, Integer minWipLimit, Integer maxWipLimit,
                            boolean isOverMax, boolean isBelowMin) {}

    /**
     * Dem so the dang nam trong cot.
     *
     * @param sprintScopeId Sprint dang hien thi voi bang SCRUM; null voi bang KANBAN.
     */
    public WipStatus evaluate(BoardColumn column, Collection<UUID> statusIds,
                              UUID projectId, UUID sprintScopeId) {
        if (statusIds == null || statusIds.isEmpty()) {
            return new WipStatus(0, column.getMinWipLimit(), column.getMaxWipLimit(), false, false);
        }
        long count = sprintScopeId != null
                ? workItemRepository.countBySprintIdAndStatusIdInAndDeletedAtIsNull(sprintScopeId, statusIds)
                : workItemRepository.countActiveByProjectIdAndStatusIdIn(projectId, statusIds);

        Integer max = column.getMaxWipLimit();
        Integer min = column.getMinWipLimit();
        return new WipStatus(
                (int) count, min, max,
                max != null && count > max,
                min != null && count < min
        );
    }

    /**
     * Chan khi the SAP DI VAO cot da day.
     *
     * Chi goi khi the doi cot. Sap xep trong cung cot hoac keo the RA KHOI cot dang day
     * khong bao gio bi chan - neu khong nguoi dung se bi ket cung.
     *
     * Gioi han WIP toi thieu chi la canh bao ngh?n luong, khong bao gio chan.
     */
    public void checkWipLimit(Board board, BoardColumn targetColumn, Collection<UUID> targetStatusIds,
                              UUID sprintScopeId, boolean override) {
        Integer max = targetColumn.getMaxWipLimit();
        if (max == null) {
            return;
        }

        WipStatus status = evaluate(targetColumn, targetStatusIds, board.getProjectId(), sprintScopeId);

        // The chua nam trong cot nen dung ">=": them 1 the nua se thanh max + 1.
        if (status.currentCount() >= max) {
            if (override) {
                log.warn("Bo qua gioi han WIP cot '{}' ({}/{}) tren bang {}",
                        targetColumn.getName(), status.currentCount(), max, board.getId());
                return;
            }
            throw new BadRequestException(String.format(
                    "Cột '%s' đã đạt giới hạn WIP tối đa (%d). Hãy hoàn thành công việc đang dở trước khi kéo thêm thẻ.",
                    targetColumn.getName(), max));
        }
    }

    /** Bang SCRUM dem theo Sprint dang chay, bang KANBAN dem theo toan du an. */
    public boolean isSprintScoped(Board board) {
        return board.getType() == BoardType.SCRUM;
    }
}
