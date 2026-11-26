package com.datn.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * notification-service:
 * <ul>
 *   <li>Thông báo trong ứng dụng: nhận sự kiện từ Kafka (topic notification-events), lưu bảng notifications,
 *       đẩy xuống trình duyệt qua STOMP/WebSocket; REST {@code /api/v1/notifications/**} qua gateway.</li>
 *   <li>Email: endpoint nội bộ {@code /internal/notifications/**} cho identity-service và các service khác,
 *       cộng với email của một số loại thông báo.</li>
 * </ul>
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
