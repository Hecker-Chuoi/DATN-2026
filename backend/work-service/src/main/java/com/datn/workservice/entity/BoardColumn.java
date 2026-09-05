package com.datn.workservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "board_columns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoardColumn {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "board_id", nullable = false)
    private UUID boardId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "column_order", nullable = false)
    private Integer columnOrder;

    @Column(name = "min_wip_limit")
    private Integer minWipLimit;

    @Column(name = "max_wip_limit")
    private Integer maxWipLimit;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;
}
