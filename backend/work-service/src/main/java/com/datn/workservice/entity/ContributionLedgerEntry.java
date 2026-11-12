package com.datn.workservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "contribution_ledger_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContributionLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "sprint_id")
    private UUID sprintId;

    @Column(name = "work_item_id")
    private UUID workItemId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "points", precision = 6, scale = 2, nullable = false)
    private BigDecimal points;

    @Column(name = "entry_type", length = 50, nullable = false)
    private String entryType; // COMPLETED, REOPENED, DELETED, MANUAL_ADJUSTMENT

    @Column(name = "note", length = 255)
    private String note;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;
}
