package com.datn.notification.service;

import com.datn.notification.catalog.NotificationCatalog;
import com.datn.notification.catalog.NotificationSpec;
import com.datn.notification.catalog.RenderContext;
import com.datn.notification.client.UserDirectory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

/**
 * Gửi email cho các loại thông báo có cấu hình email (A1, C1, D1, F1, F3, G1, G8).
 * Chạy sau commit; {@link EmailService#sendHtml} vốn đã @Async và tự nuốt lỗi SMTP.
 */
@Slf4j
@Service
public class NotificationMailer {

    private final EmailService emailService;
    private final MailTemplateService templateService;
    private final UserDirectory users;
    private final String frontendUrl;

    public NotificationMailer(EmailService emailService, MailTemplateService templateService, UserDirectory users,
                              @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.emailService = emailService;
        this.templateService = templateService;
        this.users = users;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    public void sendAfterCommit(NotificationSpec.EmailSpec email, RenderContext ctx, String link) {
        Runnable task = () -> send(email, ctx, link);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    private void send(NotificationSpec.EmailSpec email, RenderContext ctx, String link) {
        try {
            var user = users.find(ctx.recipientId()).orElse(null);
            if (user == null || user.email() == null || user.email().isBlank()) {
                log.debug("Bỏ qua email {}: không có email người nhận {}", email.template(), ctx.recipientId());
                return;
            }
            String absolute = link == null ? frontendUrl : frontendUrl + link;
            Map<String, String> vars = NotificationCatalog.withRecipient(email.vars().apply(ctx, absolute), user.displayName());
            String html = templateService.render(email.template(), vars);
            emailService.sendHtml(user.email(), email.subject().apply(ctx), html);
        } catch (Exception e) {
            log.warn("Không gửi được email {} cho {}: {}", email.template(), ctx.recipientId(), e.getMessage());
        }
    }
}
