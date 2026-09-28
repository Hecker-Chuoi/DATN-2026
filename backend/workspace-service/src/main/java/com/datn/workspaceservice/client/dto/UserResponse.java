package com.datn.workspaceservice.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Bản sao read-only của UserResponse bên identity-service, chỉ để deserialize
 * phản hồi Feign. Giữ giống bản của work-service để hai service không lệch nhau.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private UUID id;
    private String keycloakId;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private Set<String> roles;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getFullName() {
        if (firstName == null && lastName == null) {
            return username;
        }
        if (firstName == null) return lastName;
        if (lastName == null) return firstName;
        return firstName + " " + lastName;
    }
}
