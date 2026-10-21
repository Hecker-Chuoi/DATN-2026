package com.datn.identity_service.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Gọi notification-service (HTTP nội bộ) để gửi email.
 * Fail-safe: lỗi gửi mail chỉ ghi log, không làm hỏng luồng đăng ký; người dùng có thể "gửi lại email".
 * ponytail: gọi HTTP đồng bộ; chuyển sang Kafka topic khi cần retry/queue.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationClient {

    private final RestTemplate restTemplate;

    @Value("${app.services.notification.url}")
    private String baseUrl;

    @Async
    public void sendEmail(String to, String subject, String html) {
        post("/internal/notifications/email", Map.of("to", to, "subject", subject, "html", html), to);
    }

    /** Gửi theo template ở notification-service/resources/templates/{template}.html, vars thay {{key}}. */
    @Async
    public void sendTemplate(String to, String subject, String template, Map<String, String> vars) {
        post("/internal/notifications/email/template",
                Map.of("to", to, "subject", subject, "template", template, "vars", vars), to);
    }

    private boolean post(String path, Map<String, Object> body, String to) {
        try {
            restTemplate.postForEntity(baseUrl + path, body, Void.class);
            log.info("Dispatched async email to {} via notification-service", to);
            return true;
        } catch (Exception e) {
            log.error("Không gửi được email tới {} qua notification-service ({}): {}", to, baseUrl, e.getMessage());
            return false;
        }
    }
}
