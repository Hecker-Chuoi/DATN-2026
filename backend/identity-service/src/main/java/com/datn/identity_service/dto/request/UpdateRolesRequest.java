package com.datn.identity_service.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Set;

@Data
public class UpdateRolesRequest {

    @NotEmpty(message = "Roles list must not be empty")
    private Set<String> roles;
}
