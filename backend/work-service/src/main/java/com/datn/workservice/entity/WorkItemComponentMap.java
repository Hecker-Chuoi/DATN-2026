package com.datn.workservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "work_item_component_maps")
@IdClass(WorkItemComponentMap.WorkItemComponentId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkItemComponentMap {

    @Id
    @Column(name = "work_item_id", nullable = false)
    private UUID workItemId;

    @Id
    @Column(name = "component_id", nullable = false)
    private UUID componentId;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkItemComponentId implements Serializable {
        private UUID workItemId;
        private UUID componentId;
    }
}
