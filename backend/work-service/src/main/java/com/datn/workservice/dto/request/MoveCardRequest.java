package com.datn.workservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Keo tha mot the tren Board. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MoveCardRequest {

    @NotNull(message = "Cột đích là bắt buộc")
    private UUID targetColumnId;

    /** Rank cua the ngay TRUOC vi tri tha (null neu tha len dau). */
    private String prevItemRank;

    /** Rank cua the ngay SAU vi tri tha (null neu tha xuong cuoi). */
    private String nextItemRank;

    /** Phien ban the ma client dang giu - lech nghia la nguoi khac vua sua (409). */
    private Integer clientVersion;

    /** Chi dinh trang thai dich khi cot anh xa nhieu trang thai. */
    private UUID targetStatusId;

    /** Cho phep vuot gioi han WIP (danh cho Scrum Master). */
    private Boolean ignoreWipLimit;

    /** Dong luon cac Sub-task chua xong khi keo sang cot DONE. */
    private Boolean resolveAllSubtasks;

    private String comment;
    private String resolution;
}
