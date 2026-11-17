package com.datn.workservice.notification;

import com.datn.workservice.cluster.ClusterJobLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * Chạy các job thông báo lúc 08:00 giờ Việt Nam mỗi ngày. Tắt bằng {@code app.notifications.jobs-enabled=false}.
 * Nhiều instance cùng chạy cũng không sinh trùng (mỗi sự kiện có dedupKey); bật {@code app.cluster.redis-enabled}
 * thì {@link ClusterJobLock} chỉ cho một instance chạy, đỡ tốn truy vấn.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final NotificationJobService jobs;
    private final ClusterJobLock jobLock;

    @Value("${app.notifications.jobs-enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${app.notifications.jobs-cron:0 0 8 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void runDaily() {
        if (!enabled) return;
        // Chạy nhiều instance (app.cluster.redis-enabled): chỉ một instance chạy; giữ khoá ít nhất 5 phút
        jobLock.runExclusively("notification-daily", Duration.ofMinutes(30), Duration.ofMinutes(5), () -> {
            try {
                Map<String, Integer> result = jobs.runAll(LocalDate.now(ZONE), LocalDateTime.now(ZONE), null);
                log.info("Job thông báo hằng ngày: {}", result);
            } catch (Exception e) {
                log.error("Job thông báo hằng ngày lỗi: {}", e.getMessage(), e);
            }
        });
    }
}
