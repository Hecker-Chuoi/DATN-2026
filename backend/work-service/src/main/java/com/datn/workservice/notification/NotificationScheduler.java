package com.datn.workservice.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * Chạy các job thông báo lúc 08:00 giờ Việt Nam mỗi ngày. Tắt bằng {@code app.notifications.jobs-enabled=false}.
 * Nhiều instance cùng chạy cũng không sinh trùng: mỗi sự kiện có dedupKey và notification-service chống trùng.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final NotificationJobService jobs;

    @Value("${app.notifications.jobs-enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${app.notifications.jobs-cron:0 0 8 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void runDaily() {
        if (!enabled) return;
        try {
            Map<String, Integer> result = jobs.runAll(LocalDate.now(ZONE), LocalDateTime.now(ZONE), null);
            log.info("Job thông báo hằng ngày: {}", result);
        } catch (Exception e) {
            log.error("Job thông báo hằng ngày lỗi: {}", e.getMessage(), e);
        }
    }
}
