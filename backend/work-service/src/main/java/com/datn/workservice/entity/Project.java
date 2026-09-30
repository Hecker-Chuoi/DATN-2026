package com.datn.workservice.entity;

import com.datn.workservice.enums.DefaultAssigneeType;
import com.datn.workservice.enums.ProjectType;
import com.datn.workservice.enums.TemplateCategory;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "team_id")
    private UUID teamId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, unique = true, length = 20)
    private String key;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "icon_url", length = 500)
    private String iconUrl;

    @Column(name = "lead_user_id", nullable = false)
    private UUID leadUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_assignee_type", nullable = false, length = 50)
    @Builder.Default
    private DefaultAssigneeType defaultAssigneeType = DefaultAssigneeType.PROJECT_LEADER;

    @Enumerated(EnumType.STRING)
    @Column(name = "project_type", nullable = false, length = 50)
    @Builder.Default
    private ProjectType projectType = ProjectType.SOFTWARE_SCRUM;

    @Column(name = "is_template", nullable = false)
    @Builder.Default
    private Boolean isTemplate = false;

    /** Phân loại mẫu; chỉ có nghĩa khi isTemplate = true. */
    @Enumerated(EnumType.STRING)
    @Column(name = "template_category", length = 50)
    private TemplateCategory templateCategory;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
