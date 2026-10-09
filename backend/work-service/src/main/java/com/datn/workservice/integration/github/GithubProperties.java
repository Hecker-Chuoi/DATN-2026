package com.datn.workservice.integration.github;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Cấu hình app.github.* và app.public-url (xem application.yaml, .env.example). */
@Component
@Getter
public class GithubProperties {

    @Value("${app.github.client-id}")
    private String clientId;

    @Value("${app.github.client-secret}")
    private String clientSecret;

    @Value("${app.github.webhook-secret}")
    private String webhookSecret;

    /** URL gateway nhìn từ internet (ngrok khi dev) để GitHub gọi webhook. */
    @Value("${app.public-url}")
    private String publicUrl;

    public String webhookUrl() {
        return publicUrl.replaceAll("/+$", "") + "/work/api/v1/work/github/webhook";
    }
}
