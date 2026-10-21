package com.datn.notification.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.notification.service.EmailService;
import com.datn.notification.service.MailTemplateService;
import jakarta.mail.MessagingException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoint nội bộ cho các service khác gọi (không public qua gateway).
 */
@RestController
@RequestMapping("/internal/notifications")
public class InternalEmailController {

    private final EmailService emailService;
    private final MailTemplateService templateService;

    public InternalEmailController(EmailService emailService, MailTemplateService templateService) {
        this.emailService = emailService;
        this.templateService = templateService;
    }

    public record EmailRequest(
            @NotBlank @Email String to,
            @NotBlank String subject,
            @NotBlank String html
    ) {}

    /** template = tên file trong resources/templates (không đuôi .html); vars thay cho {{key}}. */
    public record TemplateEmailRequest(
            @NotBlank @Email String to,
            @NotBlank String subject,
            @NotBlank String template,
            Map<String, String> vars
    ) {}

    @PostMapping("/email")
    public ResponseEntity<ApiResponse<Void>> send(@Valid @RequestBody EmailRequest request) {
        return deliver(request.to(), request.subject(), request.html());
    }

    @PostMapping("/email/template")
    public ResponseEntity<ApiResponse<Void>> sendTemplate(@Valid @RequestBody TemplateEmailRequest request) {
        String html;
        try {
            html = templateService.render(request.template(), request.vars() == null ? Map.of() : request.vars());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
        return deliver(request.to(), request.subject(), html);
    }

    private ResponseEntity<ApiResponse<Void>> deliver(String to, String subject, String html) {
        try {
            emailService.sendHtml(to, subject, html);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(null, "Email đã được gửi"));
        } catch (MessagingException | org.springframework.mail.MailException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Không gửi được email: " + e.getMessage()));
        }
    }
}
