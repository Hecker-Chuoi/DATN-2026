package com.datn.workservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "workflow_scheme_mappings", uniqueConstraints = {
    @UniqueConstraint(name = "uq_scheme_item_type", columnNames = {"scheme_id", "work_item_type_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowSchemeMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "scheme_id", nullable = false)
    private UUID schemeId;

    @Column(name = "work_item_type_id", nullable = false)
    private UUID workItemTypeId;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;
}
