package com.datn.workservice.entity;

import com.datn.workservice.enums.CustomFieldType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "custom_field_definitions", uniqueConstraints = {
    @UniqueConstraint(name = "uq_proj_field_key", columnNames = {"project_id", "field_key"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomFieldDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "field_key", nullable = false, length = 100)
    private String fieldKey;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false, length = 50)
    private CustomFieldType fieldType; // TEXT, NUMBER, SELECT, MULTI_SELECT, DATE, USER, CHECKBOX, URL

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options", columnDefinition = "jsonb")
    private Object options;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_rules", columnDefinition = "jsonb")
    private Object validationRules;

    @Column(name = "applicable_type_ids")
    private UUID[] applicableTypeIds;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;
}
