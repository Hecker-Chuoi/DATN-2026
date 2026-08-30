package com.datn.workservice.entity;

import com.datn.workservice.enums.TeamRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "team_members", uniqueConstraints = {
    @UniqueConstraint(name = "uq_team_member", columnNames = {"team_id", "user_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "team_id", nullable = false)
    private UUID teamId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private TeamRole role = TeamRole.MEMBER;

    @CreationTimestamp
    @Column(name = "joined_at", updatable = false, nullable = false)
    private LocalDateTime joinedAt;
}
