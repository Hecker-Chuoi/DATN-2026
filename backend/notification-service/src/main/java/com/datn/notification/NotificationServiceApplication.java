package com.datn.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * notification-service: gửi email qua SMTP cho các service khác (identity-service gọi qua HTTP nội bộ).
 * Không đi qua API Gateway; chỉ lộ endpoint /internal/**.
 */
@SpringBootApplication
@EnableAsync
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
