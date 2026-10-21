package com.datn.workservice.entity;

import com.datn.workservice.enums.CloneType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "project_clones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectClone {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "source_project_id", nullable = false)
    private UUID sourceProjectId;

    @Column(name = "cloned_project_id", nullable = false)
    private UUID clonedProjectId;

    @Column(name = "cloned_by", nullable = false)
    private UUID clonedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "clone_type", nullable = false, length = 30)
    @Builder.Default
    private CloneType cloneType = CloneType.FROM_TEMPLATE;

    /** Số ngày đã dời mốc thời gian so với dự án nguồn. */
    @Column(name = "date_offset_days", nullable = false)
    @Builder.Default
    private Integer dateOffsetDays = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "clone_options", columnDefinition = "jsonb")
    private Map<String, Object> cloneOptions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "clone_stats", columnDefinition = "jsonb")
    private Map<String, Object> cloneStats;

    @CreationTimestamp
    @Column(name = "cloned_at", updatable = false, nullable = false)
    private LocalDateTime clonedAt;
}
