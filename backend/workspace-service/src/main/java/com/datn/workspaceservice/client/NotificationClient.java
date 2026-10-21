package com.datn.workspaceservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * Gọi notification-service (HTTP nội bộ, không qua gateway) để gửi email.
 * Caller phải tự bọc try/catch: lỗi mail không được làm hỏng nghiệp vụ chính.
 */
@FeignClient(name = "notification-service", url = "${app.services.notification.url:http://localhost:9994}")
public interface NotificationClient {

    record EmailRequest(String to, String subject, String html) {}

    /** template = tên file trong notification-service/resources/templates (không đuôi .html); vars thay {{key}}. */
    record TemplateEmailRequest(String to, String subject, String template, Map<String, String> vars) {}

    @PostMapping("/internal/notifications/email")
    void sendEmail(@RequestBody EmailRequest request);

    @PostMapping("/internal/notifications/email/template")
    void sendTemplate(@RequestBody TemplateEmailRequest request);
}
