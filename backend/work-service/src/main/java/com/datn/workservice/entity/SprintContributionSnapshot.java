package com.datn.workservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "sprint_contribution_snapshots", uniqueConstraints = {
    @UniqueConstraint(name = "uq_sprint_user_snapshot", columnNames = {"sprint_id", "user_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SprintContributionSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "sprint_id", nullable = false)
    private UUID sprintId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "total_points", precision = 6, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal totalPoints = BigDecimal.ZERO;

    @Column(name = "contribution_percentage", precision = 5, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal contributionPercentage = BigDecimal.ZERO;

    @Column(name = "completed_tasks", nullable = false)
    @Builder.Default
    private Integer completedTasks = 0;

    @Column(name = "discussion_count", nullable = false)
    @Builder.Default
    private Integer discussionCount = 0;

    @Column(name = "bugs_reported", nullable = false)
    @Builder.Default
    private Integer bugsReported = 0;

    @Column(name = "on_time_rate", precision = 5, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal onTimeRate = BigDecimal.ZERO;

    @Column(name = "reopened_count", nullable = false)
    @Builder.Default
    private Integer reopenedCount = 0;

    @CreationTimestamp
    @Column(name = "frozen_at", updatable = false, nullable = false)
    private OffsetDateTime frozenAt;
}
