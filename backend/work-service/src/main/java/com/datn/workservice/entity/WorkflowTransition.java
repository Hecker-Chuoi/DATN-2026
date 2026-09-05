package com.datn.workservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "workflow_transitions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowTransition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(name = "from_status_id")
    private UUID fromStatusId; // NULL nếu là khởi tạo (Create transition)

    @Column(name = "to_status_id", nullable = false)
    private UUID toStatusId;

    @Column(nullable = false, length = 150)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "conditions_config", columnDefinition = "jsonb")
    private Object conditionsConfig;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validators_config", columnDefinition = "jsonb")
    private Object validatorsConfig;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "post_functions_config", columnDefinition = "jsonb")
    private Object postFunctionsConfig;
}
