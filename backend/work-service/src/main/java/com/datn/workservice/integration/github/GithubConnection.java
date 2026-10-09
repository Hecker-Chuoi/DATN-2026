package com.datn.workservice.integration.github;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** Tài khoản GitHub đã kết nối của một user (Keycloak sub). Token lưu mã hóa, không bao giờ trả ra API. */
@Entity
@Table(name = "github_connections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GithubConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "github_user_id", nullable = false)
    private Long githubUserId;

    @Column(name = "github_login", nullable = false, length = 100)
    private String githubLogin;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @JsonIgnore
    @Column(name = "access_token_enc", nullable = false)
    private String accessTokenEnc;

    @Column(length = 100)
    private String scope;

    @Column(nullable = false)
    @Builder.Default
    private boolean invalid = false;

    @CreationTimestamp
    @Column(name = "connected_at", updatable = false, nullable = false)
    private LocalDateTime connectedAt;
}
