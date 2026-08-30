package com.datn.workservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "sprint_report_snapshots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SprintReportSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "sprint_id", nullable = false, unique = true)
    private UUID sprintId;

    @Column(name = "total_story_points", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalStoryPoints = BigDecimal.ZERO;

    @Column(name = "completed_story_points", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal completedStoryPoints = BigDecimal.ZERO;

    @Column(name = "incomplete_story_points", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal incompleteStoryPoints = BigDecimal.ZERO;

    @Column(name = "total_items")
    @Builder.Default
    private Integer totalItems = 0;

    @Column(name = "completed_items")
    @Builder.Default
    private Integer completedItems = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_data", columnDefinition = "jsonb", nullable = false)
    private Object snapshotData;

    @Column(name = "closed_by", nullable = false)
    private UUID closedBy;

    @CreationTimestamp
    @Column(name = "closed_at", updatable = false, nullable = false)
    private LocalDateTime closedAt;
}
