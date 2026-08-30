package com.datn.workservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "work_item_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkItemType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "icon_name", length = 100)
    private String iconName;

    @Column(name = "color_hex", length = 20)
    private String colorHex;

    @Column(name = "hierarchy_level", nullable = false)
    private Integer hierarchyLevel; // 1: Epic, 0: Story/Task/Bug, -1: Subtask

    @Column(name = "is_subtask", nullable = false)
    @Builder.Default
    private Boolean isSubtask = false;

    @Column(name = "is_system_default", nullable = false)
    @Builder.Default
    private Boolean isSystemDefault = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;
}
