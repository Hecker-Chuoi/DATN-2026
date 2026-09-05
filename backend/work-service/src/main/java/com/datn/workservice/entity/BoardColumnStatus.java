package com.datn.workservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "board_column_statuses")
@IdClass(BoardColumnStatus.BoardColumnStatusId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoardColumnStatus {

    @Id
    @Column(name = "column_id", nullable = false)
    private UUID columnId;

    @Id
    @Column(name = "status_id", nullable = false)
    private UUID statusId;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BoardColumnStatusId implements Serializable {
        private UUID columnId;
        private UUID statusId;
    }
}
