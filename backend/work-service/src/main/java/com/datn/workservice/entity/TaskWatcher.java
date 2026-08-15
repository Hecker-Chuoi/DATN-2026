package com.datn.workservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "task_watchers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskWatcher {

    @EmbeddedId
    private TaskWatcherId id;

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @Builder
    public static class TaskWatcherId implements Serializable {

        @Column(name = "task_id", nullable = false)
        private UUID taskId;

        @Column(name = "user_id", nullable = false)
        private UUID userId;
    }
}
