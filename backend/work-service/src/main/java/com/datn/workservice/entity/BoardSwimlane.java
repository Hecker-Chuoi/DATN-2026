package com.datn.workservice.entity;

import com.datn.workservice.enums.SwimlaneStrategy;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "board_swimlanes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoardSwimlane {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "board_id", nullable = false)
    private UUID boardId;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SwimlaneStrategy strategy; // BY_EPIC, BY_ASSIGNEE, BY_PRIORITY, CUSTOM_JQL

    @Column(name = "custom_jql", columnDefinition = "TEXT")
    private String customJql;

    @Column(name = "swimlane_order", nullable = false)
    private Integer swimlaneOrder;
}
