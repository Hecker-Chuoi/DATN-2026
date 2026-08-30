package com.datn.workservice.entity;

import com.datn.workservice.enums.LinkType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "work_item_links", uniqueConstraints = {
    @UniqueConstraint(name = "uq_work_item_link", columnNames = {"source_item_id", "target_item_id", "link_type"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkItemLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "source_item_id", nullable = false)
    private UUID sourceItemId;

    @Column(name = "target_item_id", nullable = false)
    private UUID targetItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false, length = 50)
    private LinkType linkType; // BLOCKS, RELATES_TO, DUPLICATES, CAUSES

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;
}
