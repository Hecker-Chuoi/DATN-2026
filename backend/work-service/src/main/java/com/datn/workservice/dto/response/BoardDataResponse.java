package com.datn.workservice.dto.response;

import com.datn.workservice.enums.BoardType;
import com.datn.workservice.enums.SwimlaneStrategy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Toan bo du lieu de ve mot Board.
 *
 * `cards` la danh sach PHANG, moi the mang san columnId + swimlaneKey + rank; FE tu gom
 * theo cot trong O(n). Cau truc long (luong > cot > the) se buoc phai dung lai ca cay
 * sau moi thao tac keo tha va lam phinh payload vi lap lai metadata cua cot.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoardDataResponse {

    private UUID boardId;
    private UUID projectId;
    private String boardName;
    private BoardType boardType;

    private SprintResponse activeSprint;

    private List<BoardColumnResponse> columns;

    private SwimlaneStrategy swimlaneStrategy;
    private List<BoardSwimlaneResponse> swimlanes;

    private List<BoardCardResponse> cards;

    private Integer totalCards;
    private BigDecimal totalStoryPoints;

    /**
     * Trang thai dang duoc su dung nhung chua gan vao cot nao. UI phai canh bao,
     * tuyet doi khong am tham lam the bien mat.
     */
    private List<StatusResponse> unmappedStatuses;

    private LocalDateTime generatedAt;
}
