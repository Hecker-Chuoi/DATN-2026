package com.datn.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** Người dùng tắt tiếng một dự án: chỉ còn nhận thông báo gửi đích danh và các loại bị khoá. */
@Entity
@Table(name = "notification_project_mutes")
@IdClass(ProjectMute.Key.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMute {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "project_name", length = 255)
    private String projectName;

    /** null = tắt đến khi bật lại. */
    @Column(name = "muted_until")
    private Instant mutedUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public boolean activeAt(Instant now) {
        return mutedUntil == null || mutedUntil.isAfter(now);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private UUID userId;
        private UUID projectId;
    }
}
