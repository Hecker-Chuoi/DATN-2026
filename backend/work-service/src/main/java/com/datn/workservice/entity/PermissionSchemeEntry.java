package com.datn.workservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "permission_scheme_entries", uniqueConstraints = {
    @UniqueConstraint(name = "uq_perm_entry", columnNames = {"project_id", "role_id", "permission_key"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PermissionSchemeEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "permission_key", nullable = false, length = 100)
    private String permissionKey;
}
