package com.datn.workservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "work_item_label_maps")
@IdClass(WorkItemLabelMap.WorkItemLabelId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkItemLabelMap {

    @Id
    @Column(name = "work_item_id", nullable = false)
    private UUID workItemId;

    @Id
    @Column(name = "label_id", nullable = false)
    private UUID labelId;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkItemLabelId implements Serializable {
        private UUID workItemId;
        private UUID labelId;
    }
}
