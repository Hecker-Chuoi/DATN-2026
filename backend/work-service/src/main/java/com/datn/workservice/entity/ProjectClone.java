package com.datn.workservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
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

    @CreationTimestamp
    @Column(name = "cloned_at", updatable = false)
    private LocalDateTime clonedAt;
}
