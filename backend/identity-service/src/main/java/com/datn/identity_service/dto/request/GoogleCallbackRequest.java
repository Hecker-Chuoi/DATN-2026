package com.datn.identity_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;


@Data
public class GoogleCallbackRequest {

    @NotBlank(message = "Authorization code is required")
    private String code;

    @NotBlank(message = "Redirect URI is required")
    private String redirectUri;
}
