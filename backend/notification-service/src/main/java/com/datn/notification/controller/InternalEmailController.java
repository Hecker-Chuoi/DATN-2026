package com.datn.notification.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.notification.service.EmailService;
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

/**
 * Endpoint nội bộ cho các service khác gọi (không public qua gateway).
 */
@RestController
@RequestMapping("/internal/notifications")
@RequiredArgsConstructor
public class InternalEmailController {

    private final EmailService emailService;

    public record EmailRequest(
            @NotBlank @Email String to,
            @NotBlank String subject,
            @NotBlank String html
    ) {}

    @PostMapping("/email")
    public ResponseEntity<ApiResponse<Void>> send(@Valid @RequestBody EmailRequest request) {
        try {
            emailService.sendHtml(request.to(), request.subject(), request.html());
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(null, "Email đã được gửi"));
        } catch (MessagingException | org.springframework.mail.MailException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Không gửi được email: " + e.getMessage()));
        }
    }
}
