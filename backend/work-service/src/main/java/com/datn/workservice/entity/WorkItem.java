package com.datn.workservice.entity;

import com.datn.workservice.enums.WorkItemPriority;
import com.datn.workservice.enums.WorkItemResolution;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "work_items", uniqueConstraints = {
    @UniqueConstraint(name = "uq_project_work_item_code", columnNames = {"project_id", "code"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "type_id", nullable = false)
    private UUID typeId;

    @Column(name = "status_id", nullable = false)
    private UUID statusId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "sprint_id")
    private UUID sprintId;

    @Column(nullable = false, length = 50)
    private String code; // VD: 'AUTH-1'

    @Column(nullable = false, length = 500)
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private WorkItemPriority priority = WorkItemPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(length = 100)
    private WorkItemResolution resolution;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(name = "story_points", precision = 6, scale = 2)
    private BigDecimal storyPoints;

    @Column(name = "original_estimate_seconds")
    @Builder.Default
    private Long originalEstimateSeconds = 0L;

    @Column(name = "remaining_estimate_seconds")
    @Builder.Default
    private Long remainingEstimateSeconds = 0L;

    @Column(name = "time_spent_seconds")
    @Builder.Default
    private Long timeSpentSeconds = 0L;

    @Column(nullable = false, length = 255)
    private String rank; // LexoRank string

    @Column(name = "component_id")
    private UUID componentId;

    @Column(name = "fix_version_id")
    private UUID fixVersionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_fields", columnDefinition = "jsonb")
    private Map<String, Object> customFields;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @jakarta.persistence.Version
    @Column(nullable = false)
    @Builder.Default
    private Integer version = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
