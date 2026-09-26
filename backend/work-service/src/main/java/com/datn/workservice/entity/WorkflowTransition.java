package com.datn.workservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;
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

    /**
     * Conditions config (JSONB array). Use List<Map> so Hibernate 6 can correctly
     * serialize/deserialize the JSON array via Jackson without ambiguous Object type.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "conditions_config", columnDefinition = "jsonb")
    private List<Map<String, Object>> conditionsConfig;

    /**
     * Validators config (JSONB array).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validators_config", columnDefinition = "jsonb")
    private List<Map<String, Object>> validatorsConfig;

    /**
     * Post-functions config (JSONB array).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "post_functions_config", columnDefinition = "jsonb")
    private List<Map<String, Object>> postFunctionsConfig;
}
