package com.datn.aiservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "ai_user_settings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiUserSettings {

    @Id
    @Column(name = "user_id", length = 64)
    private String userId;

    @Column(name = "memory_enabled", nullable = false)
    private boolean memoryEnabled;

    @Column(name = "daily_request_limit")
    private Integer dailyRequestLimit;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
